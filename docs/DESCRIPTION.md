# Create: Redstone Link Tablet — listing description

> Paste everything below the line into the Modrinth description (Markdown) and the
> CurseForge description editor. Image URLs point at docs/images/ on the main branch,
> so they resolve once this commit is pushed.

---

**A smart-home control panel for your Create contraptions.**

The **Link Tablet** is a handheld device with an app-style touchscreen that toggles
[Create](https://modrinth.com/mod/create)'s Redstone Links remotely. Lights, doors,
trains, farms, the entire factory — name them, give them an icon, and flick them
on and off from one screen, anywhere in link range.

![Two wall-mounted tablets with live screens — switch list and icon grid — glowing at night](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/hero2.png)

## Apps, not levers

- **Up to 32 apps per tablet** — each with a name, color, an optional item icon,
  and a persistent on/off state.
- **Scenes** — one app can drive up to 8 frequencies at once ("Shut down the
  factory", "Open all doors").
- **Momentary apps** — mark an app as momentary and it only transmits while you
  hold the button. Doorbells, dispensers, one-shot pulses — and a disconnect can
  never leave the signal stuck on.
- **Per-app signal strength (1–15)** — when several apps drive the same frequency,
  the strongest wins, exactly like stacked Redstone Links.
- **Two layouts** — icon grid or list with toggle switches, switchable per player.
- **Drag to rearrange** — a rearrange mode in the tablet GUI lets you drag apps
  into any order, with live reflow, in both layouts.
- **Real Create frequencies** — apps use Create's two-item frequency system with a
  searchable in-GUI picker (no physical items consumed). Anything a Redstone Link
  or Linked Controller can trigger, the tablet can too.

## Place it on the wall

Sneak + right-click any surface to mount the tablet on a wall, floor, or ceiling —
it becomes a block that transmits from its own position while the chunk is loaded,
and its screen glows (light level 7) whenever an app is running. Sneak + right-click
with an empty hand to pick it back up; apps and case color survive the trip.

![A wall-mounted tablet with its screen lit, overseeing a Create factory floor](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/mounted-factory.png)

## The screen is real

The tablet's physical screen isn't a texture — it renders your actual apps, live,
on both the held tablet and the placed one. An icon grid of up to 20 apps (their
real item icons, glowing when active), or mini toggle-switch rows if you last used
that tablet in list view — each tablet remembers its own layout.

And on a placed tablet, **the screen is touchable**: click an app right on the
glass to toggle it, no GUI needed. Tap the bezel for the full interface.

## Make it yours

Craft the tablet together with any dye to recolor its case — all 16 colors, apps
preserved, re-dye whenever. Plus a full set of UI sounds: toggle clicks with
distinct on/off pitch, a save chime, frequency ticks, and a faint click nearby
players can hear when someone flips an app.

![All 16 dyed tablet cases mounted on a wall, some screens glowing](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/dyed-cases.png)

## Getting started

Craft the tablet from brass-age Create parts — a Linked Controller rehoused in a
brass shell with a proper screen and a signal amplifier:

![Crafting recipe: brass sheets, tinted glass, redstone links, electron tube around a linked controller](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/recipe.png)

| | | |
|---|---|---|
| Brass Sheet | Tinted Glass | Brass Sheet |
| Redstone Link | Linked Controller | Redstone Link |
| Brass Sheet | Electron Tube | Brass Sheet |

Then:

1. **Right-click** to open the tablet, tap **+** to create an app.
2. Pick two items as the frequency (exactly like tuning a Redstone Link) and hit
   **Add** — repeat to stack a scene — then **Save**.
3. Set your receiving Redstone Links to the same frequency.
4. **Left-click toggles, right-click edits.**

While an app is on and the tablet is anywhere in your inventory, you are the
transmitter — range and chunk behavior match Create's handheld Linked Controller.
Mounted tablets transmit from the block instead.

## Compatibility

- **Minecraft 1.21.1 · NeoForge · Java 21 · requires Create 6.x** (server and client).
- Full multiplayer support: server-authoritative edits, per-player transmitters.
- Plays nicely with other link-network senders — receivers take the strongest
  signal, like stacked Redstone Links.
- MIT licensed, free to use in any modpack.

Source, issues, and changelog on [GitHub](https://github.com/FryD420/redstone-link-tablet).

---

## Image files (not part of the description)

All in `docs/images/`: `hero.png` (GUI, list view, shaders), `mounted-factory.png`
(placed tablet in a factory), `dyed-cases.png` (16-color wall), `recipe.png`
(cropped + 3× upscale from `recipe-full.png`).

Also upload the hero and factory shots to each site's Gallery tab (hero first —
it becomes the social preview).
