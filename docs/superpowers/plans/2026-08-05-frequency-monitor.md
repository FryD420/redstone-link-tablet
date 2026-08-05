# Frequency Monitor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Frequency Monitor program (1.11.0): a launcher app that shows, per Redstone Link channel the tablet uses (plus one probe channel), every live member of Create's network — classified, with strength and range — on the GUI, the kiosk face, and the overlay pin.

**Architecture:** Server-computed snapshots (Create's `RedstoneLinkNetworkHandler` is server-only). A new `compat/MonitorScanner` polls `networksIn(level)` for subscribed players (GUI) and registered kiosk controllers (faces), classifies members, and ships change-guarded payloads / update tags. One shared channel-derivation helper (`MonitorChannels`) keeps server, GUI, and face renderer on the same channel order — the `gridLayout` one-source idiom. Spec: `docs/superpowers/specs/2026-08-05-frequency-monitor-design.md`.

**Tech Stack:** NeoForge 1.21.1 (21.1.233), Create 6.0.10, Java 21. No unit-test infra — each task's gate is `./gradlew build` green; in-world verification is the user's dev pass at the end.

## Global Constraints

- **Registrar "18" → "19"** — 1.11.0 is a pairing break. All new wire types land under the one bump.
- **This repo commits ONLY when the user asks** (CLAUDE.md) — batch commits for the user's go; the per-task "Commit" steps below mean "stage + report", not `git commit`.
- Program KEYS are the persisted identity — key `"monitor"`, id 26, both frozen forever once shipped.
- The probe (`monitor_probe`) is the feature's ONLY persisted data; default (empty) is NEVER written — the theme idiom.
- Stream codecs for records with >6 fields are HAND-ROLLED (`StreamCodec.composite` maxes ~6).
- Three-pass rule on every world face: all quads, then all items, then all text.
- Sable (1.10.1/1.10.2 rules): payload range checks go through `tabletDistSqr`; any DISPLAYED coords/distance localize through `SableCompat.localizeNear`; never `getMethod` in reflection.
- Read-only v1: no mutation of Create's network, no addon-API exposure.
- Mod version stays 1.10.2 until the release step (user-gated); CHANGELOG entries go under "Unreleased".

---

### Task 1: Program.MONITOR + lang

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/Program.java` (enum constants, after `STORE(5, ...)` / the id-6 comment block)
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

**Interfaces:**
- Produces: `Program.MONITOR` (id 26, key `"monitor"`) — every later task dispatches on it.

- [ ] **Step 1: Add the enum constant** after `PAINT(25, ...)` (ids are append-only; 26 is the next free — 6 is retired, never reuse):

```java
    /** Frequency Monitor (1.11.0): read-only view of Create's link
     * network — who transmits on each channel the tablet uses. */
    MONITOR(26, "monitor", 0xFF16A0A0, "minecraft:spyglass");
```

(Change `PAINT(...)`'s trailing `;` to `,`.)

- [ ] **Step 2: Lang keys** — add to `en_us.json` beside the other `program.linktablet.*` entries:

```json
"program.linktablet.monitor": "Frequency Monitor",
"program.linktablet.monitor.desc": "See every transmitter and receiver on your channels — strength, range, and who's broadcasting.",
"gui.linktablet.monitor.probe": "Probe",
"gui.linktablet.monitor.probe.hint": "Set two items to watch any channel",
"gui.linktablet.monitor.empty": "No channels — add signals, gauges, or a probe",
"gui.linktablet.monitor.member.placed": "Placed tablet",
"gui.linktablet.monitor.member.player": "Tablet (%s)",
"gui.linktablet.monitor.transmitters": "%s transmitting",
"gui.linktablet.monitor.out_of_range": "out of range"
```

- [ ] **Step 3: Build + sanity** — `./gradlew build` green. (The store row, launcher tile, label kiosk face, and launcher-fallback screen all come free from the `Programs` table + existing `default ->` branches; the real screen arrives in Task 6.)
- [ ] **Step 4: Stage** — `git add` the two files; report done (no commit — user-gated).

### Task 2: Shared channel derivation (`MonitorChannels`)

**Files:**
- Create: `src/main/java/com/modpack/linktablet/frequency/MonitorChannels.java`

**Interfaces:**
- Produces: `MonitorChannels.channelsOf(List<Signal> signals, List<Gauge> gauges, Frequency probe)` → `List<Frequency>` — THE one channel table. Server scanner (Task 5), BE summaries (Task 7), face renderer (Task 7), and GUI (Task 6) all call it; never fork the ordering.

- [ ] **Step 1: Write the helper** — deterministic, order-stable dedupe (`Frequency.equals` already delegates to Create's channel identity):

```java
package com.modpack.linktablet.frequency;

import java.util.ArrayList;
import java.util.List;

/**
 * The ONE channel table for the Frequency Monitor (1.11.0): probe
 * first, then every signal frequency, then every gauge frequency,
 * deduped by Create-network identity in first-seen order. Server
 * scanner, BE summary sync, face renderer, and GUI all derive the
 * same indexed list from the same synced data — index-mapped wire
 * forms (the {@code gauge_readings} idiom) stay aligned for free.
 */
public final class MonitorChannels {

    public static List<Frequency> channelsOf(List<Signal> signals, List<Gauge> gauges,
                                             Frequency probe) {
        List<Frequency> channels = new ArrayList<>();
        if (probe != null && !probe.isEmpty()) channels.add(probe);
        for (Signal signal : signals) {
            for (Frequency freq : signal.frequencies()) {
                if (!freq.isEmpty() && !channels.contains(freq)) channels.add(freq);
            }
        }
        for (Gauge gauge : gauges) {
            Frequency freq = gauge.frequency();
            if (!freq.isEmpty() && !channels.contains(freq)) channels.add(freq);
        }
        return channels;
    }

    private MonitorChannels() {
    }
}
```

(Verify `Signal.frequencies()` and `Gauge.frequency()` are the real accessor names before building — they are used exactly so in `ModNetworking.handleToggle` / `handleUpsertGauge`.)

- [ ] **Step 2: Build** — `./gradlew build` green. **Step 3: Stage.**

### Task 3: Wire types + registrar "19" + client store

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/network/ModNetworking.java`
- Create: `src/main/java/com/modpack/linktablet/client/ClientMonitorSnapshot.java`

**Interfaces:**
- Produces (all nested in `ModNetworking`, the `GaugeReading` idiom):
  - `MonitorMember(byte type, Component label, BlockPos pos, int strength, boolean listening, boolean inRange)` + type constants `MEMBER_LINK_BLOCK=0, MEMBER_PLACED_TABLET=1, MEMBER_PLAYER_TABLET=2, MEMBER_OTHER=3`
  - `MonitorChannel(Frequency frequency, List<MonitorMember> members)`
  - `MonitorSubscribePayload(SignalTarget target, boolean active)` — playToServer; handler lives in Task 5's scanner
  - `SetProbePayload(SignalTarget target, Frequency probe)` — playToServer; handler in Task 4
  - `MonitorSnapshotPayload(List<MonitorChannel> channels)` — playToClient → `ClientMonitorSnapshot.accept`
- Produces: `ClientMonitorSnapshot` — client store + subscription pinger (Task 6/8 read it).

- [ ] **Step 1: Records + codecs** — add after the `SetProgramPayload` block. `MonitorMember` has 6 fields: hand-roll the codec anyway (the `Signal` precedent — the next field would force it):

```java
    // ------------------------------------------------------------------
    // Frequency Monitor (1.11.0): read-only snapshots of Create's link
    // network. Members are classified SERVER-side; labels travel as
    // Components so block names localize on the client.
    // ------------------------------------------------------------------
    public static final byte MEMBER_LINK_BLOCK = 0;
    public static final byte MEMBER_PLACED_TABLET = 1;
    public static final byte MEMBER_PLAYER_TABLET = 2;
    public static final byte MEMBER_OTHER = 3;

    public record MonitorMember(byte type, Component label, BlockPos pos, int strength,
                                boolean listening, boolean inRange) {
        public static final StreamCodec<RegistryFriendlyByteBuf, MonitorMember> STREAM_CODEC =
                StreamCodec.of((buf, m) -> {
                    buf.writeByte(m.type());
                    net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC
                            .encode(buf, m.label());
                    BlockPos.STREAM_CODEC.encode(buf, m.pos());
                    buf.writeVarInt(m.strength());
                    buf.writeBoolean(m.listening());
                    buf.writeBoolean(m.inRange());
                }, buf -> new MonitorMember(
                        buf.readByte(),
                        net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC
                                .decode(buf),
                        BlockPos.STREAM_CODEC.decode(buf),
                        buf.readVarInt(),
                        buf.readBoolean(),
                        buf.readBoolean()));
    }

    public record MonitorChannel(com.modpack.linktablet.frequency.Frequency frequency,
                                 List<MonitorMember> members) {
        public static final StreamCodec<RegistryFriendlyByteBuf, MonitorChannel> STREAM_CODEC =
                StreamCodec.composite(
                        com.modpack.linktablet.frequency.Frequency.STREAM_CODEC,
                        MonitorChannel::frequency,
                        MonitorMember.STREAM_CODEC.apply(ByteBufCodecs.list(64)),
                        MonitorChannel::members,
                        MonitorChannel::new);
    }

    public record MonitorSubscribePayload(SignalTarget target, boolean active)
            implements CustomPacketPayload {
        public static final Type<MonitorSubscribePayload> TYPE = new Type<>(id("monitor_subscribe"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MonitorSubscribePayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, MonitorSubscribePayload::target,
                        ByteBufCodecs.BOOL, MonitorSubscribePayload::active,
                        MonitorSubscribePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetProbePayload(SignalTarget target,
                                  com.modpack.linktablet.frequency.Frequency probe)
            implements CustomPacketPayload {
        public static final Type<SetProbePayload> TYPE = new Type<>(id("set_probe"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetProbePayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, SetProbePayload::target,
                        com.modpack.linktablet.frequency.Frequency.STREAM_CODEC,
                        SetProbePayload::probe,
                        SetProbePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MonitorSnapshotPayload(List<MonitorChannel> channels)
            implements CustomPacketPayload {
        public static final Type<MonitorSnapshotPayload> TYPE = new Type<>(id("monitor_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MonitorSnapshotPayload> STREAM_CODEC =
                MonitorChannel.STREAM_CODEC.apply(ByteBufCodecs.list(64))
                        .map(MonitorSnapshotPayload::new, MonitorSnapshotPayload::channels);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
```

- [ ] **Step 2: Registrar bump + registration** — append to the version-history comment and change the fence:

```java
        // "19": 1.11.0 Frequency Monitor — MonitorSubscribePayload,
        // SetProbePayload, MonitorSnapshotPayload (second playToClient).
        PayloadRegistrar registrar = event.registrar("19");
```

and register (subscribe/probe handlers are stubs until Tasks 4–5 — point them at the real classes now, create the methods there in the same session order):

```java
        registrar.playToServer(MonitorSubscribePayload.TYPE, MonitorSubscribePayload.STREAM_CODEC,
                com.modpack.linktablet.compat.MonitorScanner::handleSubscribe);
        registrar.playToServer(SetProbePayload.TYPE, SetProbePayload.STREAM_CODEC,
                ModNetworking::handleSetProbe);
        registrar.playToClient(MonitorSnapshotPayload.TYPE, MonitorSnapshotPayload.STREAM_CODEC,
                ModNetworking::handleMonitorSnapshot);
```

with the client-side handler (the `handleGaugeReadings` idiom — client class only loads when the body runs):

```java
    private static void handleMonitorSnapshot(MonitorSnapshotPayload payload, IPayloadContext context) {
        com.modpack.linktablet.client.ClientMonitorSnapshot.accept(payload.channels());
    }
```

- [ ] **Step 3: Client store + pinger** — `ClientMonitorSnapshot`, mirroring `ClientGaugeReadings` plus the re-ping duty (spec: ~2s re-ping, server expires at ~5s). One road for screen AND overlay:

```java
package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.network.ModNetworking;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Objects;

/**
 * Client store for Frequency Monitor snapshots (1.11.0) plus the
 * subscription heartbeat: while a consumer (MonitorScreen or the
 * overlay pin) is registered, a MonitorSubscribePayload re-pings every
 * PING_TICKS so the server keeps polling; dropping the last consumer
 * sends active=false. ONE target at a time — the monitor shows the
 * network as seen from one tablet.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class ClientMonitorSnapshot {

    private static final int PING_TICKS = 40;

    private static List<ModNetworking.MonitorChannel> channels = List.of();
    private static ModNetworking.SignalTarget target;
    private static int consumers;
    private static int ticksSincePing;

    public static void accept(List<ModNetworking.MonitorChannel> newChannels) {
        channels = newChannels;
    }

    public static List<ModNetworking.MonitorChannel> channels() {
        return channels;
    }

    /** Register a consumer watching {@code newTarget}; retargeting
     * (a second consumer on a different tablet) rebinds everyone —
     * last opener wins, matching overlay right-click behavior. */
    public static void acquire(ModNetworking.SignalTarget newTarget) {
        if (!Objects.equals(target, newTarget)) {
            target = newTarget;
            channels = List.of();
        }
        consumers++;
        sendPing(true);
        ticksSincePing = 0;
    }

    public static void release() {
        consumers = Math.max(0, consumers - 1);
        if (consumers == 0 && target != null) {
            sendPing(false);
            target = null;
            channels = List.of();
        }
    }

    private static void sendPing(boolean active) {
        if (target == null) return;
        PacketDistributor.sendToServer(
                new ModNetworking.MonitorSubscribePayload(target, active));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (consumers == 0 || target == null) return;
        if (++ticksSincePing >= PING_TICKS) {
            ticksSincePing = 0;
            sendPing(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        channels = List.of();
        target = null;
        consumers = 0;
    }

    private ClientMonitorSnapshot() {
    }
}
```

- [ ] **Step 4: Build** — will NOT compile until `MonitorScanner.handleSubscribe` and `handleSetProbe` exist; do Tasks 4–5 before the build gate if executing strictly in order, OR add both stubs now (empty bodies) and fill them in their tasks. Prefer the stubs — every task keeps its own green build. **Step 5: Stage.**

### Task 4: Probe persistence

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/registry/ModDataComponents.java`
- Modify: `src/main/java/com/modpack/linktablet/block/TabletBlockEntity.java` (loadAdditional/saveAdditional, loadFromItem/saveToItem, getter/setter)
- Modify: `src/main/java/com/modpack/linktablet/network/ModNetworking.java` (fill `handleSetProbe`)
- Modify: `src/main/java/com/modpack/linktablet/client/SignalView.java` (probe accessor per view)

**Interfaces:**
- Produces: `ModDataComponents.MONITOR_PROBE` (`Frequency`), `TabletBlockEntity.getMonitorProbe()/setMonitorProbe(Frequency)`, `SignalView.monitorProbe()` → `Frequency` (EMPTY when unset).

- [ ] **Step 1: Component** — beside `TABLET_GAUGES`:

```java
    /** Frequency Monitor probe channel (1.11.0); absent = none
     * (never written empty — the theme idiom). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.modpack.linktablet.frequency.Frequency>> MONITOR_PROBE =
            DATA_COMPONENTS.register("monitor_probe", () -> DataComponentType.<com.modpack.linktablet.frequency.Frequency>builder()
                    .persistent(com.modpack.linktablet.frequency.Frequency.CODEC)
                    .networkSynchronized(com.modpack.linktablet.frequency.Frequency.STREAM_CODEC)
                    .build());
```

- [ ] **Step 2: BE field + NBT** — private `Frequency monitorProbe = Frequency.EMPTY;` with getter/setter (setter marks dirty + `sendBlockUpdated`, the `setGauges` shape). In `saveAdditional`: write `"monitor_probe"` only when non-empty (`Frequency.CODEC` via `tag.put("monitor_probe", encoded)` — follow exactly how `gauges` NBT encodes with a codec). In `loadAdditional`: read when present, else EMPTY. Add to `loadFromItem` (`stack.getOrDefault(ModDataComponents.MONITOR_PROBE.get(), Frequency.EMPTY)`) and to the BE→item save path beside the `TABLET_GAUGES` line at `TabletBlockEntity.java:527`, skipping empty.
- [ ] **Step 3: Payload handler** — in `ModNetworking`, the `handleSetTheme` both-target shape:

```java
    private static void handleSetProbe(SetProbePayload payload, IPayloadContext context) {
        Player player = context.player();
        com.modpack.linktablet.frequency.Frequency probe = payload.probe();
        if (payload.target().pos().isPresent()) {
            BlockPos pos = payload.target().pos().get();
            if (!player.level().isLoaded(pos)) return;
            if (tabletDistSqr(player, pos) > MAX_BLOCK_DISTANCE_SQ) return;
            if (player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
                TabletBlockEntity controller = be.resolveController();
                if (controller != null) {
                    controller.setMonitorProbe(probe);
                }
            }
            return;
        }
        ItemStack stack = resolveStack(player, payload.target());
        if (!stack.isEmpty()) {
            if (probe.isEmpty()) {
                stack.remove(ModDataComponents.MONITOR_PROBE.get());
            } else {
                stack.set(ModDataComponents.MONITOR_PROBE.get(), probe);
            }
        }
    }
```

- [ ] **Step 4: SignalView accessor** — `monitorProbe()` beside the gauges accessors at `SignalView.java:112/152`: block view reads the BE, item views read the component, defaulting `Frequency.EMPTY`.
- [ ] **Step 5: Build green. Step 6: Stage.**

### Task 5: Server scanner + member classification

**Files:**
- Create: `src/main/java/com/modpack/linktablet/compat/MonitorScanner.java`
- Modify: `src/main/java/com/modpack/linktablet/compat/TabletTransmitterHandler.java` (+`ownerName`)
- Modify: `src/main/java/com/modpack/linktablet/compat/TabletReceiverHandler.java` (+`ownerName`)

**Interfaces:**
- Consumes: `MonitorChannels.channelsOf`, `ModNetworking.MonitorMember/MonitorChannel/MonitorSnapshotPayload`, `SignalTarget`, `VirtualTransmitter/VirtualReceiver`, Create's `networksIn`/`withinRange`.
- Produces: `MonitorScanner.handleSubscribe(MonitorSubscribePayload, IPayloadContext)` (referenced by Task 3's registration) and `MonitorScanner.registerBlock/unregisterBlock(TabletBlockEntity)` (Task 7).

- [ ] **Step 1: Owner lookups** — one static method in EACH handler over its `ACTIVE` map (`Map<UUID, Map<Frequency, VirtualTransmitter|VirtualReceiver>>`); shown for the transmitter side, mirror for the receiver side:

```java
    /** Player owning this network member, or null — the Frequency
     * Monitor's classification hook (1.11.0). */
    @Nullable
    public static String ownerName(com.simibubi.create.content.redstone.link.IRedstoneLinkable member,
                                   net.minecraft.server.MinecraftServer server) {
        for (Map.Entry<UUID, Map<Frequency, VirtualTransmitter>> entry : ACTIVE.entrySet()) {
            if (entry.getValue().containsValue(member)) {
                var player = server.getPlayerList().getPlayer(entry.getKey());
                return player != null ? player.getGameProfile().getName() : null;
            }
        }
        return null;
    }
```

- [ ] **Step 2: MonitorScanner** — `@EventBusSubscriber(modid = LinkTabletMod.MOD_ID)`, GUI-viewer half:
  - `private record Viewer(ModNetworking.SignalTarget target, long lastSeen)` in `Map<UUID, Viewer>` + `Map<UUID, List<ModNetworking.MonitorChannel>> lastSent` + `Map<UUID, Long> lastSync`.
  - `handleSubscribe`: `active=false` → remove all three map entries; `active=true` → validate the target exactly like `ModNetworking.resolve` does (block: loaded + `tabletDistSqr ≤ 64.0` + `TabletBlockEntity`; item: `resolveStack`-style tablet check — replicate the checks, the private helpers stay private) then store `new Viewer(target, gameTime)`.
  - `PlayerTickEvent.Post` (the `TabletReceiverHandler` shape): skip unless a `Viewer` exists; expire (remove) when `gameTime - lastSeen > 100` (5s); cadence-guard 4 ticks via `lastSync`; then build + send:

```java
        List<Frequency> channels = MonitorChannels.channelsOf(signals, gauges, probe);
        // signals/gauges/probe come from re-resolving the viewer's
        // target every poll — stale block targets (broken tablet)
        // silently expire via the resolve failure
        BlockPos anchor = viewer.target().pos().orElse(player.blockPosition());
        List<ModNetworking.MonitorChannel> snapshot = new ArrayList<>();
        for (Frequency channel : channels) {
            snapshot.add(new ModNetworking.MonitorChannel(channel,
                    scanChannel(level, channel, anchor)));
        }
        if (snapshot.equals(lastSent.get(player.getUUID()))) return;
        PacketDistributor.sendToPlayer(player,
                new ModNetworking.MonitorSnapshotPayload(snapshot));
        lastSent.put(player.getUUID(), snapshot);
```

  - `scanChannel(ServerLevel level, Frequency channel, BlockPos anchor)` — the classification core:

```java
    /** Range probe standing in for the viewing tablet at {@code anchor}
     * — lets Create's own withinRange decide, config included. */
    private record AnchorProbe(BlockPos pos) implements IRedstoneLinkable {
        @Override public int getTransmittedStrength() { return 0; }
        @Override public void setReceivedStrength(int power) {}
        @Override public boolean isListening() { return false; }
        @Override public boolean isAlive() { return true; }
        @Override public Couple<RedstoneLinkNetworkHandler.Frequency> getNetworkKey() { return null; }
        @Override public BlockPos getLocation() { return pos; }
    }

    private static List<ModNetworking.MonitorMember> scanChannel(
            ServerLevel level, Frequency channel, BlockPos anchor) {
        Couple<RedstoneLinkNetworkHandler.Frequency> key = Couple.create(
                RedstoneLinkNetworkHandler.Frequency.of(channel.stack1()),
                RedstoneLinkNetworkHandler.Frequency.of(channel.stack2()));
        Set<IRedstoneLinkable> members =
                Create.REDSTONE_LINK_NETWORK_HANDLER.networksIn(level).get(key);
        if (members == null) return List.of();
        AnchorProbe probeAt = new AnchorProbe(anchor);
        List<ModNetworking.MonitorMember> out = new ArrayList<>();
        for (IRedstoneLinkable member : members) {
            if (!member.isAlive()) continue;
            if (out.size() >= 64) break; // wire cap
            byte type;
            Component label;
            String owner = TabletTransmitterHandler.ownerName(member, level.getServer());
            if (owner == null) owner = TabletReceiverHandler.ownerName(member, level.getServer());
            if (owner != null) {
                type = ModNetworking.MEMBER_PLAYER_TABLET;
                label = Component.translatable("gui.linktablet.monitor.member.player", owner);
            } else if (member instanceof VirtualTransmitter || member instanceof VirtualReceiver
                    || level.getBlockEntity(member.getLocation())
                            instanceof com.modpack.linktablet.block.TabletBlockEntity) {
                type = ModNetworking.MEMBER_PLACED_TABLET;
                label = Component.translatable("gui.linktablet.monitor.member.placed");
            } else if (member instanceof com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour) {
                type = ModNetworking.MEMBER_LINK_BLOCK;
                label = level.getBlockState(member.getLocation()).getBlock().getName();
            } else {
                type = ModNetworking.MEMBER_OTHER;
                label = Component.literal(member.getClass().getSimpleName());
            }
            out.add(new ModNetworking.MonitorMember(type, label,
                    displayPos(level, anchor, member.getLocation()),
                    member.getTransmittedStrength(), member.isListening(),
                    RedstoneLinkNetworkHandler.withinRange(probeAt, member)));
        }
        out.sort(java.util.Comparator
                .comparingInt((ModNetworking.MonitorMember m) -> -m.strength()));
        return out;
    }
```

  Notes for the implementer: check `LinkBehaviour`'s actual supertype before using the `BlockEntityBehaviour` instanceof — if it isn't public/stable, classify Create links by "getLocation() has a BlockEntity and the member isn't ours" instead. `displayPos` localizes for display: `SableCompat.localizeNear(level, anchor, Vec3.atCenterOf(memberPos))` rounded back to BlockPos when the member sits on a physicalized plot (the 1.10.1 rule: near-anchor points pass through free); range math above stays on RAW positions (Create handles plot-boundary networks natively — 1.10.2 finding). The `getNetworkKey() == null` in AnchorProbe is safe: `withinRange` only reads locations (verify in the decompiled class before relying on it — if it touches the key, return `key` instead).
  - Logout cleanup (`PlayerEvent.PlayerLoggedOutEvent`): drop the player's three map entries.

- [ ] **Step 3: Replace Task 3's stubs** with the real `handleSubscribe`; build green. **Step 4: Stage.**

### Task 6: MonitorScreen + navigation

**Files:**
- Create: `src/main/java/com/modpack/linktablet/client/screen/MonitorScreen.java`
- Modify: `src/main/java/com/modpack/linktablet/client/ClientHooks.java:116` (screenFor switch)

**Interfaces:**
- Consumes: `ClientMonitorSnapshot.acquire/release/channels`, `SignalView` (theme, target, `monitorProbe()`, signals, gauges), `MonitorChannels.channelsOf` (row skeleton while the first snapshot is in flight), `SetProbePayload`, `OverlayPin.pin/isPinned` with `Program.MONITOR`.
- Produces: `MonitorScreen(SignalView view)` — the constructor `ClientHooks` and Task 7's face tap use.

- [ ] **Step 1: Screen skeleton** — pattern `GaugesScreen` end to end: same chrome painters, same header (title + per-app pin button via `OverlayPin.isPinned(view, Program.MONITOR)` / `.pin(...)`, the `GaugesScreen.java:251/412` shape), ESC → `ClientHooks.returnHome`-equivalent (copy GaugesScreen's onClose/home wiring exactly), scrolling via the LauncherScreen/StoreScreen scroll pattern. In `init()`: `ClientMonitorSnapshot.acquire(view.target())`; in `removed()`: `ClientMonitorSnapshot.release()`.
- [ ] **Step 2: Probe row** — two 18px ghost slots through `PickerOverlay` (the GaugesScreen modal-editor slot flow, minus the modal — slots sit inline in the pinned first row); on either slot changing, send `new ModNetworking.SetProbePayload(view.target(), new Frequency(slot1, slot2))` (both empty = clears). Label `gui.linktablet.monitor.probe`, hint text when empty.
- [ ] **Step 3: Channel rows** — for each `ClientMonitorSnapshot.channels()` entry: freq icons (`Frequency.icon1/icon2`, the gauge-editor rendering), summary line (`gui.linktablet.monitor.transmitters` with the count of members where `strength > 0 && inRange`, plus effective power = max such strength as a 0–15 bar — procedural `fill()`, the slider-bar look), then member rows: label Component, strength numeral, listening/transmitting badge (procedural glyph), dimmed + `gui.linktablet.monitor.out_of_range` suffix when `!inRange`, coords as `x y z` in the row's right edge. Rows with no snapshot yet render skeleton-only from `MonitorChannels.channelsOf(view.signals(), view.gauges(), view.monitorProbe())`. Empty state: `gui.linktablet.monitor.empty` centered.
- [ ] **Step 4: Wire navigation** — `ClientHooks.screenFor`: add `case MONITOR -> new com.modpack.linktablet.client.screen.MonitorScreen(view);` before `default`.
- [ ] **Step 5: Build green. Step 6: Stage.** GUI-scale-2 vertical budget is 240 units — the screen scrolls, so no budget math needed, but keep row heights matching GaugesScreen's row rhythm.

### Task 7: Kiosk face + BE summaries

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/compat/MonitorScanner.java` (block half)
- Modify: `src/main/java/com/modpack/linktablet/block/TabletBlockEntity.java` (summary fields + update tag + register/unregister)
- Modify: `src/main/java/com/modpack/linktablet/client/render/TabletScreenRenderer.java` (+`renderMonitorFace`)
- Modify: `src/main/java/com/modpack/linktablet/client/render/TabletBlockEntityRenderer.java:113` (face switch)

**Interfaces:**
- Consumes: `MonitorScanner.scanChannel` (reused — counts derive from the same member scan), `MonitorChannels.channelsOf`.
- Produces: BE transient `int[] monitorCounts / monitorPower` (index-aligned with `channelsOf(signals, gauges, probe)`) synced via update-tag ints `"monitor_counts"` / `"monitor_power"` — the `gauge_readings` idiom exactly (sync tags only, never disk; `TabletBlockEntity.java:920-941` is the template).

- [ ] **Step 1: Block registry in MonitorScanner** — static `Set<TabletBlockEntity> BLOCKS` (identity set); `registerBlock/unregisterBlock`. BE calls: register when `setCurrentProgram(...)` lands on `Program.MONITOR` (and in `onLoad` when already MONITOR), unregister on program change away, `setRemoved`, and surface-part demotion. `LevelTickEvent.Post` (server): every 4 ticks, for each registered controller BE in that level → derive channels from ITS signals/gauges/probe, `scanChannel` each with `worldPosition` as anchor, reduce to counts (members with `strength>0 && inRange`) + effective power, and when changed call a new `be.setMonitorSummary(int[] counts, int[] power)` that stores + `sendBlockUpdated` (the `onGaugeReading` shape).
- [ ] **Step 2: BE sync** — `getUpdateTag`: when summaries are non-empty, `tag.putIntArray("monitor_counts"/"monitor_power", ...)`; `loadAdditional`: read both into the transient fields (clearing first, like `gauge_readings` at line 922).
- [ ] **Step 3: renderMonitorFace** — signature and pass structure copied from `renderGaugesFace` (`TabletScreenRenderer.java:656`): params `(PoseStack, MultiBufferSource, List<Frequency> channels, int[] counts, int[] power, int rotation, ScreenTheme, boolean lit, int packedLight, int surfaceW, int surfaceH, int caseTint)`. Per channel row: power bar + count chip QUADS first, then the two freq item icons (item pass), then count + power text (text pass). Cap visible rows to what the glass height fits (integer row height, the list-mode row rhythm); no scrolling on faces.
- [ ] **Step 4: Dispatch** — in `TabletBlockEntityRenderer.renderFace`'s switch add before `default`:

```java
                case MONITOR -> TabletScreenRenderer.renderMonitorFace(poseStack, buffers,
                        com.modpack.linktablet.frequency.MonitorChannels.channelsOf(
                                be.getSignals(), be.getGauges(), be.getMonitorProbe()),
                        be.monitorCounts(), be.monitorPower(),
                        be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                        packedLight, surfaceW, surfaceH, caseTint);
```

  (Glass taps already open the GUI via the generic non-signals branch in `TabletBlock` — no hit-test work.)
- [ ] **Step 5: Build green. Step 6: Stage.**

### Task 8: Overlay pin content

**Files:**
- Create: `src/main/java/com/modpack/linktablet/client/screen/MonitorOverlayContent.java`
- Modify: `src/main/java/com/modpack/linktablet/client/screen/MiniTabletWindow.java:89` (contentFor switch)

**Interfaces:**
- Consumes: `OverlayContent` (`api/client/OverlayContent.java` — height/render/mouseClicked), `ClientMonitorSnapshot`, `MiniTabletWindow`'s `this::view` supplier (the `GaugesOverlayContent` constructor shape).

- [ ] **Step 1: Content class** — pattern `GaugesOverlayContent`: compact per-channel lines (freq icons at 8px, count, power bar), reading `ClientMonitorSnapshot.channels()`; skeleton rows from `MonitorChannels.channelsOf` when the snapshot is empty. Subscription: `acquire(view.target())` on construction is WRONG for overlays (they outlive screens and reconstruct on relog) — instead call `ClientMonitorSnapshot.acquire` lazily on the first `render` and `release` in `defocus()`; ALSO re-acquire in render if a relog cleared consumers (guard: track own acquired flag, re-acquire when flag set but store target is null).
- [ ] **Step 2: Wire** — `MiniTabletWindow.contentFor`: `case MONITOR -> new MonitorOverlayContent(this::view);`. The `@monitor` pin descriptor, restore, and right-click-opens-screen all come free from the generic program-key plumbing.
- [ ] **Step 3: Build green. Step 4: Stage.**

### Task 9: Docs + handoff

**Files:**
- Modify: `CHANGELOG.md` ("Unreleased")
- Modify: `docs/NEXT_SESSION.md` (status + test matrix)
- Modify: `CLAUDE.md` (gotchas, if any new invariant emerged — e.g. the MonitorChannels one-source rule and the anchor-raw-vs-display-localized split)

**Interfaces:** none — prose only.

- [ ] **Step 1: CHANGELOG** under "Unreleased": Frequency Monitor app (App Store), probe channel, member classification incl. inventory-tablet attribution, kiosk face summary, overlay pin; note the pairing break (registrar "19").
- [ ] **Step 2: NEXT_SESSION.md** — new status section: 1.11.0 in code, registrar "19", uncommitted-or-staged state, and the spec's test matrix verbatim (phantom-copy scenario, probe, range gating, dedicated-server `runServer` pass, merged/mounted face, overlay pin, old-world load).
- [ ] **Step 3: CLAUDE.md gotcha** — add a short "Frequency Monitor (1.11.0)" bullet: `MonitorChannels.channelsOf` is the ONE channel table (server scanner, BE summaries, face renderer, GUI — index-mapped wire forms depend on it); range math on RAW positions, displayed coords localized (Sable); `ownerName` classification lives on the two handlers.
- [ ] **Step 4: Full build** — `./gradlew build` green; report jar path. Then STOP: commits, `runClient`/`runServer` passes, version bump, and release are all user-gated.

---

## Self-review

**Spec coverage:** Program + store row (T1), channel derivation + dedupe by Create identity (T2), wire types + registrar "19" + client store + heartbeat (T3), probe persistence component/NBT/payload (T4), server scan + classification + range + Sable split + expiry (T5), GUI screen + probe slots + pin + ESC-Home (T6), kiosk face compact summary via update tag + face dispatch + free glass-tap (T7), overlay pin (T8), changelog/docs/test-matrix handoff (T9). Out-of-scope items (no mutation, no addon API) need no task.

**Placeholders:** none — every code step has real code or an exact pattern file + line to mirror. Two deliberate verify-before-trusting notes (LinkBehaviour's supertype; `withinRange` not reading the network key) are implementation checks, not gaps.

**Type consistency:** `MonitorChannels.channelsOf(List<Signal>, List<Gauge>, Frequency)` used identically in T5/T6/T7/T8; `MonitorMember` field order (type, label, pos, strength, listening, inRange) matches its codec; `acquire/release/channels` consistent across T3/T6/T8; `setMonitorSummary(int[], int[])` (T7 S1) matches the accessors `monitorCounts()/monitorPower()` used in T7 S4.
