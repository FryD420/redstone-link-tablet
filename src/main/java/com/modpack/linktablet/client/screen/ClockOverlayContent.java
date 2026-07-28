package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.api.client.OverlayContent;
import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.ClockService;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The pinned Clock body (1.10.0 program-aware overlay): local time big,
 * world-clock rows beneath, plus live timer/stopwatch lines whenever
 * those are doing something. Mostly a display — the stopwatch line is
 * tappable (start/pause) and any click quells an active ring; everything
 * else lives in the full ClockScreen (right-click the window opens it).
 */
public class ClockOverlayContent implements OverlayContent {

    private static final int BIG_H = 16;
    private static final int ROW_H = 10;
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private boolean showTimer() {
        return ClockService.timerRunning();
    }

    private boolean showStopwatch() {
        return ClockService.stopwatchRunning() || ClockService.stopwatchElapsedMillis() > 0;
    }

    @Override
    public int height(int rowWidth) {
        return BIG_H + ClockService.zones().size() * ROW_H
                + (showTimer() ? ROW_H : 0) + (showStopwatch() ? ROW_H : 0);
    }

    @Override
    public void render(GuiGraphics graphics, Font font, ScreenTheme theme, int x, int top,
                       int rowWidth, int mouseX, int mouseY, boolean reachable,
                       int clipTop, int clipBottom) {
        LocalDateTime now = LocalDateTime.now();
        String time = String.format("%02d:%02d:%02d", now.getHour(), now.getMinute(), now.getSecond());
        graphics.pose().pushPose();
        graphics.pose().translate(x + (rowWidth - font.width(time) * 1.5f) / 2f, top + 1, 0);
        graphics.pose().scale(1.5f, 1.5f, 1f);
        graphics.drawString(font, time, 0, 0, theme.textPrimary, theme.textShadow);
        graphics.pose().popPose();

        int y = top + BIG_H;
        for (ZoneId zone : ClockService.zones()) {
            ZonedDateTime there = ZonedDateTime.now(zone);
            String label = zoneLabel(zone);
            String timeThere = there.format(HHMM);
            graphics.drawString(font, label, x + 2, y + 1, theme.textMuted, theme.textShadow);
            graphics.drawString(font, timeThere, x + rowWidth - font.width(timeThere) - 2, y + 1,
                    theme.textPrimary, theme.textShadow);
            y += ROW_H;
        }
        if (showTimer()) {
            Component label = Component.translatable("gui.linktablet.clock.tab.timer");
            String remaining = ClockScreen.duration(ClockService.timerRemainingMillis());
            graphics.drawString(font, label, x + 2, y + 1, theme.accent, theme.textShadow);
            graphics.drawString(font, remaining, x + rowWidth - font.width(remaining) - 2, y + 1,
                    theme.accent, theme.textShadow);
            y += ROW_H;
        }
        if (showStopwatch()) {
            Component label = Component.translatable("gui.linktablet.clock.tab.stopwatch");
            String elapsed = ClockScreen.stopwatch(ClockService.stopwatchElapsedMillis());
            int color = ClockService.stopwatchRunning() ? theme.accent : theme.textMuted;
            graphics.drawString(font, label, x + 2, y + 1, color, theme.textShadow);
            graphics.drawString(font, elapsed, x + rowWidth - font.width(elapsed) - 2, y + 1,
                    color, theme.textShadow);
        }
    }

    /** "America/New_York" → "New York" (matches ClockScreen's rows). */
    private static String zoneLabel(ZoneId zone) {
        String id = zone.getId();
        int slash = id.lastIndexOf('/');
        return (slash >= 0 ? id.substring(slash + 1) : id).replace('_', ' ');
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, int x, int top, int rowWidth) {
        ClockService.stopRing();
        if (button != 0) return false;
        // The stopwatch line is a start/pause tap target
        if (showStopwatch()) {
            int swY = top + BIG_H + ClockService.zones().size() * ROW_H
                    + (showTimer() ? ROW_H : 0);
            if (my >= swY && my < swY + ROW_H) {
                ClockService.stopwatchStartPause();
                UISounds.toggle(ClockService.stopwatchRunning());
                return true;
            }
        }
        return false;
    }
}
