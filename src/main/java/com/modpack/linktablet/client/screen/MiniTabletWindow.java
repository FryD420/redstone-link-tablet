package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.api.client.OverlayContent;
import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.ClientPrefs;
import com.modpack.linktablet.client.OverlayPin;
import com.modpack.linktablet.client.TextFit;
import com.modpack.linktablet.client.screen.chrome.Chrome;
import com.modpack.linktablet.item.TabletItem;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The pinned mini-tablet (1.7.0, tester request; program-aware since
 * 1.10.0): a floating window live on the HUD and clickable whenever a
 * screen is open (incl. {@link OverlayFocusScreen}'s chat-style mouse
 * capture). The window owns the CHROME — frame, title, drag, unpin-X,
 * scroll, reachability — and the body pane is an {@link OverlayContent}
 * picked by the pinned program ({@link SignalsOverlayContent} rows by
 * default, the clock face for {@code @clock} pins).
 *
 * <p>Right-click anywhere in the body opens the pinned program's full
 * screen on this tablet. The X unpins.
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
    /** Body height cap — the classic 8 signal rows' worth. */
    private static final int MAX_BODY_H = 8 * SignalsOverlayContent.STRIDE - 4;

    /** Matches the server's placed-tablet edit range (MAX_BLOCK_DISTANCE_SQ). */
    private static final double MAX_BLOCK_DISTANCE_SQ = 64.0;

    private SignalView view;
    private final com.modpack.linktablet.api.TabletProgram program;
    private final OverlayContent content;
    private int x;
    private int y;
    private double scroll = 0;
    private boolean draggingTitle = false;
    private double dragDX, dragDY;
    /** Set when a slot binding finds no tablet anywhere — manager prunes. */
    private boolean orphaned = false;

    public MiniTabletWindow(SignalView view, com.modpack.linktablet.api.TabletProgram program) {
        this.view = view;
        this.program = program;
        this.content = contentFor(program);
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int defaultX = sw - W - 8;
        this.x = ClientPrefs.overlayX() >= 0 ? ClientPrefs.overlayX() : defaultX;
        this.y = ClientPrefs.overlayY() >= 0 ? ClientPrefs.overlayY() : 8;
    }

    public SignalView view() {
        return view;
    }

    public com.modpack.linktablet.api.TabletProgram program() {
        return program;
    }

    /** Built-ins keep their bespoke contents; addon programs supply
     * theirs through the API client. An addon pin restored without its
     * mod (or without overlay content) degrades to the signals rows —
     * the pre-1.10 pin look, never a dead window. */
    private OverlayContent contentFor(com.modpack.linktablet.api.TabletProgram program) {
        if (program instanceof Program builtin) {
            return switch (builtin) {
                case LAUNCHER -> new LauncherOverlayContent(this::view);
                case CLOCK -> new ClockOverlayContent();
                case CALCULATOR -> new CalculatorOverlayContent();
                case GAUGES -> new GaugesOverlayContent(this::view);
                case MONITOR -> new MonitorOverlayContent(this::view);
                case TWITCH -> new TwitchOverlayContent(this::view);
                default -> new SignalsOverlayContent(this::view);
            };
        }
        var client = com.modpack.linktablet.client.ProgramClients.get(program.key());
        OverlayContent content = client == null ? null : client.createOverlay(
                new com.modpack.linktablet.client.ProgramHostImpl(this::view, program));
        return content != null ? content : new SignalsOverlayContent(this::view);
    }

    // ---- Data binding ------------------------------------------------

    /**
     * Whether actions are currently possible: slot pins after self-heal
     * always are; block pins only in range with the chunk loaded.
     */
    private boolean reachable() {
        if (!(view instanceof SignalView.Block block)) return !orphaned;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return false;
        BlockPos pos = block.pos();
        if (!(mc.level.getBlockEntity(pos) instanceof com.modpack.linktablet.block.TabletBlockEntity)) {
            return false;
        }
        // Sable sub-level (1.10.1): a pinned vehicle tablet sits at plot
        // coordinates — localize the eye or the pin always reads distant
        return com.modpack.linktablet.compat.SableCompat
                .localizeNear(mc.level, pos, player.getEyePosition())
                .distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                <= MAX_BLOCK_DISTANCE_SQ;
    }

    /**
     * Keeps a slot binding pointed at a tablet: if the pinned slot no
     * longer holds one (moved, dropped), re-bind to the first tablet in
     * the inventory; with none anywhere, mark the window for pruning.
     */
    private void selfHeal() {
        if (!(view instanceof SignalView.Slot slot)) return;
        if (slot.stack().getItem() instanceof TabletItem) {
            orphaned = false;
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return; // world still loading — don't judge yet
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() instanceof TabletItem) {
                view = new SignalView.Slot(i);
                OverlayPin.persist(view, program);
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
        content.defocus();
        OverlayPin.clear();
    }

    @Override
    public void onPrune() {
        content.defocus();
        OverlayPin.clear();
    }

    // ---- Geometry ----------------------------------------------------

    private int bodyH() {
        return Mth.clamp(content.height(rowWidth()), 1, MAX_BODY_H);
    }

    private int height() {
        return TITLE_H + PAD + bodyH() + PAD;
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
        return Math.max(0, content.height(rowWidth()) - (bodyBottom() - bodyTop()));
    }

    @Override
    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + W && my >= y && my < y + height();
    }

    private boolean overTitleBar(double mx, double my) {
        return mx >= x && mx < x + W - TITLE_H && my >= y && my < y + TITLE_H;
    }

    private boolean overBody(double mx, double my) {
        return mx >= rowX() && mx < rowX() + rowWidth()
                && my >= bodyTop() && my < bodyBottom();
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
                view.customName() != null
                        ? view.customName().getString()
                        : program == Program.SIGNALS
                        ? Component.translatable("gui.linktablet.overlay.title").getString()
                        : program.displayName().getString(),
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

        // Body pane, clipped
        graphics.enableScissor(x + 1, bodyTop(), x + W - 1, bodyBottom());
        content.render(graphics, font, t, rowX(), bodyTop() - (int) scroll, rowWidth(),
                mouseX, mouseY, reachable, bodyTop(), bodyBottom());
        graphics.disableScissor();

        // Out-of-range block pin: dim the body so dead rows read as dead
        if (!reachable) {
            graphics.fill(x + 1, bodyTop(), x + W - 1, bodyBottom(), 0x90000000);
        }

        graphics.pose().popPose();
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
        if (!overBody(mx, my) || !reachable()) return true; // consume, no-op

        if (button == 1) {
            // Right-click: expand to the pinned program's full screen on
            // this tablet (the overlay IS the program — not a resume point)
            com.modpack.linktablet.client.ClientHooks.openProgram(program, view);
            return true;
        }
        content.mouseClicked(mx, my, button, rowX(), bodyTop() - (int) scroll, rowWidth());
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
        return content.mouseDragged(mx, my);
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        if (draggingTitle) {
            draggingTitle = false;
            ClientPrefs.setOverlayPos(x, y);
        }
        content.mouseReleased(mx, my, button);
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
        content.defocus();
        draggingTitle = false;
    }
}
