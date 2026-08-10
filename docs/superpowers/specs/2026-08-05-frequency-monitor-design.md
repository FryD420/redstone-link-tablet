# Frequency Monitor — design spec (2026-08-05)

Target release: **1.11.0** (new payloads → registrar bump → pairing
break). Motivation: the 1.10.2 session's roadmap seed — three "gauge
pinned at 15" reports in one day were a creative-mode phantom tablet
copy transmitting from an inventory, and nobody could see why. A
"who's transmitting on this frequency" view turns that class of report
into self-service.

## Summary

A new launcher program, **Frequency Monitor** (`Program.MONITOR`,
id 26, key `"monitor"`, `game=false`), distributed through the App
Store like Clock/Calculator/Gauges (never on the default roster). It
lists every Redstone Link channel the tablet uses and, for each, the
live members of Create's network — classified, with strength and
range — plus a probe row for inspecting any arbitrary channel.

## What it shows

**Channel list** = union of the tablet's signal frequencies and gauge
frequencies, deduped by Create network identity (the
`RedstoneLinkNetworkHandler.Frequency` couple — item + dyed color,
matching Create exactly), plus the probe channel when set.

Per channel:

- The two frequency items (existing ghost-slot rendering).
- Summary line: transmitter count + effective power (max transmitted
  strength among in-range members).
- Member rows, each classified **server-side**:
  - **Create link block** — block display name at `getLocation()` +
    coords.
  - **Placed tablet** — our BE position (match against loaded
    `TabletBlockEntity` receivers/transmitters), coords.
  - **Tablet in a player's inventory** — player name (via
    `TabletTransmitterHandler` / `TabletReceiverHandler` ownership).
    This is the phantom-copy diagnosis: a creative copy shows as
    "Tablet (PlayerName) — 15" next to the placed tablet's row.
  - **Other** — any modded `IRedstoneLinkable`; fallback label is the
    class simple name.
  - Per member: transmit strength (0–15), a listening-vs-transmitting
    badge, and in-range/out-of-range vs. the viewing tablet's
    position, computed with Create's own
    `RedstoneLinkNetworkHandler.withinRange`.
- Members with `isAlive() == false` are filtered out.

**Probe row** — pinned at the top of the screen: two ghost item slots
(existing `PickerOverlay` flow, same as the gauge editor). Setting
both items adds that channel to the snapshot without the tablet
transmitting or listening on it. Probe items are the feature's ONLY
persisted data: a new `monitor_probe` component + BE NBT tag,
item↔block round-trip, `optionalFieldOf` so old tablets serialize
unchanged.

## Data flow

Create's network handler is server-only, so everything is a
server-computed snapshot (the proven `GaugeReadingsPayload` pattern,
the mod's first playToClient from 1.10.0 gauges):

- Client opens the Monitor screen → sends a subscribe request payload
  (block target = kiosk GUI, held target = item view; mirrors the
  existing both-target payload shape).
- While ≥1 viewer is active, the server polls
  `RedstoneLinkNetworkHandler.networksIn(level)` every 4 ticks,
  builds a compact snapshot (channel index → member entries of
  {type byte, label string, BlockPos, strength byte, listening flag,
  in-range flag}), and sends `MonitorSnapshotPayload` to viewers.
  Change-guarded: identical snapshots are not re-sent.
- Unsubscribe on screen close + a server-side timeout backstop: each
  viewer carries a last-seen timestamp, the open screen re-pings
  every ~2s, and the server expires viewers after ~5s of silence
  (covers disconnects and crashed screens).
- Range anchor: the kiosk GUI uses the block's position, the held
  view uses the player's position — same split as gauges.
- **Sable/Create-Aeronautics**: member positions and the viewing
  tablet's position localize through `compat/SableCompat` before any
  distance math or coord display, same rule as 1.10.1. The in-range
  flag uses Create's own math on the raw positions (Create handles
  its network across the plot boundary natively — verified 1.10.2);
  only the DISPLAYED distance/coords localize.

## Surfaces

Full program treatment (user decision):

- **GUI screen** (`MonitorScreen`): probe row + scrollable channel
  list with expandable member detail. Scrolling reuses the 1.10.0
  launcher/store scroll pattern. Chrome via the existing `Chrome`
  painters; mechanisms (badges, power bars) stay procedural fills.
- **Kiosk face** (`renderMonitorFace`): the COMPACT form only — one
  line per channel: freq item icons, transmitter count, effective
  power bar. Full member lists are too heavy for update-tag sync.
  The controller BE computes its own snapshot server-side (its own
  position as the range anchor) and syncs summaries via update tag,
  exactly like `gauge_readings` — transient, never persisted. Face
  rendering obeys the three-pass rule (quads → items → text). Glass
  tap opens the GUI via the existing generic non-signals branch.
- **Overlay pin** (`MonitorOverlayContent`): same compact per-channel
  lines in the mini window, reading the client snapshot store.
  Pin descriptor rides the existing `@monitor` program-key suffix —
  no new pin plumbing.

## Components touched

- `Program` enum: `MONITOR(26, "monitor", 0xFF16A0A0,
  "minecraft:spyglass")` — teal chip, spyglass icon (both tunable at
  the first screenshot). Lang keys `program.linktablet.monitor` +
  `.desc`.
- `network/`: `MonitorSubscribePayload` (client→server),
  `MonitorSnapshotPayload` (server→client). Registrar "18" → "19".
- New `client/ClientMonitorSnapshot` store (mirrors
  `ClientGaugeReadings`).
- New server poller — follow wherever `TabletReceiverHandler` ticks;
  one poll serves all viewers in a level.
- `frequency/` or root: snapshot record types (hand-rolled stream
  codecs from day one — composite maxes at 6 fields).
- BE: `monitor_probe` NBT + summary update-tag ints;
  item component `MONITOR_PROBE`.
- No changes to `Signal`, `Gauge`, or any existing persisted format.

## Explicitly out of scope (v1)

- Browsing channels the tablet doesn't use (beyond the single probe).
- Historical graphs / logging.
- Any mutation (kicking members, overriding strength) — read-only.
- Addon API exposure of the snapshot (wait for a real request;
  api/ is append-only, so adding later is safe).

## Test matrix (dev pass)

- One channel with: a gauge, a real Create Redstone Link, and a
  creative phantom tablet copy in the inventory → three member rows,
  correctly classified; phantom row disappears when the item is
  dropped.
- Probe an unrelated channel the tablet doesn't use.
- Range gating: walk a transmitter out of range → out-of-range badge,
  effective power drops.
- Dedicated server pass (`runServer`) — payloads + classification.
- Kiosk face summary on flat, merged, and mounted tablets; face tap
  opens the GUI; pre-1.11 world's kiosks unaffected.
- Overlay pin shows live summaries; pin survives relog (`@monitor`
  descriptor).
- Old-world load: tablets without `monitor_probe` untouched;
  1.10.x ↔ 1.11.0 client/server mismatch cleanly refuses (registrar
  break, expected).
