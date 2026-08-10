# Twitch Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Twitch Chat app (1.11.0 beta.2): read-only live chat of a selected channel on the held GUI, the overlay pin, and placed-tablet faces.

**Architecture:** One anonymous TLS connection to Twitch's public chat relay, owned by a client-only service (`TwitchChatService`, the ClockService precedent) that ref-counts watched channels per surface and closes the socket when nothing displays chat. Channel selection is two-layer: a client pref for personal views, a `twitch_channel` component/BE tag for placed tablets (synced via one new payload, registrar "21"→"22"). Spec: `docs/superpowers/specs/2026-08-08-twitch-chat-design.md`.

**Tech Stack:** NeoForge 1.21.1, Java 21 (JDK SSLSocket — no new dependencies). No unit-test infra — each task's gate is `./gradlew build` green; live-chat behavior is the user's dev pass (a real Twitch channel).

## Global Constraints

- **Registrar "21" → "22"** — still inside the 1.11.0 pairing break; each wire growth gets its own fence.
- Per-task commits on `tablet-overlay`, push allowed (user-established this cycle); commit trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- READ-ONLY: the service never sends PRIVMSG; anonymous guest nick only; no tokens, no accounts, nothing stored beyond the channel name.
- No socket while no surface displays chat; at most one INFO log per connection state change — never per-message or per-retry spam.
- Channel charset `[a-zA-Z0-9_]{1,25}`, normalized lowercase; `twitch_channel` component/NBT never written empty (theme idiom).
- Never render from the socket thread: the reader thread only enqueues; the client tick drains.
- Three-pass rule on the kiosk face (fills then text; no items).
- Mod version: bump to `1.11.0-beta.2` only in the final task.

---

### Task 1: Program.TWITCH + lang

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/Program.java` (after `MONITOR(26, ...)`)
- Modify: `src/main/resources/assets/linktablet/lang/en_us.json`

**Interfaces:**
- Produces: `Program.TWITCH` (id 27, key `"twitch"`) — every later task dispatches on it.

- [ ] **Step 1:** Add after `MONITOR(...)` (change its `;` to `,`):

```java
    /** Twitch Chat (1.11.0): read-only live chat of a selected channel
     * — anonymous, client-side only, no accounts. */
    TWITCH(27, "twitch", 0xFF9146FF, "minecraft:amethyst_shard");
```

- [ ] **Step 2:** Lang keys beside the other program entries / gui blocks:

```json
"program.linktablet.twitch": "Twitch Chat",
"program.linktablet.twitch.desc": "Live chat from a Twitch channel — on your tablet, your HUD, or a wall screen.",
"gui.linktablet.twitch.channel": "Channel",
"gui.linktablet.twitch.channel.hint": "Type a Twitch channel name",
"gui.linktablet.twitch.connecting": "connecting…",
"gui.linktablet.twitch.offline": "offline — retrying",
"gui.linktablet.twitch.no_messages": "No messages yet",
"gui.linktablet.twitch.no_channel": "No channel set"
```

- [ ] **Step 3:** `./gradlew build` green (store row/label face/launcher fallback come free). **Step 4:** Commit `1.11.0-dev: Program.TWITCH + Twitch Chat lang keys`.

### Task 2: TwitchChatService

**Files:**
- Create: `src/main/java/com/modpack/linktablet/client/TwitchChatService.java`

**Interfaces:**
- Produces (all static, client-thread unless noted):
  - `record ChatMessage(String user, int color, String text)`
  - `enum Status { IDLE, CONNECTING, LIVE, OFFLINE }`
  - `acquire(String channel)` / `release(String channel)` — balanced pairs (screen/overlay)
  - `touchFace(String channel)` — per-frame BER presence; expires after 100 client ticks untouched
  - `List<ChatMessage> messages(String channel)` — snapshot, oldest→newest, ≤100
  - `Status status(String channel)`
  - `static boolean validChannel(String name)` — `[a-zA-Z0-9_]{1,25}` after lowercasing; Task 3's server handler reuses the same regex (copied, the tabletDistSqr precedent).

- [ ] **Step 1:** Write the service:

```java
package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

