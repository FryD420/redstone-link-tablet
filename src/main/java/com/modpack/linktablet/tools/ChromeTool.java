package com.modpack.linktablet.tools;

import com.modpack.linktablet.client.screen.chrome.ChromeAtlas;
import com.modpack.linktablet.client.screen.chrome.ChromeAtlas.Region;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Dev-only GUI chrome-atlas generator (excluded from the jar): writes
 * {@code textures/gui/chrome.png}, the nine-slice sheet every screen
 * surface blits through {@code client/screen/chrome/Chrome}. Region
 * coordinates come from {@link ChromeAtlas} — edit art here, coords
 * there, then rerun {@code ./gradlew chromeTool} (default output path
 * baked in; F3+T reloads it in a running client).
 *
 * <p>Pixel-art rules: no antialiasing, integer rects only, per-pixel
 * hash noise (not sequential Random) so every region is byte-stable
 * and tile seams stay coherent. Tintable surfaces are near-white
 * grayscale; wood is full-color and never tinted at runtime.
 */
public final class ChromeTool {

    private static final String DEFAULT_OUT =
            "src/main/resources/assets/linktablet/textures/gui/chrome.png";

    // Wood palette (the untinted Create identity)
    private static final int WOOD_BASE = 0xFF6E5433;
    private static final int WOOD_LIGHT = 0xFF8A6D44;
    private static final int WOOD_DARK = 0xFF4A3722;
    private static final int WOOD_OUTLINE = 0xFF2E2115;

    private static BufferedImage img;

    public static void main(String[] args) throws Exception {
        img = new BufferedImage(ChromeAtlas.SIZE, ChromeAtlas.SIZE, BufferedImage.TYPE_INT_ARGB);

        canvas(ChromeAtlas.CANVAS);
        railFrame(ChromeAtlas.RAIL_FRAME);
        plaque(ChromeAtlas.PLAQUE);
        tile(ChromeAtlas.TILE);
        slot(ChromeAtlas.SLOT);
        ghostRing(ChromeAtlas.GHOST_RING);
        railH(ChromeAtlas.RAIL_H);
        railV(ChromeAtlas.RAIL_V);
        banner(ChromeAtlas.BANNER_NORMAL, 0xFFE8E8E8, 0xFFFCFCFC, 0xFFC8C8C8);
        banner(ChromeAtlas.BANNER_HOVER, 0xFFF6F6F6, 0xFFFFFFFF, 0xFFD8D8D8);
        bannerPressed(ChromeAtlas.BANNER_PRESSED);
        bannerFlat(ChromeAtlas.BANNER_DISABLED, 0xFFC8C8C8);
        inkField(ChromeAtlas.INK_FIELD);
        sliderTrack(ChromeAtlas.SLIDER_TRACK);
        sliderKnob(ChromeAtlas.SLIDER_KNOB);
        checkbox(ChromeAtlas.CHECKBOX);
        checkFill(ChromeAtlas.CHECK_FILL);

        File out = new File(args.length > 0 ? args[0] : DEFAULT_OUT);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }

    // ---- Regions ---------------------------------------------------

