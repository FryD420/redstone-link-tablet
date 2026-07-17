package com.modpack.linktablet.client.screen.chrome;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.theme.ScreenTheme;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The 1.5.0 Create-style GUI chrome: every screen SURFACE (panels,
 * plaques, rails, banner buttons, slots, tracks) blits regions of one
 * atlas, {@code textures/gui/chrome.png}, through these helpers.
 * Regions and their coordinates live in {@link ChromeAtlas} — shared
 * with the dev-only generator ({@code ./gradlew chromeTool}) so art and
 * blits can't drift.
 *
 * <p>Tinting is a shader-color multiply ({@link GuiGraphics#setColor}):
 * the atlas surfaces are authored near-white grayscale so a theme role
 * or app color multiplied in becomes the surface color. Multiplying can
 * only darken — "light" accents in the art are pure white so they take
 * the tint exactly. Rail frames tint with {@code theme.bodyOuter} (the
 * pre-1.5.0 border role), so borders match the theme like they always
 * did; only the ink field draws untinted.
 *
 * <p>Mechanisms stay procedural {@code fill()} on purpose (switches,
 * pips, glyphs, value bars, swatches): the Stock-Keeper identity lives
 * in the surfaces, and indicators need exact theme colors, not tinted
 * art.
 *
 * <p>The ink-well text field is deliberately a fixed DARK surface on
 * every theme: vanilla {@code EditBox} hardcodes shadowed text, and
 * dark-on-parchment shadowed text smears — light text in a dark recess
 * is shadow-safe everywhere (the one deviation from Create's cream
 * search bar).
 */
public final class Chrome {

    public static final ResourceLocation ATLAS =
            ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID, "textures/gui/chrome.png");

    public enum ButtonState {NORMAL, HOVER, PRESSED, DISABLED}

    /** Canvas (tinted {@code theme.bodyInner}) under a rail frame tinted {@code theme.bodyOuter}. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, ScreenTheme theme) {
        tinted(g, theme.bodyInner, () -> NineSlice.blit(g, ChromeAtlas.CANVAS, x + 4, y + 4, w - 8, h - 8));
        tinted(g, theme.bodyOuter, () -> NineSlice.blit(g, ChromeAtlas.RAIL_FRAME, x, y, w, h));
    }

    /** Flat parchment plaque; tint = theme role or app color. */
    public static void plaque(GuiGraphics g, int x, int y, int w, int h, int argbTint) {
        tinted(g, argbTint, () -> NineSlice.blit(g, ChromeAtlas.PLAQUE, x, y, w, h));
    }

    /** Raised plaque for grid app tiles; tint = app color. */
    public static void tile(GuiGraphics g, int x, int y, int w, int h, int argbTint) {
        tinted(g, argbTint, () -> NineSlice.blit(g, ChromeAtlas.TILE, x, y, w, h));
    }

    /** Horizontal rail (divider), 6px tall; tint = {@code theme.bodyOuter}. */
    public static void railH(GuiGraphics g, int x, int y, int w, int argbTint) {
        tinted(g, argbTint, () -> NineSlice.blit(g, ChromeAtlas.RAIL_H, x, y, w, ChromeAtlas.RAIL_H.h()));
    }

    /** Vertical rail, 6px wide; tint = {@code theme.bodyOuter}. */
    public static void railV(GuiGraphics g, int x, int y, int h, int argbTint) {
        tinted(g, argbTint, () -> NineSlice.blit(g, ChromeAtlas.RAIL_V, x, y, ChromeAtlas.RAIL_V.w(), h));
    }

    /** Chamfered banner button body; tint = {@code rowBg}/{@code rowBgHover}. */
    public static void bannerButton(GuiGraphics g, int x, int y, int w, int h,
                                    ButtonState state, int argbTint) {
        ChromeAtlas.Region region = switch (state) {
            case NORMAL -> ChromeAtlas.BANNER_NORMAL;
            case HOVER -> ChromeAtlas.BANNER_HOVER;
            case PRESSED -> ChromeAtlas.BANNER_PRESSED;
            case DISABLED -> ChromeAtlas.BANNER_DISABLED;
        };
        tinted(g, argbTint, () -> NineSlice.blit(g, region, x, y, w, h));
    }

    /** Vanilla-style inset slot cell, 18x18; tint = {@code theme.rowBg}. */
    public static void slot(GuiGraphics g, int x, int y, int argbTint) {
        tinted(g, argbTint, () -> NineSlice.blit(g, ChromeAtlas.SLOT, x, y,
                ChromeAtlas.SLOT.w(), ChromeAtlas.SLOT.h()));
    }

    /** Frequency ghost-slot ring, 20x20; tint = staging red/blue. */
    public static void ghostRing(GuiGraphics g, int x, int y, int argbTint) {
        tinted(g, argbTint, () -> NineSlice.blit(g, ChromeAtlas.GHOST_RING, x, y,
                ChromeAtlas.GHOST_RING.w(), ChromeAtlas.GHOST_RING.h()));
    }

    /** Dark recessed text-field surface; untinted on every theme. */
    public static void inkField(GuiGraphics g, int x, int y, int w, int h) {
        untinted(g, () -> NineSlice.blit(g, ChromeAtlas.INK_FIELD, x, y, w, h));
    }

    /** Slider groove, 6px tall. */
    public static void sliderTrack(GuiGraphics g, int x, int y, int w, int argbTint) {
        tinted(g, argbTint, () -> NineSlice.blit(g, ChromeAtlas.SLIDER_TRACK, x, y,
                w, ChromeAtlas.SLIDER_TRACK.h()));
    }

    /**
     * Filled span of a slider groove: the same 3-slice drawn in the fill
     * tint, scissored to {@code fillX..fillX+fillW} so the end caps stay
     * crisp. {@code x/w} are the FULL track bounds.
     */
    public static void sliderFill(GuiGraphics g, int x, int y, int w,
                                  int fillX, int fillW, int argbTint) {
        if (fillW <= 0) return;
        g.enableScissor(fillX, y, fillX + fillW, y + ChromeAtlas.SLIDER_TRACK.h());
        tinted(g, argbTint, () -> NineSlice.blit(g, ChromeAtlas.SLIDER_TRACK, x, y,
                w, ChromeAtlas.SLIDER_TRACK.h()));
        g.disableScissor();
    }

    /** Slider knob, 8x12. */
    public static void sliderKnob(GuiGraphics g, int x, int y, int argbTint) {
        tinted(g, argbTint, () -> NineSlice.blit(g, ChromeAtlas.SLIDER_KNOB, x, y,
                ChromeAtlas.SLIDER_KNOB.w(), ChromeAtlas.SLIDER_KNOB.h()));
    }

    /** Inset checkbox, 12x12; the mark tints with {@code theme.accent}. */
    public static void checkbox(GuiGraphics g, int x, int y, boolean checked, ScreenTheme theme) {
        tinted(g, theme.switchOff, () -> NineSlice.blit(g, ChromeAtlas.CHECKBOX, x, y,
                ChromeAtlas.CHECKBOX.w(), ChromeAtlas.CHECKBOX.h()));
        if (checked) {
            tinted(g, theme.accent, () -> NineSlice.blit(g, ChromeAtlas.CHECK_FILL, x, y,
                    ChromeAtlas.CHECK_FILL.w(), ChromeAtlas.CHECK_FILL.h()));
        }
    }

    // ---- Tint plumbing ----------------------------------------------

    /** THE tint mechanism: shader-color multiply around plain blits; always resets. */
    private static void tinted(GuiGraphics g, int argb, Runnable draw) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, (argb >>> 24) / 255f);
        draw.run();
        g.setColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }

    private static void untinted(GuiGraphics g, Runnable draw) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        draw.run();
        RenderSystem.disableBlend();
    }

    private Chrome() {
    }
}
