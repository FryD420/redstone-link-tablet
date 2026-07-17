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

## Active list (vetted 2026-07-17; ordered smallest → largest effort)
1. **Creative-mode tab** for the mod's items (tester suggestion,
   explicitly approved by the user 2026-07-17). Trivial — registration +
   lang key; good session warm-up.
2. **Listing icon restyle** — iconTool's flat-GUI icon updated to match
   the themed chrome look (user's standing preference: all chrome
   follows the theme). Small: tool pipeline exists, no game code.
3. **Refresh listing screenshots** in docs/images/ + docs/DESCRIPTION.md —
   every current shot predates the whole 1.5.x visual line. Reshoot on
   the current build (grid chip look + 8 themes incl. Avionics make a
   better gallery). Low difficulty but time-heavy and needs the user;
   do right after the icon so both listing assets land together —
   ideally with items 1–2 already in the jar being photographed.
4. **Icon-friendly defaults** — better rendering for items that look poor
   as app icons (tester suggestion, explicitly approved 2026-07-17).
   Likely small code, but needs a scoping chat first: which items look
   bad, and is the fix a curated default set, a render tweak, or both?
5. **Per-app notes** (tester request relayed by the user 2026-07-17):
   each app gets an optional free-text note the player can open, type
   into, and close from the GUI. Requested UX: the note POPS OUT as a
   floating, draggable window (title bar + close button, like a Windows
   window) rather than an inline panel — which sidesteps the 240-unit
   GUI budget since it floats over the screen. Small-medium: new string
   field on SignalApp (extend the hand-rolled stream codec,
   optionalFieldOf so old NBT is untouched, registrar bump), a
   drag-anywhere window widget in chrome styling (new widget type — the
   chrome atlas nine-slices should cover the frame), multiline editing
   (vanilla MultiLineEditBox as the base). GUI-only for now;
   placed-screen display (tooltip? hover?) is a design question.
6. **Timed button mode** (requested by migdzy, relayed by the user
   2026-07-17): per-app activation
   option that behaves like a customizable button — tapping emits a
   timed pulse instead of latching, with a slider setting the hold
   duration. Relative of the existing momentary mode (held-while-pressed);
   this one is fixed-length after a single tap. Design TBD (duration
   units/range, interaction with slider apps and min/max ranges).
   Medium — a full release's worth: SignalApp codec + registrar bump,
   edit-screen UI, server pulse timing, both renderers, NBT compat.
7. **Pinned tablet overlay** (tester request relayed by the user
   2026-07-17): keep the tablet usable while playing — a floating,
   moveable mini-tablet window (same windowing idea as the notes popout)
   that stays on screen while mining/moving. Medium-large and the hard
   part is NOT the window: a normal Screen grabs the mouse and pauses
   input, so this needs a HUD-layer overlay plus an interaction story
   (e.g. keybind toggles between "pinned/passive" and "focused/
   clickable" — the parked open-tablet keybind is the natural
   companion/prerequisite). Renders the app grid read-only at minimum;
   click-through toggling is the stretch goal. Shares the draggable
   window widget with item 5 — build notes first, reuse the widget.
8. **Multiblock screens**: designed, not scheduled — see
   `docs/MULTIBLOCK_DESIGN.md` (open questions for the user at the
   bottom). Large, multi-session.

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
