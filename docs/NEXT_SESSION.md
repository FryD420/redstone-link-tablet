# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

## ▶ START HERE next session (state as of 2026-08-17 — 1.13.0 RELEASED, uploads pending)

- **v1.13.0 DEV PASS PASSED + TAGGED 2026-08-17** — the user walked
  both checklists in the dev client: tooltips all good; dropped-tablet
  rows all good; the throw blink they confirmed live drove one
  addition, the add-only `ItemTossEvent` fast path (instant
  re-register on deliberate throws; re-test confirmed "throw blink is
  gone"). Tagged v1.13.0, merged to main, pushed. REMAINING: user
  uploads to both platforms (game 1.21.1, NeoForge, Java 21, Create
  required) + updates the family server/pack (server side optional —
  1.13.0 pairs with 1.12.x, but the dropped-tablet feature only works
  once the SERVER has it) + Discord announcement (user posts).
  Listing text: DESCRIPTION.md gained dropped/framed lines — paste the
  refreshed listing at upload time.
- **v1.13.0 — hover tooltips AND dropped-tablet transmission, CODE +
  BUILD DONE, dev-client pass and uploads still OUTSTANDING for BOTH**:
  the tooltip cycle's 1.12.1 was never released or uploaded, so the
  owner folded it together with the dropped-tablet feature — "ship and
  test it all together" — as a single 1.13.0. Two independent
  subagent-driven SDD builds, both review-clean, ledgers in
  `.superpowers/sdd/`:
  - **Hover tooltips** (7 tasks). A shared `client/screen/ScreenTips`
  collector lets each screen register the rect it already computed for
  drawing a control; one call at the end of `render` paints the
  last-registered rect under the cursor (click handling untouched, on
  every screen). Coverage: Signals, Launcher, App Store, Arcade hub,
  Signal edit, Probe edit, Gauges, Monitor, Paint, Twitch, Clock,
  Calculator, the three picker overlays, and the two floating windows.
  Previously-wordless glyphs now name themselves (chain-link, padlock,
  solo, eyedropper, emote toggle — chain-link had NO tooltip at all
  before this, on either Signals or Launcher). Paint's tool tips name
  their keyboard shortcuts inline ("Brush (B)", "Fill (F)", "Line (L)",
  "Rectangle (R)", "Pick colour (I)"). Deliberately untipped: store
  rows/arcade rows/theme rows/named gauge tiles (already show their own
  name — a tooltip echoing visible text is noise), the Paint canvas
  cells (a tooltip trailing the cursor mid-draw would make the app
  unusable), the Calculator keypad, `PickerOverlay`'s item grid (vanilla
  item tooltip already covers it, better), and window body rows.
  Client-only — no wire/registrar/component/NBT change of any kind.
  - **Dropped-tablet transmission** (3 tasks).
  `compat/DroppedTabletHandler` is the third transmitter anchor
  (player scan / placed BE / entity sweep): a tablet lying on the
  ground, or sitting in an item frame, keeps broadcasting its
  toggled-ON signals from where it is — throw a scene tablet into the
  machine room and the scene keeps running. The Frequency Monitor
  names these members ("Dropped tablet (player)" / "Framed tablet").
  Server-side only, no wire/registrar/component/NBT change. This is
  the feature-queue item from the 2026-08-11 night wrap below — now
  shipped in code, out of the queue.

  Both halves: **1.13.0 PAIRS WITH 1.12.x**, no coordinated server
  update needed (registrar untouched at "24"). `mod_version` bumped,
  `./gradlew build` green, jar at `build/libs/linktablet-1.13.0.jar`.
  The `runServer` dedicated-server boot gate (mandatory since the
  dropped-tablet handler is server-side — the 1.10.2 dist-cleaner
  lesson) was run this session: clean boot to "Done", no dist-cleaner
  or classloading errors.
  **STILL OUTSTANDING (owed by the project owner, not done this
  session)**: the in-client dev pass covering BOTH halves together
  (owner decision — test them together) via `./gradlew runClient`:
  tooltips' visual verification, hovering every control by eye per the
  spec's test matrix (tooltip follows cursor / never clips an edge;
  state-dependent glyphs flip text — pin/unpin, lock/unlock,
  link/unlink, emotes on/off, timer start/cancel, stopwatch
  start/pause; popup/modal/picker priority over the glyph beneath;
  floating windows over a live screen; block-only glyphs absent on
  held tablets; locked screens still tip everything; signal-tile
  ellipsis-only tooltip regression; full click-regression across all
  screens including the 19 untouched game screens) PLUS dropped/framed
  tablet transmission (throw a scene tablet, item-frame case, Monitor
  classification naming) — then the actual platform uploads and
  Discord announcement once that pass is clean.
  **Residual Minor queued for the final review**: on `MonitorScreen`, a
  probe row scrolled so its header sits just above the list viewport
  can still register its remove-cross tooltip, because `renderChannel`
  runs for partially-visible channels while the click path gates on the
  viewport bounds. Cosmetic (no click-through), worth a one-line
  viewport-bounds check on the tooltip registration whenever this file
  is next touched.

## Previous START HERE (state as of 2026-08-11 — 1.12.0 LIVE)

- **v1.12.0 LIVE EVERYWHERE** (uploads user-confirmed same day —
  "everythings live"): screen lock + seam fix + title header shipped
  the same day they were built (spec re-scope → SDD build → user dev
  pass → release, one session). Remaining: tester rows below;
  reactive hotfix posture.
- **Next-session seed (user ask)**: replicate this workflow in a NEW
  mod folder — DONE same session: template BUILT and build-verified at
  DEV/mod-template (examplemod-0.1.0.jar) — copy + rename per its
  START-HERE.md; Create/JEI/EMI blocks commented out, uncomment both
  sides for a Create addon. First session in a copy: brainstorming
  skill on the new idea. (Superpowers plugin + global prefs + per-
  folder auto-memory travel free — only these files needed scaffolding.)
  Template addendum: docs/DESCRIPTION.md listing SKELETON + listing
  rules now baked in. DONE 2026-08-16: template pushed to the PRIVATE
  repo https://github.com/FryD420/mod-template (branch main) — the
  GitHub backup plan now covers it.
- **Feature queue (user, night wrap — corrected same night) — SHIPPED
  in 1.13.0 (see current START HERE)**: a
  DROPPED tablet (world item entity) should KEEP transmitting its
  toggled-ON signals from where it lies — today it goes silent
  (transmitters are player-inventory-scan driven; ground items fall
  off the network). Needs a brainstorm/spec: ItemEntity tracking,
  range anchored at the item, despawn/pickup cleanup, momentary/timer
  semantics across the drop, Monitor classification for dropped
  tablets. Gameplay upside: throwable scene tablets.
- **Polish queue (user, 2026-08-12)**: EVERY button/glyph in EVERY
  GUI gets a hover tooltip saying what it is — audit all screens
  (launcher, store, arcade hub, edit screens, gauges, monitor,
  clock, calculator, paint, twitch, overlay windows, pickers);
  TabletScreen's renderTooltip chain is the house pattern, one lang
  key each. Client-only, pairs with 1.12.0.
