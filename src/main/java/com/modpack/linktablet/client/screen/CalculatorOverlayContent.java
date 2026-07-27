package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The pinned Calculator body (1.10.0 user feedback: the pin pins
 * whatever screen you're on): a compact but fully working pad over the
 * same session-static {@link CalcEngine} the full screen uses — start a
 * sum in the GUI, finish it on the HUD.
 */
public class CalculatorOverlayContent implements OverlayContent {

    private static final int DISPLAY_H = 16;
    private static final int BTN_H = 16;
    private static final int GAP = 2;

    @Override
    public Program program() {
        return Program.CALCULATOR;
    }

    @Override
    public int height(int rowWidth) {
        return DISPLAY_H + GAP + CalcEngine.PAD.length * (BTN_H + GAP) - GAP;
    }

    private static int btnW(int rowWidth) {
        return (rowWidth - 3 * GAP) / 4;
    }

    /** Key cell rect; the "=" cell spans the last two columns. */
    private static int[] keyRect(int row, int col, int x, int top, int rowWidth) {
        int bw = btnW(rowWidth);
        int kx = x + col * (bw + GAP);
        int ky = top + DISPLAY_H + GAP + row * (BTN_H + GAP);
        int kw = row == 4 && col == 2 ? 2 * bw + GAP : bw;
        return new int[]{kx, ky, kw, BTN_H};
    }

    @Override
    public void render(GuiGraphics graphics, Font font, ScreenTheme theme, int x, int top,
                       int rowWidth, int mouseX, int mouseY, boolean reachable,
                       int clipTop, int clipBottom) {
        Chrome.inkField(graphics, x, top, rowWidth, DISPLAY_H);
        String shown = CalcEngine.error()
                ? Component.translatable("gui.linktablet.calc.error").getString()
                : CalcEngine.entry();
        graphics.drawString(font, shown, x + rowWidth - 4 - font.width(shown), top + 4,
                theme.textPrimary, theme.textShadow);
        String opGlyph = CalcEngine.pendingOpGlyph();
        if (!opGlyph.isEmpty()) {
            graphics.drawString(font, opGlyph, x + 3, top + 4, theme.accent, theme.textShadow);
        }

        for (int row = 0; row < CalcEngine.PAD.length; row++) {
            for (int col = 0; col < 4; col++) {
                if (row == 4 && col == 3) continue; // merged into "="
                String key = CalcEngine.PAD[row][col];
                int[] rect = keyRect(row, col, x, top, rowWidth);
                boolean hovered = reachable && mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                        && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
                boolean opKey = col == 3 || "=".equals(key);
                graphics.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3],
                        hovered ? theme.rowBgHover : opKey ? theme.surfaceHi : theme.rowBg);
                graphics.drawString(font, key,
                        rect[0] + (rect[2] - font.width(key)) / 2,
                        rect[1] + (rect[3] - 8) / 2,
                        opKey ? theme.accent : theme.textPrimary, theme.textShadow);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int top, int rowWidth) {
        if (button != 0) return false;
        for (int row = 0; row < CalcEngine.PAD.length; row++) {
            for (int col = 0; col < 4; col++) {
                if (row == 4 && col == 3) continue;
                int[] rect = keyRect(row, col, x, top, rowWidth);
                if (mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                        && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                    CalcEngine.pressKey(CalcEngine.PAD[row][col]);
                    return true;
                }
            }
        }
        return false;
    }
}