/**
 * The Twitch Chat app's engine (1.11.0): ONE anonymous read-only
 * connection to Twitch's public chat relay, shared by every surface
 * (screen, overlay pin, kiosk faces). Channels are ref-counted —
 * screens acquire/release, faces "touch" per frame and expire — and
 * the socket exists ONLY while at least one channel is watched. The
 * reader thread never touches game state: it enqueues, the client
 * tick drains (the renderer-thread rule). Read-only forever: this
 * class must never grow a PRIVMSG writer.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class TwitchChatService {

    public record ChatMessage(String user, int color, String text) {}

    public enum Status { IDLE, CONNECTING, LIVE, OFFLINE }

    private static final Logger LOG = LoggerFactory.getLogger("linktablet-twitch");
    private static final Pattern CHANNEL = Pattern.compile("[a-z0-9_]{1,25}");
    private static final int BUFFER_SIZE = 100;
    /** Ticks a face-touched channel stays wanted without a re-touch. */
    private static final int FACE_EXPIRE_TICKS = 100;
    private static final long BACKOFF_MIN_MS = 2_000, BACKOFF_MAX_MS = 60_000;

    // ---- client-thread state ----
    private static final Map<String, Integer> REFS = new HashMap<>();
    private static final Map<String, Long> FACE_SEEN = new HashMap<>();
    private static final Map<String, ArrayDeque<ChatMessage>> BUFFERS = new HashMap<>();
    private static final Set<String> JOINED = new HashSet<>();
    private static long clientTicks = 0;
    private static volatile Status status = Status.IDLE;

    // ---- reader-thread handoff ----
    private record Incoming(String channel, ChatMessage message) {}
    private static final ConcurrentLinkedQueue<Incoming> QUEUE = new ConcurrentLinkedQueue<>();

    // ---- socket (owned by the worker thread; client thread only flags) ----
    private static volatile Worker worker = null;

    public static boolean validChannel(String name) {
        return name != null && CHANNEL.matcher(name.toLowerCase(Locale.ROOT)).matches();
    }

    public static void acquire(String channel) {
        String c = normalize(channel);
        if (c == null) return;
        REFS.merge(c, 1, Integer::sum);
    }

    public static void release(String channel) {
        String c = normalize(channel);
        if (c == null) return;
        REFS.computeIfPresent(c, (k, v) -> v <= 1 ? null : v - 1);
    }

    /** Kiosk faces call this every rendered frame; expiry parts the
     * channel when the face leaves the screen (chunk culling). */
    public static void touchFace(String channel) {
        String c = normalize(channel);
        if (c == null) return;
        FACE_SEEN.put(c, clientTicks);
    }

    public static List<ChatMessage> messages(String channel) {
        String c = normalize(channel);
        ArrayDeque<ChatMessage> buffer = c == null ? null : BUFFERS.get(c);
        return buffer == null ? List.of() : new ArrayList<>(buffer);
    }

    public static Status status(String channel) {
        String c = normalize(channel);
        if (c == null || !wanted().contains(c)) return Status.IDLE;
        return status;
    }

    private static String normalize(String channel) {
        if (channel == null) return null;
        String c = channel.toLowerCase(Locale.ROOT);
        return CHANNEL.matcher(c).matches() ? c : null;
    }

    private static Set<String> wanted() {
        Set<String> wanted = new HashSet<>(REFS.keySet());
        FACE_SEEN.forEach((c, seen) -> {
            if (clientTicks - seen <= FACE_EXPIRE_TICKS) wanted.add(c);
        });
        return wanted;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        clientTicks++;
        FACE_SEEN.values().removeIf(seen -> clientTicks - seen > FACE_EXPIRE_TICKS);

        // Drain the reader thread's queue into the ring buffers
        Incoming in;
        while ((in = QUEUE.poll()) != null) {
            ArrayDeque<ChatMessage> buffer =
                    BUFFERS.computeIfAbsent(in.channel(), c -> new ArrayDeque<>(BUFFER_SIZE));
            if (buffer.size() >= BUFFER_SIZE) buffer.pollFirst();
            buffer.addLast(in.message());
        }

        // Reconcile the socket with what's wanted
        Set<String> wanted = wanted();
        Worker w = worker;
        if (wanted.isEmpty()) {
            if (w != null) {
                w.shutdown();
                worker = null;
                JOINED.clear();
                status = Status.IDLE;
            }
            return;
        }
        if (w == null || !w.isAlive()) {
            JOINED.clear();
            worker = new Worker();
            worker.start();
        }
        // JOIN/PART deltas (queued to the worker; it flushes when live)
        for (String c : wanted) {
            if (JOINED.add(c)) worker.send("JOIN #" + c);
        }
        JOINED.removeIf(c -> {
            if (!wanted.contains(c)) {
                worker.send("PART #" + c);
                BUFFERS.remove(c);
                return true;
            }
            return false;
        });
    }

    /** The socket thread: connect, log in anonymously, pump lines. */
    private static final class Worker extends Thread {
        private volatile boolean stop = false;
        private final ConcurrentLinkedQueue<String> outbox = new ConcurrentLinkedQueue<>();
        private volatile BufferedWriter out;
        private long backoff = BACKOFF_MIN_MS;

        Worker() {
            super("linktablet-twitch");
            setDaemon(true);
        }

        void shutdown() {
            stop = true;
            interrupt();
        }

        void send(String line) {
            outbox.add(line);
        }

        @Override
        public void run() {
            while (!stop) {
                status = Status.CONNECTING;
                try (SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault()
                        .createSocket("irc.chat.twitch.tv", 6697)) {
                    socket.setSoTimeout(360_000); // Twitch pings ~5min; 6min = dead link
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            socket.getInputStream(), StandardCharsets.UTF_8));
                    out = new BufferedWriter(new OutputStreamWriter(
                            socket.getOutputStream(), StandardCharsets.UTF_8));
                    write("CAP REQ :twitch.tv/tags");
                    write("NICK justinfan" + (10_000 + new java.util.Random().nextInt(80_000)));
                    // Re-JOIN everything the client thread thinks is joined
                    // (reconnects) — JOINED is only read here, never written
                    for (String c : Set.copyOf(JOINED)) write("JOIN #" + c);
                    LOG.info("Connected to Twitch chat (anonymous, read-only)");
                    status = Status.LIVE;
                    backoff = BACKOFF_MIN_MS;
                    String line;
                    while (!stop && (line = in.readLine()) != null) {
                        flushOutbox();
                        handle(line);
                    }
                } catch (IOException | RuntimeException e) {
                    if (!stop) LOG.info("Twitch chat link lost ({}); retrying", e.getMessage());
                }
                out = null;
                if (stop) break;
                status = Status.OFFLINE;
                try {
                    Thread.sleep(backoff);
                } catch (InterruptedException e) {
                    // shutdown() interrupts; loop re-checks stop
                }
                backoff = Math.min(backoff * 2, BACKOFF_MAX_MS);
            }
            status = Status.IDLE;
        }

        private void flushOutbox() throws IOException {
            String line;
            while ((line = outbox.poll()) != null) write(line);
        }

        private void write(String line) throws IOException {
            BufferedWriter w = out;
            if (w == null) return;
            w.write(line);
            w.write("\r\n");
            w.flush();
        }

        private void handle(String line) throws IOException {
            if (line.startsWith("PING")) {
                write("PONG" + line.substring(4));
                return;
            }
            // @tags :nick!user@host PRIVMSG #channel :message
            String tags = "";
            String rest = line;
            if (rest.startsWith("@")) {
                int sp = rest.indexOf(' ');
                if (sp < 0) return;
                tags = rest.substring(1, sp);
                rest = rest.substring(sp + 1);
            }
            int privmsg = rest.indexOf(" PRIVMSG #");
            if (privmsg < 0 || !rest.startsWith(":")) return;
            int chanStart = privmsg + " PRIVMSG #".length();
            int colon = rest.indexOf(" :", chanStart);
            if (colon < 0) return;
            String channel = rest.substring(chanStart, colon).trim();
            String text = rest.substring(colon + 2);
            String user = tagValue(tags, "display-name");
            if (user.isEmpty()) {
                int bang = rest.indexOf('!');
                user = bang > 1 ? rest.substring(1, bang) : "?";
            }
            String colorTag = tagValue(tags, "color");
            int color = colorTag.startsWith("#")
                    ? 0xFF000000 | Integer.parseInt(colorTag.substring(1), 16)
                    : defaultColor(user);
            QUEUE.add(new Incoming(channel, new ChatMessage(user, color, text)));
        }

        private static String tagValue(String tags, String key) {
            for (String tag : tags.split(";")) {
                if (tag.startsWith(key + "=")) return tag.substring(key.length() + 1);
            }
            return "";
        }

        /** Stable per-name fallback when the chatter never set a color. */
        private static int defaultColor(String user) {
            int[] palette = {0xFFE05555, 0xFF55B4E0, 0xFF6BD675, 0xFFE0A24C,
                    0xFFB47EE0, 0xFF55D4C4, 0xFFE07EB4, 0xFFC4C455};
            return palette[Math.floorMod(user.hashCode(), palette.length)];
        }
    }

    private TwitchChatService() {
    }
}
```

- [ ] **Step 2:** Build green (no consumers yet — expected). **Step 3:** Commit `1.11.0-dev: TwitchChatService — anonymous read-only chat relay link`.

### Task 3: Channel persistence + payload + registrar "22"

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/registry/ModDataComponents.java`
- Modify: `src/main/java/com/modpack/linktablet/block/TabletBlockEntity.java`
- Modify: `src/main/java/com/modpack/linktablet/network/ModNetworking.java`
- Modify: `src/main/java/com/modpack/linktablet/client/SignalView.java`
- Modify: `src/main/java/com/modpack/linktablet/client/ClientPrefs.java`

