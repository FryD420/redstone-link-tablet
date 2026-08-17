# Dropped-Tablet Transmission Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Link Tablet lying on the ground or sitting in an item frame keeps transmitting its toggled-ON signals from where it is.

**Architecture:** One new server-side class, `compat/DroppedTabletHandler` — a 4-tick sweep over the type-indexed entity lists (`ITEM`, `ItemFrame.class` covering both frame types), building a wanted `Frequency → strength` map per entity and diffing it against live `VirtualTransmitter`s, exactly the shape `TabletTransmitterHandler.onPlayerTick` uses. Monitor classification adds two member-type bytes and a lookup hook slotted into `MonitorScanner`'s chain.

**Tech Stack:** NeoForge 1.21.1 (21.1.233), Create 6.0.10, Java 21. No new dependencies.

## Global Constraints

- **SERVER-SIDE ONLY.** No wire format, component, NBT, or registrar change; registrar stays "24"; 1.13.0 PAIRS with 1.12.x.
- Dropped/framed tablets broadcast **toggled-ON, non-momentary, non-timer** signals only: the filter is `signal.active() && !signal.momentary() && !signal.timed()`, identical to `TabletTransmitterHandler.onPlayerTick`.
- **Zero changes to the hold machinery** in `TabletTransmitterHandler` — in-flight pulses finish from the thrower by design.
- RAW positions into Create's network math (the standing Sable rule); displayed coords localize via the existing `MonitorScanner.displayPos` path automatically.
- **No unit test harness exists** (`src/test` does not exist); do not scaffold one. Verification per task is `./gradlew build` green; behaviour verification is the owner's dev pass plus the `runServer` boot gate in Task 3.
- If `java` isn't found in a fresh shell: `$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')`

---

### Task 1: `DroppedTabletHandler` — the sweep

**Files:**
- Create: `src/main/java/com/modpack/linktablet/compat/DroppedTabletHandler.java`

**Interfaces:**
- Consumes: `VirtualTransmitter` (existing: ctor `(Frequency, ServerLevel, BlockPos, int)`, `update(ServerLevel, BlockPos, int)`, `removeFromNetwork()`, `getFrequency()`); `ModDataComponents.TABLET_SIGNALS`; `Signal` accessors; `Frequency.isEmpty()`.
- Produces: `DroppedTabletHandler.classify(IRedstoneLinkable member)` returning `@Nullable DroppedTabletHandler.Origin`, where `Origin` is `record Origin(boolean framed, @Nullable String throwerName)`. Task 2 depends on this exact signature.

- [ ] **Step 1: Read the two reference classes**

Read `compat/TabletTransmitterHandler.java` (the wanted-set diff shape, `onPlayerTick`) and `compat/MonitorScanner.java:130-175` (the `LevelTickEvent.Post` gating idiom and 4-tick cadence). The new class mirrors both.

- [ ] **Step 2: Create the handler**

```java
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
 * A 4-tick sweep per level walks ONLY the type-indexed ITEM and
 * ItemFrame entity lists (never the full entity list) and diffs a
 * wanted map against live {@link VirtualTransmitter}s — the
 * {@link TabletTransmitterHandler#onPlayerTick} shape. Every cleanup
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

        // 1. Sweep the two type-indexed lists for tablet-bearing entities.
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
```

Before committing, verify against the ACTUAL codebase (imports, exact accessor names on `Signal`/`Frequency`, the `LevelTickEvent.Post` gating idiom in `MonitorScanner`) — the snippet above is the design; the repo is the authority on names. If `Signal`'s accessors differ (e.g. `isActive()` vs `active()`), follow the repo and say so in your report.

One subtlety to preserve: an entity whose tablet has NO wanted frequencies is never tracked (and a tracked one whose last transmitter is removed is dropped from the map), so the registry only ever holds entities that are actually transmitting — `classify` and the sweep both stay small.

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/modpack/linktablet/compat/DroppedTabletHandler.java
git commit -m "Dropped tablets T1: DroppedTabletHandler sweep (items + frames transmit)"
```

---

### Task 2: Monitor classification

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/network/ModNetworking.java` (the `MEMBER_*` constants, ~line 583-586)
- Modify: `src/main/java/com/modpack/linktablet/compat/MonitorScanner.java` (`scanChannel`, ~line 364-384)
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

**Interfaces:**
- Consumes: `DroppedTabletHandler.classify(IRedstoneLinkable)` → `@Nullable Origin(boolean framed, @Nullable String throwerName)` from Task 1.

- [ ] **Step 1: Append the member type constants**

In `ModNetworking`, after `MEMBER_OTHER = 3`:

```java
public static final byte MEMBER_DROPPED_TABLET = 4;
public static final byte MEMBER_FRAMED_TABLET = 5;
```

These are value appends on an existing byte field — the type byte has zero client consumers (verified during design; the client renders the server-built label), so no wire or registrar change.

- [ ] **Step 2: Slot the classification branch into `MonitorScanner.scanChannel`**

The chain currently reads: player-owner → placed tablet (`instanceof VirtualTransmitter || VirtualReceiver || TabletBlockEntity at pos`) → link block → other. The dropped check MUST come **before** the placed-tablet branch — dropped transmitters ARE `VirtualTransmitter`s and would otherwise misclassify as "Placed tablet". After the `owner != null` branch, insert:

```java
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
```

(Adjust to the file's exact local variable names; do not restructure the chain otherwise.)

- [ ] **Step 3: Lang keys**

```json
"gui.linktablet.monitor.member.dropped": "Dropped tablet (%s)",
"gui.linktablet.monitor.member.dropped.anon": "Dropped tablet",
"gui.linktablet.monitor.member.framed": "Framed tablet",
```

Place them beside the existing `gui.linktablet.monitor.member.*` entries.

- [ ] **Step 4: Build and commit**

Run: `./gradlew build` (expect BUILD SUCCESSFUL)

```bash
git add src/main/java/com/modpack/linktablet/network/ModNetworking.java src/main/java/com/modpack/linktablet/compat/MonitorScanner.java src/main/resources/assets/linktablet/lang/en_us.json
git commit -m "Dropped tablets T2: Monitor classifies dropped/framed tablet members"
```

---

### Task 3: Docs, version, boot gates

**Files:**
- Modify: `CHANGELOG.md`, `gradle.properties`, `docs/NEXT_SESSION.md`, `CLAUDE.md`

- [ ] **Step 1: Version — 1.13.0, folding in the unreleased 1.12.1**

`gradle.properties`: `mod_version=1.13.0`. The tooltip cycle's 1.12.1 was never released or uploaded, so its changelog content merges into the single 1.13.0 section (tooltips + dropped-tablet transmission ship together — owner decision "we can test it all together"). Restructure the changelog accordingly: one 1.13.0-bound section (still under Unreleased until release day), no orphan 1.12.1 heading. Both halves are client-or-server-side only, so the section notes 1.13.0 pairs with 1.12.x.

- [ ] **Step 2: Changelog entry (added alongside the tooltip lines)**

```markdown
- Dropped tablets keep transmitting: a tablet lying on the ground (or
  sitting in an item frame) now broadcasts its toggled-ON signals from
  where it is — throw a scene tablet into the machine room and the
  scene keeps running. The Frequency Monitor names these members
  ("Dropped tablet (player)" / "Framed tablet"), completing the
  who-is-transmitting story.
```

- [ ] **Step 3: CLAUDE.md gotcha**

Add a bullet to the technical-gotchas section, matching the house style: `DroppedTabletHandler` is the THIRD transmitter anchor (player scan / placed BE / entity sweep); absence-from-sweep is the only cleanup mechanism (never add entity-removal events); the classification order in `MonitorScanner` (dropped BEFORE placed — dropped transmitters are `VirtualTransmitter`s); momentary/timer pulses finish from the thrower by design.

- [ ] **Step 4: Roadmap**

`docs/NEXT_SESSION.md`: move the feature-queue item into the current START HERE entry as shipped-in-code, recording: the owner's dev pass is OUTSTANDING for BOTH halves (tooltips + dropped tablets, tested together), and the `runServer` boot gate below.

- [ ] **Step 5: Build + dedicated-server boot gate**

Run: `./gradlew build` — expect BUILD SUCCESSFUL, jar `build/libs/linktablet-1.13.0.jar`.
Run: `./gradlew runServer` — expect a clean dedicated-server boot to "Done" with no dist-cleaner or classloading errors (the handler is server-side; this is the 1.10.2 lesson). Stop the server after the Done line.

- [ ] **Step 6: Commit**

```bash
git add CHANGELOG.md gradle.properties docs/NEXT_SESSION.md CLAUDE.md
git commit -m "Dropped tablets T3: 1.13.0 (folds unreleased 1.12.1), changelog, gotchas, roadmap"
```

---

## Notes for the implementer

- The sweep must never iterate `level.getAllEntities()` — the two type-indexed `getEntities` calls are the entire entity surface.
- Do not add `EntityJoinLevelEvent`, capabilities, or mixins — absence-from-sweep IS the cleanup design.
- Do not touch `TabletTransmitterHandler` at all.
