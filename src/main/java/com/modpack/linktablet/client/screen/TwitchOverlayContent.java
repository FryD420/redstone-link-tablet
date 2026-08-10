package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.api.client.OverlayContent;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.EmoteText;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.TwitchChatService;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The pinned Twitch Chat body (1.11.0): the last few messages, one
 * compact single line each — colored username, then the text — live
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
 *
 * <p>Message text (1.11.0 emotes; wrap rework post-1.11.0): messages
 * wrap to as many {@link #ROW_H} lines as they need — {@link
 * TwitchScreen}'s exact row shape (colored prefix drawn once, every
 * wrapped line at the same clamped indent, layout through {@code
 * EmoteText.wrap}) — under one TOTAL line budget, {@link #MAX_LINES}:
 * rows fill newest-first from the bottom, an older message that
 * doesn't wholly fit the remaining budget is dropped (never shown
 * partially), and only the newest message may truncate — when it
 * alone overflows the whole budget it caps its lines and spends the
 * last one on an "…" marker. The 1.11.0 draw-only-line-one hard cut
 * read as broken (user report 2026-08-10), which answered beta.6's
 * open "does the hard cut read clean" question.
 */
public class TwitchOverlayContent implements OverlayContent {

    private static final int ROW_H = 10;
    /** Newest messages considered (the ring-buffer slice), not a
     * guarantee they all show — the line budget below decides that. */
    private static final int MAX_ROWS = 8;
    /** Total wrapped-line budget across all messages — the pane's whole
     * vertical footprint (10 × {@link #ROW_H} = 100, still inside
     * MiniTabletWindow's body cap, so the window never scroll-clips). */
    private static final int MAX_LINES = 10;
    /** Mirrors {@link TwitchScreen}'s continuation-indent clamp. */
    private static final int MIN_TEXT_W = 20;

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

    /** One message laid out for the pane — {@link TwitchScreen}'s Row
     * shape plus the truncation marker (newest-message-only, see the
     * class doc). */
    private record Row(String prefix, int prefixColor, int indent,
                       List<EmoteText.Line> lines, boolean truncated) {
        int lineCount() {
            return lines.size() + (truncated ? 1 : 0);
        }
    }

    /** Lays out the newest messages against the {@link #MAX_LINES}
     * budget, newest-first from the bottom; returns rows oldest-first
     * (draw order). Older messages only appear whole; only the newest
     * may truncate. Called by BOTH {@link #height} and {@link #render}
     * each frame — cheap for ≤{@link #MAX_ROWS} messages, and exactly
     * how {@link TwitchScreen} rebuilds its rows per frame. */
    private List<Row> buildRows(Font font, List<TwitchChatService.ChatMessage> messages,
                                String channel, int rowWidth) {
        List<Row> rows = new ArrayList<>();
        int budget = MAX_LINES;
        for (int i = messages.size() - 1; i >= 0 && budget > 0; i--) {
            TwitchChatService.ChatMessage m = messages.get(i);
            String prefix = m.user() + ": ";
            int prefixWidth = font.width(prefix);
            int indent = Math.min(prefixWidth, Math.max(MIN_TEXT_W, rowWidth - MIN_TEXT_W));
            int textWidth = Math.max(MIN_TEXT_W, rowWidth - indent);
            List<EmoteText.Line> lines = EmoteText.wrap(font,
                    EmoteText.segments(m, channel), textWidth, ROW_H);
            boolean truncated = false;
            if (lines.size() > budget) {
                if (!rows.isEmpty()) break; // older messages never show partially
                // Newest alone overflows the whole budget: cap it and
                // spend the final budgeted line on the "…" marker.
                lines = List.copyOf(lines.subList(0, Math.max(1, budget - 1)));
                truncated = true;
            }
            rows.add(new Row(prefix, m.color(), indent, lines, truncated));
            budget -= lines.size() + (truncated ? 1 : 0);
        }
        Collections.reverse(rows);
        return rows;
    }

    @Override
    public int height(int rowWidth) {
        String channel = currentChannel();
        List<TwitchChatService.ChatMessage> messages = lastMessages(channel);
        Component status = statusMessage(channel, messages);
        if (status != null) return ROW_H;
        int lineTotal = 0;
        for (Row row : buildRows(Minecraft.getInstance().font, messages, channel, rowWidth)) {
            lineTotal += row.lineCount();
        }
        return lineTotal * ROW_H;
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

        int ry = top;
        for (Row row : buildRows(font, messages, channel, rowWidth)) {
            ry = drawRow(graphics, font, theme, row, x, ry, rowWidth, clipTop, clipBottom);
        }
    }

    private static boolean lineVisible(int ry, int clipTop, int clipBottom) {
        return ry + ROW_H >= clipTop && ry <= clipBottom;
    }

    /** Draws one laid-out row (prefix on the first line, wrapped lines
     * at the row's indent, optional "…" marker line) and returns the y
     * below it. */
    private int drawRow(GuiGraphics graphics, Font font, ScreenTheme theme, Row row,
                        int x, int ry, int rowWidth, int clipTop, int clipBottom) {
        if (lineVisible(ry, clipTop, clipBottom)) {
            String shownPrefix = TextFit.ellipsize(font, row.prefix(), rowWidth);
            graphics.drawString(font, shownPrefix, x, ry + 1, row.prefixColor(), theme.textShadow);
        }
        for (EmoteText.Line line : row.lines()) {
            if (lineVisible(ry, clipTop, clipBottom)) {
                EmoteText.drawGui(graphics, font, line, x + row.indent(), ry + 1,
                        ROW_H, theme.textPrimary, theme.textShadow);
            }
            ry += ROW_H;
        }
        if (row.truncated()) {
            if (lineVisible(ry, clipTop, clipBottom)) {
                graphics.drawString(font, "…", x + row.indent(), ry + 1,
                        theme.textFaint, theme.textShadow);
            }
            ry += ROW_H;
        }
        return ry;
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
