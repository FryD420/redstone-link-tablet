# Dropped-tablet transmission — design

Date: 2026-08-15
Status: approved (design), pending implementation plan
Version target: 1.13.0 (new feature; SERVER-SIDE ONLY — no wire format,
component, NBT, or registrar change; registrar stays "24" and 1.13.0
PAIRS with 1.12.x. An old client shows an untranslated label key on the
new Monitor row types — cosmetic only.)

## Goal

A Link Tablet lying on the ground (a world `ItemEntity`) keeps
transmitting its toggled-ON signals from where it lies. Today it goes
silent the moment it leaves a player's inventory, because transmitters
are player-inventory-scan driven (`TabletTransmitterHandler.
onPlayerTick`). Same for tablets in item frames.

Gameplay upside: throwable scene tablets — toggle a scene on, throw the
tablet into the machine room, the scene keeps running from where it
lands.

## Semantics

- A dropped or framed tablet broadcasts its **toggled-ON, non-momentary,
  non-timer** signals — the exact filter the player inventory scan uses
  (`active() && !momentary() && !timed()`). Strength is the signal's own;
  strongest-wins per frequency within one entity; stacked transmitters
  across entities behave as Create's network always has (max wins).
- **Momentary and timer signals never fire from the ground** — there is
  no interaction path to press them.
- **In-flight pulses finish from the thrower** (user decision): the hold
  system is keyed to the PLAYER, not the stack, so a timer pulse or held
  momentary that is live when the tablet is dropped keeps transmitting
  from the player until its clock runs out, exactly as today. Zero
  changes to `TabletTransmitterHandler`'s hold machinery.
- **Item frames included** (user decision): a tablet in an `ItemFrame`
  or `GlowItemFrame` transmits the same way — a framed tablet is a tiny
  wall transmitter. Frames never despawn, so a forgotten framed tablet
  transmits forever; that is the same standing hazard as a placed
  tablet, accepted as-is.
- **Chunk unload = silent.** An entity in an unloaded chunk leaves the
  loaded-entity index and stops transmitting, matching placed tablets
  (whose BE removes its transmitters on unload). Reload resumes it.
- Range is anchored at the entity's position and follows it (water
  drift, fans, tossing) — RAW positions into Create's network math, per
  the standing Sable rule.

## Architecture — `compat/DroppedTabletHandler`

One new server-side class, shaped like the existing
`TabletTransmitterHandler` sweep (wanted-set diff), on
`MonitorScanner`'s 4-tick cadence:

- Every 4 ticks per `ServerLevel` (level post-tick event), iterate ONLY
  the relevant entity types via the type-indexed entity getter
  (`ServerLevel.getEntities(EntityTypeTest, ...)` for
  `EntityType.ITEM`, `EntityType.ITEM_FRAME`,
  `EntityType.GLOW_ITEM_FRAME`) — never the full entity list.
- For each entity whose stack is a `TabletItem`, build the wanted map
  `Frequency → strength` from its signals (filter above).
- Diff against the live registry
  `Map<entityUUID, Map<Frequency, VirtualTransmitter>>`:
  remove transmitters for entities gone from the sweep (picked up,
  despawned, burned, hoppered, frame emptied or broken, chunk
  unloaded), add new ones, and `VirtualTransmitter.update(...)` the
  rest — `update` already no-ops unless the block position or strength
  actually changed, so drifting items cost nothing extra.
- The 4-tick cadence means at most ~200 ms between drop and first
  broadcast, imperceptible; signal state cannot change while the item
  is an entity (no GUI path), so nothing faster is needed.
- Safety net: clear all transmitters for a level on level unload and on
  server stop, mirroring the logout handler.

Why a sweep and not event registration: `EntityJoinLevelEvent` misses a
tablet placed INTO an already-placed frame (no join fires), so frames
would need a sweep anyway. One mechanism, and every cleanup case
(pickup, despawn, destruction, unload) is handled by absence from the
sweep rather than by enumerated events. A mixin ticker is rejected —
this mod is mixin-free.

## Monitor classification

- Two type bytes appended: `MEMBER_DROPPED_TABLET = 4`,
  `MEMBER_FRAMED_TABLET = 5` in `ModNetworking`. The type byte has ZERO
  client consumers (verified — the client renders the server-built
  label Component), so this is a pure value append: no wire change.
- `DroppedTabletHandler` exposes an ownership hook in the style of
  `TabletTransmitterHandler.ownerName(...)`, returning which entity a
  network member belongs to. `MonitorScanner.scanChannel`'s
  classification chain checks it BEFORE the generic
  `VirtualTransmitter` branch (which would otherwise misclassify these
  as placed tablets).
- Labels, built server-side:
  - `gui.linktablet.monitor.member.dropped` — "Dropped tablet (%s)"
    with the thrower's name via `ItemEntity.getOwner()` when known;
    fall back to a no-arg variant "Dropped tablet" when not.
  - `gui.linktablet.monitor.member.framed` — "Framed tablet".
- **The phantom-copy story improves**: the 1.11.0 diagnostic was
  "phantom row disappears when the item is dropped." Now the row
  CHANGES to "Dropped tablet (name)" instead — strictly more
  informative. The 1.11.0 test-matrix row is superseded accordingly.
- Displayed coordinates go through the existing `displayPos`
  Sable-localization automatically (same `MonitorMember` path).

## What this does NOT touch

- No wire payloads, no components, no NBT, no registrar bump.
- No client rendering (the ground item keeps its normal item model —
  no live screen on a dropped tablet).
- No hold-system changes; no `Signal` changes; no receiver-side
  changes (a dropped tablet transmits only — gauges on a dropped
  tablet stay dormant, same as in an inventory... note: gauges on an
  INVENTORY tablet are receiver-driven via `TabletReceiverHandler`;
  whether dropped tablets should also RECEIVE (for gauge parity) is
  explicitly out of scope v1 — transmit only, matching the queue item).

## Test matrix

- Toggle a signal ON, drop the tablet → receiver stays powered; walk
  away with the receiver near the ITEM → stays powered (range anchors
  at the item, not the thrower).
- Pick the tablet up → transmission transfers back to the inventory
  scan seamlessly (no flicker beyond a sweep tick).
- Despawn (5 min), lava, cactus, hopper pickup → transmission stops.
- Tablet into an item frame → transmits; take it out / break the frame
  → stops. Glow frame same.
- Toggled-ON tablet thrown THROUGH water/fans → receiver follows the
  drifting position.
- Timer signal started, tablet dropped mid-pulse → pulse finishes from
  the player, dropped tablet does not restart it.
- Monitor: drop a transmitting tablet → its row changes from
  "tablet (name)" to "Dropped tablet (name)"; framed tablet shows
  "Framed tablet"; coords and in-range badge correct; Sable
  vehicle-mounted probe still displays sane coords.
- Creative phantom-copy repro (the 3-reports-in-one-day scenario): the
  Monitor now names the dropped copy explicitly.
- Chunk unload: drop a transmitting tablet, leave render+sim distance →
  receiver near it powers down; return → powers up.
- Dedicated server pass (`runServer`) — the handler is server-side;
  boot-check per the 1.10.2 lesson.
- Regression: player-held and placed-tablet transmission unchanged;
  1.12.x client joins a 1.13.0 server (pairing intact; dropped-tablet
  Monitor rows show a raw lang key — accepted cosmetic).
