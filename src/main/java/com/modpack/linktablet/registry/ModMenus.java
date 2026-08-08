package com.modpack.linktablet.registry;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.menu.SignalEditMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, LinkTabletMod.MOD_ID);

    /** The signal editor: real player-inventory slots + two ghost frequency slots. */
    public static final DeferredHolder<MenuType<?>, MenuType<SignalEditMenu>> SIGNAL_EDIT =
            MENUS.register("signal_edit", () -> IMenuTypeExtension.create(SignalEditMenu::create));

    /** The Monitor's add-probe editor (1.11.0): SignalEditMenu reused —
     * a container menu is what makes JEI/EMI panels appear at all. */
    public static final DeferredHolder<MenuType<?>, MenuType<SignalEditMenu>> PROBE_EDIT =
            MENUS.register("probe_edit", () -> IMenuTypeExtension.create(SignalEditMenu::createProbe));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
