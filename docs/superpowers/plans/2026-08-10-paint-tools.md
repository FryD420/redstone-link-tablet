# Paint v2 Drawing Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bucket fill, line, rectangle, eyedropper, and conflict-aware undo to the Paint app as client-side gestures over the existing stroke wire.

**Architecture:** Every tool decomposes into ordinary cells fed through `PaintScreen`'s existing pending-map → `PaintStrokePayload` flush — zero wire/server/renderer changes, pairs with 1.11.0. Grid math (flood fill, rect outline) lives in `PaintCanvas` (the geometry home, beside the existing `line` walker); all gesture state (active tool, shape preview, undo history) lives in `PaintScreen`.

**Tech Stack:** Java 21, NeoForge 1.21.1 (21.1.233), no test framework — the per-task gate is `./gradlew build` green; visual behavior verifies in the dev client (`./gradlew runClient`, offline player "Dev") against the matrix in the spec.

**Spec:** `docs/superpowers/specs/2026-08-10-paint-tools-design.md`

## Global Constraints

- NO new payloads, components, NBT, or registrar changes — registrar stays "23"; everything rides `ModNetworking.PaintStrokePayload` via the existing `pending` map + `flush()`.
- Grid/geometry math goes in `PaintCanvas` ONLY (one-source rule); `PaintScreen` never re-implements cell walks.
- Build command (PowerShell; reload PATH first in a fresh shell):
  `$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User'); ./gradlew build --console=plain -q; echo "EXIT: $LASTEXITCODE"` — expected `EXIT: 0`.
- Commit after every task; end commit messages with
  `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` (put messages in a single-quoted here-string; no double quotes inside — PS 5.1 mangles them on native calls).
- Painting-lock is OUT OF SCOPE (parked into the locked-screen feature).

---

### Task 1: PaintCanvas grid math — floodRegion + rectOutline

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/PaintCanvas.java`

**Interfaces:**
- Consumes: existing `PaintCanvas.CellVisitor { void cell(int x, int y); }`.
- Produces:
  - `public static int[] floodRegion(int[] grid, int cols, int rows, int start)` — continuous indices of the 4-neighbor connected region whose cells equal `grid[start]`, always containing `start`; `start` out of range returns an empty array.
  - `public static void rectOutline(int x0, int y0, int x1, int y1, CellVisitor visitor)` — visits each perimeter cell of the corner-normalized rectangle exactly once (degenerate 1-wide/1-tall rects visit each cell once, no doubles).

- [ ] **Step 1: Add both methods** — insert directly above `private PaintCanvas()`:

```java
    /**
     * Continuous indices of the 4-neighbor connected region containing
     * {@code start} whose cells all equal {@code grid[start]} — bucket
     * fill's shape, computed on the STITCHED grid so regions cross
     * merged-wall seams for free. Out-of-range start returns empty.
     */
    public static int[] floodRegion(int[] grid, int cols, int rows, int start) {
        if (start < 0 || start >= cols * rows) return new int[0];
        int match = grid[start];
        boolean[] seen = new boolean[cols * rows];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        java.util.ArrayList<Integer> region = new java.util.ArrayList<>();
        seen[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int idx = queue.poll();
            region.add(idx);
            int x = idx % cols, y = idx / cols;
            if (x > 0) floodVisit(grid, seen, queue, idx - 1, match);
            if (x < cols - 1) floodVisit(grid, seen, queue, idx + 1, match);
            if (y > 0) floodVisit(grid, seen, queue, idx - cols, match);
            if (y < rows - 1) floodVisit(grid, seen, queue, idx + cols, match);
        }
        int[] out = new int[region.size()];
        for (int i = 0; i < out.length; i++) out[i] = region.get(i);
        return out;
    }

    private static void floodVisit(int[] grid, boolean[] seen,
                                   java.util.ArrayDeque<Integer> queue, int idx, int match) {
        if (!seen[idx] && grid[idx] == match) {
            seen[idx] = true;
            queue.add(idx);
        }
    }

    /** Visits each perimeter cell of the corner-normalized rectangle
     * exactly once (the rectangle tool; degenerate rows/columns don't
     * double-visit). */
    public static void rectOutline(int x0, int y0, int x1, int y1, CellVisitor visitor) {
        int left = Math.min(x0, x1), right = Math.max(x0, x1);
        int top = Math.min(y0, y1), bottom = Math.max(y0, y1);
        for (int x = left; x <= right; x++) {
            visitor.cell(x, top);
            if (bottom != top) visitor.cell(x, bottom);
        }
        for (int y = top + 1; y < bottom; y++) {
            visitor.cell(left, y);
            if (right != left) visitor.cell(right, y);
        }
    }
