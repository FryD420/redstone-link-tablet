# Create: Redstone Link Tablet — listing description

> Paste everything below the line into the Modrinth description (Markdown) and the
> CurseForge description editor. The `<!-- 📸 SCREENSHOT: ... -->` comments are
> placeholder slots: they render as nothing until a real image is dropped in, and
> each one describes the shot that belongs there. The full shooting checklist is
> at the bottom of this file (not part of the description).

---

<p align="center"><img src="https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/banner.png" alt="Create: Redstone Link Tablet"></p>

<p align="center"><img src="https://github.com/intergrav/devins-badges/blob/v3/assets/cozy/supported/neoforge_64h.png?raw=true" alt="Available for NeoForge" height="56">&nbsp;&nbsp;<a href="https://www.curseforge.com/minecraft/mc-mods/create"><img src="https://github.com/intergrav/devins-badges/blob/v3/assets/cozy/requires/create_64h.png?raw=true" alt="Requires Create" height="56"></a>&nbsp;&nbsp;<a href="https://discord.gg/JwkHGpE527"><img src="https://github.com/intergrav/devins-badges/blob/v3/assets/cozy/social/discord-singular_64h.png?raw=true" alt="Join the Discord" height="56"></a>&nbsp;&nbsp;<a href="https://streamelements.com/fryd42/tip"><img src="https://github.com/intergrav/devins-badges/blob/v3/assets/cozy/donate/generic-singular_64h.png?raw=true" alt="Donate" height="56"></a></p>

---

**A smart-home control panel for your Create contraptions.**

