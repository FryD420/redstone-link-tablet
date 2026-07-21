# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

## Status (2026-07-21 — 1.7.0 TAGGED + PUSHED, uploads with the user)
- Modpack-tested by the user ("much better"), committed (6caabb0),
  merged to main (e9ead01), tagged v1.7.0, pushed. UPLOADED BY THE
  USER SAME DAY — LIVE ON ALL PLATFORMS. Listing description rewritten
  for 1.7.0 (0af9f08, text-only; 📸 slots await the shoot). Test-debt
  sweep (multiplayer, floor/ceiling, ponder regression, chunk-border)
  DROPPED by user decision 2026-07-21: let the testers find bugs,
  hotfix reactively — do not re-add this item.
- Release gates cleared: TEMP "linktablet-surface" debug logging
  STRIPPED (scanner + BE), CHANGELOG dated 1.7.0 — 2026-07-21.
- Three additions this session (all uncommitted on `tablet-overlay`,
  jar handed to the user for modpack testing):
  1. **Overlay whitelist**: notes + mini-tablet only render/capture
     input over containers, chat, our own screens, and JEI/EMI
     (`NoteWindows.overlaysAllowedOn` gates all seven screen-scoped
     handlers; suppression defocuses so no stale drags). Options menus
     (vanilla/Sodium/etc.), pause menu, everything else: hidden.
  2. **JEI + EMI ghost drag** into the edit screen's frequency slots:
     `compat/jei/JeiCompat` + `compat/emi/EmiCompat`, discovered by the
     viewers' own plugin scans (no ModList guards). Optional deps in
     mods.toml; JEI 19.39.0.369 / EMI 1.1.24 pinned in
     gradle.properties. NOTE: terraformers maven truncates big jars on
     this connection — EMI dev-runtime jar comes from the Modrinth
     maven instead.
  3. **Solo screens** (user request: side-by-side tablets WITHOUT
     merging): chain-link header button on placed tablets. Unlink on a
     merged surface dissolves it and marks EVERY member solo; unlink on
     a standalone pre-flags it; re-link rejoins. BE-only flag
     (`solo_screen`, never on the item), scanner flood skips solo BEs,
     `TabletSurfaceScanner.setLinked` is the server entry,
     `SurfaceLinkPayload` added → registrar "12"→"13".
- Dev boot checks clean both builds (JEI+EMI loaded, plugins register).
- Remaining before release: user modpack + tester verdict, multiplayer
  dev-server test, floor/ceiling orientation pass, ponder + held-item
  regression, chunk-border surface; then commit, merge to main, tag
  v1.7.0, push, upload.

## Status (2026-07-19, LATE night — 1.7.0 bundle on `tablet-overlay`)
- **1.7.0 is feature-complete but UNRELEASED** (user call: hold the
  send). Two features, both dev-tested live across long sessions:
  1. **Pinned tablet overlay** (commit 511f862): mini-tablet window +
     first keybinds (B = chat-style interact, Open Tablet unbound);
     AppTarget slot mode, registrar "11"→"12". Tested "works great".
  2. **Multiblock screens** (821ce45 + tonight's uncommitted-at-time
     fix wave): merge coplanar tablets up to 4×3 into ONE continuous
     display. Tonight's debug arc (all fixed): floor-facing mismatch →
     placement auto-adopt; per-block panels → continuous raised panel
     covering full block faces + case-tint bezel band + skirt; bulk
     role-sync dropped by vanilla's batched BE path → explicit
     per-player packets; stale roles → onLoad self-heal (parts AND
     controllers); merged rotation (square=90° steps, oblong=180°) via
     wrench ANYWHERE on the face (bezel = hidden trap, removed);
     big-icon depth clipping → size-proportional lift; member
     selection outline suppressed.
- **Before release**: strip the TEMP "linktablet-surface" debug logging
  (TabletSurfaceScanner + TabletBlockEntity.loadAdditional); one more
  full polish pass by the user; CHANGELOG "Unreleased" → 1.7.0; user
  modpack test; merge to main, tag v1.7.0, push, platform uploads.
  Remaining test debt: multiplayer via dev server, floor/ceiling
  orientation pass, ponder + held-item regression, chunk-border
  surface.

## Status (2026-07-19, evening)
- **v1.6.0 is the current release** (tagged, pushed, uploaded): per-app
  NOTE WINDOWS (floating/draggable/multi-open; editable over ANY screen
  incl. inventory via the event-driven `client/screen/NoteWindows`
  manager; read-only on the HUD; a note closes only via its own X) and
  the TIMER app type (4th type in the edit screen's cycler; tap
  transmits for 2t–600t set on a quadratic seconds+ticks slider; re-tap
  restarts; world taps swing the arm, GUI taps get a ~300ms press
  flash; migdzy credited). Registrar "9"→"11" — 1.6.0 does not pair
  with 1.5.x. Dev-tested live by the user across the whole session.
- **🎉 MODRINTH IS APPROVED AND PUBLIC** (confirmed via the public API
  2026-07-19 evening): first-approval review cleared, all versions
  visible, first download logged. CurseForge also updated. ALL sites
  carry 1.6.0 — the platform-watching chore is officially over.
- Earlier this arc: 1.5.3 (vanilla Redstone creative-tab listing +
  listing icon restyled to the 1.5.x plaque+chip chrome, all three app
  kinds shown), 1.5.2 (grid chip tiles, Avionics theme, one-item
  frequencies), 1.5.0/1.5.1 (Create-style chrome overhaul, both
  surfaces). Full details per release in CHANGELOG.md.
