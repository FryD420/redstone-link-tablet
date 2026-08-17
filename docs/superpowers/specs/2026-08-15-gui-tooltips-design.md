# GUI hover tooltips — design

Date: 2026-08-15
Status: approved (design), pending implementation plan
Version target: 1.12.1 (client-only, PAIRS with 1.12.0 — no wire, no
registrar, no component, no NBT change of any kind)

## Goal

Every wordless clickable glyph in every Link Tablet program screen says
what it is on hover. Today only five screens have any tooltips at all,
and the mod is full of procedural glyphs (chain-link, padlock, solo,
eyedropper, emote toggle) whose meaning is guessable at best.

**One line, one name.** The tooltip says what the control is and nothing
else — the vanilla inventory popup, not a help system.

## Scope

**In**: Signals, Launcher, App Store, Arcade hub, Signal edit, Probe
edit, Gauges, Monitor, Paint, Twitch, Clock, Calculator, the three
picker overlays, and the two floating windows.

**Out**: the 19 arcade game screens (self-explanatory play surfaces;
hover text over a live play field gets in the way), and the pinned HUD
overlay panes (no cursor exists unless the focus screen is open, and
then the floating windows are the interactive surface, which IS in
scope).

**Also out — controls that already show their own name.** With a
single-line tooltip, a store row, arcade game row, theme popup row, or
named gauge tile would have a tooltip that merely repeats the text
already printed on it. Those get nothing. The pass targets glyphs,
chips, arrows, swatches, slots, and unlabelled boxes.

Signal tiles keep their EXISTING behaviour unchanged: a full-name
tooltip only when the printed name is ellipsized. Nothing added.

## 1. Architecture — `client/screen/ScreenTips`

One new class. A per-frame collector, not a widget system.

```
ScreenTips.add(x, y, w, h, "gui.linktablet.view.grid")   // while drawing
ScreenTips.draw(graphics, font, mouseX, mouseY)          // end of render
```

- A screen calls `add` at the moment it draws a clickable thing, passing
  the rect it already computed for drawing. No new geometry is derived
  anywhere, so no rect math is duplicated or can drift.
- `draw` finds the **last-registered** rect containing the cursor and
  renders it, then clears the collector. Last wins, so a popup or modal
  drawn over a header glyph takes priority for free — no z-index
  bookkeeping, and a control that stops being drawn automatically stops
  tipping.
- **`mouseClicked` is never touched.** Click behaviour on every screen is
  byte-identical after this change. That is the whole point of picking a
  draw-time registry over unifying hit rects.

**Floating windows**: `MiniTabletWindow` and `NoteWindow` draw themselves
through `NoteWindows`' `ScreenEvent.Render.Post` pass, above whatever
screen is open. They register into the same collector and paint at the
end of that pass.

Last-wins does NOT cover this case on its own: the screen's render and
the windows' render are two separate paint calls in the same frame, so
both could draw and leave a screen tooltip stranded under a window.
`TabletScreen`'s existing `NoteWindows.anyContains(...) → return` guard
therefore **moves into** `ScreenTips.draw` rather than being deleted —
every screen inherits the yield-to-windows rule for free, and the window
pass calls a second entry point (`drawWindows`) that skips the guard
because that pass IS the windows.

## 2. Format

A single line: the control's name, drawn via
`graphics.renderTooltip(font, Component, mouseX, mouseY)` — the same call
vanilla uses for inventory items, so the box, border, instant
appearance, cursor-following, and screen-edge flipping are all vanilla
behaviour. This is the call the mod's five existing tooltips already
use; nothing about the look changes.

`SignalEditScreen` and `ProbeEditScreen` keep vanilla `setTooltip` on
their real `ChromeButton` widgets — that path already works, already
positions correctly, and already renders one line.

### Lang keys

- **Reuse existing keys** wherever one exists (`gui.linktablet.view.grid`,
  `.home`, `.theme.title`, `.lock.lock`, `.lock.unlock`, `.overlay.pin`,
  `.overlay.unpin`, `.note`, `.picker.search`, `.links.button.tooltip`,
  the four `.edit_signal.type.*` keys). No renames — nothing already
  translated breaks.
- New keys: `gui.linktablet.tip.<screen>.<control>`.
- Controls shared across screens (Home, Pin, Theme, Lock, Link, Grid,
  List) are defined ONCE and reused on all ten screens that draw them.

## 3. Coverage inventory

Shared header glyphs — defined once, drawn by Signals, Launcher, Store,
Arcade hub, Gauges, Monitor, Twitch, Clock, Calculator:

