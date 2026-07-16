package com.modpack.linktablet.client;

import com.modpack.linktablet.client.screen.AppEditScreen;
import com.modpack.linktablet.client.screen.TabletScreen;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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

    /**
     * Right-clicked a Redstone Link with the tablet: open the app editor
     * pre-filled with the link's frequency. If an app already carries
     * that frequency, edit it instead of duplicating.
     */
    public static void openLinkPrefill(InteractionHand hand, Item item1, Item item2) {
        Minecraft mc = Minecraft.getInstance();
        AppView view = new AppView.Hand(hand);
        List<SignalApp> apps = view.apps();
        Frequency freq = Frequency.of(item1, item2);
        TabletScreen parent = new TabletScreen(view);

        for (int i = 0; i < apps.size(); i++) {
            SignalApp app = apps.get(i);
            if (app.frequencies().contains(freq)) {
                mc.player.displayClientMessage(Component.translatable(
                        "message.linktablet.link_already_added", app.name()), true);
                UISounds.open();
                mc.setScreen(new AppEditScreen(parent, i, app));
                return;
            }
        }
        if (apps.size() >= ModNetworking.MAX_APPS) {
            mc.player.displayClientMessage(
                    Component.translatable("message.linktablet.tablet_full"), true);
            return;
        }
        String name = new ItemStack(item1 == net.minecraft.world.item.Items.AIR ? item2 : item1)
                .getHoverName().getString();
        if (name.length() > SignalApp.MAX_NAME_LENGTH) {
            name = name.substring(0, SignalApp.MAX_NAME_LENGTH);
        }
        UISounds.open();
        mc.setScreen(AppEditScreen.withLinkFrequency(parent, item1, item2, name));
    }
}
