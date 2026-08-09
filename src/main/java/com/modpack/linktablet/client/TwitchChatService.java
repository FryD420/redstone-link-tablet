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
 * class must never grow a PRIVMSG writer. Channel membership crosses
 * threads through {@code WANTED_SHARED}: the client thread mirrors its
 * wanted set into it every tick, and the worker reads only that
 * concurrent set on (re)connect — it never touches the client-thread-only
 * {@code JOINED} bookkeeping.
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

    /** Client thread writes (mirrored every tick); worker thread only reads. */
    private static final Set<String> WANTED_SHARED = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
        WANTED_SHARED.retainAll(wanted);
        WANTED_SHARED.addAll(wanted);
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
        private volatile SSLSocket socket;
        private long backoff = BACKOFF_MIN_MS;

        Worker() {
            super("linktablet-twitch");
            setDaemon(true);
        }

        void shutdown() {
            stop = true;
            interrupt();
            SSLSocket sock = socket;
            if (sock != null) {
                try {
                    sock.close();
                } catch (IOException ignored) {
                }
            }
        }

        void send(String line) {
            outbox.add(line);
        }

        @Override
        public void run() {
            while (!stop) {
                status = Status.CONNECTING;
                try (SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault()
                        .createSocket("irc.chat.twitch.tv", 6697)) {
                    this.socket = s;
                    s.setSoTimeout(360_000); // Twitch pings ~5min; 6min = dead link
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            s.getInputStream(), StandardCharsets.UTF_8));
                    out = new BufferedWriter(new OutputStreamWriter(
                            s.getOutputStream(), StandardCharsets.UTF_8));
                    write("CAP REQ :twitch.tv/tags");
                    write("NICK justinfan" + (10_000 + new java.util.Random().nextInt(80_000)));
                    // Re-JOIN everything the client thread currently wants
                    // (reconnects) — WANTED_SHARED is a concurrent set safe
                    // to iterate cross-thread; JOINED stays client-thread-only.
                    outbox.clear();
                    for (String c : WANTED_SHARED) write("JOIN #" + c);
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
                this.socket = null;
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
            String channel = rest.substring(chanStart, colon).trim().toLowerCase(java.util.Locale.ROOT);
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
