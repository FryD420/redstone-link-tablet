package com.modpack.linktablet.client;

import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client-side view of wherever the tablet's apps live: the stack in a
 * hand, or a placed tablet block. The screens read apps through this and
 * stamp its {@link ModNetworking.AppTarget} onto every payload.
 */
public sealed interface AppView {

    List<SignalApp> apps();

    ModNetworking.AppTarget target();

    ScreenTheme theme();

    /** Add-time app cap; merged surfaces scale it (32 per member). */
    default int maxApps() {
        return ModNetworking.MAX_APPS;
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

    record Hand(InteractionHand hand) implements AppView {
        @Override
        public List<SignalApp> apps() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return List.of();
            ItemStack stack = mc.player.getItemInHand(hand);
            return stack.getOrDefault(ModDataComponents.TABLET_APPS.get(), List.of());
        }

        @Override
        public ModNetworking.AppTarget target() {
            return ModNetworking.AppTarget.ofHand(hand);
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
    }

    /**
     * Tablet in an inventory slot — the pinned overlay's item binding
     * (1.7.0): unlike {@link Hand}, it keeps working while the player
     * mines with something else. Callers re-validate the slot (see the
     * overlay's self-heal) — an empty or foreign stack reads as no apps.
     */
    record Slot(int slot) implements AppView {
        @Override
        public List<SignalApp> apps() {
            ItemStack stack = stack();
            return stack.getOrDefault(ModDataComponents.TABLET_APPS.get(), List.of());
        }

        @Override
        public ModNetworking.AppTarget target() {
            return ModNetworking.AppTarget.ofSlot(slot);
        }

        @Override
        public ScreenTheme theme() {
            return stack().getOrDefault(ModDataComponents.THEME.get(), ScreenTheme.DARK);
        }

        @Override
        public net.minecraft.network.chat.Component customName() {
            return stack().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
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

    record Block(BlockPos pos) implements AppView {
        @Override
        public List<SignalApp> apps() {
            TabletBlockEntity be = resolved();
            return be != null ? be.getApps() : List.of();
        }

        @Override
        public ModNetworking.AppTarget target() {
            // Target the CONTROLLER: this single redirect self-heals
            // every consumer (GUI, pinned overlay) across merges and
            // splits — the view re-resolves on every read.
            TabletBlockEntity be = resolved();
            return ModNetworking.AppTarget.ofBlock(be != null ? be.getBlockPos() : pos);
        }

        @Override
        public ScreenTheme theme() {
            TabletBlockEntity be = resolved();
            return be != null ? be.getTheme() : ScreenTheme.DARK;
        }

        @Override
        public int maxApps() {
            TabletBlockEntity be = resolved();
            return be != null ? be.maxApps() : ModNetworking.MAX_APPS;
        }

        @Override
        public net.minecraft.network.chat.Component customName() {
            TabletBlockEntity be = resolved();
            return be != null ? be.getCustomName() : null;
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