```

- [ ] **Step 2: Build gate** — run the Global Constraints build command. Expected: `EXIT: 0`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/modpack/linktablet/PaintCanvas.java
git commit -m "Paint tools T1: floodRegion + rectOutline grid math in PaintCanvas"
```
(with the co-author trailer per Global Constraints.)

---

### Task 2: Tool selection — enum, glyph row, keyboard

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java`

**Interfaces:**
- Consumes: existing `PaintScreen` fields `selected`, `cols`, `rows`, `cell`, `boardX()`, `boardY()`, `SWATCH`, `PALETTE`, `UISounds.tick(float)`; `theme()` from `ArcadeScreen` (returns `ScreenTheme` with `accent`, `textFaint` int fields).
- Produces (later tasks rely on these exact names):
  - `enum Tool { BRUSH, FILL, LINE, RECT, EYEDROPPER }` (nested in `PaintScreen`).
  - Field `private Tool tool = Tool.BRUSH;`
  - `private void selectTool(Tool t)` — sets `tool`, plays `UISounds.tick(1.3F)`.
  - `private int toolRowY()` / `private int swatchRowY()` — y of the two strip rows.
  - `private void undo()` — EXISTS AS A STUB in this task (empty body); Task 3 fills it. The undo glyph and Z key already call it.
  - Field `private final java.util.ArrayDeque<UndoStep> history = new java.util.ArrayDeque<>();` and `private record UndoStep(java.util.List<UndoCell> cells) {}` / `private record UndoCell(int index, int beforeArgb, int afterArgb) {}` — declared here so the glyph can dim on empty history; Task 3 populates them.

- [ ] **Step 1: Grow the board for the tool row.** Replace `boardH()` and the two strip-height mentions in `layout()`:

Replace:
```java
    @Override
    protected int boardH() {
        return rows * cell + SWATCH + 4;
    }
```
with:
```java
    @Override
    protected int boardH() {
        // canvas + gap + tool row + gap + swatch row
        return rows * cell + 4 + SWATCH + 2 + SWATCH;
    }
```

In `layout()`, replace the `availH` line:
```java
        int availH = Math.max(1, height - HEADER - PAD * 2 - 4 - SWATCH - 4 - MARGIN);
```
with:
```java
        int availH = Math.max(1, height - HEADER - PAD * 2 - 4 - SWATCH - 2 - SWATCH - 4 - MARGIN);
```

- [ ] **Step 2: Add the tool state.** Insert below the `private int selected = 6;` field:

```java
    /** Active drawing tool (1.11.x Paint v2). BRUSH is the classic
     * paint/erase; the rest are client-side gestures that decompose to
     * ordinary cells — see the design spec. */
    enum Tool { BRUSH, FILL, LINE, RECT, EYEDROPPER }

    private Tool tool = Tool.BRUSH;
    /** Undo history, newest first (Task 3 populates; declared here so
     * the glyph row can dim while it's empty). Depth-capped at
     * MAX_UNDO. */
    private static final int MAX_UNDO = 32;
    private final java.util.ArrayDeque<UndoStep> history = new java.util.ArrayDeque<>();

    private record UndoCell(int index, int beforeArgb, int afterArgb) {}

    private record UndoStep(java.util.List<UndoCell> cells) {}

    private void selectTool(Tool t) {
        tool = t;
        UISounds.tick(1.3F);
    }

    /** Filled by Task 3 (conflict-aware replay). */
    private void undo() {
    }

    private int toolRowY() {
        return boardY() + rows * cell + 4;
    }

    private int swatchRowY() {
        return toolRowY() + SWATCH + 2;
    }
