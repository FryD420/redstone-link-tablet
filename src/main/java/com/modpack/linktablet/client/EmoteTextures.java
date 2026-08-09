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