**Interfaces:**
- Consumes: the `monitor_probe` persistence shape from this cycle (component + BE NBT + both-target payload) — mirror it exactly.
- Produces: `ModDataComponents.TWITCH_CHANNEL` (String); `TabletBlockEntity.getTwitchChannel()/setTwitchChannel(String)` ("" = unset); `SignalView.twitchChannel()` (block view reads BE, item views read component, default ""); `ModNetworking.SetTwitchChannelPayload(SignalTarget target, String channel)` id `"set_twitch_channel"`; `ClientPrefs.twitchChannel()/setTwitchChannel(String)`.

- [ ] **Step 1:** Component beside MONITOR_PROBE (`Codec.STRING` persistent, `ByteBufCodecs.STRING_UTF8` network; javadoc: "absent = unset, never written empty — the theme idiom").
- [ ] **Step 2:** BE: `private String twitchChannel = "";` + getter/setter (equals-guard, `setChanged()` + `sendBlockUpdated`, the `setMonitorProbes` shape); `saveAdditional` writes `tag.putString("twitch_channel", ...)` only when non-empty; `loadAdditional` reads with `tag.getString` default ""; `loadFromItem` reads the component default ""; `toItemStack` sets it only when non-empty.
- [ ] **Step 3:** Payload + handler in ModNetworking (the handleSetProbe both-target shape), with server-side validation copied from the service's rule:

