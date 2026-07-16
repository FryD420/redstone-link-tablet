# Changelog

## 1.3.2 — 2026-07-16

- Held momentary buttons now light up on a placed tablet's screen for
  everyone nearby — including presses made through the GUI, and on your
  own tablet's screen in first person.
- Fixed: clicking a momentary app faster than a server tick could produce
  no signal at all; every press now transmits a short minimum pulse.
- Fixed: holding a momentary button on a placed tablet made the character
  punch the tablet repeatedly.

## 1.3.1 — 2026-07-16

- Dynamic app tiles on the physical screen: the pip grid now sizes itself to
  the app count — one app fills the whole glass, two split it, and so on,
  converging on the old 4x5 grid at 17+ apps. Big tiles (up to 4 apps) also
  show the app's name under its icon, outlined so it stays readable on
  bright tile colors. Tap-to-toggle follows the new layout exactly.
- The physical screen's list mode now shows each app's name between its
  icon chip and switch.
- Momentary apps are now pressable directly on a placed tablet's screen:
  hold right-click on the button to transmit, let go to stop (the signal
  drops within about half a second of release).
- Right-click any Redstone Link (or anything with a link frequency, like
  elevator contacts) while holding the tablet: the app editor opens with that
  link's frequency pre-filled and a name suggested — just hit Save. If an app
  already has that frequency, the editor opens on it instead of duplicating.
- Themes: a new palette button in the tablet GUI (next to rearrange) picks
  one of six UI themes — Dark (the classic), Light, AMOLED, Brass, Terminal,
  and PurpleFox (named for the player who suggested it and helped test).
  The theme is stored per tablet and
  recolors the GUI, the edit screen, and the physical screen for everyone.
- Ponder: the tablet now has a Ponder scene (hold W over the item) showing a
  mounted tablet toggling a Redstone Link and lamp, plus the new
  link-to-app shortcut.
- Note for servers: the network protocol version changed — 1.3.0 clients and
  servers must both be updated.

## 1.2.1 — 2026-07-15

- Fixed a crash ("Not building!") when rendering tablet screens whose app
  icons are items with special render types (common with modded icons):
  screen quads and item icons are now drawn in separate passes.

## 1.2.0 — 2026-07-15

- Live tablet screens: both the held tablet and the placed tablet now render
  a real mini app grid on their physical screens — one colored pip per app
  (4x5, first 20), glowing when active, hollow for momentary apps. Each
  tablet remembers the layout you last used in its GUI: in list mode the
  physical screen shows mini toggle-switch rows (first 5 apps) instead.
- Tap-to-toggle: clicking an app's pip directly on a placed tablet's screen
  toggles that app on the spot — no GUI needed. Clicking anywhere else on
  the tablet still opens the full GUI.
- The held tablet's lit screen now uses the same bright screen texture as
  the placed tablet, and empty tablets show the same flat glass as in-use
  ones (in the world; the inventory icon is unchanged).
- App reordering: a new rearrange button in the tablet's title bar enters a
  drag-to-rearrange mode (in both grid and list view) — taps stop toggling,
  apps reflow live as you drag, and the order syncs and persists. Works on
  held and placed tablets.
- Fixed: releasing a momentary app could leave its signal stuck on if the
  app list changed (app removed/reordered) while the button was held.

## 1.1.1 — 2026-07-15

- Recipe rework: the tablet is now crafted from 4 brass sheets, 2 redstone links,
  tinted glass (screen), an electron tube, and a linked controller at the core —
  no more nether stars. Fits Create's brass-age progression.

## 1.1.0 — 2026-07-15

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
