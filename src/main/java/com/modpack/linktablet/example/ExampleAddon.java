package com.modpack.linktablet.example;

import com.modpack.linktablet.api.RegisterTabletProgramsEvent;
import com.modpack.linktablet.api.TabletProgram;
import com.modpack.linktablet.api.client.RegisterTabletProgramClientsEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * DEV-ONLY example addon (excluded from the shipped jar, like tools/):
 * a "Dice" program registered through ONLY the public {@code api/}
 * package — living documentation for addon authors, and a regression
 * test for the whole registration → store → screen → overlay → kiosk
 * face path. OPT-IN via {@code -Dlinktablet.example=true} (see
 * LinkTabletMod's constructor) so normal dev runs keep a clean store
 * shelf; flip it on whenever the API changes. A real addon does
 * exactly this from its own mod class on its own mod event bus.
 * User-verified in dev 2026-07-27 ("dice works").
 */
public final class ExampleAddon {

    /** The common half: identity + store metadata. A real addon would
     * use translatable components; literals keep the example lean. */
    public static final TabletProgram DICE = new TabletProgram() {
        @Override
        public String key() {
            return "linktablet_example:dice";
        }

        @Override
        public Component displayName() {
            return Component.literal("Dice");
        }

        @Override
        public Component storeDescription() {
            return Component.literal("Roll a six-sided die.");
        }

        @Override
        public int chipColor() {
            return 0xFFF5F1E8;
        }

        @Override
        public ResourceLocation iconItem() {
            return ResourceLocation.parse("minecraft:bone_block");
        }
    };

    /** Mod-bus listener (both sides). */
    public static void registerPrograms(RegisterTabletProgramsEvent event) {
        event.register(DICE);
    }

    /** Mod-bus listener (client only — the event never fires on a
     * dedicated server; the client classes load lazily from here). */
    public static void registerClients(RegisterTabletProgramClientsEvent event) {
        ExampleAddonClient.register(event);
    }

    private ExampleAddon() {
    }
}
