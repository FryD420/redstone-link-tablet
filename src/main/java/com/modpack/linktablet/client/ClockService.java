package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * The Clock app's engine (1.10.0 OS suite): alarms, countdown timer, and
 * stopwatch live HERE, not on the screen — the NoteWindows pattern — so
 * rings fire from the client tick with no GUI open. All state is
 * client-local and persisted through {@link ClientPrefs} {@code clock.*}
 * keys (real wall-clock epochs, so a timer keeps counting across
 * relaunches and an alarm set for 7:30 rings at 7:30 regardless of the
 * session). Time math uses {@link System#currentTimeMillis()} — unlike
 * the UI-flash uses of {@code Util.getMillis()}, alarms need the real
 * wall clock.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class ClockService {

    /** A daily alarm: minute-of-day (0–1439) + enabled. Rings every day
     * while enabled — v1 has no per-weekday schedule. */
    public record Alarm(int minuteOfDay, boolean enabled) {
        public String label() {
            return String.format("%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
        }
    }

    public static final int MAX_ALARMS = 8;
    public static final int MAX_ZONES = 8;
    /** Ring duration and chime cadence (ms / ticks). */
    private static final long RING_MILLIS = 6000;
    private static final int CHIME_TICKS = 12;

    private static boolean loaded = false;
    private static final List<Alarm> ALARMS = new ArrayList<>();
    private static final List<ZoneId> ZONES = new ArrayList<>();
    /** Last-used timer length (seconds) — survives runs. */
    private static int timerDuration = 300;
    /** Epoch millis the running timer rings at; 0 = idle. */
    private static long timerEnd = 0;
    /** Accumulated stopwatch millis (excludes the live run segment). */
    private static long stopwatchElapsed = 0;
    /** Epoch millis the live stopwatch segment started; 0 = paused. */
    private static long stopwatchAnchor = 0;

    /** Day-minute stamp of the last alarm fired (epochDay * 1440 +
     * minute) — in-memory only; relogging inside the ring minute just
     * re-rings, which is what an unanswered alarm should do anyway. */
    private static long lastAlarmStamp = -1;

    // Ring in progress (alarm or timer): chimes + actionbar until quelled
    private static long ringUntil = 0;
    private static Component ringMessage = null;
    private static int chimeTicker = 0;

    // ---- Tick --------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        load();
        long now = System.currentTimeMillis();

        // Timer: one ring at expiry, then idle (the end stamp is real
        // wall time, so a timer that lapsed while the game was closed
        // rings on the first tick back in)
        if (timerEnd != 0 && now >= timerEnd) {
            timerEnd = 0;
            saveTimer();
            startRing(Component.translatable("message.linktablet.clock.timer_done"));
        }

        // Alarms: fire when the local wall clock enters the set minute
        LocalDateTime local = LocalDateTime.now();
        long stamp = LocalDate.now().toEpochDay() * 1440L
                + local.getHour() * 60L + local.getMinute();
        for (Alarm alarm : ALARMS) {
            if (alarm.enabled() && stamp % 1440 == alarm.minuteOfDay() && stamp != lastAlarmStamp) {
                lastAlarmStamp = stamp;
                startRing(Component.translatable("message.linktablet.clock.alarm_ring",
                        alarm.label()));
                break;
            }
        }

        // Active ring: chime + refresh the actionbar toast until it
        // expires or something quells it
        if (ringing()) {
            if (chimeTicker++ % CHIME_TICKS == 0) {
                UISounds.play(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4F, 0.8F);
                mc.player.displayClientMessage(ringMessage, true);
            }
        } else {
            ringMessage = null;
        }
    }

    private static void startRing(Component message) {
        ringUntil = System.currentTimeMillis() + RING_MILLIS;
        ringMessage = message;
        chimeTicker = 0;
    }

    public static boolean ringing() {
        return ringMessage != null && System.currentTimeMillis() < ringUntil;
    }

    /** Any tap in the clock GUI quells an active ring. */
    public static void stopRing() {
        ringUntil = 0;
        ringMessage = null;
    }

    // ---- Alarms ------------------------------------------------------

    public static List<Alarm> alarms() {
        load();
        return ALARMS;
    }

    /** Adds enabled; duplicates of an existing minute are refused. */
    public static boolean addAlarm(int minuteOfDay) {
        load();
        if (ALARMS.size() >= MAX_ALARMS) return false;
        for (Alarm alarm : ALARMS) {
            if (alarm.minuteOfDay() == minuteOfDay) return false;
        }
        ALARMS.add(new Alarm(minuteOfDay, true));
        ALARMS.sort(java.util.Comparator.comparingInt(Alarm::minuteOfDay));
        saveAlarms();
        return true;
    }

    public static void toggleAlarm(int index) {
        load();
        if (index < 0 || index >= ALARMS.size()) return;
        Alarm alarm = ALARMS.get(index);
        ALARMS.set(index, new Alarm(alarm.minuteOfDay(), !alarm.enabled()));
        saveAlarms();
    }

    public static void removeAlarm(int index) {
        load();
        if (index < 0 || index >= ALARMS.size()) return;
        ALARMS.remove(index);
        saveAlarms();
    }

    // ---- World clock zones -------------------------------------------

    public static List<ZoneId> zones() {
        load();
        return ZONES;
    }

    public static boolean addZone(ZoneId zone) {
        load();
        if (ZONES.size() >= MAX_ZONES || ZONES.contains(zone)) return false;
        ZONES.add(zone);
        saveZones();
        return true;
    }

    public static void removeZone(int index) {
        load();
        if (index < 0 || index >= ZONES.size()) return;
        ZONES.remove(index);
        saveZones();
    }

    // ---- Timer -------------------------------------------------------

    public static int timerDuration() {
        load();
        return timerDuration;
    }

    /** Clamped 5s–6h; only meaningful while the timer is idle. */
    public static void setTimerDuration(int seconds) {
        load();
        timerDuration = Math.max(5, Math.min(6 * 3600, seconds));
        saveTimer();
    }

    public static boolean timerRunning() {
        load();
        return timerEnd != 0;
    }

    public static long timerRemainingMillis() {
        load();
        return timerEnd == 0 ? 0 : Math.max(0, timerEnd - System.currentTimeMillis());
    }

    public static void startTimer() {
        load();
        timerEnd = System.currentTimeMillis() + timerDuration * 1000L;
        saveTimer();
    }

    public static void cancelTimer() {
        load();
        timerEnd = 0;
        saveTimer();
    }

    // ---- Stopwatch ---------------------------------------------------

    public static boolean stopwatchRunning() {
        load();
        return stopwatchAnchor != 0;
    }

    public static long stopwatchElapsedMillis() {
        load();
        return stopwatchElapsed
                + (stopwatchAnchor == 0 ? 0 : System.currentTimeMillis() - stopwatchAnchor);
    }

    /** Start/pause toggle. */
    public static void stopwatchStartPause() {
        load();
        if (stopwatchAnchor == 0) {
            stopwatchAnchor = System.currentTimeMillis();
        } else {
            stopwatchElapsed += System.currentTimeMillis() - stopwatchAnchor;
            stopwatchAnchor = 0;
        }
        saveStopwatch();
    }

    public static void stopwatchReset() {
        load();
        stopwatchElapsed = 0;
        stopwatchAnchor = 0;
        saveStopwatch();
    }

    // ---- Persistence (ClientPrefs clock.* strings) -------------------

    private static void load() {
        if (loaded) return;
        loaded = true;
        for (String entry : ClientPrefs.clock("alarms", "").split(",")) {
            String[] parts = entry.split("\\|");
            if (parts.length != 2) continue;
            try {
                int minute = Integer.parseInt(parts[0]);
                if (minute >= 0 && minute < 1440 && ALARMS.size() < MAX_ALARMS) {
                    ALARMS.add(new Alarm(minute, "1".equals(parts[1])));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        for (String id : ClientPrefs.clock("zones", "").split(",")) {
            if (id.isBlank() || ZONES.size() >= MAX_ZONES) continue;
            try {
                ZoneId zone = ZoneId.of(id);
                if (!ZONES.contains(zone)) ZONES.add(zone);
            } catch (Exception ignored) {
            }
        }
        timerDuration = (int) Math.max(5,
                Math.min(6 * 3600, parseLong(ClientPrefs.clock("timerDur", ""), 300)));
        timerEnd = parseLong(ClientPrefs.clock("timerEnd", ""), 0);
        stopwatchElapsed = Math.max(0, parseLong(ClientPrefs.clock("swElapsed", ""), 0));
        stopwatchAnchor = parseLong(ClientPrefs.clock("swAnchor", ""), 0);
        // A stopwatch left running keeps counting across sessions on
        // purpose (wall-clock anchor) — real elapsed time, like a phone.
    }

    private static long parseLong(String value, long fallback) {
        try {
            return value.isEmpty() ? fallback : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void saveAlarms() {
        StringBuilder sb = new StringBuilder();
        for (Alarm alarm : ALARMS) {
            if (sb.length() > 0) sb.append(',');
            sb.append(alarm.minuteOfDay()).append('|').append(alarm.enabled() ? 1 : 0);
        }
        ClientPrefs.setClock("alarms", sb.toString());
    }

    private static void saveZones() {
        StringBuilder sb = new StringBuilder();
        for (ZoneId zone : ZONES) {
            if (sb.length() > 0) sb.append(',');
            sb.append(zone.getId());
        }
        ClientPrefs.setClock("zones", sb.toString());
    }

    private static void saveTimer() {
        ClientPrefs.setClock("timerDur", Integer.toString(timerDuration));
        ClientPrefs.setClock("timerEnd", timerEnd == 0 ? "" : Long.toString(timerEnd));
    }

    private static void saveStopwatch() {
        ClientPrefs.setClock("swElapsed", stopwatchElapsed == 0 ? "" : Long.toString(stopwatchElapsed));
        ClientPrefs.setClock("swAnchor", stopwatchAnchor == 0 ? "" : Long.toString(stopwatchAnchor));
    }

    private ClockService() {
    }
}
