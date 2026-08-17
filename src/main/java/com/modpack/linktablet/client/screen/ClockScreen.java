package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.ClockService;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/**
 * The Clock program (1.10.0 OS suite): four tabs — Alarm, Clock (local +
 * world clock), Timer, Stopwatch — over {@link ClockService}'s state.
 * The screen is a dumb face: every alarm/timer/stopwatch mutation goes
 * through the service, which also rings with no GUI open. Fixed-height
 * body inside the 240-unit GUI budget; controls are hand-rolled hit
 * rects like the rest of the tablet's screens.
 */
public class ClockScreen extends Screen {

    private enum Tab { ALARM, CLOCK, TIMER, STOPWATCH }

    private static final int PANEL_W = 200;
    private static final int HEADER = 34;
    private static final int TABS_H = 16;
    private static final int CONTENT_H = 150;
    private static final int BOTTOM_PAD = 8;
    private static final int MODE_BTN_SIZE = 12;
    private static final int ROW_H = 14;

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final SignalView view;
    private Tab tab = Tab.CLOCK;
    private final ZonePickerOverlay zonePicker = new ZonePickerOverlay(
            net.minecraft.client.Minecraft.getInstance().font);

    /** Pending alarm time being composed on the Alarm tab. */
    private int pendingHour;
    private int pendingMinute;

    public ClockScreen(SignalView view) {
        super(Component.translatable("program.linktablet.clock"));
        this.view = view;
        LocalDateTime now = LocalDateTime.now();
        this.pendingHour = now.getHour();
        this.pendingMinute = now.getMinute();
    }

    private ScreenTheme theme() {
        return view.theme();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private int bodyHeight() {
        return HEADER + TABS_H + CONTENT_H + BOTTOM_PAD;
    }

    private int bodyTop() {
        return (height - bodyHeight()) / 2;
    }

    private int panelLeft() {
        return (width - PANEL_W) / 2;
    }

    private int tabsY() {
        return bodyTop() + HEADER;
    }

    private int contentTop() {
        return tabsY() + TABS_H + 4;
    }

    private int tabW() {
        return PANEL_W / 4;
    }

    private int homeBtnX() {
        return panelLeft() + 4;
    }

    /** Per-app overlay pin (1.10.0): pins the CLOCK overlay. */
    private int pinBtnX() {
        return homeBtnX() + MODE_BTN_SIZE + 4;
    }

    private int modeBtnY() {
        return bodyTop() + 8;
    }

    private boolean overBtn(double mouseX, double mouseY, int btnX) {
        return mouseX >= btnX && mouseX < btnX + MODE_BTN_SIZE
                && mouseY >= modeBtnY() && mouseY < modeBtnY() + MODE_BTN_SIZE;
    }

    private boolean overHomeBtn(double mouseX, double mouseY) {
        return overBtn(mouseX, mouseY, homeBtnX());
    }

    private int tabAt(double mouseX, double mouseY) {
        if (mouseY < tabsY() || mouseY >= tabsY() + TABS_H) return -1;
        int rel = (int) (mouseX - panelLeft());
        if (rel < 0 || rel >= PANEL_W) return -1;
        return rel / tabW();
    }

    // ------------------------------------------------------------------
    // Formatting helpers
    // ------------------------------------------------------------------

    private static String two(int n) {
        return n < 10 ? "0" + n : Integer.toString(n);
    }

    /** H:MM:SS above an hour, MM:SS below (shared with the overlay). */
    static String duration(long millis) {
        long totalSec = (millis + 999) / 1000; // ceil: "0:01" until truly done
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return h > 0 ? h + ":" + two((int) m) + ":" + two((int) s)
                : (int) m + ":" + two((int) s);
    }

    /** Stopwatch: M:SS.t with tenths (shared with the overlay). */
    static String stopwatch(long millis) {
        long m = millis / 60000;
        long s = (millis % 60000) / 1000;
        long tenths = (millis % 1000) / 100;
        return m + ":" + two((int) s) + "." + tenths;
    }

    /** "America/New_York" → "New York" (row label). */
    private static String zoneLabel(ZoneId zone) {
        String id = zone.getId();
        int slash = id.lastIndexOf('/');
        return (slash >= 0 ? id.substring(slash + 1) : id).replace('_', ' ');
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
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
                overHomeBtn(mouseX, mouseY) ? theme.glyphHover : theme.textFaint);
        addBackgroundTip(homeBtnX(), modeBtnY(), MODE_BTN_SIZE, MODE_BTN_SIZE, "gui.linktablet.home");
        boolean pinned = com.modpack.linktablet.client.OverlayPin.isPinned(
                view, com.modpack.linktablet.Program.CLOCK);
        HeaderGlyphs.pin(graphics, pinBtnX(), modeBtnY(),
                pinned ? theme.accent
                        : overBtn(mouseX, mouseY, pinBtnX()) ? theme.glyphHover : theme.textFaint);
        addBackgroundTip(pinBtnX(), modeBtnY(), MODE_BTN_SIZE, MODE_BTN_SIZE, pinned
                ? "gui.linktablet.overlay.unpin" : "gui.linktablet.overlay.pin");
        Chrome.railH(graphics, left - 4, tabsY() - 4, PANEL_W + 8, theme.bodyOuter);

        // Tab strip: four equal chips, active one lit with an accent bar
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            int tx = left + i * tabW();
            boolean active = tabs[i] == tab;
            boolean hovered = !zonePicker.isOpen() && tabAt(mouseX, mouseY) == i;
            graphics.fill(tx + 1, tabsY(), tx + tabW() - 1, tabsY() + TABS_H,
                    active ? theme.rowBgHover : hovered ? theme.rowBg : theme.surfaceLo);
            if (active) {
                graphics.fill(tx + 1, tabsY() + TABS_H - 2, tx + tabW() - 1,
                        tabsY() + TABS_H, theme.accent);
            }
            Component label = Component.translatable("gui.linktablet.clock.tab."
                    + tabs[i].name().toLowerCase(Locale.ROOT));
            graphics.drawString(font, label, tx + (tabW() - font.width(label)) / 2,
                    tabsY() + 4, active ? theme.textPrimary : theme.textMuted, theme.textShadow);
            addBackgroundTip(tx + 1, tabsY(), tabW() - 2, TABS_H,
                    "gui.linktablet.tip.clock.tab." + tabs[i].name().toLowerCase(Locale.ROOT));
        }

