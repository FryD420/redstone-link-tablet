# Screen Lock Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Placed tablets get a use-only screen lock — locked glass keeps working (taps, sliders, live faces) but ALL configuration needs a wrench in hand, enforced server-side; any wrench click on a locked tablet opens the GUI.

**Architecture:** A `locked` boolean on the block entity (block-only, controller-owned, mirrored onto merged-surface members so splits inherit it), one new `SetLockPayload` (registrar "23"→"24", pairing break), and ONE server-side choke point `configAllowed(player, target)` called at the top of every config-payload handler. World-side, `TabletBlock` gates pickup/bezel/GUI-open paths and reroutes the whole wrench map to "open the GUI" while locked. UI is a padlock header glyph (block views) plus a small padlock pip on locked faces.

**Tech Stack:** Java 21, NeoForge 1.21.1 (21.1.233), Create 6.0.10. No unit-test harness exists in this repo (Minecraft-runtime behavior); each task's verify step is the `./gradlew build` gate, and the user-run dev matrix in Task 7 is the acceptance test.

**Spec:** `docs/superpowers/specs/2026-08-10-screen-lock-design.md` (re-scoped 2026-08-11: the one-big-button is CUT — locked tablets render the normal grid at every signal count).

## Global Constraints

- Registrar bumps **"23"→"24"** — PAIRING BREAK vs 1.11.x; exactly one bump for this feature, with a numbered comment in `ModNetworking.register` like every previous fence.
- `locked` is **block-only**: never an item component, never in `toItemStack()`/`loadFromItem()` — a mined/re-placed tablet starts unlocked (spec decision).
- BE NBT key `locked`, absent = false, **never written false** (the `solo_screen` idiom).
- Use payloads (toggle / momentary / slider / timed) are NEVER gated — that is the whole point of "use-only lock". Link propagation is server-internal and rides the use paths untouched.
- Wrench detection is `stack.is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)` on either hand (the `TabletBlock.useItemOn` precedent) — no ownership, no UUIDs.
- Renderer additions obey the fills→icons→text batching rule: the lock pip fetches its OWN buffer after the face passes (fresh batch, never a cached consumer across an item render).
- Credit **Fluid Valve** in the changelog entry (house precedent: Timer/migdzy, Paint/Tommy).
- `mod_version` in `gradle.properties` is NOT bumped by this plan — release/beta numbering is user-gated (house release process).
- Commit messages end with the standard `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>` trailer.

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/com/modpack/linktablet/block/TabletBlockEntity.java` | `locked` field, getters/setters, surface walk, NBT |
| `src/main/java/com/modpack/linktablet/block/TabletSurfaceScanner.java` | lock OR-propagation on surface formation |
| `src/main/java/com/modpack/linktablet/network/ModNetworking.java` | `SetLockPayload`, handler, `holdingWrench`, `configAllowed`, registrar "24", 17 handler gates |
| `src/main/java/com/modpack/linktablet/block/TabletBlock.java` | world interaction gates + locked wrench map |
| `src/main/java/com/modpack/linktablet/client/screen/HeaderGlyphs.java` | padlock glyph pixel art |
| `src/main/java/com/modpack/linktablet/client/screen/TabletScreen.java` | padlock header button (block views) |
| `src/main/java/com/modpack/linktablet/client/screen/LauncherScreen.java` | same button on the launcher header |
| `src/main/java/com/modpack/linktablet/client/render/TabletScreenRenderer.java` | `renderLockPip` |
| `src/main/java/com/modpack/linktablet/client/render/TabletBlockEntityRenderer.java` | pip call after face dispatch |
| `src/main/resources/assets/linktablet/lang/en_us.json` | tooltip keys |
| `CHANGELOG.md`, `CLAUDE.md`, `docs/NEXT_SESSION.md` | docs |

---

### Task 1: BE lock state + surface propagation

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/block/TabletBlockEntity.java`
- Modify: `src/main/java/com/modpack/linktablet/block/TabletSurfaceScanner.java`

**Interfaces:**
- Produces: `boolean TabletBlockEntity.isLocked()`, `void TabletBlockEntity.setLocked(boolean)` (per-BE, syncs), `void TabletBlockEntity.lockSurface(boolean)` (controller entry point — walks members). Later tasks call `resolveController().isLocked()` for reads and `lockSurface` from the payload handler.

- [ ] **Step 1: Add the field**

Find the `private boolean soloScreen` declaration in `TabletBlockEntity.java` and add directly below it:

```java
    /** Use-only screen lock (block-only — never an item component; a
     * re-placed tablet starts unlocked). Controller-owned; members
     * MIRROR the flag so a surface split leaves every promoted
     * fragment still locked (spec decision, the solo-mark precedent). */
    private boolean locked;
```

- [ ] **Step 2: Add accessors + the surface walk**

Add a new section right after the `setSoloScreen` method (search for it; it's near the solo-screen accessors). If `setSoloScreen` doesn't exist as a method, place the section immediately before the `// ---- Frequency Monitor probe (1.11.0) ---` section:

```java
    // ---- Screen lock (1.12.0) ------------------------------------------

    public boolean isLocked() {
        return locked;
    }

    /** Per-BE setter (scanner + surface walk); syncs like the solo flag. */
    public void setLocked(boolean newLocked) {
        if (locked == newLocked) return;
        this.locked = newLocked;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** SetLockPayload entry point, called on the CONTROLLER: locks or
     * unlocks the whole surface — every member mirrors the flag (the
     * setLinked walk shape) so splitting a locked mural never unlocks
     * its pieces. */
    public void lockSurface(boolean newLocked) {
        setLocked(newLocked);
        if (level == null || !isSurfaceController()) return;
        BlockState state = getBlockState();
        for (int dx = 0; dx < surfaceW; dx++) {
            for (int dy = 0; dy < surfaceH; dy++) {
                if (dx == 0 && dy == 0) continue;
                BlockPos pos = worldPosition
                        .relative(TabletScreenMath.screenRight(state), dx)
                        .relative(TabletScreenMath.screenDown(state), dy);
                if (level.getBlockEntity(pos) instanceof TabletBlockEntity member) {
                    member.setLocked(newLocked);
                }
            }
        }
    }
```

(`BlockState`/`BlockPos` are already imported; the `.relative(TabletScreenMath.screenRight(state), …)` style avoids a `Direction` import, matching `getControllerPos()`.)

- [ ] **Step 3: Persist it**

In `saveAdditional`, directly after the `solo_screen` write (`if (soloScreen) { tag.putBoolean("solo_screen", true); }`):

```java
        if (locked) {
            tag.putBoolean("locked", true);
        }
```

In `loadAdditional`, directly after `this.soloScreen = tag.getBoolean("solo_screen");`:

```java
        this.locked = tag.getBoolean("locked");
```

Nothing in `toItemStack()` or `loadFromItem()` — the lock must NOT travel on the item.

- [ ] **Step 4: Scanner OR-propagation**

In `TabletSurfaceScanner.rescan`, after the `if (members.isEmpty()) return;` line and before the screen-space projection block, add:

```java
        // Screen lock (1.12.0): a surface containing ANY locked member
        // forms locked — otherwise placing one fresh tablet against a
        // locked wall could promote an UNLOCKED controller and open the
        // whole surface's config (every edit lands on the controller).
        boolean anyLocked = false;
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof TabletBlockEntity be && be.isLocked()) {
                anyLocked = true;
                break;
            }
        }
```

Then inside the role-assignment loop, in the `valid` branch only, after `be.setSurfaceRole(dR, dD, w, h);`:

```java
                be.setLocked(anyLocked);
```

The invalid/dissolve branch is left alone on purpose: members keep their mirrored flag, which IS the split-inheritance behavior.

- [ ] **Step 5: Build gate**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, jar produced.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/modpack/linktablet/block/TabletBlockEntity.java src/main/java/com/modpack/linktablet/block/TabletSurfaceScanner.java
git commit -m "Screen lock T1: BE locked flag, NBT, surface mirror + scanner OR-merge"
```

---

### Task 2: SetLockPayload + registrar "24"

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/network/ModNetworking.java`

**Interfaces:**
- Consumes: `TabletBlockEntity.isLocked()/lockSurface(boolean)` (Task 1).
- Produces: `ModNetworking.SetLockPayload(SignalTarget target, boolean locked)` public record (Task 5's GUIs send it), `private static boolean holdingWrench(Player)` (Task 3 reuses it).

- [ ] **Step 1: The payload record**

Add after the `PaintClearPayload` record:

```java
    // ------------------------------------------------------------------
    // Payload: screen lock (1.12.0) — block targets only. The server
    // demands a wrench in either hand BOTH ways (symmetric: it matches
    // how a locked tablet's GUI is reached).
    // ------------------------------------------------------------------
    public record SetLockPayload(SignalTarget target, boolean locked) implements CustomPacketPayload {
        public static final Type<SetLockPayload> TYPE = new Type<>(id("set_lock"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetLockPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, SetLockPayload::target,
                        ByteBufCodecs.BOOL, SetLockPayload::locked,
                        SetLockPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
```

- [ ] **Step 2: Wrench helper + handler**

Add near the other private helpers (e.g. right after `resolveStack`):

```java
    /** Wrench in either hand — the screen lock's one permission token
     * (1.12.0). No ownership: anyone holding a wrench holds the key. */
    private static boolean holdingWrench(Player player) {
        return player.getMainHandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)
                || player.getOffhandItem().is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH);
    }
```

Add the handler near `handleSurfaceLink`:

```java
    private static void handleSetLock(SetLockPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (payload.target().pos().isEmpty()) return; // block-only feature
        BlockPos pos = payload.target().pos().get();
        if (!player.level().isLoaded(pos)) return;
        if (tabletDistSqr(player, pos) > MAX_BLOCK_DISTANCE_SQ) return;
        if (!(player.level().getBlockEntity(pos) instanceof TabletBlockEntity be)) return;
        TabletBlockEntity controller = be.resolveController();
        if (controller == null) return;
        // Symmetric wrench rule: locking AND unlocking need the key in
        // hand — a UI-only check would be spoofable.
        if (!holdingWrench(player)) return;
        if (controller.isLocked() == payload.locked()) return;
        controller.lockSurface(payload.locked());
        player.level().playSound(null,
                com.modpack.linktablet.compat.SableCompat.worldBlockPos(player.level(), pos),
                payload.locked() ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, 0.5F, payload.locked() ? 1.4F : 1.2F);
    }
```

- [ ] **Step 3: Registrar bump + registration**

In `register`, append to the version-comment block:

```java
        // "24": 1.12.0 screen lock — SetLockPayload added and every
        // config payload gained the server-side wrench rule (PAIRING
        // BREAK vs 1.11.x).
```

Change `event.registrar("23")` → `event.registrar("24")`, and add at the end of the registration list:

```java
        registrar.playToServer(SetLockPayload.TYPE, SetLockPayload.STREAM_CODEC,
                ModNetworking::handleSetLock);
```

- [ ] **Step 4: Build gate**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/modpack/linktablet/network/ModNetworking.java
git commit -m "Screen lock T2: SetLockPayload + wrench-symmetric handler, registrar 24"
```

---

### Task 3: The one server rule — configAllowed choke point

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/network/ModNetworking.java`

**Interfaces:**
- Consumes: `holdingWrench(Player)` (Task 2), `TabletBlockEntity.isLocked()` (Task 1).
- Produces: `private static boolean configAllowed(Player, SignalTarget)` — internal only; the gate every config handler calls first.

- [ ] **Step 1: The helper**

Add directly below `holdingWrench`:

```java
    /**
     * The screen lock's ONE enforcement choke point (1.12.0): on a
     * LOCKED block target, config payloads are accepted only from a
     * sender currently holding a wrench (either hand). Called first by
     * every config-payload handler — never fork per-handler variants.
     * Use payloads (toggle/momentary/slider/timed) never call this.
     * Held/slot tablets can't lock, so item targets always pass.
     * Malformed block targets pass too: the handler's own resolution
     * rejects them a line later.
     */
    private static boolean configAllowed(Player player, SignalTarget target) {
        if (target.pos().isEmpty()) return true;
        BlockPos pos = target.pos().get();
        if (!player.level().isLoaded(pos)) return true;
        if (!(player.level().getBlockEntity(pos) instanceof TabletBlockEntity be)) return true;
        TabletBlockEntity controller = be.resolveController();
        if (controller == null || !controller.isLocked()) return true;
        return holdingWrench(player);
    }
```

- [ ] **Step 2: Gate every config handler**

Insert as the FIRST line of each handler body (after the `Player player = context.player();` line where one exists; where the handler has no local `player`, use `context.player()` inline). All 17:

| Handler | Gate line |
|---|---|
| `handleUpsert` | `if (!configAllowed(context.player(), payload.target())) return;` |
| `handleRemove` | `if (!configAllowed(context.player(), payload.target())) return;` |
| `handleReorder` | `if (!configAllowed(player, payload.target())) return;` |
| `handleScreenLayout` | `if (!configAllowed(player, payload.target())) return;` |
| `handleSetTheme` | `if (!configAllowed(player, payload.target())) return;` |
| `handleSetNote` | `if (!configAllowed(context.player(), payload.target())) return;` |
| `handleSetHomeApps` | `if (!configAllowed(player, payload.target())) return;` |
| `handleUpsertGauge` | `if (!configAllowed(context.player(), payload.target())) return;` |
| `handleRemoveGauge` | `if (!configAllowed(context.player(), payload.target())) return;` |
| `handleSetProgram` | `if (!configAllowed(player, payload.target())) return;` |
| `handleSetProbe` | `if (!configAllowed(player, payload.target())) return;` |
| `handleOpenProbeMenu` | `if (!configAllowed(player, payload.target())) return;` |
| `handleSetTwitchChannel` | `if (!configAllowed(player, payload.target())) return;` |
| `handlePaintStroke` | `if (!configAllowed(player, payload.target())) return;` |
| `handlePaintClear` | `if (!configAllowed(player, payload.target())) return;` |
| `handleSurfaceLink` | `if (!configAllowed(player, payload.target())) return;` |
| `handleOpenEditMenu` | `if (!configAllowed(player, payload.context().target())) return;` |

Do NOT touch `handleToggle`, `handleMomentary`, `handleSetSlider`, `handleTimed` (use payloads) or `handleGaugeReadings`/`handleMonitorSnapshot` (client-bound). `MonitorScanner::handleSubscribe` is read-only viewing, not config — untouched.

Mural protection falls out here for free: `handlePaintStroke`/`handlePaintClear` are gated.

- [ ] **Step 3: Build gate**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/modpack/linktablet/network/ModNetworking.java
git commit -m "Screen lock T3: configAllowed choke point gates all 17 config handlers"
```

---

### Task 4: World-side interaction gates (TabletBlock)

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/block/TabletBlock.java`

**Interfaces:**
- Consumes: `TabletBlockEntity.isLocked()` (Task 1), `ClientHooks.openTabletBlockScreen(BlockPos)` (existing).
- Produces: `private static void denyClick(Level, BlockPos)` (internal soft-deny tick).

- [ ] **Step 1: Deny helper**

Add next to `audiblePos`:

```java
    /** Soft deny tick (1.12.0): a locked wall answers "locked", not
     * "broken" — low-pitch off-click, distinct from the toggle sounds. */
    private static void denyClick(Level level, BlockPos pos) {
        level.playSound(null, audiblePos(level, pos), SoundEvents.STONE_BUTTON_CLICK_OFF,
                SoundSource.PLAYERS, 0.3F, 0.7F);
    }
```

- [ ] **Step 2: Gate sneak+empty-hand pickup**

In `useWithoutItem`, at the top of the `if (player.isSecondaryUseActive())` branch (before the existing pickup code):

```java
            // Screen lock (1.12.0): every pickup-BY-HAND path is config
            // — unlock first. Mining still drops the tablet normally
            // (and it places back unlocked, spec decision).
            if (level.getBlockEntity(pos) instanceof TabletBlockEntity lockBe
                    && lockBe.resolveController() instanceof TabletBlockEntity lockTarget
                    && lockTarget.isLocked()) {
                if (!level.isClientSide) {
                    denyClick(level, pos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
```

- [ ] **Step 3: Gate the launcher-face taps**

In the `target.currentProgram() == Program.LAUNCHER` branch, replace the tile-hit body:

```java
                if (tile != null) {
                    if (!level.isClientSide) {
                        if (target.isLocked()) {
                            // Program nav is config (spec list)
                            denyClick(level, pos);
                        } else {
                            target.setCurrentProgram(home.get(tile.index()));
                            ModNetworking.playToggleClick(level, null, pos, true);
                        }
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
```

and replace the off-tile fall-through (`if (level.isClientSide) { ClientHooks.openBlockHome(controllerPos); } return …`) with:

```java
                if (target.isLocked()) {
                    // No GUI without a wrench — the wrench-click path is
                    // the one door in (onWrenched).
                    if (!level.isClientSide) {
                        denyClick(level, pos);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                if (level.isClientSide) {
                    ClientHooks.openBlockHome(controllerPos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
```

- [ ] **Step 4: Gate the bezel Home tap**

In the bezel branch (`mountedOnBezel(...) : TabletScreenMath.hitBezel(...)`), replace the body:

```java
                if (!level.isClientSide) {
                    if (target.isLocked()) {
                        denyClick(level, pos);
                    } else {
                        target.setCurrentProgram(Program.LAUNCHER);
                        ModNetworking.playToggleClick(level, null, pos, false);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
```

Leave the NEXT branch (non-signals program face → `ClientHooks.openBlockProgram`) UNTOUCHED: program screens stay reachable on a locked tablet by design — they're viewers, and every config they can send bounces off Task 3's server rule (the spec's mural test encodes this: PaintScreen opens, strokes deny).

- [ ] **Step 5: Gate the signals off-pip GUI open**

Replace the tail of the BE branch (the `if (level.isClientSide) { ClientHooks.openTabletBlockScreen(controllerPos); } return …` right after the pip handling):

```java
            if (target.isLocked()) {
                if (!level.isClientSide) {
                    denyClick(level, pos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (level.isClientSide) {
                ClientHooks.openTabletBlockScreen(controllerPos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
```

The final no-BE fallback (`if (level.isClientSide) { ClientHooks.openTabletBlockScreen(pos); }`) stays as is — no BE means nothing can be locked.

Pip/slider/timer/momentary handling above these edits is untouched: use-only.

- [ ] **Step 6: Locked wrench map — onWrenched opens the GUI**

At the top of `onWrenched`, before the `if (state.getValue(MOUNTED))` block:

```java
        // Screen lock (1.12.0): while locked the wrench IS the key —
        // any wrench click, any face/region, flat or mounted, opens the
        // GUI and replaces the whole wrench map (rotate, landscape
        // flip, mounted re-aim all park until unlocked).
        if (level.getBlockEntity(pos) instanceof TabletBlockEntity lockBe
                && lockBe.resolveController() instanceof TabletBlockEntity lockTarget
                && lockTarget.isLocked()) {
            if (level.isClientSide) {
                ClientHooks.openTabletBlockScreen(lockTarget.getBlockPos());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
```

(The `level.isClientSide`-guarded `ClientHooks` call is the established dist-safe idiom in this class.)

- [ ] **Step 7: Locked sneak-wrench — blocked with the deny cue**

At the top of `onSneakWrenched`:

```java
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // Screen lock (1.12.0): sneak-wrench pickup paths (incl. mount
        // pickup and the mounted glass rotate) are config — unlock first.
        if (level.getBlockEntity(pos) instanceof TabletBlockEntity lockBe
                && lockBe.resolveController() instanceof TabletBlockEntity lockTarget
                && lockTarget.isLocked()) {
            if (!level.isClientSide) {
                denyClick(level, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
```

(The method already declares `level`/`pos` locals below — merge with them rather than redeclaring: keep the existing two declarations at the top and insert only the `if` block after them.)

- [ ] **Step 8: Build gate**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/com/modpack/linktablet/block/TabletBlock.java
git commit -m "Screen lock T4: world gates - pickup/bezel/nav deny, wrench opens GUI"
```

---

### Task 5: Padlock header glyph (GUI) + lang

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/screen/HeaderGlyphs.java`
- Modify: `src/main/java/com/modpack/linktablet/client/screen/TabletScreen.java`
- Modify: `src/main/java/com/modpack/linktablet/client/screen/LauncherScreen.java`
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

**Interfaces:**
- Consumes: `ModNetworking.SetLockPayload` (Task 2), `TabletBlockEntity.isLocked()` (Task 1).
- Produces: `static void HeaderGlyphs.lock(GuiGraphics g, int x, int y, int color, boolean locked)`.

- [ ] **Step 1: The glyph**

Add to `HeaderGlyphs` after the `link` glyph:

```java
    /** Padlock — solid body; the shackle swings open when unlocked. */
    static void lock(GuiGraphics g, int x, int y, int color, boolean locked) {
        if (locked) {
            g.fill(x + 3, y + 1, x + 9, y + 2, color);
            g.fill(x + 3, y + 2, x + 4, y + 5, color);
            g.fill(x + 8, y + 2, x + 9, y + 5, color);
        } else {
            g.fill(x + 5, y + 1, x + 11, y + 2, color);
            g.fill(x + 10, y + 2, x + 11, y + 5, color);
            g.fill(x + 5, y + 2, x + 6, y + 4, color);
        }
        g.fill(x + 2, y + 5, x + 10, y + 11, color);
    }
```

- [ ] **Step 2: TabletScreen — layout + state helpers**

After `linkBtnX()`:

```java
    /** Screen-lock toggle, right of the link — placed tablets only. */
    private int lockBtnX() {
        return linkBtnX() + MODE_BTN_SIZE + 4;
    }
```

After `soloScreen()` (same shape):

```java
    /** Whether the viewed placed tablet('s controller) is LOCKED. */
    private boolean lockedScreen() {
        if (!(view instanceof SignalView.Block block) || minecraft == null
                || minecraft.level == null) {
            return false;
        }
        if (!(minecraft.level.getBlockEntity(block.pos()) instanceof TabletBlockEntity be)) {
            return false;
        }
        TabletBlockEntity resolved = be.resolveController();
        return (resolved != null ? resolved : be).isLocked();
    }

    /** Wrench in either hand, client-side — the deny PRE-check only;
     * the server enforces the same rule regardless. */
    private boolean holdingWrench() {
        return minecraft != null && minecraft.player != null
                && (minecraft.player.getMainHandItem()
                        .is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH)
                    || minecraft.player.getOffhandItem()
                        .is(net.neoforged.neoforge.common.Tags.Items.TOOLS_WRENCH));
    }
```

- [ ] **Step 3: TabletScreen — render, click, tooltip**

In `renderModeButtons`, extend the `if (isBlockView())` block:

```java
        if (isBlockView()) {
            boolean solo = soloScreen();
            HeaderGlyphs.link(graphics, linkBtnX(), y,
                    glyphColor(solo, overModeBtn(mouseX, mouseY, linkBtnX())), solo);
            boolean locked = lockedScreen();
            HeaderGlyphs.lock(graphics, lockBtnX(), y,
                    glyphColor(locked, overModeBtn(mouseX, mouseY, lockBtnX())), locked);
        }
```

In `mouseClicked`, after the link-button `if` block:

```java
            if (isBlockView() && overModeBtn(mouseX, mouseY, lockBtnX())) {
                if (holdingWrench()) {
                    boolean locked = lockedScreen();
                    UISounds.tick(locked ? 1.7F : 0.6F);
                    PacketDistributor.sendToServer(
                            new ModNetworking.SetLockPayload(target(), !locked));
                } else {
                    UISounds.tick(0.7F); // deny — the wrench is the key
                }
                return true;
            }
```

In the tooltip chain (after the pin tooltip `else if`):

```java
        } else if (isBlockView() && !themePopupOpen && overModeBtn(mouseX, mouseY, lockBtnX())) {
            graphics.renderTooltip(font, Component.translatable(lockedScreen()
                    ? "gui.linktablet.lock.unlock" : "gui.linktablet.lock.lock"), mouseX, mouseY);
```

- [ ] **Step 4: LauncherScreen — same button**

`LauncherScreen` mirrors TabletScreen's header (settings parity, 1.10.0). Repeat Step 2's three helpers verbatim (`lockBtnX` after its `linkBtnX`, `lockedScreen`/`holdingWrench` after its `soloScreen`) — its payload target is `view.target()`:

- render: extend its `if (isBlockView())` glyph block exactly as Step 3's render snippet.
- click: after its link-button block:

```java
            if (isBlockView() && overModeBtn(mouseX, mouseY, lockBtnX())) {
                if (holdingWrench()) {
                    boolean locked = lockedScreen();
                    UISounds.tick(locked ? 1.7F : 0.6F);
                    PacketDistributor.sendToServer(
                            new ModNetworking.SetLockPayload(view.target(), !locked));
                } else {
                    UISounds.tick(0.7F); // deny — the wrench is the key
                }
                return true;
            }
```

- tooltip: if LauncherScreen has a tooltip chain for header buttons, add the same entry; if it has none, skip (parity with its current behavior).

Layout check: `lockBtnX` sits right of `linkBtnX` on the left-side cluster; the right-side `grid`/`list` buttons are anchored from `panelLeft() + panelWidth()`. If the glyphs collide at minimum panel width in the dev client, flag it in the task report — do NOT silently rearrange the header.

- [ ] **Step 5: Lang keys**

In `en_us.json`, next to `"gui.linktablet.overlay.pin"`:

```json
  "gui.linktablet.lock.lock": "Lock screen (wrench is the key)",
  "gui.linktablet.lock.unlock": "Unlock screen",
```

- [ ] **Step 6: Build gate**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/modpack/linktablet/client/screen/HeaderGlyphs.java src/main/java/com/modpack/linktablet/client/screen/TabletScreen.java src/main/java/com/modpack/linktablet/client/screen/LauncherScreen.java src/main/resources/assets/linktablet/lang/en_us.json
git commit -m "Screen lock T5: padlock header glyph on block GUIs + lang"
```

---

### Task 6: Locked-face padlock pip (world renderer)

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/render/TabletScreenRenderer.java`
- Modify: `src/main/java/com/modpack/linktablet/client/render/TabletBlockEntityRenderer.java`

**Interfaces:**
- Consumes: `TabletBlockEntity.isLocked()` (Task 1), existing `fillRect`, `SCREEN_TYPE`, `LAYER` in `TabletScreenRenderer`.
- Produces: `public static void TabletScreenRenderer.renderLockPip(PoseStack, MultiBufferSource, ScreenTheme, boolean backlit, int packedLight, int surfaceW, int surfaceH)`.

- [ ] **Step 1: The pip painter**

Add to `TabletScreenRenderer` (after `renderLabelFace`):

```java
    /**
     * Locked-face pip (1.12.0): a ~2-texel padlock hugging the
     * bottom-right glass corner so a locked wall reads "locked", not
     * "broken". Drawn in the PHYSICAL frame (no content rotation — it's
     * device status, not content) AFTER the face passes, with its OWN
     * buffer fetch: a fresh batch, so it can never interleave with a
     * cached consumer across an item render (the "Not building!" rule).
     * Quads ride the icon hairline layer (3.5x).
     */
    public static void renderLockPip(PoseStack poseStack, MultiBufferSource buffers,
                                     ScreenTheme theme, boolean backlit, int packedLight,
                                     int surfaceW, int surfaceH) {
        int members = surfaceW * surfaceH;
        float physW = members == 1 ? TabletScreenMath.GLASS_U1 - TabletScreenMath.GLASS_U0
                : TabletScreenMath.surfaceGlassW(surfaceW);
        float physH = members == 1 ? TabletScreenMath.GLASS_V1 - TabletScreenMath.GLASS_V0
                : TabletScreenMath.surfaceGlassH(surfaceH);
        float u1 = TabletScreenMath.GLASS_U0 + physW;
        float v1 = TabletScreenMath.GLASS_V0 + physH;
        VertexConsumer vc = buffers.getBuffer(SCREEN_TYPE);
        PoseStack.Pose pose = poseStack.last();
        int light = backlit ? LightTexture.FULL_BRIGHT : packedLight;
        int color = theme.textFaint;
        float x1 = u1 - 0.5f;
        float y1 = v1 - 0.5f;
        float x0 = x1 - 1.6f;
        float y0 = y1 - 1.9f;
        // Shackle: top bar + two legs
        fillRect(pose, vc, x0 + 0.35f, y0, x1 - 0.35f, y0 + 0.25f, LAYER * 3.5f, color, light);
        fillRect(pose, vc, x0 + 0.35f, y0 + 0.25f, x0 + 0.6f, y0 + 0.9f, LAYER * 3.5f, color, light);
        fillRect(pose, vc, x1 - 0.6f, y0 + 0.25f, x1 - 0.35f, y0 + 0.9f, LAYER * 3.5f, color, light);
        // Body
        fillRect(pose, vc, x0, y0 + 0.9f, x1, y1, LAYER * 3.5f, color, light);
    }
```

(If `LightTexture` is not yet imported in this file it is — `beginScreen` uses it.)

- [ ] **Step 2: Call it from the one face dispatch**

In `TabletBlockEntityRenderer`, rename the existing `renderFace` method to `renderFaceContent` (private, same signature), then add a new `renderFace` with the old signature that wraps it — this keeps BOTH the flat and mounted call sites untouched:

```java
    /** Face dispatch + the lock pip on top (1.12.0) — one wrapper so
     * flat and mounted passes stay a single call site. */
    private static void renderFace(TabletBlockEntity be, BlockState state, List<Signal> signals,
                                   com.modpack.linktablet.api.TabletProgram program,
                                   PoseStack poseStack, MultiBufferSource buffers, int packedLight,
                                   int surfaceW, int surfaceH, int caseTint, float partialTick) {
        renderFaceContent(be, state, signals, program, poseStack, buffers, packedLight,
                surfaceW, surfaceH, caseTint, partialTick);
        // Only controllers reach here (parts return early in render),
        // so the BE's own flag is authoritative.
        if (be.isLocked()) {
            TabletScreenRenderer.renderLockPip(poseStack, buffers, be.getTheme(),
                    state.getValue(TabletBlock.LIT), packedLight, surfaceW, surfaceH);
        }
    }
```

- [ ] **Step 3: Build gate**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/modpack/linktablet/client/render/TabletScreenRenderer.java src/main/java/com/modpack/linktablet/client/render/TabletBlockEntityRenderer.java
git commit -m "Screen lock T6: padlock pip on locked faces (flat + mounted)"
```

---

### Task 7: Docs, changelog, full build + dev-client smoke pass

**Files:**
- Modify: `CHANGELOG.md`
- Modify: `CLAUDE.md`
- Modify: `docs/NEXT_SESSION.md`

- [ ] **Step 1: CHANGELOG entry**

Under `## [Unreleased]` (create the section if absent, above the latest release heading):

```markdown
## [Unreleased]

### Added
- **Screen lock** (idea: Fluid Valve): placed tablets can be locked from the
  GUI's new padlock button — the wrench is the key. Locked glass keeps
  working (taps, holds, sliders, live faces), but every configuration
  path — editing signals, themes, apps, rotation, painting, channels,
  probes, gauges — needs a wrench in hand, enforced server-side. Any
  wrench click on a locked tablet opens its GUI; sneak-pickup is blocked
  while locked (mining still drops it, and it places back unlocked).
  Locked walls show a small padlock in the glass corner. Splitting a
  locked wall leaves every piece locked; merging anything into a locked
  wall locks the whole surface.

### Changed
- Network protocol version 24 — 1.12.0 clients/servers do not pair with
  1.11.x (everyone updates together, as usual).
```

- [ ] **Step 2: CLAUDE.md gotcha**

Append to the Technical gotchas section (after the Paint bullet), one compact bullet:

```markdown
- Screen lock (1.12.0, idea: Fluid Valve): `locked` is BLOCK-ONLY
  (BE NBT `locked`, never written false, never an item component —
  re-placed tablets start unlocked). ONE server choke point:
  `ModNetworking.configAllowed(player, target)` — every config-payload
  handler's first line; use payloads (toggle/momentary/slider/timed)
  are NEVER gated. Wrench-in-either-hand is the only key (symmetric:
  SetLockPayload demands it both ways). Members MIRROR the controller's
  flag (`lockSurface` walks, scanner ORs on formation) so splits stay
  locked and merging into a locked wall can't promote an unlocked
  controller. While locked, the ENTIRE wrench map becomes "open the
  GUI" and all pickup-by-hand paths deny. Program faces still open
  their screens (viewers; config bounces) — the mural test encodes
  this. Registrar "23"→"24".
```

- [ ] **Step 3: NEXT_SESSION.md**

Update the `▶ START HERE` section: screen lock is IN CODE on `tablet-overlay` (registrar "24", pairing break open — batch any other wire work), big button cut per the 2026-08-11 re-scope, F2/dev pass owed, credit Fluid Valve at release. Keep the existing style (state + what's owed).

- [ ] **Step 4: Full build gate**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, jar at `build/libs/linktablet-<version>.jar`.

- [ ] **Step 5: Commit**

```bash
git add CHANGELOG.md CLAUDE.md docs/NEXT_SESSION.md
git commit -m "Screen lock T7: changelog (credit Fluid Valve), gotchas, session notes"
```

- [ ] **Step 6: Deliver the dev test matrix**

Present the spec's test matrix (below) to the user as the in-world pass, per house workflow — the user runs it in the dev client (`./gradlew runClient`), plus one `runServer` boot gate (house rule since 1.10.2):

- Lock via padlock (wrench in hand) → bezel tap dead (deny tick), taps/sliders still work, GUI unreachable without wrench; wrench click opens GUI; unlock restores everything.
- Padlock without a wrench in hand → deny tick client-side, still unlocked; spoofed payload path also rejected (server check).
- Wrench map while locked: content rotate, landscape flip, mounted re-aim, sneak-wrench pickup, sneak-empty-hand pickup — all blocked; all work again after unlock.
- Single-signal tablet locked → renders the NORMAL tile (big button cut), taps on it still work.
- Merged wall: lock controller → whole surface locked incl. member bezels; split while locked → every promoted fragment still locked; merge a fresh tablet into a locked wall → whole new surface locked.
- Mural protection: locked painted wall rejects strokes/clear from a wrenchless second account (silently server-side; GUI can stay open), accepts from a wrench holder.
- Follow mode keeps tracking while locked; re-aim blocked.
- Break a locked tablet by mining → item places back UNLOCKED with signals/paint intact.
- Locked face shows the padlock pip (flat, mounted, merged); pip absent when unlocked.
- Old-world load: absent NBT = unlocked; registrar "24" pairing break vs 1.11.x (expected refusal).
- `runServer` boot gate: clean start, no dist-cleaner errors.

---

## Self-Review (done at planning time)

1. **Spec coverage:** decisions→T1–T4; state+wire→T1/T2; server rule→T3; world gates→T4; UI glyph+sounds→T5; face pip→T6; split-inheritance→T1 (mirror+OR); mural protection→T3 (paint handlers gated); big button→cut (re-scope); credit→T7. No gaps found.
2. **Placeholder scan:** all code steps carry complete code; the only judgment calls are flagged as such (LauncherScreen tooltip parity, header-width collision check).
3. **Type consistency:** `isLocked()/setLocked(boolean)/lockSurface(boolean)` (T1) match all uses in T2/T3/T4/T6; `SetLockPayload(SignalTarget, boolean)` (T2) matches T5's sends; `holdingWrench` defined once server-side (T2) and once per screen client-side (T5, deliberate — the server class isn't reachable from client pre-checks any cleaner way); `renderLockPip` signature (T6) matches its call.
