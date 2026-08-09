package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.api.client.OverlayContent;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.TwitchChatService;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * The pinned Twitch Chat body (1.11.0): the last few messages, one
 * compact ellipsized line each — colored username, then the text — live
 * on the HUD. No scrolling or composing (that stays in the full {@link
 * TwitchScreen}); right-click the window opens it.
 *
 * <p>Channel identity mirrors {@link TwitchScreen#currentChannel()}:
 * block views read the BLOCK's synced channel, hand/slot views read the
 * PERSONAL {@link ClientPrefs#twitchChannel()}. Unlike {@link
 * MonitorOverlayContent} (one global client-side target that a relog
 * zeroes out from under a still-pinned window), {@link TwitchChatService}
 * ref-counts per CHANNEL STRING with no such reset — so the lifecycle
 * here tracks {@link #subscribedChannel} instead of a bare acquired
 * flag: {@link #render} lazily acquires on first call (subscribedChannel
 * starts empty) and, every frame after, releases the old channel and
 * acquires the new one the moment the effective channel changes (a
 * block sync or a pref edit) — exactly {@link
 * TwitchScreen#refreshSubscription()}'s shape, including its guard
 * against acquiring an empty or invalid channel. {@link #defocus()}
 * releases and clears the tracked channel so a later render re-acquires
 * naturally, the same "defocus can be followed by more renders while
 * the pin lives on" case {@link MonitorOverlayContent} documents.
 */
public class TwitchOverlayContent implements OverlayContent {

    private static final int ROW_H = 10;
    private static final int MAX_ROWS = 8;

    private final Supplier<SignalView> view;
    /** Channel this content currently holds a {@link TwitchChatService}
     * acquire() on ("" = none). */
    private String subscribedChannel = "";

    public TwitchOverlayContent(Supplier<SignalView> view) {
        this.view = view;
    }

    private String currentChannel() {
        SignalView v = view.get();
        return v instanceof SignalView.Block ? v.twitchChannel() : ClientPrefs.twitchChannel();
    }

    /** Keeps the service subscription pointed at whatever channel is
     * effective right now (see the class doc) — the one place
     * acquire()/release() get called from this content. */
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

    /** Last {@link #MAX_ROWS} messages, oldest first (newest at the
     * bottom) — the pane never scrolls, so anything older just isn't
     * shown. */
    private List<TwitchChatService.ChatMessage> lastMessages(String channel) {
        List<TwitchChatService.ChatMessage> all = TwitchChatService.messages(channel);
        if (all.size() <= MAX_ROWS) return all;
        return all.subList(all.size() - MAX_ROWS, all.size());
    }

    /** Status hint shown in place of the feed; null means "show the
     * feed" (LIVE with a non-empty buffer) — matches {@link
     * TwitchScreen#statusMessage}. */
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

    @Override
    public int height(int rowWidth) {
        String channel = currentChannel();
        List<TwitchChatService.ChatMessage> messages = lastMessages(channel);
        Component status = statusMessage(channel, messages);
        return status != null ? ROW_H : messages.size() * ROW_H;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, ScreenTheme theme, int x, int top,
                       int rowWidth, int mouseX, int mouseY, boolean reachable,
                       int clipTop, int clipBottom) {
        refreshSubscription();

        String channel = currentChannel();
        List<TwitchChatService.ChatMessage> messages = lastMessages(channel);
        Component status = statusMessage(channel, messages);
        if (status != null) {
            String shown = TextFit.ellipsize(font, status.getString(), rowWidth);
            graphics.drawString(font, shown, x + (rowWidth - font.width(shown)) / 2,
                    top + 1, theme.textFaint, theme.textShadow);
            return;
        }

        for (int i = 0; i < messages.size(); i++) {
            int ry = top + i * ROW_H;
            if (ry + ROW_H < clipTop || ry > clipBottom) continue;
            renderRow(graphics, font, theme, messages.get(i), x, ry, rowWidth);
        }
    }

    private void renderRow(GuiGraphics graphics, Font font, ScreenTheme theme,
                           TwitchChatService.ChatMessage message, int x, int ry, int rowWidth) {
        String prefix = message.user() + ": ";
        int prefixWidth = font.width(prefix);
        if (prefixWidth >= rowWidth) {
            String shown = TextFit.ellipsize(font, prefix, rowWidth);
            graphics.drawString(font, shown, x, ry + 1, message.color(), theme.textShadow);
            return;
        }
        graphics.drawString(font, prefix, x, ry + 1, message.color(), theme.textShadow);
        String text = TextFit.ellipsize(font, message.text(), rowWidth - prefixWidth);
        graphics.drawString(font, text, x + prefixWidth, ry + 1, theme.textPrimary, theme.textShadow);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int top, int rowWidth) {
        return false; // read-only pane; the window handles right-click-to-open
    }

    @Override
    public void defocus() {
        if (!subscribedChannel.isEmpty()) {
            TwitchChatService.release(subscribedChannel);
            subscribedChannel = "";
        }
    }
}
