package com.modpack.linktablet.network;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.registry.ModDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;

/**
 * Client → server payloads that mutate the app list stored on the tablet
 * the player is holding. All edits happen server-side so the item's data
 * component stays authoritative and syncs back through normal inventory
 * sync; the transmitter handler picks up changes on the next tick.
 */
public class ModNetworking {

    public static final int MAX_APPS = 32;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(LinkTabletMod.MOD_ID, path);
    }

    // ------------------------------------------------------------------
    // Payload: toggle an app on/off
    // ------------------------------------------------------------------
    public record ToggleAppPayload(boolean mainHand, int index) implements CustomPacketPayload {
        public static final Type<ToggleAppPayload> TYPE = new Type<>(id("toggle_app"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleAppPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, ToggleAppPayload::mainHand,
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
    public record UpsertAppPayload(boolean mainHand, int index, SignalApp app) implements CustomPacketPayload {
        public static final Type<UpsertAppPayload> TYPE = new Type<>(id("upsert_app"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpsertAppPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, UpsertAppPayload::mainHand,
                        ByteBufCodecs.VAR_INT, UpsertAppPayload::index,
                        SignalApp.STREAM_CODEC, UpsertAppPayload::app,
                        UpsertAppPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------
    // Payload: remove an app
    // ------------------------------------------------------------------
    public record RemoveAppPayload(boolean mainHand, int index) implements CustomPacketPayload {
        public static final Type<RemoveAppPayload> TYPE = new Type<>(id("remove_app"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveAppPayload> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, RemoveAppPayload::mainHand,
                        ByteBufCodecs.VAR_INT, RemoveAppPayload::index,
                        RemoveAppPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ToggleAppPayload.TYPE, ToggleAppPayload.STREAM_CODEC, ModNetworking::handleToggle);
        registrar.playToServer(UpsertAppPayload.TYPE, UpsertAppPayload.STREAM_CODEC, ModNetworking::handleUpsert);
        registrar.playToServer(RemoveAppPayload.TYPE, RemoveAppPayload.STREAM_CODEC, ModNetworking::handleRemove);
    }

    private static ItemStack tablet(Player player, boolean mainHand) {
        ItemStack stack = player.getItemInHand(mainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        return stack.getItem() instanceof TabletItem ? stack : ItemStack.EMPTY;
    }

    private static List<SignalApp> apps(ItemStack stack) {
        return new ArrayList<>(stack.getOrDefault(ModDataComponents.TABLET_APPS.get(), List.of()));
    }

    private static void save(ItemStack stack, List<SignalApp> apps) {
        stack.set(ModDataComponents.TABLET_APPS.get(), List.copyOf(apps));
    }

    private static void handleToggle(ToggleAppPayload payload, IPayloadContext context) {
        ItemStack stack = tablet(context.player(), payload.mainHand());
        if (stack.isEmpty()) return;
        List<SignalApp> apps = apps(stack);
        if (payload.index() < 0 || payload.index() >= apps.size()) return;
        SignalApp app = apps.get(payload.index());
        apps.set(payload.index(), app.withActive(!app.active()));
        save(stack, apps);
    }

    private static void handleUpsert(UpsertAppPayload payload, IPayloadContext context) {
        ItemStack stack = tablet(context.player(), payload.mainHand());
        if (stack.isEmpty()) return;
        SignalApp app = payload.app().sanitized();
        if (app.frequencies().isEmpty()) return;
        List<SignalApp> apps = apps(stack);
        if (payload.index() == -1) {
            if (apps.size() >= MAX_APPS) return;
            apps.add(app);
        } else {
            if (payload.index() < 0 || payload.index() >= apps.size()) return;
            apps.set(payload.index(), app);
        }
        save(stack, apps);
    }

    private static void handleRemove(RemoveAppPayload payload, IPayloadContext context) {
        ItemStack stack = tablet(context.player(), payload.mainHand());
        if (stack.isEmpty()) return;
        List<SignalApp> apps = apps(stack);
        if (payload.index() < 0 || payload.index() >= apps.size()) return;
        apps.remove(payload.index());
        save(stack, apps);
    }
}
