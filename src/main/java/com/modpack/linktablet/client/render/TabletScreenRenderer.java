package com.modpack.linktablet.client.render;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletScreenMath;
import com.modpack.linktablet.block.TabletScreenMath.GridLayout;
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
     * evenly and always contain their tile.
     */
    private static final float SPACE = 0.25f;

    private static float tileSize(float span, int n) {
        return (span - (n + 1) * SPACE) / n;
    }

    private static float tileU0(int col, float tileW) {
        return TabletScreenMath.GLASS_U0 + SPACE + col * (tileW + SPACE);
    }

    private static float tileV0(int row, float tileH) {
        return TabletScreenMath.GLASS_V0 + SPACE + row * (tileH + SPACE);
    }

    /** List rows keep the fixed five-row height regardless of app count. */
    private static final float LIST_ROW_H =
            tileSize(TabletScreenMath.GLASS_V1 - TabletScreenMath.GLASS_V0, TabletScreenMath.LIST_ROWS);

    private static float listV0(int row) {
        return tileV0(row, LIST_ROW_H);
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
    private static final float SWITCH_W = 2.0f;
    private static final float SWITCH_H = 1.1f;
    /** Right inset from the row edge — the GUI's 4px-in-24 margin, scaled. */
    private static final float SWITCH_MARGIN = 0.35f;
    private static final float KNOB_W = 0.75f;
    private static final float KNOB_INSET = 0.2f;
    private static final float LIST_ICON = 1.75f;
    private static final float LIST_TEXT_H = 0.8f;

    private TabletScreenRenderer() {}

    /**
     * @param list     the tablet's stored screen layout (switch list vs
     *                 pip grid)
     * @param theme    the tablet's stored UI theme
     * @param backlit  true when the screen's emissive state is on (block
     *                 LIT / item GUI-open) — brightens the background
     * @param packedLight world light used for non-glowing parts
     * @param heldPips indices of momentary apps currently held down —
     *                 those pips render lit
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers,
                              List<SignalApp> apps, boolean list, ScreenTheme theme,
                              boolean backlit, int packedLight, Set<Integer> heldPips) {
        VertexConsumer vc = buffers.getBuffer(SCREEN_TYPE);
        PoseStack.Pose pose = poseStack.last();

        // The background bleeds half a texel under the bezel ring (the
        // screen sits below the lip, so the ring covers the overdraw) —
        // otherwise the baked screen art shimmers through the seam.
        float bleed = 0.5f;
        int bgLight = backlit ? LightTexture.FULL_BRIGHT : packedLight;
        fillRect(pose, vc,
                TabletScreenMath.GLASS_U0 - bleed, TabletScreenMath.GLASS_V0 - bleed,
                TabletScreenMath.GLASS_U1 + bleed, TabletScreenMath.GLASS_V1 + bleed,
                0f, backlit ? theme.screenBgLit : theme.screenBgOff, bgLight);

        int count = TabletScreenMath.visibleApps(apps.size(), list);
        GridLayout grid = TabletScreenMath.gridLayout(apps.size());
        float tileW = tileSize(TabletScreenMath.GLASS_U1 - TabletScreenMath.GLASS_U0, grid.cols());
        float tileH = tileSize(TabletScreenMath.GLASS_V1 - TabletScreenMath.GLASS_V0, grid.rows());
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
                renderSwitchRow(pose, vc, i, app, color, theme, packedLight, held);
            } else {
                float u0 = tileU0(i % grid.cols(), tileW);
                float v0 = tileV0(i / grid.cols(), tileH);
                renderPip(pose, vc, u0, v0, u0 + tileW, v0 + tileH, app, color, packedLight, ringW, held);
            }
        }
        for (int i = 0; i < count; i++) {
            SignalApp app = apps.get(i);
            boolean glowing = (app.active() && !app.momentary()) || heldPips.contains(i);
            int light = glowing ? LightTexture.FULL_BRIGHT : packedLight;
            float cu, cv, size;
            if (list) {
                cu = TabletScreenMath.GLASS_U0 + SPACE + LIST_ROW_H / 2f;
                cv = listV0(i) + LIST_ROW_H / 2f;
                size = LIST_ICON;
            } else {
                cu = tileU0(i % grid.cols(), tileW) + tileW / 2f;
                cv = tileV0(i / grid.cols(), tileH) + (tileH - labelZone) / 2f;
                size = Mth.clamp(Math.min(tileW, tileH - labelZone) * ICON_FRAC, ICON_MIN, ICON_MAX);
                if (app.momentary()) size *= MOMENTARY_ICON_FRAC;
            }
            renderIcon(poseStack, buffers, app.iconStack(), cu, cv, size, light);
        }
        // Text pass (third): big-tile labels in grid mode, row names in
        // list mode.
        Font font = Minecraft.getInstance().font;
        if (list) {
            float scale = LIST_TEXT_H / 16f / FONT_LINE;
            // Between the icon chip and the switch
            float textU0 = TabletScreenMath.GLASS_U0 + SPACE + LIST_ROW_H + 0.4f;
            float textU1 = TabletScreenMath.GLASS_U1 - SPACE - SWITCH_MARGIN - SWITCH_W - 0.4f;
            int maxPx = (int) ((textU1 - textU0) * FONT_LINE / LIST_TEXT_H);
            for (int i = 0; i < count; i++) {
                String name = font.plainSubstrByWidth(apps.get(i).name(), maxPx);
                if (name.isBlank()) continue;
                float top = listV0(i) + (LIST_ROW_H - LIST_TEXT_H) / 2f;
                // Plain text: rows sit on the themed track, no outline needed
                drawLabel(poseStack, buffers, font, name, textU0, top, scale,
                        false, false, theme.textPrimary, bgLight);
            }
        } else if (labels) {
            // Texel width budget → font pixels at this label height
            int maxPx = (int) ((tileW - 2 * SPACE) * FONT_LINE / textH);
            float scale = textH / 16f / FONT_LINE;
            for (int i = 0; i < count; i++) {
                String name = font.plainSubstrByWidth(apps.get(i).name(), maxPx);
                if (name.isBlank()) continue;
                float cu = tileU0(i % grid.cols(), tileW) + tileW / 2f;
                float top = tileV0(i / grid.cols(), tileH) + tileH - textH - SPACE;
                // White with a black outline: the label sits on the app's
                // own tile color (any brightness), not the theme surface
                drawLabel(poseStack, buffers, font, name, cu, top, scale,
                        true, true, 0xFFFFFFFF, bgLight);
            }
        }
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
        poseStack.translate(u / 16f, 0.004f, topV / 16f);
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

    /** Grid entry: colored plate, glow border = state (icon drawn later). */
    private static void renderPip(PoseStack.Pose pose, VertexConsumer vc,
                                  float u0, float v0, float u1, float v1,
                                  SignalApp app, int color, int packedLight, float ringW,
                                  boolean held) {
        if (app.momentary()) {
            if (held) {
                // Pressed: fills solid and glows, like an active toggle
                fillRect(pose, vc, u0, v0, u1, v1, LAYER, brighten(color), LightTexture.FULL_BRIGHT);
            } else {
                ring(pose, vc, u0, v0, u1, v1, dim(color), packedLight, ringW);
            }
        } else if (app.active()) {
            fillRect(pose, vc, u0, v0, u1, v1, LAYER, brighten(color), LightTexture.FULL_BRIGHT);
        } else {
            fillRect(pose, vc, u0, v0, u1, v1, LAYER, dim(color), packedLight);
        }
    }

    /** List row: colored chip left, mini switch right (icon drawn later). */
    private static void renderSwitchRow(PoseStack.Pose pose, VertexConsumer vc,
                                        int i, SignalApp app, int color,
                                        ScreenTheme theme, int packedLight, boolean held) {
        float v0 = listV0(i);
        float v1 = v0 + LIST_ROW_H;
        float u0 = TabletScreenMath.GLASS_U0 + SPACE;
        float u1 = TabletScreenMath.GLASS_U1 - SPACE;
        boolean on = (app.active() && !app.momentary()) || held;
        int stateLight = on ? LightTexture.FULL_BRIGHT : packedLight;

        fillRect(pose, vc, u0, v0, u1, v1, LAYER, theme.screenTrack, packedLight);

        // Icon chip in the app's color, left end
        fillRect(pose, vc, u0, v0, u0 + LIST_ROW_H, v1, LAYER * 2,
                on ? brighten(color) : dim(color), stateLight);

        // Track vertically centered in the row and inset from the row's
        // right edge, GUI-style
        float su1 = u1 - SWITCH_MARGIN;
        float su0 = su1 - SWITCH_W;
        float sv0 = v0 + (LIST_ROW_H - SWITCH_H) / 2f;
        float sv1 = sv0 + SWITCH_H;
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

    /** Hollow ring — the momentary marker, matching the GUI's language. */
    private static void ring(PoseStack.Pose pose, VertexConsumer vc,
                             float u0, float v0, float u1, float v1, int color, int light, float w) {
        fillRect(pose, vc, u0, v0, u1, v0 + w, LAYER, color, light);
        fillRect(pose, vc, u0, v1 - w, u1, v1, LAYER, color, light);
        fillRect(pose, vc, u0, v0 + w, u0 + w, v1 - w, LAYER, color, light);
        fillRect(pose, vc, u1 - w, v0 + w, u1, v1 - w, LAYER, color, light);
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
}
