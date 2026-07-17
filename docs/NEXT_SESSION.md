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
  **First thing next session: verify both listings went public.**
- Minor known issue spotted in a FAMILYPACK log: Distant Horizons can't
  resolve the tablet's WALL blockstate (it omits the `landscape`
  property and falls back to the default state) — harmless LOD-only
  fallback, low priority.

## Next session (priority order)
1. **Verify platform approvals** (Modrinth + CurseForge listings public).
2. **Refresh listing screenshots** in docs/images/ + docs/DESCRIPTION.md —
   every current shot predates BOTH the 1.5.0 GUI overhaul and the 1.5.1
   in-world chrome. Reshoot on 1.5.1. Decide at the same time whether
   iconTool's flat-GUI listing icon gets restyled to match (user's
   standing preference: all chrome follows the theme).
3. Multiblock screens: designed, not scheduled — see
   `docs/MULTIBLOCK_DESIGN.md` (open questions for the user at the bottom).
4. Still parked: open-tablet keybind; far-future interactive GUI on the
   held tablet (first-person); the DH landscape-blockstate LOD nit above.

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

## Unconfirmed tester suggestions (need the user's explicit go-ahead)
These arrived via a message the user later disavowed (someone else at the
keyboard / a stray paste — see local Claude memory). Reasonable ideas, but
do NOT build them until the user asks:
- Creative-mode item tab for the mod (like Create's).
- More app-icon-friendly defaults (some items render poorly as icons).
- A "blank white tablet in the ponder scene" report — the user says the
  scene works for them, but the claim is plausible: ponder's virtual level
  may skip block color handlers, which would leave the case untinted
  (bright white). If anyone reproduces it, check tinting in the ponder
  renderer first.
- A PurpleFox easter egg (the suspect requested it themselves).
