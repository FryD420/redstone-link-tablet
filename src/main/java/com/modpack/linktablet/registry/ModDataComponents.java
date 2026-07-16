package com.modpack.linktablet.registry;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.frequency.SignalApp;
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

    /** The list of apps stored on a tablet. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<SignalApp>>> TABLET_APPS =
            DATA_COMPONENTS.register("tablet_apps", () -> DataComponentType.<List<SignalApp>>builder()
                    .persistent(SignalApp.CODEC.listOf())
                    .networkSynchronized(SignalApp.STREAM_CODEC.apply(ByteBufCodecs.list()))
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

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}
