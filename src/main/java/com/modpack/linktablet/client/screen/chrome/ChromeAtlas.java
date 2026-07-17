package com.modpack.linktablet.client.screen.chrome;

/**
 * Region map for {@code textures/gui/chrome.png} (256x256) — the ONLY
 * place atlas coordinates live. The runtime ({@link Chrome}) and the
 * dev-only generator ({@code tools/ChromeTool}) both read these
 * constants, so the art and the blits can never drift. Deliberately
 * free of Minecraft imports so the generator can run standalone.
 *
 * <p>Authoring rules (enforced by ChromeTool):
 * <ul>
 *   <li>Tintable surfaces — rails included — are near-white grayscale;
 *       the runtime multiplies theme/app colors in via shader color,
 *       which can only darken. "Light" accents are authored at full
 *       white so they take the tint color exactly.</li>
 *   <li>Only the ink field ships real colors (drawn untinted — it is
 *       deliberately dark on every theme).</li>
 *   <li>Space below v=120 is reserved for future regions.</li>
 * </ul>
 */
public final class ChromeAtlas {

    /** Atlas texture dimensions. */
    public static final int SIZE = 256;

    /**
     * One atlas region. {@code left/top/right/bottom} are nine-slice
     * borders (corners drawn 1:1, edges and center tiled); all zero
     * means the region is only ever drawn 1:1.
     */
    public record Region(int u, int v, int w, int h,
                         int left, int top, int right, int bottom) {
        /** 1:1 region — never scaled or tiled. */
        public static Region fixed(int u, int v, int w, int h) {
            return new Region(u, v, w, h, 0, 0, 0, 0);
        }

        /** Uniform nine-slice border. */
        public static Region sliced(int u, int v, int w, int h, int border) {
            return new Region(u, v, w, h, border, border, border, border);
        }

        /** Horizontal three-slice (fixed height, tiling width). */
        public static Region slicedX(int u, int v, int w, int h, int border) {
            return new Region(u, v, w, h, border, 0, border, 0);
        }

        /** Vertical three-slice (fixed width, tiling height). */
        public static Region slicedY(int u, int v, int w, int h, int border) {
            return new Region(u, v, w, h, 0, border, 0, border);
        }
    }

    /** Panel background canvas; tinted with {@code theme.bodyInner}. */
    public static final Region CANVAS = Region.sliced(0, 0, 24, 24, 8);
    /** Rail frame with a transparent center; tinted {@code theme.bodyOuter}, drawn over CANVAS. */
    public static final Region RAIL_FRAME = Region.sliced(32, 0, 48, 48, 12);
    /** Parchment plaque; tinted by a theme role or an app color. */
    public static final Region PLAQUE = Region.sliced(96, 0, 24, 24, 6);
    /** Raised plaque for grid app tiles; tinted by the app color. */
    public static final Region TILE = Region.sliced(128, 0, 24, 24, 5);
    /** Inventory slot cell (vanilla-style inset); tinted with {@code theme.rowBg}. */
    public static final Region SLOT = Region.fixed(160, 0, 18, 18);
    /** Frequency ghost-slot ring; tinted with the red/blue staging colors. */
    public static final Region GHOST_RING = Region.fixed(184, 0, 20, 20);
    /** Standalone horizontal rail (dividers); tinted {@code theme.bodyOuter}. */
    public static final Region RAIL_H = Region.slicedX(208, 0, 24, 6, 6);
    /** Standalone vertical rail; tinted {@code theme.bodyOuter}. */
    public static final Region RAIL_V = Region.slicedY(240, 0, 6, 24, 6);

    /** Banner button states; tinted with {@code theme.rowBg}/{@code rowBgHover}.
     * Asymmetric slice: 3px top (outline+highlight) / 4px bottom
     * (ridge+shadow+outline) so shorter buttons keep both ridges, and the
     * 2px corner chamfers stay whole inside the 6-wide corners. */
    public static final Region BANNER_NORMAL = new Region(0, 56, 24, 20, 6, 3, 6, 4);
    public static final Region BANNER_HOVER = new Region(32, 56, 24, 20, 6, 3, 6, 4);
    public static final Region BANNER_PRESSED = new Region(64, 56, 24, 20, 6, 3, 6, 4);
    public static final Region BANNER_DISABLED = new Region(96, 56, 24, 20, 6, 3, 6, 4);
    /** Dark recessed text-field surface; untinted on every theme. */
    public static final Region INK_FIELD = Region.sliced(128, 56, 24, 18, 4);

    /** Slider groove; tinted (track base and fill are separate blits). */
    public static final Region SLIDER_TRACK = Region.slicedX(0, 96, 24, 6, 3);
    /** Slider knob; tinted. */
    public static final Region SLIDER_KNOB = Region.fixed(32, 96, 8, 12);
    /** Inset checkbox frame; tinted. */
    public static final Region CHECKBOX = Region.fixed(48, 96, 12, 12);
    /** Centered check mark overlay; tinted with {@code theme.accent}. */
    public static final Region CHECK_FILL = Region.fixed(64, 96, 12, 12);

    private ChromeAtlas() {
    }
}
