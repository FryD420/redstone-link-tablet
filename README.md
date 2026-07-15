# Create: Redstone Link Tablet

A smart-home control panel for your Create contraptions. The **Link Tablet** is a
handheld device with an app-style GUI that toggles [Create](https://modrinth.com/mod/create)'s
Redstone Links on and off remotely — flick your base's lights, doors, trains, and machines
from anywhere in range, all from one screen.

For **Minecraft 1.21.1** on **NeoForge**, requires **Create 6.x**.

## Features

- **Apps** — save up to 32 named switches on one tablet. Each app has a name, a color,
  an optional item icon, and an on/off state that persists on the item.
- **Scenes** — one app can hold up to 8 frequencies that all toggle together
  ("Shut down the factory", "Open all doors").
- **Create-native frequencies** — apps transmit on Create's real Redstone Link network
  using the same two-item frequency system, chosen from a searchable in-GUI picker
  (no physical items needed). Anything a Redstone Link or Linked Controller can
  trigger, the tablet can too.
- **Momentary apps** — a per-app setting for hold-to-transmit (doorbells, dispensers);
  the held state is server-side and transient, so it can never get stuck on.
- **Per-app signal strength (1–15)** — strongest signal wins on shared frequencies,
  like stacked Redstone Links.
- **Placeable** — sneak + right-click to mount the tablet on any wall, floor, or
  ceiling; it transmits from the block and its screen glows while any app is on.
  Sneak + right-click with an empty hand picks it back up, apps intact.
- **Dyed cases** — craft with any dye to recolor the case (all 16 colors),
  apps preserved.
- **Two layouts** — an icon grid or a list with toggle switches; pick whichever you
  like with one click, per player.
- **It feels like a tablet** — the screen is dark until you open it, then lights up
  (fully emissive, glows in the dark), with a solid back, camera, and status LED —
  plus UI sounds throughout.

## How to use

1. Craft the tablet (brass-age shaped recipe):

   ```
   Brass Sheet     Tinted Glass   Brass Sheet
   Redstone Link   Linked Ctrl.   Redstone Link
   Brass Sheet     Electron Tube  Brass Sheet
   ```

2. Right-click to open it. Tap **+** to create an app.
3. Pick two items as the frequency (exactly like tuning a Redstone Link), hit **Add** —
   repeat to stack more frequencies into a scene — then **Save**.
4. Set your receiving Redstone Links to the same frequency.
5. Tap an app to toggle it. **Left-click toggles, right-click edits.**

While an app is ON and the tablet is anywhere in your inventory, a virtual transmitter
broadcasts **signal strength 15** on its frequencies from your position. Range and
chunk behavior match Create's handheld Linked Controller: the player is the
transmitter, so receivers must be within Create's link range of you.

## Compatibility

- Requires **Create 6.x** on NeoForge 1.21.1, on both server and client.
- Full multiplayer support (server-authoritative edits, per-player transmitters).
- Plays nicely with other link-network senders (Linked Controllers, other addons):
  receivers simply take the strongest signal, like stacked Redstone Links.

## Building from source

- **Java 21** (JDK) and internet access on first Gradle sync.
- `./gradlew build` → jar appears in `build/libs/`.
- `./gradlew runClient` launches a dev client with the mod + Create.
- Check the Create dependency versions in `build.gradle` against your Create version
  (Ponder/Flywheel/Registrate versions must match what your Create build uses).

### Version-sensitive spots (if the build errors)

Each is marked with a comment in the source:

- `compat/VirtualTransmitter.java` — the `Couple` import
  (`net.createmod.catnip.data.Couple` on Create 6.x; older Create used
  `com.simibubi.create.foundation.utility.Couple`).
- `build.gradle` — Create/Ponder/Flywheel/Registrate versions.
- `compat/TabletTransmitterHandler.java` — uses
  `Create.REDSTONE_LINK_NETWORK_HANDLER` and the `IRedstoneLinkable`
  interface; method names are stable across Create 6.x.

## License

MIT — free to use in modpacks.