Home · Theme · Grid view · List view · Rearrange · Pin to overlay /
Unpin from overlay · Lock screen / Unlock screen · Link screen / Unlink
screen · Notes

**Signals** (`TabletScreen`) — header glyphs, plus the add-signal tile
("New signal"). **The chain-link glyph has NO tooltip today** — a gap
this pass closes.

**Launcher** — header glyphs, plus the store tile ("App Store").
Chain-link glyph untipped today; closed here too.

**App Store** — header glyphs, plus the search box ("Search").

**Arcade hub** — header glyphs only.

**Signal edit** — widgets: Add frequency, Search items, Icon, Links,
Save, Cancel, Delete. Procedural: frequency chip ("Remove frequency"),
colour button ("Colour"), colour popup swatch ("Colour"), type row (four
existing keys), strength track ("Signal strength"), slider range knobs
("Range"), pulse track ("Pulse length"), name box ("Name"), ghost slots
("Frequency item").

**Probe edit** — Search items (shared), Add probe, Cancel, ghost slots
(shared key).

**Gauges** — header glyphs, plus the empty tile ("New gauge"); editor
modal: name box ("Name"), two frequency slots (shared key), colour
swatch ("Colour"), Save, Delete.

**Monitor** — header glyphs, plus add-probe "+" ("Add probe") and the
probe-row remove cross ("Remove probe").

**Paint** — tool glyphs, each naming its **keyboard shortcut** inline
(currently undiscoverable, and it still fits one line): "Brush (B)",
"Fill (F)", "Line (L)", "Rectangle (R)", "Pick colour (I)", "Undo".
Plus the palette swatch ("Colour").

**Twitch** — header glyphs, plus the channel box ("Channel") and the
emote toggle ("Emotes on" / "Emotes off").

**Clock** — header glyphs, plus the four tabs (Alarm, Clock, Timer,
Stopwatch); alarm remove cross ("Remove alarm"), hour −/+ ("Hour"),
minute −/+ ("Minute"), Add alarm; add-zone row ("Add clock"); timer
−60/−10/+10/+60 ("Adjust timer"), Start, Cancel; stopwatch Start /
Pause, Reset.

**Pickers** — `PickerOverlay`: search box ("Search"), Clear.
`LinkPickerOverlay`: candidate row ("Cycle link mode").
`ZonePickerOverlay`: search box ("Search").

**Floating windows** — `MiniTabletWindow` title bar ("Drag to move");
`NoteWindow` title bar ("Drag to move") and close button ("Close").

## 4. Deliberate exemptions

Listed so they can be overruled rather than silently missed:

1. **Paint canvas cells** — a tooltip trailing the cursor while you draw
   would make the app unusable.
2. **Calculator keys** — the key face IS its meaning, digits and
   functions alike. The screen gets header glyphs only.
3. **Item grid cells in `PickerOverlay`** — they already show the vanilla
   item tooltip, which is strictly better than anything we'd write.
4. **Mini-window body rows** — the body delegates clicks to the pinned
   program's own content renderer; tipping through that indirection is a
   second design. Title bar only in v1.
5. **Click-outside-to-close dead zones** — not controls.
6. **The 19 game screens** — per scope.
7. **Anything that already prints its own name** — per scope.

## 5. Observation, not in scope

`PaintScreen` is the only program screen with **no Home glyph** — it
exits by ESC alone, while every sibling screen has one. Noted for a
future pass; this change does not add it.

## 6. Test matrix

- Hover every listed control: a one-line tooltip appears instantly,
  follows the cursor, and never clips off screen edges.
- State-dependent glyphs flip text correctly: pin/unpin, lock/unlock,
  link/unlink, emotes on/off, timer start/cancel, stopwatch start/pause.
- Priority: with the theme popup open, hovering a popup row does not tip
  the header glyph beneath it. Same for the Gauges editor modal and all
  three picker overlays.
- Floating windows: hover a window title bar while a tablet screen is
  open underneath — the window's tip shows and the screen's does not.
- Block-view-only glyphs (Lock, Link) tip on a placed tablet and are
  absent on a held one.
- A LOCKED screen still tips everything (viewers may read; config
  bounces server-side) — the lock glyph reads "Unlock screen".
- Signal tiles behave exactly as before: full-name tooltip only when
  ellipsized, nothing new.
- Regression: every click on every screen still does exactly what it did
  before; the 19 games are untouched; no dedicated-server implications
  (client-only).
- Old-world load and 1.12.0 pairing: a 1.12.1 client joins a 1.12.0
  server and vice versa.