- **v1.12.0 content** (screen lock, credit Fluid
  Valve): use-only lock, LOCKING IS FREE / wrench unlocks (asymmetric
  rule re-decided in the dev pass — a main-hand wrench can't reach an
  unlocked GUI, so the spec's symmetric rule was undiscoverable);
  `configAllowed` choke point on all 17 config handlers; world gates;
  padlock glyph + face pip; member mirroring + scanner OR-merge (the
  bulk-sync ghost-role bug was re-fixed here via `syncToTracking` —
  final review catch). PLUS two dev-pass extras: the MERGED-SEAM
  untouchable gap fix (full-face shapes — clicks between members used
  to fall through; wire-neutral, backport candidate) and the title
  plaque in its own band above the panel (both block GUIs).
  Registrar "24" — PAIRING BREAK vs 1.11.x. Big button CUT
  (2026-08-11 re-scope). User dev pass: lock flow, seams, title all
  confirmed "SO good" in the dev client; `runServer` boot gate run at
  release. REMAINING: user uploads to both platforms + updates the
  family server/pack (BOTH sides — pairing break), Discord
  announcement (user posts). Tester rows outstanding: two-account
  mural protection, dedicated-server lock, follow-while-locked.
- **Listing nit queue for the next DESCRIPTION.md pass**: Paint
  section still misses the 1.11.1 toolbox; consider a lock bullet
  (and the seam fix implicitly makes big walls nicer) — no urgency.

## Previous START HERE (state as of 2026-08-10 night — 1.11.1 LIVE)

- **v1.11.1 LIVE EVERYWHERE** (d251886, tag v1.11.1, merged to main;
  uploads + refreshed listing description user-confirmed same day —
  "people are happy"): overlay chat wrap (10-line bottom-anchored
  budget), paint fast-stroke interpolation, and the full PAINT
  TOOLBOX — fill/line/rect/eyedropper + conflict-aware 32-step undo
  (widened for the dedicated-server flush-echo window), header tool
  bar with material-art glyphs (3 user-driven rounds, held-color
  readout on brush/pour/drop). ALL client-only — PAIRS with 1.11.0.
  Built via subagent-driven SDD (7 tasks + final fable review,
  ledger in .superpowers/sdd/). Two server-only test rows ride the
  testers: fill+instant-Z revert, two-account conflict undo.
- **Listings now also carry**: the custom "Help develop on Discord"
  badge (invite discord.gg/JwkHGpE527; source docs/discord-badge.html
  + regen command in its header, colors sampled from devins v3) and
  the 2026-08-10 screenshot set. New standing rule: every release
  gets changelog-section + announcement code blocks AND the full
  CHANGELOG.md as a file, unprompted (local memory
  release-comms-workflow).
- **License: STAYING MIT** (user decision 2026-08-11 after a full
  deliberation — addon API growth, modpack promise, ecosystem trust
  all favor it; repost sites are a DMCA matter not a license one).
  Don't re-raise; if a concrete fork/monetization incident ever
  lands, LGPL-3.0 was scoped as the fallback (future releases only —
  shipped jars stay MIT forever).
- **Screen lock + big button (Fluid Valve) is SPEC'D and PARKED**
  for the next pairing-break release (registrar "23"→"24"):
  docs/superpowers/specs/2026-08-10-screen-lock-design.md — use-only
  lock, wrench-as-key, automatic full-glass button on lock+lone-
  signal, ONE server-side wrench-in-hand rule for config payloads
  (mural protection free). Next step when revived: writing-plans.
  Credit Fluid Valve when it ships. The user's wall-painting lock is
  INSIDE this design (decision from the Paint v2 brainstorm).
- Earlier same day: screenshot shoot recovered from F2 folder +
  listings updated on both platforms (chat-wall/monitor/factory-hall/
  mural/mural-remix in docs/images/, embeds live). NEVER embed chat
  close-ups without vetting the visible text.
- DESCRIPTION.md nit for next listing pass: the Paint section
  doesn't mention the new tools yet (fill/shapes/eyedropper/undo) —
  fold into the next refresh, no urgency.

## Previous START HERE (state as of 2026-08-10 — storm save, mid-session)

- **Screenshot shoot DONE + listings UPDATED (user-confirmed "listings
  are up")**: five shots from the user's SMP live in `docs/images/`
  (chat-wall, monitor, factory-hall, mural, mural-remix), embedded in
  DESCRIPTION.md, pushed to main (da3f6eb), pasted to both platforms.
  Recovery note: shots were rescued from the instance's F2 screenshots
  folder. NEVER embed chat close-ups without vetting the visible text
  (one candidate had a slur-adjacent line — skipped, note in the
  shooting checklist).
- **1.11.1-candidate fixes IN CODE on `tablet-overlay`, pushed**
  (both client-only, pair with 1.11.0; changelog under Unreleased):
  (a) overlay Twitch chat WRAPS long messages (f8d61cc) — 10-line
  bottom-anchored budget, TwitchScreen's row shape; VERIFIED live in
  the dev client (automated: seeded prefs, quickPlay into TEST world,
  screenshots — wrap + indent + emote-on-continuation + oldest-drop
  all confirmed). (b) Paint fast-drag interpolation (c437bf7) —
  Bresenham `PaintCanvas.line` bridges cursor jumps; build green,
  USER FEEL-CHECK still owed (checklist delivered in-session).
- **Paint v2 tools SPEC approved + committed** (bc6a3cc,
  `docs/superpowers/specs/2026-08-10-paint-tools-design.md`): fill/
  line/rect/eyedropper/undo as client-side gestures over the existing
  stroke wire — pairs-safe. NEXT STEP: user spec review, then the
  writing-plans skill (implementation plan) — brainstorm was complete,
  session interrupted at the spec-review gate by a storm save.
- **Task list (session task tracker)**: #1 overlay wrap DONE; #2 paint
  interpolation in code, feel-check owed; #3 paint tools = the spec
  above; #4 locked one-big-button screen (tester Fluid Valve, Discord)
  — NOW ALSO CARRIES the wall-painting lock (user decision during the
  paint brainstorm: one lock mechanism, one shared registrar bump,
  designed together; that's why Paint v2 stays pairs-safe). #4 is
  un-brainstormed.
- Dev-client automation notes that worked (reusable): seed
  `run/config/linktablet-client.properties` (overlayPin=slot:0@twitch,
  channel, lastProgram) + `pauseOnLostFocus:false` + TEMP
  `--quickPlaySingleplayer TEST` programArguments in build.gradle
  (REVERTED after — keep it that way), screenshot via PowerShell
  CopyFromScreen + WM_CLOSE to quit gracefully. Dev prefs were
  restored to pre-session values.

## Previous START HERE (state as of 2026-08-09 late night)

- **v1.11.0 IS LIVE EVERYWHERE** (same-day full cycle 2026-08-09):
  tagged `v1.11.0` (4e0c23a), merged --no-ff to main (f9f6473),
  uploaded to both platforms (user-confirmed), Discord announcement
  drafted (user posts those themselves — standing rule), family
  server + modpack updated and verified ("everything works").
  Dedicated `runServer` boot gate PASSED before release (clean, no
  dist-cleaner errors). Registrar "23" pairing break fully rolled
  out. Listing text (tightened rewrite) pasted to both platforms.
- **Late additions that made the release**: Twitch chat EMOTES
  (beta.6, subagent-driven, spike-gated) and the HELD-tablet chat
  face — resume-driven, first person + own F5 (beta.7/8; the
  "chat not showing on the held tablet" report was a design gap,
  not a regression — item renderer was signals-only since 1.2).
- **Remaining post-release**: the screenshot shoot (three new
  1.11.0 slots — monitor, chat wall/social-preview candidate,
  mural — see DESCRIPTION.md's shooting checklist); reactive
  hotfix posture for tester reports. No coding item queued.
- **Idea seed from the emotes cycle**: extending the resume-driven
  hand face beyond Twitch (any program in your hand) — noted, not
  committed.

## Previous START HERE (state as of 2026-08-09 end of day)

- **1.11.0-beta.8 is the DISTRIBUTION build** (same content as
  beta.7; renumbered at distribution time, user call 2026-08-09 —
  beta.6/7 jars were never sent and are deleted locally). The user
  is testing beta.8 and preparing the RELEASE; the full consolidated
  1.11.0 checklist went out with the jar.
- **1.11.0-beta.7 superseded beta.6 same-session** (beta.6 was never
  distributed; new number per the beta.3 overwritten-jar lesson):
  the user's first emotes dev pass ("booted fine", emotes rendering
  in GUI + wall confirmed by screenshot) surfaced that the HELD
  first-person tablet always draws the signals face — a design gap
  since 1.2, not a regression (`screen_program` never on the item).
  Fix (user decision, option A "resume"): `TabletItemRenderer` now
  renders `renderTwitchFace` on your OWN first-person tablet iff
  `ClientPrefs.lastProgram == "twitch"` AND the personal channel is
  set — your OWN hands only: FIRST_PERSON_*_HAND contexts, plus F5
  third person via stack REFERENCE identity against the local
  player's hands (frames/ground/other players keep pips; the resume
  pref is viewer-local). touchFace rides for free (kiosk lifecycle). Twitch-only for now — extending
  the resume face to other programs is an open idea, not committed.
- **1.11.0-beta.6 built earlier this session** (Twitch chat emotes —
  native + 7TV/BTTV/FFZ, animated, on all three surfaces, toggle
  glyph in the Twitch header; 100% client-only, registrar untouched
  at "23" — beta.6 pairs with beta.4/beta.5). NOT yet sent to
  testers, and the feature has NOT been seen in a running client —
  the F2 pass below is owed before any distribution.
- **1.11.0-beta.5 is with the testers** (jar + checklist sent
  2026-08-09; pairs with beta.4/beta.6, registrar "23"). The whole
  1.11.0 train: Frequency Monitor + multi-probe container editor,
  Twitch Chat, Arcade consolidation, Paint on walls (Paint its own
  app), App Store search, focus fixes, now plus emotes. All pushed
  through `1161234`, plus this session's emotes commit.
- **Beta.5 additions user-verified in dev**: Paint store row, compact
  Twitch chat walls, bezel-clean text on all four edges (three
  screenshot-driven fix rounds, all confirmed "perfect").
- **Beta.6 F2 pass owed** (never run in a client yet): live channel
  with real emotes on GUI, overlay, and a wall — wall emote
  legibility on small/1×1 faces, aliasing, and animated playback in
  sync across surfaces; overlay overflow now hard-cuts with no
  trailing ellipsis (confirm that reads clean, not broken); toggle
  glyph on/off without a relog; the full test matrix below.
- **Remaining before RELEASE** (all user-gated): the beta.6 F2 pass
  above; tester verdicts across beta.2–6 matrices (below); the
  dedicated-server `runServer` pass (Monitor classification + Twitch
  on a server — never run); listing refresh (DESCRIPTION.md still
  describes 1.10.x — none of 1.11.0 is in the listing text) + the
  long-queued screenshot shoot; then version → 1.11.0, changelog
  date, tag, push, uploads.
- **Open user calls parked in-line**: Home-from-hub kiosk renav
  (testers may answer); gauge editor container-menu conversion (EMI
  drag); App Store categories (roadmap, less urgent since the
  Arcade); survival tablet cloning (Parked section); Display Link
  target app (Parked section, shelved mid-brainstorm).

## Status (2026-08-09 — 1.11.0-beta.6: Twitch chat emotes, registrar unchanged "23", release held)

- **Twitch chat emotes** (idea from the perf/toggle discussion) —
  inline images in Twitch chat on all three surfaces, the wall as the
  point. Native Twitch emotes decode free from the IRC `emotes=` tag
  (code-point-aware span math so astral-plane characters don't shift
  later spans); third-party sets (7TV, BTTV, FFZ) fetch anonymously
  per-channel once `room-id` is known from the first message, with
  each provider failing soft to plain text on its own (never a crash,
  at most one INFO per provider per channel). New client-only classes:
  `TwitchEmotes` (set/matching state, epoch-guarded so a stale fetch
  can't clobber a newer channel), `EmoteTextures` (async GIF/PNG
  fetch + composite into one vertical sprite-sheet `DynamicTexture`
  per emote, capped 1x resolution / 40 frames / ~256 KB download / LRU
  128 with evicted textures closed), and `EmoteText` (the ONE
  tokenizer — segments, word-wrap, and GUI draw all go through it, per
  message memoized and invalidated per-channel when a third-party set
  arrives). A dev-only spike, `tools/EmoteProbe`
  (`./gradlew emoteTool`), gated the whole feature first and confirmed
  7TV/BTTV serve GIF/PNG at 1x (the WebP-migration risk didn't bite).
  Rendering: `TwitchScreen` and the overlay pane word-wrap mixed
  text/emote runs (an emote is an unbreakable token); wall faces stay
  single-line bottom-stacked and ellipsized at segment granularity,
  with emote quads drawn via `RenderType.text(sheet)` — inside the
  existing text pass, no new custom RenderType, same bleed-inset rules
  as text. A smiley toggle glyph next to the channel pin in
  `TwitchScreen`'s header flips `ClientPrefs` boolean `twitch.emotes`
  (default on); off gives text-only chat everywhere — the wall path is
  exactly beta.5's, while GUI and overlay are functionally equivalent
  text-only rendering (they still lay out through `EmoteText`, so wrap
  boundaries differ slightly from `font.split` and the overlay hard-cuts
  without a trailing ellipsis) — the potato-GPU valve. No
  wire, component, NBT, or registrar change of any kind — registrar
  stays "23"; beta.6 pairs with beta.4/beta.5.
- **Known notes carried into the next session**: emotes have NOT yet
  been seen in a running client — the F2 pass owed is wall emote
  legibility on small/1×1 faces, aliasing, and animated playback
  (does the shared animation clock actually keep GUI/overlay/wall in
  sync), plus confirming the overlay's new hard-cut overflow (no
  trailing ellipsis) reads clean rather than broken. `mod_version`
  bumped to `1.11.0-beta.6` this pass — tester distribution, final
  release name still plain `1.11.0`.
- **Commits state**: T1–T8 (spike, native spans, third-party sets,
  texture pipeline, tokenizer, GUI/overlay/wall rendering) are
  committed on `tablet-overlay`; this docs + version-bump commit lands
  right after. Not pushed this pass.

**Test matrix (beta.6 additions)** — copied verbatim from
`docs/superpowers/specs/2026-08-09-twitch-emotes-design.md` "Test
matrix (beta.6 additions)":

- Native emote mid-message renders on GUI, overlay, and wall; a
  message with an astral emoji BEFORE an emote keeps the span
  aligned.
- Heavy 7TV channel: emotes pop in as sets/textures load (text →
  image self-heal), animated ones animate in sync across GUI +
  wall simultaneously.
- Cap check: a 60+-frame 7TV monster animates with its frame cap,
  no hitch on first appearance (decode is off-thread).
- Toggle OFF → pure beta.5 text everywhere; ON again → emotes
  return without a relog.
- Kill the network after emotes are cached → chat goes OFFLINE as
  today; cached emotes still animate; new-emote fetches fail soft
  to text.
- Merged wall + rotated wall: emote quads respect the bezel bleed
  insets on all four edges (the beta.5 screenshot rule).
- Unknown/failed emote (dead CDN id) renders as text, one INFO max.
- Channel part → re-join: sets re-fetch, no stale cross-channel
  emote matches (channel A's 7TV names must not fire in channel B).
- Regression: username colors, wrapping indent, autoscroll,
  status lines, socket-lifecycle (close every surface → socket
  drops) all unchanged; registrar untouched — beta.6 client joins a
  beta.4/5 server.

## Previous status (2026-08-09 — 1.11.0-beta.5: Paint promoted to its own app; beta.4 = paint on walls, registrar "23", release held)

- **beta.5 (same day, user decision after playing beta.4)**: Paint
  left the Arcade — `PAINT(25)` dropped its `game` flag, which
  automatically: removes it from the Arcade shelf (18 games remain),
  adds its store row (desc refreshed to mention walls), and stops
  `fromKeys` migrating "paint" roster tiles to ARCADE (tiles folded
  by beta.3/4 need a one-time re-Get from the store — changelogged).
  `ClientHooks.screenFor` gained `case PAINT ->` riding the same
  key-based SecretGames dispatch (pip easter egg untouched; wall
  exits stay silent so murals park). KEY "paint" frozen as ever. No
  wire change — registrar stays "23"; beta.5 PAIRS with beta.4.
  Beta.5 test adds: store shows Paint row + Arcade (no Paint inside
  the hub); paint tile on Home opens Paint, ESC → Home; pip still
  works; a beta.4 roster's Arcade tile still opens the 18-game hub.
- **Also beta.5 (same session, user screenshot report)**:
  `renderTwitchFace` chat lines were riding the signals list's
  chunky row bands (`LIST_ROWS * surfaceH`) — a merged wall spread
  five messages across the glass with huge gaps. Now compact
  bottom-stacked lines (`LIST_TEXT_H * 1.35` leading, count =
  glassH/lineH): same text size everywhere, bigger walls fit MORE
  lines. Verify on solo + merged Twitch walls.

- **Paint on walls** (idea: Tommy) — the Paint app graduates from
  session-only doodles to a persisted per-tablet canvas. `PaintCanvas`
  (root package `com.modpack.linktablet` — the ONE geometry/mapping
  home for GUI, stroke handler, and renderer; never fork it) owns the
  20×14/280-cell layout and index math. **Slice rule**: there is no
  merged-canvas object — every tablet always owns its own 20×14 slice
  (`paint_canvas` component + BE NBT byte array, 0 = blank, never
  written all-blank); a merged wall's picture is derived by stitching
  member slices at edit/render time, so merge, split, pickup, and
  break all Just Work with zero crop/discard/orphan risk. T1 landed
  the canvas + persistence + item↔block round-trip; T2 the wire
  (`PaintStrokePayload`/`PaintClearPayload`, registrar **"22"→"23"**,
  continuous-space stroke indices for block targets, mapped to member
  BEs via `TabletScreenMath.screenRight/screenDown`); T3 the
  `PaintScreen` rework (adaptive cell size down to 2px on big walls,
  stroke batching cap 64/packet, block screens re-read synced state
  with a pending-stroke overlay); T4 `renderPaintFace` (fills-only,
  faint "Paint" label when blank) plus a kiosk-nav regression fix
  (hub game launches from block-bound GUIs now resend
  `SetProgramPayload`, restoring the ability to SET a wall to Paint —
  the Arcade consolidation had silently dropped that path).
- **The rotation-review saga, one-liner**: quarter-turned paintings on
  a merged wall rescale PER-AXIS by design (not remapped index-for-
  index) — that's the exact-fill requirement on non-square glass, and
  it took a 3-round review to settle. `renderPaintFace` doing no index
  remap at any rotation is correct; don't "fix" it again.
- **Known notes carried into the next session**: paint walls have NOT
  yet been seen in a running client (F2 pass owed — hold a stroke,
  merge/split/rotate a wall, confirm the mural). `mod_version` bumped
  to `1.11.0-beta.4` this pass — tester distribution, final release
  name still plain `1.11.0`.
- **Commits state**: T1-T4 are committed on `tablet-overlay`; this
  docs + version-bump commit lands right after. Not pushed this pass.

**Test matrix (beta.4 additions)** — copied verbatim from
`docs/superpowers/specs/2026-08-09-paint-walls-design.md` "Test
matrix (beta.4 additions)":

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

## Previous status (2026-08-08 night — 1.11.0-beta.3 = beta.2 + Arcade consolidation, registrar still "22", release held)

- **Arcade** (`Program.ARCADE`, id 28, key `"arcade"`) — the 19
  individual game apps collapse into one Arcade app: T1 `Program.
  ARCADE(28, "arcade")` + lang, `Programs.catalog()` hides `game=true`
  entries (App Store and the launcher's add flow shrink 27→9 rows
  incl. Arcade), `Programs.fromKeys` migrates any game key in a
  roster to a single deduped Arcade tile (a roster of Snake + 2048 +
  Signals loads as Arcade + Signals; a game-only roster becomes
  `[ARCADE]`, not the default). T2 `ArcadeHubScreen` — the
  StoreScreen shelf pattern, rows per game with best score
  (`"Best: %s"`, hidden at 0), whole-row tap launches via the new
  3-arg `SecretGames.createApp` with ESC returning to the hub. Kiosk
  faces left on a game key keep their existing dispatch (tap opens
  that game, Home exits) untouched, and the secret-pip trigger still
  launches into the signals-grid return path unchanged. **Pure
  client/catalog work — no wire, registrar, or component changes**;
  registrar stays "22" from beta.2. `mod_version` bumped to
  `1.11.0-beta.3` this pass.
- **Deliberate reversal, noted on purpose**: the 1.10.0 dev cycle
  built this exact hub (`Program.ARCADE(6)`, `ArcadeHubScreen`) and
  the user then chose per-game apps before committing — the hub was
  deleted UNCOMMITTED and id 6 retired. This spec re-reverses that
  with the user's explicit confirmation (2026-08-08): the store had
  since grown to 27 rows and the user independently asked for store
  categories, so consolidation now serves the same pain. Id 6 stays
  retired; the new program takes id 28.
- **Commits state**: T1/T2 (`Program.ARCADE` + catalog/migration, then
  `ArcadeHubScreen`) are committed on `tablet-overlay`; this docs +
  version-bump commit lands right after. Not pushed this pass.
- **Known notes carried into the next session**: the hub screen has
  NOT yet been seen in a running client — an F2 pass (scroll, launch
  a few games, ESC-return, best-score display/update) is owed before
  calling Arcade done, per the test matrix below.

**Test matrix (beta.3 additions)** — copied verbatim from
`docs/superpowers/specs/2026-08-08-arcade-consolidation-design.md`:

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

## Previous status (2026-08-08 night — 1.11.0-beta.2, Twitch Chat IN CODE + committed, registrar "22", release held)

- **Twitch Chat** (`Program.TWITCH`, id 27, key `"twitch"`) — all 7
  plan tasks done and committed on `tablet-overlay`: Program + lang
  (T1); `client/TwitchChatService` — one anonymous TLS link to
  Twitch's chat relay (`justinfan` guest nick, tags for username
  colors), ref-counted per-channel joins, 2s→60s reconnect backoff,
  prompt socket close on shutdown (T2); `twitch_channel` component +
  BE NBT (never written empty) + `SetTwitchChannelPayload` — registrar
  **"21"→"22"** (T3); `TwitchScreen` — channel box with click-to-focus
  + Enter/focus-loss commit, colored wrapped chat, autoscroll-unless-
  scrolled-up (T4); `TwitchOverlayContent` HUD pane (T5); kiosk
  `renderTwitchFace` chat wall, three-pass, touchFace-first presence
  (T6); this docs pass (T7). Socket runs ONLY while a surface displays
  chat — screens/overlay acquire-release, faces heartbeat via
  `touchFace` with a 100-tick expiry; logout clears every ref (and
  `NoteWindows` now defocuses windows before clearing, a general fix
  that fell out of that work). `mod_version` bumped to
  `1.11.0-beta.2` this pass — tester distribution, final release name
  still plain `1.11.0`.
- **Known notes carried into the next session**: (a) the face and
  screen visuals have NOT yet been seen in a running client — an F2
  pass is owed before calling Twitch Chat done; (b) the CONNECTING/
  LIVE/OFFLINE status lang-key switch is triplicated across screen/
  overlay/face — a cleanup candidate, not urgent; (c) chat is
  unfiltered live internet content and channel choice on a placed
  tablet is open to anyone who can open its GUI — same trust model as
  signal editing, noted for family-server awareness, not a bug.
- **Commits state**: all 7 Twitch tasks plus this docs commit are on
  `tablet-overlay`, not pushed (per this session's instructions — no
  push this pass). Full `./gradlew build` gate covered below.

**Test matrix (beta.2 additions)** — copied verbatim from
`docs/superpowers/specs/2026-08-08-twitch-chat-design.md`:

- Set a live channel on a held tablet → chat flows; pin the overlay →
  chat on HUD while playing.
- Wall tablet: set channel from its GUI → face shows chat; second
  account sees the same wall (its own connection).
- Connection hygiene: close every Twitch surface → socket closes
  (netstat or log line); reopen → rejoins.
- Kill the network mid-chat → OFFLINE status, auto-reconnect when it
  returns.
- Misspelled/empty channel → "no messages yet" / hint, no errors.
- Break + re-place a channel-set tablet → channel survives.
- Regression: registrar "22" pairing break, old-world load, the
  1.11.0 matrix still green.

## Previous status (2026-08-08 evening — 1.11.0 feedback round 2 IN CODE + user-verified, registrar "21", release held)

- **Round 2 (same day, from the redo sessions)**: (a) standalone
  EditBox click-to-focus fix — the store search box ate clicks
  without focusing (vanilla EditBox.mouseClicked never sets focus;
  that's the widget traversal's job, and unregistered boxes must do
  it on their own click path); audit found the GAUGE EDITOR'S NAME
  FIELD had the same defect since 1.10.0 — it was NEVER typeable.
  Both fixed (click-to-focus both, gauge modal focuses on open).
  (b) **Add Probe container menu** — user report "JEI doesn't show on
  the Monitor": BOTH viewers only attach to AbstractContainerScreen
  (EMI 1.1.24 ScreenMixin gates on instanceof, bytecode-verified;
  EMI also suppresses JEI's overlay when both installed), so the
  plain-screen bounds APIs can never surface a panel — the earlier
  addGuiScreenHandler/addScreenBoundsProvider attempt was a dead
  end. Fix = the house pattern: `ProbeEditScreen` on `SignalEditMenu`
  reused under the PROBE_EDIT menu type (ghost slots, all-items
  search, inventory, Add), opened server-side via
  OpenProbeMenuPayload (registrar **"20"→"21"**); MonitorScreen's +
  opens it, inline staged slots removed. **USER-VERIFIED "works
  well" 2026-08-08** (~3-min pass, log clean). Gauge editor stays a
  plain screen: viewer drag there works JEI-only; converting it to a
  container menu is the queued fix IF testers want EMI drag on it.

- **Feedback round 1 (2026-08-08, from the user's first dev-client
  session — 47 min, log clean)**: three user-requested changes, all
  IN CODE + committed, build green, **awaiting the redone test
  session**:
  (a) **multi-probe** (user decision via AskUserQuestion: list of up
  to 8, not single) — `monitor_probe` is now `List<Frequency>`
  (component + BE NBT `monitor_probe` kept; the brief single-format
  dev NBT decodes as a one-entry list), `SetProbePayload` carries the
  whole list (registrar **"19"→"20"**, still inside the 1.11.0
  pairing break), and `MonitorScreen` adopted the signal editor's
  stage-then-Add picker flow (two staged slots + procedural "+" chip,
  probe rows carry a remove cross; staged slots no longer auto-send).
  (b) **JEI/EMI ghost drag everywhere applicable** — gauge editor
  modal slots + Monitor probe staging slots joined the signal editor
  as drop targets (`GaugesScreen.frequencySlotArea/stageFrequencyItem`,
  `MonitorScreen.probeSlotArea/stageProbeItem`; zero-area rect =
  closed modal). New item-slot surfaces should join this pattern.
  (c) **App Store search** — ChromeEditBox row under the header
  filters by name/description/key; panel height stays full-catalog
  so it doesn't jump while typing; inventory key guarded while the
  box is focused (the nameBox rule).
  Redone-test additions to the matrix: probe add/remove flow (stage,
  +, row cross, cap 8, dupe rejected with the deny tick), JEI drag
  onto all three screens' slots (EMI too if present), store search
  incl. "no matches" and ESC/inventory-key behavior.

## Previous status (2026-08-05 — Frequency Monitor 1.11.0 IN CODE, registrar "19"→now "20", release held)

- **Frequency Monitor** (`Program.MONITOR`, id 26, key `"monitor"`) —
  all 9 plan tasks done: Program + lang (T1); `MonitorChannels.
  channelsOf` the one channel-derivation table, probe→signal
  freqs→gauge freqs deduped by Create identity (T2); wire types
  `MonitorSubscribePayload`/`SetProbePayload`/`MonitorSnapshotPayload`
  + client `ClientMonitorSnapshot` store, 40-tick heartbeat, registrar
  **"18"→"19" — PAIRING BREAK** (T3); `monitor_probe` component + BE
  NBT, never written empty (T4); `compat/MonitorScanner` — 4-tick
  polls of Create's `networksIn`, member classification (link block /
  placed tablet / inventory tablet via `ownerName` on both handlers /
  other), range via Create's `withinRange` on RAW positions with
  DISPLAYED coords Sable-localized, 100-tick viewer expiry, 64-member
  wire cap (T5); `MonitorScreen` — probe ghost slots, channel+member
  rows, pin, resize-guarded subscription (T6); kiosk face —
  `monitor_counts`/`monitor_power` update-tag sync, three-pass
  `renderMonitorFace`, re-registers on surface-split promotion (T7);
  `MonitorOverlayContent` compact rows (T8); this docs pass (T9).
  **All 9 tasks are COMMITTED on `tablet-overlay`** (11 commits ahead
  of `origin/tablet-overlay`, not pushed — see `git log`); the T9
  docs commit (this CHANGELOG/NEXT_SESSION/CLAUDE.md update) lands
  right after. `mod_version` stays `1.10.2` in `gradle.properties` —
  **release is user-gated**, no bump yet, so the built jar is still
  named `linktablet-1.10.2.jar` even though it carries 1.11.0-dev
  code.
- **Known limitations** (from task reviews, carried forward — not
  bugs, just v1 scope):
  (a) the client snapshot store is single-target: opening
  `MonitorScreen` on tablet A while an overlay pin watches tablet B
  rebinds the store to A (last-wins), so the overlay shows A's data
  under B's title. Candidate future fix: visually flag a retargeted
  overlay. Reviewer's cheaper alternative: the overlay could render
  skeleton rows whenever the snapshot store's bound target differs
  from its own view target ("no live data" instead of "wrong data").
  (b) the 64-member wire cap keeps the first-encountered members, not
  the strongest, on a degenerate 65+-member channel.
  (c) up to a 4-tick empty flash when a kiosk switches onto the
  Monitor program (summary hasn't synced yet).
  (d) the F2 visual pass on `renderMonitorFace` and the 8px overlay
  icons has NOT been run in a client yet — first thing to check in
  the in-world pass below.
  (e) tablets using more than 64 distinct frequencies (9+ signals × 8
  freqs; merged surfaces raise the signal cap) hit the same wire cap
  on the CHANNEL list itself — the snapshot truncates to the first 64
  channels (prefix of `MonitorChannels.channelsOf`'s order), not the
  channels currently most active.
- **`./gradlew build` green** (T9 gate) — jar at
  `build/libs/linktablet-1.10.2.jar`.
- **Remaining before release**: the user's in-world test pass (matrix
  below), then version bump + CHANGELOG date + tag/push, then the
  user uploads to both platforms — see `CLAUDE.md`'s Release process.

**Test matrix (dev pass)** — copied verbatim from
`docs/superpowers/specs/2026-08-05-frequency-monitor-design.md`:

- One channel with: a gauge, a real Create Redstone Link, and a
  creative phantom tablet copy in the inventory → three member rows,
  correctly classified; phantom row disappears when the item is
  dropped.
- Probe an unrelated channel the tablet doesn't use.
- Range gating: walk a transmitter out of range → out-of-range badge,
  effective power drops.
- Dedicated server pass (`runServer`) — payloads + classification.
- Kiosk face summary on flat, merged, and mounted tablets; face tap
  opens the GUI; pre-1.11 world's kiosks unaffected.
- Overlay pin shows live summaries; pin survives relog (`@monitor`
  descriptor).
- Old-world load: tablets without `monitor_probe` untouched;
  1.10.x ↔ 1.11.0 client/server mismatch cleanly refuses (registrar
  break, expected).
- Channel-count stress: 9+ signals × 8 distinct freqs (>64 channels)
  — Monitor opens, first 64 shown, nobody kicked.
- Probe edit while a kiosk face shows Monitor (≤4-tick row shift
  settles).
- Two players monitoring the same channel simultaneously (independent
  snapshots/anchors).
- Window resize with MonitorScreen open (subscription guard repro).
- Sable: Monitor opened on a vehicle-mounted tablet — check the
  displayed member coords read sensibly; decide if follow-up needed.
- Surface merge→split while showing Monitor (face summary resumes on
  the promoted controller).
- Overlay pinned on tablet B, open+close MonitorScreen on tablet A
  (documented limitation repro; B's heartbeat survives A closing).

## Previous status (2026-08-03 — v1.10.2 tagged; upload 1.10.2 ONLY, skip 1.10.1)

- **v1.10.2 (same-day hotfix stack on 1.10.1)**: (a) 1.10.1's Sable
  shim NEVER BOUND on dedicated servers — `getMethod` enumeration hit
  the client-only `getContainer(ClientLevel)` overload and NeoForge's
  dist cleaner threw; repro'd + fixed with exact-signature
  `findStatic`/`findVirtual` lookups, verified on dev `runServer` AND
  the user's BisectHosting server symptom. (b) Placed-gauge frozen
  dial: Create's `addToNetwork` never delivers the initial value to
  non-LinkBehaviour members → `VirtualReceiver.readInitial()`.
  Also diagnosed (NOT bugs): three "always 15" reports = a creative-
  mode tablet ITEM copy in the inventory transmitting alongside the
  placed block (stacked-transmitter design); CA/Sable handles Create's
  link network across the plot boundary natively (vehicle gauges read
  world transmitters — verified live). Both fixes user-verified in
  dev ("both worked"). 1.10.1 was never uploaded — upload 1.10.2 only.
  Roadmap seed from today: a "who's transmitting on this frequency"
  debug view (phantom-copy confusion bit 3× in one day).

## Previous status (2026-08-03 — v1.10.1 tagged; uploads pending)

- **v1.10.1 (Sable/Create-Aeronautics compat hotfix)** — first tester
  bug report against 1.10.0: swivel-mounted tablets on a physicalized
  contraption (Sable sub-level) flung to yaw=75 (the MOUNT_MAX_TILT
  clamp) and couldn't be re-aimed. Root cause proven numerically in
  the dev client (CA + Sable 2.0.3 jars now live in `run/mods` for
  repro): plot-frame block vs world-frame player, 28.9M-block aim
  vector. Fix = `compat/SableCompat` reflection shim + 32-block
  computeAim guard + localized payload range checks/sounds/drops (see
  CLAUDE.md gotcha). VERIFIED in dev: wrench re-aim tracks (~20
  samples), bezel landscape flip both ways, soft-dep boot without
  Sable clean; pip taps confirmed by the user on the physicalized
  tablet in a second dev pass ("ok it works", 2026-08-03). Release:
  committed (64539af), tagged v1.10.1, pushed same day. REMAINING:
  user uploads to CurseForge + Modrinth and updates the family
  server/pack (server-side halves matter: aim math + payload range
  checks — update BOTH sides).

## Previous status (2026-07-29 — v1.10.0 LIVE EVERYWHERE)

- **v1.10.0 LIVE 2026-07-29** (tagged + pushed 2026-07-28, commit
  b5d1941, tag v1.10.0, merged to main; user modpack-tested "tested
  and works"; **uploaded to both platforms + announced —
  user-confirmed 2026-07-29**): the tablet OS + App Store +
  Clock/Calculator/Gauges + public game apps, the ADDON API
  (+ docs/API.md tutorial), SIGNAL LINKS (incl. frequency-less scene
  masters), FOLLOW MODE, sparse placed-tile polish. Registrar "18" —
  PAIRING BREAK vs 1.9.x. The 1.10.0 DESCRIPTION.md text is pasted
  on both listings (2026-07-29), and the LISTING RESTYLE (logo
  banner + badges + emoji headers; no GitHub links, no API mention)
  is pasted and live on both same day. REMAINING: screenshot shoot queued
  (4 slots). Untested corners (dedicated-server gauges, multiplayer
  follow) ride the tester crew — reactive hotfix posture. No coding
  item queued — next dev work is tester reports or a new feature.

## Previous status (2026-07-26 — v1.9.1)

- **v1.9.1 LIVE 2026-07-26** (tagged, pushed, uploaded to both
  platforms, Discord announcement posted — all same day): JEI/EMI
  drag onto the edit screen's ICON slot — third ghost-drag target
  (`iconSlotArea`/`stageIconItem` on AppEditScreen, wired in both
  compat classes). Client-only, pairs with 1.9.0 (no coordinated
  server update needed).

- **v1.9.0 LIVE EVERYWHERE 2026-07-25** (tagged, pushed, uploaded to
  both platforms, Discord announcement posted — all same day):
  **deployer-manufactured tablet** — brass
  Tablet Case ("P P"/"PPP"), then `create:sequenced_assembly`:
  logic board → clockwork cell → quartz display LAST. Old crafting
  recipe DELETED (deployer-only, user decision). Transitional
  Incomplete Link Tablet swaps art per stage (`assembly_progress`
  item property in ClientSetup reads Create's progress component;
  override threshold 0.5 in `models/item/incomplete_tablet.json`).
  5 new items → does NOT pair with 1.8.x (registrar still "13").
  Textures: string-map pixel art, generator was session-scratch
  (regen = rewrite the tool or edit PNGs directly; brass/quartz
  shades were sampled from Create's own sprites).
- DESCRIPTION.md rewritten for 1.9.0 (manufacturing story, recipe.png
  embed RETIRED, 📸 assembly-line slot added). Remaining for the
  listings: paste the refreshed text, and the screenshot shoot
  (assembly-line shot joins the merged-wall + overlay shots).
- v1.8.1 (2026-07-22, live on both platforms): placeable Swivel
  Mount stand + separate tablet/stand pickup. ADDS A BLOCK — does
  not pair with 1.8.0 or older.
- 1.8.0 (2026-07-21, also live): Swivel Mount item + nameable tablets
  (anvil name → GUI/overlay title). UNADVERTISED and changelog-silent
  BY DESIGN: the 19-title secret arcade (local memory
  `secret-games-backlog` has the cheat sheet; trigger = Linked
  Controller icon + game name — NEVER spoil in public text).
- 1.7.0 highlights: multiblock screens (4×3), pinned overlay +
  keybinds, overlay whitelist, JEI/EMI ghost drag (EMI dev-runtime jar
  comes from the Modrinth maven — terraformers truncates big jars on
  this connection), solo screens.
- The FAMILYPACK now runs a dedicated server: server-side mods are
  linktablet + bettercombat (+ Create deps). Punchy is CLIENT-ONLY
  (crashes a dedicated server loading a client Screen class) — EMF,
  ETF, Hold My Items, BetterThirdPerson client-only too.
- Branches: `tablet-overlay` and `main` both pushed and level; tag
  v1.9.0 on the release commit.

## In progress

- **Launcher / tablet OS (1.10.0, session 1 of ~2 done 2026-07-27)** —
  scope pivot from side-chat handoff, jumped ahead of follow mode by
  user decision. Plan approved same day (decisions: launcher before
  follow mode; held GUI RESUMES last program; existing kiosks boot to the
  LAUNCHER — deliberate tester disruption; kiosk Home = bezel tap;
  secret games stay off the launcher). SHIPPED IN CODE (session 1
  verified in dev by the user 2026-07-27 — launcher, resume, Home
  button, add flow all work): `Program` enum (root pkg, byte ids
  append-only, 0=launcher never persisted; constant SIGNALS(1,
  "signals")), `LauncherScreen` (tile label "Signals"),
  `SignalTilePainter` (tile base extracted from renderSignalTile),
  ClientHooks nav helpers (openProgram/returnToProgram/openHome/
  returnHome/openResumed) + all held-GUI call sites rerouted,
  `ClientPrefs.lastProgram` resume pref, Home house-button on
  TabletScreen (held/slot views only), lang keys, changelog.
  PLUS the same-day **apps→signals rename** (user decision, all three
  layers): code identifiers (SignalApp→Signal, AppView→SignalView,
  AppEdit*→SignalEdit*, payloads *App*→*Signal*), wire ids
  (toggle_app→toggle_signal etc., menu app_edit→signal_edit,
  registrar "13"→"14" — 1.10.0 is now a PAIRING BREAK, user-approved),
  UI text + DESCRIPTION.md. DISK KEYS FROZEN: component `tablet_apps`,
  BE NBT `"apps"` (see comments at the write sites). Launcher stays
  programs-only (no signal pinning — user decision). The open
  registrar-break window means signal-links (item 3) could batch into
  1.10.0 — flagged to the user, no decision yet.
  **Session 2 (kiosk side) IN CODE same day** (build + boot clean,
  awaiting the user's in-world pass): BE `screen_program` byte
  (controller-only, 0=launcher never persisted → pre-1.10 kiosks boot
  Home; synced free via update tag; never on the item), tap routing
  in `useWithoutItem` right after `resolveController()` — launcher
  face hit-tests via the EXISTING pip pipeline with
  `Program.roster().size()` (no separate launcher table; renderer
  draws synthetic roster tiles through `TabletScreenRenderer.
  launcherTiles()`, list forced off, held pips suppressed), off-tile
  launcher taps MUST return to the GUI (falling through would
  hit-test the invisible signals — guarded, see comment), bezel-tap
  Home via new `TabletScreenMath.hitBezel` (member UV → continuous
  coords, outer band only on merged) + `mountedOnBezel` (panel minus
  wrench-inset glass, same band as the wrench flip). Program switches
  play the toggle click both ways. Wrench paths untouched.
  **Settings parity follow-up (same day, from the user's first kiosk
  test)**: launcher owns the DEVICE features — `HeaderGlyphs` painter
  extracted (glyphs + theme popup, pixel-shared with TabletScreen),
  LauncherScreen gained theme/pin/link/grid-list header row (same
  payloads and sounds; no reorder — that's signals-specific), kiosk
  launcher-face off-tile taps open the launcher GUI via
  `ClientHooks.openBlockHome` (NEVER the signals grid), and the
  signals GUI's Home button now shows on block views too.

## ▶ App addon API — IN CODE 2026-07-27 (same day as the OS suite)

Built and build+boot clean; awaiting the user's in-world pass (folds
into S6 below). Decisions locked with the user via AskUserQuestion:
**keys-native persistence now** (1.10.0 unreleased = the int formats
never shipped), **buffered face context** (three-pass rule enforced
structurally), **in-repo dev example** (jar-excluded), **no store
byline**. What shipped:

- **`api/` package** (PUBLIC COMMITMENT — append-only, see the new
  CLAUDE.md gotcha): `TabletProgram` (key "modid:name", displayName,
  storeDescription, chipColor, iconItem) + `RegisterTabletProgramsEvent`
  (posted to every mod bus from commonSetup enqueueWork, then
  `Programs.freeze()`); client: `TabletProgramClient` (screen factory,
  optional overlay, optional face painter), `ProgramHost` (theme/
  tabletName/goHome/isPinned/togglePin), `RegisterTabletProgramClientsEvent`
  (posted from ClientSetup), `TabletFacePainter`+`TabletFaceContext`
  (glass-texel fills/items/text, BUFFERED — `renderAddonFace` flushes
  in pass order), and `OverlayContent` MOVED here (program() dropped;
  the five impls updated). `LinkTabletApi.API_VERSION = 1`.
- **`Programs`** = the one unified table (enum seeds it, addons sort
  by key after; byKey falls back LAUNCHER, catalog(), fromKeys()
  sanitizer, keys()). `Program` enum implements `TabletProgram`;
  built-in dispatch stays `instanceof Program` switches (ClientHooks
  screenFor, MiniTabletWindow contentFor, BER renderFace — the flat/
  mounted switches got extracted into ONE helper), addons go through
  `ProgramClients`.
- **Keys-native persistence** (registrar "16"→"17"): HOME_APPS
  component is now List<String> under the same id (Either-codec
  decodes the old int list forever, always writes keys); BE
  `home_apps`/`screen_program` tags write string forms, load
  type-sniffs the pre-API int-array/byte forms; SetHomeAppsPayload
  carries keys (wire cap 64), SetProgramPayload a key. Prefs + pin
  `@<key>` descriptors were already strings — addon keys (with ':')
  ride through untouched.
- **Example addon** `example/ExampleAddon(+Client)` — "Dice"
  (linktablet_example:dice): store row, screen (roll + pin toggle +
  ESC→Home), overlay pane, kiosk die face — imports ONLY api/ +
  vanilla; jar-excluded in build.gradle beside tools/.
  **USER-VERIFIED in dev 2026-07-27 ("dice works"), then made
  OPT-IN by user request** ("kinda lame next to the games"): it now
  registers only with `-Dlinktablet.example=true` (dev-only guard
  kept too). Flip the flag on whenever the API changes — it's the
  API's regression test.
- **S6 additions for the API** (append to the matrix; the Dice pass
  itself is DONE — re-run with the flag only if the API changes):
  dev-world legacy decode (existing rosters/kiosk programs survive
  the int→key switch — type-sniff) and a store Get/Remove
  regression.

## Signal links + follow mode — IN CODE 2026-07-28 (batched into
## 1.10.0 by user decision; registrar "17"→"18")

- **Signal links**: `Signal` grew `linkId` (server-minted in
  handleUpsert's ensure-ids pass ONLY; clients send 0) + `links`
  (`Signal.Link` target-id/mode ON|OFF|FOLLOW, ordinal-coded,
  MAX_LINKS 8) — both optionalFieldOf + appended at the END of the
  hand-rolled stream codec (old NBT/untouched signals unchanged).
  `SignalLinks.propagate` = the ONE BFS traversal (visited-id set:
  loops safe, chains work; toggle targets chain, timer targets fire
  their pulse and are terminal, momentary/slider skipped) — called
  from BOTH mutation sites (handleToggle + TabletBlock tap branch).
  UI: Links ChromeButton right of the icon slot → `LinkPickerOverlay`
  (ZonePicker pattern; rows cycle none→ON→OFF→FOLLOW; id-0 rows inert
  with "save it once first" — the ensure-ids pass self-heals);
  chain glyph on GUI tiles/rows (world tiles skip it).
- **Follow mode**: powered Swivel Mount tracks the nearest player
  (≤8 blocks, every 3rd tick, 2° threshold — the packet-spam guard).
  `followPowered` transient (neighborChanged + onLoad); ticker gated
  on MOUNTED in getTicker (the mod's FIRST BE ticker; unmounted never
  ticks); `computeAim` extracted from aimAt (wrench keeps always-sync).
  Client: BER glides `renderPitch/renderYaw` toward the synced angles
  (exponential, rotLerp) — hit tests keep the raw `mountBasis()`.
- Docs: CHANGELOG entries added; DESCRIPTION.md rewritten for the
  full 1.10.0 story (OS section, links bullet, follow paragraph,
  addon-API dev note; games still unmentioned; two optional new 📸
  slots). CLAUDE.md gotchas added.

## OS suite S6 (in-world regression pass — still owed)

- **EVERYTHING IS UNCOMMITTED on `tablet-overlay`** — two huge days
  (2026-07-27): launcher held+kiosk, apps→signals rename (wire ids
  renamed, disk keys "apps"/"tablet_apps" FROZEN — comments at the
  write sites), settings parity, roster S1, **and the whole OS suite
  S2–S5 (Clock, Calculator, program-aware overlay, Gauges
  server+client)** — registrar now "15". Commit only when the user
  asks; if asked, suggest logical splits (launcher+rename / roster /
  clock / calc+overlay / gauges / docs).
- **S1–S5 are IN CODE, build clean, NOT user-tested in-world.**
  What S2–S5 added (all `./gradlew build` green 2026-07-27):
  - **Clock (S2)**: `client/ClockService` (client-tick manager —
    alarms/timer/stopwatch ring with no GUI open, wall-clock epochs in
    ClientPrefs `clock.*`), `ClockScreen` (tabs Alarm|Clock|Timer|
    Stopwatch + per-app pin button), `ZonePickerOverlay` (searchable
    ~600 ZoneIds), kiosk digital face (`renderClockFace`, text pass
    only; the shared background lives in `beginScreen`, extracted).
    Kiosk glass tap on ANY non-signals program face opens its screen
    client-side (generic branch in TabletBlock right after the bezel
    check — MUST return, or the invisible signals get hit-tested).
  - **Calculator (S3)**: `CalculatorScreen` — BigDecimal immediate
    execution, chrome pad + keyboard entry, session-static state.
    Kiosk face = generic `renderLabelFace` (program name as the door
    label; also covers any future program without a bespoke face).
  - **Program-aware overlay (S3)**: `OverlayContent` interface;
    MiniTabletWindow is now chrome-only and hosts
    `SignalsOverlayContent` (classic rows, interaction state migrated
    verbatim) / `ClockOverlayContent` / `GaugesOverlayContent`. Pin
    descriptor grew `@<programKey>` (`slot:3@clock`; absent = signals,
    so pre-1.10 pins restore untouched); `OverlayPin.pin/isPinned`
    take a Program (signals shorthands kept for the old call sites).
    Right-click the window opens the pinned program's screen.
  - **Gauges (S4+S5)**: `frequency/Gauge` (SOURCE enum append-only,
    LINK first; hand-rolled stream codec from day one),
    `tablet_gauges` component + BE NBT `gauges` + item↔block
    round-trip, `compat/VirtualReceiver` (isListening=true, Create
    pushes strength in, range-gated by position) +
    `compat/TabletReceiverHandler` (player-inventory scan, receivers
    ride the player, full-snapshot **GaugeReadingsPayload — the mod's
    FIRST playToClient**, 4-tick cadence + change guard, never on item
    components). The BE runs its own receivers (controller-only;
    readings sync via update-tag `gauge_readings` int array, transient
    like held pips — kiosk dials show what a link AT THE BLOCK hears).
    Client: `ClientGaugeReadings` store, `GaugesScreen` (dial grid +
    modal editor: name box, two 18px ghost slots via PickerOverlay,
    the signal editor's 16-swatch palette, save/delete →
    Upsert/RemoveGaugePayload), `GaugeDialPainter` (GUI fills) and
    `renderGaugesFace` (world quads: tick arc + rotated needle quad,
    then text — three-pass rule holds).
- **Feedback round 1 (same day, from the user's first test pass) —
  IN CODE, build clean**: (a) kiosk follows GUI navigation —
  `SetProgramPayload` (registrar "15"→"16"), sent from
  `ClientHooks.showProgram` on Block views; (b) the pin pins the
  screen you're on — launcher pin pins a LAUNCHER app-dock overlay
  (`LauncherOverlayContent`), calculator got a pin + working mini-pad
  overlay (`CalculatorOverlayContent`; model extracted to
  `CalcEngine`, session-static, shared by screen/overlay/kiosk);
  (c) the Home screen honors the grid/list toggle; (d) the kiosk
  calculator face shows the LIVE tape (`renderCalcFace` — the bare
  label face read as "nothing renders"; label face stays as the
  fallback for future programs); (e) **the app drawer is GONE**,
  replaced by the **App Store**: `Program.STORE(5)` (GUI-only door —
  never on the roster, never on the kiosk launcher face), permanent
  last tile on the launcher, `StoreScreen` shelves `Program.apps()`
  with descriptions (`program.linktablet.<key>.desc`) and Get/Remove
  over the same SetHomeAppsPayload. **User loves the ADDON API idea**
  — other mods shelving programs into the store — parked as a roadmap
  item below.
- **Feedback round 2 (same day, second test pass) — IN CODE, build
  clean**: (a) kiosk LAUNCHER faces honor list mode — new
  `plainRows` flag on TabletScreenRenderer.render (rows without the
  switch mechanism; text runs full width) + the TabletBlock launcher
  hit-test now passes `isScreenList()` (both sides synced; ≤5 roster
  apps fit the 5-row standalone list exactly); (b) **the Arcade went
  public**: `Program.ARCADE(6)` (Linked Controller icon, teal chip),
  `ArcadeHubScreen` (scrollable 19-game shelf with best scores; hub
  launches set `ArcadeScreen.setReturnProgram(ARCADE)` so ESC returns
  to the shelf — secret-pip launches still return to the signals
  grid, the easter egg lives), shelved in the App Store. Game titles
  stay literals; CHANGELOG deliberately stays silent about the games
  (the store row is the in-game announcement) — the user can overrule.
- **Round 3 (same day; user: "everything works" after pass 2, then
  asked for this before committing) — IN CODE, build+boot clean**:
  **every game is its OWN app.** The Arcade hub (Program id 6,
  ArcadeHubScreen) was DELETED — id 6 is RETIRED, never reuse
  (dev-world rosters may carry it; fromIds drops it gracefully).
  19 game Programs, ids 7–25, `game=true` flag (key == SecretGames
  dispatch id == best-score pref key — frozen); launched via
  `SecretGames.createApp` (ESC → Home; secret-pip launches still
  return to the signals grid). Each has a public lang name + store
  description now (cabinet titles stay literals). LauncherScreen and
  StoreScreen both gained SCROLLING (24-program catalog);
  SetHomeAppsPayload's sanity cap 16→64. CHANGELOG still says nothing
  about the games (policy; user can overrule).
- **S6 user test matrix** (also re-verify both feedback rounds):
  - Launcher/roster: drawer shows Clock+Calculator+Gauges, add/remove
    tiles, right-click remove, kiosk roster face + tile taps.
  - Clock: alarm rings with GUI closed, timer rings after a game
    restart (wall-clock end stamp), stopwatch keeps running across
    relog, zone search + right-click remove, kiosk wall clock incl.
    merged + mounted, clock overlay pin.
  - Calculator: pad + keyboard, divide-by-zero → Error, reopen keeps
    the sum.
  - Gauges: create a dial (needs ≥1 frequency item), value follows a
    real Redstone Link transmitter, range gating (walk away), kiosk
    dials show the BLOCK's reception not the player's, overlay HUD
    pin, dedicated-server pass (first playToClient!), two accounts.
  - Regressions: signals grid/list/reorder/sliders/momentary/timer,
    merged surfaces, mounted taps, secret games, JEI/EMI drags,
    pre-1.10 world upgrade (kiosks boot to launcher, old pins fine).
  - **Sparse placed-screen tiles (2026-07-28, three user-tuning
    rounds same day)**: 1–3 signals cap at the 4-signal cell size,
    centered as a block; a LONE signal is a bespoke centered SQUARE
    at 1.5× the cell (`loneTileRect` — ✔ USER-APPROVED "thats the
    vibe 100%"). Still worth one regression tap each on: lone
    momentary, lone slider click-and-slide (bar spans the square),
    lone tile on a merged wall + mounted, and off-tile tap → GUI.
  - **Signal links (2026-07-28)**: link A→B in all three modes, tap A
    from GUI + overlay + placed screen, chain A→B→C, loop A→B→A (must
    not hang), timer target fires its pulse, chain glyph on tiles and
    rows, "save it once first" row heals after saving that signal,
    remove a target then tap the source (stale link no-ops),
    **frequency-less links-only signal** saves and drives targets
    (user request from first look, 2026-07-28 — Save enables on
    links alone, server gate is freqs-OR-links). ✔ USER-VERIFIED
    2026-07-28 ("ok that worked") after the save()-guard fix
    (65fa5b6); the rest of the links row still pending.
  - **Follow mode (2026-07-28)**: ✔ USER-VERIFIED 2026-07-28
    ("follow mode works, it's smooth") — single-player pass; the
    multiplayer nearest-player feel-test below still stands. Full
    row: powered mounted tablet tracks you
    smoothly (no jitter standing still — the 2° threshold), freezes
    on power-off, resumes on power-on, wrench re-aim/flip still work
    while powered, taps land after the glide settles, works on floor/
    wall/ceiling mounts, chunk reload keeps following (onLoad
    re-derives power). MULTIPLAYER FEEL-TEST (per design): two
    players near one powered mount — it picks the nearest.
- CHANGELOG "Unreleased" already covers the whole suite. Listing text
  (DESCRIPTION.md) still describes 1.9.0 — refresh it before release.

## OS suite (1.10.0 — HELD until the whole suite is in; approved plan
## 2026-07-27, decisions locked)

User decisions: settable per-tablet roster (Android home + app drawer,
default = Signals only); Clock app WITH alarms + world clock (all real
timezones, searchable) + timer + stopwatch; Calculator; **Gauges as a
SEPARATE app** (own data model — future sources: velocity etc., custom
vehicle HUDs); pin option 3 = program-aware overlay (per-app pin;
pinned gauges = the vehicle HUD). Launcher tile dress lives ON the
Program enum (chipColor/iconItem). Reserved ids: 2=clock,
3=calculator, 4=gauges.

- **S1 settable roster — IN CODE 2026-07-27** (build+boot clean,
  awaiting user pass): `home_apps` component (INT list, absent =
  DEFAULT_HOME=[SIGNALS], never written at default) + BE NBT int array
  + item↔block round-trip; `SetHomeAppsPayload` (fromIds sanitizes:
  unknown/LAUNCHER dropped, dedupe, empty→default; handler mirrors
  handleSetTheme's both-target shape); `SignalView.homeApps()` per
  view; LauncherScreen: drawer button (3×3 dots glyph, leftmost) +
  drawer modal (rows with on-home checkmark, taps toggle + drawer
  stays open, last app can't be removed) + right-click tile removes
  (actionbar toast); kiosk face + hit-test read the home list
  (launcherTiles(home) synthetic signals).
- **S2 Clock — IN CODE 2026-07-27** (build clean, awaiting user pass):
  ClockService, ClockScreen, ZonePickerOverlay, kiosk digital face,
  rings with GUI closed.
- **S3 Calculator + program-aware overlay — IN CODE 2026-07-27**:
  CalculatorScreen; OverlayContent split (Signals/Clock/Gauges
  contents), `@program` pin descriptor suffix, per-app pin buttons.
- **S4 Gauges server — IN CODE 2026-07-27**: Gauge model,
  `tablet_gauges` component + BE NBT, VirtualReceiver +
  TabletReceiverHandler, GaugeReadingsPayload (first playToClient),
  registrar "15".
- **S5 Gauges client — IN CODE 2026-07-27**: GaugesScreen + modal
  editor, GaugeDialPainter, kiosk dial face, overlay HUD content.
- **S6 suite regression — NEXT** (full matrix incl. pre-1.10 world,
  dedicated server, two accounts) + listing docs; release stays
  user-gated. See the START HERE section for the test matrix.

## Next session

0. ~~App addon API~~ — **DONE IN CODE 2026-07-27** (see the section
   above); remaining work is the user's in-world pass (S6) and,
   post-release, announcing the API to addon authors (maybe a short
   API.md / wiki page — not written yet, javadoc is the doc for now).
1. ~~Redstone follow mode~~ — **DONE IN CODE 2026-07-28, batched into
   1.10.0** (see the section above; the original design notes follow
   for reference). **(queued 2026-07-22, user-requested; now ships
   AFTER the launcher, user decision 2026-07-27)** — a
   POWERED mounted tablet tracks the nearest player like the
   enchanting-table book. Design agreed: SERVER-driven (the mount
   pitch/yaw feed renderer AND hit-test — a client-only cosmetic
   follow would break tap accuracy), via `neighborChanged` power
   sensing (BE flag, re-derived on load — no new blockstate) + a
   server tick calling `aimAt(nearest non-spectator ≤~8 blocks)` at
   ~3-4 tick cadence with a small angle threshold, + client lerp in
   the BER so it glides. Power off = stays put. No new registries or
   payloads → ships as 1.9.2, pairs with 1.9.x. ~Half session incl.
   tuning range/cadence; needs a multiplayer feel-test.
2. **Listing refresh for 1.9.0 (text can go anytime, image needs the
   shoot)** — rewrite DESCRIPTION.md's "recipe" section (~184-194) to
   the manufacturing story and replace recipe.png with an
   assembly-line or JEI-chain shot; then paste to both listings.
3. ~~Signal links~~ — **DONE IN CODE 2026-07-28, batched into 1.10.0**
   (user decision — the open registrar break made it free; see the
   section above; original scoping follows for reference). **(scoped
   2026-07-25 as "app links", renamed with the 1.10.0 rename)** —
   state-level linking: tapping signal A also flips B (tile lights,
   transmits B's own strength/freqs). NOTE signals are already
   multi-frequency scene buttons (8 freqs) — shared freqs cover
   "one tap, many outputs" today; this feature is the visible state
   sync. Design: stable per-signal id + link list (target id + mode
   on/off/follow) — two new Signal fields (optionalFieldOf,
   extend the HAND-ROLLED stream codec) → registrar bump = pairing
   break. **1.10.0 is already a break (registrar "14") — batching
   this into 1.10.0 is free**; flagged to the user, no decision yet.
   Propagation: one shared helper at the two mutation sites
   (ModNetworking toggle handler ~:508, TabletBlock tap ~:413),
   single pass + visited set (kills loops, allows chains). v1
   targets: toggles + timers only (momentary transient, sliders
   excluded or "on = max"). UI is half the cost: edit screen has NO
   vertical room (240 budget) → "Links" chip opens a PickerOverlay
   listing other signals, cycling none/on/off/follow; tiny procedural
   chain glyph on linked tiles. ~1-2 sessions.
4. **Sable / Create Aeronautics compat (investigate on demand)** —
   tablets on Sable sublevels (Aeronautics ships): prediction from
   2026-07-21 analysis — rendering/transmit/flat-taps likely fine
   (real interactive blocks, remapped raycasts), but ALL eye-ray math
   mixes main-world eye coords with sublevel block coords and will
   misbehave on a moved ship: mounted taps/aiming/wrench regions and
   BlockSliderDrag. Fix if testers confirm: a Sable compat shim that
   transforms the eye into sublevel space at the three eye-ray call
   sites (optional-dep pattern like JEI/EMI). Wait for a real tester
   report from an Aeronautics pack before building.
5. **Screenshot shoot follow-up** — the user + wife will shoot, timing
   theirs: (a) a merged tablet wall (3×2 or 4×3, dyed bezel, mid-tap —
   strong candidate to replace hero4 as hero/social preview; bonus: a
   swivel-mounted tablet angled in the foreground), (b) the pinned
   overlay during real gameplay (hotbar visible). Checklist + exact
   slot placement live at the bottom of `docs/DESCRIPTION.md` (two
   `<!-- 📸 -->` comments). The description TEXT is already current
   through 1.8.1 ("Aim it anywhere" section, nameable bullet, stand
   swapping; games stay unmentioned) and can be pasted to both
   listings before the shoot — the slots render as nothing.
6. **Reactive hotfixes** — the tester crew is on 1.7.x/1.8.x now. The
   pre-release test-debt sweep (multiplayer dev-server, floor/ceiling
   orientation, ponder + held-item regression, chunk-border surfaces)
   was DROPPED by user decision 2026-07-21 ("let the testers find
   bugs") — do NOT re-propose it; those areas are simply where to look
   first if a report comes in.
7. **App Store categories** (user-requested 2026-08-08, mid-beta.2) —
   the shelf is now 26 programs deep (utilities, games, media with
   Twitch joining); group it with category headers, filter chips, or
   per-category tabs instead of one flat scrolling list. Client UI
   only, no wire/persistence change — pairs-safe, no registrar bump.
   Design open: headers (cheapest, `StoreScreen` already scrolls),
   filter chips (needs a selected-category client state), or tabs
   (biggest rework, competes with the existing scroll). Needs a
   category assignment per `Program`/addon entry either way — scope
   that decision first.

## Parked (don't propose unless the user re-raises)

- **Display Link target app** — parked 2026-08-09 mid-brainstorm
  ("lets shelf this whole idea for now"), revives the tier-2 item from
  the 2026-07-22 factory-gauges scoping. Agreed core before parking:
  the tablet block registers as a Create `DisplayTarget`, so Create's
  native Display Link flow (click target with the link item → place
  link on source) accepts placed tablets — Create does all gathering
  and source config, we render pushed text lines (likely a new
  Program with kiosk face/GUI/overlay, undesigned). OPEN question
  where the park hit: the user also wants binding a link to a HELD
  tablet ("display data on the go") — clicking a placed Display Link
  with the tablet item. Options laid out: (B) identity-based wireless
  bind to the tablet item, works in hand/chest/placed — the real
  want, but ~2+ sessions (identity tracking, no Create precedent) vs
  (C) v1 = placed-targets only, ~1 session. No decision made.

- **Survival tablet cloning** — parked 2026-08-09 ("backburner",
  scoped in-session): creative pick-block already full-clones
  (everything incl. paint rides the item). Survival options if it
  comes back: (1) crafting-grid clone, the written-book pattern —
  configured tablet + blank → both configured, ~half session, no
  wire changes, could ride any release; (2) deployer "flashing
  station" on the assembly line — Create-flavored, heavier. Open
  scope Qs recorded: clone everything vs config-only (painting?),
  does a clone copy the anvil name.

- **Orbital Cannon launch-control app** — parked 2026-07-29 mid-
  brainstorm ("I have an idea for this for later", before any design
  decisions). KEY FINDING from the context pass: **API v1 has no
  transmit surface** — ProgramHost is theme/name/goHome/pin only, and
  there's no addon networking. Any signal-firing addon app must either
  bring its OWN payload + Create hookup (own IRedstoneLinkable like
  compat/VirtualTransmitter — addons are full mods, viable) or wait
  for an API v2 transmit/frequency surface (permanent commitment —
  design carefully when it happens). Packaging question (standalone
  addon vs built-in vs addon+API-v2) was asked but never answered.
- **Factory gauges / data programs** — parked 2026-07-22 ("just a
  thought"). Scoped in-session, three tiers: (1) Gauge signal type that
  LISTENS on a link frequency and shows received 0–15 as a dial —
  flips the existing transmitter compat, works held + overlay,
  ~1-2 sessions, needs a lightweight value-sync payload → registrar
  bump; (2) tablet BE as a Create Display Link TARGET — all of
  Create's display sources for free on placed/merged screens,
  ~1 session basic; (3) bespoke deep readouts (stress graphs, vault
  browsing) — REJECTED, competes with Create's display system.
  Client-only utility programs (clock, calculator) cost what a game costs.
- **Icon-friendly defaults** — parked 2026-07-21 (was on hold for
  tester intel that never firmed up). Analysis in
  `docs/ICON_DEFAULTS_SCOPING.md` if it comes back.
- First-person interactive GUI on the held tablet.
- Distant Horizons LOD nit (WALL blockstate `landscape` fallback —
  harmless, LOD-only).
- 1.20.1 backport — possible but a big lift (Forge loader, no
  DataComponents/StreamCodec, second codebase); wait for real demand.
- **Fabric port — BLOCKED upstream** (scoped 2026-07-23): Create
  Fabric's last stable is 0.5.1 on 1.20.1; their 6.x port for 1.21.x
  is unstable/not recommended, so there's nothing to build against.
  Revisit only when Create Fabric 6 goes stable on 1.21.x, then it's
  ~1-2 weeks: ~23/79 files touch NeoForge — registries/networking/
  mods.toml are mechanical (payloads are vanilla CustomPacketPayload
  already), the real work is ClientSetup render plumbing (no BEWLR/
  IClientItemExtensions → BuiltinItemRendererRegistry, ModelEvent →
  ModelLoadingPlugin) plus re-verifying the batch-ordering invariants
  and the compat/ classes against Create Fabric's divergent
  internals. Targeting 1.20.1 Create 0.5.1 instead = rewrite, ruled
  out. Structure call if it happens: plain fork over multiloader
  refactor, and only with real demand.
- Declined tester items (vetted 2026-07-17, batch arrived via a
  disavowed shared-keyboard message): the "blank white tablet in
  ponder" report (if ever reproduced by a second person: ponder's
  virtual level may skip block color handlers — check ponder-renderer
  tinting first) and a PurpleFox easter egg (the suspect requested it
  themselves).

## Release history (compressed)

- 1.9.1 (2026-07-26): JEI/EMI drag onto the icon slot (client-only,
  pairs with 1.9.0).
- 1.9.0 (2026-07-25): deployer-manufactured tablet — sequenced
  assembly (case → board → cell → display), 5 new items, stage-aware
  incomplete art, crafting recipe removed. Registrar "13"; pairs
  with nothing older.
- 1.8.0 (2026-07-21) / 1.8.1 (2026-07-22): Swivel Mount item, then
  the placeable stand + separate pickup; nameable tablets; the silent
  19-game arcade. Registrar "13" throughout, but 1.8.1's new block
  registration means only 1.8.1 pairs with 1.8.1.
- 1.7.0 (2026-07-21): multiblock screens, pinned overlay + keybinds,
  overlay whitelist, JEI/EMI drag, solo screens. Registrar "11"→"13"
  ("12" = AppTarget slot mode, "13" = SurfaceLinkPayload).
- 1.6.0 (2026-07-19): per-app note windows (tester request), Timer app
  type (migdzy, credited). Registrar "9"→"11". Modrinth first approval
  cleared this day — all platforms live since.
- 1.5.x (2026-07-16/17): Create-style chrome overhaul (GUI + placed
  screens), Parchment + Avionics themes, slider ranges, one-item
  frequencies, grid chip tiles, creative Redstone-tab listing,
  restyled icon. Registrar "7"→"9".
- 1.4.0: ItemStack frequencies (frequency-card compat), container-menu
  editor, wrench rotation + landscape mounting, dye wash, slider apps
  with click-and-slide. Registrar "5"→"7".
- 1.3.x: dynamic screen tiles, list mode, quick-add, six themes
  ("PurpleFox" honors a tester), ponder scene, momentary buttons.
  Registrar "4"→"5".
- Earlier public: v1.3.2, v1.2.1, v1.2.0, v1.1.1 (first upload).
- Dev tooling in `tools/` (jar-excluded): `./gradlew nbtTool
  --args="gen|dump <path>"` (ponder schematic), `./gradlew iconTool
  --args="docs/icon.png"` (listing icon), `./gradlew chromeTool` (GUI
  chrome atlas). Relative, space-free args — Gradle splits on spaces.
