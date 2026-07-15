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

    public TabletBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TabletBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        List<SignalApp> apps = be.getApps();
        if (apps.isEmpty()) return;
        BlockState state = be.getBlockState();
        if (!state.hasProperty(TabletBlock.LIT)) return;

        poseStack.pushPose();
        // Same rotation the blockstate applies to the baked model
        poseStack.translate(0.5, 0.5, 0.5);
        int yRot = TabletScreenMath.yRot(state);
        int xRot = TabletScreenMath.xRot(state);
        if (yRot != 0) poseStack.mulPose(Axis.YN.rotationDegrees(yRot));
        if (xRot != 0) poseStack.mulPose(Axis.XN.rotationDegrees(xRot));
        poseStack.translate(-0.5, -0.5, -0.5);
        // Screen artwork origin in the canonical floor/north frame
        poseStack.translate(2 / 16f, SCREEN_HEIGHT, 1 / 16f);

        TabletScreenRenderer.render(poseStack, buffers, apps, be.isScreenList(),
                state.getValue(TabletBlock.LIT), packedLight);
        poseStack.popPose();
    }
}
