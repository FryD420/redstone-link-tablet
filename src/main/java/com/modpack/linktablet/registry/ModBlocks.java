package com.modpack.linktablet.registry;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.SwivelMountBlock;
import com.modpack.linktablet.block.TabletBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(LinkTabletMod.MOD_ID);

    /** The mounted tablet. No BlockItem — the TabletItem places it. */
    public static final DeferredBlock<TabletBlock> TABLET =
            BLOCKS.register("tablet", () -> new TabletBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_GRAY)
                    .strength(0.5F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(state -> state.getValue(TabletBlock.LIT) ? 7 : 0)));

    /** The empty swivel stand (1.8.1). Its BlockItem lives in ModItems. */
    public static final DeferredBlock<SwivelMountBlock> SWIVEL_MOUNT =
            BLOCKS.register("swivel_mount", () -> new SwivelMountBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(0.5F)
                    .sound(SoundType.COPPER)
                    .noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
