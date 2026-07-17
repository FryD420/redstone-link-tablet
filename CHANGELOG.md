# Changelog

## 1.5.2 — 2026-07-17

- Grid tiles now match the list rows' look on both the GUI and the
  placed screen: a themed plaque button with the app color inset as a
  chip behind the icon, instead of the color flooding the whole tile.
  The densest placed-screen grids (13+ apps portrait) keep the classic
  full-color pips — chips that small would crowd out the icons.
- Powered apps on placed tablets get a faint glowing outline around
  their tile, echoing the GUI's accent border.
- Slider tiles show their level as a stack-count style number on the
  chip's corner, and it now always draws on top — block icons could
  previously cover it on both the GUI and the placed screen.
- New theme: "Avionics" — deep navy panels, steel-blue frames, and a
  cyan accent, styled after a flight computer (tester request).
- Frequencies can now use just one item: stage a single item in either
  slot and hit Add — matching Create's own links, where the second slot
  may stay empty (requested by migdzy). One-item frequencies render
  their lone item centered on the app tile.
- Note for servers: the network protocol version changed — 1.5.2
  clients and servers don't pair with 1.5.1 or older.

## 1.5.1 — 2026-07-16

- The placed tablet's screen now matches the 1.5.0 GUI look: app tiles
  and list rows render as beveled plaques, a theme-colored frame runs
  around the glass, and slider bars sit in recessed grooves. Held
  tablets get the same treatment. On the densest grids the accents
  step aside automatically (tiles that small would just shimmer).
- Pure visual change — no network protocol change, so 1.5.1 clients and
  servers pair freely with 1.5.0.

## 1.5.0 — 2026-07-16

- Create-style UI overhaul: the tablet home screen, app editor, and item
  picker are rebuilt in the Stock Keeper's visual language — themed
  canvas inside wooden rail frames, parchment plaques for tiles, rows,
  and titles, chamfered banner buttons, and inset ink-well text fields.
  Every layout, click target, and drag works exactly as before; the
  placed tablet's in-world screen is untouched.
- New theme: "Parchment" — cream surfaces, dark-brown text, and a
  Create-blue accent, made for the new chrome.
- Sliders now show their numeric signal level everywhere they render:
  GUI list rows and both placed-screen layouts join the grid tile's
  readout (bars and knobs track the slider's range; the number is always
  the absolute 0–15 level).
- Sliders can be limited to a min/max range: in the app editor, the
  slider type's strength row becomes a "Range" row with two draggable
  knobs. The slider then travels only between those levels — a minimum
  above 0 means the signal never fully turns off (set the minimum to 0
  to keep an off position). Existing sliders keep the full 0–15 range.
- Note for servers: the network protocol version changed — 1.5.0 clients
  and servers don't pair with 1.4.0.

## 1.4.0 — 2026-07-16

- Frequencies now carry the full item — including its data components —
  so component-based frequency items (Create Unique Cards, Frequency
  Create cards, dyed items, renamed items with dyed color) work as
  distinct channels, exactly matching what Create's own Redstone Links
  distinguish. Old tablets load seamlessly and upgrade on save.
- New app type: Slider. The transmitted signal follows the slider
  position, 0 (off) to 15 — drag it live on the tablet GUI (grid tiles
  get a value bar, list rows a wide track). On a placed tablet's screen,
  click-and-slide: hold right-click on the slider's tile and sweep your
  crosshair — the value tracks the crosshair exactly and keeps following
  even past the tablet's edge. Pick the type in the app editor (click
  the Type row to cycle Toggle → Hold → Slider); the strength slider
  becomes the initial value and allows 0.
- The app editor now includes your real inventory with vanilla drag
  mechanics: pick an item up and drop it on the red/blue frequency slots
  (they take a copy — nothing is consumed), or shift-click it in.
  Components ride along, so frequency cards work naturally. The
  all-items search picker is still there as an overlay for items you
  don't carry.
- Create wrench support on placed tablets: wrench the screen glass to
  rotate the screen content 90° — tiles, labels, and tap-to-toggle all
  follow. Wrench the bezel (or any edge) to rotate the tablet itself:
  floor/ceiling tablets spin on their support, wall tablets physically
  flip portrait ↔ landscape. Sneak+wrench picks the tablet up with
  everything intact.
- Wash a dyed case back to default: dunk the tablet in a water cauldron,
  or toss it into an encased fan's washing stream (apps, theme, and
  everything else survive — only the dye comes off).
- App names no longer get cut off: GUI labels ellipsize (hover a tile for
  the full name) and the placed screen's labels shrink to fit before
  truncating.
- Fixed: typing in the app editor's name field could trigger the
  inventory key — an 'e' closed the menu mid-word and the rest of the
  keystrokes moved the player. Text fields now own the keyboard while
  focused (ESC still exits).
- Note for servers: the network protocol version changed — 1.4.0 clients
  and servers must both be updated.

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
