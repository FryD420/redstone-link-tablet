package com.modpack.linktablet.registry;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.theme.ScreenTheme;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.DyeColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, LinkTabletMod.MOD_ID);

    /** The list of signals stored on a tablet. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<Signal>>> TABLET_SIGNALS =
            DATA_COMPONENTS.register("tablet_apps", () -> DataComponentType.<List<Signal>>builder()
                    .persistent(Signal.CODEC.listOf())
                    .networkSynchronized(Signal.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());

    /** Dyed case color; absent = the default dark case. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DyeColor>> CASE_COLOR =
            DATA_COMPONENTS.register("case_color", () -> DataComponentType.<DyeColor>builder()
                    .persistent(DyeColor.CODEC)
                    .networkSynchronized(DyeColor.STREAM_CODEC)
                    .build());

    /** Physical mini-screen layout: true = switch list; absent = pip grid. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> SCREEN_LIST =
            DATA_COMPONENTS.register("screen_list", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /** UI theme; absent = {@link ScreenTheme#DARK} (never written). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ScreenTheme>> THEME =
            DATA_COMPONENTS.register("theme", () -> DataComponentType.<ScreenTheme>builder()
                    .persistent(ScreenTheme.CODEC)
                    .networkSynchronized(ScreenTheme.STREAM_CODEC)
                    .build());

    /** Ordered home-screen program KEYS (1.10.0 settable roster; string
     * keys since the addon API so addon programs persist too); absent =
     * Signals only ({@code Programs.DEFAULT_HOME}, never written). The
     * int-list alternative decodes the pre-API dev format forever (the
     * Frequency LEGACY_CODEC precedent) — encode always writes keys. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> HOME_APPS =
            DATA_COMPONENTS.register("home_apps", () -> DataComponentType.<List<String>>builder()
                    .persistent(Codec.either(Codec.STRING.listOf(), Codec.INT.listOf()).xmap(
                            either -> either.map(
                                    keys -> keys,
                                    ids -> com.modpack.linktablet.Programs.keys(
                                            com.modpack.linktablet.Program.fromIds(ids))),
                            com.mojang.datafixers.util.Either::left))
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()))
                    .build());

    /** The list of gauges stored on a tablet (1.10.0 OS suite);
     * absent = none (never written empty). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<com.modpack.linktablet.frequency.Gauge>>> TABLET_GAUGES =
            DATA_COMPONENTS.register("tablet_gauges", () -> DataComponentType.<List<com.modpack.linktablet.frequency.Gauge>>builder()
                    .persistent(com.modpack.linktablet.frequency.Gauge.CODEC.listOf())
                    .networkSynchronized(com.modpack.linktablet.frequency.Gauge.STREAM_CODEC.apply(ByteBufCodecs.list()))
                    .build());

    /** Frequency Monitor probe channels (1.11.0, multi-probe by user
     * decision — cap {@code MonitorChannels.MAX_PROBES}); absent = none
     * (never written empty — the theme idiom). The single-Frequency
     * alternative decodes the brief pre-multi dev format (never
     * released, but dev worlds carry it). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<com.modpack.linktablet.frequency.Frequency>>> MONITOR_PROBE =
            DATA_COMPONENTS.register("monitor_probe", () -> DataComponentType.<List<com.modpack.linktablet.frequency.Frequency>>builder()
                    .persistent(Codec.withAlternative(
                            com.modpack.linktablet.frequency.Frequency.CODEC.listOf(),
                            com.modpack.linktablet.frequency.Frequency.CODEC.xmap(
                                    List::of, list -> list.isEmpty()
                                            ? com.modpack.linktablet.frequency.Frequency.EMPTY
                                            : list.get(0))))
                    .networkSynchronized(com.modpack.linktablet.frequency.Frequency.STREAM_CODEC
                            .apply(ByteBufCodecs.list()))
                    .build());

    /** Twitch Chat channel name (1.11.0); absent = unset, never written
     * empty — the theme idiom. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TWITCH_CHANNEL =
            DATA_COMPONENTS.register("twitch_channel", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /** Paint canvas (1.11.0); absent = blank (never written all-blank —
     * the theme idiom); 280 palette indices, disk form frozen. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<byte[]>> PAINT_CANVAS =
            DATA_COMPONENTS.register("paint_canvas", () -> DataComponentType.<byte[]>builder()
                    .persistent(Codec.BYTE_BUFFER.xmap(buf -> {
                        byte[] array = new byte[buf.remaining()];
                        buf.get(array);
                        return array;
                    }, java.nio.ByteBuffer::wrap))
                    .networkSynchronized(ByteBufCodecs.byteArray(com.modpack.linktablet.PaintCanvas.CELLS))
                    .build());

    /** Placed-screen content rotation, quarter turns CW; absent = 0 (never written). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SCREEN_ROTATION =
            DATA_COMPONENTS.register("screen_rotation", () -> DataComponentType.<Integer>builder()
                    .persistent(Codec.intRange(0, 3))
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    .build());

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}
