# Paint on walls — design spec (2026-08-09)

Target release: **1.11.0 beta.4** (batched into the open pairing
break; registrar "22"→"23"). Tester idea (Tommy): the Paint app's
canvas shows on placed tablets and scales across merged walls.

## Summary

Paint graduates from session-only doodles to a persisted per-tablet
canvas: 20×14 cells of the 16-color signal palette. Placed tablets
display it; merged walls stitch every member's canvas into one big
picture (a 4×3 wall paints at 80×42) and the GUI edits the whole
stitched surface, writing each stroke through to the tablet that owns
that pixel. Slices travel with their tablets: split a mural and each
tablet carries its piece; re-merge in the same arrangement and it
reassembles. GUI paints; the wall displays (no direct on-glass
painting in v1 — user decision).

## Data model — the slice rule

- **Canvas is ALWAYS per-tablet 20×14** (`PaintScreen.COLS/ROWS`,
  now shared constants): 280 cells, palette index 0–16 (0 = blank),
  stored nibble-tight or as a 280-byte array — implementer's choice,
  but the DISK FORM is frozen once shipped.
- New `paint_canvas` component + BE NBT tag `"paint_canvas"`;
  item↔block round-trip beside gauges/probes/twitch_channel; never
  written when all-blank (theme idiom). Old tablets untouched.
- There is NO merged-surface canvas object: the merged picture is
  derived by stitching member slices at render/edit time. Every
  merge/split/pickup/break case therefore Just Works — no crop, no
  discard, no orphaned data.
- The pip-launched Paint edits the SAME canvas (user-confirmed:
  session-only behavior is gone everywhere; one canvas per tablet).

## Wire (registrar "23")

- `PaintStrokePayload(SignalTarget, List<PaintCell>)` where
  `PaintCell(int index, byte color)` — a drag-sweep batches its cells
  (cap ~64/packet, flush per tick during drags); index is in the
  TARGET tablet's local 20×14 space for held/1×1, or in CONTINUOUS
  surface space for block targets (the server maps continuous →
  member BE + local cell — the controller resolves members by
  surface_dx/dy, the surfaceLayout precedent). Server validates
  index bounds and color 0–16.
- `PaintClearPayload(SignalTarget)` — clears the whole visible
  canvas (every member on a merged target).
- Both playToServer; the canvas syncs back via normal component/BE
  sync (no playToClient needed — the gauge-editor save shape).

## GUI (PaintScreen changes)

- Loads from the view: held/slot → component; block → the controller
  + members stitched (`surfaceW·20 × surfaceH·14`).
- Cell size adapts: `CELL = clamp(min(availW/cols, availH/rows), 2, 7)`
  — chunky on 1×1, finer on walls; the palette strip unchanged.
- Strokes update the local canvas optimistically AND batch into
  PaintStrokePayloads; C/clear button sends PaintClearPayload.
- The screen stays an ArcadeScreen (ESC-return behavior, hub row,
  secret pip all unchanged).

## Wall face

- `renderPaintFace`: colored fills stitched from member slices
  across the continuous panel, honoring `effectiveRotation`;
  fills-only (three-pass rule trivially satisfied). Cells scale to
  the glass (each block face shows its own 20×14 at ~0.8 texel per
  cell — chunky-pixel look). All-blank canvas draws a faint "Paint"
  label (the label-face fallback look) so the face isn't a void.
- Dispatch: `case PAINT ->` in the BER face switch (PAINT is a game
  constant — the switch's game handling: games currently fall to the
  label face; add the explicit case above it).
- Member slices client-side: the controller's renderer reads member
  BEs via level lookup (positions from surface_dx/dy) — render-time
  stitching, no extra sync (each member BE already syncs its own
  canvas).

## Kiosk navigation restore

Hub game launches from a BLOCK-bound GUI send `SetProgramPayload`
again (the Arcade consolidation accidentally dropped kiosk-nav for
game launches — final-review finding 2). This is what lets a wall be
SET to Paint: wall GUI → Arcade → Paint → the wall shows the canvas.
Held/slot launches stay pref-only as today.

## Explicitly out of scope (v1)

- No painting directly on the placed glass (user decision — GUI
  edits, wall displays; the slider-drag machinery makes this a clean
  future add).
- No resolution beyond 20×14 per tablet; no image import; no extra
  palette colors.
- Paint stays an Arcade game (no store row).

## Test matrix (beta.4 additions)

- Paint on a held tablet → close → reopen (persists) → place the
  tablet → the wall shows the picture.
- Wall GUI painting: strokes appear live on the wall for a second
  viewer; clear wipes the whole wall.
- Merge two painted 1×1s → both slices show in place; paint across
  the seam → both tablets' slices update; split → each carries its
  piece; re-merge same arrangement → mural reassembles.
- Rotation: rotated merged wall shows the picture correctly.
- Pickup: break a painted tablet → place elsewhere → picture intact;
  the item survives a chest round-trip.
- Secret-pip Paint edits the same canvas (no more session-scratch).
- Kiosk nav: wall GUI → Arcade → Paint sets the wall face; ESC back
  to hub; Home exits; the wall keeps showing Paint.
- Old-world load: tablets without `paint_canvas` untouched; registrar
  "23" pairing break vs beta.3 (everyone swaps together).
- Regression: other game launches from a wall GUI nav the face again
  (restored behavior); held launches unchanged.