- Minor known issue: Distant Horizons can't resolve the tablet's WALL
  blockstate (omits `landscape`, falls back to default) — harmless
  LOD-only fallback, low priority.

## Next session (priority order)
1. **Listing refresh — TEXT + IMAGES DONE** (2026-07-19 late session):
   DESCRIPTION.md fully rewritten for 1.6.0 AND all image slots filled
   from the user+wife shoot the same evening: hero4.png (GUI with
   slider/timer/toggle variety + floating notes — triple-covers hero,
   notes, and gui-home), mounted-factory2.png, themes.png (2×2 stitch
   of Dark/Light/Parchment/Avionics from theme1–4.png raws; crop
   705,325 510×440), dyed-cases2.png, reused recipe.png. The
   click-and-slide slider action shot was SKIPPED on purpose (text
   covers it). Description LIVE ON BOTH LISTINGS, galleries + restyled
   icon uploaded, superseded images pruned — item 1 is FULLY DONE
   (2026-07-19 evening). Next priority: the pinned tablet overlay
   (item 2 below).
2. **Pinned tablet overlay** — next feature. Much cheaper since 1.6.0:
   `NoteWindows` already does event-driven windows over any screen,
   HUD rendering, and input capture; a mini-tablet is "another window
   type". Open design points: what the HUD (mouse-captured) state can
   do, whether the parked open-tablet keybind ships with it, window
   sizing vs. app count.
3. **Icon-friendly defaults** — still ON HOLD until the user reports
   what exactly renders poorly (analysis ready in
   docs/ICON_DEFAULTS_SCOPING.md).
4. **Multiblock screens** — gated on the user's answers in
   docs/MULTIBLOCK_DESIGN.md.

Parked: first-person interactive GUI on the held tablet; the DH
blockstate nit; any 1.20.1 backport (assessed 2026-07-19: possible but
a big lift — Forge loader, no DataComponents/StreamCodec, a second
codebase to maintain; wait for real demand signals now that the
listings are public).

## Active list (updated 2026-07-19 after the 1.6.0 cut)
1. **Refresh listing screenshots — FULLY DONE 2026-07-19** (user +
   wife shoot; description, galleries, and restyled icon live on both
   platforms; superseded images pruned from docs/images/).
2. **Icon-friendly defaults** — ON HOLD (2026-07-19): the user is
   pinning down with testers what exactly renders poorly before picking
   a direction. Options + analysis ready in
   `docs/ICON_DEFAULTS_SCOPING.md`; don't build until the user reports
   back.
3. **Pinned tablet overlay** (tester request relayed 2026-07-17): keep
   the tablet usable while playing — a floating mini-tablet window on
   screen while mining/moving. Got much cheaper in 1.6.0: the
   `NoteWindows` manager (event-driven windows over any screen + HUD
   render + input capture) is exactly the infrastructure it needs; a
   mini-tablet is "another window type", plus an interaction story for
   the bare HUD (the parked open-tablet keybind is the natural
   companion).
4. **Multiblock screens**: designed, not scheduled — see
   `docs/MULTIBLOCK_DESIGN.md` (open questions for the user at the
   bottom). Large, multi-session.

Shipped in 1.6.0 (2026-07-19): per-app note windows (persistent,
multi-open, editable over any screen incl. inventory, read-only HUD
pins; tester request) and the Timer app type (tap-for-a-set-time,
2t–600t quadratic slider, restart-on-retap, arm swing + press flash;
migdzy's request, credited in the changelog). Protocol "9"→"11".
Shipped in 1.5.3: creative Redstone-tab listing; restyled listing icon.

Platform approvals (Modrinth first review + CurseForge): the user is
watching the dashboards personally — off Claude's list; both listings
still 404 publicly as of 2026-07-17.

Still parked (kept parked at the 2026-07-17 vetting): open-tablet
keybind; far-future interactive GUI on the held tablet (first-person);
the DH landscape-blockstate LOD nit above.

## Release history (compressed)
- 1.4.0: ItemStack frequencies (frequency-card mod compat), container-menu
  editor with vanilla dragging, wrench rotation + landscape wall mounting,
  dye wash, text fitting, slider apps with click-and-slide
  (`client/BlockSliderDrag`). Registrar "5"→"7"; the interim
  1.3.3/1.3.4 builds were test-only.
- 1.3.x: dynamic screen tiles, list mode, quick-add, six themes (DARK never
  persists; "PurpleFox" honors a tester), ponder scene, momentary buttons.
  Registrar "4"→"5". v1.3.2 was the prior public version.
- Dev tooling in `tools/` (jar-excluded): `./gradlew nbtTool
  --args="gen|dump <path>"` (ponder schematic), `./gradlew iconTool
  --args="docs/icon.png"` (listing icon), `./gradlew chromeTool` (GUI
  chrome atlas). Relative, space-free args — Gradle splits on spaces.

## Former "unconfirmed tester suggestions" — resolved 2026-07-17
The batch arrived via a message the user later disavowed (someone else at
the keyboard / a stray paste — see local Claude memory). The user vetted
them personally on 2026-07-17:
- APPROVED → moved to the active list above: creative-mode item tab;
  app-icon-friendly defaults.
- DECLINED — do not build/investigate unless the user re-raises them:
  - The "blank white tablet in the ponder scene" report. (If a second
    person ever reproduces it, the plausible cause is ponder's virtual
    level skipping block color handlers, leaving the case untinted —
    check tinting in the ponder renderer first.)
  - A PurpleFox easter egg (the suspect requested it themselves).
