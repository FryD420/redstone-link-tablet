package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Global floating-window manager (1.6.0, generalized for 1.7.0): open
 * {@link FloatingWindow}s live HERE, not on any screen, so they persist
 * and stay usable over the tablet GUI, containers, chat, and the JEI/EMI
 * browsers (see {@link #overlaysAllowedOn} — settings menus and other
 * foreign screens suppress them), and on the HUD while no screen is
 * open. Notes
 * render read-only there; the pinned mini-tablet stays live and becomes
 * clickable through {@link OverlayFocusScreen}'s chat-style mouse
 * capture. All window kinds share ONE list: one z-order, one event path.
 *
 * <p>Rendering and input arrive via screen events, so foreign screens
 * need no knowledge of the windows: clicks a window contains are
 * consumed (cancelled) before the screen sees them; everything else
 * falls through. The list is bottom → top; clicking raises. A window
 * closes ONLY via its X (or when its backing data vanishes or the
 * player logs out).
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class NoteWindows {

    /** Open windows, bottom → top. */
    private static final List<FloatingWindow> WINDOWS = new ArrayList<>();

    // ---- API ---------------------------------------------------------

    /** Opens an app's note window, or raises it if already open. */
    public static void open(AppView view, int index) {
        List<SignalApp> apps = view.apps();
        if (index < 0 || index >= apps.size()) return;
        NoteWindow existing = findNote(view.target(), index);
        if (existing != null) {
            raise(existing);
            existing.defocus();
            UISounds.tick(1.3F);
            return;
        }
        SignalApp app = apps.get(index);
        int[] at = cascadeOrigin(NoteWindow.W, NoteWindow.H);
        WINDOWS.forEach(FloatingWindow::defocus);
        WINDOWS.add(new NoteWindow(Minecraft.getInstance().font, view, index,
                Component.literal(app.name()), app.note(), at[0], at[1]));
        UISounds.page();
    }

    /** Adds (or raises) a non-note window — the pinned mini-tablet. */
    public static void add(FloatingWindow window) {
        if (WINDOWS.contains(window)) {
            raise(window);
            return;
        }
        WINDOWS.forEach(FloatingWindow::defocus);
        WINDOWS.add(window);
    }

    public static void remove(FloatingWindow window) {
        WINDOWS.remove(window);
    }

    public static boolean anyContains(double mouseX, double mouseY) {
        return WINDOWS.stream().anyMatch(w -> w.contains(mouseX, mouseY));
    }

    /** First open window of the given type, or null (pin lookups). */
    public static <T extends FloatingWindow> T find(Class<T> type) {
        for (FloatingWindow w : WINDOWS) {
            if (type.isInstance(w)) return type.cast(w);
        }
        return null;
    }

    /** Centers the first window; cascades OS-style off the frontmost. */
    private static int[] cascadeOrigin(int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        if (WINDOWS.isEmpty()) {
            return new int[]{(sw - w) / 2, (sh - h) / 2};
        }
        FloatingWindow top = WINDOWS.getLast();
        if (top instanceof NoteWindow note) {
            return new int[]{note.x() + 16, note.y() + 16};
        }
        return new int[]{(sw - w) / 2 + 16, (sh - h) / 2 + 16};
    }

    // ---- Internals ---------------------------------------------------

    private static NoteWindow findNote(ModNetworking.AppTarget target, int index) {
        for (FloatingWindow w : WINDOWS) {
            if (w instanceof NoteWindow note
                    && note.appIndex == index && note.view.target().equals(target)) {
                return note;
            }
        }
        return null;
    }

    private static void raise(FloatingWindow window) {
        if (WINDOWS.remove(window)) {
            WINDOWS.add(window);
        }
    }

    private static void close(FloatingWindow window) {
        window.onClose();
        WINDOWS.remove(window);
        UISounds.tick(1.0F);
    }

    /** Drops windows whose backing data vanished. */
    private static void prune() {
        WINDOWS.removeIf(w -> {
            if (!w.shouldClose()) return false;
            w.onPrune();
            return true;
        });
    }

    /**
     * Whitelist (1.7.0): overlays only live over screens where they're
     * useful — containers, chat, our own screens, and the JEI/EMI recipe
     * browsers. Everything else (vanilla options, Sodium/Iris/any mod's
     * settings, pause menu, world lists) gets neither rendering nor input
     * capture. Every screen-scoped handler below consults this; the HUD
     * pass has its own {@code mc.screen == null} gate.
     */
    private static boolean overlaysAllowedOn(Screen screen) {
        if (screen == null) return false;
        if (screen instanceof OverlayFocusScreen
                || screen instanceof AbstractContainerScreen<?>
                || screen instanceof ChatScreen) {
            return true;
        }
        String name = screen.getClass().getName();
        return name.startsWith("com.modpack.linktablet.")
                || name.startsWith("mezz.jei.")
                || name.startsWith("dev.emi.");
    }

    /** Suppressed-screen housekeeping: never keep a stale drag/press/edit. */
    private static boolean suppressedOn(Screen screen) {
        if (overlaysAllowedOn(screen)) return false;
        for (FloatingWindow window : WINDOWS) {
            window.defocus();
        }
        return true;
    }

    // ---- Rendering ---------------------------------------------------

    @SubscribeEvent
    public static void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            WINDOWS.clear();
            return;
        }
        if (mc.screen != null || WINDOWS.isEmpty()) return;
        prune();
        // No mouse on the HUD: no hover states
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (FloatingWindow window : WINDOWS) {
            window.render(event.getGuiGraphics(), -1, -1, partialTick);
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (Minecraft.getInstance().level == null || WINDOWS.isEmpty()) return;
        if (suppressedOn(event.getScreen())) return;
        prune();
        for (FloatingWindow window : WINDOWS) {
            window.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(),
                    event.getPartialTick());
        }
    }

    // ---- Input over any screen ---------------------------------------

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (WINDOWS.isEmpty() || suppressedOn(event.getScreen())) return;
        double mx = event.getMouseX();
        double my = event.getMouseY();
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            FloatingWindow window = WINDOWS.get(i);
            if (window.overCloseButton(mx, my)) {
                close(window);
                event.setCanceled(true);
                return;
            }
            if (window.mouseClicked(mx, my, event.getButton())) {
                raise(window);
                for (FloatingWindow other : WINDOWS) {
                    if (other != window && other.wantsKeyboard()) {
                        other.defocus(); // focus moved on — flush edits
                    }
                }
                event.setCanceled(true);
                return;
            }
        }
        // Click landed outside every window: release the keyboard
        for (FloatingWindow window : WINDOWS) {
            if (window.wantsKeyboard()) {
                window.defocus();
            }
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (suppressedOn(event.getScreen())) return;
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            if (WINDOWS.get(i).mouseDragged(event.getMouseX(), event.getMouseY(),
                    event.getMouseButton(), event.getDragX(), event.getDragY(), w, h)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        if (suppressedOn(event.getScreen())) return;
        // Never cancelled: screens must still see their own drag ends
        for (FloatingWindow window : WINDOWS) {
            window.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton());
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (suppressedOn(event.getScreen())) return;
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            FloatingWindow window = WINDOWS.get(i);
            if (window.contains(event.getMouseX(), event.getMouseY())) {
                window.mouseScrolled(event.getMouseX(), event.getMouseY(),
                        event.getScrollDeltaX(), event.getScrollDeltaY());
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        // ESC always goes to the screen (exit GUI, windows persist)
        if (event.getKeyCode() == 256) return;
        if (suppressedOn(event.getScreen())) return;
        for (FloatingWindow window : WINDOWS) {
            if (window.wantsKeyboard()) {
                window.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        if (suppressedOn(event.getScreen())) return;
        for (FloatingWindow window : WINDOWS) {
            if (window.wantsKeyboard()) {
                window.charTyped(event.getCodePoint(), event.getModifiers());
                event.setCanceled(true);
                return;
            }
        }
    }

    // ---- Lifecycle ---------------------------------------------------

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        // Leaving any screen: flush edits, drop keyboard focus and any
        // transient presses so the HUD copies are clean
        for (FloatingWindow window : WINDOWS) {
            window.defocus();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WINDOWS.clear();
    }

    private NoteWindows() {
    }
}
