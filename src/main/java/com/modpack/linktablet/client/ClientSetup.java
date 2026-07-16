package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.client.ponder.LinkTabletPonderPlugin;
import com.modpack.linktablet.client.render.TabletBlockEntityRenderer;
import com.modpack.linktablet.client.render.TabletItemRenderer;
import com.modpack.linktablet.registry.ModBlockEntities;
import com.modpack.linktablet.registry.ModBlocks;
import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.registry.ModItems;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

    /**
     * Tint applied to the case faces (tintindex 0) when no dye is set.
     * Multiplies the bright grayscale case texture back down to the
     * original dark look, so undyed tablets are pixel-identical to 1.0.
     */
    private static final int DEFAULT_CASE_TINT = 0xFF383C45;

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) return -1;
            DyeColor dye = stack.get(ModDataComponents.CASE_COLOR.get());
            return dye == null ? DEFAULT_CASE_TINT : 0xFF000000 | dye.getTextureDiffuseColor();
        }, ModItems.TABLET.get());
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, getter, pos, tintIndex) -> {
            if (tintIndex != 0) return -1;
            if (getter != null && pos != null
                    && getter.getBlockEntity(pos) instanceof TabletBlockEntity be
                    && be.getCaseColor() != null) {
                return 0xFF000000 | be.getCaseColor().getTextureDiffuseColor();
            }
            return DEFAULT_CASE_TINT;
        }, ModBlocks.TABLET.get());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.TABLET.get(), TabletBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterAdditionalModels(ModelEvent.RegisterAdditional event) {
        // Standalone-baked geometry the tablet's custom item renderer
        // draws (the item model JSON itself is a builtin/entity stub).
        event.register(TabletItemRenderer.MODEL_BASE);
        event.register(TabletItemRenderer.MODEL_LIT);
    }

    @SubscribeEvent
    public static void onRegisterMenuScreens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(com.modpack.linktablet.registry.ModMenus.APP_EDIT.get(),
                com.modpack.linktablet.client.screen.AppEditScreen::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> PonderIndex.addPlugin(new LinkTabletPonderPlugin()));
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return TabletItemRenderer.instance();
            }
        }, ModItems.TABLET.get());
    }
}
