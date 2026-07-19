# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

## Status (2026-07-17, morning)
- **v1.5.2 is the current release** — tester-feedback batch, dev-tested
  by the user in a live session:
  - Grid tiles restyled to the list rows' plaque+chip structure on BOTH
    surfaces (GUI + placed screen; dense placed grids under 2.6 texels
    per cell keep classic full-color pips). Slider numerals are corner
    badges drawn in a raised layer (GUI z+200 / world y=0.011 — 3D block
    icons have real depth and buried them at the old layers). Faint
    accent outline on powered placed tiles.
  - New AVIONICS theme ("Avionics" — navy/steel-blue/cyan flight-computer
    look from a tester screenshot). Registrar "8"→"9".
  - One-item frequencies (requested by migdzy): the editor now accepts a
    single staged item in either slot; `Frequency.anyIcon()`/`isPair()`
    cover the icon fallbacks. Wire/NBT needed no changes.
- Earlier releases:
- **v1.5.1** (tagged, pushed, uploaded). The two
  releases of that evening:
  - **1.5.0** — Create-style UI overhaul of all three GUI screens
    (chrome atlas via `./gradlew chromeTool`, `client/screen/chrome/`
    layer; ALL chrome theme-tinted — borders tint with `bodyOuter`, the
    wood-stays-wood idea was rejected), new CREATE/"Parchment" theme,
    slider level readouts everywhere, per-slider min/max range
    (dual-knob "Range" row; min > 0 = never off, by user decision).
    Registrar "7"→"8". Feedback fixes: ink-field edge tiling, placed
    list-slider fill z-fight (latent since 1.4.0).
  - **1.5.1** — placed/held screens match the GUI chrome via quad bevel
    emulation in `TabletScreenRenderer` (see the live-screen gotcha in
    CLAUDE.md; three visual iterations: shadow frame seam, softer
    bevels, inset icon chips). No wire change — pairs with 1.5.0.
- **Platforms**: ALL versions 1.0.0–1.5.1 are uploaded to Modrinth; the
  project is "Under review" (first-approval queue) and therefore
  invisible to public search/API until a moderator approves — then
  everything goes live at once. CurseForge presumably in the same boat.
  Still 404 publicly as of 2026-07-17; the user is watching the
  dashboards personally (off Claude's list).
- Minor known issue spotted in a FAMILYPACK log: Distant Horizons can't
  resolve the tablet's WALL blockstate (it omits the `landscape`
  property and falls back to the default state) — harmless LOD-only
  fallback, low priority.

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
