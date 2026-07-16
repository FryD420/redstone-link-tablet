package com.modpack.linktablet.item;

import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.registry.ModBlocks;
import com.modpack.linktablet.registry.ModDataComponents;
import com.simibubi.create.content.redstone.link.LinkBehaviour;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
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

    /**
     * Sneak + right-click a surface: mount the tablet as a block.
     * Plain right-click on a Redstone Link (anything with Create's
     * {@code LinkBehaviour}): open the app editor pre-filled with that
     * link's frequency.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (!player.isSecondaryUseActive()) {
            InteractionResult linkResult = tryOpenLink(context, player);
            if (linkResult != null) return linkResult;
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

    /**
     * Consumes the click when the target block carries a link frequency,
     * or null to fall through. The editor itself only opens client-side;
     * the eventual save travels the normal UpsertApp path.
     */
    private static InteractionResult tryOpenLink(UseOnContext context, Player player) {
        Level level = context.getLevel();
        LinkBehaviour link = BlockEntityBehaviour.get(level, context.getClickedPos(), LinkBehaviour.TYPE);
        if (link == null) return null;

        // Link frequencies sync to clients (Create renders them in-world),
        // so both sides read the same key here.
        Couple<RedstoneLinkNetworkHandler.Frequency> key = link.getNetworkKey();
        ItemStack first = key.getFirst().getStack();
        ItemStack second = key.getSecond().getStack();
        if (first.isEmpty() && second.isEmpty()) {
            if (level.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.linktablet.link_no_frequency"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            ClientHooks.openLinkPrefill(context.getHand(), first, second);
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
