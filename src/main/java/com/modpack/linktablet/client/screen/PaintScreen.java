package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.PaintCanvas;
import com.modpack.linktablet.block.TabletBlockEntity;
import com.modpack.linktablet.block.TabletScreenMath;
import com.modpack.linktablet.client.SignalView;
import com.modpack.linktablet.client.UISounds;
import com.modpack.linktablet.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 🎨 "Paint" (persisted, 1.11.0): a sixteen-color doodle pad — left-click
 * paints, right-click erases, drag to sweep, C clears. Held/slotted
 * tablets edit their own {@link PaintCanvas#COLS}×{@link PaintCanvas#ROWS}
 * slice; a placed tablet edits its (possibly merged) wall canvas, stitched
 * live from every member's own slice by {@link #stitchArgb} — the same
 * static helper Task 4's kiosk-face renderer calls. Strokes accumulate as
 * pending cells and flush as a {@link ModNetworking.PaintStrokePayload}
 * once per client tick while dragging, and again on release/close (the
 * {@code BlockSliderDrag} per-tick precedent); C clears with a
 * {@link ModNetworking.PaintClearPayload}. Block-bound screens re-read the
 * synced canvas fresh every frame (the {@code MonitorScreen#channels()}
 * precedent), with any not-yet-flushed pending cells painted back over
 * that fresh read so an in-progress stroke never visibly reverts.
 */
public class PaintScreen extends ArcadeScreen {

    private static final int SWATCH = 8;
    /** PaintStrokePayload's wire cap (ByteBufCodecs.list(64)) — a flush
     * larger than this chunks into multiple packets. */
    private static final int MAX_CELLS_PER_PACKET = 64;
    /** Breathing room kept outside the cabinet so an adaptively huge
     * merged-surface canvas never crowds the screen edge. */
    private static final int MARGIN = 40;

    /** The dye palette (same family as the signal color presets). Public:
     * Task 4's face renderer maps stitched palette indices through this
     * same array — presentation stays here, not on PaintCanvas. */
    public static final int[] PALETTE = {
            0xFFF9FFFE, 0xFFB02E26, 0xFFF9801D, 0xFFFED83D,
            0xFF80C71F, 0xFF5E7C16, 0xFF3AB3DA, 0xFF169C9C,
            0xFF3C44AA, 0xFF8932B8, 0xFFC74EBD, 0xFFF38BAA,
            0xFF835432, 0xFF9D9D97, 0xFF474F52, 0xFF1D1D21};

    /** Grid size for the current view (1×PaintCanvas dims for held/slot;
     * surfaceW/H × PaintCanvas dims for a placed, possibly merged, wall). */
    private int cols = PaintCanvas.COLS;
    private int rows = PaintCanvas.ROWS;
    /** Adaptive cell pixel size — computed in {@link #layout()} so a huge
     * merged canvas still fits the 240-unit GUI budget. */
    private int cell = 7;

    /** Rendered content, ARGB per cell (0 = blank), row-major over
     * {@link #cols}. Block-bound screens rebuild this every frame from the
     * synced BE state; held/slot screens build it once and paint directly
     * into it (see {@link #layout()}). */
    private int[] canvas;
    /** Cells painted locally since the last flush: index → palette color
     * (0 = erase, 1..MAX_COLOR = paint). Doubles as the block-bound
     * render overlay (painted back over each frame's fresh BE read) and
     * the network send queue. */
    private final Map<Integer, Byte> pending = new LinkedHashMap<>();
    private boolean dragging = false;
    private int selected = 6;

    public PaintScreen(SignalView view, boolean returnToTablet) {
        super("paint", view, returnToTablet);
    }

    @Override
    protected int boardW() {
        return cols * cell;
    }

    @Override
    protected int boardH() {
        return rows * cell + SWATCH + 4;
    }

    // ------------------------------------------------------------------
    // Layout + canvas state
    // ------------------------------------------------------------------

    private void layout() {
        cols = Math.max(1, view.surfaceW()) * PaintCanvas.COLS;
        rows = Math.max(1, view.surfaceH()) * PaintCanvas.ROWS;
        int availW = Math.max(1, width - PAD * 2 - MARGIN);
        int availH = Math.max(1, height - HEADER - PAD * 2 - 4 - SWATCH - 4 - MARGIN);
        cell = Mth.clamp(Math.min(availW / cols, availH / rows), 2, 7);

        if (view instanceof SignalView.Block block) {
            TabletBlockEntity controller = resolveController(block.pos());
            int[] baseline = controller != null ? stitchArgb(controller) : new int[cols * rows];
            for (Map.Entry<Integer, Byte> e : pending.entrySet()) {
                int idx = e.getKey();
                if (idx >= 0 && idx < baseline.length) {
                    baseline[idx] = paletteArgb(e.getValue());
                }
            }
            canvas = baseline;
        } else if (canvas == null) {
            initItemCanvas();
        }
    }

    private void initItemCanvas() {
        byte[] raw = view.paintCanvas();
        canvas = new int[cols * rows];
        int n = Math.min(canvas.length, raw.length);
        for (int i = 0; i < n; i++) {
            canvas[i] = paletteArgb(raw[i]);
        }
    }

    private static int paletteArgb(byte color) {
        return color == 0 ? 0 : PALETTE[color - 1];
    }

    @org.jetbrains.annotations.Nullable
    private static TabletBlockEntity resolveController(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        return mc.level.getBlockEntity(pos) instanceof TabletBlockEntity be ? be.resolveController() : null;
    }

    /**
     * Client-side stitch of a (possibly 1×1) surface into one continuous
     * ARGB grid, palette-mapped — Task 4's kiosk-face renderer calls this
     * too. Reads each member's OWN paint canvas array without mutating it
     * (that array may be the BE's live reference, per SignalView.Block's
     * contract).
     */
    public static int[] stitchArgb(TabletBlockEntity controller) {
        int surfaceW = Math.max(1, controller.getSurfaceW());
        int surfaceH = Math.max(1, controller.getSurfaceH());
        int[] argb = new int[surfaceW * PaintCanvas.COLS * surfaceH * PaintCanvas.ROWS];
        Level level = controller.getLevel();
        BlockState state = controller.getBlockState();
        for (int dx = 0; dx < surfaceW; dx++) {
            for (int dy = 0; dy < surfaceH; dy++) {
                TabletBlockEntity member = stitchMemberAt(level, controller, state, dx, dy);
                if (member == null) continue;
                byte[] local = member.getPaintCanvas();
                for (int ly = 0; ly < PaintCanvas.ROWS; ly++) {
                    for (int lx = 0; lx < PaintCanvas.COLS; lx++) {
                        byte color = local[ly * PaintCanvas.COLS + lx];
                        if (color == 0) continue;
                        int contX = dx * PaintCanvas.COLS + lx;
                        int contY = dy * PaintCanvas.ROWS + ly;
                        argb[PaintCanvas.contIndex(contX, contY, surfaceW)] = paletteArgb(color);
                    }
                }
            }
        }
        return argb;
    }

    /** Mirrors {@code ModNetworking#memberAt}'s offset walk (screenRight/
     * screenDown under the controller's FACING — never plain world axes). */
    @org.jetbrains.annotations.Nullable
    private static TabletBlockEntity stitchMemberAt(Level level, TabletBlockEntity controller,
                                                      BlockState state, int dx, int dy) {
        if (dx == 0 && dy == 0) return controller;
        if (level == null) return null;
        BlockPos pos = controller.getBlockPos()
                .relative(TabletScreenMath.screenRight(state), dx)
                .relative(TabletScreenMath.screenDown(state), dy);
        if (!level.isLoaded(pos)) return null;
        return level.getBlockEntity(pos) instanceof TabletBlockEntity member ? member : null;
    }

    // ------------------------------------------------------------------
    // Strokes
    // ------------------------------------------------------------------

    private boolean apply(double mouseX, double mouseY, int button) {
        int cx = (int) Math.floor((mouseX - boardX()) / cell);
        int cy = (int) Math.floor((mouseY - boardY()) / cell);
        if (cx < 0 || cx >= cols || cy < 0 || cy >= rows) return false;
        int index = cy * cols + cx;
        byte color = (byte) (button == 1 ? 0 : selected + 1);
        canvas[index] = paletteArgb(color);
        pending.put(index, color);
        return true;
    }

    private void flush() {
        if (pending.isEmpty()) return;
        List<ModNetworking.PaintCell> cells = new ArrayList<>(pending.size());
        for (Map.Entry<Integer, Byte> e : pending.entrySet()) {
            cells.add(new ModNetworking.PaintCell(e.getKey(), e.getValue()));
        }
        pending.clear();
        for (int start = 0; start < cells.size(); start += MAX_CELLS_PER_PACKET) {
            List<ModNetworking.PaintCell> chunk =
                    cells.subList(start, Math.min(start + MAX_CELLS_PER_PACKET, cells.size()));
            PacketDistributor.sendToServer(
                    new ModNetworking.PaintStrokePayload(view.target(), List.copyOf(chunk)));
        }
    }

    @Override
    public void tick() {
        if (dragging) flush();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Palette strip below the canvas
        int py = boardY() + rows * cell + 4;
        if (mouseY >= py && mouseY < py + SWATCH) {
            int index = (int) ((mouseX - boardX()) / (SWATCH + 1));
            if (index >= 0 && index < PALETTE.length) {
                selected = index;
                UISounds.tick(1.3F);
                return true;
            }
        }
        if (apply(mouseX, mouseY, button)) {
            dragging = true;
            UISounds.tick(button == 1 ? 0.9F : 1.1F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (apply(mouseX, mouseY, button)) {
            dragging = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            flush();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 67) { // C clears
            pending.clear();
            if (canvas != null) java.util.Arrays.fill(canvas, 0);
            PacketDistributor.sendToServer(new ModNetworking.PaintClearPayload(view.target()));
            UISounds.page();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        dragging = false;
        flush();
        super.onClose();
    }

    // ------------------------------------------------------------------
    // Render
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layout();
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCabinet(graphics, "🎨");
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < canvas.length; i++) {
            if (canvas[i] == 0) continue;
            int x = bx + (i % cols) * cell;
            int y = by + (i / cols) * cell;
            graphics.fill(x, y, x + cell, y + cell, canvas[i]);
        }
        int py = by + rows * cell + 4;
        for (int i = 0; i < PALETTE.length; i++) {
            int x = bx + i * (SWATCH + 1);
            graphics.fill(x, py, x + SWATCH, py + SWATCH, PALETTE[i]);
            if (i == selected) {
                graphics.fill(x, py - 2, x + SWATCH, py - 1, 0xFFE8EAF0);
            }
        }
    }
}
