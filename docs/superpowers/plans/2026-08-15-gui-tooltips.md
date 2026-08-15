# GUI Hover Tooltips Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Every wordless clickable glyph in every Link Tablet program screen shows a one-line hover tooltip naming what it is.

**Architecture:** One new client class, `client/screen/ScreenTips` — a per-frame collector. Screens call `ScreenTips.glyph(x, y, key)` or `ScreenTips.add(x, y, w, h, key)` at the moment they draw a control, then one `ScreenTips.draw(...)` at the end of `render` paints the last-registered rect under the cursor and clears the list. `mouseClicked` is never touched on any screen, so click behaviour stays byte-identical.

**Tech Stack:** NeoForge 1.21.1, Java 21, Minecraft `GuiGraphics.renderTooltip` (the vanilla inventory tooltip renderer). No new dependencies.

## Global Constraints

- **Client-only.** No wire, registrar, component, or NBT change of any kind. 1.12.1 PAIRS with 1.12.0.
- **`mouseClicked` is never modified** on any screen. If a task finds itself editing a click handler, it has gone wrong.
- **No new geometry.** Every `add`/`glyph` call passes a rect the screen already computed for drawing. Never re-derive coordinates.
- **Header glyph rect is uniform**: `MODE_BTN_SIZE = 12`, a 12×12 square at `(btnX, modeBtnY())` on every screen that draws a header row. That is what `ScreenTips.glyph` encodes.
- **Reuse existing lang keys** where one exists; never rename one. New keys use `gui.linktablet.tip.<screen>.<control>`.
- **Do NOT create a `<key>.hint` sibling for anything.** `gui.linktablet.note.hint` already exists and means the note box's placeholder text — that namespace is taken. Tooltips are one line, no hint keys.
- **No unit test harness exists** in this repo (`src/test` does not exist). Verification per task is `./gradlew build` green; visual confirmation is the dev-client pass in Task 7. Do not scaffold a test framework.
- If `java` isn't found in a fresh shell: `$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')`

---

### Task 1: `ScreenTips` + the Signals screen as reference

**Files:**
- Create: `src/main/java/com/modpack/linktablet/client/screen/ScreenTips.java`
- Modify: `src/main/java/com/modpack/linktablet/client/screen/TabletScreen.java` (the tooltip block at ~line 565-589, and the glyph draw sites in `renderModeButtons`)
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

**Interfaces:**
- Produces: `ScreenTips.add(int x, int y, int w, int h, String key)`, `ScreenTips.add(int x, int y, int w, int h, Component text)`, `ScreenTips.glyph(int x, int y, String key)`, `ScreenTips.draw(GuiGraphics g, Font font, int mouseX, int mouseY)`, `ScreenTips.drawWindows(GuiGraphics g, Font font, int mouseX, int mouseY)`, `ScreenTips.tooltip(String key)` returning `net.minecraft.client.gui.components.Tooltip`.
- Consumes: `NoteWindows.anyContains(double, double)` (already public static).

- [ ] **Step 1: Create `ScreenTips`**

```java
package com.modpack.linktablet.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-frame hover-tooltip collector (1.12.1). Screens register the rect
 * they ALREADY computed for drawing a control; one {@link #draw} call at
 * the end of render paints the last-registered rect under the cursor and
 * clears the list.
 *
 * <p>Last-registered wins, so a popup or modal drawn over a header glyph
 * takes priority with no z-index bookkeeping, and a control that stops
 * being drawn automatically stops tipping.
 *
 * <p>Click handling is deliberately NOT involved: this class never sees
 * mouseClicked, so adding a tooltip can never change what a control does.
 */
final class ScreenTips {

    /** The header-row glyph square — MODE_BTN_SIZE on every screen. */
    private static final int GLYPH = 12;

    private record Tip(int x, int y, int w, int h, Component text) {
        boolean hit(int mx, int my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private static final List<Tip> TIPS = new ArrayList<>();

    static void add(int x, int y, int w, int h, String key) {
        add(x, y, w, h, Component.translatable(key));
    }

    static void add(int x, int y, int w, int h, Component text) {
        TIPS.add(new Tip(x, y, w, h, text));
    }

    /** A header-row glyph at the standard 12x12 mode-button rect. */
    static void glyph(int x, int y, String key) {
        add(x, y, GLYPH, GLYPH, key);
    }

    /**
     * Screen pass. Yields to the floating windows: they draw ABOVE every
     * screen through NoteWindows' ScreenEvent.Render.Post, which is a
     * SECOND draw call in the same frame — without this guard both passes
     * could paint, leaving a screen tooltip stranded under a window.
     */
    static void draw(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (NoteWindows.anyContains(mouseX, mouseY)) {
            TIPS.clear();
            return;
        }
        paint(g, font, mouseX, mouseY);
    }

    /** Window pass — already on top, so no yielding. */
    static void drawWindows(GuiGraphics g, Font font, int mouseX, int mouseY) {
        paint(g, font, mouseX, mouseY);
    }

    private static void paint(GuiGraphics g, Font font, int mouseX, int mouseY) {
        Component hit = null;
        for (Tip tip : TIPS) {
            if (tip.hit(mouseX, mouseY)) {
                hit = tip.text(); // last registered wins
            }
        }
        TIPS.clear();
        if (hit != null) {
            g.renderTooltip(font, hit, mouseX, mouseY);
        }
    }

    /** Widget-path equivalent, so ChromeButtons read identically. */
    static Tooltip tooltip(String key) {
        return Tooltip.create(Component.translatable(key));
    }

    private ScreenTips() {
    }
}
```

- [ ] **Step 2: Register the Signals header glyphs**

In `TabletScreen.renderModeButtons`, beside each existing `HeaderGlyphs.*` call, add the matching registration. The glyph x helpers already exist (`homeBtnX()`, `gridBtnX()`, `listBtnX()`, `reorderBtnX()`, `themeBtnX()`, `pinBtnX()`, `linkBtnX()`, `lockBtnX()`) and y is `modeBtnY()`:

```java
ScreenTips.glyph(homeBtnX(), modeBtnY(), "gui.linktablet.home");
ScreenTips.glyph(gridBtnX(), modeBtnY(), "gui.linktablet.view.grid");
ScreenTips.glyph(listBtnX(), modeBtnY(), "gui.linktablet.view.list");
ScreenTips.glyph(reorderBtnX(), modeBtnY(), "gui.linktablet.view.reorder");
ScreenTips.glyph(themeBtnX(), modeBtnY(), "gui.linktablet.theme.title");
ScreenTips.glyph(pinBtnX(), modeBtnY(), OverlayPin.isPinned(view)
        ? "gui.linktablet.overlay.unpin" : "gui.linktablet.overlay.pin");
if (isBlockView()) {
    ScreenTips.glyph(linkBtnX(), modeBtnY(), soloScreen()
            ? "gui.linktablet.tip.link" : "gui.linktablet.tip.unlink");
    ScreenTips.glyph(lockBtnX(), modeBtnY(), lockedScreen()
            ? "gui.linktablet.lock.unlock" : "gui.linktablet.lock.lock");
}
```

Guard each registration with the SAME condition that guards drawing that glyph — if the screen only draws Link and Lock on block views, only register them there.

- [ ] **Step 3: Replace the tooltip ladder with the collector**

Delete the `if/else if` chain at the end of `TabletScreen.render` (the block starting `// Tooltips last, on top of everything`, currently lines ~565-589) and replace it with:

```java
// Tooltips last, on top of everything
if (hoveredEllipsizedName != null && !themePopupOpen) {
    ScreenTips.add(hoveredNameX, hoveredNameY, hoveredNameW, hoveredNameH,
            Component.literal(hoveredEllipsizedName));
}
ScreenTips.draw(graphics, font, mouseX, mouseY);
```

The `NoteWindows.anyContains` early-return is REMOVED from this method — `ScreenTips.draw` now owns that rule for every screen.

If the ellipsized-name path does not already have the hovered rect available as fields, keep its existing `graphics.renderTooltip(...)` call as-is AFTER the `ScreenTips.draw` line rather than inventing geometry — the Global Constraints forbid deriving new rects. The add-signal tile registers where it is drawn:

```java
ScreenTips.add(tileX, tileY, TILE_SIZE, TILE_SIZE, "gui.linktablet.tip.signal.new");
```

- [ ] **Step 4: Add the new lang keys**

Add to `src/main/resources/assets/linktablet/lang/en_us.json`:

```json
"gui.linktablet.tip.link": "Link screen",
"gui.linktablet.tip.unlink": "Unlink screen",
"gui.linktablet.tip.signal.new": "New signal",
```

Do NOT touch the existing `gui.linktablet.home`, `.view.grid`, `.view.list`, `.view.reorder`, `.theme.title`, `.overlay.pin`, `.overlay.unpin`, `.lock.lock`, `.lock.unlock` entries — they already read correctly as tooltips.

- [ ] **Step 5: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, jar at `build/libs/linktablet-1.12.0.jar`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/modpack/linktablet/client/screen/ScreenTips.java src/main/java/com/modpack/linktablet/client/screen/TabletScreen.java src/main/resources/assets/linktablet/lang/en_us.json
git commit -m "Tooltips T1: ScreenTips collector + signals screen (closes the untipped chain-link glyph)"
```

---

### Task 2: Header glyphs on the remaining eight screens

**Files:**
- Modify: `LauncherScreen.java`, `StoreScreen.java`, `ArcadeHubScreen.java`, `GaugesScreen.java`, `MonitorScreen.java`, `TwitchScreen.java`, `ClockScreen.java`, `CalculatorScreen.java` (all under `src/main/java/com/modpack/linktablet/client/screen/`)
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

**Interfaces:**
- Consumes: `ScreenTips.glyph`, `ScreenTips.add`, `ScreenTips.draw` from Task 1.

- [ ] **Step 1: Register header glyphs on each screen**

For every screen, next to its `HeaderGlyphs.*` draw calls, add `ScreenTips.glyph(<thatBtnX>(), modeBtnY(), <key>)` using the key table below, then add `ScreenTips.draw(graphics, font, mouseX, mouseY)` as the LAST statement of `render`.

Every one of these screens already has a `modeBtnY()` and per-glyph x helper; use them, never new numbers.

| Glyph | Key |
|---|---|
| Home | `gui.linktablet.home` |
| Theme | `gui.linktablet.theme.title` |
| Grid view | `gui.linktablet.view.grid` |
| List view | `gui.linktablet.view.list` |
| Pin (unpinned) | `gui.linktablet.overlay.pin` |
| Pin (pinned) | `gui.linktablet.overlay.unpin` |
| Lock (unlocked) | `gui.linktablet.lock.lock` |
| Lock (locked) | `gui.linktablet.lock.unlock` |
| Link (merged) | `gui.linktablet.tip.unlink` |
| Link (solo) | `gui.linktablet.tip.link` |

`LauncherScreen` additionally registers its store tile where it draws it:

```java
ScreenTips.add(tileX, tileY, TILE_SIZE, TILE_SIZE, "gui.linktablet.tip.store");
```

`StoreScreen` registers its search box at the box's own rect:

```java
ScreenTips.add(searchBox.getX(), searchBox.getY(), searchBox.getWidth(),
        searchBox.getHeight(), "gui.linktablet.tip.search");
```

`TwitchScreen` registers its channel box the same way with `gui.linktablet.tip.twitch.channel`, plus the emote toggle at its glyph rect:

```java
ScreenTips.glyph(emoteBtnX(), modeBtnY(), ClientPrefs.twitchEmotes()
        ? "gui.linktablet.tip.twitch.emotes.on" : "gui.linktablet.tip.twitch.emotes.off");
```

`ArcadeHubScreen` and `CalculatorScreen` get header glyphs ONLY — game rows print their own names and the calculator keys print their own faces (spec exemptions 2 and 7).

- [ ] **Step 2: Add the new lang keys**

```json
"gui.linktablet.tip.store": "App Store",
"gui.linktablet.tip.search": "Search",
"gui.linktablet.tip.twitch.channel": "Channel",
"gui.linktablet.tip.twitch.emotes.on": "Emotes on",
"gui.linktablet.tip.twitch.emotes.off": "Emotes off",
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/modpack/linktablet/client/screen src/main/resources/assets/linktablet/lang/en_us.json
git commit -m "Tooltips T2: header glyphs on the eight remaining program screens"
```

---

### Task 3: Signal edit and Probe edit

**Files:**
- Modify: `SignalEditScreen.java`, `ProbeEditScreen.java`
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

**Interfaces:**
- Consumes: `ScreenTips.tooltip(String)`, `ScreenTips.add`, `ScreenTips.draw`.

- [ ] **Step 1: Widget tooltips**

These two screens are the only ones with real `ChromeButton` widgets, so they use the vanilla widget path. At each construction site (`SignalEditScreen.java:181,187,194,202,212,216,220` and `ProbeEditScreen.java:78,84,88`) call `setTooltip`:

```java
addFreqButton.setTooltip(ScreenTips.tooltip("gui.linktablet.tip.freq.add"));
saveButton.setTooltip(ScreenTips.tooltip("gui.linktablet.tip.save"));
```

`searchButton` already has `Tooltip.create(Component.translatable("gui.linktablet.picker.search"))` and `linksButton` already has `gui.linktablet.links.button.tooltip` — convert both to `ScreenTips.tooltip(<same key>)` so there is one construction path, but do NOT change the keys.

Cover: Add frequency, Search items (existing key), Icon, Links (existing key), Save, Cancel, Delete on `SignalEditScreen`; Search items, Add probe, Cancel on `ProbeEditScreen`.

- [ ] **Step 2: Procedural controls on `SignalEditScreen`**

Register at the draw sites, reusing the existing coordinate helpers (`chipX(i)`, `chipY(i)`, `CHIP_W`, `CHIP_H`, `RIGHT_COL`, `COLOR_BTN_Y`, `TRACK_Y`, `TRACK_W`):

```java
ScreenTips.add(chipX(i), chipY(i), CHIP_W, CHIP_H, "gui.linktablet.tip.freq.remove");
ScreenTips.add(rightX, topPos + COLOR_BTN_Y, 34, 20, "gui.linktablet.tip.colour");
ScreenTips.add(rightX, topPos + MOMENTARY_Y - 2, 90, CHECKBOX_SIZE + 4, typeKey);
ScreenTips.add(rightX - 2, topPos + TRACK_Y - 6, TRACK_W + 6, 18, trackKey);
```

`typeKey` is the SAME four-way choice the existing tooltip ladder already computes (`gui.linktablet.edit_signal.type.slider.tooltip`, `.momentary.tooltip`, `.type.timed.tooltip`, `.type.toggle.tooltip`) — reuse that expression, do not restate it. `trackKey` is `gui.linktablet.tip.range` when `slider`, `gui.linktablet.tip.pulse` when `timed`, else `gui.linktablet.tip.strength`.

Also register the colour popup swatches (inside the `colorPopupOpen` draw branch, so they register only while open and therefore win by last-registration), the name box at its own rect with `gui.linktablet.tip.name`, and the two ghost slots with `gui.linktablet.tip.freq.item`.

- [ ] **Step 3: Replace the existing ladder and draw**

Delete the `renderTooltip` chain at `SignalEditScreen.java:709-719`, keep the `renderTooltip(graphics, mouseX, mouseY)` vanilla-slot call at line 722 (that renders ITEM tooltips for the inventory and ghost slots — not ours), and add `ScreenTips.draw(graphics, font, mouseX, mouseY)` as the last statement of `render`. Do the same on `ProbeEditScreen` (its `renderTooltip(graphics, mouseX, mouseY)` at line 192 stays).

- [ ] **Step 4: Add the new lang keys**

```json
"gui.linktablet.tip.freq.add": "Add frequency",
"gui.linktablet.tip.freq.remove": "Remove frequency",
"gui.linktablet.tip.freq.item": "Frequency item",
"gui.linktablet.tip.icon": "Icon",
"gui.linktablet.tip.save": "Save",
"gui.linktablet.tip.cancel": "Cancel",
"gui.linktablet.tip.delete": "Delete",
"gui.linktablet.tip.colour": "Colour",
"gui.linktablet.tip.name": "Name",
"gui.linktablet.tip.strength": "Signal strength",
"gui.linktablet.tip.range": "Range",
"gui.linktablet.tip.pulse": "Pulse length",
"gui.linktablet.tip.probe.add": "Add probe",
```

- [ ] **Step 5: Build and commit**

Run: `./gradlew build` (expect BUILD SUCCESSFUL)

```bash
git add src/main/java/com/modpack/linktablet/client/screen src/main/resources/assets/linktablet/lang/en_us.json
git commit -m "Tooltips T3: signal edit and probe edit screens"
```

---

### Task 4: Paint and Clock

**Files:**
- Modify: `PaintScreen.java`, `ClockScreen.java`
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

- [ ] **Step 1: Paint tool bar and palette**

`PaintScreen` draws its header glyphs in a loop over `GLYPH_SLOTS` (see `PaintScreen.java:187`) at `headerGlyphX(slot)`. Register inside that loop, and note the LAST slot is Undo, not a tool:

```java
for (int slot = 0; slot < GLYPH_SLOTS; slot++) {
    // ... existing glyph drawing ...
    ScreenTips.glyph(headerGlyphX(slot), gy, slot == GLYPH_SLOTS - 1
            ? "gui.linktablet.tip.paint.undo"
            : TOOL_TIP_KEYS[slot]);
}
```

with, as a field beside the existing `Tool` enum (order: BRUSH, FILL, LINE, RECT, EYEDROPPER):

```java
private static final String[] TOOL_TIP_KEYS = {
        "gui.linktablet.tip.paint.brush",
        "gui.linktablet.tip.paint.fill",
        "gui.linktablet.tip.paint.line",
        "gui.linktablet.tip.paint.rect",
        "gui.linktablet.tip.paint.pick",
};
```

Register each palette swatch where the swatch strip is drawn, at `(boardX() + i * (SWATCH + 1), swatchRowY(), SWATCH, SWATCH)` with `gui.linktablet.tip.colour`.

**The canvas cells get NOTHING** (spec exemption 1 — a tooltip trailing the cursor while drawing would make the app unusable). Add `ScreenTips.draw(...)` as the last statement of `render`.

- [ ] **Step 2: Clock tabs and per-tab controls**

Register the four tabs at their existing tab rects with `gui.linktablet.tip.clock.tab.alarm` / `.clock` / `.timer` / `.stopwatch`.

Per tab, register at the rects the click handlers already use (`clickAlarmTab`, `clickClockTab`, `clickTimerTab`, `clickStopwatchTab` — read the rect expressions from there and use the identical ones at the DRAW sites):

- Alarm: remove cross `(right - 12, ry + 2, 10, 10)` → `gui.linktablet.tip.clock.alarm.remove`; hour −/+ `(left, stepY, 20, 14)` and `(left + 22, stepY, 20, 14)` → `gui.linktablet.tip.clock.hour`; minute −/+ (next two) → `gui.linktablet.tip.clock.minute`; add `(right - 56, stepY, 56, 14)` → `gui.linktablet.tip.clock.alarm.add`.
- Clock tab: the add-zone row → `gui.linktablet.tip.clock.zone.add`. Existing zone rows print their own zone name — nothing (spec exemption 7).
- Timer: the four delta buttons → `gui.linktablet.tip.clock.timer.adjust`; the start/cancel button → `gui.linktablet.tip.clock.timer.cancel` when `ClockService.timerRunning()`, else `gui.linktablet.tip.clock.timer.start`.
- Stopwatch: start/pause → `gui.linktablet.tip.clock.sw.pause` when `ClockService.stopwatchRunning()`, else `gui.linktablet.tip.clock.sw.start`; reset → `gui.linktablet.tip.clock.sw.reset` (register only when `ClockService.stopwatchElapsedMillis() > 0`, matching the click guard).

Add `ScreenTips.draw(...)` as the last statement of `render`.

- [ ] **Step 3: Add the new lang keys**

```json
"gui.linktablet.tip.paint.brush": "Brush (B)",
"gui.linktablet.tip.paint.fill": "Fill (F)",
"gui.linktablet.tip.paint.line": "Line (L)",
"gui.linktablet.tip.paint.rect": "Rectangle (R)",
"gui.linktablet.tip.paint.pick": "Pick colour (I)",
"gui.linktablet.tip.paint.undo": "Undo",
"gui.linktablet.tip.clock.tab.alarm": "Alarm",
"gui.linktablet.tip.clock.tab.clock": "World clocks",
"gui.linktablet.tip.clock.tab.timer": "Timer",
"gui.linktablet.tip.clock.tab.stopwatch": "Stopwatch",
"gui.linktablet.tip.clock.alarm.remove": "Remove alarm",
"gui.linktablet.tip.clock.alarm.add": "Add alarm",
"gui.linktablet.tip.clock.hour": "Hour",
"gui.linktablet.tip.clock.minute": "Minute",
"gui.linktablet.tip.clock.zone.add": "Add clock",
"gui.linktablet.tip.clock.timer.adjust": "Adjust timer",
"gui.linktablet.tip.clock.timer.start": "Start",
"gui.linktablet.tip.clock.timer.cancel": "Cancel",
"gui.linktablet.tip.clock.sw.start": "Start",
"gui.linktablet.tip.clock.sw.pause": "Pause",
"gui.linktablet.tip.clock.sw.reset": "Reset",
```

- [ ] **Step 4: Build and commit**

Run: `./gradlew build` (expect BUILD SUCCESSFUL)

```bash
git add src/main/java/com/modpack/linktablet/client/screen src/main/resources/assets/linktablet/lang/en_us.json
git commit -m "Tooltips T4: paint tool bar (with shortcuts) and clock tabs"
```

---

### Task 5: Gauges and Monitor

**Files:**
- Modify: `GaugesScreen.java`, `MonitorScreen.java`
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

- [ ] **Step 1: Gauges**

Register the empty "add" tile at its draw rect with `gui.linktablet.tip.gauge.new`. Named gauge tiles print their own names — nothing (spec exemption 7).

In the editor modal's draw branch (so they register only while open, winning by last-registration): name box → `gui.linktablet.tip.name`; the two slots at `(edSlot1X(), edSlotY(), 18, 18)` and `(edSlot2X(), edSlotY(), 18, 18)` → `gui.linktablet.tip.freq.item`; each colour swatch at `(edSwatchX(i), edSwatchY() + (i / 8) * 14, 12, 12)` → `gui.linktablet.tip.colour`; save `(edX() + 8, edButtonY(), 74, 18)` → `gui.linktablet.tip.save`; delete `(edX() + ED_W - 82, edButtonY(), 74, 18)` → `gui.linktablet.tip.delete` (register only when `editIndex >= 0`, matching the click guard).

Add `ScreenTips.draw(...)` as the last statement of `render`.

- [ ] **Step 2: Monitor**

Register the add-probe button at `(addBtnX(), addBtnY(), 18, 18)` → `gui.linktablet.tip.probe.add`, and each probe row's remove cross at `(rowX() + rowWidth() - 12, y + 3, 9, 9)` → `gui.linktablet.tip.probe.remove`, registering inside the same row walk that DRAWS the cross so scrolled-out rows never register. Add `ScreenTips.draw(...)` last in `render`.

- [ ] **Step 3: Add the new lang keys**

```json
"gui.linktablet.tip.gauge.new": "New gauge",
"gui.linktablet.tip.probe.remove": "Remove probe",
```

- [ ] **Step 4: Build and commit**

Run: `./gradlew build` (expect BUILD SUCCESSFUL)

```bash
git add src/main/java/com/modpack/linktablet/client/screen src/main/resources/assets/linktablet/lang/en_us.json
git commit -m "Tooltips T5: gauges editor and monitor probe controls"
```

---

### Task 6: Pickers and floating windows

**Files:**
- Modify: `PickerOverlay.java`, `LinkPickerOverlay.java`, `ZonePickerOverlay.java`, `MiniTabletWindow.java`, `NoteWindow.java`
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

- [ ] **Step 1: Pickers**

These are overlays drawn by their host screen, so they register into the collector and the HOST's `ScreenTips.draw` paints them — do NOT add a `draw` call inside a picker. Because pickers draw after the host's own content, their tips win automatically.

- `PickerOverlay`: search box at its rect → `gui.linktablet.tip.search`; the clear button at the rect `overClearButton` tests → `gui.linktablet.tip.picker.clear`. Item grid cells get NOTHING (spec exemption 3 — the vanilla item tooltip at `PickerOverlay.java:160` already covers them and is better).
- `LinkPickerOverlay`: each candidate row → `gui.linktablet.tip.link.cycle`, registered only when `candidate.linkId() != 0` (matching the click guard, so inert rows stay silent).
- `ZonePickerOverlay`: search box → `gui.linktablet.tip.search`. Zone rows print their own names — nothing.

- [ ] **Step 2: Floating windows**

Both windows draw through `NoteWindows`' `ScreenEvent.Render.Post` pass, ABOVE any screen. Register the title bar at the rect `overTitleBar` tests → `gui.linktablet.tip.window.drag`, and on `NoteWindow` the close button at the rect `overCloseButton` tests (`NoteWindow.java:140-143`) → `gui.linktablet.tip.window.close`.

Window body rows get NOTHING (spec exemption 4).

Then call `ScreenTips.drawWindows(graphics, font, mouseX, mouseY)` — NOT `draw` — as the last statement of `NoteWindows.onRenderScreen`, after every window has drawn. `drawWindows` skips the yield-to-windows guard, which is correct here because this pass IS the windows.

- [ ] **Step 3: Add the new lang keys**

```json
"gui.linktablet.tip.picker.clear": "Clear",
"gui.linktablet.tip.link.cycle": "Cycle link mode",
"gui.linktablet.tip.window.drag": "Drag to move",
"gui.linktablet.tip.window.close": "Close",
```

- [ ] **Step 4: Build and commit**

Run: `./gradlew build` (expect BUILD SUCCESSFUL)

```bash
git add src/main/java/com/modpack/linktablet/client/screen src/main/resources/assets/linktablet/lang/en_us.json
git commit -m "Tooltips T6: pickers and floating windows"
```

---

### Task 7: Changelog, version, and the dev-client pass

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `gradle.properties`
- Modify: `docs/NEXT_SESSION.md`

- [ ] **Step 1: Changelog**

Add under `## [Unreleased]` (create the section if absent):

```markdown
### Added
- Hover tooltips on every button and glyph across all program screens —
  the wordless ones (chain-link, padlock, solo, eyedropper, emote
  toggle) finally say what they are. Paint's tool tips also name their
  keyboard shortcuts (B/F/L/R/I), which were previously undiscoverable.
```

- [ ] **Step 2: Version bump**

In `gradle.properties`, set `mod_version=1.12.1`. Client-only — 1.12.1 PAIRS with 1.12.0, no registrar change.

- [ ] **Step 3: Full build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, jar at `build/libs/linktablet-1.12.1.jar`

- [ ] **Step 4: Dev-client verification pass**

Run: `./gradlew runClient`

Walk the spec's test matrix. Every line must be confirmed by eye before this task is called done:

- Hover every registered control on every in-scope screen: a one-line tooltip appears instantly, follows the cursor, never clips off a screen edge.
- State-dependent glyphs flip text: pin/unpin, lock/unlock, link/unlink, emotes on/off, timer start/cancel, stopwatch start/pause.
- Priority: with the theme popup open, hovering a popup row does NOT tip the header glyph beneath it. Same for the Gauges editor modal and all three pickers.
- Floating windows: hover a window title bar while a tablet screen is open underneath — the window's tip shows and the screen's does not.
- Block-only glyphs (Lock, Link) tip on a placed tablet and are absent on a held one.
- A locked screen still tips everything; the lock glyph reads "Unlock screen".
- Signal tiles behave exactly as before: full-name tooltip only when ellipsized.
- Regression: every click on every screen still does what it did before. The 19 game screens are untouched.

- [ ] **Step 5: Update the roadmap**

In `docs/NEXT_SESSION.md`, move the tooltip item out of the polish queue into a new "START HERE" status entry recording what shipped.

- [ ] **Step 6: Commit**

```bash
git add CHANGELOG.md gradle.properties docs/NEXT_SESSION.md
git commit -m "Tooltips T7: changelog, 1.12.1 bump, roadmap"
```

---

## Notes for the implementer

- **The one repeated mistake to avoid:** deriving a new rect for a tooltip. Every registration must reuse the exact expression the screen already uses to draw (or that its click handler already tests). If a rect isn't readily available at the draw site, register nothing and flag it rather than inventing coordinates — a tooltip whose box disagrees with the button is worse than no tooltip.
- Registration must sit under the same conditionals that guard drawing. A glyph drawn only on block views registers only on block views; a row clipped by a scissor registers only inside the row walk that draws it.
- If a screen's `render` has an early return, `ScreenTips.draw` must still run on that path, or stale tips carry into the next frame. Prefer a single exit point.
