# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

## Status (2026-07-21, end of day — v1.7.0 LIVE EVERYWHERE)

- **v1.7.0 is the current release**, shipped end-to-end in one day:
  modpack-tested by the user ("much better"), committed (6caabb0),
  merged to main (e9ead01), tagged, pushed, uploaded by the user, and
  the rewritten listing description is PASTED AND LIVE on both
  platforms. Registrar "13" — does not pair with 1.6.0 or older.
- 1.7.0 contents: **multiblock screens** (coplanar tablets merge up to
  4×3 into one continuous display; wrench rotation; +32 apps per
  member), **pinned tablet overlay** (mini-tablet HUD window, B =
  chat-style interact, "Open Tablet" keybind unbound by default),
  **overlay whitelist** (notes + mini-tablet hidden on options/settings/
  pause screens — `NoteWindows.overlaysAllowedOn` gates all seven
  screen-scoped handlers), **JEI + EMI ghost drag** into the edit
  screen's frequency slots (optional deps, viewer-discovered plugins;
  JEI 19.39.0.369 / EMI 1.1.24 pinned in gradle.properties — NOTE: the
  terraformers maven truncates big jars on this connection, so the EMI
  dev-runtime jar comes from the Modrinth maven), and **solo screens**
  (chain-link header button on placed tablets: unlink dissolves a
  merged surface and marks every member solo; `solo_screen` BE flag,
  `SurfaceLinkPayload`, scanner flood skips solo BEs).
- Branches: `tablet-overlay` and `main` both pushed; tablet-overlay has
  a few post-release doc commits main doesn't (merge on next touch or
  keep working on tablet-overlay — either is fine).

## Next session

1. **Screenshot shoot follow-up (the ONLY open work item)** — the user
   + wife will shoot, timing theirs: (a) a merged tablet wall (3×2 or
   4×3, dyed bezel, mid-tap — strong candidate to replace hero4 as
   hero/social preview), (b) the pinned overlay during real gameplay
   (hotbar visible). Checklist + exact slot placement live at the
   bottom of `docs/DESCRIPTION.md` (two `<!-- 📸 -->` comments). When
   shot: drop in docs/images/, swap the comments for embeds, push,
   user re-pastes both listings.
2. **Reactive hotfixes** — the tester crew is on 1.7.0 now. The
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
