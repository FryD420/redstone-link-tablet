# Multiblock screens — design (not yet implemented)

Combine multiple placed tablets into one large screen/control panel. This is
the agreed design; implementation is deferred to a future round. Modeled on
Create's Flap Display (`com.simibubi.create.content.trains.display`):
adjacency-driven assembly with a controller block entity that owns the whole
surface — no assembly tool, no GUI.

## Formation

- Placing a tablet adjacent and **coplanar** to another tablet with identical
  `FACE` + `FACING` auto-merges them into the largest filled rectangle.
- Re-evaluated in `onPlace`/`updateShape`, exactly like `FlapDisplayBlock`
  (`getConnection`/`updateColumn` pattern).
- Placement is the whole mechanic: no new items, no linking tool. Breaking any
  member splits the surface back into individual tablets.

## Controller and parts

- Controller = the top-left tablet in screen-local space.
- Parts store a `controllerOffset` (byte dx, dy) in their BE and resolve the
  owner via `getController()` — the Flap Display approach.
- **No new blockstate properties.** Connectivity is discovered at the BE level
  (like flap displays' `isController`/`xSize`/`ySize`), avoiding a state
  explosion on a block that already carries FACE × FACING × LIT.
- The controller BE owns: app list, theme, screen-list mode, content rotation.

## Merge semantics (data is never destroyed)

- The controller's apps spread across the merged glass at bigger tiles.
- Non-controller tablets keep their own app lists **dormant** in NBT and
  restore them on split. Merging and splitting is always lossless.
- Theme and rotation shown are the controller's; parts keep theirs dormant.

## Limits

- Max surface: 3×3 blocks (open question below: allow 4×3 widescreen?).
- Glass texel space scales to `(10·w) × (12·h)`.
- `TabletScreenMath.gridLayout` stays the single count→cols×rows table, but
  gains a max-cols/max-rows parameter derived from surface size — still one
  source of truth.

## Rendering

- Only the controller's BER renders; parts early-return.
- One quad/icon/text three-pass draw covers the whole surface (the three-pass
  rule is per-BER, so merging *reduces* batch churn).
- The controller's `getRenderBoundingBox` must cover the full surface.
- Wrench content-rotation applies to the whole surface (controller-owned).

## Hit-test

- Clicks on part blocks translate into controller-relative UV and delegate to
  a widened `TabletScreenMath.hitPip` — renderer and hit-test stay in lockstep
  through the same class, as today.

## Momentary holds

- Holds key on the **controller pos** (the `BLOCK_HOLD` path in
  `TabletTransmitterHandler` already keys on a BlockPos).
- On split (any member broken), clear holds for the controller pos — same
  self-healing rule as app reorder.

## Open questions (decide before implementation)

1. Max size: 3×3, or allow 4×3 for widescreen wall panels?
2. Should merged surfaces raise `MAX_APPS` (currently 32 per tablet)?
3. Cheaper "extension tablet" part item, or full tablets only?
4. Case-color mismatches: recommend each block keeps its own bezel tint while
   screen content renders uniformly — confirm the look in-game.
