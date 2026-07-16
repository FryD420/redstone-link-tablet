# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

## Status (2026-07-16, end of day)
- **v1.3.2 is LIVE on CurseForge and Modrinth** with the new listing icon
  (`docs/icon.png`); tagged and pushed. Tester-verified in the FAMILYPACK
  instances over several fix rounds.
- The 1.3.x round shipped (see CHANGELOG 1.3.1 + 1.3.2 for the split):
  dynamic screen tiles with outlined name labels, list-mode names +
  GUI-matched switches, right-click-a-link quick-add, six themes (DARK never
  persists; "PurpleFox" honors a tester), a ponder scene, hold-to-press
  momentary buttons on placed tablets (lit pips, minimum pulse, no punch
  animation), and a Light-theme text-shadow fix. Network registrar "4" → "5".
- New dev tooling in `tools/` (excluded from the jar):
  `./gradlew nbtTool --args="gen|dump <path>"` regenerates/dumps the ponder
  schematic; `./gradlew iconTool --args="docs/icon.png"` regenerates the
  listing icon (composites `docs/images/icon-bg.png` under the 3D tablet).
  Relative, space-free args only — Gradle splits on spaces.

## After 1.3.2 (priority order)
1. Small stuff: cauldron dye-wash, open-tablet keybind.
2. Far-future (parked): interactive GUI on the held tablet (first-person).
3. Listing screenshots could show the new dynamic tiles/themes — drop new
   captures in docs/images/, wire into docs/DESCRIPTION.md like hero2.png.

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
