# Link Tablet Mod — Session Handoff (2026-07-15)

## Project
"Create: Redstone Link Tablet" — NeoForge 1.21.1 (21.1.233) addon for Create 6.0.10.
Item: "Link Tablet" (linktablet:tablet). Mod id: `linktablet`, package com.modpack.linktablet.
Folder: C:\Users\ttvfr\Desktop\MY CUSTOM MODS\DEV\signaltablet-mod (old folder name, intentional).
GitHub: https://github.com/FryD420/redstone-link-tablet (main, all work pushed, tagged v1.1.1).
Author: FryD42 (GitHub is FryD420). License MIT.

## Status: RELEASED AND LIVE
- v1.1.1 is published on CurseForge (approved) and Modrinth (slug redstone-link-tablet),
  with full descriptions, screenshots, and galleries — actively getting downloads.
- Listing source of truth: docs/DESCRIPTION.md (image URLs point at docs/images/ on main).
- 1.1.1 = 1.1.0 features (UI sounds, momentary apps, per-app signal strength 1–15,
  dyed cases, placeable tablet block) + recipe rework (brass sheets, tinted glass,
  2 redstone links, electron tube around a linked controller — no more nether stars).
- All features user-tested in dev and in the user's real modpack. Multiplayer verified
  earlier on a dedicated server.

## NEXT TASK: app reordering
Let players rearrange apps on the tablet (currently fixed in creation order).
Decide interaction (drag in grid view? move up/down buttons in list view? both?)
before implementing — user likes starting sessions in plan mode.

## After that (priority order)
Ponder scene → small stuff (cauldron dye-wash, open-tablet keybind, unify handheld
lit screen with the bright tablet_screen_on.png block texture) →
far-future: interactive GUI on held tablet (parked).

## Release process (repeatable)
1. Move CHANGELOG Unreleased → new version + date; bump mod_version in gradle.properties.
2. gradlew build → build/libs/linktablet-<version>.jar; user tests in their modpack.
3. Commit, tag v<version>, push, then user uploads to CurseForge + Modrinth
   (game 1.21.1, NeoForge, Java 21, Create required). Updates are near-instant.

## Key technical notes
- Build: gradlew build / runClient. Shell may need machine PATH reload for java
  (see memory). RCON on dev server: port 25575, password devrcon; player "Dev" opped.
- Block models with transparent overlay textures NEED "render_type": "minecraft:cutout".
- Sprite bounds: tablet textures occupy cols 2–13, rows 1–14; row 0/cols 0–1 transparent.
- StreamCodec.composite maxes out ~6 fields — SignalApp uses a hand-rolled stream codec.
- GUI min height budget is 240 units (dev window at GUI scale 2) — edit screen is full.
