package com.modpack.linktablet.compat;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.registry.ModDataComponents;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side bridge between tablets and Create's Redstone Link network.
 * <p>
 * Every server tick, each online player's inventory is scanned for
 * tablets. For every app that is toggled ON — plus every momentary app
 * currently held down (see {@link #setHeld}) — a {@link VirtualTransmitter}
 * is kept registered on Create's global link network (the exact same
 * network real Redstone Links and Linked Controllers use), broadcasting
 * the app's signal strength from the player's current position. When two
 * apps drive the same frequency, the strongest strength wins, mirroring
 * how stacked Redstone Link transmitters behave.
 * <p>
 * Momentary hold state is transient and never written to the item, so a
 * disconnect or crash mid-press can't leave a signal stuck on: the
 * transmitters and holds are both discarded on logout.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID)
public class TabletTransmitterHandler {

    /** Active virtual transmitters, per player. */
    private static final Map<UUID, Map<Frequency, VirtualTransmitter>> ACTIVE = new HashMap<>();

    /**
     * One momentary app currently held down: its frequencies, strength,
     * and — for placed tablets — the block position to broadcast from
     * (null = broadcast from the player).
     */
    private record Hold(List<Frequency> frequencies, int strength, @Nullable BlockPos pos) {}

    /** Identifies a press so it always pairs up with its release. */
    private record HoldKey(boolean mainHand, @Nullable BlockPos pos, int index) {}

    /** Momentary holds per player. */
    private static final Map<UUID, Map<HoldKey, Hold>> HELD = new HashMap<>();

    /** Registers a momentary press (called from the network handler). */
    public static void setHeld(Player player, boolean mainHand, @Nullable BlockPos pos, int index,
                               List<Frequency> frequencies, int strength) {
        HELD.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>())
                .put(new HoldKey(mainHand, pos, index), new Hold(frequencies, strength, pos));
    }

    /** Clears a momentary press (release). */
    public static void clearHeld(Player player, boolean mainHand, @Nullable BlockPos pos, int index) {
        Map<HoldKey, Hold> holds = HELD.get(player.getUUID());
        if (holds == null) return;
        holds.remove(new HoldKey(mainHand, pos, index));
        if (holds.isEmpty()) HELD.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();

        // 1. Collect every frequency that should currently be broadcasting
        //    from this player, with the strongest requested strength:
        //    toggled-ON apps on any tablet in the inventory, plus held
        //    momentary apps.
        Map<Frequency, Integer> wanted = new HashMap<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof TabletItem)) continue;
            List<SignalApp> apps = stack.getOrDefault(ModDataComponents.TABLET_APPS.get(), List.of());
            for (SignalApp app : apps) {
                if (!app.active() || app.momentary()) continue;
                for (Frequency freq : app.frequencies()) {
                    if (!freq.isEmpty()) {
                        wanted.merge(freq, app.strength(), Math::max);
                    }
                }
            }
        }
        Map<Frequency, BlockPos> wantedPos = new HashMap<>();
        Map<HoldKey, Hold> holds = HELD.get(player.getUUID());
        if (holds != null) {
            for (Hold hold : holds.values()) {
                for (Frequency freq : hold.frequencies()) {
                    if (!freq.isEmpty()) {
                        wanted.merge(freq, hold.strength(), Math::max);
                        if (hold.pos() != null) {
                            wantedPos.putIfAbsent(freq, hold.pos());
                        }
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
            if (!wanted.containsKey(entry.getKey())) {
                entry.getValue().removeFromNetwork();
                it.remove();
            }
        }

        // 3. Add new transmitters / keep existing ones in sync with the
        //    desired position (player, or held block), dimension, and
        //    requested strength.
        for (Map.Entry<Frequency, Integer> entry : wanted.entrySet()) {
            BlockPos desiredPos = wantedPos.getOrDefault(entry.getKey(), player.blockPosition());
            VirtualTransmitter transmitter = current.get(entry.getKey());
            if (transmitter == null) {
                transmitter = new VirtualTransmitter(entry.getKey(), level, desiredPos, entry.getValue());
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, transmitter);
                current.put(entry.getKey(), transmitter);
            } else {
                transmitter.update(level, desiredPos, entry.getValue());
            }
        }

        if (current.isEmpty()) {
            ACTIVE.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HELD.remove(event.getEntity().getUUID());
        Map<Frequency, VirtualTransmitter> current = ACTIVE.remove(event.getEntity().getUUID());
        if (current == null) return;
        current.values().forEach(VirtualTransmitter::removeFromNetwork);
    }
}
