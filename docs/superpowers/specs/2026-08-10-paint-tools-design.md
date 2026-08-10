# Paint v2 — drawing tools (design)

Date: 2026-08-10 · Status: approved (user, same day)
Origin: user request post-1.11.0 ("paint app needs tools too like
bucket fill etc"), brainstormed same session.

## Decisions (locked with the user)

- Tool set: **brush** (existing), **bucket fill**, **line**,
  **rectangle (outline)**, **eyedropper**, **undo** (per gesture,
  conflict-aware, depth 32, session-local).
- Tool UI: **glyph row grouped with the palette strip** — its own
  row directly above the swatches (the 16-swatch strip already
  outspans a held canvas, so sharing one line never actually fits;
  deterministic beats adaptive here). Selected tool reuses the
  swatch highlight-bar look.
- **Painting-lock is OUT of scope** — parked into the locked-screen
  feature (Fluid Valve request): one lock mechanism, one shared
  registrar bump there. Paint v2 stays pairs-safe.
- Rectangle is outline-only; a filled rect = outline + bucket fill.
- Keyboard: B/F/L/R/I select tools, Z undoes; C stays clear.

## Architecture (the one big call)

**Every tool is a client-side gesture that decomposes into ordinary
cells** and flows through the EXISTING `PaintStrokePayload`
pending-map flush (64-cell chunks, per-tick). No new payloads, no
component/NBT changes, **registrar untouched — pairs with 1.11.0**,
ships as a drop-in patch.

Rejected alternative: server-side tool payloads (e.g. FillPayload).
Costs a registrar bump and duplicates the algorithms server-side;
buys nothing — a worst-case 4×3-wall fill is ~3,360 cells ≈ 53
chunked packets, one-shot, which the existing path already carries.

## Code placement (one-source rule)

- **`PaintCanvas`** (geometry home) gains pure grid math, beside the
  1.11.0-patch `line` walker:
  - `floodRegion(int[] grid, int cols, int rows, int startIndex)` —
    connected same-value region (4-neighbor), operating on the
    continuous stitched grid, so fills cross merged seams for free.
  - `rectOutline(x0, y0, x1, y1, CellVisitor)` — outline cells.
  - (`line` already exists.)
- **`PaintScreen`** keeps ALL gesture state: active tool, drag
  anchor, shape preview, undo history. Tool glyphs painted
  procedurally in the strip row (house style).
- Server handler, kiosk renderer, wire codecs: **untouched**.

## Tool behavior

- **Brush** — unchanged (paint / right-click erase, interpolated
  drags).
- **Bucket fill** — click flood-fills the connected same-color
  region under the cursor (blank IS a fillable color — background
  fills work). Left = selected color, right = erase the region.
  No-op when the region already is the target color.
- **Line / Rectangle** — press anchors, drag shows a ~50%-alpha
  ghost preview, release commits (cells → pending), ESC mid-drag
  cancels (and does NOT close the screen — swallow that one ESC).
- **Eyedropper** — tap a painted cell → its color becomes the
  selected swatch, then auto-switch back to brush. Tapping blank
  no-ops (stays on eyedropper).
- **Undo** — one gesture = one step (a full brush stroke from press
  to release, a fill, a committed shape). Each step records
  (index, before, after) per cell. Undo replays before-values ONLY
  into cells that still hold the step's after-value —
  **conflict-aware**: cells someone else painted over since are left
  alone (their work survives). Depth 32, history clears on screen
  close. Undone cells flow through the same pending-map wire path.
  On block views before-values come from the stitched baseline at
  gesture time (the same array the render overlay uses).

## UI details

- Six glyphs (brush, fill, line, rect, eyedropper, undo) at swatch
  size (8px), procedural fills, selected-tool underline matching the
  selected-swatch bar. Undo is a momentary button (flashes, doesn't
  stay selected); it dims when history is empty.
- Tool changes click (`UISounds.tick`), same feel as swatch picks.
- The tool row costs one SWATCH-height (+2px gap) of board height —
  still comfortably inside the 240-unit GUI budget at the 2px-cell
  merged-wall extreme (layout() already reserves MARGIN slack).

## Edge cases

- Fill on a merged wall operates on the stitched grid → decomposes
  to member BEs server-side exactly like any stroke (the continuous-
  space precedent). Members missing mid-edit (chunk unload, wall
  split) drop those cells server-side, same as today.
- Fill burst on a big wall: pending map absorbs it; flush chunks at
  64 cells/packet — one-shot burst, no cap change (verify in the
  test pass; if the packet burst ever matters, raising flush to
  spread over 2-3 ticks is a contained tweak).
- Shape preview never enters the pending map — pure render overlay;
  disconnect/close mid-preview loses only the preview.
- Undo after the OTHER side of a wall was repainted entirely: every
  cell conflicts → undo is a visible no-op; history still pops the
  step (no stuck state).
- Held/slot views have no concurrent writers — conflict checks
  trivially pass there.

## Test matrix (dev pass)

- Fill: enclosed region, background region, across a merged seam,
  right-click erase-fill, fill-into-same-color no-op.
- Line/rect: preview tracks drag, ESC cancels without closing the
  screen, commit lands on both a held tablet and a wall (second
  viewer sees it).
- Eyedropper: pick from own art + from another player's cells; blank
  tap no-ops; auto-return to brush.
- Undo: brush stroke, fill, shape; depth (33rd gesture forgets the
  1st); conflict case — second account paints over part of your
  stroke, undo leaves their cells; wall + held.
- Keyboard: B/F/L/R/I/Z/C; inventory-key guard unaffected.
- Regression: existing paint matrix (persist, merge/split slices,
  rotation, clear) + fast-stroke interpolation from the same patch.
