package com.modpack.linktablet.compat;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.Gauge;
import com.modpack.linktablet.frequency.MonitorChannels;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.network.ModNetworking.MonitorChannel;
import com.modpack.linktablet.network.ModNetworking.MonitorMember;
import com.modpack.linktablet.network.ModNetworking.MonitorSnapshotPayload;
import com.modpack.linktablet.network.ModNetworking.MonitorSubscribePayload;
import com.modpack.linktablet.network.ModNetworking.SignalTarget;
import com.modpack.linktablet.registry.ModDataComponents;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Frequency Monitor server scanner (1.11.0) — the GUI-viewer half of the
 * feature. A player subscribes one tablet target (block or item); every
 * {@link #SYNC_TICKS} ticks the viewed channels are re-derived from the
 * LIVE tablet ({@link MonitorChannels#channelsOf}) and each channel's
 * Create-network membership is classified and shipped down as a
 * {@link MonitorSnapshotPayload}. A subscription silently expires
 * {@link #VIEWER_EXPIRE_TICKS} after the last (re-)subscribe if the
 * client stops refreshing it, and dies immediately once the underlying
 * target stops resolving (block broken, item gone, out of range).
 * <p>
 * Range classification reuses Create's own
 * {@link RedstoneLinkNetworkHandler#withinRange} via a stand-in
 * {@link AnchorProbe} planted at the viewer's tablet, so config changes
 * to Create's link range apply here for free. Verified against Create
 * 6.0.10's decompiled {@code withinRange} (javap): a reference-equality
 * short-circuit, then {@code getLocation()} on both sides plus the
 * {@code linkRange} config — {@code getNetworkKey()} is never read, so
 * the probe's null key is safe.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID)
public final class MonitorScanner {

    /** How close a player must be to a block target to monitor it
     * (squared) — same budget as ModNetworking's edit payloads. */
    private static final double MAX_BLOCK_DISTANCE_SQ = 64.0;

    /** A subscription with no refresh this long (5s) is dropped. */
    private static final long VIEWER_EXPIRE_TICKS = 100;

    /** Minimum ticks between snapshot syncs to one viewer. */
    private static final long SYNC_TICKS = 4;

    /** Wire cap per channel — matches MonitorChannel's list(64) codec. */
    private static final int WIRE_CAP = 64;

    private record Viewer(SignalTarget target, long lastSeen) {
    }

    /** One tablet's live signals/gauges/probe, re-read every poll. */
    private record TabletData(List<Signal> signals, List<Gauge> gauges, List<Frequency> probes) {
    }

    private static final Map<UUID, Viewer> VIEWERS = new HashMap<>();
    private static final Map<UUID, List<MonitorChannel>> LAST_SENT = new HashMap<>();
    private static final Map<UUID, Long> LAST_SYNC = new HashMap<>();

    /** How often registered kiosk faces re-scan their channels (block half). */
    private static final long BLOCK_SCAN_TICKS = 4;

    /**
     * Placed tablets currently showing the Monitor program (block half,
     * 1.11.0) — identity set so a BE overriding equals/hashCode down the
     * line can never dedupe two distinct blocks into one summary.
     * Registered by {@link TabletBlockEntity#setCurrentProgram} and
     * {@code onLoad}, unregistered on program change away, removal, or
     * surface-part demotion — see the class it lives in for the exact
     * hooks. {@link #onLevelTick} also self-heals: any entry that's gone
     * {@code isRemoved()} or whose chunk unloaded gets swept before its
     * channels are scanned, so the static set can't leak across world
     * reloads (onLoad re-registers once the chunk comes back).
     */
    private static final Set<TabletBlockEntity> BLOCKS =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private MonitorScanner() {
    }

    // ------------------------------------------------------------------
    // Block registry (kiosk faces) — the 1.11.0 companion to the viewer
    // half above: no subscription, no expiry, just "is this BE currently
    // showing Monitor" tracked by the BE itself.
    // ------------------------------------------------------------------

    public static void registerBlock(TabletBlockEntity be) {
        // setCurrentProgram/onLoad fire on BOTH logical sides — only the
        // server BE instance ever gets scanned (onLevelTick filters to
        // ServerLevel), so admitting a client BE here would just leak: it
        // never matches a server level's tick pass, and a loaded client
        // chunk never trips the stale-sweep's isLoaded() check either.
        if (!(be.getLevel() instanceof ServerLevel)) return;
        BLOCKS.add(be);
    }

    public static void unregisterBlock(TabletBlockEntity be) {
        BLOCKS.remove(be);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (level.getGameTime() % BLOCK_SCAN_TICKS != 0) return;

        Iterator<TabletBlockEntity> it = BLOCKS.iterator();
        while (it.hasNext()) {
            TabletBlockEntity be = it.next();
            Level beLevel = be.getLevel();
            // Stale sweep runs on every level's own tick pass: a block
            // broken (isRemoved), or its chunk no longer loaded (chunk
            // unload never calls setRemoved) — onLoad re-registers if it
            // comes back showing Monitor, so evicting here is safe.
            if (be.isRemoved() || beLevel == null || !beLevel.isLoaded(be.getBlockPos())) {
                it.remove();
                continue;
            }
            if (beLevel != level) continue; // scanned on ITS OWN level's tick
            scanBlock(level, be);
        }
    }

    /** One registered controller's channels, reduced to counts + power,
     * pushed to the BE only when changed (the {@code onGaugeReading} shape). */
    private static void scanBlock(ServerLevel level, TabletBlockEntity be) {
        List<Frequency> channels =
                MonitorChannels.channelsOf(be.getSignals(), be.getGauges(), be.getMonitorProbes());
        int n = channels.size();
        int[] counts = new int[n];
        int[] power = new int[n];
        BlockPos anchor = be.getBlockPos();
        for (int i = 0; i < n; i++) {
            int count = 0;
            int maxPower = 0;
            for (MonitorMember member : scanChannel(level, channels.get(i), anchor)) {
                if (member.strength() > 0 && member.inRange()) {
                    count++;
                    maxPower = Math.max(maxPower, member.strength());
                }
            }
            counts[i] = count;
            power[i] = maxPower;
        }
        be.setMonitorSummary(counts, power);
    }

    // ------------------------------------------------------------------
    // Subscribe / unsubscribe
    // ------------------------------------------------------------------

    public static void handleSubscribe(MonitorSubscribePayload payload, IPayloadContext context) {
        Player player = context.player();
        UUID uuid = player.getUUID();
        if (!payload.active()) {
            drop(uuid);
            return;
        }
        // Validate exactly like ModNetworking.resolve does — an invalid
        // target never gets a viewer, so the tick loop never has to guess.
        if (resolveData(player, payload.target()) == null) return;
        VIEWERS.put(uuid, new Viewer(payload.target(), player.level().getGameTime()));
    }

    private static void drop(UUID uuid) {
        VIEWERS.remove(uuid);
        LAST_SENT.remove(uuid);
        LAST_SYNC.remove(uuid);
    }

    // ------------------------------------------------------------------
    // Target resolution — mirrors ModNetworking.resolve's validation
    // (block: loaded + in range + TabletBlockEntity + controller; item:
    // slot bounds/hand + TabletItem). ModNetworking's own helpers stay
    // private, so the checks are copied here rather than shared.
    // ------------------------------------------------------------------

    /** Payload range check, Sable-aware — identical rule to
     * ModNetworking.tabletDistSqr (copied, not shared: that method is
     * private over there). */
    private static double tabletDistSqr(Player player, BlockPos pos) {
        Vec3 eye = SableCompat.localizeNear(player.level(), pos, player.getEyePosition());
        return eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    @Nullable
    private static TabletData resolveData(Player player, SignalTarget target) {
        if (target.pos().isPresent()) {
            BlockPos pos = target.pos().get();
            if (!player.level().isLoaded(pos)) return null;
            if (tabletDistSqr(player, pos) > MAX_BLOCK_DISTANCE_SQ) return null;
            if (!(player.level().getBlockEntity(pos) instanceof TabletBlockEntity clicked)) return null;
            // Merged surfaces: the controller carries the real signals/
            // gauges/probe, exactly like ModNetworking's edit payloads.
            TabletBlockEntity be = clicked.resolveController();
            if (be == null) return null;
            return new TabletData(be.getSignals(), be.getGauges(), be.getMonitorProbes());
        }
        ItemStack stack;
        if (target.slot().isPresent()) {
            int slot = target.slot().get();
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) return null;
            stack = player.getInventory().getItem(slot);
        } else {
            stack = player.getItemInHand(
                    target.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        }
        if (!(stack.getItem() instanceof TabletItem)) return null;
        return new TabletData(
                stack.getOrDefault(ModDataComponents.TABLET_SIGNALS.get(), List.of()),
                stack.getOrDefault(ModDataComponents.TABLET_GAUGES.get(), List.of()),
                stack.getOrDefault(ModDataComponents.MONITOR_PROBE.get(), List.of()));
    }

    // ------------------------------------------------------------------
    // Poll loop
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID uuid = player.getUUID();
        Viewer viewer = VIEWERS.get(uuid);
        if (viewer == null) return;

        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        if (now - viewer.lastSeen() > VIEWER_EXPIRE_TICKS) {
            drop(uuid);
            return;
        }
        Long lastSync = LAST_SYNC.get(uuid);
        if (lastSync != null && now - lastSync < SYNC_TICKS) return;
        LAST_SYNC.put(uuid, now);

        // signals/gauges/probe come from re-resolving the viewer's target
        // every poll — stale block targets (broken tablet) or an out-of-
        // range walk silently expire via the resolve failure below.
        TabletData data = resolveData(player, viewer.target());
        if (data == null) {
            drop(uuid);
            return;
        }

        List<Frequency> channels = MonitorChannels.channelsOf(data.signals(), data.gauges(), data.probes());
        BlockPos anchor = viewer.target().pos().orElse(player.blockPosition());
        List<MonitorChannel> snapshot = new ArrayList<>();
        for (Frequency channel : channels) {
            // MonitorSnapshotPayload's codec caps the channel list at 64
            // (ByteBufCodecs.writeCount throws EncoderException past it) —
            // truncate here rather than let encode disconnect the viewer.
            // Truncation keeps prefix alignment with channelsOf's order.
            if (snapshot.size() >= WIRE_CAP) break;
            snapshot.add(new MonitorChannel(channel, scanChannel(level, channel, anchor)));
        }
        if (snapshot.equals(LAST_SENT.get(uuid))) return;
        PacketDistributor.sendToPlayer(player, new MonitorSnapshotPayload(snapshot));
        LAST_SENT.put(uuid, snapshot);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        drop(event.getEntity().getUUID());
    }

    // ------------------------------------------------------------------
    // Classification core
    // ------------------------------------------------------------------

    /** Range probe standing in for the viewing tablet at {@code anchor}
     * — lets Create's own withinRange decide, config included. */
    private record AnchorProbe(BlockPos pos) implements IRedstoneLinkable {
        @Override
        public int getTransmittedStrength() {
            return 0;
        }

        @Override
        public void setReceivedStrength(int power) {
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() {
            return null;
        }

        @Override
        public BlockPos getLocation() {
            return pos;
        }
    }

    /**
     * Localizes a member's position for DISPLAY relative to
     * {@code anchor} — Sable-aware (near-anchor points pass through
     * free; a physicalized member's plot coordinate is brought into the
     * viewer's own frame, the 1.10.1 rule). Range math never uses this:
     * it stays on RAW positions, since Create's own network already
     * spans plot boundaries correctly (the 1.10.2 finding).
     */
    private static BlockPos displayPos(ServerLevel level, BlockPos anchor, BlockPos memberPos) {
        Vec3 local = SableCompat.localizeNear(level, anchor, Vec3.atCenterOf(memberPos));
        return BlockPos.containing(local);
    }

    static List<MonitorMember> scanChannel(ServerLevel level, Frequency channel, BlockPos anchor) {
        Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(channel.stack1()),
                RedstoneLinkNetworkHandler.Frequency.of(channel.stack2()));
        Set<IRedstoneLinkable> members = Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(level).get(key);
        if (members == null) return List.of();
        AnchorProbe probeAt = new AnchorProbe(anchor);
        List<MonitorMember> out = new ArrayList<>();
        for (IRedstoneLinkable member : members) {
            if (!member.isAlive()) continue;
            if (out.size() >= WIRE_CAP) break; // wire cap
            byte type;
            Component label;
            String owner = TabletTransmitterHandler.ownerName(member, level.getServer());
            if (owner == null) owner = TabletReceiverHandler.ownerName(member, level.getServer());
            if (owner != null) {
                type = ModNetworking.MEMBER_PLAYER_TABLET;
                label = Component.translatable("gui.linktablet.monitor.member.player", owner);
            } else if (DroppedTabletHandler.classify(member) instanceof DroppedTabletHandler.Origin origin) {
                if (origin.framed()) {
                    type = ModNetworking.MEMBER_FRAMED_TABLET;
                    label = Component.translatable("gui.linktablet.monitor.member.framed");
                } else {
                    type = ModNetworking.MEMBER_DROPPED_TABLET;
                    label = origin.throwerName() != null
                            ? Component.translatable("gui.linktablet.monitor.member.dropped", origin.throwerName())
                            : Component.translatable("gui.linktablet.monitor.member.dropped.anon");
                }
            } else if (member instanceof VirtualTransmitter || member instanceof VirtualReceiver
                    || level.getBlockEntity(member.getLocation()) instanceof TabletBlockEntity) {
                type = ModNetworking.MEMBER_PLACED_TABLET;
                label = Component.translatable("gui.linktablet.monitor.member.placed");
            } else if (member instanceof BlockEntityBehaviour) {
                // javap confirmed LinkBehaviour extends BlockEntityBehaviour
                // (public, stable) — real Redstone Links / Linked
                // Controllers classify here.
                type = ModNetworking.MEMBER_LINK_BLOCK;
                label = level.getBlockState(member.getLocation()).getBlock().getName();
            } else {
                type = ModNetworking.MEMBER_OTHER;
                label = Component.literal(member.getClass().getSimpleName());
            }
            out.add(new MonitorMember(type, label,
                    displayPos(level, anchor, member.getLocation()),
                    member.getTransmittedStrength(), member.isListening(),
                    RedstoneLinkNetworkHandler.withinRange(probeAt, member)));
        }
        out.sort(Comparator.comparingInt((MonitorMember m) -> -m.strength()));
        return out;
    }
}
