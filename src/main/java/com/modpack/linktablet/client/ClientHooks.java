package com.modpack.linktablet.client;

import com.modpack.linktablet.client.screen.TabletScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;

/**
 * Client-only entry points. Only ever invoked from code paths guarded by
 * {@code level.isClientSide}, so this class is never loaded on a
 * dedicated server.
 */
public class ClientHooks {

    public static void openTabletScreen(InteractionHand hand) {
        UISounds.open();
        Minecraft.getInstance().setScreen(new TabletScreen(hand));
    }
}
