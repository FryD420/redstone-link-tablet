# Changelog

## 1.0.0 — 2026-07-14

Initial release.

- Link Tablet item: craftable handheld (4 Redstone Links + 4 Nether Stars + Linked Controller).
- App-style GUI: up to 32 named on/off apps per tablet, each with color, optional item icon,
  and persistent toggle state.
- Scenes: up to 8 Redstone Link frequencies per app, all toggled together.
- Transmits on Create's Redstone Link network from the player's position while the tablet
  is in inventory (signal strength 15, Linked Controller semantics).
- Grid and list GUI layouts with a per-player toggle.
- Tablet screen lights up (emissive) while the GUI is open; solid back with camera + LED.
- Full multiplayer support (server-authoritative app edits, per-player transmitters).