```

- [ ] **Step 3: Render the two rows.** In `render(...)`, replace everything from `int py = by + rows * cell + 4;` to the end of the method with:

```java
        int ty = toolRowY();
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            int x = bx + i * (SWATCH + 1);
            renderToolGlyph(graphics, tools[i], x, ty);
            if (tools[i] == tool) {
                graphics.fill(x, ty - 2, x + SWATCH, ty - 1, 0xFFE8EAF0);
            }
        }
        // Undo sits at the far right of the tool row — a momentary
        // button, not a mode; dimmed while there is nothing to undo.
        renderUndoGlyph(graphics, undoGlyphX(), ty, !history.isEmpty());

        int py = swatchRowY();
        for (int i = 0; i < PALETTE.length; i++) {
            int x = bx + i * (SWATCH + 1);
            graphics.fill(x, py, x + SWATCH, py + SWATCH, PALETTE[i]);
            if (i == selected) {
                graphics.fill(x, py - 2, x + SWATCH, py - 1, 0xFFE8EAF0);
            }
        }
    }

    private int undoGlyphX() {
        return boardX() + Math.max(Tool.values().length * (SWATCH + 1) + SWATCH,
                cols * cell - SWATCH);
    }

    /** Procedural 8×8 tool glyphs, house style (fills only). */
    private void renderToolGlyph(GuiGraphics g, Tool t, int x, int y) {
        int ink = 0xFFE8EAF0;
        switch (t) {
            case BRUSH -> {
                g.fill(x + 5, y, x + 8, y + 3, ink);          // tip
                g.fill(x + 3, y + 3, x + 5, y + 5, ink);      // ferrule
                g.fill(x + 1, y + 5, x + 3, y + 8, ink);      // handle
            }
            case FILL -> {
                g.fill(x + 1, y + 2, x + 6, y + 7, ink);      // bucket
                g.fill(x + 6, y + 4, x + 8, y + 8, ink);      // pour
            }
            case LINE -> {
                g.fill(x, y + 6, x + 2, y + 8, ink);
                g.fill(x + 2, y + 4, x + 4, y + 6, ink);
                g.fill(x + 4, y + 2, x + 6, y + 4, ink);
                g.fill(x + 6, y, x + 8, y + 2, ink);
            }
            case RECT -> {
                g.fill(x, y + 1, x + 8, y + 2, ink);          // top
                g.fill(x, y + 6, x + 8, y + 7, ink);          // bottom
                g.fill(x, y + 2, x + 1, y + 6, ink);          // left
                g.fill(x + 7, y + 2, x + 8, y + 6, ink);      // right
            }
            case EYEDROPPER -> {
                g.fill(x, y + 6, x + 2, y + 8, ink);          // tip
                g.fill(x + 2, y + 3, x + 5, y + 6, ink);      // barrel
                g.fill(x + 5, y + 1, x + 8, y + 4, ink);      // bulb
            }
        }
    }

    private void renderUndoGlyph(GuiGraphics g, int x, int y, boolean enabled) {
        int ink = enabled ? 0xFFE8EAF0 : 0xFF5A6170;
        g.fill(x, y + 3, x + 3, y + 6, ink);                  // arrow head block
        g.fill(x + 3, y + 4, x + 8, y + 6, ink);              // shaft
        g.fill(x + 6, y + 2, x + 8, y + 4, ink);              // curl
    }
```

(The `render` method keeps its existing beginning: `layout()`, `super.render`, `renderCabinet`, the canvas cell loop — only the strip rendering at the end changes.)

- [ ] **Step 4: Route clicks for both rows.** In `mouseClicked`, replace the palette-strip block:

```java
        // Palette strip below the canvas
        int py = boardY() + rows * cell + 4;
        if (mouseY >= py && mouseY < py + SWATCH) {
            int index = (int) ((mouseX - boardX()) / (SWATCH + 1));
            if (index >= 0 && index < PALETTE.length) {
                selected = index;
                UISounds.tick(1.3F);
                return true;
            }
        }
```
with:
```java
        // Tool row, then palette strip, below the canvas
        int ty = toolRowY();
        if (mouseY >= ty && mouseY < ty + SWATCH) {
            if (mouseX >= undoGlyphX() && mouseX < undoGlyphX() + SWATCH) {
                undo();
                UISounds.tick(0.8F);
                return true;
            }
            int index = (int) ((mouseX - boardX()) / (SWATCH + 1));
            if (index >= 0 && index < Tool.values().length) {
                selectTool(Tool.values()[index]);
                return true;
            }
            return true; // dead strip space never paints
        }
        int py = swatchRowY();
        if (mouseY >= py && mouseY < py + SWATCH) {
            int index = (int) ((mouseX - boardX()) / (SWATCH + 1));
            if (index >= 0 && index < PALETTE.length) {
                selected = index;
                UISounds.tick(1.3F);
                return true;
            }
            return true;
        }
