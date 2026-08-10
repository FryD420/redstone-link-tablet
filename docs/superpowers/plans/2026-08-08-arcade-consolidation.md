# Arcade Consolidation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 19 individual game apps with one Arcade app (1.11.0 beta.3): store shelves Arcade, its hub screen lists the games with best scores, rosters migrate automatically.

**Architecture:** `Program.ARCADE(28)` joins the enum; `Programs.catalog()` stops shelving `game=true` programs and `Programs.fromKeys` maps game keys → ARCADE (roster auto-migration). New `ArcadeHubScreen` (StoreScreen shelf pattern) launches games via a new `SecretGames.createApp` overload that sets the hub as the ESC return. Game enum constants/keys/dispatch are untouched — frozen identities. Spec: `docs/superpowers/specs/2026-08-08-arcade-consolidation-design.md`.

**Tech Stack:** Client/catalog only — no wire, registrar, or component changes. Gate per task: `./gradlew build` green.

## Global Constraints

- Id **28** for ARCADE (6 stays retired forever); key `"arcade"` frozen once shipped; `game=false` (4-arg ctor).
- The 19 game constants, keys, lang entries, `SecretGames` dispatch, and secret-pip behavior are UNTOUCHED.
- Per-task commits on `tablet-overlay`, push allowed; trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Mod version bumps to `1.11.0-beta.3` only in the final task.

---

### Task 1: Program.ARCADE + catalog shrink + roster migration

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/Program.java` (after `TWITCH(27, ...)`)
- Modify: `src/main/java/com/modpack/linktablet/Programs.java:99-123` (catalog + fromKeys)
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

**Interfaces:**
- Produces: `Program.ARCADE` (id 28, key `"arcade"`); `Programs.catalog()` without games; `Programs.fromKeys` migrating game keys → ARCADE.

- [ ] **Step 1:** Enum constant after `TWITCH(...)` (change its `;` to `,`):

```java
    /** Arcade (1.11.0): the 19 games consolidated under one door —
     * reverses the 1.10.0 per-game-app decision by user request
     * (2026-08-08); id 6 (the July hub) stays retired. */
    ARCADE(28, "arcade", 0xFF169C9C, "create:linked_controller");
```

- [ ] **Step 2:** Lang beside the other program entries:

```json
"program.linktablet.arcade": "Arcade",
"program.linktablet.arcade.desc": "All 19 mini-games in one cabinet — best scores included.",
"gui.linktablet.arcade.best": "Best: %s"
```

- [ ] **Step 3:** `Programs.catalog()` — exclude games (the store/add-flow shrink):

```java
    /** Everything the App Store shelves — every program except the
     * launcher itself (the desk the tiles sit on), the store (it
     * doesn't sell itself), and the games (they live inside the
     * Arcade since 1.11.0 — the enum constants stay for frozen keys,
     * kiosks, and the secret pips, but the catalog hides them). */
    public static List<TabletProgram> catalog() {
        List<TabletProgram> catalog = new ArrayList<>();
        for (TabletProgram program : TABLE.values()) {
            if (program == Program.LAUNCHER || program == Program.STORE) continue;
            if (program instanceof Program builtin && builtin.gameId() != null) continue;
            catalog.add(program);
        }
        return catalog;
    }
```

- [ ] **Step 4:** `Programs.fromKeys` — migrate game keys (insert before the existing null/launcher/store filter, keeping dedupe):

```java
    /** Stored key list → programs; unknown/launcher/store keys drop
     * silently (the downgrade story), an empty result falls back to
     * the default roster. Game keys resolve to the ARCADE tile
     * (1.11.0 consolidation) — a roster of Snake + 2048 + Signals
     * loads as Arcade + Signals, deduped; the next roster edit
     * writes the migrated form. */
    public static List<TabletProgram> fromKeys(List<String> keys) {
        if (keys == null) return DEFAULT_HOME;
        List<TabletProgram> home = new ArrayList<>();
        for (String key : keys) {
            TabletProgram program = TABLE.get(key);
            if (program instanceof Program builtin && builtin.gameId() != null) {
                program = Program.ARCADE;
            }
            if (program != null && program != Program.LAUNCHER && program != Program.STORE
                    && !home.contains(program)) {
                home.add(program);
            }
        }
        return home.isEmpty() ? DEFAULT_HOME : List.copyOf(home);
    }
