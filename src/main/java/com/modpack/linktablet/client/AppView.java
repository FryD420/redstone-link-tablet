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
