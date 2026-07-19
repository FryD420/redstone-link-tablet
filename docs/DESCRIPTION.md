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
from one screen, anywhere in link range.

![The tablet GUI over a floating-island vista — a slider, a timer, and toggle apps in the list, two sticky-note windows pinned beside it, and a placed tablet in the corner](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/hero4.png)

## Apps, not levers

- **Up to 32 apps per tablet**, each with a name, color, an optional item icon,
  and its own state — shown as an icon grid or a list of toggle switches, with
  drag-to-rearrange in both layouts.
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
and they stay with you — over the tablet, over your inventory or any other
screen (fully editable there), and pinned read-only on your HUD while you play.
Perfect for "flush the sorter before enabling" warnings or a train schedule —
you can see two of them pinned in the screenshot up top.

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
- MIT licensed, free to use in any modpack.

Source, issues, and changelog on [GitHub](https://github.com/FryD420/redstone-link-tablet).

---

## Shooting checklist (not part of the description)

Shot 2026-07-19 by FryD42 + wife. Done and embedded above:

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

Shoot complete — every image slot is filled (a click-and-slide action shot was
considered and skipped; the text covers it). Remaining steps: commit + push so
the raw URLs resolve, paste the description into both listings, upload hero4 +
the factory shot to each site's Gallery tab (hero first — it becomes the
social preview), and re-upload the restyled `docs/icon.png`. Old images
(`hero.png`, `hero2.png`, `hero3.png`, `mounted-factory.png`,
`dyed-cases.png`, and the `theme1–4.png` raws) can be pruned once the listings
are updated.
