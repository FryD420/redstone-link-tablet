package com.modpack.linktablet.example;

import com.modpack.linktablet.api.client.OverlayContent;
import com.modpack.linktablet.api.client.ProgramHost;
import com.modpack.linktablet.api.client.RegisterTabletProgramClientsEvent;
import com.modpack.linktablet.api.client.TabletFaceContext;
import com.modpack.linktablet.api.client.TabletFacePainter;
import com.modpack.linktablet.api.client.TabletProgramClient;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

/**
 * The Dice example's client half — screen, overlay body, and kiosk
 * face, all through the public API (plus vanilla GUI primitives; addon
 * authors bring their own widget style, so the example deliberately
 * avoids this mod's internal chrome).
 */
final class ExampleAddonClient {

    /** Session-wide last roll (the CalcEngine idiom: client-static). */
    static int lastRoll = 6;

    static int roll() {
        lastRoll = 1 + (int) (Math.random() * 6);
        return lastRoll;
    }

    static void register(RegisterTabletProgramClientsEvent event) {
        event.register(ExampleAddon.DICE.key(), new TabletProgramClient() {
            @Override
            public Screen createScreen(ProgramHost host) {
                return new DiceScreen(host);
            }

            @Override
            public OverlayContent createOverlay(ProgramHost host) {
                return new DiceOverlay();
            }

            @Override
            public TabletFacePainter facePainter() {
                return ExampleAddonClient::paintFace;
            }
        });
    }

    // ---- Kiosk face ---------------------------------------------------

    /** Standard die-face pip layout on a 3×3 grid, per value 1–6. */
    private static final int[][] PIPS = {
            {4}, {0, 8}, {0, 4, 8}, {0, 2, 6, 8}, {0, 2, 4, 6, 8}, {0, 2, 3, 5, 6, 8}};

    private static void paintFace(TabletFaceContext ctx) {
        ScreenTheme theme = ctx.theme();
        float size = Math.min(ctx.width(), ctx.height()) * 0.62f;
        float u = (ctx.width() - size) / 2f;
        float v = (ctx.height() - size) / 2f - 0.6f;
        ctx.fill(u, v, size, size, 0xFFF5F1E8);
        float pip = size / 5f;
        for (int cell : PIPS[Math.max(0, Math.min(5, lastRoll - 1))]) {
            float cu = u + size * (0.5f + (cell % 3 - 1) * 0.27f) - pip / 2f;
            float cv = v + size * (0.5f + (cell / 3 - 1) * 0.27f) - pip / 2f;
            ctx.fill(cu, cv, pip, pip, 0xFF2A2A32);
        }
        ctx.text("DICE", ctx.width() / 2f, v + size + 0.5f, 1.1f, theme.textMuted);
    }

    // ---- Overlay body -------------------------------------------------

    private static final class DiceOverlay implements OverlayContent {
        @Override
        public int height(int rowWidth) {
            return 24;
        }

        @Override
        public void render(GuiGraphics graphics, Font font, ScreenTheme theme, int x, int top,
                           int rowWidth, int mouseX, int mouseY, boolean reachable,
                           int clipTop, int clipBottom) {
            graphics.fill(x, top, x + rowWidth, top + 24, theme.rowBg);
            graphics.drawString(font, "Last roll: " + lastRoll, x + 6, top + 8,
                    theme.textPrimary, theme.textShadow);
            String hint = "click to roll";
            graphics.drawString(font, hint, x + rowWidth - font.width(hint) - 6, top + 8,
                    theme.textFaint, theme.textShadow);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int top,
                                    int rowWidth) {
            roll();
            return true;
        }
    }

    // ---- Full screen --------------------------------------------------

    private static final class DiceScreen extends Screen {
        private final ProgramHost host;

        DiceScreen(ProgramHost host) {
            super(net.minecraft.network.chat.Component.literal("Dice"));
            this.host = host;
        }

        private int panelLeft() {
            return width / 2 - 60;
        }

        private int panelTop() {
            return height / 2 - 50;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);
            ScreenTheme theme = host.theme();
            int left = panelLeft();
            int top = panelTop();
            graphics.fill(left - 2, top - 2, left + 122, top + 102, theme.bodyOuter);
            graphics.fill(left, top, left + 120, top + 100, theme.rowBg);
            graphics.drawCenteredString(font, host.tabletName(), width / 2, top + 8,
                    theme.textMuted);
            graphics.drawCenteredString(font, getTitle(), width / 2, top + 22,
                    theme.textPrimary);
            // The die
            graphics.fill(width / 2 - 16, top + 38, width / 2 + 16, top + 70, 0xFFF5F1E8);
            graphics.drawCenteredString(font, String.valueOf(lastRoll), width / 2, top + 50,
                    0xFF2A2A32);
            graphics.drawCenteredString(font, "click the die to roll", width / 2, top + 78,
                    theme.textFaint);
            String pin = host.isPinned() ? "[unpin overlay]" : "[pin overlay]";
            graphics.drawCenteredString(font, pin, width / 2, top + 90,
                    mouseY >= top + 88 && mouseY < top + 98 ? theme.accent : theme.textMuted);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int top = panelTop();
            if (mouseY >= top + 34 && mouseY < top + 74
                    && mouseX >= width / 2.0 - 20 && mouseX < width / 2.0 + 20) {
                roll();
                return true;
            }
            if (mouseY >= top + 88 && mouseY < top + 98) {
                host.togglePin();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void onClose() {
            // ESC lands on Home, like the built-in apps
            host.goHome();
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    private ExampleAddonClient() {
    }
}
