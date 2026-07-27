package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.client.OverlayPin;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Calculator program (1.10.0 OS suite): the full-size face over
 * {@link CalcEngine} — chrome-button pad plus full keyboard entry
 * (digits, ops, Enter, Backspace). The engine's state is session-static,
 * so closing and reopening mid-arithmetic doesn't wipe the tape, and
 * the pinned overlay pad ({@link CalculatorOverlayContent}) works the
 * same sum.
 */
public class CalculatorScreen extends Screen {

    private static final int PANEL_W = 148;
    private static final int HEADER = 34;
    private static final int BTN = 32;
    private static final int GAP = 4;
    private static final int BTN_H = 20;
    private static final int DISPLAY_H = 22;
    private static final int BOTTOM_PAD = 8;
    private static final int MODE_BTN_SIZE = 12;

    private final SignalView view;

    public CalculatorScreen(SignalView view) {
        super(Component.translatable("program.linktablet.calculator"));
        this.view = view;
    }

    private ScreenTheme theme() {
        return view.theme();
    }

    // ------------------------------------------------------------------
    // Layout
    // ------------------------------------------------------------------

    private int bodyHeight() {
        return HEADER + DISPLAY_H + GAP + CalcEngine.PAD.length * (BTN_H + GAP) + BOTTOM_PAD;
    }

    private int bodyTop() {
        return (height - bodyHeight()) / 2;
    }

    private int panelLeft() {
        return (width - PANEL_W) / 2;
    }

    private int padLeft() {
        return panelLeft() + (PANEL_W - (4 * BTN + 3 * GAP)) / 2;
    }

    private int displayY() {
        return bodyTop() + HEADER;
    }

    private int padTop() {
        return displayY() + DISPLAY_H + GAP;
    }

    private int homeBtnX() {
        return panelLeft() + 4;
    }

    /** Per-app overlay pin (1.10.0): pins the CALCULATOR overlay pad. */
    private int pinBtnX() {
        return homeBtnX() + MODE_BTN_SIZE + 4;
    }

    private int modeBtnY() {
        return bodyTop() + 8;
    }

    private boolean overBtn(double mouseX, double mouseY, int btnX) {
        return mouseX >= btnX && mouseX < btnX + MODE_BTN_SIZE
                && mouseY >= modeBtnY() && mouseY < modeBtnY() + MODE_BTN_SIZE;
    }

    /** The "=" key: cell (4,2) spans the last two columns. */
    private int[] keyRect(int row, int col) {
        int x = padLeft() + col * (BTN + GAP);
        int y = padTop() + row * (BTN_H + GAP);
        int w = BTN;
        if (row == 4 && col == 2) {
            w = 2 * BTN + GAP;
        }
        return new int[]{x, y, w, BTN_H};
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        ScreenTheme theme = theme();
        int left = panelLeft();
        int top = bodyTop();

        Chrome.panel(graphics, left - 6, top - 2, PANEL_W + 12, bodyHeight() + 4, theme);
        Component titleText = getTitle();
        int titleW = font.width(titleText);
        Chrome.plaque(graphics, width / 2 - titleW / 2 - 6, top + 2, titleW + 12, 18, theme.rowBg);
        graphics.drawString(font, titleText, width / 2 - titleW / 2, top + 7,
                theme.textPrimary, theme.textShadow);
        HeaderGlyphs.home(graphics, homeBtnX(), modeBtnY(),
                overBtn(mouseX, mouseY, homeBtnX()) ? theme.glyphHover : theme.textFaint);
        boolean pinned = OverlayPin.isPinned(view, Program.CALCULATOR);
        HeaderGlyphs.pin(graphics, pinBtnX(), modeBtnY(),
                pinned ? theme.accent
                        : overBtn(mouseX, mouseY, pinBtnX()) ? theme.glyphHover : theme.textFaint);
        Chrome.railH(graphics, left - 4, displayY() - 4, PANEL_W + 8, theme.bodyOuter);

        // Display: right-aligned tape in an ink well, pending op hinted
        // on the left edge
        int dx = padLeft();
        int dw = 4 * BTN + 3 * GAP;
        Chrome.inkField(graphics, dx, displayY(), dw, DISPLAY_H);
        String shown = CalcEngine.error()
                ? Component.translatable("gui.linktablet.calc.error").getString() : CalcEngine.entry();
        graphics.pose().pushPose();
        graphics.pose().translate(dx + dw - 6 - font.width(shown) * 1.5f, displayY() + 5, 0);
        graphics.pose().scale(1.5f, 1.5f, 1f);
        graphics.drawString(font, shown, 0, 0, theme.textPrimary, theme.textShadow);
        graphics.pose().popPose();
        String opGlyph = CalcEngine.pendingOpGlyph();
        if (!opGlyph.isEmpty()) {
            graphics.drawString(font, opGlyph, dx + 4, displayY() + 7,
                    theme.accent, theme.textShadow);
        }

        // Key pad
        for (int row = 0; row < CalcEngine.PAD.length; row++) {
            for (int col = 0; col < 4; col++) {
                if (row == 4 && col == 3) continue; // merged into "="
                String key = CalcEngine.PAD[row][col];
                int[] rect = keyRect(row, col);
                boolean hovered = mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                        && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
                boolean opKey = col == 3 || "=".equals(key);
                Chrome.bannerButton(graphics, rect[0], rect[1], rect[2], rect[3],
                        hovered ? Chrome.ButtonState.HOVER : Chrome.ButtonState.NORMAL,
                        opKey ? theme.rowBgHover : theme.rowBg);
                int color = opKey ? theme.accent : theme.textPrimary;
                graphics.drawString(font, key,
                        rect[0] + (rect[2] - font.width(key)) / 2,
                        rect[1] + (rect[3] - 8) / 2, color, theme.textShadow);
            }
        }
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && overBtn(mouseX, mouseY, homeBtnX())) {
            UISounds.tick(1.2F);
            ClientHooks.returnHome(view);
            return true;
        }
        if (button == 0 && overBtn(mouseX, mouseY, pinBtnX())) {
            if (OverlayPin.isPinned(view, Program.CALCULATOR)) {
                OverlayPin.unpin();
                UISounds.tick(1.0F);
            } else {
                OverlayPin.pin(view, Program.CALCULATOR);
                UISounds.tick(1.5F);
            }
            return true;
        }
        if (button == 0) {
            for (int row = 0; row < CalcEngine.PAD.length; row++) {
                for (int col = 0; col < 4; col++) {
                    if (row == 4 && col == 3) continue;
                    int[] rect = keyRect(row, col);
                    if (mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                            && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                        CalcEngine.pressKey(CalcEngine.PAD[row][col]);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        switch (codePoint) {
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> CalcEngine.pressKey(String.valueOf(codePoint));
            case '.', ',' -> CalcEngine.pressKey(".");
            case '+' -> CalcEngine.pressKey("+");
            case '-' -> CalcEngine.pressKey("-");
            case '*' -> CalcEngine.pressKey("×");
            case '/' -> CalcEngine.pressKey("÷");
            case '=' -> CalcEngine.pressKey("=");
            case 'c', 'C' -> CalcEngine.pressKey("C");
            default -> {
                return super.charTyped(codePoint, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / numpad Enter
            CalcEngine.pressKey("=");
            return true;
        }
        if (keyCode == 259) { // Backspace
            CalcEngine.pressKey("DEL");
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        UISounds.close();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
