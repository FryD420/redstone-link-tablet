package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.theme.ScreenTheme;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * The procedural gauge dial (1.10.0 OS suite), GUI side: a semicircular
 * tick arc with a rotating needle — pure {@code fill()} mechanism art,
 * per the chrome rule (surfaces blit the atlas, mechanisms stay
 * procedural). Shared by {@link GaugesScreen}'s tiles and the pinned
 * overlay's rows; the world-pass twin lives in TabletScreenRenderer
 * (quads, not fills — the batching rules differ).
 */
final class GaugeDialPainter {

    static final int MAX_VALUE = com.modpack.linktablet.frequency.Gauge.MAX_VALUE;

    /**
     * Dial centered at (cx, cy): 16 ticks on the upper semicircle
     * (value 0 = left, 15 = right), lit up to the value in the gauge's
     * color, plus the needle and a center hub.
     */
    static void dial(GuiGraphics graphics, ScreenTheme theme, int color, int value,
                     int cx, int cy, int radius) {
        for (int t = 0; t <= MAX_VALUE; t++) {
            float angle = (float) Math.toRadians(180.0 - t * 180.0 / MAX_VALUE);
            int tx = cx + Math.round(radius * Mth.cos(angle));
            int ty = cy - Math.round(radius * Mth.sin(angle));
            boolean lit = value > 0 && t <= value;
            graphics.fill(tx - 1, ty - 1, tx + 1, ty + 1,
                    lit ? color | 0xFF000000 : theme.switchOff);
        }
        // Needle: up-vector swung -90° (value 0, pointing left) → +90°
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees(-90f + value * 180f / MAX_VALUE));
        graphics.fill(-1, -(radius - 3), 1, 0, value > 0 ? color | 0xFF000000 : theme.textMuted);
        graphics.pose().popPose();
        // Hub
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, theme.textMuted);
    }

    private GaugeDialPainter() {
    }
}
