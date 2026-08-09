# Paint on Walls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist the Paint app's 20×14 canvas per tablet, display it on placed tablets, and stitch member slices into one editable picture across merged walls (1.11.0 beta.4, tester idea by Tommy).

**Architecture:** The slice rule — every tablet owns its own 20×14 canvas (`paint_canvas` component/BE NBT); merged pictures are stitched at edit/render time from member slices, so merge/split/pickup can never lose art. Strokes travel in continuous surface space (`PaintStrokePayload`) and the server maps each cell to the owning member BE. A shared `PaintCanvas` class is the ONE home for dimensions and the continuous↔member mapping. Spec: `docs/superpowers/specs/2026-08-09-paint-walls-design.md`.

**Tech Stack:** NeoForge 1.21.1; no new deps. Gate per task: `./gradlew build` green.

## Global Constraints

- **Registrar "22" → "23"** (inside the open 1.11.0 break; each wire growth gets its own fence).
- DISK FORM frozen once shipped: BE NBT `"paint_canvas"` byte array; component id `"paint_canvas"`; 280 cells of palette index 0–16 (0 = blank); never written when all-blank (theme idiom).
- Canvas is ALWAYS per-tablet 20×14 — no merged-surface canvas object anywhere.
- Per-task commits on `tablet-overlay`, push allowed; trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Mod version bumps to `1.11.0-beta.4` only in the final task.

---

### Task 1: PaintCanvas + persistence

**Files:**
- Create: `src/main/java/com/modpack/linktablet/PaintCanvas.java`
- Modify: `src/main/java/com/modpack/linktablet/registry/ModDataComponents.java`
- Modify: `src/main/java/com/modpack/linktablet/block/TabletBlockEntity.java`
- Modify: `src/main/java/com/modpack/linktablet/client/SignalView.java`

**Interfaces:**
- Produces: `PaintCanvas` (constants + mapping, below); `ModDataComponents.PAINT_CANVAS` (`byte[]`); `TabletBlockEntity.getPaintCanvas()` (byte[280], zero-filled when unset) / `setPaintCanvas(byte[])` / `setPaintCell(int localIndex, byte color)`; `SignalView.paintCanvas()` (byte[280], default blank).

- [ ] **Step 1:** The shared class — the ONE home for paint geometry (root package, beside Programs, because server payload mapping AND client screen/renderer all use it):

```java
package com.modpack.linktablet;

/**
 * Paint canvas geometry (1.11.0 "paint on walls", tester idea): every
 * tablet owns a fixed COLS×ROWS slice; merged walls stitch member
 * slices into a continuous picture at edit/render time — there is no
 * merged canvas object (the slice rule: merge/split/pickup can never
 * lose art). This class is the ONE home for the dimensions and the
 * continuous↔member cell mapping — GUI, server stroke handler, and
 * face renderer all map through here; never fork the math.
 */
public final class PaintCanvas {

    public static final int COLS = 20;
    public static final int ROWS = 14;
    public static final int CELLS = COLS * ROWS;
    /** Palette indices 1..16 paint; 0 is blank. */
    public static final int MAX_COLOR = 16;

    public static boolean isBlank(byte[] canvas) {
        for (byte cell : canvas) {
            if (cell != 0) return false;
        }
        return true;
    }

    public static byte[] blank() {
        return new byte[CELLS];
    }

    /** Continuous cell index for a surface of {@code surfaceW} blocks:
     * column-major-free, plain row-major over the stitched grid. */
    public static int contIndex(int contX, int contY, int surfaceW) {
        return contY * (surfaceW * COLS) + contX;
    }

    /** Which member block (dx, dy from the controller, surface space)
     * owns this continuous cell. */
    public static int memberDx(int contX) {
        return contX / COLS;
    }

    public static int memberDy(int contY) {
        return contY / ROWS;
    }

    /** The cell's index inside its owning member's local 20×14. */
    public static int localIndex(int contX, int contY) {
        return (contY % ROWS) * COLS + (contX % COLS);
    }

    private PaintCanvas() {
    }
}
```

