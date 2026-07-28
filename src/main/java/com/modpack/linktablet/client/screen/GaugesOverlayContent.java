package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.api.client.OverlayContent;
import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.frequency.Gauge;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * The pinned Gauges body (1.10.0): one row per gauge — mini dial, name,
 * big value — live on the HUD. This is the "vehicle HUD" the per-app
 * pin exists for: pin a gauges tablet and drive. Read-only; right-click
 * the window opens the full GaugesScreen.
 */
public class GaugesOverlayContent implements OverlayContent {

    private static final int ROW_H = 26;

    private final Supplier<SignalView> view;

    public GaugesOverlayContent(Supplier<SignalView> view) {
        this.view = view;
    }

    @Override
    public int height(int rowWidth) {
        return Math.max(1, view.get().gauges().size()) * ROW_H - 2;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, ScreenTheme theme, int x, int top,
                       int rowWidth, int mouseX, int mouseY, boolean reachable,
                       int clipTop, int clipBottom) {
        SignalView v = view.get();
        List<Gauge> gauges = v.gauges();
        if (gauges.isEmpty()) {
            Component hint = Component.translatable("gui.linktablet.gauge.empty");
            graphics.drawString(font, hint, x + (rowWidth - font.width(hint)) / 2,
                    top + 8, theme.textFaint, theme.textShadow);
            return;
        }
        for (int i = 0; i < gauges.size(); i++) {
            int ry = top + i * ROW_H;
            if (ry + ROW_H < clipTop || ry > clipBottom) continue;
            Gauge gauge = gauges.get(i);
            int value = v.gaugeReading(i);
            graphics.fill(x, ry, x + rowWidth, ry + ROW_H - 2, theme.rowBg);
            GaugeDialPainter.dial(graphics, theme, gauge.color(), value,
                    x + 14, ry + 17, 10);
            String name = TextFit.ellipsize(font, gauge.name(), rowWidth - 60);
            graphics.drawString(font, name, x + 30, ry + 8, theme.textPrimary, theme.textShadow);
            String level = String.valueOf(value);
            graphics.pose().pushPose();
            graphics.pose().translate(x + rowWidth - 6 - font.width(level) * 1.5f, ry + 5, 0);
            graphics.pose().scale(1.5f, 1.5f, 1f);
            graphics.drawString(font, level, 0, 0,
                    value > 0 ? (gauge.color() | 0xFF000000) : theme.textFaint, theme.textShadow);
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button, int x, int top, int rowWidth) {
        return false; // read-only pane; the window handles right-click-to-open
    }
}
