package com.modpack.linktablet.registry;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.item.TabletItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(LinkTabletMod.MOD_ID);

    public static final DeferredItem<Item> TABLET =
            ITEMS.register("tablet", () -> new TabletItem(new Item.Properties().stacksTo(1)));

    /**
     * Swivel mount: used ON a placed tablet (TabletBlock.useItemOn) or,
     * since 1.8.1, placed on its own as an empty stand (the BlockItem
     * path — block interactions run first, so the on-tablet gesture
     * still wins when clicking a tablet).
     */
    public static final DeferredItem<Item> SWIVEL_MOUNT =
            ITEMS.register("swivel_mount", () -> new net.minecraft.world.item.BlockItem(
                    ModBlocks.SWIVEL_MOUNT.get(), new Item.Properties()) {
                @Override
                public void appendHoverText(net.minecraft.world.item.ItemStack stack,
                                            TooltipContext context,
                                            java.util.List<net.minecraft.network.chat.Component> tooltip,
                                            net.minecraft.world.item.TooltipFlag flag) {
                    tooltip.add(net.minecraft.network.chat.Component
                            .translatable("item.linktablet.swivel_mount.tooltip")
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            });

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
