package com.modpack.linktablet.client;

import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client-side view of wherever the tablet's signals live: the stack in a
 * hand, or a placed tablet block. The screens read signals through this and
 * stamp its {@link ModNetworking.SignalTarget} onto every payload.
 */
public sealed interface SignalView {

    List<Signal> signals();

    ModNetworking.SignalTarget target();

    ScreenTheme theme();

    /** Add-time signal cap; merged surfaces scale it (32 per member). */
    default int maxSignals() {
        return ModNetworking.MAX_SIGNALS;
    }

    /** The tablet's home-screen roster (1.10.0), tile order; absent
     * data reads as {@link com.modpack.linktablet.Programs#DEFAULT_HOME}. */
    default List<com.modpack.linktablet.api.TabletProgram> homeApps() {
        return com.modpack.linktablet.Programs.DEFAULT_HOME;
    }

    /** The tablet's gauges (1.10.0 OS suite); absent data reads empty. */
    default List<com.modpack.linktablet.frequency.Gauge> gauges() {
        return List.of();
    }

    /**
     * Live reading for one of this tablet's gauges, 0–15. Carried
     * tablets read the player's own receiver snapshot
     * ({@link ClientGaugeReadings}); placed tablets read the BLOCK's
     * synced receivers — its dial shows what a link at the block hears,
     * not at the viewer.
     */
    default int gaugeReading(int index) {
        List<com.modpack.linktablet.frequency.Gauge> gauges = gauges();
        if (index < 0 || index >= gauges.size()) return 0;
        return ClientGaugeReadings.strength(gauges.get(index).frequency());
    }

    /** Frequency Monitor probe channels (1.11.0, multi-probe); absent
     * data reads an empty list. */
    default List<com.modpack.linktablet.frequency.Frequency> monitorProbes() {
        return List.of();
    }

    /** Custom (anvil) name of the tablet, or null when unnamed (1.8.0). */
    @org.jetbrains.annotations.Nullable
    default net.minecraft.network.chat.Component customName() {
        return null;
    }

    /** GUI title: the tablet's own name when it has one. */
    default net.minecraft.network.chat.Component displayName() {
        net.minecraft.network.chat.Component custom = customName();
        return custom != null ? custom
                : net.minecraft.network.chat.Component.translatable("gui.linktablet.tablet.title");
    }

    record Hand(InteractionHand hand) implements SignalView {
        @Override
        public List<Signal> signals() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return List.of();
            ItemStack stack = mc.player.getItemInHand(hand);
            return stack.getOrDefault(ModDataComponents.TABLET_SIGNALS.get(), List.of());
        }

        @Override
        public ModNetworking.SignalTarget target() {
            return ModNetworking.SignalTarget.ofHand(hand);
        }

