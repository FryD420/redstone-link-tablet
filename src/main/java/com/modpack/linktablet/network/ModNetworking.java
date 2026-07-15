package com.modpack.linktablet.network;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.compat.TabletTransmitterHandler;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Client → server payloads that mutate the app list on either a held
 * tablet or a placed tablet block ({@link AppTarget} says which). All
 * edits happen server-side so the stored data stays authoritative and
 * syncs back through normal inventory / block-entity sync.
 */
public class ModNetworking {

    public static final int MAX_APPS = 32;

    /** How close a player must be to edit a placed tablet (squared). */
    private static final double MAX_BLOCK_DISTANCE_SQ = 64.0;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID, path);
    }

    // ------------------------------------------------------------------
    // Target: a tablet in a hand, or a placed tablet block
    // ------------------------------------------------------------------
    public record AppTarget(boolean mainHand, Optional<BlockPos> pos) {
        public static final StreamCodec<RegistryFriendlyByteBuf, AppTarget> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, AppTarget::mainHand,
                        ByteBufCodecs.optional(BlockPos.STREAM_CODEC), AppTarget::pos,
                        AppTarget::new);

        public static AppTarget ofHand(InteractionHand hand) {
            return new AppTarget(hand == InteractionHand.MAIN_HAND, Optional.empty());
        }

        public static AppTarget ofBlock(BlockPos pos) {
            return new AppTarget(true, Optional.of(pos));
        }
    }

    /** Server-side handle on wherever the apps live. */
    private interface AppHost {
        List<SignalApp> apps();

        void save(List<SignalApp> apps);
    }

    @Nullable
    private static AppHost resolve(Player player, AppTarget target) {
        if (target.pos().isPresent()) {
            BlockPos pos = target.pos().get();
            if (!player.level().isLoaded(pos)) return null;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    > MAX_BLOCK_DISTANCE_SQ) return null;
            if (!(player.level().getBlockEntity(pos) instanceof TabletBlockEntity be)) return null;
            return new AppHost() {
                @Override
                public List<SignalApp> apps() {
                    return new ArrayList<>(be.getApps());
                }

                @Override
                public void save(List<SignalApp> apps) {
                    be.setApps(apps);
                }
            };
        }
        ItemStack stack = player.getItemInHand(
                target.mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (!(stack.getItem() instanceof TabletItem)) return null;
        return new AppHost() {
            @Override
            public List<SignalApp> apps() {
                return new ArrayList<>(stack.getOrDefault(ModDataComponents.TABLET_APPS.get(), List.of()));
            }

            @Override
            public void save(List<SignalApp> apps) {
                stack.set(ModDataComponents.TABLET_APPS.get(), List.copyOf(apps));
            }
        };
    }

    // ------------------------------------------------------------------
    // Payload: toggle an app on/off
    // ------------------------------------------------------------------
    public record ToggleAppPayload(AppTarget target, int index) implements CustomPacketPayload {
        public static final Type<ToggleAppPayload> TYPE = new Type<>(id("toggle_app"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleAppPayload> STREAM_CODEC =
                StreamCodec.composite(
                        AppTarget.STREAM_CODEC, ToggleAppPayload::target,
                        ByteBufCodecs.VAR_INT, ToggleAppPayload::index,
                        ToggleAppPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: add (index == -1) or overwrite (index >= 0) an app
    // ------------------------------------------------------------------
    public record UpsertAppPayload(AppTarget target, int index, SignalApp app) implements CustomPacketPayload {
        public static final Type<UpsertAppPayload> TYPE = new Type<>(id("upsert_app"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpsertAppPayload> STREAM_CODEC =
                StreamCodec.composite(
                        AppTarget.STREAM_CODEC, UpsertAppPayload::target,
                        ByteBufCodecs.VAR_INT, UpsertAppPayload::index,
                        SignalApp.STREAM_CODEC, UpsertAppPayload::app,
                        UpsertAppPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: press (held=true) or release (held=false) a momentary app
    // ------------------------------------------------------------------
    public record MomentaryAppPayload(AppTarget target, int index, boolean held) implements CustomPacketPayload {
        public static final Type<MomentaryAppPayload> TYPE = new Type<>(id("momentary_app"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MomentaryAppPayload> STREAM_CODEC =
                StreamCodec.composite(
                        AppTarget.STREAM_CODEC, MomentaryAppPayload::target,
                        ByteBufCodecs.VAR_INT, MomentaryAppPayload::index,
                        ByteBufCodecs.BOOL, MomentaryAppPayload::held,
                        MomentaryAppPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: move an app from one position to another
    // ------------------------------------------------------------------
    public record ReorderAppPayload(AppTarget target, int from, int to) implements CustomPacketPayload {
        public static final Type<ReorderAppPayload> TYPE = new Type<>(id("reorder_app"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ReorderAppPayload> STREAM_CODEC =
                StreamCodec.composite(
                        AppTarget.STREAM_CODEC, ReorderAppPayload::target,
                        ByteBufCodecs.VAR_INT, ReorderAppPayload::from,
                        ByteBufCodecs.VAR_INT, ReorderAppPayload::to,
                        ReorderAppPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: set the physical mini-screen layout (grid or switch list)
    // ------------------------------------------------------------------
    public record ScreenLayoutPayload(AppTarget target, boolean list) implements CustomPacketPayload {
        public static final Type<ScreenLayoutPayload> TYPE = new Type<>(id("screen_layout"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ScreenLayoutPayload> STREAM_CODEC =
                StreamCodec.composite(
                        AppTarget.STREAM_CODEC, ScreenLayoutPayload::target,
                        ByteBufCodecs.BOOL, ScreenLayoutPayload::list,
                        ScreenLayoutPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: remove an app
    // ------------------------------------------------------------------
    public record RemoveAppPayload(AppTarget target, int index) implements CustomPacketPayload {
        public static final Type<RemoveAppPayload> TYPE = new Type<>(id("remove_app"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveAppPayload> STREAM_CODEC =
                StreamCodec.composite(
                        AppTarget.STREAM_CODEC, RemoveAppPayload::target,
                        ByteBufCodecs.VAR_INT, RemoveAppPayload::index,
                        RemoveAppPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("4");
        registrar.playToServer(ToggleAppPayload.TYPE, ToggleAppPayload.STREAM_CODEC, ModNetworking::handleToggle);
        registrar.playToServer(MomentaryAppPayload.TYPE, MomentaryAppPayload.STREAM_CODEC, ModNetworking::handleMomentary);
        registrar.playToServer(UpsertAppPayload.TYPE, UpsertAppPayload.STREAM_CODEC, ModNetworking::handleUpsert);
        registrar.playToServer(ReorderAppPayload.TYPE, ReorderAppPayload.STREAM_CODEC, ModNetworking::handleReorder);
        registrar.playToServer(ScreenLayoutPayload.TYPE, ScreenLayoutPayload.STREAM_CODEC, ModNetworking::handleScreenLayout);
        registrar.playToServer(RemoveAppPayload.TYPE, RemoveAppPayload.STREAM_CODEC, ModNetworking::handleRemove);
    }

    private static void playClick(Player player, AppTarget target, boolean on) {
        playToggleClick(player.level(), player, target.pos().orElse(player.blockPosition()), on);
    }

    /**
     * The faint toggle click. {@code excluded} skips a player who already
     * heard a client-side UI sound; pass null to include everyone (e.g.
     * tapping an app pip directly on a placed tablet's screen).
     */
    public static void playToggleClick(Level level, @Nullable Player excluded, BlockPos pos, boolean on) {
        level.playSound(excluded, pos,
                on ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
                SoundSource.PLAYERS, 0.3F, on ? 1.6F : 1.3F);
    }

    private static void handleToggle(ToggleAppPayload payload, IPayloadContext context) {
        Player player = context.player();
        AppHost host = resolve(player, payload.target());
        if (host == null) return;
        List<SignalApp> apps = host.apps();
        if (payload.index() < 0 || payload.index() >= apps.size()) return;
        SignalApp app = apps.get(payload.index());
        if (app.momentary()) return; // momentary apps use MomentaryAppPayload
        boolean nowActive = !app.active();
        apps.set(payload.index(), app.withActive(nowActive));
        host.save(apps);

        // Faint click other nearby players can hear (the toggling player
        // is excluded — they already got the UI sound client-side).
        playClick(player, payload.target(), nowActive);
    }

    private static void handleMomentary(MomentaryAppPayload payload, IPayloadContext context) {
        Player player = context.player();
        AppHost host = resolve(player, payload.target());
        if (host == null) return;
        BlockPos holdPos = payload.target().pos().orElse(null);
        if (payload.held()) {
            List<SignalApp> apps = host.apps();
            if (payload.index() < 0 || payload.index() >= apps.size()) return;
            SignalApp app = apps.get(payload.index());
            if (!app.momentary()) return;
            TabletTransmitterHandler.setHeld(player, payload.target().mainHand(), holdPos,
                    payload.index(), app.frequencies(), app.strength());
        } else {
            // Releases always clear, even if the list changed under the
            // press (a remove/reorder mid-hold must never leave the
            // transmitter stuck on).
            TabletTransmitterHandler.clearHeld(player, payload.target().mainHand(), holdPos, payload.index());
        }
        playClick(player, payload.target(), payload.held());
    }

    private static void handleUpsert(UpsertAppPayload payload, IPayloadContext context) {
        AppHost host = resolve(context.player(), payload.target());
        if (host == null) return;
        SignalApp app = payload.app().sanitized();
        if (app.frequencies().isEmpty()) return;
        List<SignalApp> apps = host.apps();
        if (payload.index() == -1) {
            if (apps.size() >= MAX_APPS) return;
            apps.add(app);
        } else {
            if (payload.index() < 0 || payload.index() >= apps.size()) return;
            apps.set(payload.index(), app);
        }
        host.save(apps);
    }

    private static void handleReorder(ReorderAppPayload payload, IPayloadContext context) {
        Player player = context.player();
        AppHost host = resolve(player, payload.target());
        if (host == null) return;
        List<SignalApp> apps = host.apps();
        int from = payload.from();
        int to = payload.to();
        if (from < 0 || from >= apps.size() || to < 0 || to >= apps.size() || from == to) return;
        apps.add(to, apps.remove(from));
        host.save(apps);

        // Index-keyed momentary holds on this tablet may now point at the
        // wrong app; drop them (release packets are self-healing).
        TabletTransmitterHandler.clearHeldForTarget(player,
                payload.target().mainHand(), payload.target().pos().orElse(null));
    }

    private static void handleScreenLayout(ScreenLayoutPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (payload.target().pos().isPresent()) {
            BlockPos pos = payload.target().pos().get();
            if (!player.level().isLoaded(pos)) return;
            if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                    > MAX_BLOCK_DISTANCE_SQ) return;
            if (player.level().getBlockEntity(pos) instanceof TabletBlockEntity be) {
                be.setScreenList(payload.list());
            }
            return;
        }
        ItemStack stack = player.getItemInHand(
                payload.target().mainHand() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        if (stack.getItem() instanceof TabletItem) {
            if (payload.list()) {
                stack.set(ModDataComponents.SCREEN_LIST.get(), true);
            } else {
                stack.remove(ModDataComponents.SCREEN_LIST.get());
            }
        }
    }

    private static void handleRemove(RemoveAppPayload payload, IPayloadContext context) {
        AppHost host = resolve(context.player(), payload.target());
        if (host == null) return;
        List<SignalApp> apps = host.apps();
        if (payload.index() < 0 || payload.index() >= apps.size()) return;
        apps.remove(payload.index());
        host.save(apps);
    }
}
