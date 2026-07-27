package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import com.modpack.linktablet.client.screen.chrome.Chrome;

/**
 * The header-row button glyphs (procedural 12×12 pixel art) and the
 * theme dropdown, extracted from TabletScreen (1.10.0) the way
 * {@link SignalRowPainter} owns the list rows — the signals screen and
 * the {@link LauncherScreen} draw pixel-identical chrome. Pure painter:
 * layout math, hover state, and click handling stay with the callers.
 */
final class HeaderGlyphs {

    // Theme dropdown: one row per theme (swatches + name), below the button
    static final int THEME_ROW_H = 14;
    static final int THEME_POPUP_W = 96;

    /** Home: little house — roof, hollow body, door gap. */
    static void home(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 5, y + 1, x + 7, y + 2, color);
        g.fill(x + 3, y + 2, x + 9, y + 3, color);
        g.fill(x + 1, y + 3, x + 11, y + 4, color);
        g.fill(x + 2, y + 4, x + 5, y + 11, color);
        g.fill(x + 7, y + 4, x + 10, y + 11, color);
        g.fill(x + 5, y + 4, x + 7, y + 7, color);
    }

    /** Rearrange: up/down arrow pair. */
    static void reorder(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 2, y + 2, x + 4, y + 3, color);
        g.fill(x + 1, y + 3, x + 5, y + 4, color);
        g.fill(x + 2, y + 4, x + 4, y + 10, color);
        g.fill(x + 8, y + 2, x + 10, y + 8, color);
        g.fill(x + 7, y + 8, x + 11, y + 9, color);
        g.fill(x + 8, y + 9, x + 10, y + 10, color);
    }

    /** Grid view: four small squares. */
    static void grid(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 1, x + 5, y + 5, color);
        g.fill(x + 7, y + 1, x + 11, y + 5, color);
        g.fill(x + 1, y + 7, x + 5, y + 11, color);
        g.fill(x + 7, y + 7, x + 11, y + 11, color);
    }

    /** List view: three bars. */
    static void list(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 2, x + 11, y + 4, color);
        g.fill(x + 1, y + 5, x + 11, y + 7, color);
        g.fill(x + 1, y + 8, x + 11, y + 10, color);
    }

    /** Theme: frame + three color dots (a tiny palette). */
    static void themePalette(GuiGraphics g, int x, int y, int frame) {
        g.fill(x + 1, y + 1, x + 11, y + 2, frame);
        g.fill(x + 1, y + 10, x + 11, y + 11, frame);
        g.fill(x + 1, y + 2, x + 2, y + 10, frame);
        g.fill(x + 10, y + 2, x + 11, y + 10, frame);
        g.fill(x + 3, y + 3, x + 6, y + 6, 0xFF4ADE80);
        g.fill(x + 6, y + 6, x + 9, y + 9, 0xFFB07CFF);
        g.fill(x + 3, y + 6, x + 6, y + 9, 0xFF3AB3DA);
    }

    /** Pushpin: head, flange, needle. */
    static void pin(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 4, y + 1, x + 8, y + 3, color);
        g.fill(x + 5, y + 3, x + 7, y + 6, color);
        g.fill(x + 2, y + 6, x + 10, y + 8, color);
        g.fill(x + 5, y + 8, x + 7, y + 11, color);
    }

    /** Chain links — joined by a bar unless solo (then broken apart). */
    static void link(GuiGraphics g, int x, int y, int color, boolean solo) {
        g.fill(x + 1, y + 3, x + 5, y + 4, color);
        g.fill(x + 1, y + 8, x + 5, y + 9, color);
        g.fill(x + 1, y + 4, x + 2, y + 8, color);
        g.fill(x + 4, y + 4, x + 5, y + 8, color);
        g.fill(x + 7, y + 3, x + 11, y + 4, color);
        g.fill(x + 7, y + 8, x + 11, y + 9, color);
        g.fill(x + 7, y + 4, x + 8, y + 8, color);
        g.fill(x + 10, y + 4, x + 11, y + 8, color);
        if (!solo) {
            g.fill(x + 4, y + 5, x + 8, y + 7, color);
        }
    }

    /**
     * Theme dropdown, z-lifted above the batched content like the edit
     * screen's color swatches. Row hit math stays with the caller:
     * {@code (mouseY - py) / THEME_ROW_H} over
     * {@code THEME_POPUP_W × values().length * THEME_ROW_H}.
     */
    static void themePopup(GuiGraphics g, Font font, int px, int py,
                           ScreenTheme current, int mouseX, int mouseY) {
        ScreenTheme[] themes = ScreenTheme.values();
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        Chrome.panel(g, px - 8, py - 8, THEME_POPUP_W + 16, themes.length * THEME_ROW_H + 16,
                current);
        for (int i = 0; i < themes.length; i++) {
            ScreenTheme t = themes[i];
            int y = py + i * THEME_ROW_H;
            boolean hovered = mouseX >= px && mouseX < px + THEME_POPUP_W
                    && mouseY >= y && mouseY < y + THEME_ROW_H;
            if (t == current || hovered) {
                g.fill(px, y, px + THEME_POPUP_W, y + THEME_ROW_H,
                        t == current ? current.rowBgHover : current.rowBg);
            }
            // Swatch trio: panel, accent, text — the theme at a glance
            g.fill(px + 2, y + 3, px + 10, y + 11, t.screenBgLit);
            g.fill(px + 12, y + 3, px + 20, y + 11, t.accent);
            g.fill(px + 22, y + 3, px + 30, y + 11, t.textPrimary);
            g.drawString(font, t.displayName(), px + 34, y + 3,
                    t == current ? current.accent : current.textPrimary, current.textShadow);
        }
        g.pose().popPose();
    }

    private HeaderGlyphs() {
    }
}