The **Link Tablet** is a handheld touchscreen that drives
[Create](https://modrinth.com/mod/create)'s Redstone Links remotely. Name your
lights, doors, trains, and machines, give them icons, and flip them from one
screen anywhere in link range. It boots to a **Home screen of apps** — signals,
gauges, a clock, a **live network monitor**, even **Twitch chat** and a **paint
canvas** — mounts on walls where tablets **merge into one big display**, and
pins a **mini-tablet to your HUD**.

![The tablet GUI over a floating-island vista — a slider, a timer, and toggle signals in the list, two sticky-note windows pinned beside it, and a placed tablet in the corner](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/hero4.png)

---

## 📱 A pocket OS

The tablet opens to a **Home screen** you arrange yourself; the built-in
**App Store** (searchable) installs and removes apps per tablet:

- **Signals** — the control grid below; every tablet starts with it.
- **Clock** — alarms, a searchable world clock, timer, stopwatch. They ring
  even with the tablet closed; a placed Clock tablet is a **wall clock**.
- **Calculator** — click buttons or type; a placed tablet shows its
  **live tape on the wall**.
- **Gauges** — the reverse of Signals: **live dials that listen** on link
  frequencies and show the received 0–15. Merged walls make great
  **factory dashboards**.
- **Monitor** — an **X-ray for your link network** (below).
- **Twitch Chat** — live stream chat, emotes included, no account (below).
- **Paint** — a persistent pixel canvas that shows on walls (below).

Placed tablets show their app right on the glass — tap to open it, **tap the
bezel to go Home** — and the wall follows your GUI navigation.

<!-- 📸 SCREENSHOT (optional, 1.10.0): the Home screen GUI next to a placed wall-clock tablet and a gauges wall — "it's an OS" in one image -->

---

## 🎛️ Signals, not levers

- **Up to 32 signals per tablet** (+32 per tablet on merged walls), each with a
  name, color, item icon, and state — grid or list view, drag to rearrange.
- **Four types**: **Toggle** (on/off), **Hold** (transmits only while held — a
  disconnect can never leave it stuck on), **Slider** (0–15), **Timer**
  (transmits for 0.1–30 s, then switches off by itself).
- **Scenes** — one signal drives up to 8 frequencies at once ("Shut down the
  factory").
- **Signal links** — signals drive each other: up to 8 targets each, set to
  *turn ON*, *turn OFF*, or *follow*. Links chain, loops are safe, and a linked
  signal needs no frequency of its own — pure **scene master buttons**.
- **Per-signal strength** — strongest wins, exactly like stacked Redstone
  Links.

---

## 📡 Real Create frequencies

Signals tune to Create's item-pair frequencies — anything a Redstone Link can
trigger, the tablet can too:

- The editor includes **your real inventory** (slots take a copy — nothing is
  consumed) plus a searchable all-items picker.
- **JEI/EMI**: drag straight from the ingredient panel onto a frequency slot.
- One-item frequencies work, and frequencies carry the **full item, components
  included** — frequency-card mods, dyed and renamed items all count as
  distinct channels, exactly like Create's own links.

> 📋 **Tip — quick-add:** right-click any Redstone Link while holding the
> tablet — the editor opens with that frequency pre-filled. Just hit Save.

---

## 🔎 Who's on this frequency?

The **Monitor** app lists every channel your tablet touches — plus any you
**probe** (up to 8) — with live power and **every member transmitting or
listening**: Redstone Links, placed tablets, even *"a tablet in someone's
inventory"*. When a receiver is mysteriously pinned at 15, the Monitor points
at the spare tablet in your pocket. Out-of-range members wear a badge; wall
tablets make it a **status board**, and the overlay pin puts it on your HUD.

![The Frequency Monitor GUI open in a working assembly hall — two channels with classified member rows (a placed tablet and a Redstone Link), while the overlay pin mirrors the same channels on the HUD](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/monitor.png)

![A Create assembly hall with a wall tablet showing the Monitor's live channel bars as a status board](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/factory-hall.png)

---

## 🎚️ Sliders you can feel

Sliders drag live in the GUI, and on a placed tablet you **click-and-slide
right on the glass**. Every slider shows its numeric level, and an optional
min/max range means a machine never fully turns off.

---

## 📝 Sticky notes for your factory

Every signal can carry a note in a **floating window**: drag it anywhere, keep
several open, and they follow you — over the tablet, over your inventory, even
pinned read-only on your HUD. Perfect for "flush the sorter before enabling"
warnings — two of them are pinned in the screenshot up top.

---

## 📌 Pin it to your HUD

The pin button keeps a **floating mini-tablet on your screen while you play** —
per app: live switch rows, a heads-up gauge cluster, the clock, a working
mini calculator, or the launcher as an app dock. Press the **"Use Pinned
Tablet" key (default B)** to free your mouse chat-style and tap toggles, drag
sliders, or hold buttons mid-mining — no GUI ever opens. Right-click any row
to jump into the full interface; the window remembers its spot across
sessions.

<!-- 📸 SCREENSHOT: the pinned mini-tablet on the HUD during gameplay — mining or riding a train, a slider mid-drag, hotbar visible so it reads as "playing, not in a menu" -->

---

## ✨ The screen is real

The physical screen isn't a texture — it renders your actual signals live,
held or placed. Tiles size themselves to the signal count, active signals
glow, and on a placed tablet **the glass is touchable**: tap to toggle, hold a
Hold button, slide a slider — no GUI needed. Tap the bezel for the full
interface.

---

## 🧱 Place it on the wall

Sneak + right-click any surface to mount the tablet on a wall, floor, or
ceiling — it transmits from its own position and its screen glows while a
signal runs. Sneak + right-click with an empty hand picks it back up;
everything survives the trip. A Create wrench rotates the screen content
(glass) or physically flips the tablet between portrait and landscape (edge).

![A shaded Create factory hall with a wall-mounted tablet showing its live switch list](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/mounted-factory2.png)

---

## 🎯 Aim it anywhere

Craft a **Swivel Mount** — an iron nugget atop two brass sheets — place the
stand on any surface, and click a tablet onto its ball joint. Wrench the glass
and it **swings around to aim straight at your eyes**, wherever you're
standing — one click, perfect angle. Wrench the bezel for landscape; sneak +
right-click swaps tablets and **leaves the stand as furniture**. The tilted
glass stays fully touchable.

And the party trick: **power the mount with redstone and the screen follows
you**, gliding after the nearest player like the enchanting table's book. Cut
the power and it freezes exactly where it points — aim by standing in the
right spot, lock with a lever.

<!-- 📸 SCREENSHOT (optional, 1.10.0): follow mode — ideally a short GIF of a powered swivel tablet tracking the player past a console; a still with lever + angled tablet works too -->

---

## 🖥️ One screen, many tablets

Mount tablets side by side and they **merge into one big display, up to
4 wide by 3 tall**: a single continuous glass panel, no seams. Signals spread
across the whole surface at bigger tiles, taps and click-and-slide work
anywhere on the glass — and every merged tablet raises the signal cap by
another 32. Dye the lead tablet and the whole frame dyes.

<p align="center"><strong style="color:#f2c94c; font-size:1.3em;">🧮 A 4×3 wall = 12 merged tablets — 384 signals on one screen</strong></p>

Merging is always safe — every tablet keeps its own signals and gets them back
the moment it's split off — and always your choice: a **chain-link button**
keeps a tablet standalone or breaks a wall back apart.

<!-- 📸 SCREENSHOT: a merged tablet wall (3×2 or 4×3) in a factory setting, dyed bezel, a good mix of signal tiles — the new hero candidate; bonus if a hand is mid-tap on the glass -->

---

## 💬 Your stream chat, in the game

Type a channel name and its live chat flows onto your held tablet, your HUD,
or a placed **chat wall** every viewer sees — merged walls give it the
big-screen treatment. **No account, no login, nothing stored**: an anonymous,
strictly read-only view of public chat, connected only while chat is actually
on a screen somewhere.

And it's real chat: **emotes render as actual images** — Twitch's own plus
**7TV, BetterTTV, and FrankerFaceZ**, animated ones animating (a header toggle
switches back to plain text). Keep Twitch as your last-used app and **the
tablet in your hand becomes a pocket chat screen**, even in third person.

Shared-server note: chat is live, unfiltered internet, and anyone who can open
a placed tablet's GUI can change its channel — same trust rules as its
signals.

![A cozy cabin room with a merged tablet wall streaming live Twitch chat — colored usernames scrolling down the glass while a player sits watching](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/chat-wall.png)

---

## 🖌️ Paint on the walls

A pixel canvas that lives on the tablet: doodle, place it, and the picture
**shows right on the glass**. Merged walls become **murals** — paint across
the seams while viewers watch your strokes land live, split the wall and each
tablet keeps its slice, merge back and the mural reassembles. Break it, chest
it, gift it — the art travels with the tablet.

![A 3×2 merged tablet wall outside a workshop showing a hand-painted sunrise mural](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/mural.png)

![The same wall with its member tablets rearranged — the sunrise mural remixed into a new picture](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/mural-remix.png)

*Every tablet keeps its slice — rearrange the wall, remix the painting.*

---

## 🎨 Make it yours

- **8 UI themes** — Dark, Light, AMOLED, Brass, Terminal, PurpleFox, Parchment,
  Avionics — stored per tablet, styling GUI and glass alike in Create's own
  visual language.
- **16 dyed cases** — craft with any dye; wash it off in a cauldron or a fan's
  washing stream.
- **A full set of UI sounds**, including a faint click nearby players hear when
  someone flips a signal.
- **Name your tablets** on an anvil — the name titles the GUI and overlay and
  survives placing and picking up.

![The tablet GUI in four themes — Dark, Light, Parchment, and Avionics](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/themes.png)

![All 16 dyed tablet cases mounted on a wooden wall, several screens lit](https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/dyed-cases2.png)

---

## 🏭 Getting started

The tablet comes off a real assembly line — brass-age manufacturing, not
crafting. Shape a **Tablet Case** from five brass sheets, then send it down a
belt past three Deployers: the **Link Logic Board** (the radio), the
**Clockwork Cell** (the power), and the **Quartz Display** (the screen). The
in-progress tablet visibly gains each part, and JEI/EMI shows the whole
sequence.

<!-- 📸 SCREENSHOT: the assembly line in action — belt with three deployers, an in-progress tablet mid-line showing the seated board/cell, finished tablet coming off the end -->

Then:

1. **Right-click** to open the tablet, tap **+** to create a signal.
2. Pick the frequency items (like tuning a Redstone Link), **Add**, **Save**.
3. Set your receiving Redstone Links to the same frequency.
4. **Left-click toggles, right-click edits.**

There's a **Ponder scene** too — hold W over the tablet item. A tablet
anywhere in your inventory transmits like Create's handheld Linked Controller;
placed tablets transmit from the block.

---

## 📦 Compatibility

- **Minecraft 1.21.1 · NeoForge · Java 21 · requires Create 6.x** (server and client).
- Full multiplayer support: server-authoritative edits, per-player transmitters.
- Plays nicely with other link-network senders; frequency-card mods work out of
  the box; optional **JEI/EMI** drag integration.
- **Twitch Chat is client-side and optional** — anonymous and read-only,
  connected only while chat is on a screen; nothing sent, nothing stored, and
  it does nothing at all on the server side.
- MIT licensed, free to use in any modpack.

---

## ❤️ Credits

Huge thanks to the [Create](https://www.curseforge.com/minecraft/mc-mods/create) team — this addon stands on their Redstone Link network and visual language. Go support the original mod!

Banner lettering uses the community [Minecraft Ten](https://modrinth.com/resourcepack/mc10) font art by xllifi (CC-BY 4.0).

Enjoying the tablet? You can [drop a tip](https://streamelements.com/fryd42/tip) ☕ — thank you!

---

Created by **FryD42** · **MIT licensed** — free to use in any modpack.

---

## Shooting checklist (not part of the description)

NOTE (2026-07-29, user decision): the listing description EXCLUDES GitHub —
no source/issues/changelog link in the paste content. The raw.githubusercontent
image embeds stay (that's just hosting, invisible in the rendered page).
The ADDON-API developer bullet was removed entirely (same decision — its only
docs link was GitHub); the API is announced to addon authors elsewhere.

### Banner (2026-07-29, Claude-generated — no shoot needed)

- ✅ **banner.png** — Create-addon-style logo banner (Minecraft Ten lettering,
  GUI + tablet-back flanks, blurred game-tablet wall). Regenerate after edits
  to `docs/banner.html` with:
  `chrome --headless --screenshot=docs/images/banner.png --window-size=1200,436 --default-background-color=00000000 --hide-scrollbars --virtual-time-budget=8000 --allow-file-access-from-files docs/banner.html`
  (`docs/banner-font-ascii.png` is the Minecraft Ten sheet, CC-BY 4.0 xllifi —
  keep the Credits line if the lettering stays.) NOTE: the listing embed serves
  from the `main` branch — **push banner.png to main BEFORE re-pasting the
  listings** or the image 404s.

### 1.11.0 additions — SHOT 2026-08-10 (FryD42, SMP shoot), all three
### slots embedded above:

- ✅ **monitor.png** — Monitor GUI + overlay pin in the assembly hall
  (DoF blur sells the GUI). Bonus ✅ **factory-hall.png** — wide hall
  shot with the Monitor wall face as a status board, embedded beside it.
  (Skipped garnishes: inventory-tablet row, out-of-range badge.)
- ✅ **chat-wall.png** — cozy cabin wall, player seated watching; chose
  vibe framing over the "beside a signals wall" spec. A close-up with
  legible emotes exists (screenshots 2026-08-10_11.42.08) but carries
  HUD clutter AND borderline live-chat text — NEVER embed unvetted
  chat close-ups; live chat must be illegible or manually checked.
- ✅ **mural.png** + **mural-remix.png** — mural pair with the remix
  caption. The remix REPLACES the "held slice" framing from the old
  slot text: held tablets render pips, not paint (item renderer is
  signals/Twitch-only) — that framing was unshootable as written.

### 1.10.0 additions — (superseded header kept for history) two OPTIONAL
### slots still queued:

- ⬜ **pocket OS** (optional) — Home screen GUI beside a placed wall-clock
  tablet and a gauges wall; one image that says "it's an OS".
- ⬜ **follow mode** (optional) — GIF of a powered swivel tablet tracking the
  player past a console (a still with a lever works if GIFs are a pain).

### 1.7.0 + 1.8.0 refresh — TO SHOOT (slots are placeholders above)

- ⬜ **multiblock wall** — a merged surface (3×2 or 4×3) in a factory
  setting: dyed bezel, good tile variety, ideally mid-tap on the glass.
  This is the flashiest shot the mod has ever had — strong candidate to
  REPLACE hero4 as the listing hero / social preview once shot.
  BONUS: a swivel-mounted tablet angled in the foreground — that one
  shot then covers the "Aim it anywhere" section too (no dedicated
  mount slot; add one later only if the foreground doesn't read).
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
  a slider (with numeric readout), a timer, a hold signal, and an active toggle,
  so it covers the notes AND gui-home slots too. (`hero3.png` was the earlier
  take without the signal-type variety — prune it.)
- ✅ **mounted-factory2.png** — shaded factory hall, wall tablet in list mode.
- ✅ **themes.png** — 2×2 stitch of Dark/Light/Parchment/Avionics (sources:
  `theme1–4.png`, same framing, GUI crop at 705,325 510×440 — safe to delete
  the four raws once happy with the stitch).
- ✅ **dyed-cases2.png** — 16-color wall with lit screens.
- ❌ **recipe.png** — RETIRED with 1.9.0 (the crafting recipe it showed no
  longer exists; embed removed from the text 2026-07-25). Its slot is now the
  assembly-line 📸 comment in "Getting started" — shoot the belt + three
  deployers with an in-progress tablet mid-line. Prune `recipe.png` and
  `recipe-full.png` from the listings/`docs/images/` once the new shot lands.

Refresh COMPLETE (2026-07-19): every slot filled (a click-and-slide action
shot was considered and skipped; the text covers it), description pasted to
both listings, galleries + restyled icon uploaded, superseded images pruned.
`icon-bg.png` stays — it's a source for the iconTool. For the next refresh: shoot, drop files in `docs/images/`,
embed at
`https://raw.githubusercontent.com/FryD420/redstone-link-tablet/main/docs/images/<file>.png`,
push, then paste everything below the divider into both listing editors.