- [ ] **Step 2:** Component beside TWITCH_CHANNEL — persistent `Codec.BYTE_BUFFER` xmapped to `byte[]` (copy in/out), network `ByteBufCodecs.byteArray(PaintCanvas.CELLS)`; javadoc: "Paint canvas (1.11.0); absent = blank (never written all-blank — the theme idiom); 280 palette indices, disk form frozen." If `Codec.BYTE_BUFFER` proves awkward in this MC version, use `Codec.INT_STREAM` xmapped to byte[] — note the choice in the report; the NBT side below is the frozen form either way.
- [ ] **Step 3:** BE — `private byte[] paintCanvas = PaintCanvas.blank();` + `getPaintCanvas()`; `setPaintCanvas(byte[])` (Arrays.equals guard + setChanged + sendBlockUpdated — the setTwitchChannel shape); `setPaintCell(int localIndex, byte color)` (bounds + no-op guard, then mutate a COPY and delegate to setPaintCanvas so the sync path stays one road); `saveAdditional` writes `tag.putByteArray("paint_canvas", paintCanvas)` only when `!PaintCanvas.isBlank(...)`; `loadAdditional` reads `tag.getByteArray` (empty → blank, wrong length → blank); `loadFromItem`/`toItemStack` round-trip the component (set only when non-blank).
- [ ] **Step 4:** `SignalView.paintCanvas()` — default `PaintCanvas.blank()`; Hand/Slot read the component (`getOrDefault(..., new byte[0])`, wrong length → blank); Block reads `be.getPaintCanvas()` (controller-resolved as the siblings are).
- [ ] **Step 5:** Build green. **Step 6:** Commit `1.11.0-dev: paint_canvas persistence — the slice rule`.

### Task 2: Stroke wire + server mapping (registrar "23")

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/network/ModNetworking.java`

**Interfaces:**
- Consumes: `PaintCanvas` mapping, `TabletBlockEntity.setPaintCell/setPaintCanvas/getSurfaceW/getSurfaceH`, the private `resolveStack`/`tabletDistSqr` helpers in the same file, member lookup (read how `TabletSurfaceScanner` or the BE resolves member positions from surface dx/dy — the controller knows its FACING/orientation; find the existing helper that walks members, e.g. what `setSurfaceRole`'s callers use, and reuse it; if none is public, iterate the surface rectangle via the same offset math the scanner uses and `level.getBlockEntity` each member — note the approach in the report).
- Produces: `PaintStrokePayload(SignalTarget target, List<PaintCell> cells)` with nested `PaintCell(int index, byte color)` (index continuous for block targets, local for item targets; list cap 64); `PaintClearPayload(SignalTarget target)`; registrar "23".

- [ ] **Step 1:** Payload records (the SetProbePayload shape; PaintCell codec: VAR_INT + BYTE composite; list `ByteBufCodecs.list(64)`), ids `"paint_stroke"` / `"paint_clear"`, both playToServer, registrar comment `// "23": 1.11.0 paint on walls — PaintStrokePayload/PaintClearPayload added (still inside the 1.11.0 break).` and fence → `"23"`.
- [ ] **Step 2:** `handlePaintStroke` — resolve like handleSetProbe: item targets → read component (default blank), apply local-index cells (bounds `0..CELLS`, color `0..MAX_COLOR`, reject others silently), write back component (remove when blank); block targets → controller + `surfaceW/H`, for each cell: continuous index → `contX = index % (surfaceW*COLS)`, `contY = index / (surfaceW*COLS)`, bounds-check against `surfaceW*COLS × surfaceH*ROWS`, then `memberDx/Dy` + `localIndex` → find the member BE → `member.setPaintCell(local, color)`. Batch member writes: collect cells per member first, apply each member's batch through ONE `setPaintCanvas` call (one sync packet per touched member per stroke packet, not per cell).
- [ ] **Step 3:** `handlePaintClear` — item: remove component; block: blank every member's canvas (same member walk).
- [ ] **Step 4:** Build green. **Step 5:** Commit `1.11.0-dev: paint stroke wire — continuous-space strokes, registrar "23"`.

### Task 3: PaintScreen rework

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/screen/PaintScreen.java`

**Interfaces:**
- Consumes: `SignalView.paintCanvas()`, Block views' surface dims (add whatever accessor the view lacks — check `SignalView.Block` for surfaceW/H exposure; if absent, read the controller BE like its other overrides do and add `surfaceW()/surfaceH()` defaults of 1), `PaintCanvas` mapping, both payloads.
- Produces: the persisted-canvas Paint experience; COLS/ROWS/CELL private constants replaced by `PaintCanvas.COLS/ROWS` + computed cell size.

- [ ] **Step 1:** Dimensions: `cols = surfaceW*PaintCanvas.COLS`, `rows = surfaceH*PaintCanvas.ROWS` (1×1 for held/slot); `CELL` becomes a field: `Mth.clamp(Math.min(availW/cols, availH/rows), 2, 7)` (availW/H from the ArcadeScreen board budget — read `boardW/boardH`'s callers to size correctly).
- [ ] **Step 2:** Canvas state: initialize from the view — held/1×1 copies `view.paintCanvas()`; merged stitches member slices client-side (Block view: controller + `level.getBlockEntity` per member — same walk the renderer will use; put the client-side stitcher as a static helper HERE and let Task 4's renderer call it: `public static int[] stitchArgb(TabletBlockEntity controller)` returning ARGB per continuous cell, mapping palette indices through the PALETTE array — move PALETTE to `PaintCanvas`? NO: palette colors are presentation; keep PALETTE here but make it `static final` public for the renderer).
- [ ] **Step 3:** Strokes: optimistic local update + accumulate `PaintCell`s in a pending list; flush a `PaintStrokePayload` per tick while dragging (the BlockSliderDrag per-tick precedent) and on mouseReleased/close; block targets send continuous indices, item targets local. Clear button/C sends `PaintClearPayload` + local blank. While a block-bound screen is open, re-read synced state each frame like MonitorScreen's channels — EXCEPT keep unflushed optimistic cells painted over it (pending list wins until flushed).
- [ ] **Step 4:** Build green. **Step 5:** Commit `1.11.0-dev: PaintScreen — persisted canvas, merged-wall editing`.

### Task 4: Wall face + kiosk-nav restore

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/render/TabletScreenRenderer.java` (+`renderPaintFace`)
- Modify: `src/main/java/com/modpack/linktablet/client/render/TabletBlockEntityRenderer.java` (face switch)
- Modify: `src/main/java/com/modpack/linktablet/client/screen/ArcadeHubScreen.java` (block-view nav)

