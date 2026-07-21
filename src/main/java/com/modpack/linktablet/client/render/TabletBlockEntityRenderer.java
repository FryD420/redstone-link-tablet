package com.modpack.linktablet.client.render;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletBlock;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.block.TabletScreenMath;
import com.modpack.linktablet.frequency.SignalApp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;

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

    /** Swivel-mount stand (1.8.0), standalone-baked like tablet_base. */
    public static final ModelResourceLocation MODEL_STAND = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID, "block/swivel_stand"));

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

        if (be.isMounted()) {
            renderMounted(be, state, apps, poseStack, buffers, packedLight, packedOverlay);
            return;
        }

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
     * Swivel mount (1.8.0): the chunk model is EMPTY for mounted
     * tablets, so this pass draws everything — the stand (attach-face
     * rotation only), then the case model and the live screen under the
     * ball-joint quaternion. The quaternion comes from the SAME
     * {@code mountBasis} the hit tests use, so clicks and pixels can't
     * drift apart. Pass order per the batching rule: baked models
     * first, then the screen's quads → icons → text.
     */
    private static void renderMounted(TabletBlockEntity be, BlockState state,
                                      List<SignalApp> apps, PoseStack poseStack,
                                      MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        var modelRenderer = mc.getBlockRenderer().getModelRenderer();

        // Stand, tilted onto the attach face (yRot is cosmetic for the
        // symmetric stand but keeps wall bases flush like the case was)
        poseStack.pushPose();
        poseStack.translate(0.5, 0.5, 0.5);
        int yRot = TabletScreenMath.yRot(state);
        int xRot = TabletScreenMath.xRot(state);
        if (yRot != 0) poseStack.mulPose(Axis.YN.rotationDegrees(yRot));
        if (xRot != 0) poseStack.mulPose(Axis.XN.rotationDegrees(xRot));
        poseStack.translate(-0.5, -0.5, -0.5);
        BakedModel stand = mc.getModelManager().getModel(MODEL_STAND);
        modelRenderer.renderModel(poseStack.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                null, stand, 1f, 1f, 1f, packedLight, packedOverlay);
        poseStack.popPose();

        // Tablet on the ball: quaternion from the shared basis
        TabletScreenMath.MountBasis basis = be.mountBasis();
        Vec3 pivot = TabletScreenMath.MountBasis.pivot(be.getBlockPos(), be.mountAttachNormal())
                .subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
        poseStack.pushPose();
        poseStack.translate(pivot.x, pivot.y, pivot.z);
        // Canonical floor frame: +X → right, +Y → normal, +Z → down
        Matrix3f orient = new Matrix3f(
                (float) basis.right().x, (float) basis.right().y, (float) basis.right().z,
                (float) basis.normal().x, (float) basis.normal().y, (float) basis.normal().z,
                (float) basis.down().x, (float) basis.down().y, (float) basis.down().z);
        poseStack.mulPose(new Quaternionf().setFromNormalized(orient));
        // Canonical panel center (0.5, 0.5/16, 0.5) lands PANEL_OFF out
        // from the pivot along the screen normal
        poseStack.translate(-0.5, TabletScreenMath.MOUNT_PANEL_OFF - 0.5 / 16.0, -0.5);

        // Case: the unrotated canonical variant of the normal block
        // model, hand-tinted (the chunk-mesh BlockColor can't reach us)
        BlockState canonical = state.setValue(TabletBlock.MOUNTED, false)
                .setValue(TabletBlock.FACE, AttachFace.FLOOR)
                .setValue(TabletBlock.FACING, Direction.NORTH)
                .setValue(TabletBlock.LANDSCAPE, false);
        int caseTint = be.getCaseColor() != null
                ? 0xFF000000 | be.getCaseColor().getTextureDiffuseColor()
                : TabletScreenRenderer.DEFAULT_CASE_TINT;
        BakedModel caseModel = mc.getBlockRenderer().getBlockModel(canonical);
        modelRenderer.renderModel(poseStack.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                canonical, caseModel,
                (caseTint >> 16 & 0xFF) / 255f, (caseTint >> 8 & 0xFF) / 255f,
                (caseTint & 0xFF) / 255f, packedLight, packedOverlay);

        // Live screen in the same canonical frame
        poseStack.translate(2 / 16f, SCREEN_HEIGHT, 1 / 16f);
        TabletScreenRenderer.render(poseStack, buffers, apps, be.isScreenList(),
                be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                packedLight, be.getHeldPips(), 1, 1, caseTint);
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
        // Mounted panels can lean outside the block space
        return box.inflate(be.isMounted() ? 1.0 : 0.5);
    }
}
