package com.modpack.linktablet.item;

import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.DyeColor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

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

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.linktablet.tablet.tooltip").withStyle(ChatFormatting.GRAY));
        DyeColor caseColor = stack.get(ModDataComponents.CASE_COLOR.get());
        if (caseColor != null) {
            tooltip.add(Component.translatable("item.linktablet.tablet.case",
                            Component.translatable("color.minecraft." + caseColor.getName()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
