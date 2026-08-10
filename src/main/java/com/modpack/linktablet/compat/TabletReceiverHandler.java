package com.modpack.linktablet.compat;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.Gauge;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.registry.ModDataComponents;
import com.simibubi.create.Create;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The listening half of the Create-network bridge (1.10.0 gauges),
 * parallel to {@link TabletTransmitterHandler}: every server tick, each
 * online player's inventory is scanned for tablets carrying LINK gauges,
 * and a {@link VirtualReceiver} is kept registered per listened
 * frequency, riding the player's position (Create range-gates). Readings
 * are shipped to that player's client via
 * {@code ModNetworking.GaugeReadingsPayload} — the full snapshot,
 * cadence-guarded: at most every {@link #SYNC_TICKS} ticks and only when
 * something changed. Placed tablets run their own receivers on the
 * block entity; this class is players only.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID)
public class TabletReceiverHandler {

    /** Minimum ticks between reading syncs to one player. */
    private static final int SYNC_TICKS = 4;

    /** Active virtual receivers, per player. */
    private static final Map<UUID, Map<Frequency, VirtualReceiver>> ACTIVE = new HashMap<>();
    /** Last snapshot each player's client was sent. */
    private static final Map<UUID, Map<Frequency, Integer>> LAST_SENT = new HashMap<>();
    /** Game time of each player's last sync (cadence guard). */
    private static final Map<UUID, Long> LAST_SYNC = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = (ServerLevel) player.level();

        // 1. Every frequency some gauge in this player's inventory
        //    listens on
        Set<Frequency> wanted = new HashSet<>();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!(stack.getItem() instanceof TabletItem)) continue;
            List<Gauge> gauges = stack.getOrDefault(ModDataComponents.TABLET_GAUGES.get(), List.of());
            for (Gauge gauge : gauges) {
                if (gauge.source() == Gauge.Source.LINK && !gauge.frequency().isEmpty()) {
                    wanted.add(gauge.frequency());
                }
            }
        }

        Map<Frequency, VirtualReceiver> current =
                ACTIVE.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>());

        // 2. Drop receivers nothing listens through anymore
        Iterator<Map.Entry<Frequency, VirtualReceiver>> it = current.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Frequency, VirtualReceiver> entry = it.next();
            if (!wanted.contains(entry.getKey())) {
                entry.getValue().removeFromNetwork();
                it.remove();
            }
        }

        // 3. Add new receivers / keep existing ones riding the player
        for (Frequency freq : wanted) {
            VirtualReceiver receiver = current.get(freq);
            if (receiver == null) {
                // Change hook is a no-op: the tick below snapshots and
                // ships — player receivers need no eager push
                receiver = new VirtualReceiver(freq, level, player.blockPosition(), power -> {});
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(level, receiver);
                // 1.10.2: Create's add-time evaluation skips non-
                // LinkBehaviour members — read once so the first GUI
                // frame shows a static channel instead of 0
                receiver.readInitial();
                current.put(freq, receiver);
            } else {
                receiver.update(level, player.blockPosition());
            }
        }

        if (current.isEmpty()) {
            ACTIVE.remove(player.getUUID());
            if (LAST_SENT.remove(player.getUUID()) != null) {
                // Ship the empty snapshot once so stale dials zero out
                PacketDistributor.sendToPlayer(player,
                        new ModNetworking.GaugeReadingsPayload(List.of()));
            }
            return;
        }

        // 4. Cadence-guarded snapshot sync
        long now = level.getGameTime();
        Long last = LAST_SYNC.get(player.getUUID());
        if (last != null && now - last < SYNC_TICKS) return;

        Map<Frequency, Integer> snapshot = new HashMap<>();
        current.forEach((freq, receiver) -> snapshot.put(freq, receiver.getReceivedStrength()));
        if (snapshot.equals(LAST_SENT.get(player.getUUID()))) return;

        List<ModNetworking.GaugeReading> readings = new ArrayList<>(snapshot.size());
        snapshot.forEach((freq, strength) ->
                readings.add(new ModNetworking.GaugeReading(freq, strength)));
        PacketDistributor.sendToPlayer(player, new ModNetworking.GaugeReadingsPayload(readings));
        LAST_SENT.put(player.getUUID(), snapshot);
        LAST_SYNC.put(player.getUUID(), now);
    }

    /** Player owning this network member, or null — the Frequency
     * Monitor's classification hook (1.11.0). */
    @Nullable
    public static String ownerName(com.simibubi.create.content.redstone.link.IRedstoneLinkable member,
                                   net.minecraft.server.MinecraftServer server) {
        for (Map.Entry<UUID, Map<Frequency, VirtualReceiver>> entry : ACTIVE.entrySet()) {
            if (entry.getValue().containsValue(member)) {
                var player = server.getPlayerList().getPlayer(entry.getKey());
                return player != null ? player.getGameProfile().getName() : null;
            }
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        Map<Frequency, VirtualReceiver> current = ACTIVE.remove(uuid);
        if (current != null) {
            current.values().forEach(VirtualReceiver::removeFromNetwork);
        }
        LAST_SENT.remove(uuid);
        LAST_SYNC.remove(uuid);
    }
}
