package com.modpack.linktablet.client.screen.chrome;

import com.modpack.linktablet.client.screen.chrome.ChromeAtlas.Region;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Nine-slice blitter for {@link ChromeAtlas} regions. Corners draw 1:1;
 * edges and the center TILE (never stretch) — wood grain and parchment
 * noise smear when scaled, and 1:1 blits sidestep texture filtering
 * entirely. Partial tiles are cropped by shortening the source rect.
 */
final class NineSlice {

    /** Draws {@code region} covering {@code dw x dh} at (x, y). */
    static void blit(GuiGraphics g, Region r, int x, int y, int dw, int dh) {
        int l = r.left();
        int t = r.top();
        int rt = r.right();
        int b = r.bottom();
        if (l == 0 && t == 0 && rt == 0 && b == 0) {
            tile(g, r.u(), r.v(), r.w(), r.h(), x, y, dw, dh);
            return;
        }
        int midSrcW = r.w() - l - rt;
        int midSrcH = r.h() - t - b;
        int midDstW = dw - l - rt;
        int midDstH = dh - t - b;

        // Corners
        if (l > 0 && t > 0) piece(g, r.u(), r.v(), l, t, x, y);
        if (rt > 0 && t > 0) piece(g, r.u() + r.w() - rt, r.v(), rt, t, x + dw - rt, y);
        if (l > 0 && b > 0) piece(g, r.u(), r.v() + r.h() - b, l, b, x, y + dh - b);
        if (rt > 0 && b > 0) piece(g, r.u() + r.w() - rt, r.v() + r.h() - b, rt, b, x + dw - rt, y + dh - b);

        // Edges
        if (midDstW > 0) {
            if (t > 0) tile(g, r.u() + l, r.v(), midSrcW, t, x + l, y, midDstW, t);
            if (b > 0) tile(g, r.u() + l, r.v() + r.h() - b, midSrcW, b, x + l, y + dh - b, midDstW, b);
        }
        if (midDstH > 0) {
            if (l > 0) tile(g, r.u(), r.v() + t, l, midSrcH, x, y + t, l, midDstH);
            if (rt > 0) tile(g, r.u() + r.w() - rt, r.v() + t, rt, midSrcH, x + dw - rt, y + t, rt, midDstH);
        }

        // Center
        if (midDstW > 0 && midDstH > 0) {
            tile(g, r.u() + l, r.v() + t, midSrcW, midSrcH, x + l, y + t, midDstW, midDstH);
        }
    }

    /** Fills the destination by repeating the source rect 1:1. */
    private static void tile(GuiGraphics g, int su, int sv, int sw, int sh,
                             int dx, int dy, int dw, int dh) {
        for (int oy = 0; oy < dh; oy += sh) {
            int th = Math.min(sh, dh - oy);
            for (int ox = 0; ox < dw; ox += sw) {
                int tw = Math.min(sw, dw - ox);
                g.blit(Chrome.ATLAS, dx + ox, dy + oy, su, sv, tw, th,
                        ChromeAtlas.SIZE, ChromeAtlas.SIZE);
            }
        }
    }

    private static void piece(GuiGraphics g, int su, int sv, int sw, int sh, int dx, int dy) {
        g.blit(Chrome.ATLAS, dx, dy, su, sv, sw, sh, ChromeAtlas.SIZE, ChromeAtlas.SIZE);
    }

    private NineSlice() {
    }
}
