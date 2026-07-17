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
  truth for renderer AND server hit-test — keep them together). Placed-screen
  chrome (1.5.1) is quad bevel EMULATION of the GUI look — NEVER sample the
  chrome atlas in the world pass (second RenderType kills the shared batch;
  the art aliases at 2-texel tiles). Hairline layers are reserved: frame
  0.5x, plaque bevels 1.5x, grid chip 1.75x, list groove 2.75x, grid
  groove 3.25x (icons 3.5x, text 4x); bevels auto-skip cells under 1.2
  texels, and grid tiles under 2.6 texels keep the classic full-color
  pip instead of the plaque+chip look. Screen quads
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
  so 1.2.x tablets stay untouched. The theme STREAM_CODEC is ordinal-based:
  append-only, and every appended constant needs a registrar bump (CREATE/
  "Parchment" drove "7"→"8"). `textShadow` is derived from the id — light
  surfaces (LIGHT, CREATE) opt out in the ctor.
- GUI chrome (1.5.0): all screen SURFACES blit `textures/gui/chrome.png`
  through `client/screen/chrome/Chrome`; region coords live ONLY in
  `ChromeAtlas` (shared with the generator — regen the atlas with
  `./gradlew chromeTool`, F3+T reloads it in a running client). Tinting is
  shader-color multiply (`GuiGraphics.setColor`): surfaces are authored
  near-white so theme/app colors multiply in; wood rails are full-color and
  NEVER tinted. Mechanisms (switches, pips, glyphs, value bars, swatches)
  stay procedural `fill()` on purpose. NineSlice TILES edges/centers (never
  stretches). ChromeEditBox callers construct inset by (4,5)/(w-8,h-10) so
  the painted ink-well matches the old bordered-EditBox rect.
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
- Frequencies (1.4.0): `Frequency` stores two full ItemStacks (count forced
  to 1); channel identity DELEGATES to Create's
  `RedstoneLinkNetworkHandler.Frequency` (item + dyed_color component only —
  other components are ignored, matching Create's network exactly). Pre-1.4.0
  NBT (`item1`/`item2` item IDs) decodes forever via the LEGACY_CODEC
  alternative in `Frequency.CODEC` — never remove it. The ponder schematic
  still carries legacy-format NBT on purpose (free regression test).
- Create fan processing picks ONE type per position by priority (descending;
  SPLASHING=400). `compat/TabletWashingType` (500) shadows splashing in water
  and therefore MUST delegate everything that isn't a dyed tablet back to
  `AllFanProcessingTypes.SPLASHING` — a water-valid type that doesn't
  delegate silently kills sand→clay etc.
- Screen content rotation (1.4.0): quarter-turn count lives on the BE
  (`screen_rotation`, 0 never persisted, SCREEN_ROTATION component for the
  item round-trip). `TabletScreenMath` owns the transform — `glassW/H(rot)`,
  `gridLayout(count, rot)`, and the inverse swizzle inside `hitPip` — and the
  renderer's center-pivot pose rotation must stay in lockstep with it. Held
  items always render rotation 0.
- Slider apps (1.4.0): `active` is DERIVED from `strength > 0` and never
  user-toggled — that's what keeps the transmitter collectors'
  `active && !momentary` rule working unchanged. `sanitized()` enforces it.
  1.5.0 adds `sliderMin`/`sliderMax` (defaults 0/15, optionalFieldOf so old
  NBT and untouched sliders serialize unchanged): `SignalApp.
  valueFromFraction`/`fillFraction` are the ONLY drag→value and
  value→bar-position mapping sites — every consumer (GUI drags,
  BlockSliderDrag, TabletBlock click-to-set, both renderers) goes through
  them. min > 0 means always transmitting (no off notch, user decision);
  numerals always show ABSOLUTE strength while bars/knobs are
  range-relative. `withSliderValue` clamps into the range server-side.
- `TabletScreenMath` also owns the screen LAYOUT constants (SPACE, tile
  helpers, `sliderInset`, `sliderBarU`): the renderer draws through them and
  the slider drag maps the crosshair against them — three consumers, one
  source; never fork the numbers.
- Placed-tablet slider drags are CLIENT-driven: `client/BlockSliderDrag`
  projects the look-ray onto the screen's infinite plane each tick
  (`TabletScreenMath.logicalUFromRay`) and sends `SetSliderPayload`s; it also
  cancels the repeating use-key while dragging so neighbors can't be clicked.
  The server only ever sees validated payloads.

## Release process

1. Move CHANGELOG "Unreleased" → new version + date; bump `mod_version` in
   `gradle.properties`.
2. `./gradlew build`; the user tests the jar in their real modpack.
3. Commit, tag `v<version>`, push, then the user uploads to CurseForge and
   Modrinth (game 1.21.1, NeoForge, Java 21, Create required). Platform updates
   are near-instant after first approval.
