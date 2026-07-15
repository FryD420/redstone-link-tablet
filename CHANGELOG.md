# Changelog

## Unreleased

- Placeable tablet: sneak + right-click any surface to mount the tablet on walls,
  floors, or ceilings. Right-click to use the same GUI; the mounted tablet transmits
  from its own position while the chunk is loaded. Its screen switches on (emissive,
  light level 7) whenever any app is active. Sneak + right-click with an empty hand
  picks it back up — apps and case color survive placing, breaking, and support loss.
- Dyed cases: craft the tablet with any dye (shapeless) to color its case — back,
  edges, and front bezel ring. Apps are preserved; re-dye anytime. All 16 colors.
- Momentary apps: a per-app "Momentary" setting makes the app transmit only while the
  button is held down (doorbells, dispensers). Held state is transient and server-side,
  so a disconnect can never leave a signal stuck on. Momentary apps show a hollow ring
  pip in grid view and a push button in list view.
- Per-app signal strength (1-15) via a slider in the edit screen; when multiple apps
  drive the same frequency, the strongest signal wins, like stacked Redstone Links.
- Edit screen layout rework: two columns, and the color picker is now a compact
  dropdown swatch button instead of an always-visible grid.
- UI sounds throughout the tablet: open/close, toggle clicks (distinct on/off pitch),
  save chime, remove sound, frequency chip ticks, view-mode switch. Nearby players
  hear a faint click when someone toggles an app.

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
