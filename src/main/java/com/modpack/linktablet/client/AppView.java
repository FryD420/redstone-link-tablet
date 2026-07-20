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
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return List.of();
            return mc.level.getBlockEntity(pos) instanceof TabletBlockEntity be ? be.getApps() : List.of();
        }

        @Override
        public ModNetworking.AppTarget target() {
            return ModNetworking.AppTarget.ofBlock(pos);
        }

        @Override
        public ScreenTheme theme() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return ScreenTheme.DARK;
            return mc.level.getBlockEntity(pos) instanceof TabletBlockEntity be
                    ? be.getTheme() : ScreenTheme.DARK;
        }
    }
}