```java
    /** Twitch channel charset — TwitchChatService.validChannel's rule,
     * copied (that class is client-only). */
    private static final java.util.regex.Pattern TWITCH_CHANNEL =
            java.util.regex.Pattern.compile("[a-z0-9_]{1,25}");

    private static void handleSetTwitchChannel(SetTwitchChannelPayload payload, IPayloadContext context) {
        Player player = context.player();
        String channel = payload.channel().toLowerCase(java.util.Locale.ROOT);
        if (!channel.isEmpty() && !TWITCH_CHANNEL.matcher(channel).matches()) return;
        if (payload.target().pos().isPresent()) {
            BlockPos pos = payload.target().pos().get();
            if (!player.level().isLoaded(pos)) return;
            if (tabletDistSqr(player, pos) > MAX_BLOCK_DISTANCE_SQ) return;
            if (player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
                TabletBlockEntity controller = be.resolveController();
                if (controller != null) controller.setTwitchChannel(channel);
            }
            return;
        }
        ItemStack stack = resolveStack(player, payload.target());
        if (!stack.isEmpty()) {
            if (channel.isEmpty()) {
                stack.remove(ModDataComponents.TWITCH_CHANNEL.get());
            } else {
                stack.set(ModDataComponents.TWITCH_CHANNEL.get(), channel);
            }
        }
    }
```

