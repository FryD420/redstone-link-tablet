# Twitch Chat Emotes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship inline Twitch emotes (native + 7TV/BTTV/FFZ, animated) in the Twitch Chat app on all three surfaces (GUI, overlay, kiosk walls) as 1.11.0-beta.6.

**Architecture:** Native emotes come free from the IRC `emotes=` tag (code-point ranges); third-party sets are fetched anonymously per channel keyed by the `room-id` tag. A new `TwitchEmotes` (sets/matching) + `EmoteTextures` (fetch → ImageIO decode → sprite-sheet `DynamicTexture`, LRU) pair feeds ONE tokenizer (`EmoteText` — segments, wrap, GUI draw) used by every surface. Wall emotes ride `RenderType.text(sheet)` inside the existing text pass. Spec: `docs/superpowers/specs/2026-08-09-twitch-emotes-design.md`.

**Tech Stack:** NeoForge 1.21.1, Java 21. NO new dependencies: `java.net.http.HttpClient`, `javax.imageio` (GIF/PNG), Gson (ships with Minecraft). No unit-test infra — each task's gate is `./gradlew build` green; live behavior is the user's dev pass.

## Global Constraints

- **NO wire/component/NBT/registrar change** — registrar stays `"23"`; beta.6 pairs with beta.4/5. If any task seems to need one, STOP — the design is wrong.
- Everything in this plan is CLIENT-ONLY (`client/` or `tools/`); nothing may load on a dedicated server.
- Read-only model intact: anonymous CDN/API GETs only, no tokens, no accounts, nothing stored on disk.
- Caps ("cap the crazy ones"): 1x images only, max 40 decoded frames, max 256 KB source download, LRU 128 emotes with texture release on evict.
- Fail soft everywhere: provider API down → that provider's emotes stay text; emote fetch/decode fails → text name; at most ONE `LOG.info` per failure, never per-frame spam.
- ONE tokenizer: all three surfaces lay out through `EmoteText` — never fork matching/wrapping into a renderer.
- Kiosk face pass discipline: emote quads use `RenderType.text(...)` only (the font's family) — NEVER a new custom RenderType near the cached background-quad consumer; the beta.5 bleed insets apply to emote quads exactly as to text.
- Per-task commits on `tablet-overlay`, push allowed; trailer `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.
- Mod version: bump to `1.11.0-beta.6` only in the final task.

---

### Task 1: Spike — third-party GIF/PNG coverage (GATE)

The spec's mandatory first task: measure how many 7TV/BTTV/FFZ emotes
actually serve an ImageIO-decodable format (GIF/PNG) before building
anything on top.

**Files:**
- Create: `src/main/java/com/modpack/linktablet/tools/EmoteProbe.java`
- Modify: `build.gradle` (new `emoteTool` JavaExec task — copy the `nbtTool` block verbatim, main class `com.modpack.linktablet.tools.EmoteProbe`; `tools/` is already jar-excluded)

**Interfaces:**
- Produces: a console report only — no shipped code depends on this task. Its URL/JSON findings calibrate Task 3's parsers.

- [ ] **Step 1:** Write the probe (plain `main`, JDK HttpClient + Gson):

```java
package com.modpack.linktablet.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Dev-only probe (jar-excluded): how many 7TV/BTTV/FFZ emotes serve a
 * GIF/PNG that ImageIO can decode? Run:
 *   ./gradlew emoteTool --args="71092938"
 * (numeric Twitch room id; default = xqc, heavy 7TV culture).
 */
public final class EmoteProbe {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public static void main(String[] args) throws Exception {
        String roomId = args.length > 0 ? args[0] : "71092938";
        probe7tv(json("https://7tv.io/v3/emote-sets/global")
                .getAsJsonObject().getAsJsonArray("emotes"), "7TV global");
        JsonObject user = json("https://7tv.io/v3/users/twitch/" + roomId).getAsJsonObject();
        JsonObject set = user.getAsJsonObject("emote_set");
        if (set != null) probe7tv(set.getAsJsonArray("emotes"), "7TV channel " + roomId);
        probeBttv(json("https://api.betterttv.net/3/cached/emotes/global").getAsJsonArray(),
                "BTTV global");
        JsonObject bttvUser = json("https://api.betterttv.net/3/cached/users/twitch/" + roomId)
                .getAsJsonObject();
        JsonArray bttvAll = new JsonArray();
        bttvAll.addAll(bttvUser.getAsJsonArray("channelEmotes"));
        bttvAll.addAll(bttvUser.getAsJsonArray("sharedEmotes"));
        probeBttv(bttvAll, "BTTV channel");
        probeFfz(json("https://api.frankerfacez.com/v1/room/id/" + roomId)
                .getAsJsonObject().getAsJsonObject("sets"), "FFZ channel");
    }

    private static void probe7tv(JsonArray emotes, String label) {
        int total = 0, gifOrPng = 0, decoded = 0, tried = 0;
        for (JsonElement el : emotes) {
            total++;
            JsonObject host = el.getAsJsonObject().getAsJsonObject("data").getAsJsonObject("host");
            String file = pick7tvFile(host.getAsJsonArray("files"));
            if (file == null) continue;
            gifOrPng++;
            if (tried < 5 && decode("https:" + host.get("url").getAsString() + "/" + file)) decoded++;
            if (tried < 5) tried++;
        }
        System.out.printf("%s: %d emotes, %d with 1x.gif/1x.png (%d%%), sample decode %d/%d%n",
                label, total, gifOrPng, total == 0 ? 0 : 100 * gifOrPng / total, decoded, tried);
    }

    static String pick7tvFile(JsonArray files) {
        String png = null;
        for (JsonElement f : files) {
            String name = f.getAsJsonObject().get("name").getAsString();
            if (name.equals("1x.gif")) return name;
            if (name.equals("1x.png")) png = name;
        }
        return png;
    }

    private static void probeBttv(JsonArray emotes, String label) {
        int total = 0, ok = 0;
        for (JsonElement el : emotes) {
            total++;
            String type = el.getAsJsonObject().get("imageType").getAsString();
            if (type.equals("gif") || type.equals("png")) ok++;
        }
        System.out.printf("%s: %d emotes, %d gif/png (%d%%)%n",
                label, total, ok, total == 0 ? 0 : 100 * ok / total);
    }

    private static void probeFfz(JsonObject sets, String label) {
        int total = 0, animated = 0, decoded = 0, tried = 0;
        for (String key : sets.keySet()) {
            for (JsonElement el : sets.getAsJsonObject(key).getAsJsonArray("emoticons")) {
                total++;
                JsonObject e = el.getAsJsonObject();
                boolean anim = e.has("animated") && !e.get("animated").isJsonNull();
                if (anim) {
                    animated++;
                    if (tried < 3) {
                        tried++;
                        if (decode(e.getAsJsonObject("animated").get("1").getAsString() + ".gif"))
                            decoded++;
                    }
                }
            }
        }
        System.out.printf("%s: %d emotes (%d animated), animated .gif sample decode %d/%d%n",
                label, total, animated, decoded, tried);
    }

    private static boolean decode(String url) {
        try {
            HttpResponse<byte[]> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            boolean ok = r.statusCode() == 200
                    && ImageIO.read(new ByteArrayInputStream(r.body())) != null;
            System.out.println("  " + url + " -> " + r.statusCode() + (ok ? " decodable" : " NOT decodable"));
            return ok;
        } catch (Exception e) {
            System.out.println("  " + url + " -> " + e.getMessage());
            return false;
        }
    }

    private EmoteProbe() {}
}
```

- [ ] **Step 2:** `build.gradle`: duplicate the `nbtTool` JavaExec task as `emoteTool` pointing at `EmoteProbe`.
- [ ] **Step 3:** Run `./gradlew emoteTool` (and once more with a second room id the user actually watches, if known). Record the numbers in the commit message.
- [ ] **Step 4: GATE.** If 7TV GIF/PNG coverage is below ~80% (globals + channel combined), STOP THE PLAN and report to the user — the WebP decision (extra decoder vs partial 7TV) is theirs. If any JSON shape differs from Task 3's parser sketches, adjust Task 3 before executing it. Otherwise proceed.
- [ ] **Step 5:** Commit `1.11.0-dev: emote format probe tool (spike) — <coverage numbers>`.

### Task 2: Native emote spans + room-id in TwitchChatService

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/TwitchChatService.java`

**Interfaces:**
- Produces:
  - `record EmoteSpan(int from, int to, String id)` — INCLUSIVE Unicode CODE POINT indices into the message text, sorted by `from`.
  - `ChatMessage` becomes `record ChatMessage(String user, int color, String text, List<EmoteSpan> emotes)` (never null, `List.of()` when none).
  - `static String roomId(String channel)` — numeric Twitch channel id, `""` until the first message arrives.

- [ ] **Step 1:** Add beside `ChatMessage`:

```java
    /** One native emote occurrence: INCLUSIVE Unicode CODE-POINT range
     * into the message text (Twitch counts code points, not chars —
     * astral emoji before an emote shift char indices, not these). */
    public record EmoteSpan(int from, int to, String id) {}
```

and grow the record: `public record ChatMessage(String user, int color, String text, List<EmoteSpan> emotes) {}`.

- [ ] **Step 2:** Room-id handoff (worker writes, client reads — concurrent map, the WANTED_SHARED precedent):

```java
    /** channel → numeric Twitch room id, learned from message tags.
     * Worker thread writes; client thread (TwitchEmotes) reads. */
    private static final Map<String, String> ROOM_IDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static String roomId(String channel) {
        String c = normalize(channel);
        return c == null ? "" : ROOM_IDS.getOrDefault(c, "");
    }
```

Add `ROOM_IDS.clear();` to `onLoggingOut`.

- [ ] **Step 3:** In `Worker.handle`, after the color parse and before `QUEUE.add`:

```java
            String roomId = tagValue(tags, "room-id");
            if (!roomId.isEmpty()) ROOM_IDS.putIfAbsent(channel, roomId);
            QUEUE.add(new Incoming(channel,
                    new ChatMessage(user, color, text, parseEmotes(tagValue(tags, "emotes")))));
```

with, beside `tagValue`:

```java
        /** "25:0-4,12-16/1902:6-10" → sorted spans; malformed pieces dropped. */
        private static List<EmoteSpan> parseEmotes(String tag) {
            if (tag.isEmpty()) return List.of();
            List<EmoteSpan> spans = new ArrayList<>();
            for (String group : tag.split("/")) {
                int colon = group.indexOf(':');
                if (colon <= 0) continue;
                String id = group.substring(0, colon);
                for (String range : group.substring(colon + 1).split(",")) {
                    int dash = range.indexOf('-');
                    if (dash <= 0) continue;
                    try {
                        int from = Integer.parseInt(range.substring(0, dash));
                        int to = Integer.parseInt(range.substring(dash + 1));
                        if (from >= 0 && to >= from) spans.add(new EmoteSpan(from, to, id));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            spans.sort(java.util.Comparator.comparingInt(EmoteSpan::from));
            return spans;
        }
```

- [ ] **Step 4:** `./gradlew build` — the record change breaks NOTHING else (the only constructor call is in `handle`; readers use accessors). **Step 5:** Commit `1.11.0-dev: native emote spans + room-id from IRC tags`.

### Task 3: TwitchEmotes — third-party set fetching + matching

**Files:**
- Create: `src/main/java/com/modpack/linktablet/client/TwitchEmotes.java`
- Modify: `src/main/java/com/modpack/linktablet/client/TwitchChatService.java` (two hook calls + logout)

**Interfaces:**
- Consumes: `TwitchChatService.roomId(channel)` (Task 2).
- Produces (all static, client thread):
  - `record Emote(String cacheKey, String name, String url)` — `cacheKey` = `"twitch:<id>"` / `"7tv:<id>"` / `"bttv:<id>"` / `"ffz:<id>"`.
  - `Emote resolve(String word, String channel)` — third-party match (channel set, then globals), null if none.
  - `Emote nativeEmote(String id, String name)` — CDN URL template, no set needed.
  - `int generation(String channel)` — bumps when a set lands; `EmoteText`'s memo key.
  - `void onChannelJoined(String channel)` / `onChannelParted(String channel)` — called by `TwitchChatService`.

- [ ] **Step 1:** Write the service:

```java
package com.modpack.linktablet.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.modpack.linktablet.LinkTabletMod;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Third-party emote sets (1.11.0 emotes): 7TV/BTTV/FFZ name→emote maps
 * per channel plus merged globals, fetched anonymously (public cached
 * endpoints, no tokens — the read-only model). Fetches are async but
 * ALL map state is client-thread-only: responses hop through
 * Minecraft.execute. Each provider fails independently and soft — a
 * dead API means that provider's emotes stay text, one INFO, no retry
 * until re-join. Channel sets need the numeric room id, which arrives
 * with the channel's first message — the tick loop polls for it.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class TwitchEmotes {

    public record Emote(String cacheKey, String name, String url) {}

    private static final Logger LOG = LoggerFactory.getLogger("linktablet-twitch");
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    // ---- client-thread state ("" = the merged global sets) ----
    private static final Map<String, Map<String, Emote>> SETS = new HashMap<>();
    private static final Map<String, Integer> GENERATION = new HashMap<>();
    private static final Set<String> FETCHED = new HashSet<>();
    private static final Set<String> JOINED = new HashSet<>();

    public static Emote resolve(String word, String channel) {
        Map<String, Emote> set = SETS.get(channel);
        Emote e = set == null ? null : set.get(word);
        if (e != null) return e;
        Map<String, Emote> global = SETS.get("");
        return global == null ? null : global.get(word);
    }

    public static Emote nativeEmote(String id, String name) {
        return new Emote("twitch:" + id, name,
                "https://static-cdn.jtvnw.net/emoticons/v2/" + id + "/default/dark/1.0");
    }

    /** Channel generation + global generation: a landing GLOBAL set
     * invalidates every channel's memoized tokenization too. */
    public static int generation(String channel) {
        return GENERATION.getOrDefault(channel, 0) + GENERATION.getOrDefault("", 0);
    }

    public static void onChannelJoined(String channel) {
        JOINED.add(channel);
        if (FETCHED.add("")) fetchGlobals();
    }

    public static void onChannelParted(String channel) {
        JOINED.remove(channel);
        FETCHED.remove(channel);
        SETS.remove(channel);
        GENERATION.merge(channel, 1, Integer::sum);
    }

    /** Channel sets wait on the room id (first message); poll here. */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        for (String c : JOINED) {
            if (FETCHED.contains(c)) continue;
            String roomId = TwitchChatService.roomId(c);
            if (roomId.isEmpty()) continue;
            FETCHED.add(c);
            fetchChannel(c, roomId);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        SETS.clear();
        GENERATION.clear();
        FETCHED.clear();
        JOINED.clear();
    }

    // ------------------------------------------------------------------
    // Fetching (async; results applied on the client thread)
    // ------------------------------------------------------------------

    private static void fetchGlobals() {
        get("https://7tv.io/v3/emote-sets/global", "7TV global", json ->
                apply("", parse7tv(json.getAsJsonObject().getAsJsonArray("emotes"))));
        get("https://api.betterttv.net/3/cached/emotes/global", "BTTV global", json ->
                apply("", parseBttv(json.getAsJsonArray())));
        get("https://api.frankerfacez.com/v1/set/global", "FFZ global", json ->
                apply("", parseFfz(json.getAsJsonObject().getAsJsonObject("sets"))));
    }

    private static void fetchChannel(String channel, String roomId) {
        get("https://7tv.io/v3/users/twitch/" + roomId, "7TV/" + channel, json -> {
            JsonObject set = json.getAsJsonObject().getAsJsonObject("emote_set");
            if (set != null) apply(channel, parse7tv(set.getAsJsonArray("emotes")));
        });
        get("https://api.betterttv.net/3/cached/users/twitch/" + roomId, "BTTV/" + channel,
                json -> {
                    JsonObject o = json.getAsJsonObject();
                    Map<String, Emote> m = parseBttv(o.getAsJsonArray("channelEmotes"));
                    m.putAll(parseBttv(o.getAsJsonArray("sharedEmotes")));
                    apply(channel, m);
                });
        get("https://api.frankerfacez.com/v1/room/id/" + roomId, "FFZ/" + channel, json ->
                apply(channel, parseFfz(json.getAsJsonObject().getAsJsonObject("sets"))));
    }

    private static void get(String url, String label, Consumer<JsonElement> onJson) {
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(15)).build(),
                HttpResponse.BodyHandlers.ofString())
                .thenAccept(resp -> {
                    if (resp.statusCode() != 200) {
                        LOG.info("Emote set {} unavailable (http {})", label, resp.statusCode());
                        return;
                    }
                    try {
                        JsonElement json = JsonParser.parseString(resp.body());
                        Minecraft.getInstance().execute(() -> onJson.accept(json));
                    } catch (RuntimeException e) {
                        LOG.info("Emote set {} unparseable ({})", label, e.getMessage());
                    }
                })
                .exceptionally(e -> {
                    LOG.info("Emote set {} unavailable ({})", label, e.getMessage());
                    return null;
                });
    }

    private static void apply(String channel, Map<String, Emote> emotes) {
        if (emotes.isEmpty()) return;
        if (!channel.isEmpty() && !JOINED.contains(channel)) return; // parted while in flight
        SETS.computeIfAbsent(channel, c -> new HashMap<>()).putAll(emotes);
        GENERATION.merge(channel, 1, Integer::sum);
    }

    // Parsers fail soft per emote: any missing member skips that entry.
    // Shapes verified by the Task 1 probe — adjust here if it disagreed.

    private static Map<String, Emote> parse7tv(JsonArray emotes) {
        Map<String, Emote> out = new HashMap<>();
        if (emotes == null) return out;
        for (JsonElement el : emotes) {
            try {
                JsonObject e = el.getAsJsonObject();
                JsonObject host = e.getAsJsonObject("data").getAsJsonObject("host");
                String file = com.modpack.linktablet.tools.EmoteProbe.pick7tvFile(
                        host.getAsJsonArray("files"));
                if (file == null) continue; // WebP/AVIF-only: stays text (spec risk #1)
                String name = e.get("name").getAsString();
                out.put(name, new Emote("7tv:" + e.get("id").getAsString(), name,
                        "https:" + host.get("url").getAsString() + "/" + file));
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    private static Map<String, Emote> parseBttv(JsonArray emotes) {
        Map<String, Emote> out = new HashMap<>();
        if (emotes == null) return out;
        for (JsonElement el : emotes) {
            try {
                JsonObject e = el.getAsJsonObject();
                String type = e.get("imageType").getAsString();
                if (!type.equals("gif") && !type.equals("png")) continue;
                String name = e.get("code").getAsString();
                String id = e.get("id").getAsString();
                out.put(name, new Emote("bttv:" + id, name,
                        "https://cdn.betterttv.net/emote/" + id + "/1x." + type));
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    private static Map<String, Emote> parseFfz(JsonObject sets) {
        Map<String, Emote> out = new HashMap<>();
        if (sets == null) return out;
        for (String key : sets.keySet()) {
            JsonArray emoticons = sets.getAsJsonObject(key).getAsJsonArray("emoticons");
            if (emoticons == null) continue;
            for (JsonElement el : emoticons) {
                try {
                    JsonObject e = el.getAsJsonObject();
                    String name = e.get("name").getAsString();
                    String id = e.get("id").getAsString();
                    String url;
                    if (e.has("animated") && e.get("animated").isJsonObject()) {
                        // FFZ serves WebP at the bare animated URL but GIF
                        // with an explicit .gif suffix (probe-verified)
                        url = e.getAsJsonObject("animated").get("1").getAsString() + ".gif";
                    } else {
                        url = e.getAsJsonObject("urls").get("1").getAsString();
                    }
                    if (url.startsWith("//")) url = "https:" + url;
                    out.put(name, new Emote("ffz:" + id, name, url));
                } catch (RuntimeException ignored) {
                }
            }
        }
        return out;
    }

    private TwitchEmotes() {
    }
}
```

NOTE: `parse7tv` reuses `EmoteProbe.pick7tvFile` — move that helper INTO `TwitchEmotes` as a private static and have the probe call it, or duplicate it privately here and drop the probe import (tools/ is jar-excluded, so shipped code must NOT import it — **duplicate it privately here**; add the comment `// pick7tvFile: the EmoteProbe rule, copied — tools/ never ships`).

- [ ] **Step 2:** Hooks in `TwitchChatService.onClientTick` — inside the JOIN loop after `worker.send("JOIN #" + c)` add `TwitchEmotes.onChannelJoined(c);`; inside the PART removal branch after `BUFFERS.remove(c)` add `TwitchEmotes.onChannelParted(c);`. (Logout: `TwitchEmotes` has its own subscriber — no service change needed.)
- [ ] **Step 3:** `./gradlew build` green. **Step 4:** Commit `1.11.0-dev: TwitchEmotes — 7TV/BTTV/FFZ set fetching + matching`.

### Task 4: EmoteTextures — fetch, decode, sprite sheets, LRU

**Files:**
- Create: `src/main/java/com/modpack/linktablet/client/EmoteTextures.java`

**Interfaces:**
- Consumes: `TwitchEmotes.Emote` (cacheKey/name/url).
- Produces (all static, client thread unless noted):
  - `record Sprite(ResourceLocation texture, int frameCount, int frameW, int frameH, int[] delaysMs, int totalMs)`
  - `Sprite get(TwitchEmotes.Emote emote)` — null while unloaded/FAILED; first call kicks off the async load.
  - `int frameAt(Sprite sprite, long nowMs)` — the ONE animation clock (all surfaces call it with `Util.getMillis()` so frames agree everywhere).

- [ ] **Step 1:** Write the class:

```java
package com.modpack.linktablet.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.modpack.linktablet.LinkTabletMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Emote image cache (1.11.0 emotes): url → one sprite-sheet
 * DynamicTexture (frames stacked vertically; a static PNG is a 1-frame
 * sheet). Fetch + decode run on a 2-thread daemon pool; ALL cache maps
 * and every texture register/release happen on the client thread via
 * Minecraft.execute. Animation is UV selection only — zero texture
 * uploads after load. Caps (spec: "cap the crazy ones"): 1x source,
 * 256 KB download, 40 frames, LRU 128 with release-on-evict.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class EmoteTextures {

    public record Sprite(ResourceLocation texture, int frameCount, int frameW, int frameH,
                         int[] delaysMs, int totalMs) {}

    private static final Logger LOG = LoggerFactory.getLogger("linktablet-twitch");
    private static final int MAX_FRAMES = 40;
    private static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_CACHED = 128;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "linktablet-emote-fetch");
        t.setDaemon(true);
        return t;
    });

    // ---- client-thread state ----
    /** Access-ordered LRU; eviction releases the GPU texture. */
    private static final LinkedHashMap<String, Sprite> READY =
            new LinkedHashMap<>(64, 0.75f, true);
    private static final Set<String> LOADING = new HashSet<>();
    private static final Set<String> FAILED = new HashSet<>();

    public static Sprite get(TwitchEmotes.Emote emote) {
        Sprite s = READY.get(emote.cacheKey());
        if (s != null) return s;
        if (FAILED.contains(emote.cacheKey())) return null;
        if (LOADING.add(emote.cacheKey())) POOL.submit(() -> load(emote));
        return null;
    }

    public static int frameAt(Sprite s, long nowMs) {
        if (s.frameCount() <= 1) return 0;
        int t = (int) (nowMs % s.totalMs());
        for (int i = 0; i < s.frameCount(); i++) {
            t -= s.delaysMs()[i];
            if (t < 0) return i;
        }
        return s.frameCount() - 1;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        for (Sprite s : READY.values()) {
            Minecraft.getInstance().getTextureManager().release(s.texture());
        }
        READY.clear();
        LOADING.clear();
        FAILED.clear();
    }

    // ------------------------------------------------------------------
    // Worker side (POOL threads) — never touches the maps directly
    // ------------------------------------------------------------------

    private static void load(TwitchEmotes.Emote emote) {
        try {
            HttpResponse<byte[]> resp = HTTP.send(
                    HttpRequest.newBuilder(URI.create(emote.url()))
                            .timeout(Duration.ofSeconds(15)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) throw new IOException("http " + resp.statusCode());
            if (resp.body().length > MAX_BYTES) throw new IOException(resp.body().length + " bytes");
            Decoded d = decode(resp.body());
            NativeImage sheet = toSheet(d);
            Minecraft.getInstance().execute(() -> install(emote.cacheKey(), sheet, d.delaysMs()));
        } catch (Exception e) {
            LOG.info("Emote {} unavailable ({})", emote.name(), e.getMessage());
            Minecraft.getInstance().execute(() -> {
                LOADING.remove(emote.cacheKey());
                FAILED.add(emote.cacheKey());
            });
        }
    }

    private record Decoded(List<BufferedImage> frames, int[] delaysMs) {}

    private static Decoded decode(byte[] data) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("no decoder (WebP?)");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, false, false);
                if (!"gif".equalsIgnoreCase(reader.getFormatName())) {
                    return new Decoded(List.of(toArgb(reader.read(0))), new int[]{0});
                }
                return decodeGif(reader);
            } finally {
                reader.dispose();
            }
        }
    }

    /** GIF frames arrive as partial patches with per-frame disposal
     * rules; composite onto a logical-screen canvas or animated emotes
     * ghost/flicker (spec risk #2). */
    private static Decoded decodeGif(ImageReader reader) throws IOException {
        IIOMetadataNode stream = (IIOMetadataNode) reader.getStreamMetadata()
                .getAsTree("javax_imageio_gif_stream_1.0");
        IIOMetadataNode lsd = child(stream, "LogicalScreenDescriptor");
        int w = Integer.parseInt(lsd.getAttribute("logicalScreenWidth"));
        int h = Integer.parseInt(lsd.getAttribute("logicalScreenHeight"));
        if (w <= 0 || h <= 0 || w > 128 || h > 128) throw new IOException(w + "x" + h);
        int count = Math.min(reader.getNumImages(true), MAX_FRAMES);
        BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        List<BufferedImage> frames = new ArrayList<>(count);
        int[] delays = new int[count];
        for (int i = 0; i < count; i++) {
            IIOMetadataNode meta = (IIOMetadataNode) reader.getImageMetadata(i)
                    .getAsTree("javax_imageio_gif_image_1.0");
            IIOMetadataNode gce = child(meta, "GraphicControlExtension");
            IIOMetadataNode desc = child(meta, "ImageDescriptor");
            delays[i] = Math.max(20, Integer.parseInt(gce.getAttribute("delayTime")) * 10);
            int x = Integer.parseInt(desc.getAttribute("imageLeftPosition"));
            int y = Integer.parseInt(desc.getAttribute("imageTopPosition"));
            String disposal = gce.getAttribute("disposalMethod");
            BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
            BufferedImage raw = reader.read(i);
            Graphics2D g = canvas.createGraphics();
            g.drawImage(raw, x, y, null);
            g.dispose();
            frames.add(copy(canvas));
            if (before != null) {
                canvas = before;
            } else if ("restoreToBackgroundColor".equals(disposal)) {
                Graphics2D clear = canvas.createGraphics();
                clear.setComposite(AlphaComposite.Clear);
                clear.fillRect(x, y, raw.getWidth(), raw.getHeight());
                clear.dispose();
            }
        }
        return new Decoded(frames, delays);
    }

    private static IIOMetadataNode child(IIOMetadataNode node, String name) throws IOException {
        for (int i = 0; i < node.getLength(); i++) {
            if (node.item(i).getNodeName().equals(name)) return (IIOMetadataNode) node.item(i);
        }
        throw new IOException("no " + name);
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage toArgb(BufferedImage src) {
        return src.getType() == BufferedImage.TYPE_INT_ARGB ? src : copy(src);
    }

    /** Frames stacked vertically into one NativeImage (ARGB → ABGR). */
    private static NativeImage toSheet(Decoded d) {
        int w = d.frames().get(0).getWidth();
        int h = d.frames().get(0).getHeight();
        NativeImage img = new NativeImage(w, h * d.frames().size(), false);
        for (int f = 0; f < d.frames().size(); f++) {
            BufferedImage frame = d.frames().get(f);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = frame.getRGB(x, y);
                    int abgr = (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF);
                    img.setPixelRGBA(x, f * h + y, abgr);
                }
            }
        }
        return img;
    }

    // ------------------------------------------------------------------
    // Client-thread install + eviction
    // ------------------------------------------------------------------

    private static void install(String key, NativeImage sheet, int[] delays) {
        LOADING.remove(key);
        int frames = delays.length;
        int frameH = sheet.getHeight() / frames;
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID,
                "twitch_emote/" + key.replace(':', '_').toLowerCase(Locale.ROOT));
        Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(sheet));
        int total = 0;
        for (int ms : delays) total += ms;
        READY.put(key, new Sprite(rl, frames, sheet.getWidth(), frameH, delays, Math.max(total, 1)));
        if (READY.size() > MAX_CACHED) {
            Iterator<Map.Entry<String, Sprite>> it = READY.entrySet().iterator();
            Sprite evicted = it.next().getValue();
            it.remove();
            Minecraft.getInstance().getTextureManager().release(evicted.texture());
        }
    }

    private EmoteTextures() {
    }
}
```

- [ ] **Step 2:** `./gradlew build` green (no consumers yet). **Step 3:** Commit `1.11.0-dev: EmoteTextures — sprite-sheet cache, GIF compositing, LRU caps`.

### Task 5: EmoteText — the one tokenizer + wrap + memo + toggle pref

**Files:**
- Create: `src/main/java/com/modpack/linktablet/client/EmoteText.java`
- Modify: `src/main/java/com/modpack/linktablet/client/ClientPrefs.java`

**Interfaces:**
- Consumes: `ChatMessage`/`EmoteSpan` (T2), `TwitchEmotes.resolve/nativeEmote/generation` (T3), `EmoteTextures.get/frameAt` (T4).
- Produces:
  - `ClientPrefs.twitchEmotes()` / `setTwitchEmotes(boolean)` — key `"twitch.emotes"`, default TRUE (mirror the `twitchChannel` pref shape with a boolean).
  - `sealed interface Segment` with `record TextSeg(String text)` and `record EmoteSeg(TwitchEmotes.Emote emote)`.
  - `List<Segment> segments(ChatMessage m, String channel)` — memoized per message; honors the toggle (OFF → one TextSeg).
  - `record Run(int x, int width, Segment seg, String text)` / `record Line(List<Run> runs)`.
  - `List<Line> wrap(Font font, List<Segment> segments, int maxWidth, int emoteH)`.
  - `int emoteWidth(TwitchEmotes.Emote e, Font font, int emoteH)` — aspect-scaled if loaded, else the name's text width.
  - `void drawGui(GuiGraphics g, Font font, Line line, int x, int y, int emoteH, int textColor, boolean shadow)` — GUI/overlay draw of one line (the face draws itself in T8).

- [ ] **Step 1:** `ClientPrefs`: add `twitchEmotes()`/`setTwitchEmotes` under key `"twitch.emotes"`, default `true`, mirroring the existing boolean pref shape (or `twitchChannel()`'s shape with Boolean parsing if no boolean pref exists yet).
- [ ] **Step 2:** Write the tokenizer:

```java
package com.modpack.linktablet.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * THE emote tokenizer (1.11.0 emotes) — the one place chat text becomes
 * text/emote segments and wrapped lines. GUI, overlay, and kiosk face
 * all lay out through here (the TabletScreenMath one-source rule):
 * never fork matching or wrapping into a renderer. Tokenization is
 * memoized per ChatMessage (weak keys ride the ring buffer's lifetime)
 * and invalidated by TwitchEmotes.generation() when a set lands, or by
 * the toggle flipping.
 */
public final class EmoteText {

    public sealed interface Segment permits TextSeg, EmoteSeg {}
    public record TextSeg(String text) implements Segment {}
    public record EmoteSeg(TwitchEmotes.Emote emote) implements Segment {}

    /** One placed run on a wrapped line; text is null for emote runs. */
    public record Run(int x, int width, Segment seg, String text) {}
    public record Line(List<Run> runs) {}

    private record Memo(int generation, boolean emotesOn, List<Segment> segments) {}
    private static final Map<TwitchChatService.ChatMessage, Memo> MEMO = new WeakHashMap<>();

    // ------------------------------------------------------------------
    // Tokenization (memoized)
    // ------------------------------------------------------------------

    public static List<Segment> segments(TwitchChatService.ChatMessage m, String channel) {
        boolean on = ClientPrefs.twitchEmotes();
        int gen = TwitchEmotes.generation(channel);
        Memo memo = MEMO.get(m);
        if (memo != null && memo.generation() == gen && memo.emotesOn() == on) {
            return memo.segments();
        }
        List<Segment> segs = on ? build(m, channel) : List.of(new TextSeg(m.text()));
        MEMO.put(m, new Memo(gen, on, segs));
        return segs;
    }

    private static List<Segment> build(TwitchChatService.ChatMessage m, String channel) {
        List<Segment> out = new ArrayList<>();
        String text = m.text();
        try {
            // Native spans are INCLUSIVE CODE-POINT ranges (see EmoteSpan)
            int cpCursor = 0;
            int charCursor = 0;
            for (TwitchChatService.EmoteSpan span : m.emotes()) {
                if (span.from() < cpCursor) continue; // overlapping/bad tag
                int start = text.offsetByCodePoints(charCursor, span.from() - cpCursor);
                int end = text.offsetByCodePoints(start, span.to() - span.from() + 1);
                if (start > charCursor) words(text.substring(charCursor, start), channel, out);
                out.add(new EmoteSeg(TwitchEmotes.nativeEmote(span.id(), text.substring(start, end))));
                charCursor = end;
                cpCursor = span.to() + 1;
            }
            if (charCursor < text.length()) words(text.substring(charCursor), channel, out);
        } catch (IndexOutOfBoundsException e) {
            return List.of(new TextSeg(text)); // malformed ranges: text wins
        }
        return out.isEmpty() ? List.of(new TextSeg("")) : out;
    }

    /** Word-boundary third-party matching; unmatched text coalesces
     * into single TextSegs (fewer segments, fewer draw calls). */
    private static void words(String text, String channel, List<Segment> out) {
        StringBuilder pending = new StringBuilder();
        for (String token : text.split("(?<= )")) { // split AFTER spaces, keeps them
            String bare = token.strip();
            TwitchEmotes.Emote e = bare.isEmpty() ? null : TwitchEmotes.resolve(bare, channel);
            if (e == null) {
                pending.append(token);
                continue;
            }
            if (!pending.isEmpty()) {
                out.add(new TextSeg(pending.toString()));
                pending.setLength(0);
            }
            out.add(new EmoteSeg(e));
            pending.append(token, bare.length(), token.length()); // trailing space
        }
        if (!pending.isEmpty()) out.add(new TextSeg(pending.toString()));
    }

    // ------------------------------------------------------------------
    // Wrapping (GUI + overlay; the face is single-line and self-clips)
    // ------------------------------------------------------------------

    public static List<Line> wrap(Font font, List<Segment> segments, int maxWidth, int emoteH) {
        List<Line> lines = new ArrayList<>();
        List<Run> current = new ArrayList<>();
        int x = 0;
        for (Segment seg : segments) {
            if (seg instanceof EmoteSeg es) {
                int w = Math.min(emoteWidth(es.emote(), font, emoteH), maxWidth);
                if (x > 0 && x + w > maxWidth) {
                    lines.add(new Line(current));
                    current = new ArrayList<>();
                    x = 0;
                }
                current.add(new Run(x, w, seg, null));
                x += w;
            } else if (seg instanceof TextSeg ts) {
                for (String word : ts.text().split("(?<= )")) {
                    // Hard-break single words wider than the row
                    while (font.width(word) > maxWidth) {
                        String head = font.plainSubstrByWidth(word, maxWidth - Math.min(x, maxWidth - 1));
                        if (head.isEmpty()) {
                            lines.add(new Line(current));
                            current = new ArrayList<>();
                            x = 0;
                            head = font.plainSubstrByWidth(word, maxWidth);
                        }
                        x = appendText(current, lines, font, x, maxWidth, head);
                        word = word.substring(head.length());
                    }
                    if (word.isEmpty()) continue;
                    int w = font.width(word);
                    if (x > 0 && x + w > maxWidth) {
                        lines.add(new Line(current));
                        current = new ArrayList<>();
                        x = 0;
                        if (word.isBlank()) continue; // no leading wrap-space
                    }
                    x = appendText(current, lines, font, x, maxWidth, word);
                }
            }
        }
        if (!current.isEmpty() || lines.isEmpty()) lines.add(new Line(current));
        return lines;
    }

    /** Appends text to the current line, merging into a trailing text
     * run when possible. Returns the new x cursor. */
    private static int appendText(List<Run> current, List<Line> lines, Font font,
                                  int x, int maxWidth, String text) {
        int w = font.width(text);
        if (!current.isEmpty()) {
            Run last = current.get(current.size() - 1);
            if (last.seg() instanceof TextSeg && last.x() + last.width() == x) {
                current.set(current.size() - 1,
                        new Run(last.x(), last.width() + w, last.seg(), last.text() + text));
                return x + w;
            }
        }
        current.add(new Run(x, w, new TextSeg(text), text));
        return x + w;
    }

    /** Loaded emotes are aspect-scaled to emoteH; unloaded/FAILED render
     * as their text name (chat never blocks on the network). */
    public static int emoteWidth(TwitchEmotes.Emote e, Font font, int emoteH) {
        EmoteTextures.Sprite s = EmoteTextures.get(e);
        return s == null ? font.width(e.name())
                : Mth.clamp(emoteH * s.frameW() / Math.max(1, s.frameH()), 1, emoteH * 4);
    }

    // ------------------------------------------------------------------
    // GUI draw (screen + overlay; NOT the world face)
    // ------------------------------------------------------------------

    public static void drawGui(GuiGraphics g, Font font, Line line, int x, int y,
                               int emoteH, int textColor, boolean shadow) {
        for (Run run : line.runs()) {
            if (run.seg() instanceof EmoteSeg es) {
                EmoteTextures.Sprite s = EmoteTextures.get(es.emote());
                if (s == null) {
                    g.drawString(font, es.emote().name(), x + run.x(), y, textColor, shadow);
                    continue;
                }
                int frame = EmoteTextures.frameAt(s, Util.getMillis());
                g.blit(s.texture(), x + run.x(), y, run.width(), emoteH,
                        0, frame * s.frameH(), s.frameW(), s.frameH(),
                        s.frameW(), s.frameH() * s.frameCount());
            } else {
                g.drawString(font, run.text(), x + run.x(), y, textColor, shadow);
            }
        }
    }

    private EmoteText() {
    }
}
```

(If the `g.blit(ResourceLocation, x, y, w, h, u, v, uW, vH, texW, texH)` overload's argument order differs in the mapped 1.21.1 `GuiGraphics`, match the mapping — the intent is: draw the `frame`-th vertical sheet cell scaled to `run.width() × emoteH`.)

- [ ] **Step 3:** `./gradlew build` green. **Step 4:** Commit `1.11.0-dev: EmoteText — one tokenizer, wrap, memo, emotes pref`.

### Task 6: TwitchScreen — emote rows + header toggle

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/screen/TwitchScreen.java`
- Modify: `src/main/java/com/modpack/linktablet/client/screen/HeaderGlyphs.java`

**Interfaces:**
- Consumes: `EmoteText.segments/wrap/drawGui` (T5), `ClientPrefs.twitchEmotes/setTwitchEmotes`.
- Produces: `HeaderGlyphs.emotes(GuiGraphics, int x, int y, int color)` — reused nowhere else yet, but follows the home/pin painter signature.

- [ ] **Step 1:** Replace the wrapping: `Row` becomes `record Row(Component prefix, int prefixColor, int indent, List<EmoteText.Line> lines, int height)`; in `buildRows`, replace the `font.split` call with:

```java
            List<EmoteText.Line> lines = EmoteText.wrap(font,
                    EmoteText.segments(m, currentChannel()), textWidth, LINE_H);
            rows.add(new Row(prefix, m.color(), indent, lines, lines.size() * LINE_H));
```

and in `render`, replace the inner lines loop with:

```java
                    List<EmoteText.Line> lines = r.lines();
                    for (int i = 0; i < lines.size(); i++) {
                        EmoteText.drawGui(graphics, font, lines.get(i), rowX() + r.indent(),
                                y + i * LINE_H, LINE_H, theme.textPrimary, theme.textShadow);
                    }
```

- [ ] **Step 2:** `HeaderGlyphs`: add an `emotes` painter beside `home`/`pin`, same signature and 12px procedural-fill style (a smiley: box outline via four 1px fills, two 1×2 eyes, a 4×1 mouth — match the neighbors' pixel idiom exactly):

```java
    /** Smiley — the Twitch emotes on/off toggle. */
    public static void emotes(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 2, y + 1, x + 10, y + 2, color);
        graphics.fill(x + 1, y + 2, x + 2, y + 10, color);
        graphics.fill(x + 10, y + 2, x + 11, y + 10, color);
        graphics.fill(x + 2, y + 10, x + 10, y + 11, color);
        graphics.fill(x + 4, y + 4, x + 5, y + 6, color);
        graphics.fill(x + 7, y + 4, x + 8, y + 6, color);
        graphics.fill(x + 4, y + 7, x + 8, y + 8, color);
    }
```

- [ ] **Step 3:** `TwitchScreen`: third header button — `private int emoteBtnX() { return pinBtnX() + MODE_BTN_SIZE + 4; }`; in `render`, after the pin glyph:

```java
        HeaderGlyphs.emotes(graphics, emoteBtnX(), modeBtnY(),
                ClientPrefs.twitchEmotes() ? theme.accent
                        : overBtn(mouseX, mouseY, emoteBtnX()) ? theme.glyphHover : theme.textFaint);
```

and in `mouseClicked`, after the pin branch:

```java
        if (button == 0 && overBtn(mouseX, mouseY, emoteBtnX())) {
            ClientPrefs.setTwitchEmotes(!ClientPrefs.twitchEmotes());
            UISounds.tick(ClientPrefs.twitchEmotes() ? 1.5F : 1.0F);
            return true;
        }
```

- [ ] **Step 4:** `./gradlew build` green. **Step 5:** Commit `1.11.0-dev: TwitchScreen emote rows + header toggle`.

### Task 7: Overlay pane emotes

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/screen/TwitchOverlayContent.java`

**Interfaces:**
- Consumes: `EmoteText.segments/wrap/drawGui` — nothing new produced.

- [ ] **Step 1:** Same substitution as Task 6 Step 1 applied to the overlay's own wrapping/drawing (it wraps at its own row width and line height — find its `font.split`/line loop and route through `EmoteText.wrap` + `EmoteText.drawGui` with ITS width/lineH values; the channel for `segments()` is the overlay's bound channel). Do NOT copy Task 6's row heights — keep the overlay's existing metrics; emoteH = the overlay's line height.
- [ ] **Step 2:** `./gradlew build` green. **Step 3:** Commit `1.11.0-dev: overlay chat pane emotes`.

### Task 8: Kiosk wall face emotes

**Files:**
- Modify: `src/main/java/com/modpack/linktablet/client/render/TabletScreenRenderer.java` (`renderTwitchFace`, lines ~953–1020)

**Interfaces:**
- Consumes: `EmoteText.segments/emoteWidth`, `EmoteTextures.get/frameAt`, the face's existing locals (`u0`, `u1`, `scale`, `maxPx`, `top`, `bgLight`, `LIST_TEXT_H`, `FONT_LINE`, `TextFit.ellipsize`, `drawLabel`).

- [ ] **Step 1:** In `renderTwitchFace`, declare before the message loop:

```java
        record EmoteQuad(EmoteTextures.Sprite sprite, float u, float v, float w, float h) {}
        List<EmoteQuad> emoteQuads = new ArrayList<>();
```

Replace the per-message body AFTER the prefix draw (keep the prefix logic, including the prefix-overflow `continue` branch) with a segment walk:

```java
            float advanceU = u0 + prefixWidth * LIST_TEXT_H / FONT_LINE;
            int budgetPx = maxPx - prefixWidth;
            for (EmoteText.Segment seg : EmoteText.segments(message, channel)) {
                if (budgetPx <= 0) break;
                if (seg instanceof EmoteText.EmoteSeg es) {
                    EmoteTextures.Sprite sprite = EmoteTextures.get(es.emote());
                    int wPx = EmoteText.emoteWidth(es.emote(), font, FONT_LINE);
                    if (sprite == null) { // unloaded: text-name fallback
                        String name = TextFit.ellipsize(font, es.emote().name(), budgetPx);
                        drawLabel(poseStack, buffers, font, name, advanceU, top, scale,
                                false, false, theme.textPrimary, bgLight);
                        int drawn = font.width(name);
                        advanceU += drawn * LIST_TEXT_H / (float) FONT_LINE;
                        budgetPx -= drawn;
                        continue;
                    }
                    if (wPx > budgetPx) break; // no partial emotes on a wall line
                    emoteQuads.add(new EmoteQuad(sprite, advanceU, top,
                            wPx * LIST_TEXT_H / (float) FONT_LINE, LIST_TEXT_H));
                    advanceU += wPx * LIST_TEXT_H / (float) FONT_LINE;
                    budgetPx -= wPx;
                } else if (seg instanceof EmoteText.TextSeg ts) {
                    String draw = TextFit.ellipsize(font, ts.text(), budgetPx);
                    drawLabel(poseStack, buffers, font, draw, advanceU, top, scale,
                            false, false, theme.textPrimary, bgLight);
                    int drawn = font.width(draw);
                    advanceU += drawn * LIST_TEXT_H / (float) FONT_LINE;
                    budgetPx -= drawn;
                    if (!draw.equals(ts.text())) budgetPx = 0; // ellipsized: stop
                }
            }
```

- [ ] **Step 2:** After the message loop (before `popPose`), flush the quads grouped by texture through `RenderType.text` — the font's own family, so the pass discipline holds:

```java
        if (!emoteQuads.isEmpty()) {
            emoteQuads.sort(java.util.Comparator.comparing(q -> q.sprite().texture().toString()));
            org.joml.Matrix4f mat = poseStack.last().pose();
            long now = net.minecraft.Util.getMillis();
            for (EmoteQuad q : emoteQuads) {
                com.mojang.blaze3d.vertex.VertexConsumer vc =
                        buffers.getBuffer(net.minecraft.client.renderer.RenderType.text(
                                q.sprite().texture()));
                int frame = EmoteTextures.frameAt(q.sprite(), now);
                float v0 = (float) frame / q.sprite().frameCount();
                float v1 = (float) (frame + 1) / q.sprite().frameCount();
                vc.addVertex(mat, q.u(), q.v() + q.h(), Z).setColor(-1).setUv(0f, v1).setLight(bgLight);
                vc.addVertex(mat, q.u() + q.w(), q.v() + q.h(), Z).setColor(-1).setUv(1f, v1).setLight(bgLight);
                vc.addVertex(mat, q.u() + q.w(), q.v(), Z).setColor(-1).setUv(1f, v0).setLight(bgLight);
                vc.addVertex(mat, q.u(), q.v(), Z).setColor(-1).setUv(0f, v0).setLight(bgLight);
            }
        }
```

**Before writing this block, read how `drawLabel` (and the item-icon pass) map their u/top coordinates and Z into the pose** — the quad's `(u, v)` coordinate plane and winding must match EXACTLY what text uses at that point in the pose stack, and `Z` is the ITEM-ICON hairline layer constant (icons 3.5x — emotes are image content, one hairline below text). If `drawLabel` applies a scale transform rather than drawing in glass-texel space directly, wrap the quad emission in the same pose transform. Verify the vertex winding against an existing quad emission in this file (the fills pass) — flip if the emotes render only from behind.

- [ ] **Step 3:** `./gradlew build` green. **Step 4:** Commit `1.11.0-dev: kiosk chat wall emotes — RenderType.text quads`.

### Task 9: Docs + beta.6

**Files:**
- Modify: `CHANGELOG.md`, `docs/NEXT_SESSION.md`, `CLAUDE.md`, `gradle.properties`

- [ ] **Step 1:** CHANGELOG "Unreleased": Twitch emotes bullet (native + 7TV/BTTV/FFZ, animated, all three surfaces, toggle in the Twitch header; client-only — no pairing impact, beta.6 pairs with beta.4/5).
- [ ] **Step 2:** NEXT_SESSION: beta.6 status section + the spec's "Test matrix (beta.6 additions)" copied verbatim. CLAUDE.md: extend the Twitch Chat gotcha — TwitchEmotes/EmoteTextures/EmoteText are CLIENT-ONLY; anonymous CDN GETs only (no tokens ever); `EmoteText` is the ONE tokenizer (never fork into a renderer); wall emotes ride `RenderType.text` only; caps 1x/40 frames/256 KB/LRU 128; toggle pref `twitch.emotes`.
- [ ] **Step 3:** `gradle.properties` → `mod_version=1.11.0-beta.6`. Full `./gradlew build`; jar `build/libs/linktablet-1.11.0-beta.6.jar`.
- [ ] **Step 4:** Commit `1.11.0-beta.6: Twitch chat emotes + docs`, push. STOP: dev-mode boot check, jar delivery, tester checklist, and the in-world F2 pass (live channel on GUI + wall) are the controller's/user's steps per the standing workflow.

---

## Self-review

**Spec coverage:** spike gate (T1 — spec risk #1's mandated first task); native spans + code points + room-id (T2); third-party sets, per-provider soft-fail, lifecycle on join/part/logout (T3); texture pipeline, GIF compositing (risk #2), caps, LRU, animation clock (T4); one tokenizer + memo + generation invalidation + toggle pref (T5); GUI rows + header toggle glyph (T6); overlay (T7); wall face quads via RenderType.text + bleed-inset compliance (quads use the existing `u0`/`top` line positions, which already carry the insets) + no-partial-emote ellipsis (T8); docs/version (T9). Non-goals are absences. No gaps.

**Placeholders:** none — T1–T5 carry full code; T6–T8 give exact substitution code anchored to the current file contents; T8's coordinate-verification note is an instruction to check the existing helpers, not deferred design.

**Type consistency:** `Emote(cacheKey, name, url)` consistent T3→T4/T5; `Sprite(texture, frameCount, frameW, frameH, delaysMs, totalMs)` consistent T4→T5/T6/T8; `EmoteSpan(from, to, id)` T2→T5; `segments(ChatMessage, String)` / `wrap(Font, List<Segment>, int, int)` / `drawGui(...)` / `emoteWidth(Emote, Font, int)` signatures match at every call site; `pick7tvFile` duplication rule stated once (T3 note) so shipped code never imports `tools/`.
