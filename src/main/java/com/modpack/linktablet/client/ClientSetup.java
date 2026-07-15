package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.client.screen.TabletScreen;
import com.modpack.linktablet.registry.ModBlocks;
import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

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
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // "lit" = 1 while this player has the tablet GUI open on this
            // stack; the item model override swaps to the glowing-screen
            // variant. Purely cosmetic, so client-side only.
            ItemProperties.register(ModItems.TABLET.get(),
                    ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID, "lit"),
                    (stack, level, entity, seed) -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (!(mc.screen instanceof TabletScreen tablet)) return 0.0F;
                        if (!(tablet.view() instanceof AppView.Hand handView)) return 0.0F;
                        if (entity == null || entity != mc.player) return 0.0F;
                        return entity.getItemInHand(handView.hand()) == stack ? 1.0F : 0.0F;
                    });
        });
    }
}
