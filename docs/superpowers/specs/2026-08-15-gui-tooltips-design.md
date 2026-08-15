# GUI hover tooltips — design

Date: 2026-08-15
Status: approved (design), pending implementation plan
Version target: 1.12.1 (client-only, PAIRS with 1.12.0 — no wire, no
registrar, no component, no NBT change of any kind)

## Goal

Every clickable control in every Link Tablet program screen says what it
is on hover. Today only five screens have any tooltips at all, and the
mod is full of wordless procedural glyphs (chain-link, padlock, solo,
eyedropper, emote toggle) whose meaning is guessable at best.

## Scope

**In**: Signals, Launcher, App Store, Arcade hub, Signal edit, Probe
edit, Gauges, Monitor, Paint, Twitch, Clock, Calculator, the three
picker overlays, and the two floating windows.

**Out**: the 19 arcade game screens (self-explanatory play surfaces;
hover text over a live play field gets in the way), and the pinned HUD
overlay panes (no cursor exists unless the focus screen is open, and
then the floating windows are the interactive surface, which IS in
scope).

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
screen is open. They register into the same collector and `draw` at the
end of that pass. Because the window pass runs after the screen's, window
tips land on top — which means `TabletScreen`'s existing
`NoteWindows.anyContains(...) → return` guard can be **deleted**, since
last-wins already produces exactly that behaviour.

## 2. Format — name + hint

`ScreenTips` owns the two-line formatting so no caller can get it wrong:

- Line 1: `<key>` — white. The control's name.
- Line 2: `<key>.hint` — gray. One short clause on what it does.
- **If `<key>.hint` is absent from the lang file, only the name draws.**
  That is the escape hatch for anything genuinely self-evident, and it
  makes this pass incrementally completable rather than all-or-nothing.

Rendering is `graphics.renderTooltip(font, List<Component>, mouseX,
mouseY)` — the same call vanilla uses for inventory items, so the box,
border, instant appearance, cursor-following, and screen-edge flipping
are all vanilla behaviour, matching the look the user asked for.

`SignalEditScreen` and `ProbeEditScreen` keep vanilla `setTooltip` on
their real `ChromeButton` widgets (that path already works and already
positions correctly), but build the tooltip through a
`ScreenTips.tooltip(key)` factory returning a vanilla `Tooltip`, so the
white-name/gray-hint convention has exactly ONE definition across both
mechanisms.

### Lang keys

- **Reuse existing keys** wherever one already exists
  (`gui.linktablet.view.grid`, `.home`, `.theme.title`, `.lock.lock`,
  `.lock.unlock`, `.overlay.pin`, `.overlay.unpin`, `.note`,
  `.picker.search`, `.links.button.tooltip`, the four
  `.edit_signal.type.*` keys). Those gain a `.hint` sibling — no renames,
  so nothing already translated breaks.
- New keys: `gui.linktablet.tip.<screen>.<control>`.
- Controls shared across screens (Home, Pin, Theme, Lock, Link, Grid,
  List) are defined ONCE and reused on all ten screens that draw them.

### Labelled controls

