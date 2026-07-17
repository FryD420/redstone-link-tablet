package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.Supplier;

/**
 * Floating per-app note window (1.6.0): a small chrome-framed "OS
 * window" that hovers over the tablet home screen — draggable by its
 * title bar, closed with the corner button (or a click outside). Hosts
 * a vanilla {@link MultiLineEditBox} for wrapping/cursor/scrolling with
 * the vanilla frame suppressed — the themed panel + ink field underneath
 * are the visuals, following the ChromeEditBox recipe.
 *
 * <p>The owner ({@link TabletScreen}) forwards input while a window is
 * open and saves via {@link #value()} when it closes. The window never
 * sends packets itself.
 */
class NoteWindow {

    static final int W = 176;
    static final int H = 120;
    private static final int TITLE_H = 20;
    private static final int CLOSE_SIZE = 10;

    private final Font font;
    private final Supplier<ScreenTheme> theme;
    private final Component title;
    private final String original;
    private final MultiLineEditBox box;

    private int x;
    private int y;
    private boolean draggingTitle = false;
    private double dragDX, dragDY;

    NoteWindow(Font font, Supplier<ScreenTheme> theme, int screenW, int screenH,
               Component title, String note) {
        this.font = font;
        this.theme = theme;
        this.title = title;
        this.original = note;
        this.x = (screenW - W) / 2;
        this.y = (screenH - H) / 2;
        this.box = new MultiLineEditBox(font, 0, 0, W - 20, H - TITLE_H - 16,
                Component.translatable("gui.linktablet.note.hint"), title) {
            @Override
            protected void renderBackground(GuiGraphics graphics) {
                // The window draws a themed ink field instead
            }
        };
        this.box.setCharacterLimit(SignalApp.MAX_NOTE_LENGTH);
        this.box.setValue(note);
        this.box.setFocused(true);
    }

    String value() {
        return box.getValue();
    }

    boolean changed() {
        return !value().equals(original);
    }

    // ---- Geometry ----------------------------------------------------

    boolean contains(double mx, double my) {
        return mx >= x && mx < x + W && my >= y && my < y + H;
    }

    private boolean overTitleBar(double mx, double my) {
        return mx >= x && mx < x + W - TITLE_H && my >= y && my < y + TITLE_H;
    }

    private boolean overClose(double mx, double my) {
        int cx = x + W - CLOSE_SIZE - 7;
        int cy = y + 5;
        return mx >= cx - 2 && mx < cx + CLOSE_SIZE + 2 && my >= cy - 2 && my < cy + CLOSE_SIZE + 2;
    }

    // ---- Render ------------------------------------------------------

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ScreenTheme t = theme.get();

        // Elevate the whole window above the home screen. GuiGraphics
        // flushes text AND items sorted by z, and renderItem self-lifts
        // to z~232 — at base z the grid's icons/labels bleed through the
        // panel. 350 clears the icons and stays under vanilla tooltips.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350);

        // Soft drop shadow so the window reads as floating over the body
        graphics.fill(x + 3, y + 3, x + W + 3, y + H + 3, 0x50000000);

        Chrome.panel(graphics, x, y, W, H, t);

        // Title bar: app name + close button, rail divider underneath
        String name = TextFit.ellipsize(font, title.getString(), W - TITLE_H - 24);
        graphics.drawString(font, name, x + 10, y + 7, t.textPrimary, t.textShadow);
        Chrome.railH(graphics, x + 4, y + TITLE_H - 3, W - 8, t.bodyOuter);

        int cx = x + W - CLOSE_SIZE - 7;
        int cy = y + 5;
        int closeColor = overClose(mouseX, mouseY) ? t.glyphHover : t.textFaint;
        // X glyph: two 2px diagonals
        for (int i = 0; i < CLOSE_SIZE - 2; i++) {
            graphics.fill(cx + 1 + i, cy + 1 + i, cx + 3 + i, cy + 3 + i, closeColor);
            graphics.fill(cx + CLOSE_SIZE - 3 - i, cy + 1 + i, cx + CLOSE_SIZE - 1 - i, cy + 3 + i, closeColor);
        }

        // Ink well + the edit box inset by the ChromeEditBox convention
        Chrome.inkField(graphics, x + 6, y + TITLE_H + 2, W - 12, H - TITLE_H - 8);
        box.setX(x + 10);
        box.setY(y + TITLE_H + 7);
        box.render(graphics, mouseX, mouseY, partialTick);

        graphics.pose().popPose();
    }

    // ---- Input (returns true when the event stays inside the window) --

    boolean mouseClicked(double mx, double my, int button) {
        if (overClose(mx, my)) {
            return false; // owner closes + saves
        }
        if (button == 0 && overTitleBar(mx, my)) {
            draggingTitle = true;
            dragDX = mx - x;
            dragDY = my - y;
            return true;
        }
        if (contains(mx, my)) {
            box.mouseClicked(mx, my, button);
            return true;
        }
        return false; // outside — owner closes + saves
    }

    boolean mouseDragged(double mx, double my, int button, double dx, double dy,
                         int screenW, int screenH) {
        if (draggingTitle) {
            x = Mth.clamp((int) (mx - dragDX), 2, screenW - W - 2);
            y = Mth.clamp((int) (my - dragDY), 2, screenH - H - 2);
            return true;
        }
        return box.mouseDragged(mx, my, button, dx, dy);
    }

    void mouseReleased(double mx, double my, int button) {
        draggingTitle = false;
        box.mouseReleased(mx, my, button);
    }

    boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return box.mouseScrolled(mx, my, sx, sy);
    }

    boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return box.keyPressed(keyCode, scanCode, modifiers);
    }

    boolean charTyped(char chr, int modifiers) {
        return box.charTyped(chr, modifiers);
    }
}
