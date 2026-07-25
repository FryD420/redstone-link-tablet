# Roadmap and status

Project facts, build setup, gotchas, and the release process live in `CLAUDE.md`
at the repo root (auto-loaded every Claude session).

## Status (2026-07-25 — v1.9.0 TAGGED, upload = user's next step)

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

## Next session

1. **Redstone follow mode (queued 2026-07-22, user-requested)** — a
   POWERED mounted tablet tracks the nearest player like the
   enchanting-table book. Design agreed: SERVER-driven (the mount
   pitch/yaw feed renderer AND hit-test — a client-only cosmetic
   follow would break tap accuracy), via `neighborChanged` power
   sensing (BE flag, re-derived on load — no new blockstate) + a
   server tick calling `aimAt(nearest non-spectator ≤~8 blocks)` at
   ~3-4 tick cadence with a small angle threshold, + client lerp in
   the BER so it glides. Power off = stays put. No new registries or
   payloads → ships as 1.9.1, pairs with 1.9.0. ~Half session incl.
   tuning range/cadence; needs a multiplayer feel-test.
2. **Listing refresh for 1.9.0 (text can go anytime, image needs the
   shoot)** — rewrite DESCRIPTION.md's "recipe" section (~184-194) to
   the manufacturing story and replace recipe.png with an
   assembly-line or JEI-chain shot; then paste to both listings.
3. **App links — apps toggling other apps (scoped 2026-07-25)** —
   state-level linking: tapping app A also flips B (tile lights,
   transmits B's own strength/freqs). NOTE apps are already
   multi-frequency scene buttons (8 freqs) — shared freqs cover
   "one tap, many outputs" today; this feature is the visible state
   sync. Design: stable per-app id + link list (target id + mode
   on/off/follow) — two new SignalApp fields (optionalFieldOf,
   extend the HAND-ROLLED stream codec) → registrar bump = its own
   pairing break (missed the 1.9.0 window; batch with the next
   registry/payload-breaking release).
   Propagation: one shared helper at the two mutation sites
   (ModNetworking toggle handler ~:508, TabletBlock tap ~:413),
   single pass + visited set (kills loops, allows chains). v1
   targets: toggles + timers only (momentary transient, sliders
   excluded or "on = max"). UI is half the cost: edit screen has NO
   vertical room (240 budget) → "Links" chip opens a PickerOverlay
   listing other apps, cycling none/on/off/follow; tiny procedural
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

## Parked (don't propose unless the user re-raises)

- **Factory gauges / data apps** — parked 2026-07-22 ("just a
  thought"). Scoped in-session, three tiers: (1) Gauge app type that
  LISTENS on a link frequency and shows received 0–15 as a dial —
  flips the existing transmitter compat, works held + overlay,
  ~1-2 sessions, needs a lightweight value-sync payload → registrar
  bump; (2) tablet BE as a Create Display Link TARGET — all of
  Create's display sources for free on placed/merged screens,
  ~1 session basic; (3) bespoke deep readouts (stress graphs, vault
  browsing) — REJECTED, competes with Create's display system.
  Client-only utility apps (clock, calculator) cost what a game costs.
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
