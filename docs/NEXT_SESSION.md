# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

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
1. **Listing refresh** — the user shoots gallery screenshots SOLO from
   real 1.6.0 gameplay (their call). When they exist: refresh
   docs/images/ + rewrite docs/DESCRIPTION.md (it predates 1.4.0 —
   notes, timers, sliders, themes, chrome are all missing) and make
   sure the restyled docs/icon.png is on both listings. Claude can do
   the DESCRIPTION.md rewrite without the screenshots — good warm-up.
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
1. **Refresh listing screenshots** — the user shoots these SOLO from
   real gameplay now that 1.6.0 is out (their call 2026-07-19); update
   docs/images/ + docs/DESCRIPTION.md + re-upload the restyled
   docs/icon.png to both platforms when they land.
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
