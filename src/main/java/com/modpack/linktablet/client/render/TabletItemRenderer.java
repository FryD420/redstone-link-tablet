package com.modpack.linktablet.client.render;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.screen.TabletScreen;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.theme.ScreenTheme;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/**
 * Custom item renderer for the tablet: draws the baked model (base or
 * lit — the old {@code linktablet:lit} property override, now decided
 * here), then overlays the live app pips on the screen plane in every
 * display context except GUI slots, where 16px pips would be mush.
 */
public class TabletItemRenderer extends BlockEntityWithoutLevelRenderer {

    public static final ModelResourceLocation MODEL_BASE = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID, "item/tablet_base"));
    public static final ModelResourceLocation MODEL_LIT = ModelResourceLocation.standalone(
            ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID, "item/tablet_base_lit"));

    /** Screen content just above the glass face (z 8.5 texels) —
     * recessed below the bezel lip (8.55), like a real inset display. */
    private static final float SCREEN_Z = 8.5f / 16f + 0.001f;

    private static TabletItemRenderer instance;

    private TabletItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels());
    }

    public static TabletItemRenderer instance() {
        if (instance == null) {
            instance = new TabletItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        boolean lit = isScreenLit(stack);
        BakedModel model = mc.getModelManager().getModel(lit ? MODEL_LIT : MODEL_BASE);

        ItemRenderer itemRenderer = mc.getItemRenderer();
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(
                buffers, Sheets.translucentCullBlockSheet(), true, stack.hasFoil());
        itemRenderer.renderModelLists(model, stack, packedLight, packedOverlay, poseStack, vc);

        if (context == ItemDisplayContext.GUI) return;
        // Drawn even with no apps so the empty screen matches the in-use
        // look (flat glass, no baked home-button art)
        List<SignalApp> apps = stack.getOrDefault(ModDataComponents.TABLET_APPS.get(), List.of());

        poseStack.pushPose();
        // Map the shared screen frame (u right, v down, +Y out) onto the
        // model's south-facing screen plane.
        poseStack.translate(2 / 16f, 15 / 16f, SCREEN_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        boolean listLayout = stack.getOrDefault(ModDataComponents.SCREEN_LIST.get(), false);
        ScreenTheme theme = stack.getOrDefault(ModDataComponents.THEME.get(), ScreenTheme.DARK);
        TabletScreenRenderer.render(poseStack, buffers, apps, listLayout, theme, lit, packedLight,
                heldPips(stack));
        poseStack.popPose();
    }

    /**
     * While this player holds a momentary app down in the GUI open on
     * this stack, mirror it on the first-person screen.
     */
    private static Set<Integer> heldPips(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TabletScreen tablet)) return Set.of();
        if (!(tablet.view() instanceof AppView.Hand handView)) return Set.of();
        if (mc.player == null || mc.player.getItemInHand(handView.hand()) != stack) return Set.of();
        int held = tablet.heldMomentaryIndex();
        return held >= 0 ? Set.of(held) : Set.of();
    }

    /**
     * True while this player has the tablet GUI open on this exact stack
     * (moved from the old ItemProperties "linktablet:lit" lambda).
     */
    private static boolean isScreenLit(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof TabletScreen tablet)) return false;
        if (!(tablet.view() instanceof AppView.Hand handView)) return false;
        return mc.player != null && mc.player.getItemInHand(handView.hand()) == stack;
    }
}