    /** Near-white canvas with an inset shadow ring emerging from under the rail. */
    private static void canvas(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                int d = edgeDist(r, x, y);
                int base = d <= 1 ? 0xD8 : d <= 3 ? 0xE2 : d <= 5 ? 0xEA : 0xF2;
                set(r, x, y, gray(base + (d > 5 ? noise(x, y, 3) : 0)));
            }
        }
    }

    /**
     * Wood frame, 8px painted rail inside a 12px slice border, center
     * transparent so the tinted canvas shows through. Grain runs along
     * each rail (nearest-edge rule miters the corners automatically).
     */
    private static void railFrame(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                int d = edgeDist(r, x, y);
                if (d >= 8) continue; // transparent center
                boolean horizontal = Math.min(y, r.h() - 1 - y) <= Math.min(x, r.w() - 1 - x);
                int color;
                if (d == 0) color = WOOD_OUTLINE;
                else if (d == 1) color = WOOD_LIGHT;
                else if (d == 6) color = WOOD_DARK;
                else if (d == 7) color = WOOD_OUTLINE;
                else color = grain(horizontal ? y : x);
                set(r, x, y, color);
            }
        }
    }

    /** Flat parchment plaque with a soft edge and 1px rounded corners. */
    private static void plaque(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                if (isCorner(r, x, y, 1)) continue; // rounded: corner px transparent
                int d = edgeDist(r, x, y);
                int color = d == 0 ? gray(0xC4) : d == 1 ? gray(0xE0)
                        : gray(0xF4 + noise(x, y, 3));
                set(r, x, y, color);
            }
        }
    }

    /** Raised plaque (grid app tiles): stronger bevel, button feel. */
    private static void tile(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                if (isCorner(r, x, y, 1)) continue;
                int d = edgeDist(r, x, y);
                boolean lightSide = y <= x && y < r.h() - 1 - (r.w() - 1 - x); // top/left
                int color;
                if (d == 0) color = gray(0xA0);
                else if (d == 1) color = lightSide ? 0xFFFFFFFF : gray(0xC0);
                else if (d == 2) color = lightSide ? gray(0xF8) : gray(0xD8);
                else color = gray(0xEC + noise(x, y, 2));
                set(r, x, y, color);
            }
        }
    }

    /** Vanilla-style inset slot cell; the light edge is pure white so the tint shows exactly. */
    private static void slot(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                boolean top = y == 0;
                boolean left = x == 0;
                boolean bottom = y == r.h() - 1;
                boolean right = x == r.w() - 1;
                int color;
                if ((top && right) || (bottom && left)) color = gray(0xB4); // vanilla corner mids
                else if (top || left) color = gray(0x5A);
                else if (bottom || right) color = 0xFFFFFFFF;
                else color = gray(0xB4);
                set(r, x, y, color);
            }
        }
    }

    /** White ring + faint interior wash; tinted by the staging colors. */
    private static void ghostRing(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                set(r, x, y, edgeDist(r, x, y) == 0 ? 0xFFFFFFFF : 0x18FFFFFF);
            }
        }
    }

    private static void railH(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                int color;
                if (y == 0 || y == r.h() - 1 || x == 0 || x == r.w() - 1) color = WOOD_OUTLINE;
                else if (y == 1) color = WOOD_LIGHT;
                else if (y == r.h() - 2) color = WOOD_DARK;
                else color = grain(y);
                set(r, x, y, color);
            }
        }
    }

    private static void railV(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                int color;
                if (x == 0 || x == r.w() - 1 || y == 0 || y == r.h() - 1) color = WOOD_OUTLINE;
                else if (x == 1) color = WOOD_LIGHT;
                else if (x == r.w() - 2) color = WOOD_DARK;
                else color = grain(x);
                set(r, x, y, color);
            }
        }
    }

    /** Chamfered banner button: highlight ridge on top, shadow ridge below. */
    private static void banner(Region r, int body, int highlight, int ridge) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                Integer c = bannerShape(r, x, y);
                if (c == null) continue;
                int color;
                if (c == 1) color = gray(0x38); // outline
                else if (y == 1) color = highlight;
                else if (y == r.h() - 3) color = ridge;
                else if (y == r.h() - 2) color = gray(0xB4);
                else color = body;
                set(r, x, y, color);
            }
        }
    }

    /** Pressed: inverted bevel (shadow on top, light on the bottom). */
    private static void bannerPressed(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                Integer c = bannerShape(r, x, y);
                if (c == null) continue;
                int color;
                if (c == 1) color = gray(0x38);
                else if (y == 1) color = gray(0xB4);
                else if (y >= r.h() - 3) color = gray(0xF0);
                else color = gray(0xDC);
                set(r, x, y, color);
            }
        }
    }

    /** Disabled: flat body, no ridges. */
    private static void bannerFlat(Region r, int body) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                Integer c = bannerShape(r, x, y);
                if (c == null) continue;
                set(r, x, y, c == 1 ? gray(0x70) : body);
            }
        }
    }

    /**
     * Banner silhouette with 2px chamfered corners: {@code null} =
     * transparent, 1 = outline, 0 = interior.
     */
    private static Integer bannerShape(Region r, int x, int y) {
        int dx = Math.min(x, r.w() - 1 - x);
        int dy = Math.min(y, r.h() - 1 - y);
        int diag = dx + dy;
        if (diag < 2) return null;          // chamfer cut
        if (diag == 2) return 1;            // diagonal outline
        if (dx == 0 || dy == 0) return 1;   // straight outline
        return 0;
    }

    /** Dark recessed ink-well text field; untinted on every theme. */
    private static void inkField(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                int d = edgeDist(r, x, y);
                boolean lightSide = y <= x && y < r.h() - 1 - (r.w() - 1 - x);
                int color;
                if (d == 0) color = 0xFF25272C;
                else if (d == 1) color = lightSide ? 0xFF07080A : 0xFF34383F;
                else color = 0xFF14161A;
                set(r, x, y, color);
            }
        }
    }

    /** Inset groove with rounded end caps. */
    private static void sliderTrack(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                if (isCorner(r, x, y, 1)) continue;
                int color;
                if (y == 0 || x == 0 || x == r.w() - 1) color = gray(0x90);
                else if (y == r.h() - 1) color = 0xFFFFFFFF;
                else if (y == r.h() - 2) color = gray(0xF4);
                else color = gray(0xE4);
                set(r, x, y, color);
            }
        }
    }

    /** Grip knob with a center ridge. */
    private static void sliderKnob(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                if (isCorner(r, x, y, 1)) continue;
                int color;
                if (edgeDist(r, x, y) == 0) color = gray(0x40);
                else if (x == 1) color = 0xFFFFFFFF;
                else if (x == r.w() - 2) color = gray(0xC8);
                else if ((x == 3 || x == 4) && y >= 3 && y <= r.h() - 4) color = gray(0xC4);
                else color = gray(0xF0);
                set(r, x, y, color);
            }
        }
    }

    /** Inset checkbox frame. */
    private static void checkbox(Region r) {
        for (int y = 0; y < r.h(); y++) {
            for (int x = 0; x < r.w(); x++) {
                int d = edgeDist(r, x, y);
                boolean lightSide = y <= x && y < r.h() - 1 - (r.w() - 1 - x);
                int color;
                if (d == 0) color = gray(0x60);
                else if (d == 1) color = lightSide ? gray(0x50) : 0xFFFFFFFF;
                else color = gray(0xC8);
                set(r, x, y, color);
            }
        }
    }

    /** Centered 6x6 white mark; everything else transparent. */
    private static void checkFill(Region r) {
        for (int y = 3; y < 9; y++) {
            for (int x = 3; x < 9; x++) {
                set(r, x, y, 0xFFFFFFFF);
            }
        }
    }

    // ---- Helpers ---------------------------------------------------

    private static void set(Region r, int x, int y, int argb) {
        img.setRGB(r.u() + x, r.v() + y, argb);
    }

    /** Chebyshev-ish distance to the nearest region edge. */
    private static int edgeDist(Region r, int x, int y) {
        return Math.min(Math.min(x, r.w() - 1 - x), Math.min(y, r.h() - 1 - y));
    }

    /** True in an n-px square at each corner (1px rounding). */
    private static boolean isCorner(Region r, int x, int y, int n) {
        return Math.min(x, r.w() - 1 - x) < n && Math.min(y, r.h() - 1 - y) < n;
    }

    private static int gray(int v) {
        int c = Math.max(0, Math.min(0xFF, v));
        return 0xFF000000 | (c << 16) | (c << 8) | c;
    }

    /** Row/column wood grain: per-line jitter so tiled edges stay seamless. */
    private static int grain(int line) {
        int j = hash(line, 0x9E37) % 11 - 5;
        int rr = clamp8(((WOOD_BASE >> 16) & 0xFF) + j);
        int gg = clamp8(((WOOD_BASE >> 8) & 0xFF) + j);
        int bb = clamp8((WOOD_BASE & 0xFF) + j);
        return 0xFF000000 | (rr << 16) | (gg << 8) | bb;
    }

    /** Deterministic per-pixel noise in [-amp, amp]. */
    private static int noise(int x, int y, int amp) {
        return hash(x * 0x1F1F + y, 0x1517) % (2 * amp + 1) - amp;
    }

    private static int hash(int v, int seed) {
        int h = v * 0x9E3779B9 + seed;
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h & 0x7FFFFFFF;
    }

    private static int clamp8(int v) {
        return Math.max(0, Math.min(0xFF, v));
    }

    private ChromeTool() {
    }
}