A control whose visible face is already its label (store rows, arcade
rows, theme popup rows, gauge tiles) still gets a tooltip — the name line
repeats the label, and the **hint line carries the action** ("Add to your
home screen", "Play", "Edit this gauge"). A tooltip that only echoed the
visible text would be noise; the hint is what earns it.

## 3. Coverage inventory

Shared header controls, defined once, drawn by Signals, Launcher, Store,
Arcade hub, Gauges, Monitor, Twitch, Clock, Calculator:

| Control | Name | Hint |
|---|---|---|
| Home | Home | Back to the launcher |
| Theme | Theme | Change this screen's colours |
| Grid view | Grid view | Show signals as tiles |
| List view | List view | Show signals as rows |
| Rearrange | Rearrange | Drag signals into a new order |
| Pin (unpinned) | Pin to overlay | Keep this screen on your HUD |
| Pin (pinned) | Unpin from overlay | Take this screen off your HUD |
| Lock (unlocked) | Lock screen | Stop others editing this tablet |
| Lock (locked) | Unlock screen | Hold a wrench to unlock |
| Link (merged) | Unlink screen | Split this tablet off its neighbours |
| Link (solo) | Link screen | Let this tablet merge with its neighbours |
| Notes | Notes | Open a floating scratch note |

**Signals** (`TabletScreen`) — header above, plus: signal tile/row (name
= the signal's full name, hint = its type action: "Click to toggle" /
"Hold to fire" / "Drag to set the level" / "Click to start the timer");
add-signal tile ("New signal" / "Add a signal to this tablet"); theme
popup rows (theme name / one-line character, e.g. "Parchment" / "Warm
paper tones, dark text"). **The chain-link glyph currently has NO
tooltip on this screen** — a gap this pass closes.

**Launcher** — header, plus program tile (program name / "Open it.
Right-click removes it from Home"), store tile ("App Store" / "Browse
programs to add"). Chain-link glyph untipped today; closed here too.

**App Store** — header, plus search box ("Search" / "Filter the
catalogue by name"), app row not owned (name / "Add to your home
screen"), owned (name / "Remove from your home screen"), owned-and-last
(name / "Your home screen needs at least one app").

**Arcade hub** — header, plus game row (game name / "Play").

**Signal edit** — widgets: Add frequency, Search items, Icon, Links,
Save, Cancel, Delete. Procedural: frequency chip ("Frequency" / "Click
to remove"), colour button ("Colour" / "Change this signal's tile
colour"), colour popup swatch ("Colour" / "Use this colour"), type row
(four existing keys + hints), strength track ("Signal strength" /
"Redstone power this signal sends"), slider range knobs ("Range" /
"Lowest and highest this slider can send"), pulse track ("Pulse length"
/ "How long a timed signal stays on"), name box ("Name" / "What this
signal is called"), ghost slots ("Frequency item" / "Drag an item here,
or click to pick one").

**Probe edit** — Search items (shared), Add probe ("Add probe" / "Watch
this frequency on the Monitor"), Cancel, ghost slots (shared key).

**Gauges** — header, plus gauge tile (gauge name / "Edit this gauge"),
empty tile ("New gauge" / "Add a gauge to this tablet"); editor modal:
name box, two frequency slots (shared key), colour swatch, Save, Delete.

**Monitor** — header, plus add-probe "+" ("Add probe" / "Watch another
frequency"), probe-row remove cross ("Remove probe" / "Stop watching
this frequency").

**Paint** — tool glyphs, each hint carrying its **keyboard shortcut**
(currently undiscoverable): Brush / "Paint cells — right-click erases.
Shortcut: B"; Fill / "Flood the connected area. Shortcut: F"; Line /
"Drag a straight line. Shortcut: L"; Rectangle / "Drag a box. Shortcut:
R"; Pick colour / "Copy the colour under the cursor. Shortcut: I"; Undo
/ "Step back through your last changes". Plus palette swatch ("Colour" /
"Paint with this colour").

**Twitch** — header, plus channel box ("Channel" / "Type a Twitch
channel and press Enter"), emote toggle on ("Emotes" / "Showing emote
images") and off ("Emotes" / "Showing plain text").

**Clock** — header, plus four tabs (Alarm / Clock / Timer / Stopwatch,
each with a one-line hint); alarm row (time / "Click to enable or
disable"), alarm remove cross ("Remove alarm"), hour −/+ ("Hour" / "Set
the alarm hour"), minute −/+ ("Minute" / "Set the alarm minute — hold
Shift for single minutes"), Add alarm ("Add alarm" / "Save this time");
zone row (zone / "Right-click to remove"), add-zone row ("Add clock" /
"Show another time zone"); timer −60/−10/+10/+60 ("Adjust timer" /
"Change the countdown"), Start / Cancel; stopwatch Start-Pause / Reset.

**Pickers** — `PickerOverlay`: search box, Clear ("Clear" / "Leave this
slot empty"). `LinkPickerOverlay`: candidate row (signal name / "Click
to cycle: none → on → off → follow"). `ZonePickerOverlay`: search box,
zone row (zone / "Use this time zone").

**Floating windows** — `MiniTabletWindow` title bar (tablet name / "Drag
to move — right-click the body to open the full screen");
`NoteWindow` title bar ("Note" / "Drag to move"), close button ("Close"
/ "Save and close this note"), text area ("Note" / "Type anything — it
saves with the tablet").

## 4. Deliberate exemptions

Listed so they can be overruled rather than silently missed:

1. **Paint canvas cells** — a tooltip trailing the cursor while you draw
   would make the app unusable.
2. **Calculator digit and decimal keys** — the key face IS its meaning.
   Function keys (C, ±, %, ÷, ×, −, +, =) DO get tips.
3. **Item grid cells in `PickerOverlay`** — they already show the vanilla
   item tooltip, which is strictly better than anything we'd write.
4. **Mini-window body rows** — the window body delegates clicks to the
   pinned program's own content renderer; tipping through that
   indirection is a second design. Title bar only in v1.
5. **Click-outside-to-close dead zones** — not controls.
6. **The 19 game screens** — per scope.

## 5. Observation, not in scope

`PaintScreen` is the only program screen with **no Home glyph** — it
exits by ESC alone, while every sibling screen has one. Noted for a
future pass; this change does not add it.

## 6. Test matrix

- Hover every control on every in-scope screen: a tooltip appears
  instantly, follows the cursor, and never clips off screen edges.
- Two-line shape: white name, gray hint. A key with no `.hint` draws one
  line and does not crash or draw an empty second row.
- State-dependent controls flip text correctly: pin/unpin, lock/unlock,
  link/unlink, emote on/off, timer start/cancel, stopwatch start/pause.
- Priority: with the theme popup open, hovering a popup row tips the row,
  not the header glyph beneath it. Same for the Gauges editor modal and
  all three picker overlays.
- Floating windows: hover a window title bar while a tablet screen is
  open underneath — the window's tip shows and the screen's does not.
- Block-view-only controls (Lock, Link) tip on a placed tablet and are
  absent on a held one.
- A LOCKED screen still tips everything (viewers may read; config
  bounces server-side) — the lock glyph reads "Unlock screen / Hold a
  wrench to unlock".
- Regression: every click on every screen still does exactly what it did
  before; the 19 games are untouched; no dedicated-server implications
  (client-only).
- Old-world load and 1.12.0 pairing: a 1.12.1 client joins a 1.12.0
  server and vice versa.
