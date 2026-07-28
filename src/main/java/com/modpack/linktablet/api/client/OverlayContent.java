package com.modpack.linktablet.api.client;

import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The body pane of the pinned overlay window (1.10.0 program-aware
 * overlay): the window owns the chrome — frame, title bar, drag,
 * close/unpin, scroll, reachability dim — and delegates the body to one
 * of these. Addon programs supply theirs via
 * {@link TabletProgramClient#createOverlay}.
 *
 * <p>Coordinates: {@code top} is the body's first content pixel already
 * shifted by the window's scroll; {@code clipTop}/{@code clipBottom}
 * bound the visible body (the window scissors — contents use them only
 * to skip fully hidden rows).
 */
public interface OverlayContent {

    /** Unclipped content height in px (the window sizes and scrolls by it). */
    int height(int rowWidth);

    void render(GuiGraphics graphics, Font font, ScreenTheme theme, int x, int top,
                int rowWidth, int mouseX, int mouseY, boolean reachable,
                int clipTop, int clipBottom);

    /** Left-click inside the body (only called while reachable). */
    boolean mouseClicked(double mouseX, double mouseY, int button, int x, int top, int rowWidth);

    default boolean mouseDragged(double mouseX, double mouseY) {
        return false;
    }

    default void mouseReleased(double mouseX, double mouseY, int button) {
    }

    /** Focus loss / screen close: drop transient presses and drags. */
    default void defocus() {
    }
}
