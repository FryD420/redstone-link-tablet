package com.modpack.linktablet;

import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.registry.ModCreativeTabs;
import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.registry.ModBlockEntities;
import com.modpack.linktablet.registry.ModBlocks;
import com.modpack.linktablet.registry.ModItems;
import com.modpack.linktablet.registry.ModMenus;
import com.modpack.linktablet.registry.ModRecipeSerializers;
import com.modpack.linktablet.compat.TabletWashingType;
import com.modpack.linktablet.item.TabletCauldronWash;
import com.simibubi.create.api.registry.CreateRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.minecraft.world.item.CreativeModeTabs;

/**
 * Link Tablet — a handheld tablet whose "signals" transmit on Create's
 * Redstone Link network. Each signal stores a two-item frequency pair
 * (identical to tuning a Redstone Link), an optional custom icon item,
 * a tile color, and an on/off state. While a signal is ON and the tablet
 * is in someone's inventory, a virtual transmitter broadcasts signal
 * strength 15 on that frequency from the player's position — any
 * receiving Redstone Link on the same frequency powers up.
 */
@Mod(LinkTabletMod.MOD_ID)
public class LinkTabletMod {

    public static final String MOD_ID = "linktablet";

    public LinkTabletMod(IEventBus modEventBus) {
        // Dev-only example addon (jar-excluded, like tools/): exercises
        // the public program API. OPT-IN even in dev (user verdict after
        // testing: it crowds the store shelf) — launch with
        // -Dlinktablet.example=true to shelve Dice when touching the
        // API. A real addon adds the same listener on ITS OWN mod bus.
        if (!net.neoforged.fml.loading.FMLEnvironment.production
                && Boolean.getBoolean("linktablet.example")) {
            modEventBus.addListener(com.modpack.linktablet.example.ExampleAddon::registerPrograms);
            if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
                modEventBus.addListener(com.modpack.linktablet.example.ExampleAddon::registerClients);
            }
        }
        ModDataComponents.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModItems.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModRecipeSerializers.register(modEventBus);
        ModMenus.register(modEventBus);
        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::onRegister);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::addToVanillaTabs);
    }

    // The mod's own tab is easy to miss with one item in it; list the
    // tablet where redstone players actually browse.
    private void addToVanillaTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS) {
            event.accept(ModItems.TABLET.get());
            event.accept(ModItems.SWIVEL_MOUNT.get());
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        ModNetworking.register(event);
    }

    private void onRegister(RegisterEvent event) {
        // Bulk-washing support: strips case dye while keeping the signals
        // (a create:splashing recipe can't preserve components).
        event.register(CreateRegistries.FAN_PROCESSING_TYPE,
                ResourceLocation.fromNamespaceAndPath(MOD_ID, "tablet_washing"),
                TabletWashingType::new);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TabletCauldronWash::register);
        // Addon API (1.10.0): collect every mod's tablet programs, then
        // freeze the table so worlds always load against the complete,
        // deterministically ordered catalog. Client halves register in
        // ClientSetup (client setup runs after common setup).
        event.enqueueWork(() -> {
            net.neoforged.fml.ModLoader.postEvent(
                    new com.modpack.linktablet.api.RegisterTabletProgramsEvent());
            com.modpack.linktablet.Programs.freeze();
        });
    }
}
