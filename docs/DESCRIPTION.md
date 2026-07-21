# Create: Redstone Link Tablet — listing description

> Paste everything below the line into the Modrinth description (Markdown) and the
> CurseForge description editor. The `<!-- 📸 SCREENSHOT: ... -->` comments are
> placeholder slots: they render as nothing until a real image is dropped in, and
> each one describes the shot that belongs there. The full shooting checklist is
> at the bottom of this file (not part of the description).

---

**A smart-home control panel for your Create contraptions.**

The **Link Tablet** is a handheld device with an app-style touchscreen that drives
[Create](https://modrinth.com/mod/create)'s Redstone Links remotely. Lights, doors,
trains, farms, the entire factory — name them, give them an icon, and control them
from one screen, anywhere in link range. Mount tablets side by side and they merge
into one **wall-sized display**; pin a **mini-tablet to your HUD** and flip switches
without ever opening a GUI.

![The tablet GUI over a floating-island vista — a slider, a timer, and toggle apps in the list, two sticky-note windows pinned beside it, and a placed tablet in the corner](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/hero4.png)

## Apps, not levers

- **Up to 32 apps per tablet** (and 32 more per tablet on merged multi-tablet
  screens), each with a name, color, an optional item icon, and its own state —
  shown as an icon grid or a list of toggle switches, with drag-to-rearrange in
  both layouts.
- **Four app types**, cycled right in the editor:
  - **Toggle** — classic on/off with a persistent state.
  - **Hold** — transmits only while you hold the button. Doorbells, dispensers,
    one-shot pulses — a disconnect can never leave the signal stuck on.
  - **Slider** — the signal follows the slider, 0 to 15. Dim lights, throttle
    trains, meter item flow.
  - **Timer** — tap it and it transmits for a set time (0.1 s to 30 s), then
    switches off by itself. Tap again to restart the clock.
- **Scenes** — one app can drive up to 8 frequencies at once ("Shut down the
  factory", "Open all doors").
- **Per-app signal strength** — when several apps drive the same frequency, the
  strongest wins, exactly like stacked Redstone Links.

You can see all of them working in the screenshot above — a slider mid-drag, a
timer, and toggles side by side.

## Real Create frequencies

Apps tune to Create's item-pair frequencies — anything a Redstone Link or Linked
Controller can trigger, the tablet can too:

- The app editor includes **your real inventory** with vanilla drag mechanics:
  drop an item on the frequency slots (they take a copy — nothing is consumed),
  or use the searchable all-items picker for things you don't carry.
- Running **JEI or EMI**? Drag an item straight from the ingredient panel onto
  a frequency slot — no need to own it at all.
- **One-item frequencies** work, matching Create's own links.
- Frequencies carry the **full item, components included** — frequency-card mods
  (Create Unique Cards and friends), dyed items, and renamed gear all count as
  distinct channels, exactly matching what Create's links distinguish.
- **Quick-add:** right-click any Redstone Link (or anything with a link
  frequency, like elevator contacts) while holding the tablet — the editor opens
  with that frequency pre-filled and a name suggested. Just hit Save.

## Sliders you can feel

Sliders drag live in the GUI, and on a placed tablet you **click-and-slide right
on the glass**: hold the button on a slider's tile and sweep your crosshair — the
value tracks it exactly. Every slider shows its numeric signal level wherever it
renders, and you can limit one to a min/max range — set a minimum above 0 and
that machine never fully turns off.

## Sticky notes for your factory

Every app can carry a free-text note, opened from a little glyph on its tile.
Notes are **floating windows**: drag them by the title bar, open several at once,
and they stay with you — over the tablet, over your inventory and other screens
(fully editable there), and pinned read-only on your HUD while you play. They
politely stay out of settings menus and other screens where they don't belong.
Perfect for "flush the sorter before enabling" warnings or a train schedule —
you can see two of them pinned in the screenshot up top.

## Pin it to your HUD

The pin button in the tablet GUI keeps a **floating mini-tablet on your screen
while you play** — live app rows with real switches, draggable anywhere, and it
remembers its spot and pin across sessions. Press the **"Use Pinned Tablet" key
(default B)** to free your mouse chat-style and tap toggles, drag sliders, or
hold buttons — mid-mining, mid-flight, no GUI ever opens. It's also clickable
with the normal cursor over your inventory or chat, and a right-click on any
row jumps into the full tablet interface. (There's an "Open Tablet" keybind
too, unbound by default, that opens the tablet from anywhere in your
inventory.)

Pin a placed tablet and it dims when you wander out of edit range; pin a
carried one and it follows the item around your inventory.

<!-- 📸 SCREENSHOT: the pinned mini-tablet on the HUD during gameplay — mining or riding a train, a slider mid-drag, hotbar visible so it reads as "playing, not in a menu" -->

## The screen is real

The tablet's physical screen isn't a texture — it renders your actual apps,
live, on both the held and the placed tablet. Tiles size themselves to the app
count (one app fills the whole glass; a full tablet converges on a dense grid),
active apps glow, timers glow for as long as their pulse runs, and each tablet
remembers whether you last used it in grid or list view.

And on a placed tablet, **the screen is touchable**: tap an app right on the
glass to toggle it, hold a Hold button, slide a slider — no GUI needed. Tap the
bezel for the full interface.

## Place it on the wall

Sneak + right-click any surface to mount the tablet on a wall, floor, or
ceiling — it becomes a block that transmits from its own position while the
chunk is loaded, and its screen glows whenever an app is running. Sneak +
right-click with an empty hand picks it back up; everything survives the trip.

Got a Create wrench? Wrench the glass to rotate the screen content 90°, wrench
the edge to spin the tablet — wall tablets physically flip between portrait and
landscape.

![A shaded Create factory hall with a wall-mounted tablet showing its live switch list](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/mounted-factory2.png)

## One screen, many tablets

Mount tablets side by side — same wall, floor, or ceiling; new ones
auto-match their neighbor's orientation — and they **merge into one big
display, up to 4 wide by 3 tall**: a single continuous glass panel with a
case-colored bezel frame and no seams. Your apps spread across the whole
surface at bigger tiles, taps and click-and-slide work anywhere on the
glass, list rows run its full width, the entire wall lights up together —
and every merged tablet raises the app cap by another 32. Dye the lead
tablet and the whole frame dyes; wrench the glass and the whole screen's
content rotates.

Merging is always safe: every tablet keeps its own apps dormant and gets
them back the moment it's split off. And it's always your choice — a
**chain-link button** in the screen header breaks a merged surface back
into independent tablets, or keeps a single tablet standalone so you can
build a bank of separate dashboards side by side.

<!-- 📸 SCREENSHOT: a merged tablet wall (3×2 or 4×3) in a factory setting, dyed bezel, a good mix of app tiles — the new hero candidate; bonus if a hand is mid-tap on the glass -->

## Make it yours

- **8 UI themes** — Dark, Light, AMOLED, Brass, Terminal, PurpleFox, Parchment,
  and Avionics — stored per tablet, recoloring the GUI and the physical screen
  for everyone. The whole interface is styled in Create's own visual language:
  wooden rails, parchment plaques, beveled buttons.
- **16 dyed cases** — craft the tablet with any dye to recolor it, apps
  preserved, re-dye whenever. Dunk it in a water cauldron (or an encased fan's
  washing stream) to strip the dye again.
- **A full set of UI sounds** — toggle clicks with distinct on/off pitch, a save
  chime, frequency ticks, and a faint click nearby players hear when someone
  flips an app.

![The tablet GUI in four themes — Dark, Light, Parchment, and Avionics](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/themes.png)

![All 16 dyed tablet cases mounted on a wooden wall, several screens lit](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/dyed-cases2.png)

## Getting started

Craft the tablet from brass-age Create parts — a Linked Controller rehoused in a
brass shell with a proper screen and a signal amplifier (it's in the vanilla
Redstone creative tab too):

![Crafting recipe: brass sheets, tinted glass, redstone links, electron tube around a linked controller](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/recipe.png)

| | | |
|---|---|---|
| Brass Sheet | Tinted Glass | Brass Sheet |
| Redstone Link | Linked Controller | Redstone Link |
| Brass Sheet | Electron Tube | Brass Sheet |

Then:

1. **Right-click** to open the tablet, tap **+** to create an app.
2. Pick the frequency items (exactly like tuning a Redstone Link) and hit
   **Add** — repeat to stack a scene — then **Save**.
3. Set your receiving Redstone Links to the same frequency.
4. **Left-click toggles, right-click edits.**

There's also a **Ponder scene** — hold W over the tablet item — that walks
through mounting, linking, and the quick-add shortcut.

While an app is on and the tablet is anywhere in your inventory, you are the
transmitter — range and chunk behavior match Create's handheld Linked
Controller. Mounted tablets transmit from the block instead.

## Compatibility

- **Minecraft 1.21.1 · NeoForge · Java 21 · requires Create 6.x** (server and client).
- Full multiplayer support: server-authoritative edits, per-player transmitters.
- Plays nicely with other link-network senders — receivers take the strongest
  signal, like stacked Redstone Links.
- Frequency-card mods (component-based frequency items) work out of the box.
- Optional **JEI and EMI** integration: drag ingredients straight into the
  frequency slots.
- MIT licensed, free to use in any modpack.

Source, issues, and changelog on [GitHub](https://github.com/FryD420/redstone-link-tablet).

---

## Shooting checklist (not part of the description)

### 1.7.0 refresh — TO SHOOT (text is done, slots are placeholders above)

- ⬜ **multiblock wall** — a merged surface (3×2 or 4×3) in a factory
  setting: dyed bezel, good tile variety, ideally mid-tap on the glass.
  This is the flashiest shot the mod has ever had — strong candidate to
  REPLACE hero4 as the listing hero / social preview once shot.
- ⬜ **pinned overlay** — the mini-tablet on the HUD during real gameplay
  (mining / riding a train), slider mid-drag, hotbar visible so it clearly
  isn't a GUI screenshot.
- Existing 1.6.0 shots all stay valid. When shot: drop files in
  `docs/images/`, swap each `<!-- 📸 SCREENSHOT -->` comment for the image
  embed (raw.githubusercontent URL), push, re-paste into both listings.
  The description TEXT can go up before the shots — the comment slots
  render as nothing.

### 1.6.0 shoot — done (2026-07-19, FryD42 + wife), embedded above:

- ✅ **hero4.png** — GUI + floating notes over the island vista; the list shows
  a slider (with numeric readout), a timer, a hold app, and an active toggle,
  so it covers the notes AND gui-home slots too. (`hero3.png` was the earlier
  take without the app-type variety — prune it.)
- ✅ **mounted-factory2.png** — shaded factory hall, wall tablet in list mode.
- ✅ **themes.png** — 2×2 stitch of Dark/Light/Parchment/Avionics (sources:
  `theme1–4.png`, same framing, GUI crop at 705,325 510×440 — safe to delete
  the four raws once happy with the stitch).
- ✅ **dyed-cases2.png** — 16-color wall with lit screens.
- ✅ **recipe.png** — unchanged since 1.1.1, reused (3× upscale crop of
  `recipe-full.png`).

Refresh COMPLETE (2026-07-19): every slot filled (a click-and-slide action
shot was considered and skipped; the text covers it), description pasted to
both listings, galleries + restyled icon uploaded, superseded images pruned.
`recipe-full.png` and `icon-bg.png` stay — they're sources for `recipe.png`
and the iconTool. For the next refresh: shoot, drop files in `docs/images/`,
embed at
`https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/<file>.png`,
push, then paste everything below the divider into both listing editors.
