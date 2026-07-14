package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.client.screen.TabletScreen;
import com.modpack.linktablet.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {

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
                        if (entity == null || entity != mc.player) return 0.0F;
                        return entity.getItemInHand(tablet.hand()) == stack ? 1.0F : 0.0F;
                    });
        });
    }
}
