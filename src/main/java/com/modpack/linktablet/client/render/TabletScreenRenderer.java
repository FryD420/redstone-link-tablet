package com.modpack.linktablet.client.render;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletScreenMath;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.TwitchChatService;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.theme.ScreenTheme;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Draws the tablet's live mini screen: a background over the glass area
 * and the first visible signals as either icon pips (grid) or icon +
 * mini-switch rows (list), echoing the GUI's two layouts. The grid sizes
 * itself to the signal count ({@link TabletScreenMath#gridLayout}) — one signal
 * fills the glass, tiles shrink as signals are added. Shared by the block
 * entity renderer and the item renderer — the caller positions the
 * PoseStack so that screen-local texel {@code (u, v)} maps to local
 * {@code (u/16, layer, v/16)} with the screen normal on +Y.
 */
public final class TabletScreenRenderer {

    private static final ResourceLocation WHITE_TEX =
            ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID, "textures/misc/white.png");
    private static final RenderType SCREEN_TYPE = RenderType.entityCutoutNoCull(WHITE_TEX);

    /** Vertical spacing between stacked layers (block units). Kept as
     * tight as depth precision allows so the screen reads as one flat
     * surface rather than stacked plates. */
    private static final float LAYER = 0.001f;

    /**
     * Uniform breathing room: the same margin against the bezel and gap
     * between entries, everywhere. Tile sizes fall out of the glass
     * dimensions and the count-driven grid; click cells divide the glass
     * evenly and always contain their tile. The values live in
     * TabletScreenMath — slider drags map against this exact geometry.
     */
    private static final float SPACE = TabletScreenMath.SPACE;

    private static float tileSize(float span, int n) {
        return TabletScreenMath.tileSize(span, n);
    }

    private static float tileU0(int col, float tileW) {
        return TabletScreenMath.tileU0(col, tileW);
    }

    private static float tileV0(int row, float tileH) {
        return TabletScreenMath.tileV0(row, tileH);
    }

    /** List rows split the (rotation-dependent) glass height five ways. */
    private static float listRowH(float glassH) {
        return tileSize(glassH, TabletScreenMath.LIST_ROWS);
    }

    private static float listV0(int row, float rowH) {
        return tileV0(row, rowH);
    }

    /** Hollow-ring (momentary) thickness in texels at the densest grid. */
    private static final float RING = 0.5f;

    /** Icon fraction of the tile's short side; the reference tile is the
     * densest grid's 2.1-texel cell, where icons render 1.75 texels — the
     * same as the fixed-grid look this replaced. */
    private static final float REFERENCE_TILE = 2.1f;
    private static final float ICON_FRAC = 1.75f / REFERENCE_TILE;
    private static final float MOMENTARY_ICON_FRAC = 1.25f / 1.75f;
    private static final float ICON_MIN = 1.25f;
    private static final float ICON_MAX = 6f;

    // Big-tile name labels (grids of up to 4 cells)
    private static final int LABEL_CELLS_MAX = 4;
    private static final float LABEL_GAP = 0.5f;
    private static final float FONT_LINE = 9f;

    // List row mini-switch, matching the GUI toggle's proportions
    // (GUI: 22×12 switch in a 24px row, 8-wide knob inset 2px)
    private static final float SWITCH_W = TabletScreenMath.LIST_SWITCH_W;
    private static final float SWITCH_H = 1.1f;
    /** Right inset from the row edge — the GUI's 4px-in-24 margin, scaled. */
    private static final float SWITCH_MARGIN = TabletScreenMath.LIST_SWITCH_MARGIN;
    private static final float KNOB_W = 0.75f;
    private static final float KNOB_INSET = 0.2f;
    private static final float LIST_ICON = 1.75f;
    private static final float LIST_TEXT_H = 0.8f;

    private TabletScreenRenderer() {}

    /**
     * Kiosk launcher face (1.10.0): one synthetic tile per home-roster
     * program, drawn through the exact signal-grid pipeline — the same
     * layout table the server hit-test reads via the home list's size,
     * so there is no separate launcher geometry to drift. Tiles pose as
     * active toggles (bright chip, accent outline, full-bright icon) so
     * they read as lit app icons. Rebuilt per call: labels go stale on
     * language switch, rosters change from the drawer.
     */
    public static List<Signal> launcherTiles(List<com.modpack.linktablet.api.TabletProgram> home) {
        List<Signal> tiles = new java.util.ArrayList<>(home.size());
        for (com.modpack.linktablet.api.TabletProgram program : home) {
            tiles.add(new Signal(
                    program.displayName().getString(),
                    List.of(), true, false, Signal.MAX_STRENGTH, program.chipColor(),
                    java.util.Optional.ofNullable(program.iconItem()),
                    false, 0, Signal.MAX_STRENGTH, "", false, Signal.MIN_PULSE_TICKS,
                    0, List.of()));
        }
        return tiles;
    }

    /**
     * @param list     the tablet's stored screen layout (switch list vs
     *                 pip grid)
     * @param rot      screen content rotation, quarter turns CW (0–3) —
     *                 must match the {@code rot} the hit-test uses
     * @param theme    the tablet's stored UI theme
     * @param backlit  true when the screen's emissive state is on (block
     *                 LIT / item GUI-open) — brightens the background
     * @param packedLight world light used for non-glowing parts
     * @param heldPips indices of momentary signals currently held down —
     *                 those pips render lit
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers,
                              List<Signal> signals, boolean list, int rot, ScreenTheme theme,
                              boolean backlit, int packedLight, Set<Integer> heldPips) {
        render(poseStack, buffers, signals, list, rot, theme, backlit, packedLight, heldPips,
                1, 1, 0, false);
    }

    /** Pre-plainRows signature kept for the signal call sites. */
    public static void render(PoseStack poseStack, MultiBufferSource buffers,
                              List<Signal> signals, boolean list, int rot, ScreenTheme theme,
                              boolean backlit, int packedLight, Set<Integer> heldPips,
                              int surfaceW, int surfaceH, int caseTint) {
        render(poseStack, buffers, signals, list, rot, theme, backlit, packedLight, heldPips,
                surfaceW, surfaceH, caseTint, false);
    }

    /** Default (undyed) case tint — mirrors ClientSetup's block color. */
    public static final int DEFAULT_CASE_TINT = 0xFF383C45;

    /**
     * Surface-aware entry (1.7.0): a merged w×h surface draws every
     * member's glass at a texel offset of (16·bx, 16·by) under ONE pose —
     * one block is exactly 16 texels, so no per-member pose pushes are
     * needed and the strict three-pass order holds across the whole
     * surface by construction. Content rotation only exists at 1×1
     * (surfaces clamp to 0; see TabletBlockEntity.effectiveRotation).
     */
    /**
     * @param plainRows list rows without the switch/knob mechanism —
     *                  the kiosk LAUNCHER face's list mode (1.10.0 test
     *                  pass 2: rows are doors, not toggles). Same
     *                  geometry as signal rows, so the hit-test stays
     *                  shared.
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers,
                              List<Signal> signals, boolean list, int rot, ScreenTheme theme,
                              boolean backlit, int packedLight, Set<Integer> heldPips,
                              int surfaceW, int surfaceH, int caseTint, boolean plainRows) {
        ScreenBase base = beginScreen(poseStack, buffers, rot, theme, backlit,
                packedLight, surfaceW, surfaceH, caseTint);
        VertexConsumer vc = base.vc();
        PoseStack.Pose pose = base.pose();
        float glassW = base.glassW();
        float glassH = base.glassH();
        float u1 = base.u1();
        float v1 = base.v1();
        int bgLight = base.bgLight();

        int count = TabletScreenMath.visibleSignals(signals.size(), list, surfaceW, surfaceH);
        TabletScreenMath.SurfaceLayout sl =
                TabletScreenMath.surfaceLayout(signals.size(), surfaceW, surfaceH, rot);
        int cols = sl.cols();
        float rowH = tileSize(glassH, TabletScreenMath.LIST_ROWS * surfaceH);
        float tileW = tileSize(glassW, cols);
        float tileH = tileSize(glassH, sl.rows());
        // Centered sparse block: place tiles in a usedCols-wide block
        // centered on the glass — the hit-test applies the same offsets
        int usedCols = TabletScreenMath.usedCols(count, cols);
        float offU = (cols - usedCols) * (tileW + SPACE) / 2f;
        float offV = (sl.rows() - TabletScreenMath.usedRows(count, cols, sl.rows()))
                * (tileH + SPACE) / 2f;
        if (!list && count == 1) {
            // Lone tile: the bespoke centered square (loneTileRect is
            // the one source — hit tail and slider bar read it too).
            // Redirecting tileW/tileH/offU/offV here feeds every pass
            // below without touching their placement expressions.
            float[] lone = TabletScreenMath.loneTileRect(glassW, glassH);
            tileW = lone[2];
            tileH = lone[2];
            offU = lone[0] - (TabletScreenMath.GLASS_U0 + SPACE);
            offV = lone[1] - (TabletScreenMath.GLASS_V0 + SPACE);
        }
        boolean labels = !list && sl.k() * sl.m() <= LABEL_CELLS_MAX;
        float textH = labels ? Mth.clamp(tileH * 0.16f, 1.1f, 2f) : 0f;
        // Bottom strip of each big tile reserved for the name label
        float labelZone = labels ? textH + LABEL_GAP : 0f;
        float ringW = Mth.clamp(Math.min(tileW, tileH) * (RING / REFERENCE_TILE), RING, 1.25f);

        // Strict pass order — all quads, then all icons, then all text.
        // An icon's model (or the font) may need a render type that shares
        // the fallback buffer, which ends any in-progress shared batch;
        // interleaving would leave our quad buffer dead mid-loop
        // ("Not building!" crash with modded icons).
        for (int i = 0; i < count; i++) {
            Signal signal = signals.get(i);
            int color = signal.color() | 0xFF000000;
            boolean held = heldPips.contains(i);
            if (list) {
                renderSwitchRow(pose, vc, listV0(i, rowH), signal, color, theme,
                        packedLight, held, rowH,
                        TabletScreenMath.GLASS_U0 + SPACE, u1 - SPACE, plainRows);
            } else {
                float u0 = offU + tileU0(i % usedCols, tileW);
                float v0 = offV + tileV0(i / usedCols, tileH);
                renderPip(pose, vc, u0, v0, u0 + tileW, v0 + tileH, signal, color, theme,
                        packedLight, ringW, held, labelZone);
            }
        }
        for (int i = 0; i < count; i++) {
            Signal signal = signals.get(i);
            boolean glowing = (signal.active() && !signal.momentary()) || heldPips.contains(i);
            int light = glowing ? LightTexture.FULL_BRIGHT : packedLight;
            float cu, cv, size;
            if (list) {
                cu = TabletScreenMath.GLASS_U0 + SPACE + rowH / 2f;
                cv = listV0(i, rowH) + rowH / 2f;
                // Icon fills the inset chip exactly, like the GUI's 16px-on-16px
                size = Math.min(LIST_ICON, rowH * 2f / 3f);
            } else {
                cu = offU + tileU0(i % usedCols, tileW) + tileW / 2f;
                cv = offV + tileV0(i / usedCols, tileH) + (tileH - labelZone) / 2f;
                float cell = Math.min(tileW, tileH - labelZone);
                if (cell >= CHIP_MIN_CELL) {
                    // Icon fills the inset chip exactly, like the list rows
                    size = Math.min(cell - 2 * chipInset(cell), ICON_MAX);
                } else {
                    size = Mth.clamp(cell * ICON_FRAC, ICON_MIN, ICON_MAX);
                }
                if (signal.momentary() || signal.timed()) size *= MOMENTARY_ICON_FRAC;
            }
            renderIcon(poseStack, buffers, signal.iconStack(), cu, cv, size, light);
        }
        // Text pass (third): big-tile labels in grid mode, row names in
        // list mode, slider level numerals in both.
        Font font = Minecraft.getInstance().font;
        if (list) {
            float scale = LIST_TEXT_H / 16f / FONT_LINE;
            // Between the icon chip and the switch; slider rows have a
            // wider track plus the level numeral, so budget per row
            float textU0 = TabletScreenMath.GLASS_U0 + SPACE + rowH + 0.4f;
            for (int i = 0; i < count; i++) {
                Signal signal = signals.get(i);
                // Plain (launcher) rows have no control — text runs to
                // the row's right edge
                float controlU0 = plainRows ? u1 - SPACE
                        : u1 - SPACE - SWITCH_MARGIN
                        - (signal.slider() ? SWITCH_W * 1.6f : SWITCH_W);
                float textU1 = controlU0 - 0.4f;
                if (signal.slider()) {
                    String level = String.valueOf(signal.strength());
                    float levelW = font.width(level) * LIST_TEXT_H / FONT_LINE;
                    float levelTop = listV0(i, rowH) + (rowH - LIST_TEXT_H) / 2f;
                    drawLabel(poseStack, buffers, font, level, controlU0 - 0.3f - levelW,
                            levelTop, scale, false, false, theme.textPrimary, bgLight);
                    textU1 = controlU0 - 0.6f - levelW;
                }
                int maxPx = (int) ((textU1 - textU0) * FONT_LINE / LIST_TEXT_H);
                FittedLabel label = fitLabel(font, signal.name(), maxPx);
                if (label.text().isBlank()) continue;
                float top = listV0(i, rowH) + (rowH - LIST_TEXT_H * label.fit()) / 2f;
                // Plain text: rows sit on the themed track, no outline needed
                drawLabel(poseStack, buffers, font, label.text(), textU0, top, scale * label.fit(),
                        false, false, theme.textPrimary, bgLight);
            }
        } else if (labels) {
            // Texel width budget → font pixels at this label height
            int maxPx = (int) ((tileW - 2 * SPACE) * FONT_LINE / textH);
            float scale = textH / 16f / FONT_LINE;
            for (int i = 0; i < count; i++) {
                FittedLabel label = fitLabel(font, signals.get(i).name(), maxPx);
                if (label.text().isBlank()) continue;
                float cu = offU + tileU0(i % usedCols, tileW) + tileW / 2f;
                // Bottom-aligned so a shrunk label hugs the tile edge
                float top = offV + tileV0(i / usedCols, tileH) + tileH - textH * label.fit() - SPACE;
                // White with a black outline: the label sits on the signal's
                // own tile color (any brightness), not the theme surface
                drawLabel(poseStack, buffers, font, label.text(), cu, top, scale * label.fit(),
                        true, true, 0xFFFFFFFF, bgLight);
            }
        }
        if (!list) {
            // Slider level numerals, stack-count style on the chip's
            // bottom-right corner (outlined, drawn in the raised text
            // layer so block icons can't cover them); dense classic pips
            // keep the old under-the-strip badge
            for (int i = 0; i < count; i++) {
                Signal signal = signals.get(i);
                if (!signal.slider()) continue;
                String level = String.valueOf(signal.strength());
                float u0 = offU + tileU0(i % usedCols, tileW);
                float v0 = offV + tileV0(i / usedCols, tileH);
                float numH = Mth.clamp(tileH * 0.22f, 0.8f, 1.6f);
                float levelW = font.width(level) * numH / FONT_LINE;
                float cell = Math.min(tileW, tileH - labelZone);
                float u, v;
                if (cell >= CHIP_MIN_CELL) {
                    float half = (cell - 2 * chipInset(cell)) / 2f;
                    u = u0 + tileW / 2f + half - levelW;
                    v = v0 + (tileH - labelZone) / 2f + half - numH;
                } else {
                    float inset = TabletScreenMath.sliderInset(tileW);
                    u = u0 + tileW - inset - levelW;
                    v = v0 + inset + sliderBarH(tileH) + 0.25f;
                }
                drawLabel(poseStack, buffers, font, level, u, v,
                        numH / 16f / FONT_LINE, false, true, 0xFFFFFFFF, bgLight);
            }
        }
        poseStack.popPose();
    }

    /** Slider value-strip height — shared by the quad pass and the numeral pass. */
    private static float sliderBarH(float tileH) {
        return Mth.clamp(tileH * 0.18f, 0.25f, 0.6f);
    }

    /** Everything the content passes need after the shared background:
     * logical glass spans, glass bounds, the shared buffer, background
     * light. Produced by {@link #beginScreen} with the pose PUSHED —
     * the caller must {@code popPose()}. */
    private record ScreenBase(float glassW, float glassH, float u1, float v1,
                              VertexConsumer vc, PoseStack.Pose pose, int bgLight) {
    }

    /**
     * Shared screen base (extracted for the 1.10.0 program faces): the
     * rotation pose and the background/bezel/skirt quads, identical for
     * every program the kiosk can show. Pushes the pose — every caller
     * pops after its content passes.
     */
    private static ScreenBase beginScreen(PoseStack poseStack, MultiBufferSource buffers,
                                          int rot, ScreenTheme theme, boolean backlit,
                                          int packedLight, int surfaceW, int surfaceH,
                                          int caseTint) {
        int members = surfaceW * surfaceH;
        // Content rotation: draw in a "logical" glass (spans swapped
        // when rot is odd) spun about the physical glass center. The
        // pivot maps the logical rect exactly onto the physical one;
        // the hit-test applies the same quarter-turns in reverse
        // (TabletScreenMath). Merged surfaces use the CONTINUOUS spans
        // — odd rotations only reach here on square surfaces
        // (effectiveRotation clamps oblong ones to half turns).
        float physW = members == 1 ? TabletScreenMath.GLASS_U1 - TabletScreenMath.GLASS_U0
                : TabletScreenMath.surfaceGlassW(surfaceW);
        float physH = members == 1 ? TabletScreenMath.GLASS_V1 - TabletScreenMath.GLASS_V0
                : TabletScreenMath.surfaceGlassH(surfaceH);
        float glassW = (rot & 1) == 0 ? physW : physH;
        float glassH = (rot & 1) == 0 ? physH : physW;
        float u1 = TabletScreenMath.GLASS_U0 + glassW;
        float v1 = TabletScreenMath.GLASS_V0 + glassH;
        poseStack.pushPose();
        if ((rot & 3) != 0) {
            float pivotU = (TabletScreenMath.GLASS_U0 + physW / 2f) / 16f;
            float pivotV = (TabletScreenMath.GLASS_V0 + physH / 2f) / 16f;
            float logicalU = (TabletScreenMath.GLASS_U0 + glassW / 2f) / 16f;
            float logicalV = (TabletScreenMath.GLASS_V0 + glassH / 2f) / 16f;
            poseStack.translate(pivotU, 0, pivotV);
            poseStack.mulPose(Axis.YN.rotationDegrees(90 * (rot & 3)));
            poseStack.translate(-logicalU, 0, -logicalV);
        }
        VertexConsumer vc = buffers.getBuffer(SCREEN_TYPE);
        PoseStack.Pose pose = poseStack.last();

        // Backgrounds. Standalone (members == 1): the classic recessed
        // glass with its half-texel bleed under the bezel ring.
        // Merged: ONE continuous panel spanning every member — the BER
        // raises it above the bezel lips (MERGED_SCREEN_HEIGHT), so it
        // covers interior bezels and case seams and the wall reads as a
        // single big screen (1.7.0 user feedback: per-member glass +
        // mosaic bezels never read as "one display" in-game).
        int bgLight = backlit ? LightTexture.FULL_BRIGHT : packedLight;
        int bg = backlit ? theme.screenBgLit : theme.screenBgOff;
        float frameW = 0.4f;
        int frame = theme.bodyOuter;
        if (members == 1) {
            float bleed = 0.5f;
            fillRect(pose, vc,
                    TabletScreenMath.GLASS_U0 - bleed, TabletScreenMath.GLASS_V0 - bleed,
                    u1 + bleed, v1 + bleed,
                    0f, bg, bgLight);
            ring(pose, vc, TabletScreenMath.GLASS_U0, TabletScreenMath.GLASS_V0, u1, v1,
                    LAYER * 0.5f, frame, bgLight, frameW);
            ring(pose, vc, TabletScreenMath.GLASS_U0 + frameW, TabletScreenMath.GLASS_V0 + frameW,
                    u1 - frameW, v1 - frameW, LAYER * 0.5f, shade(frame, 0.5f), bgLight, 0.12f);
        } else {
            // The panel spans the surface's FULL BLOCK footprint — not
            // just the case rect — so nothing baked (case corners, side
            // bevels, wall slivers between cases) can poke past the
            // screen edge anywhere (1.7.0 user feedback). Inset a
            // quarter texel so ADJACENT surfaces get a visible seam
            // instead of their raised panels fusing. Margins are the
            // glass→block distances (u: 3 texels, v: 2), swapped under
            // a (square-only) odd rotation so the logical rect rotates
            // onto the physical footprint.
            float inset = 0.25f;
            float uMargin = (rot & 1) == 0 ? 3f : 2f;
            float vMargin = (rot & 1) == 0 ? 2f : 3f;
            float su0 = TabletScreenMath.GLASS_U0 - uMargin + inset;
            float sv0 = TabletScreenMath.GLASS_V0 - vMargin + inset;
            float su1 = u1 + uMargin - inset;
            float sv1 = v1 + vMargin - inset;
            int tint = caseTint == 0 ? DEFAULT_CASE_TINT : caseTint;
            fillRect(pose, vc, su0, sv0, su1, sv1, 0f, bg, bgLight);
            // One continuous case-colored bezel band around the whole
            // display (1 texel — same width as a single tablet's bezel
            // ring), then the theme rail + seam inside it. Dyed
            // controllers get a dyed frame.
            ring(pose, vc, su0, sv0, su1, sv1, LAYER * 0.5f, tint, packedLight, 1f);
            ring(pose, vc, su0 + 1f, sv0 + 1f, su1 - 1f, sv1 - 1f,
                    LAYER * 0.75f, frame, bgLight, frameW);
            ring(pose, vc, su0 + 1f + frameW, sv0 + 1f + frameW,
                    su1 - 1f - frameW, sv1 - 1f - frameW,
                    LAYER * 0.75f, shade(frame, 0.5f), bgLight, 0.12f);
            // Rim skirt: the panel floats above the bezel lips, so
            // shallow angles would otherwise see under its edge (case
            // fronts and gaps). Case-colored walls make it read as a
            // solid display housing.
            skirt(pose, vc, su0, sv0, su1, sv1, 1.2f, shade(tint, 0.8f), packedLight);
        }
        return new ScreenBase(glassW, glassH, u1, v1, vc, pose, bgLight);
    }

    /**
     * Generic kiosk face for programs without a bespoke world pass
     * (1.10.0 suite — calculator today): the shared screen base plus the
     * program's name, dimmed, centered. The face is a door — tapping the
     * glass opens the program's GUI — so it only needs to say which door
     * it is. Text-only content, pass order trivially safe.
     */
    public static void renderLabelFace(PoseStack poseStack, MultiBufferSource buffers,
                                       int rot, ScreenTheme theme, boolean backlit,
                                       int packedLight, int surfaceW, int surfaceH,
                                       int caseTint, String label) {
        ScreenBase base = beginScreen(poseStack, buffers, rot, theme, backlit,
                packedLight, surfaceW, surfaceH, caseTint);
        Font font = Minecraft.getInstance().font;
        float cu = TabletScreenMath.GLASS_U0 + base.glassW() / 2f;
        float textH = Mth.clamp(
                (base.glassW() - 2f) * FONT_LINE / Math.max(1, font.width(label)),
                0.8f, base.glassH() * 0.2f);
        float top = TabletScreenMath.GLASS_V0 + (base.glassH() - textH) / 2f;
        drawLabel(poseStack, buffers, font, label, cu, top, textH / 16f / FONT_LINE,
                true, false, theme.textMuted, base.bgLight());
        poseStack.popPose();
    }

    /**
     * Addon kiosk face (the addon API): the shared screen base, then the
     * painter's BUFFERED calls flushed in the mandatory pass order —
     * all fills, then all items, then all text — so addon code cannot
     * break the shared-batch invariant however it paints. Fill layers
     * follow call order (painter's algorithm) inside the band below the
     * icon/text layers.
     */
    public static void renderAddonFace(PoseStack poseStack, MultiBufferSource buffers,
                                       int rot, ScreenTheme theme, boolean backlit,
                                       int packedLight, int surfaceW, int surfaceH, int caseTint,
                                       com.modpack.linktablet.api.client.TabletFacePainter painter,
                                       net.minecraft.world.level.block.entity.BlockEntity be,
                                       float partialTick) {
        ScreenBase base = beginScreen(poseStack, buffers, rot, theme, backlit,
                packedLight, surfaceW, surfaceH, caseTint);
        BufferedFace ctx = new BufferedFace(base.glassW(), base.glassH(), theme, backlit,
                be, partialTick);
        painter.paint(ctx);
        int fills = ctx.fills.size();
        for (int i = 0; i < fills; i++) {
            BufferedFace.Fill fill = ctx.fills.get(i);
            // Stack later fills above earlier ones, staying under the
            // icon (3.5x) and text (4x) layers
            float layer = LAYER * (1f + 2f * (fills <= 1 ? 0f : i / (float) (fills - 1)));
            fillRect(base.pose(), base.vc(),
                    TabletScreenMath.GLASS_U0 + fill.u0(), TabletScreenMath.GLASS_V0 + fill.v0(),
                    TabletScreenMath.GLASS_U0 + fill.u1(), TabletScreenMath.GLASS_V0 + fill.v1(),
                    layer, fill.argb(), base.bgLight());
        }
        for (BufferedFace.Item item : ctx.items) {
            renderIcon(poseStack, buffers, item.stack(),
                    TabletScreenMath.GLASS_U0 + item.centerU(),
                    TabletScreenMath.GLASS_V0 + item.centerV(),
                    item.size(), base.bgLight());
        }
        Font addonFont = Minecraft.getInstance().font;
        for (BufferedFace.Text text : ctx.texts) {
            drawLabel(poseStack, buffers, addonFont, text.text(),
                    TabletScreenMath.GLASS_U0 + text.u(),
                    TabletScreenMath.GLASS_V0 + text.top(),
                    text.height() / 16f / FONT_LINE,
                    text.centered(), text.outline(), text.argb(), base.bgLight());
        }
        poseStack.popPose();
    }

    /** The buffering {@code TabletFaceContext} behind {@link #renderAddonFace}. */
    private static final class BufferedFace
            implements com.modpack.linktablet.api.client.TabletFaceContext {

        record Fill(float u0, float v0, float u1, float v1, int argb) {}

        record Item(ItemStack stack, float centerU, float centerV, float size) {}

        record Text(String text, float u, float top, float height, int argb,
                    boolean centered, boolean outline) {}

        final List<Fill> fills = new java.util.ArrayList<>();
        final List<Item> items = new java.util.ArrayList<>();
        final List<Text> texts = new java.util.ArrayList<>();

        private final float width;
        private final float height;
        private final ScreenTheme theme;
        private final boolean backlit;
        private final net.minecraft.world.level.block.entity.BlockEntity blockEntity;
        private final float partialTick;

        BufferedFace(float width, float height, ScreenTheme theme, boolean backlit,
                     net.minecraft.world.level.block.entity.BlockEntity blockEntity,
                     float partialTick) {
            this.width = width;
            this.height = height;
            this.theme = theme;
            this.backlit = backlit;
            this.blockEntity = blockEntity;
            this.partialTick = partialTick;
        }

        @Override
        public float width() {
            return width;
        }

        @Override
        public float height() {
            return height;
        }

        @Override
        public ScreenTheme theme() {
            return theme;
        }

        @Override
        public boolean backlit() {
            return backlit;
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity blockEntity() {
            return blockEntity;
        }

        @Override
        public float partialTick() {
            return partialTick;
        }

        @Override
        public void fill(float u, float v, float w, float h, int argb) {
            if (w <= 0 || h <= 0) return;
            fills.add(new Fill(u, v, u + w, v + h, argb | 0xFF000000));
        }

        @Override
        public void item(ItemStack stack, float centerU, float centerV, float size) {
            if (stack == null || stack.isEmpty() || size <= 0) return;
            items.add(new Item(stack, centerU, centerV, size));
        }

        @Override
        public void text(String text, float u, float top, float height, int argb,
                         boolean centered, boolean outline) {
            if (text == null || text.isEmpty() || height <= 0) return;
            texts.add(new Text(text, u, top, height, argb, centered, outline));
        }
    }

    /**
     * Kiosk calculator face (1.10.0, from the first test pass — the
     * bare label read as "nothing renders"): the shared screen base
     * plus the LIVE calculator display — the viewer's own session tape
     * ({@code CalcEngine} is client-static, like the GUI). Pending op
     * small above, entry big below; tap the glass to work the pad.
     * Text-only content, pass order trivially safe.
     */
    public static void renderCalcFace(PoseStack poseStack, MultiBufferSource buffers,
                                      int rot, ScreenTheme theme, boolean backlit,
                                      int packedLight, int surfaceW, int surfaceH,
                                      int caseTint) {
        ScreenBase base = beginScreen(poseStack, buffers, rot, theme, backlit,
                packedLight, surfaceW, surfaceH, caseTint);
        Font font = Minecraft.getInstance().font;
        boolean error = com.modpack.linktablet.client.screen.CalcEngine.error();
        String entry = error
                ? Component.translatable("gui.linktablet.calc.error").getString()
                : com.modpack.linktablet.client.screen.CalcEngine.entry();
        String opGlyph = com.modpack.linktablet.client.screen.CalcEngine.pendingOpGlyph();

        float cu = TabletScreenMath.GLASS_U0 + base.glassW() / 2f;
        float entryH = Mth.clamp(
                (base.glassW() - 1.5f) * FONT_LINE / Math.max(1, font.width(entry)),
                0.8f, base.glassH() * 0.28f);
        float opH = Mth.clamp(entryH * 0.5f, 0.7f, 2f);
        float gap = entryH * 0.2f;
        float blockH = entryH + (opGlyph.isEmpty() ? 0 : opH + gap);
        float top = TabletScreenMath.GLASS_V0 + (base.glassH() - blockH) / 2f;
        if (!opGlyph.isEmpty()) {
            drawLabel(poseStack, buffers, font, opGlyph, cu, top,
                    opH / 16f / FONT_LINE, true, false, theme.accent, base.bgLight());
            top += opH + gap;
        }
        drawLabel(poseStack, buffers, font, entry, cu, top, entryH / 16f / FONT_LINE,
                true, false, theme.textPrimary, base.bgLight());
        poseStack.popPose();
    }

    /**
     * Kiosk gauges face (1.10.0 OS suite): the shared screen base plus
     * one procedural dial per gauge — tick arc, needle, value, name.
     * The world twin of {@code GaugeDialPainter}: quads instead of
     * fills, and the strict pass order holds (all dial quads first,
     * then all text). Readings arrive per-index from the BE's own
     * synced receivers — a kiosk dial shows what a link AT THE BLOCK
     * hears.
     */
    public static void renderGaugesFace(PoseStack poseStack, MultiBufferSource buffers,
                                        List<com.modpack.linktablet.frequency.Gauge> gauges,
                                        int[] readings, int rot, ScreenTheme theme,
                                        boolean backlit, int packedLight, int surfaceW,
                                        int surfaceH, int caseTint) {
        ScreenBase base = beginScreen(poseStack, buffers, rot, theme, backlit,
                packedLight, surfaceW, surfaceH, caseTint);
        int count = Math.min(gauges.size(), readings.length);
        if (count == 0) {
            poseStack.popPose();
            return;
        }
        int cols = count <= 2 ? 1 : 2;
        int rows = Mth.positiveCeilDiv(count, cols);
        float cellW = base.glassW() / cols;
        float cellH = base.glassH() / rows;
        float radius = Math.min(cellW, cellH) * 0.30f;
        Font font = Minecraft.getInstance().font;

        // Pass 1: quads — ticks, needle, hub for every dial
        for (int i = 0; i < count; i++) {
            com.modpack.linktablet.frequency.Gauge gauge = gauges.get(i);
            int value = Mth.clamp(readings[i], 0, com.modpack.linktablet.frequency.Gauge.MAX_VALUE);
            float cu = TabletScreenMath.GLASS_U0 + (i % cols + 0.5f) * cellW;
            float cv = TabletScreenMath.GLASS_V0 + (i / cols) * cellH + cellH * 0.55f;
            int color = gauge.color() | 0xFF000000;
            int max = com.modpack.linktablet.frequency.Gauge.MAX_VALUE;
            float tick = Mth.clamp(radius * 0.12f, 0.08f, 0.3f);
            for (int t = 0; t <= max; t++) {
                float angle = (float) Math.toRadians(180.0 - t * 180.0 / max);
                float tu = cu + radius * Mth.cos(angle);
                float tv = cv - radius * Mth.sin(angle);
                boolean lit = value > 0 && t <= value;
                fillRect(base.pose(), base.vc(), tu - tick, tv - tick, tu + tick, tv + tick,
                        LAYER, lit ? color : theme.switchOff,
                        lit ? LightTexture.FULL_BRIGHT : packedLight);
            }
            // Needle: thin quad from the hub toward the value angle
            float needleAngle = (float) Math.toRadians(180.0 - value * 180.0 / max);
            float du = Mth.cos(needleAngle);
            float dv = -Mth.sin(needleAngle);
            float len = radius - 2.5f * tick;
            float half = Mth.clamp(radius * 0.06f, 0.06f, 0.15f);
            // Perpendicular in (u,v)
            float pu = -dv * half;
            float pv = du * half;
            int needleColor = value > 0 ? color : theme.textMuted;
            int needleLight = value > 0 ? LightTexture.FULL_BRIGHT : packedLight;
            quad(base.pose(), base.vc(),
                    cu - pu, cv - pv,
                    cu + pu, cv + pv,
                    cu + du * len + pu, cv + dv * len + pv,
                    cu + du * len - pu, cv + dv * len - pv,
                    LAYER * 2, needleColor, needleLight);
            // Hub
            fillRect(base.pose(), base.vc(), cu - 1.5f * tick, cv - 1.5f * tick,
                    cu + 1.5f * tick, cv + 1.5f * tick, LAYER * 3, theme.textMuted, packedLight);
        }

        // Pass 2 (text): value inside the arc, name under the dial
        for (int i = 0; i < count; i++) {
            com.modpack.linktablet.frequency.Gauge gauge = gauges.get(i);
            int value = Mth.clamp(readings[i], 0, com.modpack.linktablet.frequency.Gauge.MAX_VALUE);
            float cu = TabletScreenMath.GLASS_U0 + (i % cols + 0.5f) * cellW;
            float cv = TabletScreenMath.GLASS_V0 + (i / cols) * cellH + cellH * 0.55f;
            String level = String.valueOf(value);
            float numH = Mth.clamp(radius * 0.55f, 0.8f, 2.2f);
            drawLabel(poseStack, buffers, font, level, cu, cv - radius * 0.55f - numH,
                    numH / 16f / FONT_LINE, true, false,
                    value > 0 ? theme.textPrimary : theme.textFaint, base.bgLight());
            if (!gauge.name().isBlank()) {
                float nameH = Mth.clamp(radius * 0.4f, 0.7f, 1.6f);
                int maxPx = (int) ((cellW - 2 * SPACE) * FONT_LINE / nameH);
                FittedLabel label = fitLabel(font, gauge.name(), maxPx);
                drawLabel(poseStack, buffers, font, label.text(), cu, cv + nameH * 0.4f,
                        nameH * label.fit() / 16f / FONT_LINE, true, false,
                        theme.textMuted, base.bgLight());
            }
        }
        poseStack.popPose();
    }

    /** One arbitrary quad (the dial needle) at the given layer height. */
    private static void quad(PoseStack.Pose pose, VertexConsumer vc,
                             float u0, float v0, float u1, float v1,
                             float u2, float v2, float u3, float v3,
                             float layer, int argb, int light) {
        vertex(pose, vc, u0 / 16f, layer, v0 / 16f, argb, light);
        vertex(pose, vc, u1 / 16f, layer, v1 / 16f, argb, light);
        vertex(pose, vc, u2 / 16f, layer, v2 / 16f, argb, light);
        vertex(pose, vc, u3 / 16f, layer, v3 / 16f, argb, light);
    }

    /**
     * Kiosk Frequency Monitor face (1.11.0): one summary row per channel
     * — both frequency item icons, a count "chip" (members transmitting
     * and in range), and a power bar (the max strength among them) —
     * the world-face digest of {@code MonitorScreen}'s channel header,
     * without the member breakdown (a glance, not the full monitor).
     * Counts/power arrive pre-reduced from the BE's synced summary
     * ({@code MonitorScanner}'s block half scans the network on a
     * server tick, never here); this face just draws what it's given.
     * List-mode row rhythm, rows capped to what the glass fits — no
     * scrolling on a placed face. Strict pass order: row/chip/bar quads,
     * then both frequency icons, then the count and power numerals.
     */
    public static void renderMonitorFace(PoseStack poseStack, MultiBufferSource buffers,
                                         List<com.modpack.linktablet.frequency.Frequency> channels,
                                         int[] counts, int[] power, int rot, ScreenTheme theme,
                                         boolean backlit, int packedLight, int surfaceW,
                                         int surfaceH, int caseTint) {
        ScreenBase base = beginScreen(poseStack, buffers, rot, theme, backlit,
                packedLight, surfaceW, surfaceH, caseTint);
        int total = Math.min(channels.size(), Math.min(counts.length, power.length));
        int count = TabletScreenMath.visibleSignals(total, true, surfaceW, surfaceH);
        if (count == 0) {
            poseStack.popPose();
            return;
        }
        VertexConsumer vc = base.vc();
        PoseStack.Pose pose = base.pose();
        int bgLight = base.bgLight();
        float rowH = tileSize(base.glassH(), TabletScreenMath.LIST_ROWS * surfaceH);
        float u0Row = TabletScreenMath.GLASS_U0 + SPACE;
        float u1Row = base.u1() - SPACE;
        // Two icons share the row's icon zone, so each gets half the
        // single-icon list row's budget (Math.min, no forced floor —
        // dense merged rows can shrink below the grid's ICON_MIN).
        float iconSize = Math.min(LIST_ICON, rowH * 0.5f);
        float barW = Mth.clamp((u1Row - u0Row) * 0.28f, 1.5f, 4f);
        float chipInsetPx = rowH / 6f;
        float chipSize = rowH - 2f * chipInsetPx;
        Font font = Minecraft.getInstance().font;

        // Pass 1 (quads): row plaque, count chip, power bar per channel
        for (int i = 0; i < count; i++) {
            float v0 = listV0(i, rowH);
            float v1 = v0 + rowH;
            boolean active = counts[i] > 0;
            int lineLight = active ? LightTexture.FULL_BRIGHT : packedLight;

            fillRect(pose, vc, u0Row, v0, u1Row, v1, LAYER, theme.screenTrack, packedLight);
            raisedBevel(pose, vc, u0Row, v0, u1Row, v1, LAYER * 1.5f, theme.screenTrack, packedLight);

            float chipU0 = u1Row - SPACE - chipSize;
            fillRect(pose, vc, chipU0, v0 + chipInsetPx, chipU0 + chipSize, v1 - chipInsetPx,
                    LAYER * 2, active ? theme.accentDim : theme.switchOff, lineLight);

            float barU1 = chipU0 - SPACE * 2f;
            float barU0 = barU1 - barW;
            float barV0 = v0 + (rowH - rowH * 0.3f) / 2f;
            float barV1 = barV0 + rowH * 0.3f;
            fillRect(pose, vc, barU0, barV0, barU1, barV1, LAYER * 2, theme.switchOff, packedLight);
            if (power[i] > 0) {
                float frac = Mth.clamp(power[i] / (float) com.modpack.linktablet.frequency.Gauge.MAX_VALUE, 0f, 1f);
                fillRect(pose, vc, barU0, barV0, barU0 + (barU1 - barU0) * frac, barV1,
                        LAYER * 2.5f, theme.accentDim, LightTexture.FULL_BRIGHT);
            }
            insetGroove(pose, vc, barU0, barV0, barU1, barV1, LAYER * 2.75f, theme.switchOff, packedLight);
        }

        // Pass 2 (items): both frequency icons, left of the row
        for (int i = 0; i < count; i++) {
            com.modpack.linktablet.frequency.Frequency freq = channels.get(i);
            float v0 = listV0(i, rowH);
            float cv = v0 + rowH / 2f;
            boolean active = counts[i] > 0;
            int light = active ? LightTexture.FULL_BRIGHT : packedLight;
            float cu1 = u0Row + SPACE + iconSize / 2f;
            float cu2 = cu1 + iconSize + 0.2f;
            renderIcon(poseStack, buffers, freq.icon1(), cu1, cv, iconSize, light);
            renderIcon(poseStack, buffers, freq.icon2(), cu2, cv, iconSize, light);
        }

        // Pass 3 (text): count numeral over its chip, power numeral by its bar
        for (int i = 0; i < count; i++) {
            float v0 = listV0(i, rowH);
            boolean active = counts[i] > 0;
            float chipU0 = u1Row - SPACE - chipSize;
            String countText = String.valueOf(counts[i]);
            float numH = Mth.clamp(chipSize * 0.55f, 0.7f, 1.6f);
            float countW = font.width(countText) * numH / FONT_LINE;
            drawLabel(poseStack, buffers, font, countText,
                    chipU0 + (chipSize - countW) / 2f, v0 + (rowH - numH) / 2f,
                    numH / 16f / FONT_LINE, false, false,
                    active ? 0xFFFFFFFF : theme.textMuted, bgLight);

            float barU1 = chipU0 - SPACE * 2f;
            float barU0 = barU1 - barW;
            // Left of the bar, vertically centered — the row is one line
            // tall, so stacking the numeral above/below the bar (the
            // GUI's two-line layout) would either crowd the row top or
            // spill past its bottom.
            String powerText = String.valueOf(power[i]);
            float powH = Mth.clamp(rowH * 0.5f, 0.7f, 1.4f);
            float powW = font.width(powerText) * powH / FONT_LINE;
            drawLabel(poseStack, buffers, font, powerText, barU0 - 0.3f - powW,
                    v0 + (rowH - powH) / 2f, powH / 16f / FONT_LINE, false, false,
                    power[i] > 0 ? theme.textPrimary : theme.textMuted, bgLight);
        }
        poseStack.popPose();
    }

    /**
     * Kiosk Paint face (1.11.0 "paint on walls"): the shared screen base
     * plus the wall's own stitched picture — {@code PaintScreen#stitchArgb}
     * is the ONE stitch (GUI and this face read the same helper so they
     * never drift), palette-mapped ARGB per continuous cell, dimensions
     * {@code surfaceW*PaintCanvas.COLS × surfaceH*PaintCanvas.ROWS}
     * row-major. Blank cells (0) are skipped so the themed background
     * shows through underneath; an entirely blank canvas shows the faint
     * centered "Paint" door label instead — {@link #renderLabelFace}'s
     * look, inlined here since the base is already open (can't call that
     * method directly without opening a second, duplicate base). Fills
     * only, no items, and the blank-canvas label is the only text, so the
     * strict pass order (fills, then text) holds trivially.
     */
    public static void renderPaintFace(PoseStack poseStack, MultiBufferSource buffers,
                                       com.modpack.linktablet.block.TabletBlockEntity controller,
                                       int rot, ScreenTheme theme, boolean backlit,
                                       int packedLight, int surfaceW, int surfaceH, int caseTint) {
        ScreenBase base = beginScreen(poseStack, buffers, rot, theme, backlit,
                packedLight, surfaceW, surfaceH, caseTint);
        int[] argb = com.modpack.linktablet.client.screen.PaintScreen.stitchArgb(controller);
        boolean blank = true;
        for (int c : argb) {
            if (c != 0) { blank = false; break; }
        }
        if (blank) {
            Font font = Minecraft.getInstance().font;
            String label = com.modpack.linktablet.Program.PAINT.displayName().getString();
            float cu = TabletScreenMath.GLASS_U0 + base.glassW() / 2f;
            float textH = Mth.clamp(
                    (base.glassW() - 2f) * FONT_LINE / Math.max(1, font.width(label)),
                    0.8f, base.glassH() * 0.2f);
            float top = TabletScreenMath.GLASS_V0 + (base.glassH() - textH) / 2f;
            drawLabel(poseStack, buffers, font, label, cu, top, textH / 16f / FONT_LINE,
                    true, false, theme.textMuted, base.bgLight());
            poseStack.popPose();
            return;
        }
        int cols = surfaceW * com.modpack.linktablet.PaintCanvas.COLS;
        int rows = surfaceH * com.modpack.linktablet.PaintCanvas.ROWS;
        VertexConsumer vc = base.vc();
        PoseStack.Pose pose = base.pose();
        int bgLight = base.bgLight();
        float cellW = base.glassW() / cols;
        float cellH = base.glassH() / rows;
        for (int r = 0; r < rows; r++) {
            int rowBase = r * cols;
            float v0 = TabletScreenMath.GLASS_V0 + r * cellH;
            for (int c = 0; c < cols; c++) {
                int color = argb[rowBase + c];
                if (color == 0) continue;
                float u0 = TabletScreenMath.GLASS_U0 + c * cellW;
                fillRect(pose, vc, u0, v0, u0 + cellW, v0 + cellH, LAYER, color, bgLight);
            }
        }
        poseStack.popPose();
    }

    /** Status hint for the Twitch face, mirroring {@code
     * TwitchOverlayContent#statusMessage}/{@code TwitchScreen#statusMessage}:
     * null means "show the chat wall" (LIVE with a non-empty buffer). */
    private static Component twitchStatus(String channel, List<TwitchChatService.ChatMessage> messages) {
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

    /**
     * Kiosk Twitch Chat face (1.11.0-beta.2): the shared screen base plus
     * a chat wall — the newest message anchored to the LAST row, older
     * ones stacking upward, so a sparse buffer clings to the bottom the
     * way a real chat window does rather than floating at the top. Each
     * line is "user: text" in the chatter's own color, ellipsized (no
     * wrapping — a placed face is a glance, not a reader) to the glass
     * width. Empty channel or a non-LIVE/empty-buffer status shows the
     * matching centered hint instead ({@link #twitchStatus}, the same
     * mapping {@code TwitchOverlayContent} and {@code TwitchScreen} use).
     * Touches the service's per-frame presence heartbeat FIRST, before
     * any layout math — the face's presence keeps the channel joined;
     * once chunk culling stops the touches, the service parts it on its
     * own. Text-only content beyond the shared background, so the strict
     * pass order holds trivially.
     */
    public static void renderTwitchFace(PoseStack poseStack, MultiBufferSource buffers,
                                        String channel, int rot, ScreenTheme theme, boolean backlit,
                                        int packedLight, int surfaceW, int surfaceH, int caseTint) {
        if (!channel.isEmpty()) TwitchChatService.touchFace(channel);

        ScreenBase base = beginScreen(poseStack, buffers, rot, theme, backlit,
                packedLight, surfaceW, surfaceH, caseTint);
        Font font = Minecraft.getInstance().font;
        int bgLight = base.bgLight();

        List<TwitchChatService.ChatMessage> messages = TwitchChatService.messages(channel);
        Component status = twitchStatus(channel, messages);
        if (status != null) {
            String text = status.getString();
            float cu = TabletScreenMath.GLASS_U0 + base.glassW() / 2f;
            float textH = Mth.clamp(
                    (base.glassW() - 2f) * FONT_LINE / Math.max(1, font.width(text)),
                    0.8f, base.glassH() * 0.2f);
            float top = TabletScreenMath.GLASS_V0 + (base.glassH() - textH) / 2f;
            drawLabel(poseStack, buffers, font, text, cu, top, textH / 16f / FONT_LINE,
                    true, false, theme.textMuted, bgLight);
            poseStack.popPose();
            return;
        }

        int maxRows = TabletScreenMath.LIST_ROWS * surfaceH;
        int shown = Math.min(messages.size(), maxRows);
        float rowH = tileSize(base.glassH(), maxRows);
        float u0 = TabletScreenMath.GLASS_U0 + SPACE;
        float u1 = base.u1() - SPACE;
        float scale = LIST_TEXT_H / 16f / FONT_LINE;
        int maxPx = (int) ((u1 - u0) * FONT_LINE / LIST_TEXT_H);

        // Bottom-anchored: the newest message (last in the oldest→newest
        // list) lands in row (maxRows - 1); older messages fill upward.
        for (int i = 0; i < shown; i++) {
            TwitchChatService.ChatMessage message = messages.get(messages.size() - shown + i);
            int row = maxRows - shown + i;
            float top = listV0(row, rowH) + (rowH - LIST_TEXT_H) / 2f;
            String prefix = message.user() + ": ";
            int prefixWidth = font.width(prefix);
            if (prefixWidth >= maxPx) {
                String line = TextFit.ellipsize(font, prefix, maxPx);
                drawLabel(poseStack, buffers, font, line, u0, top, scale,
                        false, false, message.color(), bgLight);
                continue;
            }
            drawLabel(poseStack, buffers, font, prefix, u0, top, scale,
                    false, false, message.color(), bgLight);
            float textU0 = u0 + prefixWidth * LIST_TEXT_H / FONT_LINE;
            String text = TextFit.ellipsize(font, message.text(), maxPx - prefixWidth);
            drawLabel(poseStack, buffers, font, text, textU0, top, scale,
                    false, false, theme.textPrimary, bgLight);
        }
        poseStack.popPose();
    }

    /**
     * Kiosk clock face (1.10.0 OS suite): the shared screen base plus a
     * big digital wall clock — local time with a blinking colon, the
     * date beneath. Content is TEXT ONLY, so the strict pass order holds
     * trivially (base quads, then font batches). Time is the viewer's
     * real local clock, which every client agrees on to the second.
     */
    public static void renderClockFace(PoseStack poseStack, MultiBufferSource buffers,
                                       int rot, ScreenTheme theme, boolean backlit,
                                       int packedLight, int surfaceW, int surfaceH,
                                       int caseTint) {
        ScreenBase base = beginScreen(poseStack, buffers, rot, theme, backlit,
                packedLight, surfaceW, surfaceH, caseTint);
        Font font = Minecraft.getInstance().font;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        String time = String.format("%02d%s%02d", now.getHour(),
                now.getSecond() % 2 == 0 ? ":" : " ", now.getMinute());
        String date = now.getDayOfWeek().getDisplayName(
                java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                + " " + now.getDayOfMonth() + " " + now.getMonth().getDisplayName(
                java.time.format.TextStyle.SHORT, java.util.Locale.getDefault());

        // Time height: as big as fits the glass width (with margins),
        // capped by height; date scales at 40% underneath
        float cu = TabletScreenMath.GLASS_U0 + base.glassW() / 2f;
        float timeH = Math.min(base.glassH() * 0.34f,
                (base.glassW() - 2f) * FONT_LINE / font.width(time));
        float dateH = Mth.clamp(timeH * 0.4f, 0.8f, 2.5f);
        float gap = timeH * 0.25f;
        float top = TabletScreenMath.GLASS_V0
                + (base.glassH() - (timeH + gap + dateH)) / 2f;
        drawLabel(poseStack, buffers, font, time, cu, top, timeH / 16f / FONT_LINE,
                true, false, theme.textPrimary, base.bgLight());
        drawLabel(poseStack, buffers, font, date, cu, top + timeH + gap,
                dateH / 16f / FONT_LINE, true, false, theme.textMuted, base.bgLight());
        poseStack.popPose();
    }

    /** Label after fitting: possibly ellipsized text + the scale multiplier applied. */
    private record FittedLabel(String text, float fit) {
    }

    /** Minimum shrink before long names fall back to ellipsizing. */
    private static final float MIN_LABEL_FIT = 0.7f;

    /**
     * Shrink a too-wide name down to {@link #MIN_LABEL_FIT}; if it still
     * overflows at minimum scale, ellipsize against the enlarged budget.
     */
    private static FittedLabel fitLabel(Font font, String name, int maxPx) {
        float fit = TextFit.fitScale(font, name, maxPx, MIN_LABEL_FIT);
        if (font.width(name) * fit <= maxPx) {
            return new FittedLabel(name, fit);
        }
        return new FittedLabel(TextFit.ellipsize(font, name, (int) (maxPx / MIN_LABEL_FIT)), MIN_LABEL_FIT);
    }

    /**
     * One label lying flat on the screen. Font space is +x right / +y
     * down facing -z; +90° about X lays it flat, +y running down-screen
     * (+v). Outlined labels use the vanilla glowing-sign 8x outline so
     * they stay readable on bright tile colors.
     */
    private static void drawLabel(PoseStack poseStack, MultiBufferSource buffers, Font font,
                                  String name, float u, float topV, float scale,
                                  boolean centered, boolean outline, int color, int light) {
        poseStack.pushPose();
        // Text rides well above the icon pass: GUI-transformed block
        // models keep real depth even squashed (front faces reach ~0.009
        // at the 6-texel icon cap), so anything lower gets buried under
        // block icons — the world-side twin of the GUI's z-200 lift.
        poseStack.translate(u / 16f, 0.011f, topV / 16f);
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        poseStack.scale(scale, scale, scale);
        float x = centered ? -font.width(name) / 2f : 0f;
        if (outline) {
            font.drawInBatch8xOutline(Component.literal(name).getVisualOrderText(), x, 0f,
                    color, 0xFF000000, poseStack.last().pose(), buffers, light);
        } else {
            font.drawInBatch(name, x, 0f, color, false,
                    poseStack.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        }
        poseStack.popPose();
    }

    /** Neutral dark track behind slider value bars (no theme in this path). */
    private static final int SLIDER_TRACK = 0xFF14161C;

    /** Minimum tile cell (texels) for the plaque+chip tile look; smaller
     * cells keep the classic full-color pip — a chip inset would crowd
     * the icon below readability on dense grids. */
    private static final float CHIP_MIN_CELL = 2.6f;

    /** Chip inset from its cell — the list chip's 1/6 proportion. */
    private static float chipInset(float cell) {
        return Mth.clamp(cell / 6f, 0.3f, 1.2f);
    }

    /**
     * Grid entry (icon drawn later). Big-enough tiles get the list rows'
     * structure — themed plaque with an inset signal-color chip
     * ({@link #renderChipTile}); dense grids keep the classic pip where
     * the whole cell is the signal color.
     */
    private static void renderPip(PoseStack.Pose pose, VertexConsumer vc,
                                  float u0, float v0, float u1, float v1,
                                  Signal signal, int color, ScreenTheme theme,
                                  int packedLight, float ringW, boolean held,
                                  float labelZone) {
        float cell = Math.min(u1 - u0, v1 - v0 - labelZone);
        if (cell >= CHIP_MIN_CELL) {
            renderChipTile(pose, vc, u0, v0, u1, v1, signal, color, theme,
                    packedLight, ringW, held, labelZone, cell);
            return;
        }
        if (signal.slider()) {
            // Plate lights with any output; a top strip shows the value
            boolean on = signal.strength() > 0;
            int plate = on ? color : dim(color);
            int plateLight = on ? LightTexture.FULL_BRIGHT : packedLight;
            fillRect(pose, vc, u0, v0, u1, v1, LAYER, plate, plateLight);
            raisedBevel(pose, vc, u0, v0, u1, v1, LAYER * 1.5f, plate, plateLight);
            float inset = TabletScreenMath.sliderInset(u1 - u0);
            float barH = sliderBarH(v1 - v0);
            float bu0 = u0 + inset;
            float bu1 = u1 - inset;
            float bv0 = v0 + inset;
            fillRect(pose, vc, bu0, bv0, bu1, bv0 + barH, LAYER * 2, SLIDER_TRACK, packedLight);
            if (on) {
                float fill = bu0 + (bu1 - bu0) * signal.fillFraction();
                fillRect(pose, vc, bu0, bv0, fill, bv0 + barH, LAYER * 3,
                        brighten(color), LightTexture.FULL_BRIGHT);
            }
            // Groove hairlines above the fill (3x), below the icons (3.5x)
            insetGroove(pose, vc, bu0, bv0, bu1, bv0 + barH, LAYER * 3.25f,
                    SLIDER_TRACK, packedLight);
            return;
        }
        if (signal.momentary() || signal.timed()) {
            if (held) {
                // Pressed / pulse running: fills solid and glows
                fillRect(pose, vc, u0, v0, u1, v1, LAYER, brighten(color), LightTexture.FULL_BRIGHT);
                raisedBevel(pose, vc, u0, v0, u1, v1, LAYER * 1.5f, brighten(color), LightTexture.FULL_BRIGHT);
            } else {
                ring(pose, vc, u0, v0, u1, v1, LAYER, dim(color), packedLight, ringW);
            }
        } else if (signal.active()) {
            fillRect(pose, vc, u0, v0, u1, v1, LAYER, brighten(color), LightTexture.FULL_BRIGHT);
            raisedBevel(pose, vc, u0, v0, u1, v1, LAYER * 1.5f, brighten(color), LightTexture.FULL_BRIGHT);
        } else {
            fillRect(pose, vc, u0, v0, u1, v1, LAYER, dim(color), packedLight);
            raisedBevel(pose, vc, u0, v0, u1, v1, LAYER * 1.5f, dim(color), packedLight);
        }
    }

    /**
     * Grid tile as a themed plaque with an inset signal-color chip — the
     * list rows' look (and the GUI grid's). The chip centers on the icon
     * center (labels reserve the tile bottom) and carries the state:
     * bright + full-bright when on, dim otherwise, hollow ring while a
     * momentary signal is unheld. Slider value strips keep their exact
     * pre-chip geometry — {@code TabletScreenMath.sliderBarU} maps drags
     * against it — so the chip sits at LAYER*1.75, under the strip (2x)
     * it can graze on small tiles.
     */
    private static void renderChipTile(PoseStack.Pose pose, VertexConsumer vc,
                                       float u0, float v0, float u1, float v1,
                                       Signal signal, int color, ScreenTheme theme,
                                       int packedLight, float ringW, boolean held,
                                       float labelZone, float cell) {
        boolean on = (signal.active() && !signal.momentary()) || held;
        int stateLight = on ? LightTexture.FULL_BRIGHT : packedLight;
        fillRect(pose, vc, u0, v0, u1, v1, LAYER, theme.screenTrack, packedLight);
        raisedBevel(pose, vc, u0, v0, u1, v1, LAYER * 1.5f, theme.screenTrack, packedLight);
        if (on) {
            // Faint powered outline hugging the tile edge — the GUI's
            // accent glow border brought in-world (hairline, full-bright)
            ring(pose, vc, u0, v0, u1, v1, LAYER * 1.75f, theme.accent,
                    LightTexture.FULL_BRIGHT, 0.12f);
        }

        float half = (cell - 2 * chipInset(cell)) / 2f;
        float cu = (u0 + u1) / 2f;
        float cv = v0 + (v1 - v0 - labelZone) / 2f;
        if ((signal.momentary() || signal.timed()) && !held) {
            ring(pose, vc, cu - half, cv - half, cu + half, cv + half, LAYER * 1.75f,
                    dim(color), packedLight, ringW);
        } else {
            fillRect(pose, vc, cu - half, cv - half, cu + half, cv + half, LAYER * 1.75f,
                    on ? brighten(color) : dim(color), stateLight);
        }

        if (signal.slider()) {
            float inset = TabletScreenMath.sliderInset(u1 - u0);
            float barH = sliderBarH(v1 - v0);
            float bu0 = u0 + inset;
            float bu1 = u1 - inset;
            float bv0 = v0 + inset;
            fillRect(pose, vc, bu0, bv0, bu1, bv0 + barH, LAYER * 2, SLIDER_TRACK, packedLight);
            if (signal.strength() > 0) {
                float fill = bu0 + (bu1 - bu0) * signal.fillFraction();
                fillRect(pose, vc, bu0, bv0, fill, bv0 + barH, LAYER * 3,
                        brighten(color), LightTexture.FULL_BRIGHT);
            }
            insetGroove(pose, vc, bu0, bv0, bu1, bv0 + barH, LAYER * 3.25f,
                    SLIDER_TRACK, packedLight);
        }
    }

    /** List row: colored chip left, mini switch right (icon drawn later).
     *  Bounds arrive precomputed (member offsets included on surfaces).
     *  {@code plainRows} drops the switch — launcher rows are doors. */
    private static void renderSwitchRow(PoseStack.Pose pose, VertexConsumer vc,
                                        float v0, Signal signal, int color,
                                        ScreenTheme theme, int packedLight, boolean held,
                                        float rowH, float u0, float u1, boolean plainRows) {
        float v1 = v0 + rowH;
        boolean on = (signal.active() && !signal.momentary()) || held;
        int stateLight = on ? LightTexture.FULL_BRIGHT : packedLight;

        fillRect(pose, vc, u0, v0, u1, v1, LAYER, theme.screenTrack, packedLight);
        // Rows read as raised plaques, like the GUI's list rows
        raisedBevel(pose, vc, u0, v0, u1, v1, LAYER * 1.5f, theme.screenTrack, packedLight);

        // Icon chip in the signal's color, inset INSIDE the row plaque like
        // the GUI's chip (its 4px-in-24 margin, scaled); flat — color IS
        // the content
        float chipInset = rowH / 6f;
        fillRect(pose, vc, u0 + chipInset, v0 + chipInset, u0 + rowH - chipInset, v1 - chipInset,
                LAYER * 2, on ? brighten(color) : dim(color), stateLight);

        if (plainRows) {
            return; // no switch mechanism — the row itself is the button
        }

        // Track vertically centered in the row and inset from the row's
        // right edge, GUI-style
        float su1 = u1 - SWITCH_MARGIN;
        float su0 = su1 - SWITCH_W;
        float sv0 = v0 + (rowH - SWITCH_H) / 2f;
        float sv1 = sv0 + SWITCH_H;
        if (signal.slider()) {
            // Wider mini track with a knob at the current value
            float tu0 = su1 - SWITCH_W * 1.6f;
            float tv0 = v0 + (rowH - SWITCH_H * 0.5f) / 2f;
            float tv1 = tv0 + SWITCH_H * 0.5f;
            fillRect(pose, vc, tu0, tv0, su1, tv1, LAYER * 2, theme.switchOff, packedLight);
            float knob = tu0 + (su1 - tu0 - KNOB_W) * signal.fillFraction();
            if (signal.strength() > 0) {
                // Above the track, below the knob — coplanar quads z-fight
                // at glancing angles (latent since 1.4.0, exposed by
                // higher-contrast themes)
                fillRect(pose, vc, tu0, tv0, knob + KNOB_W / 2f, tv1, LAYER * 2.5f, theme.accentDim, stateLight);
            }
            // Groove hairlines above the fill (2.5x), below the knob (3x)
            insetGroove(pose, vc, tu0, tv0, su1, tv1, LAYER * 2.75f,
                    theme.switchOff, packedLight);
            fillRect(pose, vc, knob, sv0, knob + KNOB_W, sv1, LAYER * 3,
                    on ? theme.accent : theme.textMuted, stateLight);
            return;
        }
        if (signal.momentary()) {
            // Push button: dot lights while held, mirroring the GUI
            fillRect(pose, vc, su0, sv0, su1, sv1, LAYER * 2,
                    held ? theme.accentDim : theme.switchOff, stateLight);
            float mid = (su0 + su1) / 2f;
            float cv = (sv0 + sv1) / 2f;
            fillRect(pose, vc, mid - 0.2f, cv - 0.2f, mid + 0.2f, cv + 0.2f,
                    LAYER * 3, held ? theme.accent : theme.textMuted, stateLight);
        } else {
            fillRect(pose, vc, su0, sv0, su1, sv1, LAYER * 2,
                    on ? theme.accentDim : theme.switchOff, stateLight);
            float knobU0 = on ? su1 - KNOB_INSET - KNOB_W : su0 + KNOB_INSET;
            fillRect(pose, vc, knobU0, sv0 + KNOB_INSET, knobU0 + KNOB_W, sv1 - KNOB_INSET,
                    LAYER * 3, on ? theme.accent : theme.textMuted, stateLight);
        }
    }

    /**
     * The signal's item icon rendered flat on the screen. Depth is squashed
     * to a sliver, so even block models read as 2D screen icons (their
     * GUI display angle flattens into the familiar inventory-icon look).
     */
    private static void renderIcon(PoseStack poseStack, MultiBufferSource buffers,
                                   ItemStack icon, float centerU, float centerV,
                                   float sizeTexels, int light) {
        if (icon.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        BakedModel model = mc.getItemRenderer().getModel(icon, mc.level, null, 0);
        float scale = sizeTexels / 16f;

        poseStack.pushPose();
        // Size-proportional lift: the depth squash still leaves a big
        // icon's back corners dipping ~0.022·size blocks below its
        // center — at 6 texels that's beneath the base 0.0035 float and
        // the panel clips the icon (surfaced by 1.7.0's giant surface
        // tiles). Small icons keep effectively the old height.
        poseStack.translate(centerU / 16f, 0.0035f + sizeTexels * 0.0009f, centerV / 16f);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        poseStack.scale(scale, scale, scale * 0.04f);
        mc.getItemRenderer().render(icon, ItemDisplayContext.GUI, false, poseStack, buffers,
                light, OverlayTexture.NO_OVERLAY, model);
        poseStack.popPose();
    }

    /** Hollow ring — momentary marker, bevel outlines, the glass frame. */
    private static void ring(PoseStack.Pose pose, VertexConsumer vc,
                             float u0, float v0, float u1, float v1,
                             float layer, int color, int light, float w) {
        fillRect(pose, vc, u0, v0, u1, v0 + w, layer, color, light);
        fillRect(pose, vc, u0, v1 - w, u1, v1, layer, color, light);
        fillRect(pose, vc, u0, v0 + w, u0 + w, v1 - w, layer, color, light);
        fillRect(pose, vc, u1 - w, v0 + w, u1, v1 - w, layer, color, light);
    }

    // ---- World chrome (1.5.1) ----------------------------------------
    // Quad EMULATION of the GUI chrome — never sample the atlas here (a
    // second RenderType mid-pass ends the shared batch; art bevels alias
    // at 2-texel tiles). Hairline layers: frame 0.5x, plaque bevels 1.5x,
    // grid chip 1.75x, list groove 2.75x, grid groove 3.25x — between the
    // existing quads, below icons (3.5x) and text (4x).

    /** Bevel/groove hairlines skip cells this small — they'd shimmer. */
    private static final float MIN_BEVEL_CELL = 1.2f;

    /**
     * Raised-plaque hairlines over a plate drawn at {@code LAYER}: thin
     * outline ring, highlight along top+left, shadow along bottom+right —
     * the GUI tile/plaque look, colors derived from the plate's own color
     * so every state (active glow, dim, momentary held) and theme matches.
     */
    private static void raisedBevel(PoseStack.Pose pose, VertexConsumer vc,
                                    float u0, float v0, float u1, float v1,
                                    float layer, int base, int light) {
        float size = Math.min(u1 - u0, v1 - v0);
        if (size < MIN_BEVEL_CELL) return;
        // ~4.5% of the cell, the GUI tile art's bevel proportion; the
        // gentle highlight keeps dark dimmed plates from flaring
        float bw = Mth.clamp(size * 0.06f, 0.10f, 0.22f);
        float ow = bw * 0.5f;
        int hi = bevelHi(base);
        int lo = shade(base, 0.55f);
        ring(pose, vc, u0, v0, u1, v1, layer, shade(base, 0.35f), light, ow);
        fillRect(pose, vc, u0 + ow, v0 + ow, u1 - ow, v0 + ow + bw, layer, hi, light);
        fillRect(pose, vc, u0 + ow, v0 + ow + bw, u0 + ow + bw, v1 - ow, layer, hi, light);
        fillRect(pose, vc, u0 + ow + bw, v1 - ow - bw, u1 - ow, v1 - ow, layer, lo, light);
        fillRect(pose, vc, u1 - ow - bw, v0 + ow + bw, u1 - ow, v1 - ow - bw, layer, lo, light);
    }

    /**
     * Inset-groove hairlines over a slider track (and its fill): shadow
     * along the top, light lip along the bottom — the GUI's recessed
     * slider groove. Spans the full track so the groove reads even where
     * the fill covers it, like the GUI's tinted-art fill does.
     */
    private static void insetGroove(PoseStack.Pose pose, VertexConsumer vc,
                                    float u0, float v0, float u1, float v1,
                                    float layer, int base, int light) {
        float gh = Mth.clamp((v1 - v0) * 0.22f, 0.08f, 0.18f);
        fillRect(pose, vc, u0, v0, u1, v0 + gh, layer, shade(base, 0.45f), light);
        fillRect(pose, vc, u0, v1 - gh, u1, v1, layer, brighten(base), light);
    }

    /** One texel-space rect as a quad at the given layer height. */
    private static void fillRect(PoseStack.Pose pose, VertexConsumer vc,
                                 float u0, float v0, float u1, float v1,
                                 float layer, int argb, int light) {
        float x0 = u0 / 16f, z0 = v0 / 16f;
        float x1 = u1 / 16f, z1 = v1 / 16f;
        vertex(pose, vc, x0, layer, z0, argb, light);
        vertex(pose, vc, x0, layer, z1, argb, light);
        vertex(pose, vc, x1, layer, z1, argb, light);
        vertex(pose, vc, x1, layer, z0, argb, light);
    }

    /**
     * Vertical walls sealing a floating panel's rim down to the block
     * face (merged surfaces float above the bezel lips). Each edge is
     * emitted with both windings so it reads from every side regardless
     * of the render type's culling.
     */
    private static void skirt(PoseStack.Pose pose, VertexConsumer vc,
                              float u0, float v0, float u1, float v1,
                              float depth, int argb, int light) {
        float x0 = u0 / 16f, z0 = v0 / 16f;
        float x1 = u1 / 16f, z1 = v1 / 16f;
        float yb = -depth / 16f;
        wall(pose, vc, x0, z0, x1, z0, yb, argb, light);
        wall(pose, vc, x0, z1, x1, z1, yb, argb, light);
        wall(pose, vc, x0, z0, x0, z1, yb, argb, light);
        wall(pose, vc, x1, z0, x1, z1, yb, argb, light);
    }

    private static void wall(PoseStack.Pose pose, VertexConsumer vc,
                             float xa, float za, float xb, float zb, float yBottom,
                             int argb, int light) {
        vertex(pose, vc, xa, 0f, za, argb, light);
        vertex(pose, vc, xa, yBottom, za, argb, light);
        vertex(pose, vc, xb, yBottom, zb, argb, light);
        vertex(pose, vc, xb, 0f, zb, argb, light);
        vertex(pose, vc, xb, 0f, zb, argb, light);
        vertex(pose, vc, xb, yBottom, zb, argb, light);
        vertex(pose, vc, xa, yBottom, za, argb, light);
        vertex(pose, vc, xa, 0f, za, argb, light);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc,
                               float x, float y, float z, int argb, int light) {
        vc.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(0.5f, 0.5f)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0f, 1f, 0f);
    }

    /** Mix 30% toward white — the "glowing" color. */
    private static int brighten(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (255 - r) * 3 / 10;
        g += (255 - g) * 3 / 10;
        b += (255 - b) * 3 / 10;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Scale to 40% — the "standby" color. */
    private static int dim(int argb) {
        int r = ((argb >> 16) & 0xFF) * 2 / 5;
        int g = ((argb >> 8) & 0xFF) * 2 / 5;
        int b = (argb & 0xFF) * 2 / 5;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Scale RGB by an arbitrary factor — bevel shadow/outline shades. */
    private static int shade(int argb, float f) {
        int r = (int) (((argb >> 16) & 0xFF) * f);
        int g = (int) (((argb >> 8) & 0xFF) * f);
        int b = (int) ((argb & 0xFF) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Mix 15% toward white — a bevel highlight gentler than brighten(). */
    private static int bevelHi(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (255 - r) * 3 / 20;
        g += (255 - g) * 3 / 20;
        b += (255 - b) * 3 / 20;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
