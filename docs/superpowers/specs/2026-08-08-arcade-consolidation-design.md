# Arcade consolidation — design spec (2026-08-08)

Target release: **1.11.0 beta.3** (batched by user decision — pure
client/catalog work, no wire changes, no registrar bump; pairs with
beta.2 but ships as beta.3 so testers run one jar).

## History note (deliberate reversal)

The 1.10.0 dev cycle built exactly this hub (`Program.ARCADE(6)`,
`ArcadeHubScreen`) and the user then chose per-game apps before
committing; the hub was deleted UNCOMMITTED (verified — no commit
carries the file) and id 6 retired. This spec re-reverses that with
the user's explicit confirmation (2026-08-08): the store has since
grown to 27 rows and the user independently asked for store
categories — consolidation now serves the same pain. Id 6 stays
retired; the new program takes id 28.

## Summary

One **Arcade** app replaces the 19 individual game apps in the
catalog. Open Arcade → scrollable game shelf with best scores → tap
launches the game → ESC returns to the shelf. Existing rosters and
pins that reference game keys migrate to Arcade automatically.

## Design

- **`Program.ARCADE(28, "arcade", 0xFF169C9C,
  "create:linked_controller")`** — teal chip, Linked Controller icon
  (the July dress). NOT a game (`game=false`). Normal store app:
  lang `program.linktablet.arcade` ("Arcade") + `.desc`.
- **`ArcadeHubScreen(SignalView view)`** — the StoreScreen shelf
  pattern: header (title, pin via `OverlayPin` + `Program.ARCADE`,
  Home), scrollable rows over `Programs` entries with `game=true` in
  enum order. Per row: chip color + icon item + display name + best
  score (`ClientPrefs.gameBest(key)`; hide the score text when 0).
  No store descriptions on rows (user-approved lean look — name +
  best score). Row tap → `SecretGames.createApp(key, view)` with the
  hub set as the ESC return (`ArcadeScreen.setReturnProgram(ARCADE)`
  — the exact July mechanism, still present for the retired hub's
  sake; verify the setter survives in `SecretGames`/`ArcadeScreen`
  and re-add if the round-3 deletion stripped it).
- **Catalog shrink**: `Programs.catalog()` (and therefore the App
  Store shelf and the launcher's add flow) excludes `game=true`
  programs. The 19 enum constants, their keys, lang entries, and
  `SecretGames` dispatch stay — keys are frozen (best-score prefs,
  secret-pip cabinet titles, kiosk `screen_program` values in the
  wild).
- **Migration**: `Programs.fromKeys` maps any `game=true` key →
  ARCADE, deduped (a roster of Snake + 2048 + Signals loads as
  Arcade + Signals). Same path covers overlay pin descriptors
  (`@snake` → the signals fallback is NOT wanted; pins resolve
  programs via the same key lookup — verify the pin restore path
  goes through `Programs.byKey`: a game key there should resolve to
  the game program still, which is acceptable — pins keep working;
  only ROSTER keys migrate). Kiosks left on a game key keep
  rendering that game's label face and tap/Home work unchanged.
- **ClientHooks.screenFor**: `case ARCADE -> new ArcadeHubScreen(view)`;
  game programs keep their existing dispatch (kiosk taps on a
  game-faced kiosk still open that game).
- **Overlay**: no Arcade overlay in v1 — the hub screen simply hides
  the pin button (games are fullscreen experiences; a pinned Arcade
  pane has no obvious content, and the `contentFor` signals-fallback
  would feel wrong). Explicit v1 limit.
- **Kiosk face**: ARCADE on a kiosk shows the generic label face
  ("Arcade") — the existing default branch, zero work; tap opens the
  hub GUI via the generic non-signals branch.

## Explicitly out of scope

- No wire/registrar/component changes of any kind.
- Secret-pip easter egg unchanged (launches return to the signals
  grid, not the hub).
- App Store categories (separate roadmap item — this consolidation
  reduces its urgency but doesn't replace it).

## Test matrix (beta.3 additions)

- Store shows Arcade, shows NO individual games; launcher add flow
  likewise.
- Hub: scroll, launch each of a few games, ESC returns to shelf,
  best scores display (and update after beating one).
- Roster migration: a beta.2 tablet with several game tiles loads as
  ONE Arcade tile + its other apps; re-saving the roster writes the
  migrated form.
- Secret pip trigger still launches its game and ESC returns to the
  SIGNALS grid (easter egg intact).
- Kiosk left showing a game (set pre-beta.3): face renders, tap
  opens the game, Home exits.
- Regression: store search, Twitch, Monitor, signals all untouched.
