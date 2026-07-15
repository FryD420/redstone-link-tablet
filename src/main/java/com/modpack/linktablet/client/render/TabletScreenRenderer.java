package com.modpack.linktablet.client.render;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletScreenMath;
import com.modpack.linktablet.frequency.SignalApp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Draws the tablet's live mini screen: a background over the glass area
 * and the first visible apps as either icon pips (grid) or icon +
 * mini-switch rows (list), echoing the GUI's two layouts. Shared by the
 * block entity renderer and the item renderer — the caller positions the
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
     * dimensions: width (10 - 5*0.25) / 4 = 2.1875, height
     * (12 - 6*0.25) / 5 = 2.1. Click cells divide the glass evenly and
     * always contain their tile.
     */
    private static final float SPACE = 0.25f;
    private static final float TILE_W =
            (TabletScreenMath.GLASS_U1 - TabletScreenMath.GLASS_U0 - (TabletScreenMath.COLS + 1) * SPACE)
                    / TabletScreenMath.COLS;
    private static final float TILE_H =
            (TabletScreenMath.GLASS_V1 - TabletScreenMath.GLASS_V0 - (TabletScreenMath.ROWS + 1) * SPACE)
                    / TabletScreenMath.ROWS;

    private static float tileU0(int col) {
        return TabletScreenMath.GLASS_U0 + SPACE + col * (TILE_W + SPACE);
    }

    private static float tileV0(int row) {
        return TabletScreenMath.GLASS_V0 + SPACE + row * (TILE_H + SPACE);
    }

    private static final int BG_LIT = 0xFF223044;
    private static final int BG_OFF = 0xFF12151B;
    /** Hollow-ring (momentary) thickness in texels. */
    private static final float RING = 0.5f;

    private static final float GRID_ICON = 1.75f;
    private static final float GRID_ICON_MOMENTARY = 1.25f;

    // List row mini-switch, mirroring the GUI's toggle colors
    private static final int TRACK = 0xFF2A2E37;
    private static final int SWITCH_ON = 0xFF2F855A;
    private static final int SWITCH_OFF = 0xFF444955;
    private static final int KNOB_ON = 0xFF4ADE80;
    private static final int KNOB_OFF = 0xFF9AA0AC;
    private static final float SWITCH_W = 4f;
    private static final float KNOB_W = 2f;
    private static final float LIST_ICON = 1.75f;

    private TabletScreenRenderer() {}

    /**
     * @param list     the tablet's stored screen layout (switch list vs
     *                 pip grid)
     * @param backlit  true when the screen's emissive state is on (block
     *                 LIT / item GUI-open) — brightens the background
     * @param packedLight world light used for non-glowing parts
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers,
                              List<SignalApp> apps, boolean list, boolean backlit, int packedLight) {
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
                0f, backlit ? BG_LIT : BG_OFF, bgLight);

        // All quads first, all icons second: an icon's model may need a
        // render type that shares the fallback buffer, which ends any
        // in-progress shared batch — interleaving would leave our quad
        // buffer dead mid-loop ("Not building!" crash with modded icons).
        int count = TabletScreenMath.visibleApps(apps.size(), list);
        for (int i = 0; i < count; i++) {
            SignalApp app = apps.get(i);
            int color = app.color() | 0xFF000000;
            if (list) {
                renderSwitchRow(pose, vc, i, app, color, packedLight);
            } else {
                renderPip(pose, vc, i, app, color, packedLight);
            }
        }
        for (int i = 0; i < count; i++) {
            SignalApp app = apps.get(i);
            boolean glowing = app.active() && !app.momentary();
            int light = glowing ? LightTexture.FULL_BRIGHT : packedLight;
            float cu, cv, size;
            if (list) {
                cu = TabletScreenMath.GLASS_U0 + SPACE + TILE_H / 2f;
                cv = tileV0(i) + TILE_H / 2f;
                size = LIST_ICON;
            } else {
                cu = tileU0(i % TabletScreenMath.COLS) + TILE_W / 2f;
                cv = tileV0(i / TabletScreenMath.COLS) + TILE_H / 2f;
                size = app.momentary() ? GRID_ICON_MOMENTARY : GRID_ICON;
            }
            renderIcon(poseStack, buffers, app.iconStack(), cu, cv, size, light);
        }
    }

    /** Grid entry: colored plate, glow border = state (icon drawn later). */
    private static void renderPip(PoseStack.Pose pose, VertexConsumer vc,
                                  int i, SignalApp app, int color, int packedLight) {
        float u0 = tileU0(i % TabletScreenMath.COLS);
        float v0 = tileV0(i / TabletScreenMath.COLS);
        float u1 = u0 + TILE_W;
        float v1 = v0 + TILE_H;
        float cu = (u0 + u1) / 2f;
        float cv = (v0 + v1) / 2f;

        if (app.momentary()) {
            ring(pose, vc, u0, v0, u1, v1, dim(color), packedLight);
        } else if (app.active()) {
            fillRect(pose, vc, u0, v0, u1, v1, LAYER, brighten(color), LightTexture.FULL_BRIGHT);
        } else {
            fillRect(pose, vc, u0, v0, u1, v1, LAYER, dim(color), packedLight);
        }
    }

    /** List row: colored chip left, mini switch right (icon drawn later). */
    private static void renderSwitchRow(PoseStack.Pose pose, VertexConsumer vc,
                                        int i, SignalApp app, int color, int packedLight) {
        float v0 = tileV0(i);
        float v1 = v0 + TILE_H;
        float u0 = TabletScreenMath.GLASS_U0 + SPACE;
        float u1 = TabletScreenMath.GLASS_U1 - SPACE;
        boolean on = app.active() && !app.momentary();
        int stateLight = on ? LightTexture.FULL_BRIGHT : packedLight;

        fillRect(pose, vc, u0, v0, u1, v1, LAYER, TRACK, packedLight);

        // Icon chip in the app's color, left end
        fillRect(pose, vc, u0, v0, u0 + TILE_H, v1, LAYER * 2,
                on ? brighten(color) : dim(color), stateLight);

        float su0 = u1 - SWITCH_W;
        if (app.momentary()) {
            // Push button: dark button with a centered dot
            fillRect(pose, vc, su0, v0, u1, v1, LAYER * 2, SWITCH_OFF, packedLight);
            float mid = (su0 + u1) / 2f;
            float cv = (v0 + v1) / 2f;
            fillRect(pose, vc, mid - 0.5f, cv - 0.5f, mid + 0.5f, cv + 0.5f,
                    LAYER * 3, KNOB_OFF, packedLight);
        } else {
            fillRect(pose, vc, su0, v0, u1, v1, LAYER * 2,
                    on ? SWITCH_ON : SWITCH_OFF, stateLight);
            float knobU0 = on ? u1 - KNOB_W : su0;
            fillRect(pose, vc, knobU0, v0, knobU0 + KNOB_W, v1, LAYER * 3,
                    on ? KNOB_ON : KNOB_OFF, stateLight);
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
                             float u0, float v0, float u1, float v1, int color, int light) {
        fillRect(pose, vc, u0, v0, u1, v0 + RING, LAYER, color, light);
        fillRect(pose, vc, u0, v1 - RING, u1, v1, LAYER, color, light);
        fillRect(pose, vc, u0, v0 + RING, u0 + RING, v1 - RING, LAYER, color, light);
        fillRect(pose, vc, u1 - RING, v0 + RING, u1, v1 - RING, LAYER, color, light);
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
