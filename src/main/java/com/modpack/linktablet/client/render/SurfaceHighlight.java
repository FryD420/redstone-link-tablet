package com.modpack.linktablet.client.render;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/**
 * Suppresses the vanilla block-selection wireframe on merged surface
 * members (1.7.0): the continuous panel presents the wall as ONE
 * display, and a single-block outline crawling across it gives the
 * blocks away. Standalone tablets keep their outline.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class SurfaceHighlight {

    @SubscribeEvent
    public static void onBlockHighlight(RenderHighlightEvent.Block event) {
        BlockHitResult target = event.getTarget();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getBlockEntity(target.getBlockPos()) instanceof TabletBlockEntity be
                && be.isMerged()) {
            event.setCanceled(true);
        }
    }

    private SurfaceHighlight() {
    }
}
