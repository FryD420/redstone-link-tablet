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

    // ---- API (the program screens' pin buttons) ----------------------

    /** Whether the given GUI view is pinned showing the given program
     * (1.10.0: the pin is per-app — a clock pin is not a signals pin). */
    public static boolean isPinned(SignalView guiView, com.modpack.linktablet.Program program) {
        MiniTabletWindow window = NoteWindows.find(MiniTabletWindow.class);
        if (window == null) return false;
        return window.program() == program
                && window.view().target().equals(effective(guiView).target());
    }

    /** Signals-program shorthand (the classic 1.7.0 pin). */
    public static boolean isPinned(SignalView guiView) {
        return isPinned(guiView, com.modpack.linktablet.Program.SIGNALS);
    }

    /** Pins (or re-pins) the given tablet showing the given program;
     * replaces any existing pin — there is ONE overlay window. */
    public static void pin(SignalView guiView, com.modpack.linktablet.Program program) {
        MiniTabletWindow existing = NoteWindows.find(MiniTabletWindow.class);
        if (existing != null) {
            NoteWindows.remove(existing);
        }
        SignalView view = effective(guiView);
        persist(view, program);
        NoteWindows.add(new MiniTabletWindow(view, program));
    }

    /** Signals-program shorthand (the classic 1.7.0 pin). */
    public static void pin(SignalView guiView) {
        pin(guiView, com.modpack.linktablet.Program.SIGNALS);
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
    private static SignalView effective(SignalView guiView) {
        if (!(guiView instanceof SignalView.Hand hand)) return guiView;
        Minecraft mc = Minecraft.getInstance();
        int slot = hand.hand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? mc.player.getInventory().selected
                : Inventory.SLOT_OFFHAND;
        return new SignalView.Slot(slot);
    }

    // ---- Persistence -------------------------------------------------

    /** Descriptor grows an {@code @<programKey>} suffix for non-signals
     * pins (1.10.0) — absent means Signals, so every pre-1.10 pin
     * restores unchanged. */
    public static void persist(SignalView view, com.modpack.linktablet.Program program) {
        String suffix = program == com.modpack.linktablet.Program.SIGNALS
                ? "" : "@" + program.key();
        if (view instanceof SignalView.Slot slot) {
            ClientPrefs.setOverlayPin("slot:" + slot.slot() + suffix);
        } else if (view instanceof SignalView.Block block) {
            BlockPos p = block.pos();
            ClientPrefs.setOverlayPin(
                    "block:" + p.getX() + "," + p.getY() + "," + p.getZ() + suffix);
        }
    }

    public static void clear() {
        ClientPrefs.setOverlayPin("");
    }

    private record Pin(SignalView view, com.modpack.linktablet.Program program) {
    }

    private static Pin parse(String descriptor) {
        // Program suffix first; the launcher is a legitimate pin target
        // (the app dock), and byKey folds unknown future keys onto it —
        // a downgrade still shows a usable dock instead of losing the pin
        com.modpack.linktablet.Program program = com.modpack.linktablet.Program.SIGNALS;
        int at = descriptor.indexOf('@');
        if (at >= 0) {
            program = com.modpack.linktablet.Program.byKey(descriptor.substring(at + 1));
            descriptor = descriptor.substring(0, at);
        }
        try {
            if (descriptor.startsWith("slot:")) {
                return new Pin(new SignalView.Slot(
                        Integer.parseInt(descriptor.substring(5))), program);
            }
            if (descriptor.startsWith("block:")) {
                String[] parts = descriptor.substring(6).split(",");
                return new Pin(new SignalView.Block(new BlockPos(
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]))), program);
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
        Pin pin = parse(descriptor);
        if (pin == null) {
            clear();
            return;
        }
        NoteWindows.add(new MiniTabletWindow(pin.view(), pin.program()));
    }

    private OverlayPin() {
    }
}
