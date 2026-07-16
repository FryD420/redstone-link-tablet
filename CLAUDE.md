# Create: Redstone Link Tablet

NeoForge 1.21.1 (21.1.233) addon for Create 6.0.10. Adds the "Link Tablet"
(`linktablet:tablet`) — a handheld/placeable device with app-style toggles that
transmit on Create's Redstone Link network.

- Mod id `linktablet`, package `com.modpack.linktablet`. The project folder is
  still named `signaltablet-mod` — old name, intentional, do not rename.
- GitHub: https://github.com/FryD420/redstone-link-tablet (author FryD42; the
  GitHub account is FryD420). License MIT.
- Published on CurseForge and Modrinth (slug `redstone-link-tablet`). Listing
  text lives in `docs/DESCRIPTION.md`, listing images in `docs/images/`.
- Current status and roadmap: `docs/NEXT_SESSION.md`.

## Build and run

- `./gradlew build` → jar at `build/libs/linktablet-<version>.jar`.
  `./gradlew runClient` launches a dev client (offline player "Dev").
- If `java` isn't found in a fresh shell, reload the machine PATH first:
  `$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')`
- Dev dedicated server has RCON enabled; player "Dev" is opped. RCON
  credentials are in local Claude memory, not in this repo.
- Version lives only in `gradle.properties` (`mod_version`); mods.toml gets it
  via Gradle expansion.

## Technical gotchas

- Block models with transparent overlay textures NEED `"render_type": "minecraft:cutout"`.
- Tablet textures occupy sprite cols 2–13, rows 1–14; row 0 and cols 0–1 are transparent.
- `StreamCodec.composite` maxes out around 6 fields — `SignalApp` uses a
  hand-rolled stream codec; extend that, don't switch back to composite.
- GUI vertical budget is 240 units (dev window at GUI scale 2); the edit screen
  already uses all of it.
- Create 6.x import quirk: `Couple` is `net.createmod.catnip.data.Couple`
  (older Create used `com.simibubi.create.foundation.utility.Couple`) — see
  `compat/VirtualTransmitter.java`.
- Live-screen rendering (1.2.0): `client/render/` holds the BER + BEWLR;
  shared geometry/click math in `block/TabletScreenMath.java` (one source of
  truth for renderer AND server hit-test — keep them together). Screen quads
  must bleed ~0.5 texel under the bezel ring or the baked art shimmers
  through the seam. The chunk mesh bakes the case tint, so the BE forces a
  client re-render in `onDataPacket` (tint arrives after the placement
  rebuild). The item model is a `builtin/entity` stub; real geometry is
  standalone-baked `tablet_base(_lit).json` via `ModelEvent.RegisterAdditional`.
- NEVER interleave custom-RenderType quads with nested item rendering in one
  loop: an item model needing a non-fixed render type ends the shared buffer
  batch, killing the cached VertexConsumer ("Not building!" crash — only
  surfaces with modded icons, not vanilla ones). Draw all quads, then all
  icons, then all text (see TabletScreenRenderer.render; font rendering has
  its own render types, so labels are a third pass — 1.3.0 extension of the
  1.2.1 rule).
- Dynamic screen grid (1.3.0): `TabletScreenMath.gridLayout(appCount)` is the
  ONLY count→cols×rows table; renderer and server hit-test both call it —
  never duplicate the breakpoints.
- Themes (1.3.0): `theme/ScreenTheme` — DARK must stay byte-identical to the
  pre-1.3.0 hardcoded colors and is never persisted (no component, no NBT),
  so 1.2.x tablets stay untouched.
- Ponder 1.0.82 API is builder-style: `overlay().showControls(vec, Pointing,
  ticks).rightClick()` (no InputWindowElement); `Pointing` lives in
  `net.createmod.catnip.math`. Plugin registered via `PonderIndex.addPlugin`
  in `FMLClientSetupEvent`. The scene schematic
  `assets/linktablet/ponder/tablet.nbt` is regenerated with
  `./gradlew nbtTool --args="gen <path>"` (space-free path — Gradle splits
  args on spaces; the tools/ class is excluded from the shipped jar).
- Ponder text is LANG-KEY based at runtime: the inline strings in scene code
  only feed datagen (which this project doesn't run). Every `scene.title` and
  `.text(...)` needs a manual en_us.json entry —
  `linktablet.ponder.<sceneId>.header` and `.text_<n>`, where n follows the
  program order of `.text()` calls. Reordering scene text beats means
  renumbering the lang keys.

## Release process

1. Move CHANGELOG "Unreleased" → new version + date; bump `mod_version` in
   `gradle.properties`.
2. `./gradlew build`; the user tests the jar in their real modpack.
3. Commit, tag `v<version>`, push, then the user uploads to CurseForge and
   Modrinth (game 1.21.1, NeoForge, Java 21, Create required). Platform updates
   are near-instant after first approval.
