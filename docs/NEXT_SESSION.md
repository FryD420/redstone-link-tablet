# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

## Status (2026-07-22 — v1.8.1 LIVE EVERYWHERE)

- **v1.8.1 is the current release** (tagged, pushed, uploaded to both
  platforms 2026-07-22; Discord announcement text drafted in-session,
  posting is the user's step): **placeable Swivel Mount stand** (empty
  stand block on any face, right-click with a tablet to mount it,
  auto-aimed at the clicker; mount item on a placed tablet still
  works) + **separate tablet/stand pickup** (sneak-grab or
  sneak-wrench the PANEL = tablet only, stand stays — swap tablets
  between consoles; the STAND region = both; sneak-wrench GLASS is
  still content rotate; breaking drops both). ADDS A BLOCK
  (`linktablet:swivel_mount`) — 1.8.1 does not pair with 1.8.0 or
  older, even though the payload registrar is still "13".
- 1.8.0 (2026-07-21, also live): Swivel Mount item + nameable tablets
  (anvil name → GUI/overlay title). UNADVERTISED and changelog-silent
  BY DESIGN: the 19-title secret arcade (local memory
  `secret-games-backlog` has the cheat sheet; trigger = Linked
  Controller icon + game name — NEVER spoil in public text).
- 1.7.0 highlights: multiblock screens (4×3), pinned overlay +
  keybinds, overlay whitelist, JEI/EMI ghost drag (EMI dev-runtime jar
  comes from the Modrinth maven — terraformers truncates big jars on
  this connection), solo screens.
- The FAMILYPACK now runs a dedicated server: server-side mods are
  linktablet + bettercombat (+ Create deps). Punchy is CLIENT-ONLY
  (crashes a dedicated server loading a client Screen class) — EMF,
  ETF, Hold My Items, BetterThirdPerson client-only too.
- Branches: `tablet-overlay` and `main` both pushed and level; tag
  v1.8.1 on the release commit.

## Next session

1. **Redstone follow mode (queued 2026-07-22, user-requested)** — a
   POWERED mounted tablet tracks the nearest player like the
   enchanting-table book. Design agreed: SERVER-driven (the mount
   pitch/yaw feed renderer AND hit-test — a client-only cosmetic
   follow would break tap accuracy), via `neighborChanged` power
   sensing (BE flag, re-derived on load — no new blockstate) + a
   server tick calling `aimAt(nearest non-spectator ≤~8 blocks)` at
   ~3-4 tick cadence with a small angle threshold, + client lerp in
   the BER so it glides. Power off = stays put. No new registries or
   payloads → ships as 1.8.2, pairs with 1.8.1. ~Half session incl.
   tuning range/cadence; needs a multiplayer feel-test.
2. **Sable / Create Aeronautics compat (investigate on demand)** —
   tablets on Sable sublevels (Aeronautics ships): prediction from
   2026-07-21 analysis — rendering/transmit/flat-taps likely fine
   (real interactive blocks, remapped raycasts), but ALL eye-ray math
   mixes main-world eye coords with sublevel block coords and will
   misbehave on a moved ship: mounted taps/aiming/wrench regions and
   BlockSliderDrag. Fix if testers confirm: a Sable compat shim that
   transforms the eye into sublevel space at the three eye-ray call
   sites (optional-dep pattern like JEI/EMI). Wait for a real tester
   report from an Aeronautics pack before building.
2. **Screenshot shoot follow-up** — the user + wife will shoot, timing
   theirs: (a) a merged tablet wall (3×2 or 4×3, dyed bezel, mid-tap —
   strong candidate to replace hero4 as hero/social preview; bonus: a
   swivel-mounted tablet angled in the foreground), (b) the pinned
   overlay during real gameplay (hotbar visible). Checklist + exact
   slot placement live at the bottom of `docs/DESCRIPTION.md` (two
   `<!-- 📸 -->` comments). The description TEXT is already current
   through 1.8.1 ("Aim it anywhere" section, nameable bullet, stand
   swapping; games stay unmentioned) and can be pasted to both
   listings before the shoot — the slots render as nothing.
