package com.modpack.linktablet.network;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.PaintCanvas;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.block.TabletScreenMath;
import com.modpack.linktablet.compat.TabletTransmitterHandler;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.menu.SignalEditMenu;
import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.registry.ModMenus;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client → server payloads that mutate the signal list on either a held
 * tablet or a placed tablet block ({@link SignalTarget} says which). All
 * edits happen server-side so the stored data stays authoritative and
 * syncs back through normal inventory / block-entity sync.
 */
public class ModNetworking {

    public static final int MAX_SIGNALS = 32;

    /** How close a player must be to edit a placed tablet (squared). */
    private static final double MAX_BLOCK_DISTANCE_SQ = 64.0;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID, path);
    }

    // ------------------------------------------------------------------
    // Target: a tablet in a hand, a placed tablet block, or a tablet in
    // an inventory slot (1.7.0 — the pinned overlay acts on tablets the
    // player carries but isn't holding)
    // ------------------------------------------------------------------
    public record SignalTarget(boolean mainHand, Optional<BlockPos> pos, Optional<Integer> slot) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SignalTarget> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, SignalTarget::mainHand,
                        ByteBufCodecs.optional(BlockPos.STREAM_CODEC), SignalTarget::pos,
                        ByteBufCodecs.optional(ByteBufCodecs.VAR_INT), SignalTarget::slot,
                        SignalTarget::new);

        public static SignalTarget ofHand(InteractionHand hand) {
            return new SignalTarget(hand == InteractionHand.MAIN_HAND, Optional.empty(), Optional.empty());
        }

        public static SignalTarget ofBlock(BlockPos pos) {
            return new SignalTarget(true, Optional.of(pos), Optional.empty());
        }

        public static SignalTarget ofSlot(int slot) {
            return new SignalTarget(true, Optional.empty(), Optional.of(slot));
        }
    }

    /** Server-side handle on wherever the signals live. */
    private interface SignalHost {
        List<Signal> signals();

        void save(List<Signal> signals);

        /** Add-time cap: merged surfaces scale it (32 per member). */
        default int maxSignals() {
            return MAX_SIGNALS;
        }
    }

    /**
     * Payload range check, Sable-aware (1.10.1): a physicalized tablet's
     * pos is plot-space while the player is world-space — localize the
     * eye first or every payload for a vehicle tablet fails validation.
     * Identity (and free) for normal tablets.
     */
    private static double tabletDistSqr(Player player, BlockPos pos) {
        net.minecraft.world.phys.Vec3 eye = com.modpack.linktablet.compat.SableCompat
                .localizeNear(player.level(), pos, player.getEyePosition());
        return eye.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    @Nullable
    private static SignalHost resolve(Player player, SignalTarget target) {
        if (target.pos().isPresent()) {
            BlockPos pos = target.pos().get();
            if (!player.level().isLoaded(pos)) return null;
            if (tabletDistSqr(player, pos)
                    > MAX_BLOCK_DISTANCE_SQ) return null;
            if (!(player.level().getBlockEntity(pos) instanceof TabletBlockEntity clicked)) return null;
            // Merged surfaces: edits land on the controller (a stale
            // client may still target a part; the controller is at most
            // 3 blocks further — inside the distance budget's slack)
            TabletBlockEntity be = clicked.resolveController();
            if (be == null) return null;
            return new SignalHost() {
                @Override
                public List<Signal> signals() {
                    return new ArrayList<>(be.getSignals());
                }

                @Override
                public void save(List<Signal> signals) {
                    be.setSignals(signals);
                }

                @Override
                public int maxSignals() {
                    return be.maxSignals();
                }
            };
        }
        ItemStack stack;
        if (target.slot().isPresent()) {
            int slot = target.slot().get();
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) return null;
            stack = player.getInventory().getItem(slot);
        } else {
            stack = player.getItemInHand(
                    target.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        }
        if (!(stack.getItem() instanceof TabletItem)) return null;
        return new SignalHost() {
            @Override
            public List<Signal> signals() {
                return new ArrayList<>(stack.getOrDefault(ModDataComponents.TABLET_SIGNALS.get(), List.of()));
            }

            @Override
            public void save(List<Signal> signals) {
                stack.set(ModDataComponents.TABLET_SIGNALS.get(), List.copyOf(signals));
            }
        };
    }

    /**
     * The tablet stack an item-mode target points at (hand or inventory
     * slot), or EMPTY when it isn't a tablet — for handlers that write
     * item components directly instead of going through {@link SignalHost}.
     */
    private static ItemStack resolveStack(Player player, SignalTarget target) {
        ItemStack stack;
        if (target.slot().isPresent()) {
            int slot = target.slot().get();
            if (slot < 0 || slot >= player.getInventory().getContainerSize()) return ItemStack.EMPTY;
            stack = player.getInventory().getItem(slot);
        } else {
            stack = player.getItemInHand(
                    target.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        }
        return stack.getItem() instanceof TabletItem ? stack : ItemStack.EMPTY;
    }

    // ------------------------------------------------------------------
    // Payload: toggle a signal on/off
    // ------------------------------------------------------------------
    public record ToggleSignalPayload(SignalTarget target, int index) implements CustomPacketPayload {
        public static final Type<ToggleSignalPayload> TYPE = new Type<>(id("toggle_signal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleSignalPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, ToggleSignalPayload::target,
                        ByteBufCodecs.VAR_INT, ToggleSignalPayload::index,
                        ToggleSignalPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: add (index == -1) or overwrite (index >= 0) a signal
    // ------------------------------------------------------------------
    public record UpsertSignalPayload(SignalTarget target, int index, Signal signal) implements CustomPacketPayload {
        public static final Type<UpsertSignalPayload> TYPE = new Type<>(id("upsert_signal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpsertSignalPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, UpsertSignalPayload::target,
                        ByteBufCodecs.VAR_INT, UpsertSignalPayload::index,
                        Signal.STREAM_CODEC, UpsertSignalPayload::signal,
                        UpsertSignalPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: press (held=true) or release (held=false) a momentary signal
    // ------------------------------------------------------------------
    public record MomentarySignalPayload(SignalTarget target, int index, boolean held) implements CustomPacketPayload {
        public static final Type<MomentarySignalPayload> TYPE = new Type<>(id("momentary_signal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MomentarySignalPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, MomentarySignalPayload::target,
                        ByteBufCodecs.VAR_INT, MomentarySignalPayload::index,
                        ByteBufCodecs.BOOL, MomentarySignalPayload::held,
                        MomentarySignalPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: move a signal from one position to another
    // ------------------------------------------------------------------
    public record ReorderSignalPayload(SignalTarget target, int from, int to) implements CustomPacketPayload {
        public static final Type<ReorderSignalPayload> TYPE = new Type<>(id("reorder_signal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReorderSignalPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, ReorderSignalPayload::target,
                        ByteBufCodecs.VAR_INT, ReorderSignalPayload::from,
                        ByteBufCodecs.VAR_INT, ReorderSignalPayload::to,
                        ReorderSignalPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: set the physical mini-screen layout (grid or switch list)
    // ------------------------------------------------------------------
    public record ScreenLayoutPayload(SignalTarget target, boolean list) implements CustomPacketPayload {
        public static final Type<ScreenLayoutPayload> TYPE = new Type<>(id("screen_layout"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScreenLayoutPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, ScreenLayoutPayload::target,
                        ByteBufCodecs.BOOL, ScreenLayoutPayload::list,
                        ScreenLayoutPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: set the tablet's UI theme
    // ------------------------------------------------------------------
    public record SetThemePayload(SignalTarget target, ScreenTheme theme) implements CustomPacketPayload {
        public static final Type<SetThemePayload> TYPE = new Type<>(id("set_theme"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetThemePayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, SetThemePayload::target,
                        ScreenTheme.STREAM_CODEC, SetThemePayload::theme,
                        SetThemePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: remove a signal
    // ------------------------------------------------------------------
    public record RemoveSignalPayload(SignalTarget target, int index) implements CustomPacketPayload {
        public static final Type<RemoveSignalPayload> TYPE = new Type<>(id("remove_signal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveSignalPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, RemoveSignalPayload::target,
                        ByteBufCodecs.VAR_INT, RemoveSignalPayload::index,
                        RemoveSignalPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: set a slider signal's live value (0..15)
    // ------------------------------------------------------------------
    public record SetSliderPayload(SignalTarget target, int index, int value) implements CustomPacketPayload {
        public static final Type<SetSliderPayload> TYPE = new Type<>(id("set_slider"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetSliderPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, SetSliderPayload::target,
                        ByteBufCodecs.VAR_INT, SetSliderPayload::index,
                        ByteBufCodecs.VAR_INT, SetSliderPayload::value,
                        SetSliderPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: tap a Timer signal — starts (or restarts) its timed pulse
    // ------------------------------------------------------------------
    public record TimedSignalPayload(SignalTarget target, int index) implements CustomPacketPayload {
        public static final Type<TimedSignalPayload> TYPE = new Type<>(id("timed_signal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TimedSignalPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, TimedSignalPayload::target,
                        ByteBufCodecs.VAR_INT, TimedSignalPayload::index,
                        TimedSignalPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: set (or clear, note == "") a signal's free-text note
    // ------------------------------------------------------------------
    public record SetNotePayload(SignalTarget target, int index, String note) implements CustomPacketPayload {
        public static final Type<SetNotePayload> TYPE = new Type<>(id("set_note"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetNotePayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, SetNotePayload::target,
                        ByteBufCodecs.VAR_INT, SetNotePayload::index,
                        ByteBufCodecs.stringUtf8(Signal.MAX_NOTE_LENGTH), SetNotePayload::note,
                        SetNotePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: the placed tablet's link toggle — false dissolves the
    // clicked surface and marks its members solo (never auto-merge),
    // true re-links the clicked tablet (block targets only)
    // ------------------------------------------------------------------
    public record SurfaceLinkPayload(SignalTarget target, boolean linked) implements CustomPacketPayload {
        public static final Type<SurfaceLinkPayload> TYPE = new Type<>(id("surface_link"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SurfaceLinkPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, SurfaceLinkPayload::target,
                        ByteBufCodecs.BOOL, SurfaceLinkPayload::linked,
                        SurfaceLinkPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: open the signal edit container menu (server-backed so the
    // editor gets real, vanilla-feeling inventory slots)
    // ------------------------------------------------------------------
    public record OpenEditMenuPayload(SignalEditMenu.EditContext context) implements CustomPacketPayload {
        public static final Type<OpenEditMenuPayload> TYPE = new Type<>(id("open_edit_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenEditMenuPayload> STREAM_CODEC =
                SignalEditMenu.EditContext.STREAM_CODEC.map(OpenEditMenuPayload::new, OpenEditMenuPayload::context);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // Payload: replace the tablet's home-screen roster (1.10.0 settable
    // roster — the launcher's app drawer and tile removal both send it)
    // ------------------------------------------------------------------
    public record SetHomeAppsPayload(SignalTarget target, List<String> keys)
            implements CustomPacketPayload {
        public static final Type<SetHomeAppsPayload> TYPE = new Type<>(id("set_home_apps"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetHomeAppsPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, SetHomeAppsPayload::target,
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)),
                        SetHomeAppsPayload::keys,
                        SetHomeAppsPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Gauges (1.10.0 OS suite): read-only dials with their own data
    // model — see frequency/Gauge. Edits mirror the signal payloads;
    // readings flow the OTHER way (the mod's first playToClient).
    // ------------------------------------------------------------------

    /** Server-side handle on wherever the gauges live (SignalHost's twin). */
    private interface GaugeHost {
        List<com.modpack.linktablet.frequency.Gauge> gauges();

        void save(List<com.modpack.linktablet.frequency.Gauge> gauges);
    }

    @Nullable
    private static GaugeHost resolveGauges(Player player, SignalTarget target) {
        if (target.pos().isPresent()) {
            BlockPos pos = target.pos().get();
            if (!player.level().isLoaded(pos)) return null;
            if (tabletDistSqr(player, pos)
                    > MAX_BLOCK_DISTANCE_SQ) return null;
            if (!(player.level().getBlockEntity(pos) instanceof TabletBlockEntity clicked)) return null;
            TabletBlockEntity be = clicked.resolveController();
            if (be == null) return null;
            return new GaugeHost() {
                @Override
                public List<com.modpack.linktablet.frequency.Gauge> gauges() {
                    return new ArrayList<>(be.getGauges());
                }

                @Override
                public void save(List<com.modpack.linktablet.frequency.Gauge> gauges) {
                    be.setGauges(gauges);
                }
            };
        }
        ItemStack stack = resolveStack(player, target);
        if (stack.isEmpty()) return null;
        return new GaugeHost() {
            @Override
            public List<com.modpack.linktablet.frequency.Gauge> gauges() {
                return new ArrayList<>(stack.getOrDefault(
                        ModDataComponents.TABLET_GAUGES.get(), List.of()));
            }

            @Override
            public void save(List<com.modpack.linktablet.frequency.Gauge> gauges) {
                // Empty is never written — untouched tablets stay
                // component-free (the theme idiom)
                if (gauges.isEmpty()) {
                    stack.remove(ModDataComponents.TABLET_GAUGES.get());
                } else {
                    stack.set(ModDataComponents.TABLET_GAUGES.get(), List.copyOf(gauges));
                }
            }
        };
    }

    // ------------------------------------------------------------------
    // Payload: add (index == -1) or overwrite (index >= 0) a gauge
    // ------------------------------------------------------------------
    public record UpsertGaugePayload(SignalTarget target, int index,
                                     com.modpack.linktablet.frequency.Gauge gauge)
            implements CustomPacketPayload {
        public static final Type<UpsertGaugePayload> TYPE = new Type<>(id("upsert_gauge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpsertGaugePayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, UpsertGaugePayload::target,
                        ByteBufCodecs.VAR_INT, UpsertGaugePayload::index,
                        com.modpack.linktablet.frequency.Gauge.STREAM_CODEC, UpsertGaugePayload::gauge,
                        UpsertGaugePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: remove a gauge
    // ------------------------------------------------------------------
    public record RemoveGaugePayload(SignalTarget target, int index) implements CustomPacketPayload {
        public static final Type<RemoveGaugePayload> TYPE = new Type<>(id("remove_gauge"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveGaugePayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, RemoveGaugePayload::target,
                        ByteBufCodecs.VAR_INT, RemoveGaugePayload::index,
                        RemoveGaugePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: server → client gauge readings (the mod's FIRST
    // playToClient). Full snapshot of the player's listened frequencies,
    // cadence-guarded sender-side (TabletReceiverHandler). NEVER written
    // to item components — readings are transient wire state.
    // ------------------------------------------------------------------
    public record GaugeReading(com.modpack.linktablet.frequency.Frequency frequency, int strength) {
        public static final StreamCodec<RegistryFriendlyByteBuf, GaugeReading> STREAM_CODEC =
                StreamCodec.composite(
                        com.modpack.linktablet.frequency.Frequency.STREAM_CODEC, GaugeReading::frequency,
                        ByteBufCodecs.VAR_INT, GaugeReading::strength,
                        GaugeReading::new);
    }

    public record GaugeReadingsPayload(List<GaugeReading> readings) implements CustomPacketPayload {
        public static final Type<GaugeReadingsPayload> TYPE = new Type<>(id("gauge_readings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GaugeReadingsPayload> STREAM_CODEC =
                GaugeReading.STREAM_CODEC.apply(ByteBufCodecs.list(64))
                        .map(GaugeReadingsPayload::new, GaugeReadingsPayload::readings);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: point a placed tablet's SCREEN at a program (1.10.0 user
    // feedback: the kiosk face follows GUI navigation — opening Clock in
    // a block-bound GUI switches the wall screen too, and Home brings
    // both back). Block targets only; held/slot GUIs resume via the
    // client pref instead.
    // ------------------------------------------------------------------
    public record SetProgramPayload(SignalTarget target, String programKey) implements CustomPacketPayload {
        public static final Type<SetProgramPayload> TYPE = new Type<>(id("set_program"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetProgramPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, SetProgramPayload::target,
                        ByteBufCodecs.STRING_UTF8, SetProgramPayload::programKey,
                        SetProgramPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Frequency Monitor (1.11.0): read-only snapshots of Create's link
    // network. Members are classified SERVER-side; labels travel as
    // Components so block names localize on the client.
    // ------------------------------------------------------------------
    public static final byte MEMBER_LINK_BLOCK = 0;
    public static final byte MEMBER_PLACED_TABLET = 1;
    public static final byte MEMBER_PLAYER_TABLET = 2;
    public static final byte MEMBER_OTHER = 3;

    public record MonitorMember(byte type, Component label, BlockPos pos, int strength,
                                boolean listening, boolean inRange) {
        public static final StreamCodec<RegistryFriendlyByteBuf, MonitorMember> STREAM_CODEC =
                StreamCodec.of((buf, m) -> {
                    buf.writeByte(m.type());
                    net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC
                            .encode(buf, m.label());
                    BlockPos.STREAM_CODEC.encode(buf, m.pos());
                    buf.writeVarInt(m.strength());
                    buf.writeBoolean(m.listening());
                    buf.writeBoolean(m.inRange());
                }, buf -> new MonitorMember(
                        buf.readByte(),
                        net.minecraft.network.chat.ComponentSerialization.TRUSTED_STREAM_CODEC
                                .decode(buf),
                        BlockPos.STREAM_CODEC.decode(buf),
                        buf.readVarInt(),
                        buf.readBoolean(),
                        buf.readBoolean()));
    }

    public record MonitorChannel(com.modpack.linktablet.frequency.Frequency frequency,
                                 List<MonitorMember> members) {
        public static final StreamCodec<RegistryFriendlyByteBuf, MonitorChannel> STREAM_CODEC =
                StreamCodec.composite(
                        com.modpack.linktablet.frequency.Frequency.STREAM_CODEC,
                        MonitorChannel::frequency,
                        MonitorMember.STREAM_CODEC.apply(ByteBufCodecs.list(64)),
                        MonitorChannel::members,
                        MonitorChannel::new);
    }

    public record MonitorSubscribePayload(SignalTarget target, boolean active)
            implements CustomPacketPayload {
        public static final Type<MonitorSubscribePayload> TYPE = new Type<>(id("monitor_subscribe"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MonitorSubscribePayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, MonitorSubscribePayload::target,
                        ByteBufCodecs.BOOL, MonitorSubscribePayload::active,
                        MonitorSubscribePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Replaces the tablet's whole probe list (multi-probe, cap
     * {@code MonitorChannels.MAX_PROBES}) — add and remove both send
     * the full list, the SetHomeAppsPayload shape. */
    public record SetProbePayload(SignalTarget target,
                                  List<com.modpack.linktablet.frequency.Frequency> probes)
            implements CustomPacketPayload {
        public static final Type<SetProbePayload> TYPE = new Type<>(id("set_probe"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetProbePayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, SetProbePayload::target,
                        com.modpack.linktablet.frequency.Frequency.STREAM_CODEC
                                .apply(ByteBufCodecs.list(
                                        com.modpack.linktablet.frequency.MonitorChannels.MAX_PROBES)),
                        SetProbePayload::probes,
                        SetProbePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Opens the Monitor's add-probe container menu (1.11.0 second
     * tester round — a real menu is what makes JEI/EMI panels show). */
    public record OpenProbeMenuPayload(SignalTarget target) implements CustomPacketPayload {
        public static final Type<OpenProbeMenuPayload> TYPE = new Type<>(id("open_probe_menu"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenProbeMenuPayload> STREAM_CODEC =
                SignalTarget.STREAM_CODEC.map(OpenProbeMenuPayload::new, OpenProbeMenuPayload::target);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Twitch Chat channel (1.11.0), the SetProbePayload both-target shape. */
    public record SetTwitchChannelPayload(SignalTarget target, String channel)
            implements CustomPacketPayload {
        public static final Type<SetTwitchChannelPayload> TYPE = new Type<>(id("set_twitch_channel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetTwitchChannelPayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, SetTwitchChannelPayload::target,
                        ByteBufCodecs.stringUtf8(25), SetTwitchChannelPayload::channel,
                        SetTwitchChannelPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record MonitorSnapshotPayload(List<MonitorChannel> channels)
            implements CustomPacketPayload {
        public static final Type<MonitorSnapshotPayload> TYPE = new Type<>(id("monitor_snapshot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MonitorSnapshotPayload> STREAM_CODEC =
                MonitorChannel.STREAM_CODEC.apply(ByteBufCodecs.list(64))
                        .map(MonitorSnapshotPayload::new, MonitorSnapshotPayload::channels);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Paint on walls (1.11.0): strokes travel as sparse cell lists in
    // CONTINUOUS surface-space index (block targets) or local canvas
    // index (item targets) — see PaintCanvas for the index math.
    // ------------------------------------------------------------------

    /** One painted cell: index (continuous for block targets, local for
     * item targets) + palette color (0 = blank, 1..MAX_COLOR = paint). */
    public record PaintCell(int index, byte color) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PaintCell> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, PaintCell::index,
                        ByteBufCodecs.BYTE, PaintCell::color,
                        PaintCell::new);
    }

    /** A stroke's sparse cell list (cap 64 — a drag gesture's worth of
     * cells per packet, not the whole canvas). */
    public record PaintStrokePayload(SignalTarget target, List<PaintCell> cells)
            implements CustomPacketPayload {
        public static final Type<PaintStrokePayload> TYPE = new Type<>(id("paint_stroke"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PaintStrokePayload> STREAM_CODEC =
                StreamCodec.composite(
                        SignalTarget.STREAM_CODEC, PaintStrokePayload::target,
                        PaintCell.STREAM_CODEC.apply(ByteBufCodecs.list(64)), PaintStrokePayload::cells,
                        PaintStrokePayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Blanks the whole canvas (item: the whole component; block: every
     * touched member's canvas). */
    public record PaintClearPayload(SignalTarget target) implements CustomPacketPayload {
        public static final Type<PaintClearPayload> TYPE = new Type<>(id("paint_clear"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PaintClearPayload> STREAM_CODEC =
                SignalTarget.STREAM_CODEC.map(PaintClearPayload::new, PaintClearPayload::target);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static void handleSetProgram(SetProgramPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (payload.target().pos().isEmpty()) return;
        BlockPos pos = payload.target().pos().get();
        if (!player.level().isLoaded(pos)) return;
        if (tabletDistSqr(player, pos)
                > MAX_BLOCK_DISTANCE_SQ) return;
        if (player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
            TabletBlockEntity controller = be.resolveController();
            if (controller != null) {
                // byKey sanitizes: unknown keys fall back to the launcher
                controller.setCurrentProgram(
                        com.modpack.linktablet.Programs.byKey(payload.programKey()));
            }
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // "6": 1.3.3 — Frequency's wire format grew from two item IDs to
        // two full ItemStacks (component-bearing frequency items).
        // "7": slider signals — Signal gained the slider flag and
        // SetSliderPayload was added.
        // "8": 1.5.0 — ScreenTheme gained CREATE ("Parchment"), growing
        // SetThemePayload's ordinal domain, and Signal gained
        // sliderMin/sliderMax on the wire.
        // "9": 1.5.2 — ScreenTheme gained AVIONICS.
        // "10": per-signal notes — Signal gained the note string on the
        // wire and SetNotePayload was added.
        // "11": Timer signals — Signal gained timed/pulseTicks on the
        // wire and TimedSignalPayload was added ("10" never shipped; both
        // land in 1.6.0, but each wire growth gets its own fence).
        // "12": 1.7.0 pinned overlay — SignalTarget gained the inventory-
        // slot mode (third optional field on every payload's wire).
        // "13": 1.7.0 solo screens — SurfaceLinkPayload added (the
        // placed tablet's link toggle; both land in 1.7.0).
        // "14": 1.10.0 apps→signals rename — every *_app payload id and
        // the app_edit menu id renamed *_signal (user-approved pairing
        // break; disk keys "apps"/"tablet_apps" stay frozen forever).
        // "15": 1.10.0 gauges — Gauge on the wire (upsert/remove) and
        // GaugeReadingsPayload, the mod's first playToClient (both land
        // in 1.10.0 with "14", but each wire growth gets its own fence).
        // "16": 1.10.0 kiosk-follows-GUI — SetProgramPayload added
        // (still inside the 1.10.0 pairing break).
        // "17": 1.10.0 addon API — programs travel by string KEY:
        // SetHomeAppsPayload carries keys, SetProgramPayload a key
        // (still inside the 1.10.0 pairing break).
        // "18": 1.10.0 signal links — Signal grew linkId + links on the
        // wire (still inside the 1.10.0 pairing break).
        // "19": 1.11.0 Frequency Monitor — MonitorSubscribePayload,
        // SetProbePayload, MonitorSnapshotPayload (second playToClient).
        // "20": 1.11.0 multi-probe — SetProbePayload carries the probe
        // LIST (cap 8; still inside the 1.11.0 pairing break, but each
        // wire growth gets its own fence).
        // "21": 1.11.0 probe editor — OpenProbeMenuPayload added (the
        // add-probe container menu; still inside the 1.11.0 break).
        // "22": 1.11.0 Twitch Chat — SetTwitchChannelPayload added (still
        // inside the 1.11.0 break).
        // "23": 1.11.0 paint on walls — PaintStrokePayload/PaintClearPayload
        // added (still inside the 1.11.0 break).
        PayloadRegistrar registrar = event.registrar("23");
        registrar.playToServer(ToggleSignalPayload.TYPE, ToggleSignalPayload.STREAM_CODEC, ModNetworking::handleToggle);
        registrar.playToServer(MomentarySignalPayload.TYPE, MomentarySignalPayload.STREAM_CODEC, ModNetworking::handleMomentary);
        registrar.playToServer(UpsertSignalPayload.TYPE, UpsertSignalPayload.STREAM_CODEC, ModNetworking::handleUpsert);
        registrar.playToServer(ReorderSignalPayload.TYPE, ReorderSignalPayload.STREAM_CODEC, ModNetworking::handleReorder);
        registrar.playToServer(ScreenLayoutPayload.TYPE, ScreenLayoutPayload.STREAM_CODEC, ModNetworking::handleScreenLayout);
        registrar.playToServer(SetThemePayload.TYPE, SetThemePayload.STREAM_CODEC, ModNetworking::handleSetTheme);
        registrar.playToServer(RemoveSignalPayload.TYPE, RemoveSignalPayload.STREAM_CODEC, ModNetworking::handleRemove);
        registrar.playToServer(OpenEditMenuPayload.TYPE, OpenEditMenuPayload.STREAM_CODEC, ModNetworking::handleOpenEditMenu);
        registrar.playToServer(SetSliderPayload.TYPE, SetSliderPayload.STREAM_CODEC, ModNetworking::handleSetSlider);
        registrar.playToServer(SetNotePayload.TYPE, SetNotePayload.STREAM_CODEC, ModNetworking::handleSetNote);
        registrar.playToServer(TimedSignalPayload.TYPE, TimedSignalPayload.STREAM_CODEC, ModNetworking::handleTimed);
        registrar.playToServer(SurfaceLinkPayload.TYPE, SurfaceLinkPayload.STREAM_CODEC, ModNetworking::handleSurfaceLink);
        registrar.playToServer(SetHomeAppsPayload.TYPE, SetHomeAppsPayload.STREAM_CODEC, ModNetworking::handleSetHomeApps);
        registrar.playToServer(UpsertGaugePayload.TYPE, UpsertGaugePayload.STREAM_CODEC, ModNetworking::handleUpsertGauge);
        registrar.playToServer(RemoveGaugePayload.TYPE, RemoveGaugePayload.STREAM_CODEC, ModNetworking::handleRemoveGauge);
        registrar.playToClient(GaugeReadingsPayload.TYPE, GaugeReadingsPayload.STREAM_CODEC, ModNetworking::handleGaugeReadings);
        registrar.playToServer(SetProgramPayload.TYPE, SetProgramPayload.STREAM_CODEC, ModNetworking::handleSetProgram);
        registrar.playToServer(MonitorSubscribePayload.TYPE, MonitorSubscribePayload.STREAM_CODEC,
                com.modpack.linktablet.compat.MonitorScanner::handleSubscribe);
        registrar.playToServer(SetProbePayload.TYPE, SetProbePayload.STREAM_CODEC,
                ModNetworking::handleSetProbe);
        registrar.playToServer(OpenProbeMenuPayload.TYPE, OpenProbeMenuPayload.STREAM_CODEC,
                ModNetworking::handleOpenProbeMenu);
        registrar.playToClient(MonitorSnapshotPayload.TYPE, MonitorSnapshotPayload.STREAM_CODEC,
                ModNetworking::handleMonitorSnapshot);
        registrar.playToServer(SetTwitchChannelPayload.TYPE, SetTwitchChannelPayload.STREAM_CODEC,
                ModNetworking::handleSetTwitchChannel);
        registrar.playToServer(PaintStrokePayload.TYPE, PaintStrokePayload.STREAM_CODEC,
                ModNetworking::handlePaintStroke);
        registrar.playToServer(PaintClearPayload.TYPE, PaintClearPayload.STREAM_CODEC,
                ModNetworking::handlePaintClear);
    }

    /** Mirrors {@link #handleOpenEditMenu}: resolve() carries the whole
     * validation (loaded + range + tablet), then the server opens the
     * PROBE_EDIT container menu. */
    private static void handleOpenProbeMenu(OpenProbeMenuPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (resolve(player, payload.target()) == null) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        SignalEditMenu.EditContext ctx = SignalEditMenu.EditContext.plain(payload.target(), -1);
        serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new SignalEditMenu(ModMenus.PROBE_EDIT.get(), id, inv, ctx),
                        Component.translatable("gui.linktablet.probe_edit.title")),
                buf -> SignalEditMenu.EditContext.STREAM_CODEC.encode(buf, ctx));
    }

    private static void handleSetProbe(SetProbePayload payload, IPayloadContext context) {
        Player player = context.player();
        // Sanitize: drop empties, dedupe by Create-network identity,
        // cap — the fromKeys shape (never trust the client's list)
        List<com.modpack.linktablet.frequency.Frequency> probes = new ArrayList<>();
        for (com.modpack.linktablet.frequency.Frequency probe : payload.probes()) {
            if (probe.isEmpty() || probes.contains(probe)) continue;
            if (probes.size() >= com.modpack.linktablet.frequency.MonitorChannels.MAX_PROBES) break;
            probes.add(probe);
        }
        if (payload.target().pos().isPresent()) {
            BlockPos pos = payload.target().pos().get();
            if (!player.level().isLoaded(pos)) return;
            if (tabletDistSqr(player, pos) > MAX_BLOCK_DISTANCE_SQ) return;
            if (player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
                TabletBlockEntity controller = be.resolveController();
                if (controller != null) {
                    controller.setMonitorProbes(probes);
                }
            }
            return;
        }
        ItemStack stack = resolveStack(player, payload.target());
        if (!stack.isEmpty()) {
            if (probes.isEmpty()) {
                stack.remove(ModDataComponents.MONITOR_PROBE.get());
            } else {
                stack.set(ModDataComponents.MONITOR_PROBE.get(), List.copyOf(probes));
            }
        }
    }

    private static void handleMonitorSnapshot(MonitorSnapshotPayload payload, IPayloadContext context) {
        com.modpack.linktablet.client.ClientMonitorSnapshot.accept(payload.channels());
    }

    /** Twitch channel charset — TwitchChatService.validChannel's rule,
     * copied (that class is client-only). */
    private static final java.util.regex.Pattern TWITCH_CHANNEL =
            java.util.regex.Pattern.compile("[a-z0-9_]{1,25}");

    private static void handleSetTwitchChannel(SetTwitchChannelPayload payload, IPayloadContext context) {
        Player player = context.player();
        String channel = payload.channel().toLowerCase(java.util.Locale.ROOT);
        if (!channel.isEmpty() && !TWITCH_CHANNEL.matcher(channel).matches()) return;
        if (payload.target().pos().isPresent()) {
            BlockPos pos = payload.target().pos().get();
            if (!player.level().isLoaded(pos)) return;
            if (tabletDistSqr(player, pos) > MAX_BLOCK_DISTANCE_SQ) return;
            if (player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
                TabletBlockEntity controller = be.resolveController();
                if (controller != null) controller.setTwitchChannel(channel);
            }
            return;
        }
        ItemStack stack = resolveStack(player, payload.target());
        if (!stack.isEmpty()) {
            if (channel.isEmpty()) {
                stack.remove(ModDataComponents.TWITCH_CHANNEL.get());
            } else {
                stack.set(ModDataComponents.TWITCH_CHANNEL.get(), channel);
            }
        }
    }

    /**
     * The member BE at (dx, dy) SURFACE offset from a controller, or
     * null when unloaded/absent — (0,0) is the controller itself. Mirrors
     * the offset math {@code TabletBlockEntity.updateLit()}'s controller
     * loop and {@code surfaceIntact()} use: {@code screenRight}/
     * {@code screenDown} give the world axes for surface dx/dy under the
     * controller's FACING, so this is NOT plain world-axis arithmetic.
     */
    @Nullable
    private static TabletBlockEntity memberAt(Level level, TabletBlockEntity controller, int dx, int dy) {
        if (dx == 0 && dy == 0) return controller;
        BlockState state = controller.getBlockState();
        BlockPos pos = controller.getBlockPos()
                .relative(TabletScreenMath.screenRight(state), dx)
                .relative(TabletScreenMath.screenDown(state), dy);
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof TabletBlockEntity member ? member : null;
    }

    /** Resolves a stroke's block target down to its controller + surface
     * span, the SetProbePayload/handleSetProbe resolution shape. */
    @Nullable
    private static TabletBlockEntity resolvePaintController(Player player, BlockPos pos) {
        if (!player.level().isLoaded(pos)) return null;
        if (tabletDistSqr(player, pos) > MAX_BLOCK_DISTANCE_SQ) return null;
        if (!(player.level().getBlockEntity(pos) instanceof TabletBlockEntity clicked)) return null;
        return clicked.resolveController();
    }

    private static void handlePaintStroke(PaintStrokePayload payload, IPayloadContext context) {
        Player player = context.player();
        if (payload.target().pos().isPresent()) {
            TabletBlockEntity controller = resolvePaintController(player, payload.target().pos().get());
            if (controller == null) return;
            int surfaceW = controller.getSurfaceW();
            int surfaceH = controller.getSurfaceH();
            int contW = surfaceW * PaintCanvas.COLS;
            int contH = surfaceH * PaintCanvas.ROWS;
            // Group cells by owning member first — one setPaintCanvas
            // (one sync packet) per touched member per stroke, not per cell.
            Map<Long, Map<Integer, Byte>> byMember = new LinkedHashMap<>();
            for (PaintCell cell : payload.cells()) {
                if (cell.color() < 0 || cell.color() > PaintCanvas.MAX_COLOR) continue;
                int index = cell.index();
                if (index < 0 || index >= contW * contH) continue;
                int contX = index % contW;
                int contY = index / contW;
                int dx = PaintCanvas.memberDx(contX);
                int dy = PaintCanvas.memberDy(contY);
                int local = PaintCanvas.localIndex(contX, contY);
                long key = (((long) dx) << 32) | (dy & 0xFFFFFFFFL);
                byMember.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(local, cell.color());
            }
            Level level = player.level();
            for (Map.Entry<Long, Map<Integer, Byte>> entry : byMember.entrySet()) {
                int dx = (int) (entry.getKey() >> 32);
                int dy = (int) (long) entry.getKey();
                TabletBlockEntity member = memberAt(level, controller, dx, dy);
                if (member == null) continue;
                // Clone before mutating — setPaintCanvas stores by
                // reference, and the array we hand it must never be
                // touched again after the call (T1 aliasing rule).
                byte[] updated = member.getPaintCanvas().clone();
                boolean changed = false;
                for (Map.Entry<Integer, Byte> localCell : entry.getValue().entrySet()) {
                    int local = localCell.getKey();
                    byte color = localCell.getValue();
                    if (updated[local] == color) continue;
                    updated[local] = color;
                    changed = true;
                }
                if (changed) member.setPaintCanvas(updated);
            }
            return;
        }
        ItemStack stack = resolveStack(player, payload.target());
        if (stack.isEmpty()) return;
        byte[] canvas = stack.getOrDefault(ModDataComponents.PAINT_CANVAS.get(), PaintCanvas.blank()).clone();
        boolean changed = false;
        for (PaintCell cell : payload.cells()) {
            int index = cell.index();
            byte color = cell.color();
            if (index < 0 || index >= PaintCanvas.CELLS) continue;
            if (color < 0 || color > PaintCanvas.MAX_COLOR) continue;
            if (canvas[index] == color) continue;
            canvas[index] = color;
            changed = true;
        }
        if (!changed) return;
        if (PaintCanvas.isBlank(canvas)) {
            stack.remove(ModDataComponents.PAINT_CANVAS.get());
        } else {
            stack.set(ModDataComponents.PAINT_CANVAS.get(), canvas);
        }
    }

    private static void handlePaintClear(PaintClearPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (payload.target().pos().isPresent()) {
            TabletBlockEntity controller = resolvePaintController(player, payload.target().pos().get());
            if (controller == null) return;
            Level level = player.level();
            for (int dx = 0; dx < controller.getSurfaceW(); dx++) {
                for (int dy = 0; dy < controller.getSurfaceH(); dy++) {
                    TabletBlockEntity member = memberAt(level, controller, dx, dy);
                    if (member != null) member.setPaintCanvas(PaintCanvas.blank());
                }
            }
            return;
        }
        ItemStack stack = resolveStack(player, payload.target());
        if (!stack.isEmpty()) {
            stack.remove(ModDataComponents.PAINT_CANVAS.get());
        }
    }

    private static void handleUpsertGauge(UpsertGaugePayload payload, IPayloadContext context) {
        GaugeHost host = resolveGauges(context.player(), payload.target());
        if (host == null) return;
        com.modpack.linktablet.frequency.Gauge gauge = payload.gauge().sanitized();
        // v1: only LINK gauges exist, and a link gauge needs a channel
        if (gauge.source() == com.modpack.linktablet.frequency.Gauge.Source.LINK
                && gauge.frequency().isEmpty()) return;
        List<com.modpack.linktablet.frequency.Gauge> gauges = host.gauges();
        if (payload.index() == -1) {
            if (gauges.size() >= com.modpack.linktablet.frequency.Gauge.MAX_GAUGES) return;
            gauges.add(gauge);
        } else {
            if (payload.index() < 0 || payload.index() >= gauges.size()) return;
            gauges.set(payload.index(), gauge);
        }
        host.save(gauges);
    }

    private static void handleRemoveGauge(RemoveGaugePayload payload, IPayloadContext context) {
        GaugeHost host = resolveGauges(context.player(), payload.target());
        if (host == null) return;
        List<com.modpack.linktablet.frequency.Gauge> gauges = host.gauges();
        if (payload.index() < 0 || payload.index() >= gauges.size()) return;
        gauges.remove(payload.index());
        host.save(gauges);
    }

    /** Client side: hand the snapshot to the client store. The client
     * class only loads when this body runs — never on a dedicated
     * server (the TabletBlock/ClientHooks idiom). */
    private static void handleGaugeReadings(GaugeReadingsPayload payload, IPayloadContext context) {
        com.modpack.linktablet.client.ClientGaugeReadings.accept(payload.readings());
    }

    /** Same both-target shape as {@link #handleSetTheme}; Programs.fromKeys
     * sanitizes (unknown/launcher keys drop, dedupe, empty → default). */
    private static void handleSetHomeApps(SetHomeAppsPayload payload, IPayloadContext context) {
        Player player = context.player();
        // Sanity cap, above the whole catalog (the wire codec also caps
        // the list at 64)
        if (payload.keys().size() > 64) return;
        List<com.modpack.linktablet.api.TabletProgram> home =
                com.modpack.linktablet.Programs.fromKeys(payload.keys());
        if (payload.target().pos().isPresent()) {
            BlockPos pos = payload.target().pos().get();
            if (!player.level().isLoaded(pos)) return;
            if (tabletDistSqr(player, pos)
                    > MAX_BLOCK_DISTANCE_SQ) return;
            if (player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
                TabletBlockEntity controller = be.resolveController();
                if (controller != null) {
                    controller.setHomeApps(home);
                }
            }
            return;
        }
        ItemStack stack = resolveStack(player, payload.target());
        if (!stack.isEmpty()) {
            // The default roster is never written, so untouched tablets
            // stay component-free (the theme idiom)
            if (home.equals(com.modpack.linktablet.Programs.DEFAULT_HOME)) {
                stack.remove(ModDataComponents.HOME_APPS.get());
            } else {
                stack.set(ModDataComponents.HOME_APPS.get(),
                        com.modpack.linktablet.Programs.keys(home));
            }
        }
    }

    private static void handleSurfaceLink(SurfaceLinkPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (payload.target().pos().isEmpty()) return;
        BlockPos pos = payload.target().pos().get();
        if (!player.level().isLoaded(pos)) return;
        if (tabletDistSqr(player, pos)
                > MAX_BLOCK_DISTANCE_SQ) return;
        if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.modpack.linktablet.block.TabletSurfaceScanner.setLinked(
                    serverLevel, pos, payload.linked());
        }
    }

    private static void handleTimed(TimedSignalPayload payload, IPayloadContext context) {
        Player player = context.player();
        SignalHost host = resolve(player, payload.target());
        if (host == null) return;
        List<Signal> signals = host.signals();
        if (payload.index() < 0 || payload.index() >= signals.size()) return;
        Signal signal = signals.get(payload.index());
        if (!signal.timed()) return;
        TabletTransmitterHandler.startTimed(player, payload.target().mainHand(),
                payload.target().pos().orElse(null), payload.index(),
                signal.frequencies(), signal.strength(), signal.pulseTicks());
        playClick(player, payload.target(), true);
    }

    private static void handleSetNote(SetNotePayload payload, IPayloadContext context) {
        SignalHost host = resolve(context.player(), payload.target());
        if (host == null) return;
        List<Signal> signals = host.signals();
        if (payload.index() < 0 || payload.index() >= signals.size()) return;
        Signal signal = signals.get(payload.index());
        Signal updated = signal.withNote(payload.note());
        if (updated.note().equals(signal.note())) return;
        signals.set(payload.index(), updated);
        host.save(signals);
    }

    private static void handleSetSlider(SetSliderPayload payload, IPayloadContext context) {
        Player player = context.player();
        SignalHost host = resolve(player, payload.target());
        if (host == null) return;
        List<Signal> signals = host.signals();
        if (payload.index() < 0 || payload.index() >= signals.size()) return;
        Signal signal = signals.get(payload.index());
        if (!signal.slider()) return;
        boolean wasOn = signal.strength() > 0;
        Signal updated = signal.withSliderValue(payload.value());
        if (updated.strength() == signal.strength()) return;
        signals.set(payload.index(), updated);
        host.save(signals);
        // Click only on the off↔on edge — not on every drag step
        boolean nowOn = updated.strength() > 0;
        if (wasOn != nowOn) {
            playClick(player, payload.target(), nowOn);
        }
    }

    private static void handleOpenEditMenu(OpenEditMenuPayload payload, IPayloadContext context) {
        Player player = context.player();
        SignalEditMenu.EditContext ctx = payload.context();
        SignalHost host = resolve(player, ctx.target());
        if (host == null) return;
        List<Signal> signals = host.signals();
        if (ctx.index() < -1 || ctx.index() >= signals.size()) return;
        if (ctx.index() == -1 && signals.size() >= host.maxSignals()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        serverPlayer.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new SignalEditMenu(ModMenus.SIGNAL_EDIT.get(), id, inv, ctx),
                        Component.translatable(ctx.index() == -1
                                ? "gui.linktablet.edit_signal.title.new"
                                : "gui.linktablet.edit_signal.title.edit")),
                buf -> SignalEditMenu.EditContext.STREAM_CODEC.encode(buf, ctx));
    }

    private static void playClick(Player player, SignalTarget target, boolean on) {
        playToggleClick(player.level(), player, target.pos().orElse(player.blockPosition()), on);
    }

    /**
     * The faint toggle click. {@code excluded} skips a player who already
     * heard a client-side UI sound; pass null to include everyone (e.g.
     * tapping a signal pip directly on a placed tablet's screen).
     */
    public static void playToggleClick(Level level, @Nullable Player excluded, BlockPos pos, boolean on) {
        level.playSound(excluded, pos,
                on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                SoundSource.PLAYERS, 0.3F, on ? 1.6F : 1.3F);
    }

    private static void handleToggle(ToggleSignalPayload payload, IPayloadContext context) {
        Player player = context.player();
        SignalHost host = resolve(player, payload.target());
        if (host == null) return;
        List<Signal> signals = host.signals();
        if (payload.index() < 0 || payload.index() >= signals.size()) return;
        Signal signal = signals.get(payload.index());
        if (signal.momentary()) return; // momentary signals use MomentarySignalPayload
        if (signal.slider()) return;    // sliders use SetSliderPayload
        if (signal.timed()) return;     // timers use TimedSignalPayload
        boolean nowActive = !signal.active();
        signals.set(payload.index(), signal.withActive(nowActive));
        // Signal links (1.10.0): the tap also drives linked targets —
        // ONE shared helper with TabletBlock's world-tap branch
        com.modpack.linktablet.frequency.SignalLinks.propagate(signals, payload.index(),
                (index, target) -> TabletTransmitterHandler.startTimed(player,
                        payload.target().mainHand(), payload.target().pos().orElse(null),
                        index, target.frequencies(), target.strength(), target.pulseTicks()));
        host.save(signals);

        // Faint click other nearby players can hear (the toggling player
        // is excluded — they already got the UI sound client-side).
        playClick(player, payload.target(), nowActive);
    }

    private static void handleMomentary(MomentarySignalPayload payload, IPayloadContext context) {
        Player player = context.player();
        SignalHost host = resolve(player, payload.target());
        if (host == null) return;
        BlockPos holdPos = payload.target().pos().orElse(null);
        if (payload.held()) {
            List<Signal> signals = host.signals();
            if (payload.index() < 0 || payload.index() >= signals.size()) return;
            Signal signal = signals.get(payload.index());
            if (!signal.momentary()) return;
            TabletTransmitterHandler.setHeld(player, payload.target().mainHand(), holdPos,
                    payload.index(), signal.frequencies(), signal.strength());
        } else {
            // Releases always clear, even if the list changed under the
            // press (a remove/reorder mid-hold must never leave the
            // transmitter stuck on).
            TabletTransmitterHandler.clearHeld(player, payload.target().mainHand(), holdPos, payload.index());
        }
        playClick(player, payload.target(), payload.held());
    }

    private static void handleUpsert(UpsertSignalPayload payload, IPayloadContext context) {
        SignalHost host = resolve(context.player(), payload.target());
        if (host == null) return;
        // Never trust a client-supplied id: the stored signal's id wins
        // (edits), adds start at 0 for the ensure-ids mint below. Id is
        // restored BEFORE sanitized() so self-link pruning sees it.
        List<Signal> signals = host.signals();
        if (payload.index() != -1
                && (payload.index() < 0 || payload.index() >= signals.size())) return;
        int storedId = payload.index() == -1 ? 0 : signals.get(payload.index()).linkId();
        Signal signal = payload.signal().withLinkId(storedId).sanitized();
        // A signal needs a frequency OR links (1.10.0): links-only
        // signals are legitimate scene masters that transmit nothing
        // themselves (empty-frequency rendering is proven — the kiosk
        // launcher tiles are frequency-less synthetic signals)
        if (signal.frequencies().isEmpty() && signal.links().isEmpty()) return;
        if (payload.index() == -1) {
            if (signals.size() >= host.maxSignals()) return;
            signals.add(signal);
        } else {
            signals.set(payload.index(), signal);
        }
        // Ensure-ids pass (1.10.0 links): the ONLY mint site — one edit
        // round retrofits every pre-links signal on the tablet
        com.modpack.linktablet.frequency.SignalLinks.ensureIds(signals,
                context.player().getRandom());
        host.save(signals);
    }

    private static void handleReorder(ReorderSignalPayload payload, IPayloadContext context) {
        Player player = context.player();
        SignalHost host = resolve(player, payload.target());
        if (host == null) return;
        List<Signal> signals = host.signals();
        int from = payload.from();
        int to = payload.to();
        if (from < 0 || from >= signals.size() || to < 0 || to >= signals.size() || from == to) return;
        signals.add(to, signals.remove(from));
        host.save(signals);

        // Index-keyed momentary holds on this tablet may now point at the
        // wrong signal; drop them (release packets are self-healing).
        TabletTransmitterHandler.clearHeldForTarget(player,
                payload.target().mainHand(), payload.target().pos().orElse(null));
    }

    private static void handleScreenLayout(ScreenLayoutPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (payload.target().pos().isPresent()) {
            BlockPos pos = payload.target().pos().get();
            if (!player.level().isLoaded(pos)) return;
            if (tabletDistSqr(player, pos)
                    > MAX_BLOCK_DISTANCE_SQ) return;
            if (player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
                TabletBlockEntity controller = be.resolveController();
                if (controller != null) {
                    controller.setScreenList(payload.list());
                }
            }
            return;
        }
        ItemStack stack = resolveStack(player, payload.target());
        if (!stack.isEmpty()) {
            if (payload.list()) {
                stack.set(ModDataComponents.SCREEN_LIST.get(), true);
            } else {
                stack.remove(ModDataComponents.SCREEN_LIST.get());
            }
        }
    }

    private static void handleSetTheme(SetThemePayload payload, IPayloadContext context) {
        Player player = context.player();
        if (payload.target().pos().isPresent()) {
            BlockPos pos = payload.target().pos().get();
            if (!player.level().isLoaded(pos)) return;
            if (tabletDistSqr(player, pos)
                    > MAX_BLOCK_DISTANCE_SQ) return;
            if (player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
                TabletBlockEntity controller = be.resolveController();
                if (controller != null) {
                    controller.setTheme(payload.theme());
                }
            }
            return;
        }
        ItemStack stack = resolveStack(player, payload.target());
        if (!stack.isEmpty()) {
            // DARK is the default and is never written, so 1.2.x tablets
            // stay component-free.
            if (payload.theme() == ScreenTheme.DARK) {
                stack.remove(ModDataComponents.THEME.get());
            } else {
                stack.set(ModDataComponents.THEME.get(), payload.theme());
            }
        }
    }

    private static void handleRemove(RemoveSignalPayload payload, IPayloadContext context) {
        SignalHost host = resolve(context.player(), payload.target());
        if (host == null) return;
        List<Signal> signals = host.signals();
        if (payload.index() < 0 || payload.index() >= signals.size()) return;
        signals.remove(payload.index());
        host.save(signals);
    }
}
