package com.modpack.linktablet.client.render;

import com.modpack.linktablet.block.TabletBlock;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.block.TabletScreenMath;
import com.modpack.linktablet.frequency.SignalApp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Draws the live app pips on a placed tablet's screen face. The baked
 * model (bezel, case, LIT texture swap) is untouched; this only layers
 * the mini screen slightly proud of the bezel. Tablets with no apps
 * render nothing here and look exactly like the plain model.
 */
public class TabletBlockEntityRenderer implements BlockEntityRenderer<TabletBlockEntity> {

    /** Screen content sits just above the glass face at 1 texel —
     * recessed below the bezel lip (1.05), like a real inset display. */
    private static final float SCREEN_HEIGHT = 1f / 16f + 0.001f;

    /** Merged surfaces float ONE continuous panel just above the bezel
     * lips instead — it spans every member, covering interior bezels
     * and case seams so the wall reads as a single big screen. */
    private static final float MERGED_SCREEN_HEIGHT = 1.1f / 16f + 0.001f;

    public TabletBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TabletBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // Merged-surface parts draw nothing: the controller's pass covers
        // every member's glass (1.7.0).
        if (be.isSurfacePart()) return;
        // Rendered even with no apps: the flat glass replaces the baked
        // screen art everywhere, so empty and in-use tablets match.
        List<SignalApp> apps = be.getApps();
        BlockState state = be.getBlockState();
        if (!state.hasProperty(TabletBlock.LIT)) return;

        poseStack.pushPose();
        // Same rotation the blockstate applies to the baked model; the
        // landscape model's baked pre-rotation goes last (it's applied
        // to vertices first).
        poseStack.translate(0.5, 0.5, 0.5);
        int yRot = TabletScreenMath.yRot(state);
        int xRot = TabletScreenMath.xRot(state);
        int preRot = TabletScreenMath.preRot(state);
        if (yRot != 0) poseStack.mulPose(Axis.YN.rotationDegrees(yRot));
        if (xRot != 0) poseStack.mulPose(Axis.XN.rotationDegrees(xRot));
        if (preRot != 0) poseStack.mulPose(Axis.YN.rotationDegrees(preRot));
        poseStack.translate(-0.5, -0.5, -0.5);
        // Screen artwork origin in the canonical floor/north frame
        poseStack.translate(2 / 16f,
                be.isSurfaceController() ? MERGED_SCREEN_HEIGHT : SCREEN_HEIGHT, 1 / 16f);

        int caseTint = be.getCaseColor() != null
                ? 0xFF000000 | be.getCaseColor().getTextureDiffuseColor()
                : TabletScreenRenderer.DEFAULT_CASE_TINT;
        TabletScreenRenderer.render(poseStack, buffers, apps, be.isScreenList(),
                be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                packedLight, be.getHeldPips(), be.getSurfaceW(), be.getSurfaceH(), caseTint);
        poseStack.popPose();
    }

    /**
     * A controller draws across every member block — without widening
     * the box, frustum culling clips the surface whenever the controller
     * block itself leaves the view.
     */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(TabletBlockEntity be) {
        net.minecraft.core.BlockPos pos = be.getBlockPos();
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos);
        if (be.isSurfaceController()) {
            BlockState state = be.getBlockState();
            net.minecraft.core.BlockPos far = pos
                    .relative(TabletScreenMath.screenRight(state), be.getSurfaceW() - 1)
                    .relative(TabletScreenMath.screenDown(state), be.getSurfaceH() - 1);
            box = box.minmax(new net.minecraft.world.phys.AABB(far));
        }
        return box.inflate(0.5);
    }
}