3. **Reactive hotfixes** — the tester crew is on 1.7.x/1.8.x now. The
   pre-release test-debt sweep (multiplayer dev-server, floor/ceiling
   orientation, ponder + held-item regression, chunk-border surfaces)
   was DROPPED by user decision 2026-07-21 ("let the testers find
   bugs") — do NOT re-propose it; those areas are simply where to look
   first if a report comes in.

## Parked (don't propose unless the user re-raises)

- **Factory gauges / data apps** — parked 2026-07-22 ("just a
  thought"). Scoped in-session, three tiers: (1) Gauge app type that
  LISTENS on a link frequency and shows received 0–15 as a dial —
  flips the existing transmitter compat, works held + overlay,
  ~1-2 sessions, needs a lightweight value-sync payload → registrar
  bump; (2) tablet BE as a Create Display Link TARGET — all of
  Create's display sources for free on placed/merged screens,
  ~1 session basic; (3) bespoke deep readouts (stress graphs, vault
  browsing) — REJECTED, competes with Create's display system.
  Client-only utility apps (clock, calculator) cost what a game costs.
- **Icon-friendly defaults** — parked 2026-07-21 (was on hold for
  tester intel that never firmed up). Analysis in
  `docs/ICON_DEFAULTS_SCOPING.md` if it comes back.
- First-person interactive GUI on the held tablet.
- Distant Horizons LOD nit (WALL blockstate `landscape` fallback —
  harmless, LOD-only).
- 1.20.1 backport — possible but a big lift (Forge loader, no
  DataComponents/StreamCodec, second codebase); wait for real demand.
- Declined tester items (vetted 2026-07-17, batch arrived via a
  disavowed shared-keyboard message): the "blank white tablet in
  ponder" report (if ever reproduced by a second person: ponder's
  virtual level may skip block color handlers — check ponder-renderer
  tinting first) and a PurpleFox easter egg (the suspect requested it
  themselves).

## Release history (compressed)

- 1.8.0 (2026-07-21) / 1.8.1 (2026-07-22): Swivel Mount item, then
  the placeable stand + separate pickup; nameable tablets; the silent
  19-game arcade. Registrar "13" throughout, but 1.8.1's new block
  registration means only 1.8.1 pairs with 1.8.1.
- 1.7.0 (2026-07-21): multiblock screens, pinned overlay + keybinds,
  overlay whitelist, JEI/EMI drag, solo screens. Registrar "11"→"13"
  ("12" = AppTarget slot mode, "13" = SurfaceLinkPayload).
- 1.6.0 (2026-07-19): per-app note windows (tester request), Timer app
  type (migdzy, credited). Registrar "9"→"11". Modrinth first approval
  cleared this day — all platforms live since.
- 1.5.x (2026-07-16/17): Create-style chrome overhaul (GUI + placed
  screens), Parchment + Avionics themes, slider ranges, one-item
  frequencies, grid chip tiles, creative Redstone-tab listing,
  restyled icon. Registrar "7"→"9".
- 1.4.0: ItemStack frequencies (frequency-card compat), container-menu
  editor, wrench rotation + landscape mounting, dye wash, slider apps
  with click-and-slide. Registrar "5"→"7".
- 1.3.x: dynamic screen tiles, list mode, quick-add, six themes
  ("PurpleFox" honors a tester), ponder scene, momentary buttons.
  Registrar "4"→"5".
- Earlier public: v1.3.2, v1.2.1, v1.2.0, v1.1.1 (first upload).
- Dev tooling in `tools/` (jar-excluded): `./gradlew nbtTool
  --args="gen|dump <path>"` (ponder schematic), `./gradlew iconTool
  --args="docs/icon.png"` (listing icon), `./gradlew chromeTool` (GUI
  chrome atlas). Relative, space-free args — Gradle splits on spaces.
