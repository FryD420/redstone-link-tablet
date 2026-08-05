package com.modpack.linktablet.compat;

import com.modpack.linktablet.network.ModNetworking;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Frequency Monitor server scanner (1.11.0) — filled in by the scanner task.
 */
public class MonitorScanner {

    public static void handleSubscribe(ModNetworking.MonitorSubscribePayload payload, IPayloadContext context) {
        // stub — real handler lands with the scanner
    }
}
