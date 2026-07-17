# Icon-friendly defaults — scoping

Tester feedback (approved to build 2026-07-17): *"some items render poorly
as app icons."* This doc lays out **what the problem actually is** and
**concrete options** so the user can pick a direction before any code.
Nothing here is built yet.

## How icons work today

An app's tile/row icon comes from `SignalApp.iconStack()`:

1. If the app has a **custom icon item** (`icon` present) → render that.
2. Else → render the **first frequency's** item(s): a one-item frequency
   draws its lone item centered; a two-item pair draws the two items
   overlapping with the little red/blue link bars underneath.

Every draw is a plain `graphics.renderItem(stack, x, y)` at vanilla 16×16,
at three sites: the GUI grid tile, the GUI list row, and the world screen
renderer (`TabletScreenRenderer`, placed + held).

## Why some items "render poorly"

`renderItem` uses each item's real model, so the failure modes are:

- **3D block models** (furnace, chest, most blocks) render in the vanilla
  angled "block" perspective with real depth. On a small chip that reads
  as busy, and the depth is why we already had to z-lift the slider level
  numerals (CLAUDE.md note) so blocks wouldn't bury them.
- **Pair overlap** — two 3D blocks drawn 12px apart overlap into visual
  mush; fine for two flat items (redstone + torch), bad for two blocks.
- **Tall/entity-ish models** (beds, banners, shulker boxes, signs,
  skulls) extend outside the 16×16 footprint or render as their BEWLR
  fallback and look cropped.
- **Tinted/animated items** are fine; not a real problem.

The common thread: the icon is only "poor" for a subset of items, mostly
**3D block models and pair-overlap of two blocks**. Flat 2D items already
look great.

## Options (pick one or mix)

### A. Better default-icon *selection* (data-only, safest)
When no custom icon is set, be smarter about what we draw:
- For a two-item pair where both are blocks, draw only ONE (no overlap),
  or shrink the pair so both fit.
- Prefer whichever frequency item is a flat 2D item as the shown icon.
- **Pros:** no render-pipeline risk, no new art, purely in `iconStack()`
  / the render sites' pair branch. **Cons:** doesn't help a single block
  that just looks busy small.

### B. Flat-render mode for blocks (render tweak)
Detect block-model items and render them front-flat (GUI-style, no angled
depth) or scaled down a hair, instead of the 3D perspective.
- **Pros:** fixes the busy/depth problem at the source, every item still
  shows its own icon. **Cons:** touches all three render sites; the world
  renderer already has strict batching rules (CLAUDE.md) — a second render
  path there needs care.

### C. Curated fallback glyph set (new art / mapping)
When an item renders poorly (or always, when no custom icon), draw a clean
flat symbol from a small mod-supplied set (lamp, door, piston, gate,
generic "signal") chosen by item tag/category.
- **Pros:** always crisp, on-brand with the chrome look. **Cons:** most
  work (art + a mapping table); changes the default look of existing apps
  unless gated to "poor" items only.

### D. Just make the custom-icon picker more discoverable
The editor already supports a custom icon; maybe testers didn't find it.
Cheapest "fix": surface the icon picker better and document it, do nothing
to rendering.
- **Pros:** near-zero code. **Cons:** doesn't help defaults, which is what
  was actually reported.

## Recommendation

Start with **A** (smart pair/flat-item selection) — it's the lowest-risk
chunk of the reported problem and needs no art. If a single busy block
still bothers testers after that, layer on **B** for block-model items.
Hold **C** unless the user wants a distinct visual identity for icons.

## Open questions for the user
1. Is the complaint mostly about **two-block pairs overlapping**, or also
   about **single blocks** looking busy? (Changes A-vs-B priority.)
2. Should any change apply only when **no custom icon** is set, or also
   override a custom icon that happens to be a bad-rendering item?
3. Appetite for **new flat art** (option C), or keep it code-only?
