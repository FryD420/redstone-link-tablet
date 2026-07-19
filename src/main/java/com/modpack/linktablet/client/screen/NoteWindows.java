package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.LinkTabletMod;
import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Global note-window manager (1.6.0): open {@link NoteWindow}s live HERE,
 * not on any screen, so they persist and stay usable EVERYWHERE — over
 * the tablet GUI, over the inventory or any other screen (user request
 * 2026-07-19: edit/close notes from the inventory), and read-only on the
 * HUD while no screen is open (the mouse belongs to the player there).
 *
 * <p>Rendering and input arrive via screen events, so foreign screens
 * need no knowledge of the windows: clicks a window contains are
 * consumed (cancelled) before the screen sees them; everything else
 * falls through. The list is bottom → top; clicking raises. A window
 * closes ONLY via its X (or when its app/tablet vanishes or the player
 * logs out).
 *
 * <p>Saves are sent when a window's text box loses focus, when a screen
 * closes, and on X-close — the server stays authoritative and other
 * players' edits sync back through normal app-list sync.
 */
@EventBusSubscriber(modid = LinkTabletMod.MOD_ID, value = Dist.CLIENT)
public final class NoteWindows {

    /** Open windows, bottom → top. */
    private static final List<NoteWindow> WINDOWS = new ArrayList<>();

    // ---- API (TabletScreen's glyph + tooltip gate) -------------------

    /** Opens an app's note window, or raises it if already open. */
    public static void open(AppView view, int index) {
        List<SignalApp> apps = view.apps();
        if (index < 0 || index >= apps.size()) return;
        NoteWindow existing = find(view.target(), index);
        if (existing != null) {
            raise(existing);
            existing.unfocus();
            UISounds.tick(1.3F);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        SignalApp app = apps.get(index);
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        int x, y;
        if (WINDOWS.isEmpty()) {
            x = (w - NoteWindow.W) / 2;
            y = (h - NoteWindow.H) / 2;
        } else {
            // Cascade off the frontmost window, OS style
            NoteWindow top = WINDOWS.getLast();
            x = top.x() + 16;
            y = top.y() + 16;
        }
        WINDOWS.forEach(NoteWindow::unfocus);
        WINDOWS.add(new NoteWindow(mc.font, view, index,
                Component.literal(app.name()), app.note(), x, y));
        UISounds.page();
    }

    public static boolean anyContains(double mouseX, double mouseY) {
        return WINDOWS.stream().anyMatch(w -> w.contains(mouseX, mouseY));
    }

    // ---- Internals ---------------------------------------------------

    private static NoteWindow find(ModNetworking.AppTarget target, int index) {
        for (NoteWindow w : WINDOWS) {
            if (w.appIndex == index && w.view.target().equals(target)) return w;
        }
        return null;
    }

    private static void raise(NoteWindow window) {
        if (WINDOWS.remove(window)) {
            WINDOWS.add(window);
        }
    }

    private static void save(NoteWindow window) {
        if (!window.changed()) return;
        if (window.appIndex >= window.view.apps().size()) return;
        PacketDistributor.sendToServer(new ModNetworking.SetNotePayload(
                window.view.target(), window.appIndex, window.value()));
        window.markSaved();
    }

    private static void close(NoteWindow window) {
        save(window);
        WINDOWS.remove(window);
        UISounds.tick(1.0F);
    }

    /** Drops windows whose app/tablet vanished (no save — data's gone). */
    private static void prune() {
        WINDOWS.removeIf(w -> w.appIndex < 0 || w.appIndex >= w.view.apps().size());
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
        // Read-only on the HUD: no mouse, so no hover states
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (NoteWindow window : WINDOWS) {
            window.render(event.getGuiGraphics(), -1, -1, partialTick);
        }
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        if (Minecraft.getInstance().level == null || WINDOWS.isEmpty()) return;
        prune();
        for (NoteWindow window : WINDOWS) {
            window.render(event.getGuiGraphics(), event.getMouseX(), event.getMouseY(),
                    event.getPartialTick());
        }
    }

    // ---- Input over any screen ---------------------------------------

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (WINDOWS.isEmpty()) return;
        double mx = event.getMouseX();
        double my = event.getMouseY();
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            NoteWindow window = WINDOWS.get(i);
            if (window.overCloseButton(mx, my)) {
                close(window);
                event.setCanceled(true);
                return;
            }
            if (window.mouseClicked(mx, my, event.getButton())) {
                raise(window);
                for (NoteWindow other : WINDOWS) {
                    if (other != window && other.boxFocused()) {
                        save(other); // focus moved on — flush edits
                        other.unfocus();
                    }
                }
                event.setCanceled(true);
                return;
            }
        }
        // Click landed outside every window: release the keyboard
        for (NoteWindow window : WINDOWS) {
            if (window.boxFocused()) {
                save(window);
                window.unfocus();
            }
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
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
        // Never cancelled: screens must still see their own drag ends
        for (NoteWindow window : WINDOWS) {
            window.mouseReleased(event.getMouseX(), event.getMouseY(), event.getButton());
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        for (int i = WINDOWS.size() - 1; i >= 0; i--) {
            NoteWindow window = WINDOWS.get(i);
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
        for (NoteWindow window : WINDOWS) {
            if (window.boxFocused()) {
                window.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onCharTyped(ScreenEvent.CharacterTyped.Pre event) {
        for (NoteWindow window : WINDOWS) {
            if (window.boxFocused()) {
                window.charTyped(event.getCodePoint(), event.getModifiers());
                event.setCanceled(true);
                return;
            }
        }
    }

    // ---- Lifecycle ---------------------------------------------------

    @SubscribeEvent
    public static void onScreenClosing(ScreenEvent.Closing event) {
        // Leaving any screen: flush edits and drop keyboard focus so the
        // HUD copies are clean read-only
        for (NoteWindow window : WINDOWS) {
            save(window);
            window.unfocus();
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WINDOWS.clear();
    }

    private NoteWindows() {
    }
}
