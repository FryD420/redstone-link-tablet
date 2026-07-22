# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

## Status (2026-07-21, end of day — v1.8.0 LIVE EVERYWHERE)

- **v1.8.0 is the current release** (three releases shipped this day:
  1.7.0 → 1.7.1 → 1.8.0, all live on both platforms + announced on
  Discord). 1.8.0 public contents: **Swivel Mount** (ball-joint stand
  for placed tablets; wrench glass = aim-at-eyes, bezel = landscape
  flip on ALL faces, sneak-wrench glass = content rotate, elsewhere =
  pickup; mounted never merges; coarse hitbox outline hidden) and
  **nameable tablets** (anvil name shows as GUI/overlay title,
  survives place/pickup). UNADVERTISED and changelog-silent BY DESIGN:
  the 19-title secret arcade (local memory `secret-games-backlog` has
  the cheat sheet; trigger = Linked Controller icon + game name —
  NEVER spoil in public text). Registrar "13" across all of 1.7.x and
  1.8.0 — they all pair; nothing pairs with 1.6.0 or older.
- 1.7.0 (same day) brought: multiblock screens (4×3 continuous
  display), pinned overlay + keybinds, overlay whitelist, JEI/EMI
  ghost drag (EMI dev-runtime jar comes from the Modrinth maven — the
  terraformers maven truncates big jars on this connection), and solo
  screens (chain-link header button). 1.7.1 = "a minor screen issue".
- Branches: `tablet-overlay` and `main` both pushed and level.

## Next session

1. **Sable / Create Aeronautics compat (investigate on demand)** —
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
   `<!-- 📸 -->` comments). NOTE: the description hasn't been updated
   for 1.8.0 yet — when doing the image swap, also add a short Swivel
   Mount + nameable-tablets section (games stay unmentioned).
3. **Reactive hotfixes** — the tester crew is on 1.7.x now. The
   pre-release test-debt sweep (multiplayer dev-server, floor/ceiling
   orientation, ponder + held-item regression, chunk-border surfaces)
   was DROPPED by user decision 2026-07-21 ("let the testers find
   bugs") — do NOT re-propose it; those areas are simply where to look
   first if a report comes in.

## Parked (don't propose unless the user re-raises)

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
