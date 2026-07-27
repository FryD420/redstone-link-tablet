package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The classic pinned-tablet body (1.7.0, extracted for the 1.10.0
 * program-aware overlay): live signal rows via {@link SignalRowPainter},
 * actions over the same payloads as the GUI. The transient interaction
 * state (held momentary, slider drag, timer flash) migrated here from
 * MiniTabletWindow untouched. The view arrives through a supplier so the
 * window's slot self-heal is always visible.
 */
public class SignalsOverlayContent implements OverlayContent {

    static final int STRIDE = TabletScreen.ROW_HEIGHT + 4;

    private final Supplier<SignalView> view;

    private int heldMomentary = -1;
    private int draggingSlider = -1;
    /** Tap flash for Timer rows, index → millis until it renders lit. */
    private final Map<Integer, Long> timerFlash = new HashMap<>();

    public SignalsOverlayContent(Supplier<SignalView> view) {
        this.view = view;
    }

    private List<Signal> signals() {
        return view.get().signals();
    }

    @Override
    public Program program() {
        return Program.SIGNALS;
    }

    @Override
    public int height(int rowWidth) {
        return Math.max(1, signals().size()) * STRIDE - 4;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, ScreenTheme theme, int x, int top,
                       int rowWidth, int mouseX, int mouseY, boolean reachable,
                       int clipTop, int clipBottom) {
        List<Signal> signals = signals();
        if (signals.isEmpty()) {
            Component hint = Component.translatable("gui.linktablet.overlay.empty");
            graphics.drawString(font, hint,
                    x + (rowWidth - font.width(hint)) / 2, top + (TabletScreen.ROW_HEIGHT - 8) / 2,
                    theme.textFaint, theme.textShadow);
            return;
        }
        int hoveredIndex = mouseX >= 0 ? rowIndexAt(mouseX, mouseY, x, top, rowWidth) : -1;
        for (int i = 0; i < signals.size(); i++) {
            int ry = top + i * STRIDE;
            if (ry + TabletScreen.ROW_HEIGHT < clipTop || ry > clipBottom) continue;
            SignalRowPainter.paint(graphics, font, theme, signals.get(i), x, ry, rowWidth,
                    reachable && i == hoveredIndex,
                    i == heldMomentary || timerFlashActive(i));
        }
    }

    private boolean timerFlashActive(int index) {
        Long until = timerFlash.get(index);
        if (until == null) return false;
        if (Util.getMillis() >= until) {
            timerFlash.remove(index);
            return false;
        }
        return true;
    }

    /** Row index under the cursor, or -1. */
    private int rowIndexAt(double mx, double my, int x, int top, int rowWidth) {
        if (mx < x || mx >= x + rowWidth) return -1;
        List<Signal> signals = signals();
        for (int i = 0; i < signals.size(); i++) {
            int ry = top + i * STRIDE;
            if (my >= ry && my < ry + TabletScreen.ROW_HEIGHT) return i;
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, int x, int top, int rowWidth) {
        int index = rowIndexAt(mx, my, x, top, rowWidth);
        if (index < 0 || button != 0) return false;

        // Mirrors TabletScreen.handleEntryClick minus edit/add/notes
        Signal signal = signals().get(index);
        ModNetworking.SignalTarget target = view.get().target();
        if (signal.slider()) {
            draggingSlider = index;
            sliderRight = x + rowWidth - 4;
            UISounds.tick(1.2F);
            sendSliderValue(index, sliderValueFromMouse(index, mx));
        } else if (signal.timed()) {
            UISounds.toggle(true);
            timerFlash.put(index, Util.getMillis() + 300);
            PacketDistributor.sendToServer(new ModNetworking.TimedSignalPayload(target, index));
        } else if (signal.momentary()) {
            UISounds.toggle(true);
            heldMomentary = index;
            PacketDistributor.sendToServer(new ModNetworking.MomentarySignalPayload(target, index, true));
        } else {
            UISounds.toggle(!signal.active());
            PacketDistributor.sendToServer(new ModNetworking.ToggleSignalPayload(target, index));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my) {
        if (draggingSlider != -1) {
            sendSliderValue(draggingSlider, sliderValueFromMouse(draggingSlider, mx));
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        if (button == 0 && heldMomentary != -1) {
            UISounds.toggle(false);
            releaseMomentary();
        }
        if (button == 0 && draggingSlider != -1) {
            List<Signal> signals = signals();
            if (draggingSlider < signals.size()) {
                UISounds.tick(1.0F + signals.get(draggingSlider).strength() / 15.0F);
            }
            draggingSlider = -1;
        }
    }

    @Override
    public void defocus() {
        releaseMomentary();
        draggingSlider = -1;
    }

    private void releaseMomentary() {
        if (heldMomentary == -1) return;
        PacketDistributor.sendToServer(new ModNetworking.MomentarySignalPayload(
                view.get().target(), heldMomentary, false));
        heldMomentary = -1;
    }

    // ---- Slider mapping (mirrors TabletScreen's list-mode span) ------

    /** Right edge of the slider track, latched at press (drags arrive
     * without body coordinates). */
    private int sliderRight;

    private int sliderValueFromMouse(int index, double mx) {
        List<Signal> signals = signals();
        if (index >= signals.size()) return 0;
        int left = sliderRight - TabletScreen.LIST_SLIDER_W;
        float rel = Mth.clamp((float) ((mx - left) / (sliderRight - left)), 0.0F, 1.0F);
        return signals.get(index).valueFromFraction(rel);
    }

    private void sendSliderValue(int index, int value) {
        List<Signal> signals = signals();
        if (index >= signals.size()) return;
        Signal signal = signals.get(index);
        if (!signal.slider() || signal.strength() == value) return;
        PacketDistributor.sendToServer(new ModNetworking.SetSliderPayload(
                view.get().target(), index, value));
    }
}
