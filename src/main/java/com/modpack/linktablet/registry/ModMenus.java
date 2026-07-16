package com.modpack.linktablet.registry;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.menu.AppEditMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, LinkTabletMod.MOD_ID);

    /** The app editor: real player-inventory slots + two ghost frequency slots. */
    public static final DeferredHolder<MenuType<?>, MenuType<AppEditMenu>> APP_EDIT =
            MENUS.register("app_edit", () -> IMenuTypeExtension.create(AppEditMenu::create));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
