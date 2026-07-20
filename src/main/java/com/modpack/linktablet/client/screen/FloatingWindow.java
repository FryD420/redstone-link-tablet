package com.modpack.linktablet.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A floating "OS window" managed by {@link NoteWindows} (1.7.0: extracted
 * from the note windows so the pinned mini-tablet shares the SAME list —
 * one z-order, one event path). Implementations own their geometry and
 * contents; the manager owns ordering, focus hand-off, and routing the
 * screen/HUD events. Coordinates are gui-scaled, same space as screens.
 */
public interface FloatingWindow {

    /** HUD passes call with mouseX/mouseY = -1 (no hover states). */
    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    boolean contains(double mouseX, double mouseY);

    /** The corner X. The manager checks this BEFORE {@link #mouseClicked}. */
    boolean overCloseButton(double mouseX, double mouseY);

    /**
     * Returns true when the click landed inside the window and was
     * consumed (the manager raises the window and cancels the event).
     */
    boolean mouseClicked(double mouseX, double mouseY, int button);

    boolean mouseDragged(double mouseX, double mouseY, int button,
                         double dragX, double dragY, int screenW, int screenH);

    void mouseReleased(double mouseX, double mouseY, int button);

    /** Only called while the cursor is over the window. */
    boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY);

    /** Whether this window currently owns the keyboard. */
    boolean wantsKeyboard();

    void keyPressed(int keyCode, int scanCode, int modifiers);

    void charTyped(char chr, int modifiers);

    /**
     * Focus is leaving this window (click elsewhere, screen closing):
     * flush pending edits and drop keyboard focus / transient presses.
     */
    void defocus();

    /** True when the window's backing data vanished — the manager prunes it. */
    boolean shouldClose();

    /** X-close: final flush before the manager removes the window. */
    void onClose();

    /** Pruned (data gone or self-dismissed): cleanup without saving. */
    default void onPrune() {
    }
}