with `SetTwitchChannelPayload` (SignalTarget + `ByteBufCodecs.stringUtf8(25)`), registrar comment `// "22": 1.11.0 Twitch Chat — SetTwitchChannelPayload added (still inside the 1.11.0 break).` and fence `event.registrar("22")`, registration beside the probe payloads.
- [ ] **Step 4:** `SignalView.twitchChannel()` — default "" + three impls (the monitorProbes shape). `ClientPrefs`: `twitchChannel()`/`setTwitchChannel` mirroring `lastProgram()`/`setLastProgram` with key `"twitch.channel"`.
- [ ] **Step 5:** Build green. **Step 6:** Commit `1.11.0-dev: twitch_channel persistence — component, BE NBT, payload, registrar "22"`.

### Task 4: TwitchScreen + navigation

**Files:**
- Create: `src/main/java/com/modpack/linktablet/client/screen/TwitchScreen.java`
- Modify: `src/main/java/com/modpack/linktablet/client/ClientHooks.java` (screenFor switch, before `default`)

**Interfaces:**
- Consumes: `TwitchChatService.acquire/release/messages/status/validChannel`, `SignalView.twitchChannel()` (block channel) / `ClientPrefs.twitchChannel()` (item views), `SetTwitchChannelPayload`.
- Produces: `TwitchScreen(SignalView view)`.

- [ ] **Step 1:** Pattern MonitorScreen end to end (panel/header/pin with `Program.TWITCH`/Home/scroll/`isPauseScreen false`/`UISounds`): channel `ChromeEditBox` row at top (the StoreScreen search-box code INCLUDING the click-to-focus block — copy that mouseClicked shape verbatim), message list below (scrolling; per message: username in `ChatMessage.color`, `": "`, text wrapped via `font.split(Component.literal(text), rowWidth - indent)`, continuation lines indented). Status line under the box via `status(channel)`: CONNECTING → `gui.linktablet.twitch.connecting`, OFFLINE → `.offline`, LIVE with empty buffer → `.no_messages`, IDLE/no channel → `.no_channel`.
- [ ] **Step 2:** Channel plumbing: current channel = block views → `view.twitchChannel()`, item views → `ClientPrefs.twitchChannel()`. The box's responder (on Enter or focus-loss commit, the nameBox save idiom) validates via `TwitchChatService.validChannel` (silent deny-tick on bad input), then: block views send `SetTwitchChannelPayload(view.target(), channel)`, item views `ClientPrefs.setTwitchChannel(channel)`. Subscription: `acquire(channel)` in `init()` guarded by the MonitorScreen `subscribed` flag (resize!), `release` + flag reset in `removed()`; on channel CHANGE mid-screen, release old + acquire new.
- [ ] **Step 3:** `ClientHooks.screenFor`: `case TWITCH -> new com.modpack.linktablet.client.screen.TwitchScreen(view);`
- [ ] **Step 4:** Build green. **Step 5:** Commit `1.11.0-dev: TwitchScreen — channel box, live chat list`.

### Task 5: Overlay pin content

**Files:**
- Create: `src/main/java/com/modpack/linktablet/client/screen/TwitchOverlayContent.java`
- Modify: `src/main/java/com/modpack/linktablet/client/screen/MiniTabletWindow.java` (contentFor switch)

**Interfaces:**
- Consumes: `OverlayContent` (api/client), `TwitchChatService`, the MonitorOverlayContent lazy-acquire lifecycle (acquired flag on first render, release in `defocus()`, re-acquire guard).

- [ ] **Step 1:** Pattern MonitorOverlayContent: compact message rows (username colored + wrapped text, last ~8 messages fitting `height(rowWidth)`), channel from the bound view (block → `view.twitchChannel()`, else `ClientPrefs.twitchChannel()`); empty states reuse the Task 1 lang keys. Lazy acquire/release exactly like MonitorOverlayContent (including re-acquire when the service lost the ref via relog — track own flag; on channel change between frames release old/acquire new).
- [ ] **Step 2:** `case TWITCH -> new TwitchOverlayContent(this::view);` in contentFor.
- [ ] **Step 3:** Build green. **Step 4:** Commit `1.11.0-dev: Twitch overlay pin — HUD chat pane`.

