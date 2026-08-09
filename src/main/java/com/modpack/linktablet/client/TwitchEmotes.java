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

    /** Bumped on logout; in-flight fetch results from an older epoch are
     * dropped in apply(). */
    private static int epoch = 0;

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
        epoch++;
        SETS.clear();
        GENERATION.clear();
        FETCHED.clear();
        JOINED.clear();
    }

    // ------------------------------------------------------------------
    // Fetching (async; results applied on the client thread)
    // ------------------------------------------------------------------

    private static void fetchGlobals() {
        int fetchEpoch = epoch;
        get("https://7tv.io/v3/emote-sets/global", "7TV global", json ->
                apply(fetchEpoch, "", parse7tv(json.getAsJsonObject().getAsJsonArray("emotes"))));
        get("https://api.betterttv.net/3/cached/emotes/global", "BTTV global", json ->
                apply(fetchEpoch, "", parseBttv(json.getAsJsonArray())));
        get("https://api.frankerfacez.com/v1/set/global", "FFZ global", json ->
                apply(fetchEpoch, "", parseFfz(json.getAsJsonObject().getAsJsonObject("sets"))));
    }

    private static void fetchChannel(String channel, String roomId) {
        int fetchEpoch = epoch;
        get("https://7tv.io/v3/users/twitch/" + roomId, "7TV/" + channel, json -> {
            JsonObject set = json.getAsJsonObject().getAsJsonObject("emote_set");
            if (set != null) apply(fetchEpoch, channel, parse7tv(set.getAsJsonArray("emotes")));
        });
        get("https://api.betterttv.net/3/cached/users/twitch/" + roomId, "BTTV/" + channel,
                json -> {
                    JsonObject o = json.getAsJsonObject();
                    Map<String, Emote> m = parseBttv(o.getAsJsonArray("channelEmotes"));
                    m.putAll(parseBttv(o.getAsJsonArray("sharedEmotes")));
                    apply(fetchEpoch, channel, m);
                });
        get("https://api.frankerfacez.com/v1/room/id/" + roomId, "FFZ/" + channel, json ->
                apply(fetchEpoch, channel, parseFfz(json.getAsJsonObject().getAsJsonObject("sets"))));
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

    private static void apply(int fetchEpoch, String channel, Map<String, Emote> emotes) {
        if (fetchEpoch != epoch) return; // logged out (and possibly back in) since dispatch
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
                String file = pick7tvFile(host.getAsJsonArray("files"));
                if (file == null) continue; // WebP/AVIF-only: stays text (spec risk #1)
                String name = e.get("name").getAsString();
                out.put(name, new Emote("7tv:" + e.get("id").getAsString(), name,
                        "https:" + host.get("url").getAsString() + "/" + file));
            } catch (RuntimeException ignored) {
            }
        }
        return out;
    }

    // pick7tvFile: the EmoteProbe rule, copied — tools/ never ships
    private static String pick7tvFile(JsonArray files) {
        String png = null;
        for (JsonElement f : files) {
            String name = f.getAsJsonObject().get("name").getAsString();
            if (name.equals("1x.gif")) return name;
            if (name.equals("1x.png")) png = name;
        }
        return png;
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