```

- [ ] **Step 5:** Build green (ARCADE has no screen yet — `screenFor`'s `default ->` shows the launcher; fine until Task 2). **Step 6:** Commit `1.11.0-dev: Program.ARCADE — catalog shrink + roster migration`.

### Task 2: ArcadeHubScreen + launch wiring

**Files:**
- Create: `src/main/java/com/modpack/linktablet/client/screen/ArcadeHubScreen.java`
- Modify: `src/main/java/com/modpack/linktablet/client/screen/SecretGames.java:12-21`
- Modify: `src/main/java/com/modpack/linktablet/client/ClientHooks.java` (screenFor switch)

**Interfaces:**
- Consumes: `Programs`/`Program.gameId()`, `ClientPrefs.gameBest(String)` (0 = none), `SecretGames.create/createApp`, `ArcadeScreen.setReturnProgram(Program)` (package-private, same package), StoreScreen's shelf layout.
- Produces: `ArcadeHubScreen(SignalView view)`; `SecretGames.createApp(String id, SignalView view, Program returnTo)`.

- [ ] **Step 1:** `SecretGames` — add the return-target overload, keep the old signature delegating:

```java
    /** App-tile launch (1.10.0: every game is its own program): same
     * dispatch, but ESC goes Home — the tile is the door, unlike
     * secret-pip launches which return to the signals grid. */
    public static Screen createApp(String id, SignalView view) {
        return createApp(id, view, com.modpack.linktablet.Program.LAUNCHER);
    }

    /** Arcade-hub launch (1.11.0): same dispatch, ESC returns to the
     * given program — the hub passes ARCADE so the shelf feels like
     * home base. */
    public static Screen createApp(String id, SignalView view,
                                   com.modpack.linktablet.Program returnTo) {
        Screen screen = create(id, view, true);
        if (screen instanceof ArcadeScreen arcade) {
            arcade.setReturnProgram(returnTo);
        }
        return screen;
    }
```

- [ ] **Step 2:** `ArcadeHubScreen` — pattern StoreScreen end to end (constants, layout methods, scroll, scissor, header, `isPauseScreen` false, `UISounds`), with these deltas: title `Program.ARCADE.displayName()`; NO pin button (Home only — the spec's v1 limit); rows over the game list built once in the constructor:

```java
    private static java.util.List<Program> games() {
        java.util.List<Program> games = new java.util.ArrayList<>();
        for (Program program : Program.values()) {
            if (program.gameId() != null) games.add(program);
        }
        return games;
    }
```

Row rendering: chip fill in `program.chipColor()`, icon item (the StoreScreen icon block), display name at `theme.textPrimary`, and — replacing the store's description line — the best score via `ClientPrefs.gameBest(program.key())`: when > 0 draw `Component.translatable("gui.linktablet.arcade.best", best)` at `theme.textMuted`, when 0 draw nothing. No Get/Remove button; the WHOLE row is the tap target: `Minecraft.getInstance().setScreen(SecretGames.createApp(program.key(), view, Program.ARCADE))` with `UISounds.page()`.
- [ ] **Step 3:** `ClientHooks.screenFor` — add `case ARCADE -> new com.modpack.linktablet.client.screen.ArcadeHubScreen(view);` beside the other cases (before `default`). The game-program dispatch above the switch (`builtin.gameId() != null` → `SecretGames.createApp(game, view)`) stays — kiosk taps on game-faced kiosks keep their ESC-goes-Home behavior via the 2-arg overload.
- [ ] **Step 4:** Build green. **Step 5:** Commit `1.11.0-dev: ArcadeHubScreen — one cabinet for the 19 games`.

### Task 3: Docs + beta.3

**Files:**
- Modify: `CHANGELOG.md`, `docs/NEXT_SESSION.md`, `gradle.properties`

- [ ] **Step 1:** CHANGELOG "Unreleased": Arcade bullet — the 19 games moved into one **Arcade** app (shelf with best scores, tap to play, ESC back to the shelf); existing game tiles on your Home screen become a single Arcade tile automatically; the App Store shrinks accordingly. No pairing impact (this part is client-only).
- [ ] **Step 2:** NEXT_SESSION: new top status (beta.3 = beta.2 + Arcade consolidation, the deliberate-reversal note, no registrar change — still "22") + append the spec's test matrix verbatim.
- [ ] **Step 3:** `gradle.properties` → `mod_version=1.11.0-beta.3`; full `./gradlew build`; jar `build/libs/linktablet-1.11.0-beta.3.jar`.
- [ ] **Step 4:** Commit `1.11.0-beta.3: Arcade consolidation + docs`. STOP — push, jar delivery, and the tester checklist are the controller's steps.

---

## Self-review

**Spec coverage:** ARCADE program + lang (T1), catalog shrink incl. store/add flow (T1 — both read `catalog()`), fromKeys migration with dedupe (T1), hub screen + best scores + tap-to-launch + ESC-return-to-hub (T2), secret-pip untouched (no task touches `create(id, view, false)` or the pip path), kiosk game faces untouched (game dispatch kept in T3... in T2 Step 3), no-pin v1 limit (T2), docs + beta.3 (T3). Out-of-scope items need no tasks.

**Placeholders:** none — T1 carries full method bodies; T2 names exact pattern deltas with the launch call spelled out.

**Type consistency:** `createApp(String, SignalView, Program)` defined T2 Step 1, used T2 Step 2; `games()` local to the hub; `gameBest(String)` matches ClientPrefs (verified line 113); `gui.linktablet.arcade.best` defined T1, used T2.
