package com.modpack.linktablet.client;

import com.modpack.linktablet.client.screen.TabletScreen;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.menu.AppEditMenu;
import com.modpack.linktablet.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Client-only entry points. Only ever invoked from code paths guarded by
 * {@code level.isClientSide}, so this class is never loaded on a
 * dedicated server.
 */
public class ClientHooks {

    public static void openTabletScreen(InteractionHand hand) {
        UISounds.open();
        Minecraft.getInstance().setScreen(new TabletScreen(new AppView.Hand(hand)));
    }

    public static void openTabletBlockScreen(BlockPos pos) {
        UISounds.open();
        Minecraft.getInstance().setScreen(new TabletScreen(new AppView.Block(pos)));
    }

    /** Begins a placed-tablet slider drag (client drives it from here). */
    public static void startBlockSliderDrag(BlockPos pos, int index) {
        BlockSliderDrag.start(pos, index);
    }

    /**
     * Right-clicked a Redstone Link with the tablet: open the app editor
     * pre-filled with the link's frequency (full stacks — components
     * intact). If an app already carries that frequency, edit it instead
     * of duplicating.
     */
    public static void openLinkPrefill(InteractionHand hand, ItemStack stack1, ItemStack stack2) {
        Minecraft mc = Minecraft.getInstance();
        AppView view = new AppView.Hand(hand);
        List<SignalApp> apps = view.apps();
        Frequency freq = Frequency.of(stack1, stack2);
        ModNetworking.AppTarget target = view.target();

        for (int i = 0; i < apps.size(); i++) {
            SignalApp app = apps.get(i);
            if (app.frequencies().contains(freq)) {
                mc.player.displayClientMessage(Component.translatable(
                        "message.linktablet.link_already_added", app.name()), true);
                UISounds.open();
                PacketDistributor.sendToServer(new ModNetworking.OpenEditMenuPayload(
                        AppEditMenu.EditContext.plain(target, i)));
                return;
            }
        }
        if (apps.size() >= ModNetworking.MAX_APPS) {
            mc.player.displayClientMessage(
                    Component.translatable("message.linktablet.tablet_full"), true);
            return;
        }
        String name = (stack1.isEmpty() ? stack2 : stack1).getHoverName().getString();
        if (name.length() > SignalApp.MAX_NAME_LENGTH) {
            name = name.substring(0, SignalApp.MAX_NAME_LENGTH);
        }
        UISounds.open();
        // A full pair prefills the ghost staging slots (the classic flow —
        // Save auto-commits); a half-set link commits its lone-item
        // frequency directly (screen-side), since staging needs both.
        PacketDistributor.sendToServer(new ModNetworking.OpenEditMenuPayload(
                new AppEditMenu.EditContext(target, -1, stack1, stack2, name)));
    }
}