        switch (tab) {
            case ALARM -> renderAlarmTab(graphics, mouseX, mouseY, theme);
            case CLOCK -> renderClockTab(graphics, mouseX, mouseY, theme);
            case TIMER -> renderTimerTab(graphics, mouseX, mouseY, theme);
            case STOPWATCH -> renderStopwatchTab(graphics, mouseX, mouseY, theme);
        }

        if (zonePicker.isOpen()) {
            zonePicker.render(graphics, mouseX, mouseY, partialTick, width, height, theme);
        }

        ScreenTips.draw(graphics, font, mouseX, mouseY);
    }

    /** Big scaled text, centered on cx. */
    private void drawBig(GuiGraphics graphics, String text, int cx, int y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(cx - font.width(text) * scale / 2f, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, color, theme().textShadow);
        graphics.pose().popPose();
    }

    /** Hand-rolled banner button; returns true when hovered. */
    private boolean button(GuiGraphics graphics, int x, int y, int w, int h,
                           Component label, double mouseX, double mouseY, boolean enabled) {
        ScreenTheme theme = theme();
        boolean hovered = enabled && !zonePicker.isOpen()
                && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        Chrome.bannerButton(graphics, x, y, w, h,
                !enabled ? Chrome.ButtonState.DISABLED
                        : hovered ? Chrome.ButtonState.HOVER : Chrome.ButtonState.NORMAL,
                hovered ? theme.rowBgHover : theme.rowBg);
        graphics.drawString(font, label, x + (w - font.width(label)) / 2,
                y + (h - 8) / 2, enabled ? theme.textPrimary : theme.textFaint, theme.textShadow);
        return hovered;
    }

    private static boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * Registers a tooltip for a BACKGROUND control (tabs and every
     * per-tab control) — suppressed while the zone-picker overlay is
     * open, mirroring {@code SignalEditScreen#addBackgroundTip}: the
     * overlay is a full-screen modal drawn after these controls, so an
     * unsuppressed tooltip would paint on top of it.
     */
    private void addBackgroundTip(int x, int y, int w, int h, String key) {
        if (zonePicker.isOpen()) return;
        ScreenTips.add(x, y, w, h, key);
    }

    // ---- Clock tab ----------------------------------------------------

    private void renderClockTab(GuiGraphics graphics, int mouseX, int mouseY, ScreenTheme theme) {
        int cx = width / 2;
        int y = contentTop();
        LocalDateTime now = LocalDateTime.now();
        // Blinking colon, like a bedside clock
        String time = two(now.getHour()) + (now.getSecond() % 2 == 0 ? ":" : " ") + two(now.getMinute());
        drawBig(graphics, time, cx, y + 2, 2f, theme.textPrimary);
        String date = now.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault())
                + " " + now.getDayOfMonth() + " "
                + now.getMonth().getDisplayName(TextStyle.SHORT, Locale.getDefault());
        drawBig(graphics, date, cx, y + 22, 1f, theme.textMuted);

        // World clock rows
        int rowsTop = y + 36;
        int left = panelLeft() + 8;
        int right = panelLeft() + PANEL_W - 8;
        List<ZoneId> zones = ClockService.zones();
        for (int i = 0; i < zones.size(); i++) {
            int ry = rowsTop + i * ROW_H;
            boolean hovered = !zonePicker.isOpen()
                    && over(mouseX, mouseY, left, ry, right - left, ROW_H);
            if (hovered) {
                graphics.fill(left, ry, right, ry + ROW_H, theme.rowBgHover);
            }
            ZonedDateTime there = ZonedDateTime.now(zones.get(i));
            String timeThere = there.format(HHMM);
            // Day drift marker when "there" is on a different calendar day
            int dayDiff = there.toLocalDate().compareTo(now.toLocalDate());
            if (dayDiff != 0) {
                timeThere = (dayDiff > 0 ? "+1d " : "-1d ") + timeThere;
            }
            graphics.drawString(font, zoneLabel(zones.get(i)), left + 2, ry + 3,
                    theme.textPrimary, theme.textShadow);
            graphics.drawString(font, timeThere, right - font.width(timeThere) - 2, ry + 3,
                    theme.textMuted, theme.textShadow);
        }
        if (zones.size() < ClockService.MAX_ZONES) {
            int ry = rowsTop + zones.size() * ROW_H;
            boolean hovered = !zonePicker.isOpen()
                    && over(mouseX, mouseY, left, ry, right - left, ROW_H);
            graphics.fill(left, ry, right, ry + ROW_H, hovered ? theme.surfaceHi : theme.surfaceLo);
            Component add = Component.translatable("gui.linktablet.clock.add_zone");
            graphics.drawString(font, add, left + (right - left - font.width(add)) / 2,
                    ry + 3, theme.textMuted, theme.textShadow);
            addBackgroundTip(left, ry, right - left, ROW_H, "gui.linktablet.tip.clock.zone.add");
        }
        if (!zones.isEmpty()) {
            Component hint = Component.translatable("gui.linktablet.clock.zone_hint");
            graphics.drawString(font, hint,
                    cx - font.width(hint) / 2, contentTop() + CONTENT_H - 12,
                    theme.textFaint, theme.textShadow);
        }
    }

    // ---- Alarm tab ----------------------------------------------------

    private void renderAlarmTab(GuiGraphics graphics, int mouseX, int mouseY, ScreenTheme theme) {
        int left = panelLeft() + 8;
        int right = panelLeft() + PANEL_W - 8;
        int y = contentTop();
        List<ClockService.Alarm> alarms = ClockService.alarms();
        if (alarms.isEmpty()) {
            Component none = Component.translatable("gui.linktablet.clock.no_alarms");
            graphics.drawString(font, none, width / 2 - font.width(none) / 2, y + 4,
                    theme.textMuted, theme.textShadow);
        }
        for (int i = 0; i < alarms.size(); i++) {
            ClockService.Alarm alarm = alarms.get(i);
            int ry = y + i * ROW_H;
            graphics.fill(left, ry, right, ry + ROW_H, theme.rowBg);
            graphics.drawString(font, alarm.label(), left + 4, ry + 3,
                    alarm.enabled() ? theme.textPrimary : theme.textFaint, theme.textShadow);
            // Mini switch (toggle) + X (remove), right-aligned
            int sw = 18, sh = 10;
            int sx = right - 34;
            int sy = ry + (ROW_H - sh) / 2;
            graphics.fill(sx, sy, sx + sw, sy + sh, alarm.enabled() ? theme.accentDim : theme.switchOff);
            int knobX = alarm.enabled() ? sx + sw - 8 : sx + 2;
            graphics.fill(knobX, sy + 2, knobX + 6, sy + sh - 2,
                    alarm.enabled() ? theme.accent : theme.textMuted);
            boolean overX = over(mouseX, mouseY, right - 12, ry + 2, 10, 10);
            graphics.drawString(font, "x", right - 10, ry + 3,
                    overX ? theme.glyphHover : theme.textFaint, false);
            addBackgroundTip(right - 12, ry + 2, 10, 10, "gui.linktablet.tip.clock.alarm.remove");
        }

        // Composer: hour/minute steppers + Add, pinned to the tab bottom
        int by = contentTop() + CONTENT_H - 38;
        Chrome.railH(graphics, panelLeft() - 4, by - 6, PANEL_W + 8, theme.bodyOuter);
        drawBig(graphics, two(pendingHour) + ":" + two(pendingMinute), width / 2, by, 1.5f,
                theme.textPrimary);
        int bw = 20, bh = 14;
        int stepY = by + 16;
        button(graphics, left, stepY, bw, bh, Component.literal("h-"), mouseX, mouseY, true);
        addBackgroundTip(left, stepY, bw, bh, "gui.linktablet.tip.clock.hour");
        button(graphics, left + bw + 2, stepY, bw, bh, Component.literal("h+"), mouseX, mouseY, true);
        addBackgroundTip(left + bw + 2, stepY, bw, bh, "gui.linktablet.tip.clock.hour");
        button(graphics, left + 2 * (bw + 2), stepY, bw, bh, Component.literal("m-"), mouseX, mouseY, true);
        addBackgroundTip(left + 2 * (bw + 2), stepY, bw, bh, "gui.linktablet.tip.clock.minute");
        button(graphics, left + 3 * (bw + 2), stepY, bw, bh, Component.literal("m+"), mouseX, mouseY, true);
        addBackgroundTip(left + 3 * (bw + 2), stepY, bw, bh, "gui.linktablet.tip.clock.minute");
        button(graphics, right - 56, stepY, 56, bh,
                Component.translatable("gui.linktablet.clock.add_alarm"), mouseX, mouseY,
                alarms.size() < ClockService.MAX_ALARMS);
        addBackgroundTip(right - 56, stepY, 56, bh, "gui.linktablet.tip.clock.alarm.add");
    }

    // ---- Timer tab ----------------------------------------------------

    private void renderTimerTab(GuiGraphics graphics, int mouseX, int mouseY, ScreenTheme theme) {
        int cx = width / 2;
        int y = contentTop() + 10;
        boolean running = ClockService.timerRunning();
        String big = running ? duration(ClockService.timerRemainingMillis())
                : duration(ClockService.timerDuration() * 1000L);
        drawBig(graphics, big, cx, y, 3f, running ? theme.accent : theme.textPrimary);

        int bh = 16;
        int by = y + 40;
        if (running) {
            button(graphics, cx - 40, by + 24, 80, bh,
                    Component.translatable("gui.linktablet.clock.cancel"), mouseX, mouseY, true);
            addBackgroundTip(cx - 40, by + 24, 80, bh, "gui.linktablet.tip.clock.timer.cancel");
        } else {
            int bw = 34;
            int total = 4 * bw + 3 * 4;
            int bx = cx - total / 2;
            button(graphics, bx, by, bw, bh, Component.literal("-1m"), mouseX, mouseY, true);
            addBackgroundTip(bx, by, bw, bh, "gui.linktablet.tip.clock.timer.adjust");
            button(graphics, bx + bw + 4, by, bw, bh, Component.literal("-10s"), mouseX, mouseY, true);
            addBackgroundTip(bx + bw + 4, by, bw, bh, "gui.linktablet.tip.clock.timer.adjust");
            button(graphics, bx + 2 * (bw + 4), by, bw, bh, Component.literal("+10s"), mouseX, mouseY, true);
            addBackgroundTip(bx + 2 * (bw + 4), by, bw, bh, "gui.linktablet.tip.clock.timer.adjust");
            button(graphics, bx + 3 * (bw + 4), by, bw, bh, Component.literal("+1m"), mouseX, mouseY, true);
            addBackgroundTip(bx + 3 * (bw + 4), by, bw, bh, "gui.linktablet.tip.clock.timer.adjust");
            button(graphics, cx - 40, by + 24, 80, bh,
                    Component.translatable("gui.linktablet.clock.start"), mouseX, mouseY, true);
            addBackgroundTip(cx - 40, by + 24, 80, bh, "gui.linktablet.tip.clock.timer.start");
        }
    }

    // ---- Stopwatch tab -------------------------------------------------

    private void renderStopwatchTab(GuiGraphics graphics, int mouseX, int mouseY, ScreenTheme theme) {
        int cx = width / 2;
        int y = contentTop() + 10;
        boolean running = ClockService.stopwatchRunning();
        drawBig(graphics, stopwatch(ClockService.stopwatchElapsedMillis()), cx, y, 3f,
                running ? theme.accent : theme.textPrimary);
        int bh = 16;
        int by = y + 50;
        button(graphics, cx - 74, by, 70, bh,
                Component.translatable(running ? "gui.linktablet.clock.pause"
                        : "gui.linktablet.clock.start"), mouseX, mouseY, true);
        addBackgroundTip(cx - 74, by, 70, bh, running
                ? "gui.linktablet.tip.clock.sw.pause" : "gui.linktablet.tip.clock.sw.start");
        boolean canReset = ClockService.stopwatchElapsedMillis() > 0;
        button(graphics, cx + 4, by, 70, bh,
                Component.translatable("gui.linktablet.clock.reset"), mouseX, mouseY, canReset);
        if (canReset) {
            addBackgroundTip(cx + 4, by, 70, bh, "gui.linktablet.tip.clock.sw.reset");
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        ClockService.stopRing();
        if (zonePicker.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0 && overHomeBtn(mouseX, mouseY)) {
            UISounds.tick(1.2F);
            ClientHooks.returnHome(view);
            return true;
        }
        if (button == 0 && overBtn(mouseX, mouseY, pinBtnX())) {
            if (com.modpack.linktablet.client.OverlayPin.isPinned(
                    view, com.modpack.linktablet.Program.CLOCK)) {
                com.modpack.linktablet.client.OverlayPin.unpin();
                UISounds.tick(1.0F);
            } else {
                com.modpack.linktablet.client.OverlayPin.pin(
                        view, com.modpack.linktablet.Program.CLOCK);
                UISounds.tick(1.5F);
            }
            return true;
        }
        int tabHit = tabAt(mouseX, mouseY);
        if (button == 0 && tabHit >= 0) {
            Tab picked = Tab.values()[tabHit];
            if (picked != tab) {
                tab = picked;
                UISounds.tick(1.3F);
            }
            return true;
        }

        boolean handled = switch (tab) {
            case ALARM -> clickAlarmTab(mouseX, mouseY, button);
            case CLOCK -> clickClockTab(mouseX, mouseY, button);
            case TIMER -> clickTimerTab(mouseX, mouseY, button);
            case STOPWATCH -> clickStopwatchTab(mouseX, mouseY, button);
        };
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean clickClockTab(double mouseX, double mouseY, int button) {
        int rowsTop = contentTop() + 36;
        int left = panelLeft() + 8;
        int right = panelLeft() + PANEL_W - 8;
        List<ZoneId> zones = ClockService.zones();
        for (int i = 0; i < zones.size(); i++) {
            if (over(mouseX, mouseY, left, rowsTop + i * ROW_H, right - left, ROW_H)) {
                if (button == 1) { // right-click removes, like home tiles
                    ClockService.removeZone(i);
                    UISounds.tick(1.0F);
                }
                return true;
            }
        }
        if (button == 0 && zones.size() < ClockService.MAX_ZONES
                && over(mouseX, mouseY, left, rowsTop + zones.size() * ROW_H, right - left, ROW_H)) {
            UISounds.tick(1.3F);
            zonePicker.open(width, height, zone -> {
                if (ClockService.addZone(zone)) {
                    UISounds.confirm();
                }
            });
            return true;
        }
        return false;
    }

    private boolean clickAlarmTab(double mouseX, double mouseY, int button) {
        int left = panelLeft() + 8;
        int right = panelLeft() + PANEL_W - 8;
        int y = contentTop();
        List<ClockService.Alarm> alarms = ClockService.alarms();
        for (int i = 0; i < alarms.size(); i++) {
            int ry = y + i * ROW_H;
            if (over(mouseX, mouseY, right - 12, ry + 2, 10, 10)) {
                ClockService.removeAlarm(i);
                UISounds.delete();
                return true;
            }
            if (over(mouseX, mouseY, left, ry, right - left, ROW_H)) {
                ClockService.toggleAlarm(i);
                UISounds.toggle(ClockService.alarms().get(i).enabled());
                return true;
            }
        }
        if (button != 0) return false;
        int bw = 20, bh = 14;
        int stepY = contentTop() + CONTENT_H - 38 + 16;
        if (over(mouseX, mouseY, left, stepY, bw, bh)) {
            pendingHour = (pendingHour + 23) % 24;
            UISounds.tick(1.0F);
            return true;
        }
        if (over(mouseX, mouseY, left + bw + 2, stepY, bw, bh)) {
            pendingHour = (pendingHour + 1) % 24;
            UISounds.tick(1.2F);
            return true;
        }
        if (over(mouseX, mouseY, left + 2 * (bw + 2), stepY, bw, bh)) {
            pendingMinute = (pendingMinute + 60 - (hasShiftDown() ? 1 : 5)) % 60;
            UISounds.tick(1.0F);
            return true;
        }
        if (over(mouseX, mouseY, left + 3 * (bw + 2), stepY, bw, bh)) {
            pendingMinute = (pendingMinute + (hasShiftDown() ? 1 : 5)) % 60;
            UISounds.tick(1.2F);
            return true;
        }
        if (over(mouseX, mouseY, right - 56, stepY, 56, bh)) {
            if (ClockService.addAlarm(pendingHour * 60 + pendingMinute)) {
                UISounds.confirm();
            } else {
                UISounds.tick(0.7F);
            }
            return true;
        }
        return false;
    }

    private boolean clickTimerTab(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int cx = width / 2;
        int by = contentTop() + 50;
        int bh = 16;
        if (ClockService.timerRunning()) {
            if (over(mouseX, mouseY, cx - 40, by + 24, 80, bh)) {
                ClockService.cancelTimer();
                UISounds.tick(0.8F);
                return true;
            }
            return false;
        }
        int bw = 34;
        int total = 4 * bw + 3 * 4;
        int bx = cx - total / 2;
        int[] deltas = {-60, -10, 10, 60};
        for (int i = 0; i < 4; i++) {
            if (over(mouseX, mouseY, bx + i * (bw + 4), by, bw, bh)) {
                ClockService.setTimerDuration(ClockService.timerDuration() + deltas[i]);
                UISounds.tick(deltas[i] > 0 ? 1.3F : 1.0F);
                return true;
            }
        }
        if (over(mouseX, mouseY, cx - 40, by + 24, 80, bh)) {
            ClockService.startTimer();
            UISounds.confirm();
            return true;
        }
        return false;
    }

    private boolean clickStopwatchTab(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        int cx = width / 2;
        int by = contentTop() + 60;
        int bh = 16;
        if (over(mouseX, mouseY, cx - 74, by, 70, bh)) {
            ClockService.stopwatchStartPause();
            UISounds.toggle(ClockService.stopwatchRunning());
            return true;
        }
        if (over(mouseX, mouseY, cx + 4, by, 70, bh)
                && ClockService.stopwatchElapsedMillis() > 0) {
            ClockService.stopwatchReset();
            UISounds.tick(0.9F);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (zonePicker.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (zonePicker.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (zonePicker.charTyped(codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void onClose() {
        UISounds.close();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
