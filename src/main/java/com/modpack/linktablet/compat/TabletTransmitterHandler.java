package com.modpack.linktablet.compat;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.registry.ModDataComponents;
import com.simibubi.create.Create;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side bridge between tablets and Create's Redstone Link network.
 * <p>
 * Every server tick, each online player's inventory is scanned for
 * tablets. For every app that is toggled ON, a {@link VirtualTransmitter}
 * is kept registered on Create's global link network (the exact same
 * network real Redstone Links and Linked Controllers use), broadcasting
 * signal strength 15 from the player's current position. Toggling the
 * app off — or dropping / losing the tablet — removes the transmitter,
 * and matching receiving links fall back to whatever other transmitters
 * remain on that frequency.
 * <p>
 * Because the player is the transmitter, range and chunk-loading behave
 * exactly like Create's handheld Linked Controller.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID)
public class TabletTransmitterHandler {

    /** Active virtual transmitters, per player. */
    private static final Map<UUID, Map<Frequency, VirtualTransmitter>> ACTIVE = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();

        // 1. Collect every frequency that should currently be broadcasting
        //    from this player (any ON app on any tablet in their inventory).
        Set<Frequency> wanted = new HashSet<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof TabletItem)) continue;
            List<SignalApp> apps = stack.getOrDefault(ModDataComponents.TABLET_APPS.get(), List.of());
            for (SignalApp app : apps) {
                if (!app.active()) continue;
                for (Frequency freq : app.frequencies()) {
                    if (!freq.isEmpty()) {
                        wanted.add(freq);
                    }
                }
            }
        }

        Map<Frequency, VirtualTransmitter> current =
                ACTIVE.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>());

        // 2. Remove transmitters that are no longer wanted.
        Iterator<Map.Entry<Frequency, VirtualTransmitter>> it = current.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Frequency, VirtualTransmitter> entry = it.next();
            if (!wanted.contains(entry.getKey())) {
                entry.getValue().removeFromNetwork();
                it.remove();
            }
        }

        // 3. Add new transmitters / keep existing ones in sync with the
        //    player's position and dimension.
        for (Frequency freq : wanted) {
            VirtualTransmitter transmitter = current.get(freq);
            if (transmitter == null) {
                transmitter = new VirtualTransmitter(freq, level, player.blockPosition());
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, transmitter);
                current.put(freq, transmitter);
            } else {
                transmitter.updatePosition(level, player.blockPosition());
            }
        }

        if (current.isEmpty()) {
            ACTIVE.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Map<Frequency, VirtualTransmitter> current = ACTIVE.remove(event.getEntity().getUUID());
        if (current == null) return;
        current.values().forEach(VirtualTransmitter::removeFromNetwork);
    }
}
