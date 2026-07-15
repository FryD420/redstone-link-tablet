# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

## Status (2026-07-15, end of day)
- **v1.2.0 tagged and pushed**; user uploading to CurseForge/Modrinth with the
  prepared changelog text and updated docs/DESCRIPTION.md (new hero2.png).
- 1.2.0 added: app reordering (drag-to-rearrange GUI mode), live physical
  screens on held + placed tablets (4x5 icon grid or per-tablet switch-list,
  chosen by the last GUI view used on that tablet), tap-to-toggle on placed
  tablets, plus fixes (momentary stuck-signal, dye tint on placement,
  empty-screen consistency).
- All dev-tested by the user through many visual iteration rounds; the final
  jar was headed for real-modpack testing at session end.

## NEXT TASK: Ponder scene
Create-style Ponder scene(s): craft → open → add app → toggle → mount → tap
the screen. Start the session in plan mode.

## After that (priority order)
1. Small stuff: cauldron dye-wash, open-tablet keybind.
2. Far-future (parked): interactive GUI on the held tablet (first-person).
3. User may bring new gameplay screenshots for the listings — drop in
   docs/images/, wire into docs/DESCRIPTION.md like hero2.png was.
