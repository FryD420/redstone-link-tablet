# Making apps for the Link Tablet

Since 1.10.0 the Link Tablet is a little OS: a Home screen of apps, an
App Store, and a public API that lets **your mod add its own app**.
A registered app gets, for free:

- an **App Store row** (name, icon, description, Get/Remove),
- a **Home screen tile** players can add to any tablet,
- its **screen** opening from tiles, from taps on placed tablets, and
  from the pinned overlay,
- optionally a **pinned HUD overlay** pane and a **custom face** drawn
  on placed tablets (walls, merged multi-tablet displays, and swivel
  mounts included).

Everything below compiles against `com.modpack.linktablet.api` — the
only package with a stability guarantee (see [Stability](#stability)).

## Setup

Add the mod as a compile dependency. The easiest source is the
Modrinth maven:

```gradle
repositories {
    maven { url = "https://api.modrinth.com/maven" }
}
dependencies {
    implementation "maven.modrinth:redstone-link-tablet:1.10.0"
}
```

And declare the dependency in your `neoforge.mods.toml`:

```toml
[[dependencies.yourmodid]]
modId = "linktablet"
type = "required"
versionRange = "[1.10.0,)"
ordering = "NONE"
side = "BOTH"
```

**Your addon must be installed on both server and client.** Programs
persist on tablets server-side; a server that doesn't know your app
quietly drops it from rosters (see [Degradation](#degradation)).

## Step 1 — register the program (common, both sides)

Implement `TabletProgram` and register it during
`RegisterTabletProgramsEvent`, which Link Tablet fires **on your mod's
event bus** during common setup:

```java
public static final TabletProgram DICE = new TabletProgram() {
    @Override public String key() { return "yourmodid:dice"; }
    @Override public Component displayName() {
        return Component.translatable("program.yourmodid.dice");
    }
    @Override public Component storeDescription() {
        return Component.translatable("program.yourmodid.dice.desc");
    }
    @Override public int chipColor() { return 0xFFF5F1E8; }
    @Override public ResourceLocation iconItem() {
        return ResourceLocation.parse("minecraft:bone_block"); // or null
    }
};

// in your mod's constructor:
modEventBus.addListener((RegisterTabletProgramsEvent e) -> e.register(DICE));
```

Rules for `key()`:

- It must be a **namespaced id** (`"yourmodid:name"`); the
  `linktablet` namespace is reserved and registration throws on it,
  on malformed ids, and on duplicates.
- The key is your app's **permanent identity** — tablets store it in
  rosters, kiosk navigation, overlay pins, and the resume preference.
  Renaming it orphans every tablet that stored it. Pick once.

`chipColor` paints the launcher tile chip; `iconItem` (nullable) draws
an item on it. `displayName`/`storeDescription` feed the tile label
and the App Store row.

## Step 2 — register the client half (client only)

Implement `TabletProgramClient` and register it during
`RegisterTabletProgramClientsEvent` (fired on your mod bus during
client setup, after all programs exist):

```java
// client-only registration; guard the listener however you guard
// client code (e.g. only add it when FMLEnvironment.dist.isClient())
modEventBus.addListener((RegisterTabletProgramClientsEvent e) ->
        e.register(DICE.key(), new TabletProgramClient() {
            @Override
            public Screen createScreen(ProgramHost host) {
                return new DiceScreen(host);
            }

            @Override // optional — omit for "can't be pinned"
            public OverlayContent createOverlay(ProgramHost host) {
                return new DiceOverlay();
            }

            @Override // optional — omit for the default label face
            public TabletFacePainter facePainter() {
                return MyMod::paintDiceFace;
            }
        }));
```

A program without a client still shelves in the store but its tile
opens nothing — always register both halves.

### The screen

`createScreen` returns a plain vanilla `Screen`. The `ProgramHost`
you're handed is the tablet it runs on:

- `host.theme()` — the tablet's `ScreenTheme` (public color fields);
  draw with it and your app matches the player's chosen skin.
- `host.tabletName()` — the tablet's (anvil) name for titles.
- `host.goHome()` — call this from your screen's `onClose()` so ESC
  returns to the launcher like the built-in apps (recommended).
- `host.isPinned()` / `host.togglePin()` — offer a pin button if you
  provide overlay content.

The host stays live — read through it per frame rather than caching.

### The pinned overlay (optional)

`OverlayContent` is the body pane of the floating mini-tablet window.
The window owns all chrome (frame, title, drag, close, scroll); you
draw rows and handle clicks in the area it gives you:

- `height(rowWidth)` — your unclipped content height; the window
  sizes and scrolls by it.
- `render(...)` — `top` is already scroll-shifted; use
  `clipTop`/`clipBottom` only to skip fully hidden rows.
- `mouseClicked(...)` — only called while the tablet is reachable.

Right-clicking the overlay opens your full screen automatically.

### The kiosk face (optional)

`facePainter()` returns a `TabletFacePainter` that draws your app on
PLACED tablets every frame — a wall clock, a live readout, whatever.
You paint through a `TabletFaceContext`:

- Coordinates are **glass texels**: `(0,0)` top-left,
  `width() × height()` the extent. A standalone tablet's glass is
  10×12; merged walls and content rotation change it — always lay
  out relative to the reported size. One block face is 16 texels.
- `fill(u, v, w, h, argb)`, `item(stack, centerU, centerV, size)`,
  `text(str, u, top, height, argb, centered, outline)` — calls are
  **buffered** and flushed in the renderer's mandatory pass order, so
  paint in whatever order reads best; you cannot corrupt the render
  batch from here. Within each kind, later calls draw on top.
- `theme()`, `backlit()`, `partialTick()` for style and animation;
  `blockEntity()` if you want per-tablet face state — hang your own
  NeoForge data attachments on it.

Tapping the glass on your app's face opens your screen; tapping the
bezel ring goes Home. You don't implement either — it's OS behavior.

## Degradation

Stored keys degrade gracefully when your addon is missing (removed,
or never installed server-side):

- Roster entries with unknown keys are silently dropped — they come
  back when the addon returns.
- A placed tablet showing an unknown program falls back to the
  launcher.
- An overlay pin for an unknown program falls back to the signals
  pane.

Nothing crashes and no data is rewritten, so "addon uninstalled for a
session" is safe.

## Stability

`com.modpack.linktablet.api` is **append-only**: interfaces only ever
grow (new methods arrive with default implementations); existing
signatures never change or vanish. `LinkTabletApi.API_VERSION` (an
`int`, currently `1`) bumps once per release that grows the surface —
assert a minimum at runtime if you depend on later additions.
Anything outside `api/` is internal and may change without notice —
if you need something that isn't exposed, open an issue instead of
reaching in.

## Reference example

The repo carries a complete worked example — a Dice app with screen,
overlay, and kiosk face, written against only the API:
[`src/main/java/com/modpack/linktablet/example/`](../src/main/java/com/modpack/linktablet/example/).
It's excluded from the shipped jar; in a dev checkout it registers
when launched with `-Dlinktablet.example=true`.

Questions, requests for more API surface, or a cool app to show off:
[GitHub issues](https://github.com/FryD420/redstone-link-tablet/issues)
are open.
