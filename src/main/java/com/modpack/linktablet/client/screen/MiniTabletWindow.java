package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.OverlayPin;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The pinned mini-tablet (1.7.0, tester request): a floating window with
 * the pinned tablet's app rows — live on the HUD, clickable whenever a
 * screen is open (incl. {@link OverlayFocusScreen}'s chat-style mouse
 * capture). Rows are the SAME painter the GUI's list mode uses
 * ({@link AppRowPainter}); actions travel over the same payloads.
 *
 * <p>Compact by design: fixed width, grows with the app count up to
 * {@link #MAX_ROWS}, then wheel-scrolls. No editing here — right-click
 * any row to open the full tablet GUI. The X unpins.
 *
 * <p>Bindings: an inventory-slot pin self-heals when the tablet moves
 * slots (first tablet found wins) and unpins when the player carries no
 * tablet at all; a block pin never unpins by itself — beyond edit range
 * (or with the chunk unloaded) it dims and ignores clicks instead.
 */
public class MiniTabletWindow implements FloatingWindow {

    public static final int W = 176;
    private static final int TITLE_H = 20;
    private static final int CLOSE_SIZE = 10;
    private static final int PAD = 6;
    private static final int MAX_ROWS = 8;
    private static final int STRIDE = TabletScreen.ROW_HEIGHT + 4;

    /** Matches the server's placed-tablet edit range (MAX_BLOCK_DISTANCE_SQ). */
    private static final double MAX_BLOCK_DISTANCE_SQ = 64.0;

    private AppView view;
    private int x;
    private int y;
    private double scroll = 0;
    private boolean draggingTitle = false;
    private double dragDX, dragDY;
    private int heldMomentary = -1;
    private int draggingSlider = -1;
    /** Tap flash for Timer rows, index → millis until it renders lit. */
    private final Map<Integer, Long> timerFlash = new HashMap<>();
    /** Set when a slot binding finds no tablet anywhere — manager prunes. */
    private boolean orphaned = false;

    public MiniTabletWindow(AppView view) {
        this.view = view;
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int defaultX = sw - W - 8;
        this.x = ClientPrefs.overlayX() >= 0 ? ClientPrefs.overlayX() : defaultX;
        this.y = ClientPrefs.overlayY() >= 0 ? ClientPrefs.overlayY() : 8;
    }

    public AppView view() {
        return view;
    }

    // ---- Data binding ------------------------------------------------

    private List<SignalApp> apps() {
        return view.apps();
    }

    /**
     * Whether actions are currently possible: slot pins after self-heal
     * always are; block pins only in range with the chunk loaded.
     */
    private boolean reachable() {
        if (!(view instanceof AppView.Block block)) return !orphaned;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return false;
        BlockPos pos = block.pos();
        if (!(mc.level.getBlockEntity(pos) instanceof com.modpack.linktablet.block.TabletBlockEntity)) {
            return false;
        }
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= MAX_BLOCK_DISTANCE_SQ;
    }

    /**
     * Keeps a slot binding pointed at a tablet: if the pinned slot no
     * longer holds one (moved, dropped), re-bind to the first tablet in
     * the inventory; with none anywhere, mark the window for pruning.
     */
    private void selfHeal() {
        if (!(view instanceof AppView.Slot slot)) return;
        if (slot.stack().getItem() instanceof TabletItem) {
            orphaned = false;
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return; // world still loading — don't judge yet
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof TabletItem) {
                view = new AppView.Slot(i);
                OverlayPin.persist(view);
                orphaned = false;
                return;
            }
        }
        orphaned = true;
    }

    @Override
    public boolean shouldClose() {
        selfHeal();
        return orphaned;
    }

    @Override
    public void onClose() {
        // X-click = unpin (the window IS the pin)
        releaseMomentary();
        OverlayPin.clear();
    }

    @Override
    public void onPrune() {
        releaseMomentary();
        OverlayPin.clear();
    }

    // ---- Geometry ----------------------------------------------------

    private int visibleRows() {
        return Mth.clamp(apps().size(), 1, MAX_ROWS);
    }

    private int height() {
        return TITLE_H + PAD + visibleRows() * STRIDE - 4 + PAD;
    }

    private int rowX() {
        return x + PAD;
    }

    private int rowWidth() {
        return W - 2 * PAD;
    }

    private int bodyTop() {
        return y + TITLE_H + PAD;
    }

    private int bodyBottom() {
        return y + height() - PAD;
    }

    private double maxScroll() {
        return Math.max(0, apps().size() * STRIDE - 4 - (bodyBottom() - bodyTop()));
    }

    @Override
    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + W && my >= y && my < y + height();
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

    /** Row index under the cursor, or -1. */
    private int rowIndexAt(double mx, double my) {
        if (my < bodyTop() || my >= bodyBottom()) return -1;
        if (mx < rowX() || mx >= rowX() + rowWidth()) return -1;
        List<SignalApp> apps = apps();
        for (int i = 0; i < apps.size(); i++) {
            int ry = bodyTop() + i * STRIDE - (int) scroll;
            if (my >= ry && my < ry + TabletScreen.ROW_HEIGHT) return i;
        }
        return -1;
    }

    // ---- Render ------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        selfHeal();
        ScreenTheme t = view.theme();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int h = height();
        scroll = Mth.clamp(scroll, 0, maxScroll());

        // Keep the window reachable across screen resizes
        x = Mth.clamp(x, 2, Math.max(2, graphics.guiWidth() - W - 2));
        y = Mth.clamp(y, 2, Math.max(2, graphics.guiHeight() - h - 2));

        boolean reachable = reachable();

        // Same elevation rule as NoteWindow: clear the underlying GUI's
        // self-lifting item icons, stay under vanilla tooltips.
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 350);

        // Frame (the NoteWindow.paintFrame recipe at dynamic height)
        graphics.fill(x + 3, y + 3, x + W + 3, y + h + 3, 0x50000000);
        Chrome.panel(graphics, x, y, W, h, t);
        String title = TextFit.ellipsize(font,
                Component.translatable("gui.linktablet.overlay.title").getString(),
                W - TITLE_H - 24);
        graphics.drawString(font, title, x + 10, y + 7, t.textPrimary, t.textShadow);
        Chrome.railH(graphics, x + 4, y + TITLE_H - 3, W - 8, t.bodyOuter);

        // Unpin X
        int cx = x + W - CLOSE_SIZE - 7;
        int cy = y + 5;
        int closeColor = mouseX >= 0 && overCloseButton(mouseX, mouseY) ? t.glyphHover : t.textFaint;
        for (int i = 0; i < CLOSE_SIZE - 2; i++) {
            graphics.fill(cx + 1 + i, cy + 1 + i, cx + 3 + i, cy + 3 + i, closeColor);
            graphics.fill(cx + CLOSE_SIZE - 3 - i, cy + 1 + i, cx + CLOSE_SIZE - 1 - i, cy + 3 + i, closeColor);
        }

        // Rows, clipped to the body
        List<SignalApp> apps = apps();
        graphics.enableScissor(x + 1, bodyTop(), x + W - 1, bodyBottom());
        if (apps.isEmpty()) {
            Component hint = Component.translatable("gui.linktablet.overlay.empty");
            graphics.drawString(font, hint,
                    x + (W - font.width(hint)) / 2, bodyTop() + (TabletScreen.ROW_HEIGHT - 8) / 2,
                    t.textFaint, t.textShadow);
        }
        int hoveredIndex = mouseX >= 0 ? rowIndexAt(mouseX, mouseY) : -1;
        for (int i = 0; i < apps.size(); i++) {
            int ry = bodyTop() + i * STRIDE - (int) scroll;
            if (ry + TabletScreen.ROW_HEIGHT < bodyTop() || ry > bodyBottom()) continue;
            AppRowPainter.paint(graphics, font, t, apps.get(i), rowX(), ry, rowWidth(),
                    reachable && i == hoveredIndex,
                    i == heldMomentary || timerFlashActive(i));
        }
        graphics.disableScissor();

        // Out-of-range block pin: dim the body so dead rows read as dead
        if (!reachable) {
            graphics.fill(x + 1, bodyTop(), x + W - 1, bodyBottom(), 0x90000000);
        }

        graphics.pose().popPose();
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

    // ---- Input -------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overTitleBar(mx, my)) {
            draggingTitle = true;
            dragDX = mx - x;
            dragDY = my - y;
            return true;
        }
        if (!contains(mx, my)) return false;

        int index = rowIndexAt(mx, my);
        if (index < 0 || !reachable()) return true; // window body: consume, no-op

        if (button == 1) {
            // Right-click: open the full tablet GUI on this tablet
            UISounds.open();
            Minecraft.getInstance().setScreen(new TabletScreen(view));
            return true;
        }
        if (button != 0) return true;

        // Mirrors TabletScreen.handleEntryClick minus edit/add/notes
        SignalApp app = apps().get(index);
        if (app.slider()) {
            draggingSlider = index;
            UISounds.tick(1.2F);
            sendSliderValue(index, sliderValueFromMouse(index, mx));
        } else if (app.timed()) {
            UISounds.toggle(true);
            timerFlash.put(index, Util.getMillis() + 300);
            PacketDistributor.sendToServer(new ModNetworking.TimedAppPayload(view.target(), index));
        } else if (app.momentary()) {
            UISounds.toggle(true);
            heldMomentary = index;
            PacketDistributor.sendToServer(new ModNetworking.MomentaryAppPayload(view.target(), index, true));
        } else {
            UISounds.toggle(!app.active());
            PacketDistributor.sendToServer(new ModNetworking.ToggleAppPayload(view.target(), index));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy,
                                int screenW, int screenH) {
        if (draggingTitle) {
            x = Mth.clamp((int) (mx - dragDX), 2, screenW - W - 2);
            y = Mth.clamp((int) (my - dragDY), 2, screenH - height() - 2);
            return true;
        }
        if (draggingSlider != -1) {
            sendSliderValue(draggingSlider, sliderValueFromMouse(draggingSlider, mx));
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        if (draggingTitle) {
            draggingTitle = false;
            ClientPrefs.setOverlayPos(x, y);
        }
        if (button == 0 && heldMomentary != -1) {
            UISounds.toggle(false);
            releaseMomentary();
        }
        if (button == 0 && draggingSlider != -1) {
            List<SignalApp> apps = apps();
            if (draggingSlider < apps.size()) {
                UISounds.tick(1.0F + apps.get(draggingSlider).strength() / 15.0F);
            }
            draggingSlider = -1;
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - sy * 16, 0, maxScroll());
        return true;
    }

    @Override
    public boolean wantsKeyboard() {
        return false;
    }

    @Override
    public void keyPressed(int keyCode, int scanCode, int modifiers) {
    }

    @Override
    public void charTyped(char chr, int modifiers) {
    }

    @Override
    public void defocus() {
        // Screen closing (or focus loss) mid-press: never leave a held
        // signal on, never keep a stale drag
        releaseMomentary();
        draggingSlider = -1;
        draggingTitle = false;
    }

    private void releaseMomentary() {
        if (heldMomentary == -1) return;
        PacketDistributor.sendToServer(
                new ModNetworking.MomentaryAppPayload(view.target(), heldMomentary, false));
        heldMomentary = -1;
    }

    // ---- Slider mapping (mirrors TabletScreen's list-mode span) ------

    private int sliderValueFromMouse(int index, double mx) {
        List<SignalApp> apps = apps();
        if (index >= apps.size()) return 0;
        int right = rowX() + rowWidth() - 4;
        int left = right - TabletScreen.LIST_SLIDER_W;
        float rel = Mth.clamp((float) ((mx - left) / (right - left)), 0.0F, 1.0F);
        return apps.get(index).valueFromFraction(rel);
    }

    private void sendSliderValue(int index, int value) {
        List<SignalApp> apps = apps();
        if (index >= apps.size()) return;
        SignalApp app = apps.get(index);
        if (!app.slider() || app.strength() == value) return;
        PacketDistributor.sendToServer(new ModNetworking.SetSliderPayload(view.target(), index, value));
    }
}
