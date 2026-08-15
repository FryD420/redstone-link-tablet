package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.EmoteText;
import com.modpack.linktablet.client.OverlayPin;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.TwitchChatService;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.client.screen.chrome.ChromeEditBox;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Twitch Chat program (1.11.0): a channel box up top and a live,
 * read-only, scrolling chat feed underneath — {@link TwitchChatService}
 * is the shared engine, this screen is just one of its surfaces
 * (overlay pin and kiosk faces are the others, later tasks). Patterned
 * on {@link MonitorScreen} for the panel/header/pin/Home shape and
 * {@link StoreScreen}'s search box for the standalone {@link
 * ChromeEditBox} click-to-focus/commit dance.
 *
 * <p>Channel identity: block views read/write the BLOCK's channel
 * (synced, {@link SignalView#twitchChannel()}); hand/slot views read/
 * write the PERSONAL {@link ClientPrefs#twitchChannel()} instead — the
 * item carries no channel of its own from this screen's perspective.
 * The box commits on Enter or on losing focus with a changed value,
 * validating through {@link TwitchChatService#validChannel} first (an
 * empty string always clears). {@link #refreshSubscription()} runs
 * every frame and is the ONE place that keeps the service subscription
 * in step with whichever channel is currently effective — it covers
 * both a local commit and a server-synced value arriving for a block
 * view, so the commit path itself never has to touch acquire/release.
 *
 * <p>Message wrapping (1.11.0 emotes): the "user: " prefix is drawn
 * once, colored, as the leading segment of the first line; the message
 * text is wrapped and rendered through {@link EmoteText}, which performs
 * emote-aware wrapping (via {@code EmoteText.wrap}, per-channel and
 * memoized) and draws lines via {@code EmoteText.drawGui}. Every wrapped
 * line — including the first — is drawn starting at that same indent, so
 * continuation lines land flush under where the text (not the username)
 * begins.
 */
public class TwitchScreen extends Screen {

    private static final int PANEL_W = 200;
    private static final int HEADER = 34;
    private static final int MODE_BTN_SIZE = 12;
    private static final int BOTTOM_PAD = 8;

    private static final int CHANNEL_ROW_H = 34;
    /** Fixed scrolling viewport for the chat feed. */
    private static final int CONTENT_H = 140;
    private static final int LINE_H = 9;
    private static final int MSG_GAP = 2;
    /** Floor so a very long username never eats the whole row. */
    private static final int MIN_TEXT_W = 20;

    private final SignalView view;

    private ChromeEditBox channelBox;
    private double scroll = 0;
    /** Pins the view to the newest message unless the user scrolled up. */
    private boolean atBottom = true;

    private boolean subscribed = false;
    /** Channel this screen currently holds an acquire() on ("" = none). */
    private String subscribedChannel = "";

    public TwitchScreen(SignalView view) {
        super(Component.translatable("program.linktablet.twitch"));
        this.view = view;
    }

    private ScreenTheme theme() {
        return view.theme();
    }

    // ------------------------------------------------------------------
    // Channel identity + subscription lifecycle
    // ------------------------------------------------------------------

    private String currentChannel() {
        return view instanceof SignalView.Block ? view.twitchChannel() : ClientPrefs.twitchChannel();
    }

    @Override
    protected void init() {
        super.init();
        channelBox = new ChromeEditBox(font, boxX(), boxY(), boxW(), 8,
                Component.translatable("gui.linktablet.twitch.channel"));
        channelBox.setMaxLength(25);
        channelBox.setHint(Component.translatable("gui.linktablet.twitch.channel.hint"));
        channelBox.setValue(currentChannel());
        // Vanilla Screen.resize() re-calls init() WITHOUT removed() first —
        // guard so a window resize while open doesn't bump the ref count
        // with no matching release (the MonitorScreen orphaned-subscription
        // rule).
        if (!subscribed) {
            String channel = currentChannel();
            if (TwitchChatService.validChannel(channel)) {
                TwitchChatService.acquire(channel);
                subscribedChannel = channel;
            }
            subscribed = true;
        }
    }

    @Override
    public void removed() {
        if (subscribed) {
            if (!subscribedChannel.isEmpty()) TwitchChatService.release(subscribedChannel);
            subscribed = false;
            subscribedChannel = "";
        }
        super.removed();
    }

    /** Keeps the service subscription pointed at whatever channel is
     * effective right now — a local commit AND a block's synced value
     * changing both flow through here, so neither path forks the
     * acquire/release bookkeeping. */
    private void refreshSubscription() {
        String channel = currentChannel();
        if (channel.equals(subscribedChannel)) return;
        if (!subscribedChannel.isEmpty()) TwitchChatService.release(subscribedChannel);
        if (TwitchChatService.validChannel(channel)) {
            TwitchChatService.acquire(channel);
            subscribedChannel = channel;
        } else {
            subscribedChannel = "";
        }
    }

    private void commitChannelBox() {
        String raw = channelBox.getValue().strip().toLowerCase(Locale.ROOT);
        if (raw.equals(currentChannel())) return;
        if (!raw.isEmpty() && !TwitchChatService.validChannel(raw)) {
            UISounds.tick(0.7F); // deny — revert to the last good value
            channelBox.setValue(currentChannel());
            return;
        }
        if (view instanceof SignalView.Block) {
            PacketDistributor.sendToServer(new ModNetworking.SetTwitchChannelPayload(view.target(), raw));
        } else {
            ClientPrefs.setTwitchChannel(raw);
        }
        atBottom = true;
        UISounds.confirm();
    }

    // ------------------------------------------------------------------
    // Message layout
    // ------------------------------------------------------------------

    private List<TwitchChatService.ChatMessage> currentMessages() {
        return TwitchChatService.messages(currentChannel());
    }

    /** Status hint shown in place of the feed; null means "show the
     * feed" (LIVE with a non-empty buffer). */
    private Component statusMessage(String channel, List<TwitchChatService.ChatMessage> messages) {
        if (channel.isEmpty()) return Component.translatable("gui.linktablet.twitch.no_channel");
        TwitchChatService.Status status = TwitchChatService.status(channel);
        return switch (status) {
            case CONNECTING -> Component.translatable("gui.linktablet.twitch.connecting");
            case OFFLINE -> Component.translatable("gui.linktablet.twitch.offline");
            case LIVE -> messages.isEmpty()
                    ? Component.translatable("gui.linktablet.twitch.no_messages") : null;
            case IDLE -> Component.translatable("gui.linktablet.twitch.no_channel");
        };
    }

    /** One message, pre-wrapped: colored "user: " leading segment plus
     * the text's wrapped lines (now emote-aware, see {@link EmoteText}),
     * all drawn at the same continuation indent (see the class doc's
     * wrapping note). */
    private record Row(Component prefix, int prefixColor, int indent,
                       List<EmoteText.Line> lines, int height) {
    }

    private List<Row> buildRows(List<TwitchChatService.ChatMessage> messages, int width) {
        List<Row> rows = new ArrayList<>(messages.size());
        for (TwitchChatService.ChatMessage m : messages) {
            Component prefix = Component.literal(m.user() + ": ");
            int prefixWidth = font.width(prefix);
            int indent = Math.min(prefixWidth, Math.max(MIN_TEXT_W, width - MIN_TEXT_W));
            int textWidth = Math.max(MIN_TEXT_W, width - indent);
            List<EmoteText.Line> lines = EmoteText.wrap(font,
                    EmoteText.segments(m, currentChannel()), textWidth, LINE_H);
            rows.add(new Row(prefix, m.color(), indent, lines, lines.size() * LINE_H));
        }
        return rows;
    }

    private static int contentHeight(List<Row> rows) {
        if (rows.isEmpty()) return 0;
        int h = -MSG_GAP;
        for (Row r : rows) h += r.height() + MSG_GAP;
        return h;
    }

    private double maxScroll() {
        List<Row> rows = buildRows(currentMessages(), rowWidth());
        return Math.max(0, contentHeight(rows) - (listBottom() - listTop()));
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private int bodyHeight() {
        return HEADER + CHANNEL_ROW_H + CONTENT_H + BOTTOM_PAD;
    }

    private int bodyTop() {
        return (height - bodyHeight()) / 2;
    }

    private int panelLeft() {
        return (width - PANEL_W) / 2;
    }

    private int rowX() {
        return panelLeft() + 6;
    }

    private int rowWidth() {
        return PANEL_W - 12;
    }

    private int channelTop() {
        return bodyTop() + HEADER;
    }

    private int boxX() {
        return panelLeft() + 12;
    }

    private int boxY() {
        return channelTop() + 17;
    }

    private int boxW() {
        return PANEL_W - 24;
    }

    private int listTop() {
        return channelTop() + CHANNEL_ROW_H;
    }

    private int listBottom() {
        return bodyTop() + bodyHeight() - BOTTOM_PAD;
    }

    private int homeBtnX() {
        return panelLeft() + 4;
    }

    private int pinBtnX() {
        return homeBtnX() + MODE_BTN_SIZE + 4;
    }

    private int emoteBtnX() {
        return pinBtnX() + MODE_BTN_SIZE + 4;
    }

    private int modeBtnY() {
        return bodyTop() + 8;
    }

    private boolean overBtn(double mouseX, double mouseY, int btnX) {
        return mouseX >= btnX && mouseX < btnX + MODE_BTN_SIZE
                && mouseY >= modeBtnY() && mouseY < modeBtnY() + MODE_BTN_SIZE;
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        refreshSubscription();
        ScreenTheme theme = theme();
        int left = panelLeft();
        int top = bodyTop();

        Chrome.panel(graphics, left - 6, top - 2, PANEL_W + 12, bodyHeight() + 4, theme);
        Component titleText = getTitle();
        int titleW = font.width(titleText);
        Chrome.plaque(graphics, width / 2 - titleW / 2 - 6, top + 2, titleW + 12, 18, theme.rowBg);
        graphics.drawString(font, titleText, width / 2 - titleW / 2, top + 7,
                theme.textPrimary, theme.textShadow);
        HeaderGlyphs.home(graphics, homeBtnX(), modeBtnY(),
                overBtn(mouseX, mouseY, homeBtnX()) ? theme.glyphHover : theme.textFaint);
        ScreenTips.glyph(homeBtnX(), modeBtnY(), "gui.linktablet.home");
        boolean pinned = OverlayPin.isPinned(view, Program.TWITCH);
        HeaderGlyphs.pin(graphics, pinBtnX(), modeBtnY(),
                pinned ? theme.accent
                        : overBtn(mouseX, mouseY, pinBtnX()) ? theme.glyphHover : theme.textFaint);
        ScreenTips.glyph(pinBtnX(), modeBtnY(), pinned
                ? "gui.linktablet.overlay.unpin" : "gui.linktablet.overlay.pin");
        HeaderGlyphs.emotes(graphics, emoteBtnX(), modeBtnY(),
                ClientPrefs.twitchEmotes() ? theme.accent
                        : overBtn(mouseX, mouseY, emoteBtnX()) ? theme.glyphHover : theme.textFaint);
        ScreenTips.glyph(emoteBtnX(), modeBtnY(), ClientPrefs.twitchEmotes()
                ? "gui.linktablet.tip.twitch.emotes.on" : "gui.linktablet.tip.twitch.emotes.off");
        Chrome.railH(graphics, left - 4, top + HEADER - 8, PANEL_W + 8, theme.bodyOuter);

        renderChannelRow(graphics, theme, mouseX, mouseY, partialTick);
        ScreenTips.add(channelBox.getX(), channelBox.getY(), channelBox.getWidth(),
                channelBox.getHeight(), "gui.linktablet.tip.twitch.channel");
        Chrome.railH(graphics, left - 4, listTop() - 4, PANEL_W + 8, theme.bodyOuter);

        String channel = currentChannel();
        List<TwitchChatService.ChatMessage> messages = currentMessages();
        Component status = statusMessage(channel, messages);
        if (status != null) {
            scroll = 0;
            atBottom = true;
            graphics.drawString(font, status, width / 2 - font.width(status) / 2,
                    (listTop() + listBottom()) / 2 - 4, theme.textMuted, theme.textShadow);
        } else {
            List<Row> rows = buildRows(messages, rowWidth());
            double max = Math.max(0, contentHeight(rows) - (listBottom() - listTop()));
            scroll = atBottom ? max : Mth.clamp(scroll, 0, max);

            graphics.enableScissor(rowX(), listTop(), rowX() + rowWidth(), listBottom());
            int y = listTop() - (int) scroll;
            for (Row r : rows) {
                if (y + r.height() >= listTop() && y <= listBottom()) {
                    graphics.drawString(font, r.prefix(), rowX(), y, r.prefixColor(), theme.textShadow);
                    List<EmoteText.Line> lines = r.lines();
                    for (int i = 0; i < lines.size(); i++) {
                        EmoteText.drawGui(graphics, font, lines.get(i), rowX() + r.indent(),
                                y + i * LINE_H, LINE_H, theme.textPrimary, theme.textShadow);
                    }
                }
                y += r.height() + MSG_GAP;
            }
            graphics.disableScissor();
        }

        ScreenTips.draw(graphics, font, mouseX, mouseY);
    }

    private void renderChannelRow(GuiGraphics graphics, ScreenTheme theme,
                                  int mouseX, int mouseY, float partialTick) {
        int x = rowX();
        int top = channelTop();
        Chrome.plaque(graphics, x, top, rowWidth(), CHANNEL_ROW_H - 2, theme.rowBg);
        Component label = Component.translatable("gui.linktablet.twitch.channel");
        graphics.drawString(font, label, x + 4, top + 3, theme.textMuted, theme.textShadow);
        channelBox.render(graphics, mouseX, mouseY, partialTick);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Standalone EditBox (no widget registration): vanilla's
        // mouseClicked repositions the cursor but never focuses — the
        // StoreScreen search-box shape, copied verbatim.
        if (channelBox.mouseClicked(mouseX, mouseY, button)) {
            channelBox.setFocused(true);
            return true;
        }
        if (channelBox.isFocused()) {
            channelBox.setFocused(false);
            commitChannelBox();
        }
        if (button == 0 && overBtn(mouseX, mouseY, homeBtnX())) {
            UISounds.tick(1.2F);
            ClientHooks.returnHome(view);
            return true;
        }
        if (button == 0 && overBtn(mouseX, mouseY, pinBtnX())) {
            if (OverlayPin.isPinned(view, Program.TWITCH)) {
                OverlayPin.unpin();
                UISounds.tick(1.0F);
            } else {
                OverlayPin.pin(view, Program.TWITCH);
                UISounds.tick(1.5F);
            }
            return true;
        }
        if (button == 0 && overBtn(mouseX, mouseY, emoteBtnX())) {
            ClientPrefs.setTwitchEmotes(!ClientPrefs.twitchEmotes());
            UISounds.tick(ClientPrefs.twitchEmotes() ? 1.5F : 1.0F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double max = maxScroll();
        scroll = Mth.clamp(scroll - scrollY * LINE_H * 3, 0, max);
        atBottom = scroll >= max - 0.5;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) return super.keyPressed(keyCode, scanCode, modifiers); // ESC passes through
        if (channelBox.isFocused() && (keyCode == 257 || keyCode == 335)) { // Enter / numpad Enter
            commitChannelBox();
            channelBox.setFocused(false);
            return true;
        }
        if (channelBox.keyPressed(keyCode, scanCode, modifiers)) return true;
        // While typing, keep the inventory key from closing the screen
        // (the GaugesScreen nameBox rule)
        if (channelBox.isFocused()) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (channelBox.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        if (channelBox != null && channelBox.isFocused()) commitChannelBox();
        UISounds.close();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
