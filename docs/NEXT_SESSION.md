# Link Tablet Mod — Session Handoff (2026-07-14)

## Project
"Create: Redstone Link Tablet" — NeoForge 1.21.1 (21.1.233) addon for Create 6.0.10.
Item: "Link Tablet" (linktablet:tablet). Mod id: `linktablet`, package com.modpack.linktablet.
Folder: C:\Users\ttvfr\Desktop\MY CUSTOM MODS\DEV\signaltablet-mod (old folder name, intentional).
GitHub: https://github.com/FryD420/redstone-link-tablet (main, all work pushed, tagged v1.0.0).
Author: FryD42 (GitHub is FryD420). License MIT.

## Status
- 1.0.0 submitted to Modrinth (slug redstone-link-tablet) AND CurseForge — both awaiting
  first-time review. Updates after approval are near-instant.
- Everything below is committed/pushed but UNRELEASED (in CHANGELOG "Unreleased"):
  - UI sounds (UISounds helper, repurposed vanilla sounds)
  - Momentary apps (hold-to-transmit; transient server-side holds, never on the item)
  - Per-app signal strength 1–15 (slider; per-frequency max wins)
  - Dyed cases (16 colors; grayscale case texture + tint, custom TabletDyeRecipe keeps apps)
  - Placeable tablet block (sneak+use mounts on wall/floor/ceiling; same GUI via
    AppTarget/AppView abstraction; transmits from block pos; emissive screen-on model
    + light level 7; sneak+empty-hand pickup; drops with data)
- All features user-tested in dev and working. Multiplayer verified earlier on dedicated server.

## NEXT TASK: cut 1.1.0 release
1. Bump mod_version in gradle.properties → 1.1.0, move CHANGELOG Unreleased → 1.1.0.
2. Build (jar: build/libs/linktablet-1.1.0.jar), commit, tag v1.1.0, push.
3. User tests jar in their real modpack, then uploads to Modrinth/CurseForge
   (game version 1.21.1, NeoForge, Java 21, Create required dependency).

## After that (priority order)
App reordering → Ponder scene → small stuff (cauldron dye-wash, open-tablet keybind,
unify handheld lit screen with new bright tablet_screen_on.png texture) →
far-future: interactive GUI on held tablet (parked).

## Key technical notes
- Build: gradlew build / runClient. Shell may need machine PATH reload for java
  (see memory). RCON on dev server: port 25575, password devrcon; player "Dev" opped.
- Block models with transparent overlay textures NEED "render_type": "minecraft:cutout".
- Sprite bounds: tablet textures occupy cols 2–13, rows 1–14; row 0/cols 0–1 transparent.
- StreamCodec.composite maxes out ~6 fields — SignalApp uses a hand-rolled stream codec.
- GUI min height budget is 240 units (dev window at GUI scale 2) — edit screen is full.
