package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.OverlayKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The chat-style mouse capture for the pinned overlay (1.7.0): an
 * intentionally INVISIBLE screen. Opening it frees the cursor; the
 * {@link NoteWindows} manager's screen events then make the {@link
 * MiniTabletWindow} (and any note windows) fully interactive — this
 * class renders nothing and handles nothing but its own dismissal
 * (ESC, or the same keybind that opened it). The world keeps rendering
 * and the game keeps running underneath, exactly like chat.
 */
public class OverlayFocusScreen extends Screen {

    public OverlayFocusScreen() {
        super(Component.translatable("gui.linktablet.overlay.focus"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing — no dim, no blur; the floating windows draw themselves
        // through the manager's ScreenEvent.Render.Post pass.
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No background blur/dim: the point is seeing the world.
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (OverlayKeys.OVERLAY_INTERACT.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
