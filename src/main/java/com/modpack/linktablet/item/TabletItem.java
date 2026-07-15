package com.modpack.linktablet.item;

import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.registry.ModBlocks;
import com.modpack.linktablet.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public class TabletItem extends Item {

    public TabletItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            // ClientHooks is only class-loaded when this branch actually
            // runs, which never happens on a dedicated server.
            ClientHooks.openTabletScreen(hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Sneak + right-click a surface: mount the tablet as a block. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS; // fall through to use() → GUI
        }
        BlockPlaceContext placeContext = new BlockPlaceContext(context);
        if (!placeContext.canPlace()) return InteractionResult.PASS;
        Level level = placeContext.getLevel();
        BlockPos pos = placeContext.getClickedPos();
        BlockState state = ModBlocks.TABLET.get().getStateForPlacement(placeContext);
        if (state == null || !state.canSurvive(level, pos)) return InteractionResult.PASS;

        if (!level.isClientSide) {
            level.setBlock(pos, state, 3);
            if (level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
                be.loadFromItem(context.getItemInHand());
            }
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 1.0F);
            if (!player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.linktablet.tablet.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.linktablet.tablet.tooltip.mount").withStyle(ChatFormatting.DARK_GRAY));
        DyeColor caseColor = stack.get(ModDataComponents.CASE_COLOR.get());
        if (caseColor != null) {
            tooltip.add(Component.translatable("item.linktablet.tablet.case",
                            Component.translatable("color.minecraft." + caseColor.getName()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
