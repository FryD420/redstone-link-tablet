package com.modpack.linktablet.registry;

import com.modpack.linktablet.LinkTabletMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LinkTabletMod.MOD_ID);

    static {
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.linktablet"))
                .icon(() -> new ItemStack(ModItems.TABLET.get()))
                .displayItems((params, output) -> {
                    output.accept(ModItems.TABLET.get());
                    output.accept(ModItems.SWIVEL_MOUNT.get());
                })
                .build());
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
