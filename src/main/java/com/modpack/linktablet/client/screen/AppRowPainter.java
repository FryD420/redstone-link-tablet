package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The list-mode app row (plaque, icon chip, name, per-type control),
 * extracted from TabletScreen (1.7.0) so the pinned {@link
 * MiniTabletWindow} draws pixel-identical rows. Pure painter: positional
 * args in, no side effects. The note glyph is NOT painted here — it's a
 * GUI-only affordance the TabletScreen overlays afterward (the name
 * reserve still leaves its gap, so both surfaces lay out identically).
 */
final class AppRowPainter {

    /**
     * Paints one row at (x, y, w × {@link TabletScreen#ROW_HEIGHT}).
     * Returns the name as drawn (possibly ellipsized) so the caller can
     * decide whether a full-name tooltip is warranted.
     */
    static String paint(GuiGraphics graphics, Font font, ScreenTheme theme, SignalApp app,
                        int x, int y, int w, boolean hovered, boolean held) {
        boolean lit = app.active() || held;
        Chrome.plaque(graphics, x, y, w, TabletScreen.ROW_HEIGHT, hovered ? theme.rowBgHover : theme.rowBg);

        // Colored icon chip
        graphics.fill(x + 4, y + 4, x + 20, y + 20, app.color() | 0xFF000000);
        graphics.renderItem(app.iconStack(), x + 4, y + 4);

        // Name (leave room for chip + glyph gap + switch/track + count tag)
        String countTag = app.frequencies().size() > 1 ? " x" + app.frequencies().size() : "";
        int tagWidth = countTag.isEmpty() ? 0 : font.width(countTag);
        // Sliders reserve extra room for the numeric level readout
        int controlW = app.slider()
                ? TabletScreen.LIST_SLIDER_W + font.width("15") + 4
                : TabletScreen.SWITCH_W;
        String name = TextFit.ellipsize(font, app.name(), w - 24 - controlW - 24 - tagWidth);
        int nameY = y + (TabletScreen.ROW_HEIGHT - 8) / 2;
        graphics.drawString(font, name, x + 26, nameY,
                lit ? theme.textPrimary : theme.textMuted, theme.textShadow);
        if (!countTag.isEmpty()) {
            graphics.drawString(font, countTag, x + 26 + font.width(name), nameY,
                    0xFF6A7284, theme.textShadow);
        }

        if (app.slider()) {
            // Wide track with a knob at the current value, level readout beside it
            int tx1 = x + w - 4;
            int tx0 = tx1 - TabletScreen.LIST_SLIDER_W;
            int ty = y + TabletScreen.ROW_HEIGHT / 2 - 2;
            String level = String.valueOf(app.strength());
            graphics.drawString(font, level, tx0 - 4 - font.width(level), nameY,
                    lit ? theme.textPrimary : theme.textMuted, theme.textShadow);
            graphics.fill(tx0, ty, tx1, ty + 4, theme.switchOff);
            int knobX = tx0 + Math.round((tx1 - tx0 - 4) * app.fillFraction());
            if (app.strength() > 0) {
                graphics.fill(tx0, ty, knobX + 2, ty + 4, theme.accentDim);
            }
            graphics.fill(knobX, ty - 3, knobX + 4, ty + 7, lit ? theme.accent : theme.textMuted);
            return name;
        }

        int sx = x + w - TabletScreen.SWITCH_W - 4;
        int sy = y + (TabletScreen.ROW_HEIGHT - TabletScreen.SWITCH_H) / 2;
        if (app.timed()) {
            // Timer button: a tiny clock face (dot + 12 and 3 o'clock
            // hands) instead of the momentary center dot; lights while
            // the tap flash runs
            graphics.fill(sx, sy, sx + TabletScreen.SWITCH_W, sy + TabletScreen.SWITCH_H,
                    held ? theme.accentDim : theme.switchOff);
            int cx = sx + TabletScreen.SWITCH_W / 2;
            int cy = sy + TabletScreen.SWITCH_H / 2;
            int hand = held ? theme.accent : theme.textMuted;
            graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, hand);
            graphics.fill(cx - 1, cy - 4, cx, cy - 1, hand);
            graphics.fill(cx + 1, cy - 1, cx + 3, cy, hand);
        } else if (app.momentary()) {
            // Push button: center dot lights while held
            graphics.fill(sx, sy, sx + TabletScreen.SWITCH_W, sy + TabletScreen.SWITCH_H,
                    held ? theme.accentDim : theme.switchOff);
            int cx = sx + TabletScreen.SWITCH_W / 2;
            int cy = sy + TabletScreen.SWITCH_H / 2;
            graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, held ? theme.accent : theme.textMuted);
        } else {
            // Toggle switch
            int track = app.active() ? theme.accentDim : theme.switchOff;
            graphics.fill(sx, sy, sx + TabletScreen.SWITCH_W, sy + TabletScreen.SWITCH_H, track);
            int knobX = app.active() ? sx + TabletScreen.SWITCH_W - 10 : sx + 2;
            graphics.fill(knobX, sy + 2, knobX + 8, sy + TabletScreen.SWITCH_H - 2,
                    app.active() ? theme.accent : theme.textMuted);
        }
        return name;
    }

    private AppRowPainter() {
    }
}
