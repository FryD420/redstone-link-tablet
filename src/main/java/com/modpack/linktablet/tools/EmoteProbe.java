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

    private static JsonElement json(String url) throws Exception {
        HttpResponse<String> r = HTTP.send(
                HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString());
        return JsonParser.parseString(r.body());
    }

    private EmoteProbe() {}
}
