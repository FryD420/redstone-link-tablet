package com.modpack.linktablet.item;

import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Dunking a dyed tablet in a water cauldron washes the case back to default. */
public final class TabletCauldronWash {

    private TabletCauldronWash() {
    }

    /** Called from common setup ({@code enqueueWork} — the interaction maps aren't thread-safe). */
    public static void register() {
        CauldronInteraction.WATER.map().put(ModItems.TABLET.get(), TabletCauldronWash::wash);
    }

    private static ItemInteractionResult wash(BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, ItemStack stack) {
        if (!stack.has(ModDataComponents.CASE_COLOR.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            stack.remove(ModDataComponents.CASE_COLOR.get());
            player.awardStat(Stats.CLEAN_ARMOR);
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 1.1F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
