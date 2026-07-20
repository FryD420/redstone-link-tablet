package com.modpack.linktablet.client;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.client.screen.MiniTabletWindow;
import com.modpack.linktablet.client.screen.NoteWindows;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The pinned-overlay state (1.7.0): which tablet the {@link
 * MiniTabletWindow} shows, persisted across sessions in {@link
 * ClientPrefs} ("slot:&lt;n&gt;" / "block:&lt;x&gt;,&lt;y&gt;,&lt;z&gt;").
 * Pinning a held tablet stores its inventory SLOT, not the hand — the
 * whole point is acting on the tablet while holding a pickaxe.
 *
 * <p>Restore waits {@link #RESTORE_GRACE_TICKS} after world join so the
 * server has synced the inventory — otherwise a slot pin would look
 * orphaned and self-clear on every login.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class OverlayPin {

    private static final int RESTORE_GRACE_TICKS = 40;

    private static int ticksInWorld = 0;
    private static boolean restoredThisSession = false;

    // ---- API (TabletScreen's pin button) -----------------------------

    /** Whether the given GUI view is the currently pinned tablet. */
    public static boolean isPinned(AppView guiView) {
        MiniTabletWindow window = NoteWindows.find(MiniTabletWindow.class);
        if (window == null) return false;
        return window.view().target().equals(effective(guiView).target());
    }

    /** Pins (or re-pins) the given tablet; replaces any existing pin. */
    public static void pin(AppView guiView) {
        MiniTabletWindow existing = NoteWindows.find(MiniTabletWindow.class);
        if (existing != null) {
            NoteWindows.remove(existing);
        }
        AppView view = effective(guiView);
        persist(view);
        NoteWindows.add(new MiniTabletWindow(view));
    }

    /** Unpins from the GUI button (the window's X calls onClose itself). */
    public static void unpin() {
        MiniTabletWindow window = NoteWindows.find(MiniTabletWindow.class);
        if (window != null) {
            NoteWindows.remove(window);
        }
        clear();
    }

    /**
     * A Hand view pinned as-is would track whatever the player holds
     * next; convert it to the underlying inventory slot instead.
     */
    private static AppView effective(AppView guiView) {
        if (!(guiView instanceof AppView.Hand hand)) return guiView;
        Minecraft mc = Minecraft.getInstance();
        int slot = hand.hand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? mc.player.getInventory().selected
                : Inventory.SLOT_OFFHAND;
        return new AppView.Slot(slot);
    }

    // ---- Persistence -------------------------------------------------

    public static void persist(AppView view) {
        if (view instanceof AppView.Slot slot) {
            ClientPrefs.setOverlayPin("slot:" + slot.slot());
        } else if (view instanceof AppView.Block block) {
            BlockPos p = block.pos();
            ClientPrefs.setOverlayPin("block:" + p.getX() + "," + p.getY() + "," + p.getZ());
        }
    }

    public static void clear() {
        ClientPrefs.setOverlayPin("");
    }

    private static AppView parse(String descriptor) {
        try {
            if (descriptor.startsWith("slot:")) {
                return new AppView.Slot(Integer.parseInt(descriptor.substring(5)));
            }
            if (descriptor.startsWith("block:")) {
                String[] parts = descriptor.substring(6).split(",");
                return new AppView.Block(new BlockPos(
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])));
            }
        } catch (RuntimeException ignored) {
            // Malformed prefs line — treat as no pin
        }
        return null;
    }

    // ---- Session restore ---------------------------------------------

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            ticksInWorld = 0;
            restoredThisSession = false;
            return;
        }
        if (restoredThisSession || ++ticksInWorld < RESTORE_GRACE_TICKS) return;
        restoredThisSession = true;

        String descriptor = ClientPrefs.overlayPin();
        if (descriptor.isEmpty() || NoteWindows.find(MiniTabletWindow.class) != null) return;
        AppView view = parse(descriptor);
        if (view == null) {
            clear();
            return;
        }
        NoteWindows.add(new MiniTabletWindow(view));
    }

    private OverlayPin() {
    }
}
