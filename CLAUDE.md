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

## Release process

1. Move CHANGELOG "Unreleased" → new version + date; bump `mod_version` in
   `gradle.properties`.
2. `./gradlew build`; the user tests the jar in their real modpack.
3. Commit, tag `v<version>`, push, then the user uploads to CurseForge and
   Modrinth (game 1.21.1, NeoForge, Java 21, Create required). Platform updates
   are near-instant after first approval.
