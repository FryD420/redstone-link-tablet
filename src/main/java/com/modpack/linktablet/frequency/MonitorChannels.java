package com.modpack.linktablet.frequency;

import java.util.ArrayList;
import java.util.List;

/**
 * The ONE channel table for the Frequency Monitor (1.11.0): probes
 * first (in stored order), then every signal frequency, then every
 * gauge frequency, deduped by Create-network identity in first-seen
 * order. Server scanner, BE summary sync, face renderer, and GUI all
 * derive the same indexed list from the same synced data —
 * index-mapped wire forms (the {@code gauge_readings} idiom) stay
 * aligned for free.
 */
public final class MonitorChannels {

    /** Probe list cap — mirrors the signal editor's frequency cap. */
    public static final int MAX_PROBES = 8;

    public static List<Frequency> channelsOf(List<Signal> signals, List<Gauge> gauges,
                                             List<Frequency> probes) {
        List<Frequency> channels = new ArrayList<>();
        if (probes != null) {
            for (Frequency probe : probes) {
                if (!probe.isEmpty() && !channels.contains(probe)) channels.add(probe);
            }
        }
        for (Signal signal : signals) {
            for (Frequency freq : signal.frequencies()) {
                if (!freq.isEmpty() && !channels.contains(freq)) channels.add(freq);
            }
        }
        for (Gauge gauge : gauges) {
            Frequency freq = gauge.frequency();
            if (!freq.isEmpty() && !channels.contains(freq)) channels.add(freq);
        }
        return channels;
    }

    private MonitorChannels() {
    }
}
