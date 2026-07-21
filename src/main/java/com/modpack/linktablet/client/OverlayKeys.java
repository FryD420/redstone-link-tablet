package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.client.screen.MiniTabletWindow;
import com.modpack.linktablet.client.screen.NoteWindows;
import com.modpack.linktablet.client.screen.OverlayFocusScreen;
import com.modpack.linktablet.client.screen.TabletScreen;
import com.modpack.linktablet.item.TabletItem;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's keybinds (1.7.0 — first ones): registered from {@link
 * ClientSetup}, handled here on the game tick.
 * <ul>
 *   <li><b>Overlay interact</b> (default B): chat-style mouse capture
 *       for the pinned mini-tablet via {@link OverlayFocusScreen} —
 *       only meaningful while something is pinned. The screen itself
 *       handles the closing press.</li>
 *   <li><b>Open tablet</b> (default unbound): opens the full tablet GUI
 *       from anywhere — mainhand tablet first, then offhand, then the
 *       first tablet in the inventory.</li>
 * </ul>
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class OverlayKeys {

    public static final KeyMapping OVERLAY_INTERACT = new KeyMapping(
            "key.linktablet.overlay_interact", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B, "key.categories.linktablet");

    public static final KeyMapping OPEN_TABLET = new KeyMapping(
            "key.linktablet.open_tablet", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_UNKNOWN, "key.categories.linktablet");

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        while (OVERLAY_INTERACT.consumeClick()) {
            // Clicks only arrive with no screen open; the focus screen
            // handles its own closing press.
            if (mc.screen == null && NoteWindows.find(MiniTabletWindow.class) != null) {
                mc.setScreen(new OverlayFocusScreen());
            }
        }

        while (OPEN_TABLET.consumeClick()) {
            if (mc.screen != null) continue;
            if (player.getMainHandItem().getItem() instanceof TabletItem) {
                ClientHooks.openTabletScreen(InteractionHand.MAIN_HAND);
            } else if (player.getOffhandItem().getItem() instanceof TabletItem) {
                ClientHooks.openTabletScreen(InteractionHand.OFF_HAND);
            } else {
                int slot = firstTabletSlot();
                if (slot >= 0) {
                    UISounds.open();
                    mc.setScreen(new TabletScreen(new AppView.Slot(slot)));
                } else {
                    player.displayClientMessage(
                            Component.translatable("message.linktablet.no_tablet"), true);
                }
            }
        }
    }

    private static int firstTabletSlot() {
        LocalPlayer player = Minecraft.getInstance().player;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof TabletItem) return i;
        }
        return -1;
    }

    private OverlayKeys() {
    }
}
