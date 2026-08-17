package com.modpack.linktablet.compat;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.registry.ModDataComponents;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Third transmitter anchor (1.13.0, joining the player-inventory scan
 * and the placed-block BE): tablets lying on the ground as item
 * entities, and tablets sitting in item frames, keep broadcasting
 * their toggled-ON signals from where they are.
 * <p>
 * A 4-tick sweep per level uses {@code getEntities(EntityTypeTest, ...)}
 * to linearly scan visible entities with type-and-predicate filters
 * for tablet-bearing items/frames, then diffs a wanted map against live
 * {@link VirtualTransmitter}s — O(visible entities) per sweep, every 4
 * ticks, per level; idiomatic and acceptable at this scale. Every cleanup
 * case (pickup, despawn, lava, hopper, frame emptied or broken, chunk
 * unload, portal transfer) is handled by ABSENCE from the sweep, not
 * by enumerated events. Momentary and timer signals never fire from
 * the ground — there is no interaction path — and in-flight pulses
 * finish from the thrower (the hold system is player-keyed, untouched
 * by design).
 * <p>
 * A player TOSS (Q-drop or inventory drag-out) additionally registers
 * instantly via an add-only {@link ItemTossEvent} fast path, closing
 * the 1-4 tick power blink the sweep cadence would otherwise leave at
 * a receiver between the player-inventory anchor releasing the stack
 * and the next sweep boundary picking it back up. This is deliberately
 * NOT extended to other spawn paths — death drops, dispensers, block
 * drops, item frames popping their item — which keep the ≤4-tick gap;
 * cleanup for all of them, tosses included, stays sweep-only.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID)
public class DroppedTabletHandler {

    /** MonitorScanner's cadence: at most ~200ms drop-to-broadcast. */
    private static final int SWEEP_INTERVAL = 4;

    /** What one tracked entity is transmitting. */
    private record Tracked(boolean framed, @Nullable String throwerName,
                           Map<Frequency, VirtualTransmitter> transmitters) {}