### Task 6: Kiosk face

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/render/TabletScreenRenderer.java` (+`renderTwitchFace`)
- Modify: `src/main/java/com/modpack/linktablet/client/render/TabletBlockEntityRenderer.java` (face switch)

**Interfaces:**
- Consumes: `TwitchChatService.touchFace/messages/status`, `TabletBlockEntity.getTwitchChannel()` (synced), `renderMonitorFace`'s pass structure.

- [ ] **Step 1:** `renderTwitchFace(PoseStack, MultiBufferSource, String channel, int rotation, ScreenTheme, boolean lit, int packedLight, int surfaceW, int surfaceH, int caseTint)` — pattern renderMonitorFace: Pass 1 background fills (row striping optional, keep minimal), Pass 2 none (no items), Pass 3 text: newest messages bottom-up, username in its color + wrapped text, capped to glass rows; empty channel → centered `gui.linktablet.twitch.no_channel`, else status/no-messages lines. FIRST line of the method (client-side render path): `TwitchChatService.touchFace(channel)` when channel non-empty — the face's presence heartbeat; chunk culling stops the touches and the service parts the channel after 100 ticks.
- [ ] **Step 2:** Dispatch in `TabletBlockEntityRenderer.renderFace` before `default`:

```java
                case TWITCH -> TabletScreenRenderer.renderTwitchFace(poseStack, buffers,
                        be.getTwitchChannel(),
                        be.effectiveRotation(), be.getTheme(), state.getValue(TabletBlock.LIT),
                        packedLight, surfaceW, surfaceH, caseTint);
```

(Glass taps open the GUI via the existing generic branch — no hit-test work.)
- [ ] **Step 3:** Build green. **Step 4:** Commit `1.11.0-dev: Twitch kiosk face — chat wall`.

### Task 7: Docs + beta.2

**Files:**
- Modify: `CHANGELOG.md`, `docs/NEXT_SESSION.md`, `CLAUDE.md`, `gradle.properties`

- [ ] **Step 1:** CHANGELOG "Unreleased": Twitch Chat app bullet (read-only, anonymous, three surfaces, per-player channel + wall-tablet channel) + registrar note now "18"→"22".
- [ ] **Step 2:** NEXT_SESSION status: beta.2 contents + the spec's test matrix appended to the 1.11.0 matrix. CLAUDE.md gotcha: TwitchChatService is client-only/read-only (never grow a writer), socket exists only while a surface displays chat, faces heartbeat via touchFace, channel rule `[a-z0-9_]{1,25}` copied server-side.
- [ ] **Step 3:** `gradle.properties` → `mod_version=1.11.0-beta.2`. Full `./gradlew build`; jar `build/libs/linktablet-1.11.0-beta.2.jar`.
- [ ] **Step 4:** Commit `1.11.0-beta.2: Twitch Chat app + docs`, push (user-approved this cycle). STOP: jar delivery, tester checklist (per the standing workflow), and in-world verification are the controller's/user's steps.

---

## Self-review

**Spec coverage:** Program/store (T1); connection layer incl. one socket, ref-counts, face expiry, backoff, status, read-only, no-idle-connections (T2); two-layer channel selection + payload + registrar "22" + never-written-empty (T3); screen with click-to-focus lesson + status lines (T4); overlay (T5); face + touchFace presence + three-pass (T6); v1 limits are absences (no code needed); test matrix + beta.2 (T7). No gaps.

**Placeholders:** none — T2 carries the full service; T3 carries the handler; UI tasks name their exact pattern files and the specific mechanisms to copy.

**Type consistency:** `ChatMessage(user, color, text)` consistent T2/T4/T5/T6; `acquire/release/touchFace/messages/status/validChannel` consistent; `getTwitchChannel()/setTwitchChannel` (BE) vs `twitchChannel()` (SignalView) vs `ClientPrefs.twitchChannel()` distinct and used per their owners; payload id `set_twitch_channel` single definition.
