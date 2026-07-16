# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

## Status (2026-07-16, end of day)
- **v1.4.0 SHIPPED**: tagged and pushed; the user is uploading to
  CurseForge/Modrinth (near-instant after first approval — verify next
  session that both went live). Contents: ItemStack frequencies (Create
  Unique Cards / Frequency Create compat), container-menu app editor with
  vanilla dragging (Create GhostItemMenu + picker overlay), Create wrench
  rotation + landscape wall mounting, dye wash (cauldron + fan),
  text fitting, slider apps with exact click-and-slide on placed tablets
  (client-driven, `client/BlockSliderDrag`), editor keyboard fix.
  Registrar "5"→"7": 1.4.0 does not pair with 1.3.x. The interim
  "1.3.3"/"1.3.4" builds were TEST-ONLY (never tagged; changelogs folded
  into 1.4.0; the v1.3.3-test draft GitHub release was deleted).
- **v1.3.2 was the prior public version**; tester-verified in the
  FAMILYPACK instances over several fix rounds.
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

## After 1.4.0 (priority order)
1. **1.5.0 — full Create-style (Stock Keeper) UI overhaul** + "Parchment"
   theme (registrar "7"→"8"; DARK stays the never-persisted default with
   byte-identical ints — the overhaul changes pixels, not persistence).
   Plan sketch lives in the 2026-07-16 session plan: a `Chrome` nine-slice
   helper layer over one `textures/gui/chrome.png` atlas, themes tint the
   parchment, wood rails stay untinted; convert PickerOverlay-bearing
   AppEditScreen last (it's tightest on space). Afterwards: refresh listing
   screenshots in docs/images/ + docs/DESCRIPTION.md.
2. Multiblock screens: designed, not scheduled — see
   `docs/MULTIBLOCK_DESIGN.md` (open questions for the user at the bottom).
3. Still parked: open-tablet keybind; far-future interactive GUI on the held
   tablet (first-person).

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
