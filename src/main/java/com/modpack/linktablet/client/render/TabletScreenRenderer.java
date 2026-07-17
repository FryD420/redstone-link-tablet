package com.modpack.linktablet.client.render;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletScreenMath;
import com.modpack.linktablet.block.TabletScreenMath.GridLayout;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.frequency.SignalApp;
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
 * and the first visible apps as either icon pips (grid) or icon +
 * mini-switch rows (list), echoing the GUI's two layouts. The grid sizes
 * itself to the app count ({@link TabletScreenMath#gridLayout}) — one app
 * fills the glass, tiles shrink as apps are added. Shared by the block
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
     * @param list     the tablet's stored screen layout (switch list vs
     *                 pip grid)
     * @param rot      screen content rotation, quarter turns CW (0–3) —
     *                 must match the {@code rot} the hit-test uses
     * @param theme    the tablet's stored UI theme
     * @param backlit  true when the screen's emissive state is on (block
     *                 LIT / item GUI-open) — brightens the background
     * @param packedLight world light used for non-glowing parts
     * @param heldPips indices of momentary apps currently held down —
     *                 those pips render lit
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers,
                              List<SignalApp> apps, boolean list, int rot, ScreenTheme theme,
                              boolean backlit, int packedLight, Set<Integer> heldPips) {
        // Content rotation: draw in a "logical" glass (landscape when rot
        // is odd) spun about the physical glass center. The pivot maps
        // the logical rect exactly onto the physical one; the hit-test
        // applies the same quarter-turns in reverse (TabletScreenMath).
        float glassW = TabletScreenMath.glassW(rot);
        float glassH = TabletScreenMath.glassH(rot);
        float u1 = TabletScreenMath.GLASS_U0 + glassW;
        float v1 = TabletScreenMath.GLASS_V0 + glassH;
        poseStack.pushPose();
        if ((rot & 3) != 0) {
            float pivotU = (TabletScreenMath.GLASS_U0 + TabletScreenMath.GLASS_U1) / 2f / 16f;
            float pivotV = (TabletScreenMath.GLASS_V0 + TabletScreenMath.GLASS_V1) / 2f / 16f;
            float logicalU = (TabletScreenMath.GLASS_U0 + glassW / 2f) / 16f;
            float logicalV = (TabletScreenMath.GLASS_V0 + glassH / 2f) / 16f;
            poseStack.translate(pivotU, 0, pivotV);
            poseStack.mulPose(Axis.YN.rotationDegrees(90 * (rot & 3)));
            poseStack.translate(-logicalU, 0, -logicalV);
        }
        VertexConsumer vc = buffers.getBuffer(SCREEN_TYPE);
        PoseStack.Pose pose = poseStack.last();

        // The background bleeds half a texel under the bezel ring (the
        // screen sits below the lip, so the ring covers the overdraw) —
        // otherwise the baked screen art shimmers through the seam.
        float bleed = 0.5f;
        int bgLight = backlit ? LightTexture.FULL_BRIGHT : packedLight;
        int bg = backlit ? theme.screenBgLit : theme.screenBgOff;
        fillRect(pose, vc,
                TabletScreenMath.GLASS_U0 - bleed, TabletScreenMath.GLASS_V0 - bleed,
                u1 + bleed, v1 + bleed,
                0f, bg, bgLight);

        // Theme frame hugging the glass edge — the GUI panel's rail
        // brought in-world (1.5.1). Lives in the existing margins, so
        // layout and hit-tests are untouched. A dark seam (not a bright
        // ridge) separates rail from canvas: on themes whose border color
        // sits near the unlit background a bright ridge reads as a stray
        // glowing rectangle, while a shadow seam degrades to invisible.
        float frameW = 0.4f;
        int frame = theme.bodyOuter;
        ring(pose, vc, TabletScreenMath.GLASS_U0, TabletScreenMath.GLASS_V0, u1, v1,
                LAYER * 0.5f, frame, bgLight, frameW);
        ring(pose, vc, TabletScreenMath.GLASS_U0 + frameW, TabletScreenMath.GLASS_V0 + frameW,
                u1 - frameW, v1 - frameW, LAYER * 0.5f, shade(frame, 0.5f), bgLight, 0.12f);

        int count = TabletScreenMath.visibleApps(apps.size(), list);
        GridLayout grid = TabletScreenMath.gridLayout(apps.size(), rot);
        float rowH = listRowH(glassH);
        float tileW = tileSize(glassW, grid.cols());
        float tileH = tileSize(glassH, grid.rows());
        boolean labels = !list && grid.cells() <= LABEL_CELLS_MAX;
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
            SignalApp app = apps.get(i);
            int color = app.color() | 0xFF000000;
            boolean held = heldPips.contains(i);
            if (list) {
                renderSwitchRow(pose, vc, i, app, color, theme, packedLight, held, rowH, u1);
            } else {
                float u0 = tileU0(i % grid.cols(), tileW);
                float v0 = tileV0(i / grid.cols(), tileH);
                renderPip(pose, vc, u0, v0, u0 + tileW, v0 + tileH, app, color, theme,
                        packedLight, ringW, held, labelZone);
            }
        }
        for (int i = 0; i < count; i++) {
            SignalApp app = apps.get(i);
            boolean glowing = (app.active() && !app.momentary()) || heldPips.contains(i);
            int light = glowing ? LightTexture.FULL_BRIGHT : packedLight;
            float cu, cv, size;
            if (list) {
                cu = TabletScreenMath.GLASS_U0 + SPACE + rowH / 2f;
                cv = listV0(i, rowH) + rowH / 2f;
                // Icon fills the inset chip exactly, like the GUI's 16px-on-16px
                size = Math.min(LIST_ICON, rowH * 2f / 3f);
            } else {
                cu = tileU0(i % grid.cols(), tileW) + tileW / 2f;
                cv = tileV0(i / grid.cols(), tileH) + (tileH - labelZone) / 2f;
                float cell = Math.min(tileW, tileH - labelZone);
                if (cell >= CHIP_MIN_CELL) {
                    // Icon fills the inset chip exactly, like the list rows
                    size = Math.min(cell - 2 * chipInset(cell), ICON_MAX);
                } else {
                    size = Mth.clamp(cell * ICON_FRAC, ICON_MIN, ICON_MAX);
                }
                if (app.momentary()) size *= MOMENTARY_ICON_FRAC;
            }
            renderIcon(poseStack, buffers, app.iconStack(), cu, cv, size, light);
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
                SignalApp app = apps.get(i);
                float controlU0 = u1 - SPACE - SWITCH_MARGIN
                        - (app.slider() ? SWITCH_W * 1.6f : SWITCH_W);
                float textU1 = controlU0 - 0.4f;
                if (app.slider()) {
                    String level = String.valueOf(app.strength());
                    float levelW = font.width(level) * LIST_TEXT_H / FONT_LINE;
                    float levelTop = listV0(i, rowH) + (rowH - LIST_TEXT_H) / 2f;
                    drawLabel(poseStack, buffers, font, level, controlU0 - 0.3f - levelW,
                            levelTop, scale, false, false, theme.textPrimary, bgLight);
                    textU1 = controlU0 - 0.6f - levelW;
                }
                int maxPx = (int) ((textU1 - textU0) * FONT_LINE / LIST_TEXT_H);
                FittedLabel label = fitLabel(font, app.name(), maxPx);
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
                FittedLabel label = fitLabel(font, apps.get(i).name(), maxPx);
                if (label.text().isBlank()) continue;
                float cu = tileU0(i % grid.cols(), tileW) + tileW / 2f;
                // Bottom-aligned so a shrunk label hugs the tile edge
                float top = tileV0(i / grid.cols(), tileH) + tileH - textH * label.fit() - SPACE;
                // White with a black outline: the label sits on the app's
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
                SignalApp app = apps.get(i);
                if (!app.slider()) continue;
                String level = String.valueOf(app.strength());
                float u0 = tileU0(i % grid.cols(), tileW);
                float v0 = tileV0(i / grid.cols(), tileH);
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
     * structure — themed plaque with an inset app-color chip
     * ({@link #renderChipTile}); dense grids keep the classic pip where
     * the whole cell is the app color.
     */
    private static void renderPip(PoseStack.Pose pose, VertexConsumer vc,
                                  float u0, float v0, float u1, float v1,
                                  SignalApp app, int color, ScreenTheme theme,
                                  int packedLight, float ringW, boolean held,
                                  float labelZone) {
        float cell = Math.min(u1 - u0, v1 - v0 - labelZone);
        if (cell >= CHIP_MIN_CELL) {
            renderChipTile(pose, vc, u0, v0, u1, v1, app, color, theme,
                    packedLight, ringW, held, labelZone, cell);
            return;
        }
        if (app.slider()) {
            // Plate lights with any output; a top strip shows the value
            boolean on = app.strength() > 0;
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
                float fill = bu0 + (bu1 - bu0) * app.fillFraction();
                fillRect(pose, vc, bu0, bv0, fill, bv0 + barH, LAYER * 3,
                        brighten(color), LightTexture.FULL_BRIGHT);
            }
            // Groove hairlines above the fill (3x), below the icons (3.5x)
            insetGroove(pose, vc, bu0, bv0, bu1, bv0 + barH, LAYER * 3.25f,
                    SLIDER_TRACK, packedLight);
            return;
        }
        if (app.momentary()) {
            if (held) {
                // Pressed: fills solid and glows, like an active toggle
                fillRect(pose, vc, u0, v0, u1, v1, LAYER, brighten(color), LightTexture.FULL_BRIGHT);
                raisedBevel(pose, vc, u0, v0, u1, v1, LAYER * 1.5f, brighten(color), LightTexture.FULL_BRIGHT);
            } else {
                ring(pose, vc, u0, v0, u1, v1, LAYER, dim(color), packedLight, ringW);
            }
        } else if (app.active()) {
            fillRect(pose, vc, u0, v0, u1, v1, LAYER, brighten(color), LightTexture.FULL_BRIGHT);
            raisedBevel(pose, vc, u0, v0, u1, v1, LAYER * 1.5f, brighten(color), LightTexture.FULL_BRIGHT);
        } else {
            fillRect(pose, vc, u0, v0, u1, v1, LAYER, dim(color), packedLight);
            raisedBevel(pose, vc, u0, v0, u1, v1, LAYER * 1.5f, dim(color), packedLight);
        }
    }

    /**
     * Grid tile as a themed plaque with an inset app-color chip — the
     * list rows' look (and the GUI grid's). The chip centers on the icon
     * center (labels reserve the tile bottom) and carries the state:
     * bright + full-bright when on, dim otherwise, hollow ring while a
     * momentary app is unheld. Slider value strips keep their exact
     * pre-chip geometry — {@code TabletScreenMath.sliderBarU} maps drags
     * against it — so the chip sits at LAYER*1.75, under the strip (2x)
     * it can graze on small tiles.
     */
    private static void renderChipTile(PoseStack.Pose pose, VertexConsumer vc,
                                       float u0, float v0, float u1, float v1,
                                       SignalApp app, int color, ScreenTheme theme,
                                       int packedLight, float ringW, boolean held,
                                       float labelZone, float cell) {
        boolean on = (app.active() && !app.momentary()) || held;
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
        if (app.momentary() && !held) {
            ring(pose, vc, cu - half, cv - half, cu + half, cv + half, LAYER * 1.75f,
                    dim(color), packedLight, ringW);
        } else {
            fillRect(pose, vc, cu - half, cv - half, cu + half, cv + half, LAYER * 1.75f,
                    on ? brighten(color) : dim(color), stateLight);
        }

        if (app.slider()) {
            float inset = TabletScreenMath.sliderInset(u1 - u0);
            float barH = sliderBarH(v1 - v0);
            float bu0 = u0 + inset;
            float bu1 = u1 - inset;
            float bv0 = v0 + inset;
            fillRect(pose, vc, bu0, bv0, bu1, bv0 + barH, LAYER * 2, SLIDER_TRACK, packedLight);
            if (app.strength() > 0) {
                float fill = bu0 + (bu1 - bu0) * app.fillFraction();
                fillRect(pose, vc, bu0, bv0, fill, bv0 + barH, LAYER * 3,
                        brighten(color), LightTexture.FULL_BRIGHT);
            }
            insetGroove(pose, vc, bu0, bv0, bu1, bv0 + barH, LAYER * 3.25f,
                    SLIDER_TRACK, packedLight);
        }
    }

    /** List row: colored chip left, mini switch right (icon drawn later). */
    private static void renderSwitchRow(PoseStack.Pose pose, VertexConsumer vc,
                                        int i, SignalApp app, int color,
                                        ScreenTheme theme, int packedLight, boolean held,
                                        float rowH, float glassU1) {
        float v0 = listV0(i, rowH);
        float v1 = v0 + rowH;
        float u0 = TabletScreenMath.GLASS_U0 + SPACE;
        float u1 = glassU1 - SPACE;
        boolean on = (app.active() && !app.momentary()) || held;
        int stateLight = on ? LightTexture.FULL_BRIGHT : packedLight;

        fillRect(pose, vc, u0, v0, u1, v1, LAYER, theme.screenTrack, packedLight);
        // Rows read as raised plaques, like the GUI's list rows
        raisedBevel(pose, vc, u0, v0, u1, v1, LAYER * 1.5f, theme.screenTrack, packedLight);

        // Icon chip in the app's color, inset INSIDE the row plaque like
        // the GUI's chip (its 4px-in-24 margin, scaled); flat — color IS
        // the content
        float chipInset = rowH / 6f;
        fillRect(pose, vc, u0 + chipInset, v0 + chipInset, u0 + rowH - chipInset, v1 - chipInset,
                LAYER * 2, on ? brighten(color) : dim(color), stateLight);

        // Track vertically centered in the row and inset from the row's
        // right edge, GUI-style
        float su1 = u1 - SWITCH_MARGIN;
        float su0 = su1 - SWITCH_W;
        float sv0 = v0 + (rowH - SWITCH_H) / 2f;
        float sv1 = sv0 + SWITCH_H;
        if (app.slider()) {
            // Wider mini track with a knob at the current value
            float tu0 = su1 - SWITCH_W * 1.6f;
            float tv0 = v0 + (rowH - SWITCH_H * 0.5f) / 2f;
            float tv1 = tv0 + SWITCH_H * 0.5f;
            fillRect(pose, vc, tu0, tv0, su1, tv1, LAYER * 2, theme.switchOff, packedLight);
            float knob = tu0 + (su1 - tu0 - KNOB_W) * app.fillFraction();
            if (app.strength() > 0) {
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
        if (app.momentary()) {
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
     * The app's item icon rendered flat on the screen. Depth is squashed
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
        poseStack.translate(centerU / 16f, 0.0035f, centerV / 16f);
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
