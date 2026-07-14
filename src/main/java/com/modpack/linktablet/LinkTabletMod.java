package com.modpack.linktablet;

import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.registry.ModCreativeTabs;
import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.registry.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Link Tablet — a handheld tablet whose "apps" transmit on Create's
 * Redstone Link network. Each app stores a two-item frequency pair
 * (identical to tuning a Redstone Link), an optional custom icon item,
 * a tile color, and an on/off state. While an app is ON and the tablet
 * is in someone's inventory, a virtual transmitter broadcasts signal
 * strength 15 on that frequency from the player's position — any
 * receiving Redstone Link on the same frequency powers up.
 */
@Mod(LinkTabletMod.MOD_ID)
public class LinkTabletMod {

    public static final String MOD_ID = "linktablet";

    public LinkTabletMod(IEventBus modEventBus) {
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        ModNetworking.register(event);
    }
}
