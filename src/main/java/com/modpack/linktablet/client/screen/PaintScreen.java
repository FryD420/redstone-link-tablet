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
 * 🎨 "Paint" (persisted 1.11.0; tools 1.11.x): a sixteen-color canvas —
 * brush/fill/line/rect/eyedropper tools with conflict-aware undo
 * (left = paint, right = erase, C clears, Z undoes; every tool is a
 * client-side gesture that decomposes into ordinary stroke cells). Held/slotted
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

    /** Active drawing tool (1.11.x Paint v2). BRUSH is the classic
     * paint/erase; the rest are client-side gestures that decompose to
     * ordinary cells — see the design spec. */
    enum Tool { BRUSH, FILL, LINE, RECT, EYEDROPPER }

    private Tool tool = Tool.BRUSH;
    /** Undo history, newest first, depth-capped at MAX_UNDO — one entry
     * per finished gesture (brush stroke, fill, committed shape).
     * Session-local: cleared with the screen. */
    private static final int MAX_UNDO = 32;
    private final java.util.ArrayDeque<UndoStep> history = new java.util.ArrayDeque<>();

    /** Cells of the in-progress gesture: index → {beforeArgb,
     * afterArgb}. before is captured on FIRST touch (the stitched
     * baseline on block views — same array the render overlay uses),
     * after is last-write-wins. endGesture() turns it into one
     * UndoStep. */
    private final java.util.LinkedHashMap<Integer, int[]> gesture = new java.util.LinkedHashMap<>();

    /** One finished gesture (brush stroke, fill, committed shape) →
     * one undo step. No-op when nothing was painted. */
    private void endGesture() {
        if (gesture.isEmpty()) return;
        java.util.List<UndoCell> cells = new java.util.ArrayList<>(gesture.size());
        for (java.util.Map.Entry<Integer, int[]> e : gesture.entrySet()) {
            cells.add(new UndoCell(e.getKey(), e.getValue()[0], e.getValue()[1]));
        }
        gesture.clear();
        history.push(new UndoStep(cells));
        while (history.size() > MAX_UNDO) history.removeLast();
    }

    /** Inverse of {@link #paletteArgb}: 0 for blank (or any unknown
     * value), else the 1-based palette color. */
    private static byte paletteIndexOf(int argb) {
        for (int i = 0; i < PALETTE.length; i++) {
            if (PALETTE[i] == argb) return (byte) (i + 1);
        }
        return 0;
    }

    private record UndoCell(int index, int beforeArgb, int afterArgb) {}

    private record UndoStep(java.util.List<UndoCell> cells) {}

    private void selectTool(Tool t) {
        cancelShape(); // a tool switch aborts any in-progress shape (stale-anchor commit guard)
        tool = t;
        UISounds.tick(1.3F);
    }

    /** Steps back one gesture, CONFLICT-AWARE: a cell is only reverted
     * while it still holds this step's after-value OR its before-value —
     * cells someone else painted over since (a third value) are left
     * alone (their work survives; see the design spec). Accepting the
     * before-value too closes the flush→server-echo window on block
     * views: a just-committed gesture's cells can briefly read their
     * before-value again (canvas rebuilds each frame from synced BE
     * state; pending was already cleared by flush) between the client's
     * flush and the server's echo, and without this the step would be
     * silently consumed with nothing reverted. Undone cells ride the
     * normal pending→stroke wire. */
    private void undo() {
        if (canvas == null) return;
        UndoStep step = history.poll();
        if (step == null) return;
        for (UndoCell cell : step.cells()) {
            if (cell.index() < canvas.length) {
                int now = canvas[cell.index()];
                if (now == cell.afterArgb() || now == cell.beforeArgb()) {
                    canvas[cell.index()] = cell.beforeArgb();
                    pending.put(cell.index(), paletteIndexOf(cell.beforeArgb()));
                }
            }
        }
        flush();
    }

    private int toolRowY() {
        return boardY() + rows * cell + 4;
    }

    private int swatchRowY() {
        return toolRowY() + SWATCH + 2;
    }

    public PaintScreen(SignalView view, boolean returnToTablet) {
        super("paint", view, returnToTablet);
    }

    @Override
    protected int boardW() {
        return cols * cell;
    }

    @Override
    protected int boardH() {
        // canvas + gap + tool row + gap + swatch row
        return rows * cell + 4 + SWATCH + 2 + SWATCH;
    }

    // ------------------------------------------------------------------
    // Layout + canvas state
    // ------------------------------------------------------------------

    private void layout() {
        cols = Math.max(1, view.surfaceW()) * PaintCanvas.COLS;
        rows = Math.max(1, view.surfaceH()) * PaintCanvas.ROWS;
        int availW = Math.max(1, width - PAD * 2 - MARGIN);
        int availH = Math.max(1, height - HEADER - PAD * 2 - 4 - SWATCH - 2 - SWATCH - 4 - MARGIN);
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

    /** Anchor of the in-progress stroke (continuous cell index), -1 when
     * no previous cell — fast drags deliver cursor positions several
     * cells apart, and each new position draws a {@link PaintCanvas#line}
     * from this anchor so the stroke never gaps. */
    private int lastStrokeCell = -1;

    /** In-progress shape gesture (LINE/RECT): anchor + current cell,
     * −1 = none. The preview is a pure render overlay — cells enter
     * pending only on commit (release), never mid-drag. */
    private int shapeStartX = -1, shapeStartY = -1, shapeEndX = -1, shapeEndY = -1;
    private int shapeButton = 0;

    private void shapeCells(PaintCanvas.CellVisitor visitor) {
        if (tool == Tool.RECT) {
            PaintCanvas.rectOutline(shapeStartX, shapeStartY, shapeEndX, shapeEndY, visitor);
        } else {
            PaintCanvas.line(shapeStartX, shapeStartY, shapeEndX, shapeEndY, visitor);
        }
    }

    private void cancelShape() {
        shapeStartX = shapeStartY = shapeEndX = shapeEndY = -1;
    }

    private boolean apply(double mouseX, double mouseY, int button) {
        // canvas is first populated by layout() (called from render()); a
        // mouse event arriving before the first frame must not NPE.
        if (canvas == null) return false;
        int cx = (int) Math.floor((mouseX - boardX()) / cell);
        int cy = (int) Math.floor((mouseY - boardY()) / cell);
        if (cx < 0 || cx >= cols || cy < 0 || cy >= rows) return false;
        byte color = (byte) (button == 1 ? 0 : selected + 1);
        if (dragging && lastStrokeCell >= 0) {
            PaintCanvas.line(lastStrokeCell % cols, lastStrokeCell / cols, cx, cy,
                    (x, y) -> paintCell(y * cols + x, color));
        } else {
            paintCell(cy * cols + cx, color);
        }
        lastStrokeCell = cy * cols + cx;
        return true;
    }

    private void paintCell(int index, byte color) {
        gesture.computeIfAbsent(index, i -> new int[]{canvas[i], 0})[1] = paletteArgb(color);
        canvas[index] = paletteArgb(color);
        pending.put(index, color);
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
        // Tool row, then palette strip, below the canvas
        int ty = toolRowY();
        if (mouseY >= ty && mouseY < ty + SWATCH) {
            if (mouseX >= undoGlyphX() && mouseX < undoGlyphX() + SWATCH) {
                if (!history.isEmpty()) {
                    undo();
                    UISounds.tick(0.8F);
                }
                return true;
            }
            int index = (int) ((mouseX - boardX()) / (SWATCH + 1));
            if (index >= 0 && index < Tool.values().length) {
                selectTool(Tool.values()[index]);
                return true;
            }
            return true; // dead strip space never paints
        }
        int py = swatchRowY();
        if (mouseY >= py && mouseY < py + SWATCH) {
            int index = (int) ((mouseX - boardX()) / (SWATCH + 1));
            if (index >= 0 && index < PALETTE.length) {
                selected = index;
                UISounds.tick(1.3F);
                return true;
            }
            return true;
        }
        if (tool == Tool.FILL && canvas != null) {
            int cx = (int) Math.floor((mouseX - boardX()) / cell);
            int cy = (int) Math.floor((mouseY - boardY()) / cell);
            if (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
                byte color = (byte) (button == 1 ? 0 : selected + 1);
                int start = cy * cols + cx;
                if (canvas[start] != paletteArgb(color)) { // same-color fill is a no-op
                    for (int idx : PaintCanvas.floodRegion(canvas, cols, rows, start)) {
                        paintCell(idx, color);
                    }
                    endGesture();
                    flush();
                    UISounds.tick(button == 1 ? 0.9F : 1.1F);
                }
                return true;
            }
        }
        if ((tool == Tool.LINE || tool == Tool.RECT) && canvas != null) {
            int cx = (int) Math.floor((mouseX - boardX()) / cell);
            int cy = (int) Math.floor((mouseY - boardY()) / cell);
            if (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
                shapeStartX = shapeEndX = cx;
                shapeStartY = shapeEndY = cy;
                shapeButton = button;
                UISounds.tick(1.1F);
                return true;
            }
        }
        if (tool == Tool.EYEDROPPER && canvas != null) {
            int cx = (int) Math.floor((mouseX - boardX()) / cell);
            int cy = (int) Math.floor((mouseY - boardY()) / cell);
            if (cx >= 0 && cx < cols && cy >= 0 && cy < rows) {
                byte picked = paletteIndexOf(canvas[cy * cols + cx]);
                if (picked != 0) { // blank tap stays on the eyedropper
                    selected = picked - 1;
                    selectTool(Tool.BRUSH); // auto-return, like every paint app
                }
                return true;
            }
        }
        if (tool == Tool.BRUSH && apply(mouseX, mouseY, button)) {
            dragging = true;
            UISounds.tick(button == 1 ? 0.9F : 1.1F);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (shapeStartX >= 0) {
            shapeEndX = Mth.clamp((int) Math.floor((mouseX - boardX()) / cell), 0, cols - 1);
            shapeEndY = Mth.clamp((int) Math.floor((mouseY - boardY()) / cell), 0, rows - 1);
            return true;
        }
        if (tool == Tool.BRUSH && apply(mouseX, mouseY, button)) {
            dragging = true;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (shapeStartX >= 0) {
            byte color = (byte) (shapeButton == 1 ? 0 : selected + 1);
            shapeCells((x, y) -> paintCell(y * cols + x, color));
            cancelShape();
            endGesture();
            flush();
            UISounds.tick(shapeButton == 1 ? 0.9F : 1.1F);
            return true;
        }
        lastStrokeCell = -1;
        if (dragging) {
            dragging = false;
            endGesture();
            flush();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && shapeStartX >= 0) { // ESC mid-shape: cancel, keep screen
            cancelShape();
            return true;
        }
        switch (keyCode) {
            case 66 -> { selectTool(Tool.BRUSH); return true; }      // B
            case 70 -> { selectTool(Tool.FILL); return true; }       // F
            case 76 -> { selectTool(Tool.LINE); return true; }       // L
            case 82 -> { selectTool(Tool.RECT); return true; }       // R
            case 73 -> { selectTool(Tool.EYEDROPPER); return true; } // I
            case 90 -> { undo(); return true; }                      // Z
            default -> { }
        }
        if (keyCode == 67) { // C clears
            pending.clear();
            gesture.clear();
            history.clear();
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
        lastStrokeCell = -1;
        cancelShape();
        endGesture();
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
        if (shapeStartX >= 0) {
            int ghost = (PALETTE[selected] & 0x00FFFFFF) | 0x80000000;
            int ghostArgb = shapeButton == 1 ? 0x80101216 : ghost;
            shapeCells((cx2, cy2) -> graphics.fill(
                    bx + cx2 * cell, by + cy2 * cell,
                    bx + cx2 * cell + cell, by + cy2 * cell + cell, ghostArgb));
        }
        int ty = toolRowY();
        Tool[] tools = Tool.values();
        for (int i = 0; i < tools.length; i++) {
            int x = bx + i * (SWATCH + 1);
            renderToolGlyph(graphics, tools[i], x, ty);
            if (tools[i] == tool) {
                graphics.fill(x, ty - 2, x + SWATCH, ty - 1, 0xFFE8EAF0);
            }
        }
        // Undo sits at the far right of the tool row — a momentary
        // button, not a mode; dimmed while there is nothing to undo.
        renderUndoGlyph(graphics, undoGlyphX(), ty, !history.isEmpty());

        int py = swatchRowY();
        for (int i = 0; i < PALETTE.length; i++) {
            int x = bx + i * (SWATCH + 1);
            graphics.fill(x, py, x + SWATCH, py + SWATCH, PALETTE[i]);
            if (i == selected) {
                graphics.fill(x, py - 2, x + SWATCH, py - 1, 0xFFE8EAF0);
            }
        }
    }

    private int undoGlyphX() {
        return boardX() + Math.max(Tool.values().length * (SWATCH + 1) + SWATCH,
                cols * cell - SWATCH);
    }

    /** Procedural 8×8 tool glyphs, house style (fills only). */
    private void renderToolGlyph(GuiGraphics g, Tool t, int x, int y) {
        int ink = 0xFFE8EAF0;
        switch (t) {
            case BRUSH -> {
                g.fill(x + 5, y, x + 8, y + 3, ink);          // tip
                g.fill(x + 3, y + 3, x + 5, y + 5, ink);      // ferrule
                g.fill(x + 1, y + 5, x + 3, y + 8, ink);      // handle
            }
            case FILL -> {
                g.fill(x + 1, y + 2, x + 6, y + 7, ink);      // bucket
                g.fill(x + 6, y + 4, x + 8, y + 8, ink);      // pour
            }
            case LINE -> {
                g.fill(x, y + 6, x + 2, y + 8, ink);
                g.fill(x + 2, y + 4, x + 4, y + 6, ink);
                g.fill(x + 4, y + 2, x + 6, y + 4, ink);
                g.fill(x + 6, y, x + 8, y + 2, ink);
            }
            case RECT -> {
                g.fill(x, y + 1, x + 8, y + 2, ink);          // top
                g.fill(x, y + 6, x + 8, y + 7, ink);          // bottom
                g.fill(x, y + 2, x + 1, y + 6, ink);          // left
                g.fill(x + 7, y + 2, x + 8, y + 6, ink);      // right
            }
            case EYEDROPPER -> {
                g.fill(x, y + 6, x + 2, y + 8, ink);          // tip
                g.fill(x + 2, y + 3, x + 5, y + 6, ink);      // barrel
                g.fill(x + 5, y + 1, x + 8, y + 4, ink);      // bulb
            }
        }
    }

    private void renderUndoGlyph(GuiGraphics g, int x, int y, boolean enabled) {
        int ink = enabled ? 0xFFE8EAF0 : 0xFF5A6170;
        g.fill(x, y + 3, x + 3, y + 6, ink);                  // arrow head block
        g.fill(x + 3, y + 4, x + 8, y + 6, ink);              // shaft
        g.fill(x + 6, y + 2, x + 8, y + 4, ink);              // curl
    }
}
