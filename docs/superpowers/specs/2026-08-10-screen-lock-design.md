# Screen lock (design)

Date: 2026-08-10 · Status: approved (user, same day)
Re-scoped 2026-08-11 (user): the one-big-button half is CUT from v1 —
lock only. Locked single-signal tablets render the normal grid; the
full-glass lone-tile idea stays a possible follow-up, not committed.
Origin: tester request via the official Discord (Fluid Valve — giant
locked button, wrench-to-unlock, no rotation while locked), plus the
user's wall-painting lock from the Paint v2 brainstorm (2026-08-10,
deliberately batched here). CREDIT Fluid Valve in the changelog
(house precedent: Timer/migdzy, Paint/Tommy).

## Decisions (locked with the user)

- **Use-only lock**: locked glass keeps working (taps, momentary,
  sliders, link propagation, live faces); configuration is blocked.
- **Anyone with a wrench unlocks** — the wrench is the key, no
  ownership system. Any wrench click on a locked tablet opens the
  GUI (Fluid Valve's exact flow).
- ~~Big button is automatic~~ — CUT 2026-08-11 (user re-scope): no
  full-glass rendering; locked tablets keep the normal grid at every
  signal count.
- Sneak-wrench **pickup is blocked while locked** (unlock first;
  mining the block still drops it normally).
- Ships in the NEXT pairing-break release (registrar "23"→"24") —
  batch other wire work with it.

## State + wire

- `locked` boolean on the BE — **block-only** (never an item
  component; the mount-angles/solo precedent), controller-owned on
  merged surfaces (members inherit via `resolveController`), synced
  free via the update tag, BE NBT `locked` (absent = false, never
  written false).
- New `SetLockPayload(target, locked)` — registrar **"23"→"24",
  PAIRING BREAK**. Server handler validates: sender in range, target
  resolves, and — for UNLOCK only — **a wrench in either hand**.
  ~~Symmetric~~ RE-DECIDED 2026-08-11 (first dev pass): locking is
  FREE — an open GUI already carries full config trust, and a
  main-hand wrench can't reach the GUI on an unlocked tablet at all
  (wrench-click rotates), so the symmetric rule made locking
  undiscoverable. Fluid Valve's literal ask was "wrench-to-unlock".

## The one server rule (enforcement)

On a LOCKED target, the server accepts:

- **Use payloads** — toggle, momentary press/release, slider set —
  always (plus internal link propagation, which is server-side
  anyway).
- **Everything else** (signal upsert/remove/reorder, theme, home
  apps, program nav, rotation, paint strokes + clear, twitch
  channel, probes, gauges, solo-link, lock itself) — **only from a
  sender currently holding a wrench** (either hand).

No read-only GUI variants anywhere: a player who wrench-opened the
GUI is holding the permission; a wrenchless player can neither reach
the GUI normally nor spoof edits past the server. Mural protection
falls out free (paint payloads are config). Implementation shape:
one `configAllowed(player, targetBE)` helper in ModNetworking,
called by every config-payload handler's block-target branch — one
choke point, never per-handler forks. Edge accepted: stowing the
wrench mid-edit makes further edits bounce (deny tick) until it's
back in hand.

## World-side interaction gates

Locked placed tablet:

- Bezel tap: no GUI — soft deny tick, so a locked wall communicates
  "locked" rather than "broken".
- Glass tap: existing pip/tile pipeline unchanged (use-only).
- Wrench, ANY face/region, flat or mounted: **opens the GUI**
  client-side — replaces the whole wrench map (rotate, landscape
  flip, mounted re-aim, bezel flip) while locked.
- Sneak-wrench (pickup paths, incl. mount pickup): blocked with a
  deny cue. Mining drops the tablet as today (lock does NOT persist
  on the item — a re-placed tablet starts unlocked).
- Follow mode: a powered mount keeps following (it's display, not
  config) — but wrench re-aim needs unlock like everything else.

## UI

- Padlock glyph joins the block-GUI header row (`HeaderGlyphs`
  precedent, beside theme/pin/chain-link) — block views only, hidden
  on held/slot views. Toggles `SetLockPayload`; the server's wrench
  check applies (the glyph shows a deny tick if it bounces).
- Locked faces draw a small padlock pip near the bezel (both flat
  and mounted renderers) so a locked wall reads as locked, not
  broken. Text/icon passes only — respect the fills→icons→text
  batching rule.
- Sounds: lock/unlock get distinct ticks (existing UISounds family).

## Explicitly out of scope

- The one-big-button (full-glass lone-signal tile while locked) —
  cut from v1 by user re-scope 2026-08-11; possible follow-up if
  testers ask again.
- Ownership/locker-only unlock (rejected: new player-UUID concept).
- Per-app lock behavior (rejected: one semantics everywhere).
- Locking HELD tablets (block-only feature).
- Any change to creative/survival break behavior.

## Test matrix (dev pass)

- Lock via padlock (wrench in hand) → bezel tap dead, taps/sliders
  still work, GUI unreachable without wrench; wrench click opens
  GUI; unlock restores everything.
- Padlock without a wrench in hand → deny tick, still unlocked
  (server check, not just UI).
- Wrench map while locked: content rotate, landscape flip, mounted
  re-aim, sneak-wrench pickup — all blocked; same actions work
  again after unlock.
- Single-signal tablet locked → renders the NORMAL tile (big button
  cut), and taps/sliders on it still work.
- Merged wall: lock controller → whole surface locked incl. member
  bezels; split while
  locked → EVERY promoted fragment controller INHERITS the lock
  (decided: splitting a locked mural must not unlock its pieces;
  mirrors how solo-marking propagates on unlink).
- Mural protection: locked painted wall rejects strokes/clear from
  a wrenchless second account (deny), accepts from a wrench holder.
- Config payloads spoof-check: wrenchless client sending upsert/
  theme/program payloads at a locked target → server rejects.
- Follow mode keeps tracking while locked; re-aim blocked.
- Break a locked tablet by mining → item places back UNLOCKED with
  signals/paint intact.
- Old-world load: absent NBT = unlocked; registrar "24" pairing
  break vs 1.11.x (expected refusal).