**Interfaces:**
- Consumes: `PaintScreen.stitchArgb(controller)` (Task 3), `PaintCanvas` dims, renderMonitorFace's pass/geometry shape, `ClientHooks`' `SetProgramPayload` send in `showProgram` (read it — the hub restores the same behavior).

- [ ] **Step 1:** `renderPaintFace(poseStack, buffers, TabletBlockEntity controller, rotation, theme, lit, packedLight, surfaceW, surfaceH, caseTint)` — fills only: stitched ARGB per continuous cell scaled to the glass (cells span `glassW/cols` texels; skip blank cells so the screen background shows through); all-blank → faint centered "Paint" label via the label-face text style (that one label is the only text — still pass-ordered: fills, then text). Dispatch `case PAINT ->` ABOVE the games' fall-through in the BER switch (PAINT is a game constant — verify games currently hit the `default` label face and add the explicit case before it).
- [ ] **Step 2:** Kiosk-nav restore in `ArcadeHubScreen.mouseClicked`'s launch: when `view instanceof SignalView.Block`, send `new ModNetworking.SetProgramPayload(view.target(), program.key())` before `setScreen` (mirrors `ClientHooks.showProgram`'s block branch — cite it in a comment; the consolidation dropped this, final-review finding 2).
- [ ] **Step 3:** Build green. **Step 4:** Commit `1.11.0-dev: paint wall face + kiosk-nav restore for hub launches`.

### Task 5: Docs + beta.4

**Files:**
- Modify: `CHANGELOG.md`, `docs/NEXT_SESSION.md`, `CLAUDE.md`, `gradle.properties`

- [ ] **Step 1:** CHANGELOG Unreleased: Paint-on-walls bullet (persists on the tablet, shows on placed tablets, merged walls become murals painted across every member, pieces travel with their tablets, credit "idea: Tommy"); registrar note → "18"→"23".
- [ ] **Step 2:** NEXT_SESSION top status (beta.4 = beta.3 + paint walls; slice rule summary) + spec test matrix VERBATIM. CLAUDE.md gotcha: `PaintCanvas` is the ONE geometry/mapping home (GUI, stroke handler, renderer — never fork); disk form frozen (`paint_canvas` byte array, 0=blank); slice rule (no merged canvas object); strokes continuous-space for block targets.
- [ ] **Step 3:** `gradle.properties` → `1.11.0-beta.4`; `./gradlew build`; jar path in report. **Step 4:** Commit `1.11.0-beta.4: paint on walls + docs`. STOP — push/jar/checklist are the controller's steps.

---

## Self-review

**Spec coverage:** slice rule + persistence + round-trip (T1), stroke/clear wire + continuous mapping + member batching (T2), GUI rework incl. adaptive cells, stitching, optimistic strokes, pip-same-canvas (T3 — pip path reuses the same screen), wall face + blank label + kiosk-nav restore (T4), docs/beta.4/matrix (T5). Out of scope items are absences.

**Placeholders:** none — T1 code complete; T2 names exact mapping math and batching; T3/T4 carry precise deltas with pattern cites. Two verify-notes (BYTE_BUFFER codec viability, member-walk helper existence) are implementation checks with stated fallbacks.

**Type consistency:** `PaintCanvas.COLS/ROWS/CELLS/MAX_COLOR/blank/isBlank/contIndex/memberDx/memberDy/localIndex` used identically in T2/T3/T4; `setPaintCell(int, byte)` (T1) used by T2; `stitchArgb(TabletBlockEntity)` defined T3, consumed T4; payload names/ids single-sourced in T2.