    /** Per-level registry, keyed by entity UUID. Per-level so a level
     *  unload can drop its members wholesale, and a portal transfer
     *  (same UUID, new level) cleanly leaves one sweep and joins the
     *  other's. */
    private static final Map<ServerLevel, Map<UUID, Tracked>> ACTIVE = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % SWEEP_INTERVAL != 0) return;

        Map<UUID, Tracked> tracked = ACTIVE.computeIfAbsent(level, l -> new HashMap<>());

        // 1. Linear sweep of visible entities with type filter for tablet-bearing ones.
        //    ItemFrame.class covers GlowItemFrame (it extends ItemFrame).
        Map<UUID, Entity> present = new HashMap<>();
        for (ItemEntity item : level.getEntities(EntityType.ITEM,
                e -> e.getItem().getItem() instanceof TabletItem)) {
            present.put(item.getUUID(), item);
        }
        for (ItemFrame frame : level.getEntities(
                EntityTypeTest.forClass(ItemFrame.class),
                f -> f.getItem().getItem() instanceof TabletItem)) {
            present.put(frame.getUUID(), frame);
        }

        // 2. Entities gone from the sweep: remove all their transmitters.
        Iterator<Map.Entry<UUID, Tracked>> gone = tracked.entrySet().iterator();
        while (gone.hasNext()) {
            Map.Entry<UUID, Tracked> entry = gone.next();
            if (!present.containsKey(entry.getKey())) {
                entry.getValue().transmitters().values()
                        .forEach(VirtualTransmitter::removeFromNetwork);
                gone.remove();
            }
        }

        // 3. Present entities: diff wanted frequencies against live ones.
        for (Map.Entry<UUID, Entity> entry : present.entrySet()) {
            Entity entity = entry.getValue();
            ItemStack stack = entity instanceof ItemFrame frame
                    ? frame.getItem() : ((ItemEntity) entity).getItem();
            syncTransmitters(level, tracked, entity, stack, null);
        }

        if (tracked.isEmpty()) ACTIVE.remove(level);
    }

    /**
     * Toss fast path (add-only): a player Q-drop or inventory drag-out
     * registers the thrown tablet's transmitters immediately instead of
     * waiting up to {@link #SWEEP_INTERVAL} ticks for the sweep to pick
     * it up, closing the power blink between the player-inventory anchor
     * releasing the stack and the sweep noticing the dropped one. Runs the
     * exact same per-entity registration the sweep uses (never forked —
     * see {@link #syncTransmitters}), so a sweep landing on this entity
     * before or after this handler runs is a no-op either way: {@code
     * update()} on an already-live transmitter only touches the network
     * when position or strength actually changed, and a wanted frequency
     * that's already registered is simply fetched from {@code live}, not
     * re-added.
     * <p>
     * Add-only by design: cleanup remains sweep-only (see class javadoc).
     * If some other listener cancels the toss afterward and the item
     * entity never truly enters the world, this handler has already
     * registered it — but the next sweep won't find it in {@code
     * present} and removes it within {@link #SWEEP_INTERVAL} ticks, same
     * as any other despawn. No {@code receiveCanceled}, no removal event.
     */
    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer().level() instanceof ServerLevel level)) return;
        ItemEntity itemEntity = event.getEntity();
        ItemStack stack = itemEntity.getItem();
        if (!(stack.getItem() instanceof TabletItem)) return;

        Map<UUID, Tracked> tracked = ACTIVE.computeIfAbsent(level, l -> new HashMap<>());
        // Prefer the event's player directly: GUI drag-out tosses never
        // call setThrower (Player.drop's traceItem arg is false there), so
        // getOwner() would stay null for them permanently — the event is
        // the only reliable thrower source.
        syncTransmitters(level, tracked, itemEntity, stack, event.getPlayer().getName().getString());
    }

    /**
     * Builds the wanted-frequency map for one tablet-bearing entity and
     * diffs it against that entity's live transmitters, registering new
     * ones and dropping ones no longer wanted. Shared by the sweep (step
     * 3 above) and the toss fast path — the ONLY place either adds or
     * updates a transmitter for a present entity; never fork this logic.
     * Idempotent: calling it twice in a row for the same entity and stack
     * state is a no-op the second time (see {@link #onItemToss}).
     *
     * @param throwerNameOverride thrower name to use when first tracking
     *                            this entity, or null to fall back to
     *                            {@link #throwerName}; ignored once the
     *                            entity is already tracked.
     */
    private static void syncTransmitters(ServerLevel level, Map<UUID, Tracked> tracked,
                                         Entity entity, ItemStack stack,
                                         @Nullable String throwerNameOverride) {
        UUID uuid = entity.getUUID();

        Map<Frequency, Integer> wanted = new HashMap<>();
        List<Signal> signals = stack.getOrDefault(
                ModDataComponents.TABLET_SIGNALS.get(), List.of());
        for (Signal signal : signals) {
            // Same filter as the player-inventory scan: momentary
            // and timer signals broadcast via holds, never from here
            if (!signal.active() || signal.momentary() || signal.timed()) continue;
            for (Frequency freq : signal.frequencies()) {
                if (!freq.isEmpty()) {
                    wanted.merge(freq, signal.strength(), Math::max);
                }
            }
        }

        Tracked existing = tracked.get(uuid);
        if (existing == null) {
            if (wanted.isEmpty()) return; // nothing to say, don't track
            existing = new Tracked(entity instanceof ItemFrame,
                    throwerNameOverride != null ? throwerNameOverride : throwerName(entity),
                    new HashMap<>());
            tracked.put(uuid, existing);
        }

        Map<Frequency, VirtualTransmitter> live = existing.transmitters();
        Iterator<Map.Entry<Frequency, VirtualTransmitter>> it =
                live.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Frequency, VirtualTransmitter> t = it.next();
            if (!wanted.containsKey(t.getKey())) {
                t.getValue().removeFromNetwork();
                it.remove();
            }
        }
        for (Map.Entry<Frequency, Integer> want : wanted.entrySet()) {
            VirtualTransmitter transmitter = live.get(want.getKey());
            if (transmitter == null) {
                transmitter = new VirtualTransmitter(want.getKey(), level,
                        entity.blockPosition(), want.getValue());
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, transmitter);
                live.put(want.getKey(), transmitter);
            } else {
                // update() no-ops on the network unless position or
                // strength actually changed — drifting items are cheap,
                // and a sweep re-visiting an entity this handler already
                // registered is equally cheap.
                transmitter.update(level, entity.blockPosition(), want.getValue());
            }
        }
        if (live.isEmpty()) {
            tracked.remove(uuid);
        }
    }

    /** Thrower attribution for the Monitor row; null when unknown.
     *  getOwner() resolves the thrower UUID against loaded entities, so
     *  an offline or far-away thrower yields the anonymous label. */
    @Nullable
    private static String throwerName(Entity entity) {
        if (entity instanceof ItemEntity item && item.getOwner() != null) {
            return item.getOwner().getName().getString();
        }
        return null;
    }

    /** Level going away (also fires per level at server stop): drop
     *  everything it was transmitting. Mirrors the logout handler. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Map<UUID, Tracked> tracked = ACTIVE.remove(level);
        if (tracked == null) return;
        tracked.values().forEach(t ->
                t.transmitters().values().forEach(VirtualTransmitter::removeFromNetwork));
    }

    /** Origin of a network member owned by this handler, or null.
     *  The Frequency Monitor's classification hook (Task 2). */
    public record Origin(boolean framed, @Nullable String throwerName) {}

    @Nullable
    public static Origin classify(IRedstoneLinkable member) {
        for (Map<UUID, Tracked> perLevel : ACTIVE.values()) {
            for (Tracked t : perLevel.values()) {
                if (t.transmitters().containsValue(member)) {
                    return new Origin(t.framed(), t.throwerName());
                }
            }
        }
        return null;
    }

    private DroppedTabletHandler() {
    }
}
