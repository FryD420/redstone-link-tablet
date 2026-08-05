package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.network.ModNetworking;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Objects;

/**
 * Client store for Frequency Monitor snapshots (1.11.0) plus the
 * subscription heartbeat: while a consumer (MonitorScreen or the
 * overlay pin) is registered, a MonitorSubscribePayload re-pings every
 * PING_TICKS so the server keeps polling; dropping the last consumer
 * sends active=false. ONE target at a time — the monitor shows the
 * network as seen from one tablet.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class ClientMonitorSnapshot {

    private static final int PING_TICKS = 40;

    private static List<ModNetworking.MonitorChannel> channels = List.of();
    private static ModNetworking.SignalTarget target;
    private static int consumers;
    private static int ticksSincePing;

    public static void accept(List<ModNetworking.MonitorChannel> newChannels) {
        channels = newChannels;
    }

    public static List<ModNetworking.MonitorChannel> channels() {
        return channels;
    }

    /** Register a consumer watching {@code newTarget}; retargeting
     * (a second consumer on a different tablet) rebinds everyone —
     * last opener wins, matching overlay right-click behavior. */
    public static void acquire(ModNetworking.SignalTarget newTarget) {
        if (!Objects.equals(target, newTarget)) {
            target = newTarget;
            channels = List.of();
        }
        consumers++;
        sendPing(true);
        ticksSincePing = 0;
    }

    public static void release() {
        consumers = Math.max(0, consumers - 1);
        if (consumers == 0 && target != null) {
            sendPing(false);
            target = null;
            channels = List.of();
        }
    }

    private static void sendPing(boolean active) {
        if (target == null) return;
        PacketDistributor.sendToServer(
                new ModNetworking.MonitorSubscribePayload(target, active));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (consumers == 0 || target == null) return;
        if (++ticksSincePing >= PING_TICKS) {
            ticksSincePing = 0;
            sendPing(true);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        channels = List.of();
        target = null;
        consumers = 0;
    }

    private ClientMonitorSnapshot() {
    }
}
