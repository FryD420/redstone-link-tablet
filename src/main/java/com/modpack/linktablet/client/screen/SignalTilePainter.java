package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The grid tile's base look (glow/hover border, themed plaque, inset
 * color chip, name label), extracted from TabletScreen.renderSignalTile
 * (1.10.0) the way {@link SignalRowPainter} owns the list rows — the signal
 * grid and the {@link LauncherScreen} draw pixel-identical tiles. Pure
 * painter: the per-type content (icons, pips, value bars, badges,
 * note glyph) stays with the caller.
 */
final class SignalTilePainter {

    /** Hover border shade shared by both grids (pre-1.10.0 literal). */
    static final int HOVER_BORDER = 0xFF5A6070;

    /** Chip inset inside a tile — 1/6 of the span, like the list chip's
     * 4px-in-24 margin (moved here from TabletScreen with the painter). */
    static final int CHIP_INSET = 7;

    /**
     * Paints the tile base at (x, y, size²): lit tiles glow accent,
     * hovered ones get the neutral border, and the signal/program color
     * lands as an inset chip on the themed plaque.
     */
    static void base(GuiGraphics graphics, ScreenTheme theme, int x, int y, int size,
                     int chipColor, boolean lit, boolean hovered) {
        if (lit) {
            graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, theme.accent);
        } else if (hovered) {
            graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, HOVER_BORDER);
        }
        Chrome.tile(graphics, x, y, size, size, hovered ? theme.rowBgHover : theme.rowBg);
        graphics.fill(x + CHIP_INSET, y + CHIP_INSET,
                x + size - CHIP_INSET, y + size - CHIP_INSET, chipColor);
    }

    /**
     * Centered name label under a tile. Returns the name as drawn
     * (possibly ellipsized) so the caller can decide on a tooltip.
     */
    static String label(GuiGraphics graphics, Font font, ScreenTheme theme, String name,
                        int x, int y, int size, int gap) {
        String drawn = TextFit.ellipsize(font, name, size + gap - 2);
        graphics.drawString(font, drawn, x + size / 2 - font.width(drawn) / 2, y + size + 3,
                theme.textPrimary, theme.textShadow);
        return drawn;
    }

    private SignalTilePainter() {
    }
}