        @Override
        public ScreenTheme theme() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return ScreenTheme.DARK;
            return mc.player.getItemInHand(hand)
                    .getOrDefault(ModDataComponents.THEME.get(), ScreenTheme.DARK);
        }

        @Override
        public net.minecraft.network.chat.Component customName() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player == null ? null : mc.player.getItemInHand(hand)
                    .get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        }

        @Override
        public List<com.modpack.linktablet.api.TabletProgram> homeApps() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player == null ? com.modpack.linktablet.Programs.DEFAULT_HOME
                    : com.modpack.linktablet.Programs.fromKeys(mc.player.getItemInHand(hand)
                            .get(ModDataComponents.HOME_APPS.get()));
        }

        @Override
        public List<com.modpack.linktablet.frequency.Gauge> gauges() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return List.of();
            return mc.player.getItemInHand(hand)
                    .getOrDefault(ModDataComponents.TABLET_GAUGES.get(), List.of());
        }

        @Override
        public List<com.modpack.linktablet.frequency.Frequency> monitorProbes() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return List.of();
            return mc.player.getItemInHand(hand)
                    .getOrDefault(ModDataComponents.MONITOR_PROBE.get(), List.of());
        }
    }

    /**
     * Tablet in an inventory slot — the pinned overlay's item binding
     * (1.7.0): unlike {@link Hand}, it keeps working while the player
     * mines with something else. Callers re-validate the slot (see the
     * overlay's self-heal) — an empty or foreign stack reads as no signals.
     */
    record Slot(int slot) implements SignalView {
        @Override
        public List<Signal> signals() {
            ItemStack stack = stack();
            return stack.getOrDefault(ModDataComponents.TABLET_SIGNALS.get(), List.of());
        }

        @Override
        public ModNetworking.SignalTarget target() {
            return ModNetworking.SignalTarget.ofSlot(slot);
        }

        @Override
        public ScreenTheme theme() {
            return stack().getOrDefault(ModDataComponents.THEME.get(), ScreenTheme.DARK);
        }

        @Override
        public net.minecraft.network.chat.Component customName() {
            return stack().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        }

        @Override
        public List<com.modpack.linktablet.api.TabletProgram> homeApps() {
            return com.modpack.linktablet.Programs.fromKeys(
                    stack().get(ModDataComponents.HOME_APPS.get()));
        }

        @Override
        public List<com.modpack.linktablet.frequency.Gauge> gauges() {
            return stack().getOrDefault(ModDataComponents.TABLET_GAUGES.get(), List.of());
        }

        @Override
        public List<com.modpack.linktablet.frequency.Frequency> monitorProbes() {
            return stack().getOrDefault(ModDataComponents.MONITOR_PROBE.get(), List.of());
        }

        public ItemStack stack() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || slot < 0
                    || slot >= mc.player.getInventory().getContainerSize()) {
                return ItemStack.EMPTY;
            }
            return mc.player.getInventory().getItem(slot);
        }
    }

    record Block(BlockPos pos) implements SignalView {
        @Override
        public List<Signal> signals() {
            TabletBlockEntity be = resolved();
            return be != null ? be.getSignals() : List.of();
        }

        @Override
        public ModNetworking.SignalTarget target() {
            // Target the CONTROLLER: this single redirect self-heals
            // every consumer (GUI, pinned overlay) across merges and
            // splits — the view re-resolves on every read.
            TabletBlockEntity be = resolved();
            return ModNetworking.SignalTarget.ofBlock(be != null ? be.getBlockPos() : pos);
        }

        @Override
        public ScreenTheme theme() {
            TabletBlockEntity be = resolved();
            return be != null ? be.getTheme() : ScreenTheme.DARK;
        }

        @Override
        public int maxSignals() {
            TabletBlockEntity be = resolved();
            return be != null ? be.maxSignals() : ModNetworking.MAX_SIGNALS;
        }

        @Override
        public net.minecraft.network.chat.Component customName() {
            TabletBlockEntity be = resolved();
            return be != null ? be.getCustomName() : null;
        }

        @Override
        public List<com.modpack.linktablet.api.TabletProgram> homeApps() {
            TabletBlockEntity be = resolved();
            return be != null ? be.getHomeApps()
                    : com.modpack.linktablet.Programs.DEFAULT_HOME;
        }

        @Override
        public List<com.modpack.linktablet.frequency.Gauge> gauges() {
            TabletBlockEntity be = resolved();
            return be != null ? be.getGauges() : List.of();
        }

        @Override
        public int gaugeReading(int index) {
            TabletBlockEntity be = resolved();
            return be != null ? be.gaugeReading(index) : 0;
        }

        @Override
        public List<com.modpack.linktablet.frequency.Frequency> monitorProbes() {
            TabletBlockEntity be = resolved();
            return be != null ? be.getMonitorProbes() : List.of();
        }

        /** The BE that owns this position's data (controller when merged). */
        @org.jetbrains.annotations.Nullable
        private TabletBlockEntity resolved() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return null;
            if (!(mc.level.getBlockEntity(pos) instanceof TabletBlockEntity be)) return null;
            return be.resolveController();
        }
    }
}
