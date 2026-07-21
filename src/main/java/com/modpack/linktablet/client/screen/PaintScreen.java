package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 🎨 (1.8.0, unadvertised): "Paint". A sixteen-color doodle pad —
 * left-click paints, right-click erases, drag to sweep, C clears.
 * Session-only art: closing the pad is the gallery fire.
 */
public class PaintScreen extends ArcadeScreen {

    private static final int COLS = 20;
    private static final int ROWS = 14;
    private static final int CELL = 7;
    private static final int SWATCH = 8;

    /** The dye palette (same family as the app color presets). */
    private static final int[] PALETTE = {
            0xFFF9FFFE, 0xFFB02E26, 0xFFF9801D, 0xFFFED83D,
            0xFF80C71F, 0xFF5E7C16, 0xFF3AB3DA, 0xFF169C9C,
            0xFF3C44AA, 0xFF8932B8, 0xFFC74EBD, 0xFFF38BAA,
            0xFF835432, 0xFF9D9D97, 0xFF474F52, 0xFF1D1D21};

    private final int[] canvas = new int[COLS * ROWS];
    private int selected = 6;

    public PaintScreen(AppView view, boolean returnToTablet) {
        super("paint", view, returnToTablet);
    }

    @Override
    protected int boardW() {
        return COLS * CELL;
    }

    @Override
    protected int boardH() {
        return ROWS * CELL + SWATCH + 4;
    }

    private boolean apply(double mouseX, double mouseY, int button) {
        int cx = (int) Math.floor((mouseX - boardX()) / CELL);
        int cy = (int) Math.floor((mouseY - boardY()) / CELL);
        if (cx >= 0 && cx < COLS && cy >= 0 && cy < ROWS) {
            canvas[cy * COLS + cx] = button == 1 ? 0 : PALETTE[selected];
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Palette strip below the canvas
        int py = boardY() + ROWS * CELL + 4;
        if (mouseY >= py && mouseY < py + SWATCH) {
            int index = (int) ((mouseX - boardX()) / (SWATCH + 1));
            if (index >= 0 && index < PALETTE.length) {
                selected = index;
                UISounds.tick(1.3F);
                return true;
            }
        }
        if (apply(mouseX, mouseY, button)) {
            UISounds.tick(button == 1 ? 0.9F : 1.1F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (apply(mouseX, mouseY, button)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 67) { // C clears
            java.util.Arrays.fill(canvas, 0);
            UISounds.page();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCabinet(graphics, "🎨");
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < canvas.length; i++) {
            if (canvas[i] == 0) continue;
            int x = bx + (i % COLS) * CELL;
            int y = by + (i / COLS) * CELL;
            graphics.fill(x, y, x + CELL, y + CELL, canvas[i]);
        }
        int py = by + ROWS * CELL + 4;
        for (int i = 0; i < PALETTE.length; i++) {
            int x = bx + i * (SWATCH + 1);
            graphics.fill(x, py, x + SWATCH, py + SWATCH, PALETTE[i]);
            if (i == selected) {
                graphics.fill(x, py - 2, x + SWATCH, py - 1, 0xFFE8EAF0);
            }
        }
    }
}
