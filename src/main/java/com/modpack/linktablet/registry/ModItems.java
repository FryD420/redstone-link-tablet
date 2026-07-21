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

    /** Swivel mount (1.8.0): used ON a placed tablet — see TabletBlock.useItemOn. */
    public static final DeferredItem<Item> SWIVEL_MOUNT =
            ITEMS.register("swivel_mount", () -> new Item(new Item.Properties()) {
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