```

- [ ] **Step 5: Keyboard.** In `keyPressed`, insert ABOVE the existing `if (keyCode == 67)` clear branch:

```java
        switch (keyCode) {
            case 66 -> { selectTool(Tool.BRUSH); return true; }      // B
            case 70 -> { selectTool(Tool.FILL); return true; }       // F
            case 76 -> { selectTool(Tool.LINE); return true; }       // L
            case 82 -> { selectTool(Tool.RECT); return true; }       // R
            case 73 -> { selectTool(Tool.EYEDROPPER); return true; } // I
            case 90 -> { undo(); return true; }                      // Z
            default -> { }
        }
```

- [ ] **Step 6: Build gate** — expected `EXIT: 0`.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java
git commit -m "Paint tools T2: tool enum, glyph row above the palette, keyboard selection"
```

---

### Task 3: Conflict-aware undo + gesture recording

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java`

**Interfaces:**
- Consumes: Task 2's `history`, `UndoStep`, `UndoCell`, `MAX_UNDO`, empty `undo()`; existing `paintCell(int index, byte color)`, `pending`, `flush()`, `canvas`, `paletteArgb(byte)`, `PALETTE`.
- Produces:
  - `private void endGesture()` — pushes the accumulated gesture (if any) as one `UndoStep`, caps history at `MAX_UNDO`. Tasks 4–5 call it after their cell batches; brush calls it from `mouseReleased`/`onClose`.
  - `paintCell` now records into the in-progress gesture (first-touch before-value, last-write after-value).
  - `undo()` implemented: conflict-aware replay + immediate `flush()`.
  - `private static byte paletteIndexOf(int argb)` — 0 for blank/unknown, else 1..16 (inverse of `paletteArgb`).

- [ ] **Step 1: Gesture recording.** Add below the `history` field:

```java
    /** Cells of the in-progress gesture: index → {beforeArgb,
     * afterArgb}. before is captured on FIRST touch (the stitched
     * baseline on block views — same array the render overlay uses),
     * after is last-write-wins. endGesture() turns it into one
     * UndoStep. */
    private final java.util.LinkedHashMap<Integer, int[]> gesture = new java.util.LinkedHashMap<>();

    /** One finished gesture (brush stroke, fill, committed shape) →
     * one undo step. No-op when nothing was painted. */
    private void endGesture() {
        if (gesture.isEmpty()) return;
        java.util.List<UndoCell> cells = new java.util.ArrayList<>(gesture.size());
        for (java.util.Map.Entry<Integer, int[]> e : gesture.entrySet()) {
            cells.add(new UndoCell(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        gesture.clear();
        history.push(new UndoStep(cells));
        while (history.size() > MAX_UNDO) history.removeLast();
    }

    /** Inverse of {@link #paletteArgb}: 0 for blank (or any unknown
     * value), else the 1-based palette color. */
    private static byte paletteIndexOf(int argb) {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == argb) return (byte) (i + 1);
        }
        return 0;
    }
```

- [ ] **Step 2: Record in paintCell.** Replace:

```java
    private void paintCell(int index, byte color) {
        canvas[index] = paletteArgb(color);
        pending.put(index, color);
    }
```
with:
```java
    private void paintCell(int index, byte color) {
        gesture.computeIfAbsent(index, i -> new int[]{canvas[i], 0})[1] = paletteArgb(color);
        canvas[index] = paletteArgb(color);
        pending.put(index, color);
    }
```

- [ ] **Step 3: Implement undo.** Replace the Task 2 stub:

```java
    /** Steps back one gesture, CONFLICT-AWARE: a cell is only reverted
     * while it still holds this step's after-value — cells someone else
     * painted over since are left alone (their work survives; see the
     * design spec). Undone cells ride the normal pending→stroke wire. */
    private void undo() {
        UndoStep step = history.poll();
        if (step == null || canvas == null) return;
        for (UndoCell cell : step.cells()) {
            if (cell.index() < canvas.length && canvas[cell.index()] == cell.afterArgb()) {
                canvas[cell.index()] = cell.beforeArgb();
                pending.put(cell.index(), paletteIndexOf(cell.beforeArgb()));
            }
        }
        flush();
    }
```

- [ ] **Step 4: Close brush gestures.** In `mouseReleased`, after the existing `flush()` inside the `if (dragging)` block, the stroke is over — change the block to:

```java
        lastStrokeCell = -1;
        if (dragging) {
            dragging = false;
            endGesture();
            flush();
        }
```

And in `onClose()`, insert `endGesture();` directly before `flush();`.

Also in `keyPressed`'s C-clear branch, insert `gesture.clear();` and `history.clear();` directly after the existing `pending.clear();` (a wall clear is not undoable — it wipes OTHER members' cells the client never recorded; leaving stale steps behind would "undo" into a cleared wall).

- [ ] **Step 5: Build gate** — expected `EXIT: 0`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java
git commit -m "Paint tools T3: gesture recording + conflict-aware undo (depth 32)"
```

---

### Task 4: Bucket fill

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java`

**Interfaces:**
- Consumes: Task 1 `PaintCanvas.floodRegion(int[] grid, int cols, int rows, int start)`; Task 2 `tool`/`Tool.FILL`; Task 3 `paintCell`, `endGesture`; existing `flush()`, `paletteArgb`, `apply` cell math (`boardX()`, `cell`).
- Produces: fill behavior inside `mouseClicked` — nothing new for later tasks.

- [ ] **Step 1: Fill branch.** In `mouseClicked`, directly AFTER the two strip blocks (tool row / swatches) and BEFORE the existing `if (apply(mouseX, mouseY, button))` brush block, insert:

```java
        if (tool == Tool.FILL && canvas != null) {
            int cx = (int) Math.floor((mouseX - boardX()) / cell);
            int cy = (int) Math.floor((mouseY - boardY()) / cell);
            if (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
                byte color = (byte) (button == 1 ? 0 : selected + 1);
                int start = cy * cols + cx;
                if (canvas[start] != paletteArgb(color)) { // same-color fill is a no-op
                    for (int idx : PaintCanvas.floodRegion(canvas, cols, rows, start)) {
                        paintCell(idx, color);
                    }
                    endGesture();
                    flush();
                    UISounds.tick(button == 1 ? 0.9F : 1.1F);
                }
                return true;
            }
        }
```

Note: the brush block below must not also run for FILL — change the existing brush condition from `if (apply(mouseX, mouseY, button))` to `if (tool == Tool.BRUSH && apply(mouseX, mouseY, button))`, and the same guard on the `mouseDragged` apply call (`if (tool == Tool.BRUSH && apply(...))`). FILL never drags.

- [ ] **Step 2: Build gate** — expected `EXIT: 0`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java
git commit -m "Paint tools T4: bucket fill (flood on the stitched grid, right-click erase-fill)"
```

---

### Task 5: Line + rectangle with ghost preview

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java`

**Interfaces:**
- Consumes: Task 1 `PaintCanvas.rectOutline` + existing `PaintCanvas.line`; Task 2 `tool`; Task 3 `paintCell`/`endGesture`; existing `Mth` (already imported).
- Produces: fields `shapeStartX/shapeStartY/shapeEndX/shapeEndY` (−1 = no shape in progress) — internal only.

- [ ] **Step 1: Shape state + helpers.** Add below the `lastStrokeCell` field:

```java
    /** In-progress shape gesture (LINE/RECT): anchor + current cell,
     * −1 = none. The preview is a pure render overlay — cells enter
     * pending only on commit (release), never mid-drag. */
    private int shapeStartX = -1, shapeStartY = -1, shapeEndX = -1, shapeEndY = -1;
    private int shapeButton = 0;

    private void shapeCells(PaintCanvas.CellVisitor visitor) {
        if (tool == Tool.RECT) {
            PaintCanvas.rectOutline(shapeStartX, shapeStartY, shapeEndX, shapeEndY, visitor);
        } else {
            PaintCanvas.line(shapeStartX, shapeStartY, shapeEndX, shapeEndY, visitor);
        }
    }

    private void cancelShape() {
        shapeStartX = shapeStartY = shapeEndX = shapeEndY = -1;
    }
```

- [ ] **Step 2: Anchor on click.** In `mouseClicked`, after the FILL branch and before the (now BRUSH-guarded) apply block, insert:

```java
        if ((tool == Tool.LINE || tool == Tool.RECT) && canvas != null) {
            int cx = (int) Math.floor((mouseX - boardX()) / cell);
            int cy = (int) Math.floor((mouseY - boardY()) / cell);
            if (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
                shapeStartX = shapeEndX = cx;
                shapeStartY = shapeEndY = cy;
                shapeButton = button;
                UISounds.tick(1.1F);
                return true;
            }
        }
```

- [ ] **Step 3: Track the drag.** In `mouseDragged`, insert before the BRUSH-guarded apply:

```java
        if (shapeStartX >= 0) {
            shapeEndX = Mth.clamp((int) Math.floor((mouseX - boardX()) / cell), 0, cols - 1);
            shapeEndY = Mth.clamp((int) Math.floor((mouseY - boardY()) / cell), 0, rows - 1);
            return true;
        }
```

- [ ] **Step 4: Commit on release.** In `mouseReleased`, insert ABOVE the `lastStrokeCell = -1;` line:

```java
        if (shapeStartX >= 0) {
            byte color = (byte) (shapeButton == 1 ? 0 : selected + 1);
            shapeCells((x, y) -> paintCell(y * cols + x, color));
            cancelShape();
            endGesture();
            flush();
            UISounds.tick(shapeButton == 1 ? 0.9F : 1.1F);
            return true;
        }
```

- [ ] **Step 5: Ghost preview.** In `render(...)`, insert directly after the canvas cell loop (before the tool row rendering):

```java
        if (shapeStartX >= 0) {
            int ghost = (PALETTE[selected] & 0x00FFFFFF) | 0x80000000;
            int ghostArgb = shapeButton == 1 ? 0x80101216 : ghost;
            shapeCells((cx2, cy2) -> graphics.fill(
                    bx + cx2 * cell, by + cy2 * cell,
                    bx + cx2 * cell + cell, by + cy2 * cell + cell, ghostArgb));
        }
```

- [ ] **Step 6: ESC cancels (and must not close the screen).** In `keyPressed`, insert ABOVE the tool-shortcut switch:

```java
        if (keyCode == 256 && shapeStartX >= 0) { // ESC mid-shape: cancel, keep screen
            cancelShape();
            return true;
        }
```

Also add `cancelShape();` in `onClose()` directly before `endGesture();` (a screen close mid-preview loses only the preview — spec).

- [ ] **Step 7: Build gate** — expected `EXIT: 0`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java
git commit -m "Paint tools T5: line + rectangle with ghost preview, ESC cancel"
```

---

### Task 6: Eyedropper

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java`

**Interfaces:**
- Consumes: Task 2 `tool`/`selectTool`; Task 3 `paletteIndexOf`.
- Produces: nothing new.

- [ ] **Step 1: Pick branch.** In `mouseClicked`, after the shape-anchor branch and before the BRUSH apply block, insert:

```java
        if (tool == Tool.EYEDROPPER && canvas != null) {
            int cx = (int) Math.floor((mouseX - boardX()) / cell);
            int cy = (int) Math.floor((mouseY - boardY()) / cell);
            if (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
                byte picked = paletteIndexOf(canvas[cy * cols + cx]);
                if (picked != 0) { // blank tap stays on the eyedropper
                    selected = picked - 1;
                    selectTool(Tool.BRUSH); // auto-return, like every paint app
                }
                return true;
            }
        }
```

- [ ] **Step 2: Build gate** — expected `EXIT: 0`.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java
git commit -m "Paint tools T6: eyedropper (pick color, auto-return to brush)"
```

---

### Task 7: Changelog, class doc, verification handoff

**Files:**
- Modify: `CHANGELOG.md`, `src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java` (class javadoc only)

- [ ] **Step 1: Changelog.** Under `## Unreleased`, insert as the FIRST bullet:

```markdown
- **Paint grew a toolbox** — bucket fill (floods across merged-wall
  seams), line and rectangle tools with a live preview, an eyedropper,
  and a 32-step undo that never reverts paint someone else laid down
  after you. Tool row sits above the color swatches; B/F/L/R/I pick
  tools, Z undoes. Client-only, pairs with 1.11.0.
```

- [ ] **Step 2: Class doc.** In `PaintScreen`'s class javadoc, replace the first sentence
`🎨 "Paint" (persisted, 1.11.0): a sixteen-color doodle pad — left-click paints, right-click erases, drag to sweep, C clears.` with:

```
🎨 "Paint" (persisted 1.11.0; tools 1.11.x): a sixteen-color canvas —
brush/fill/line/rect/eyedropper tools with conflict-aware undo
(left = paint, right = erase, C clears, Z undoes; every tool is a
client-side gesture that decomposes into ordinary stroke cells).
```

- [ ] **Step 3: Full build gate** — expected `EXIT: 0`.

- [ ] **Step 4: Commit**

```bash
git add CHANGELOG.md src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java
git commit -m "Paint tools T7: changelog + class doc"
```

- [ ] **Step 5: Hand the spec's test matrix to the user** (dev client, plus a second account for the conflict-undo case) — the matrix lives in `docs/superpowers/specs/2026-08-10-paint-tools-design.md` §Test matrix. Release remains user-gated.
