package com.modpack.linktablet.compat;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.registry.ModDataComponents;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
     * Ticks a block-tap hold survives without being refreshed. Holding
     * right-click on a block re-fires the use action every 4 ticks, so 8
     * bridges one dropped repeat; releasing drops the signal within this.
     */
    public static final int BLOCK_HOLD_TICKS = 8;

    /**
     * Minimum ticks every press transmits, however fast the release
     * lands. A quick GUI click can deliver press and release in the same
     * server tick — without this floor the hold would come and go before
     * the transmitter tick ever saw it, producing no pulse at all.
     */
    public static final int MIN_PULSE_TICKS = 4;

    /**
     * One momentary app currently held down: its frequencies, strength,
     * for placed tablets the block position to broadcast from (null =
     * broadcast from the player), the game time the hold expires unless
     * refreshed (-1 = held until an explicit release packet), and the
     * game time before which a release only shortens — never cancels —
     * the pulse.
     */
    private record Hold(List<Frequency> frequencies, int strength, @Nullable BlockPos pos,
                        long expireAt, long minUntil) {}

    /** Identifies a press so it always pairs up with its release. */
    private record HoldKey(boolean mainHand, @Nullable BlockPos pos, int index) {}

    /** Momentary holds per player. */
    private static final Map<UUID, Map<HoldKey, Hold>> HELD = new HashMap<>();

    /** Registers a momentary press (called from the network handler). */
    public static void setHeld(Player player, boolean mainHand, @Nullable BlockPos pos, int index,
                               List<Frequency> frequencies, int strength) {
        long now = player.level().getGameTime();
        HELD.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>())
                .put(new HoldKey(mainHand, pos, index),
                        new Hold(frequencies, strength, pos, -1, now + MIN_PULSE_TICKS));
        setBePip(player, pos, index, true);
    }

    /**
     * Starts (or RESTARTS — a re-tap tops the clock back up, user
     * decision 2026-07-19) a Timer app's pulse: a self-expiring hold
     * that the normal expiry loop finishes, off-click and pip included.
     * No release packet exists for timers; the clock is the release.
     */
    public static void startTimed(Player player, boolean mainHand, @Nullable BlockPos pos, int index,
                                  List<Frequency> frequencies, int strength, int pulseTicks) {
        long expireAt = player.level().getGameTime() + pulseTicks;
        HELD.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>())
                .put(new HoldKey(mainHand, pos, index),
                        new Hold(frequencies, strength, pos, expireAt, expireAt));
        setBePip(player, pos, index, true);
    }

    /**
     * Registers or refreshes a tap-and-hold press on a placed tablet's
     * screen; it expires on its own unless the (repeating) block use
     * refreshes it. Returns true when this started a new press.
     */
    public static boolean pressBlockPip(Player player, BlockPos pos, int index,
                                        List<Frequency> frequencies, int strength, long gameTime) {
        boolean newPress = HELD.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>())
                .put(new HoldKey(true, pos, index),
                        new Hold(frequencies, strength, pos,
                                gameTime + BLOCK_HOLD_TICKS, gameTime + MIN_PULSE_TICKS)) == null;
        if (newPress) setBePip(player, pos, index, true);
        return newPress;
    }

    /** Clears a momentary press (release). */
    public static void clearHeld(Player player, boolean mainHand, @Nullable BlockPos pos, int index) {
        Map<HoldKey, Hold> holds = HELD.get(player.getUUID());
        if (holds == null) return;
        HoldKey key = new HoldKey(mainHand, pos, index);
        Hold hold = holds.get(key);
        if (hold == null) return;
        if (player.level().getGameTime() < hold.minUntil()) {
            // Released faster than the minimum pulse: let the expiry
            // loop finish it so the click always produces a signal.
            holds.put(key, new Hold(hold.frequencies(), hold.strength(), hold.pos(),
                    hold.minUntil(), hold.minUntil()));
            return;
        }
        holds.remove(key);
        if (holds.isEmpty()) HELD.remove(player.getUUID());
        setBePip(player, pos, index, false);
    }

    /** Updates a placed tablet's held-pip screen visual, if any. */
    private static void setBePip(Player player, @Nullable BlockPos pos, int index, boolean held) {
        if (pos != null && player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
            be.setPipHeld(index, held);
        }
    }

    /**
     * Clears every momentary hold aimed at one tablet. Holds are keyed by
     * app index, so a reorder invalidates them: for a placed tablet any
     * player may be holding one of its apps; for a held tablet only the
     * reordering player's own hand can be.
     */
    public static void clearHeldForTarget(Player player, boolean mainHand, @Nullable BlockPos pos) {
        if (pos != null) {
            HELD.values().forEach(holds -> holds.keySet().removeIf(key -> pos.equals(key.pos())));
            HELD.values().removeIf(Map::isEmpty);
            // Indices shifted, so every held-pip visual on it is stale
            if (player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
                be.clearHeldPips();
            }
        } else {
            Map<HoldKey, Hold> holds = HELD.get(player.getUUID());
            if (holds == null) return;
            holds.keySet().removeIf(key -> key.pos() == null && key.mainHand() == mainHand);
            if (holds.isEmpty()) HELD.remove(player.getUUID());
        }
    }

    /**
     * Like the pos branch of {@link #clearHeldForTarget}, but callable
     * without a player — surface merges/splits run from block ticks
     * (1.7.0), and a role change invalidates every hold on the old and
     * new controller positions across ALL players.
     */
    public static void clearHeldForBlock(Level level, BlockPos pos) {
        HELD.values().forEach(holds -> holds.keySet().removeIf(key -> pos.equals(key.pos())));
        HELD.values().removeIf(Map::isEmpty);
        if (level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
            be.clearHeldPips();
        }
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
                // Momentary and Timer apps broadcast via holds, never
                // via a persisted active flag
                if (!app.active() || app.momentary() || app.timed()) continue;
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
            // Expire timed holds: block-taps that stopped being refreshed
            // (the player let go of right-click) and fast GUI clicks
            // finishing their minimum pulse. The release click mirrors
            // the press's.
            Iterator<Map.Entry<HoldKey, Hold>> holdIt = holds.entrySet().iterator();
            while (holdIt.hasNext()) {
                Map.Entry<HoldKey, Hold> entry = holdIt.next();
                Hold hold = entry.getValue();
                if (hold.expireAt() >= 0 && level.getGameTime() > hold.expireAt()) {
                    holdIt.remove();
                    setBePip(player, hold.pos(), entry.getKey().index(), false);
                    ModNetworking.playToggleClick(level, null,
                            hold.pos() != null ? hold.pos() : player.blockPosition(), false);
                }
            }
            if (holds.isEmpty()) HELD.remove(player.getUUID());
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
        Map<HoldKey, Hold> holds = HELD.remove(event.getEntity().getUUID());
        if (holds != null) {
            // Never leave a pip visually stuck lit by a disconnect
            holds.forEach((key, hold) ->
                    setBePip(event.getEntity(), hold.pos(), key.index(), false));
        }
        Map<Frequency, VirtualTransmitter> current = ACTIVE.remove(event.getEntity().getUUID());
        if (current == null) return;
        current.values().forEach(VirtualTransmitter::removeFromNetwork);
    }
}
