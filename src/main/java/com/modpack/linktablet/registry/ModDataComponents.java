package com.modpack.linktablet.registry;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.frequency.SignalApp;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
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

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}
