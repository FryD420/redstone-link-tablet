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
 * title bar, closed ONLY by the corner button or ESC. It is NOT modal:
 * clicks outside it fall through to the tablet GUI (little-window
 * behavior, user decision 2026-07-19), and the keyboard belongs to the
 * window only while its text box is focused. Hosts a vanilla
 * {@link MultiLineEditBox} for wrapping/cursor/scrolling with the
 * vanilla frame suppressed — the themed panel + ink field underneath
 * are the visuals, following the ChromeEditBox recipe.
 *
 * <p>Closing the tablet GUI with a window open PINS it: the {@link
 * NoteWindows} manager keeps rendering it — read-only on the bare HUD,
 * editable again whenever any screen is open.
 *
 * <p>Input arrives through the {@link NoteWindows} manager's screen
 * events. Since the 1.7.0 {@link FloatingWindow} extraction the window
 * flushes its own edits: {@link #defocus()}/{@link #onClose()} send a
 * {@code SetNotePayload} when the text changed — the server stays
 * authoritative and other players' edits sync back through normal
 * app-list sync.
 */
public class NoteWindow implements FloatingWindow {

    public static final int W = 176;
    public static final int H = 120;
    public static final int TITLE_H = 20;
    private static final int CLOSE_SIZE = 10;

    private final Font font;
    private final Supplier<ScreenTheme> theme;
    private final Component title;
    private final MultiLineEditBox box;
    /** Tablet this window's app lives on (windows outlive any screen). */
    final com.modpack.linktablet.client.AppView view;
    /** App this window belongs to (several windows may be open at once). */
    final int appIndex;

    private String original;
    private int x;
    private int y;
    private boolean draggingTitle = false;
    private double dragDX, dragDY;

    NoteWindow(Font font, com.modpack.linktablet.client.AppView view, int appIndex,
               Component title, String note, int x, int y) {
        this.view = view;
        this.appIndex = appIndex;
        this.font = font;
        this.theme = view::theme;
        this.title = title;
        this.original = note;
        this.x = x;
        this.y = y;
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

    private String value() {
        return box.getValue();
    }

    private boolean changed() {
        return !value().equals(original);
    }

    int x() {
        return x;
    }

    int y() {
        return y;
    }

    /** Sends the note to the server when it changed since the last save. */
    private void save() {
        if (!changed()) return;
        if (appIndex >= view.apps().size()) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.modpack.linktablet.network.ModNetworking.SetNotePayload(
                        view.target(), appIndex, value()));
        original = value();
    }

    @Override
    public boolean wantsKeyboard() {
        return box.isFocused();
    }

    @Override
    public void defocus() {
        save();
        box.setFocused(false);
    }

    @Override
    public boolean shouldClose() {
        return appIndex < 0 || appIndex >= view.apps().size();
    }

    @Override
    public void onClose() {
        save();
    }

    // ---- Geometry ----------------------------------------------------

    @Override
    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + W && my >= y && my < y + H;
    }

    private boolean overTitleBar(double mx, double my) {
        return mx >= x && mx < x + W - TITLE_H && my >= y && my < y + TITLE_H;
    }

    @Override
    public boolean overCloseButton(double mx, double my) {
        int cx = x + W - CLOSE_SIZE - 7;
        int cy = y + 5;
        return mx >= cx - 2 && mx < cx + CLOSE_SIZE + 2 && my >= cy - 2 && my < cy + CLOSE_SIZE + 2;
    }

    // ---- Render ------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ScreenTheme t = theme.get();

        // Keep the window reachable across screen resizes
        x = Mth.clamp(x, 2, Math.max(2, graphics.guiWidth() - W - 2));
        y = Mth.clamp(y, 2, Math.max(2, graphics.guiHeight() - H - 2));

        // Elevate the whole window above the home screen. GuiGraphics
        // flushes text AND items sorted by z, and renderItem self-lifts
        // to z~232 — at base z the grid's icons/labels bleed through the
        // panel. 350 clears the icons and stays under vanilla tooltips.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350);

        paintFrame(graphics, font, t, x, y, title);

        int cx = x + W - CLOSE_SIZE - 7;
        int cy = y + 5;
        int closeColor = overCloseButton(mouseX, mouseY) ? t.glyphHover : t.textFaint;
        // X glyph: two 2px diagonals
        for (int i = 0; i < CLOSE_SIZE - 2; i++) {
            graphics.fill(cx + 1 + i, cy + 1 + i, cx + 3 + i, cy + 3 + i, closeColor);
            graphics.fill(cx + CLOSE_SIZE - 3 - i, cy + 1 + i, cx + CLOSE_SIZE - 1 - i, cy + 3 + i, closeColor);
        }

        // The edit box inset by the ChromeEditBox convention
        box.setX(x + 10);
        box.setY(y + TITLE_H + 7);
        box.render(graphics, mouseX, mouseY, partialTick);

        graphics.pose().popPose();
    }

    /**
     * Frame shared by the live window and the pinned HUD copy: drop
     * shadow, chrome panel, ellipsized title, rail divider, ink field.
     */
    public static void paintFrame(GuiGraphics graphics, Font font, ScreenTheme t,
                                  int x, int y, Component title) {
        graphics.fill(x + 3, y + 3, x + W + 3, y + H + 3, 0x50000000);
        Chrome.panel(graphics, x, y, W, H, t);
        String name = TextFit.ellipsize(font, title.getString(), W - TITLE_H - 24);
        graphics.drawString(font, name, x + 10, y + 7, t.textPrimary, t.textShadow);
        Chrome.railH(graphics, x + 4, y + TITLE_H - 3, W - 8, t.bodyOuter);
        Chrome.inkField(graphics, x + 6, y + TITLE_H + 2, W - 12, H - TITLE_H - 8);
    }

    // ---- Input -------------------------------------------------------

    /**
     * Returns true when the click landed inside the window (and was
     * consumed); false when it fell outside — the owner lets it through
     * to the GUI. The close button is the OWNER's check, before this.
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overTitleBar(mx, my)) {
            draggingTitle = true;
            dragDX = mx - x;
            dragDY = my - y;
            box.setFocused(false);
            return true;
        }
        if (contains(mx, my)) {
            box.setFocused(true);
            box.mouseClicked(mx, my, button);
            return true;
        }
        box.setFocused(false);
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy,
                                int screenW, int screenH) {
        if (draggingTitle) {
            x = Mth.clamp((int) (mx - dragDX), 2, screenW - W - 2);
            y = Mth.clamp((int) (my - dragDY), 2, screenH - H - 2);
            return true;
        }
        return box.isFocused() && box.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        draggingTitle = false;
        box.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        return box.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
        box.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void charTyped(char chr, int modifiers) {
        box.charTyped(chr, modifiers);
    }
}
