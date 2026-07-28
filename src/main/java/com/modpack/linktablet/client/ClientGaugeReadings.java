package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.network.ModNetworking;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client store for the player's gauge readings (1.10.0): the latest
 * {@code GaugeReadingsPayload} snapshot, keyed by frequency. Every dial
 * on a CARRIED tablet reads through {@link #strength} — kiosk dials use
 * the block entity's own synced readings instead (the block's receivers
 * sit at the block, not the player). Frequencies absent from the map
 * read 0: no transmitter in range, or nothing listening yet.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class ClientGaugeReadings {

    private static final Map<Frequency, Integer> READINGS = new HashMap<>();

    /** Payload handler entry: the snapshot REPLACES the map wholesale. */
    public static void accept(List<ModNetworking.GaugeReading> readings) {
        READINGS.clear();
        for (ModNetworking.GaugeReading reading : readings) {
            READINGS.put(reading.frequency(), reading.strength());
        }
    }

    public static int strength(Frequency frequency) {
        return READINGS.getOrDefault(frequency, 0);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        READINGS.clear();
    }

    private ClientGaugeReadings() {
    }
}
