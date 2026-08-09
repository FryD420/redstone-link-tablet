package com.modpack.linktablet.client.render;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.Program;
import com.modpack.linktablet.block.TabletBlock;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.block.TabletScreenMath;
import com.modpack.linktablet.frequency.Signal;
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
 * Draws the live signal pips on a placed tablet's screen face. The baked
 * model (bezel, case, LIT texture swap) is untouched; this only layers
 * the mini screen slightly proud of the bezel. Tablets with no signals
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
        // Kiosk programs (1.10.0): the screen shows whatever program the
        // controller's synced nav byte says — launcher faces render the
        // roster tiles through the same pipeline the signals use (grid
        // forced, no held pips; the server hit-test mirrors both), and
        // other programs get their own face pass (clock: text-only).
        com.modpack.linktablet.api.TabletProgram program = be.currentProgram();
        boolean launcher = program == Program.LAUNCHER;
        // Rendered even with no signals: the flat glass replaces the baked
        // screen art everywhere, so empty and in-use tablets match.
        List<Signal> signals = launcher
                ? TabletScreenRenderer.launcherTiles(be.getHomeApps()) : be.getSignals();
        BlockState state = be.getBlockState();
        if (!state.hasProperty(TabletBlock.LIT)) return;

        if (be.isMounted()) {
            renderMounted(be, state, signals, program, poseStack, buffers, packedLight,
                    packedOverlay, partialTick);
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
        renderFace(be, state, signals, program, poseStack, buffers, packedLight,
                be.getSurfaceW(), be.getSurfaceH(), caseTint, partialTick);
        poseStack.popPose();
    }

    /**
     * One face dispatch for both the flat and mounted passes: built-ins
     * keep their bespoke faces, addon programs draw through their API
     * {@code facePainter} (buffered — the three-pass order is enforced
     * by the flush), and everything else labels the door.
     */
    private static void renderFace(TabletBlockEntity be, BlockState state, List<Signal> signals,
                                   com.modpack.linktablet.api.TabletProgram program,
                                   PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                                   int surfaceW, int surfaceH, int caseTint, float partialTick) {
        boolean launcher = program == Program.LAUNCHER;
        if (program instanceof Program builtin) {
            switch (builtin) {
                case CLOCK -> TabletScreenRenderer.renderClockFace(poseStack, buffers,
                        be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                        packedLight, surfaceW, surfaceH, caseTint);
                case CALCULATOR -> TabletScreenRenderer.renderCalcFace(poseStack, buffers,
                        be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                        packedLight, surfaceW, surfaceH, caseTint);
                case GAUGES -> TabletScreenRenderer.renderGaugesFace(poseStack, buffers,
                        be.getGauges(), gaugeReadings(be),
                        be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                        packedLight, surfaceW, surfaceH, caseTint);
                case MONITOR -> TabletScreenRenderer.renderMonitorFace(poseStack, buffers,
                        com.modpack.linktablet.frequency.MonitorChannels.channelsOf(
                                be.getSignals(), be.getGauges(), be.getMonitorProbes()),
                        be.monitorCounts(), be.monitorPower(),
                        be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                        packedLight, surfaceW, surfaceH, caseTint);
                case TWITCH -> TabletScreenRenderer.renderTwitchFace(poseStack, buffers,
                        be.getTwitchChannel(),
                        be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                        packedLight, surfaceW, surfaceH, caseTint);
                // Paint (1.11.0): games' constant, but this face has a
                // bespoke wall picture, so it needs its own case ABOVE the
                // games' fall-through to the label-face default below.
                case PAINT -> TabletScreenRenderer.renderPaintFace(poseStack, buffers, be,
                        be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                        packedLight, surfaceW, surfaceH, caseTint);
                // Launcher faces honor list mode too (test pass 2) — plain
                // rows, since roster rows are doors, not toggles
                case LAUNCHER, SIGNALS -> TabletScreenRenderer.render(poseStack, buffers, signals,
                        be.isScreenList(),
                        be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                        packedLight, launcher ? java.util.Set.of() : be.getHeldPips(),
                        surfaceW, surfaceH, caseTint, launcher);
                // Programs with no bespoke face show their name — the tap
                // opens the GUI, the face just labels the door
                default -> TabletScreenRenderer.renderLabelFace(poseStack, buffers,
                        be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                        packedLight, surfaceW, surfaceH, caseTint,
                        program.displayName().getString());
            }
            return;
        }
        var client = com.modpack.linktablet.client.ProgramClients.get(program.key());
        var painter = client == null ? null : client.facePainter();
        if (painter != null) {
            TabletScreenRenderer.renderAddonFace(poseStack, buffers,
                    be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                    packedLight, surfaceW, surfaceH, caseTint, painter, be, partialTick);
        } else {
            TabletScreenRenderer.renderLabelFace(poseStack, buffers,
                    be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                    packedLight, surfaceW, surfaceH, caseTint,
                    program.displayName().getString());
        }
    }

    /** Per-gauge readings from the BE's synced receiver state. */
    private static int[] gaugeReadings(TabletBlockEntity be) {
        int[] readings = new int[be.getGauges().size()];
        for (int i = 0; i < readings.length; i++) {
            readings[i] = be.gaugeReading(i);
        }
        return readings;
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
                                      List<Signal> signals,
                                      com.modpack.linktablet.api.TabletProgram program,
                                      PoseStack poseStack, MultiBufferSource buffers,
                                      int packedLight, int packedOverlay, float partialTick) {
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

        // Tablet on the ball: quaternion from the RENDER basis — the
        // synced angles smoothed client-side so follow-mode updates
        // (and wrench flips) glide instead of snapping. Hit tests keep
        // be.mountBasis() (the raw synced angles): clicks stay
        // server-agreed, pixels catch up within a few frames — the
        // accepted trade from the follow-mode design.
        long now = net.minecraft.Util.getMillis();
        float dt = be.renderLerpMillis == 0 ? 1f
                : Math.min((now - be.renderLerpMillis) / 1000f, 1f);
        be.renderLerpMillis = now;
        if (Float.isNaN(be.renderPitch)) {
            be.renderPitch = be.getMountPitch();
            be.renderYaw = be.getMountYaw();
        } else {
            // Exponential approach, ~90% of the way in a quarter second
            float step = 1f - (float) Math.pow(0.0001f, dt);
            be.renderPitch = net.minecraft.util.Mth.rotLerp(step, be.renderPitch, be.getMountPitch());
            be.renderYaw = net.minecraft.util.Mth.rotLerp(step, be.renderYaw, be.getMountYaw());
        }
        TabletScreenMath.MountBasis basis = TabletScreenMath.mountBasis(
                be.getBlockPos(), be.mountAttachNormal(), be.renderPitch, be.renderYaw,
                state.getValue(TabletBlock.LANDSCAPE));
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
        renderFace(be, state, signals, program, poseStack, buffers, packedLight,
                1, 1, caseTint, partialTick);
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
