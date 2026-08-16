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

            Tracked existing = tracked.get(entry.getKey());
            if (existing == null) {
                if (wanted.isEmpty()) continue; // nothing to say, don't track
                existing = new Tracked(entity instanceof ItemFrame,
                        throwerName(entity), new HashMap<>());
                tracked.put(entry.getKey(), existing);
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
                    // strength actually changed — drifting items are cheap
                    transmitter.update(level, entity.blockPosition(), want.getValue());
                }
            }
            if (live.isEmpty()) {
                tracked.remove(entry.getKey());
            }
        }

        if (tracked.isEmpty()) ACTIVE.remove(level);
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
