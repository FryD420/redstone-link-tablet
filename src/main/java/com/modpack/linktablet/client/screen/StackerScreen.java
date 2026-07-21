package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import com.modpack.linktablet.client.UISounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;

/**
 * 🧊 (1.8.0, unadvertised): "Stacker". Falling tetrominoes, 10×18
 * well, seven-bag randomizer, hard drop on space. Best = lines.
 */
public class StackerScreen extends ArcadeScreen {

    private static final int COLS = 10;
    private static final int ROWS = 18;
    private static final int CELL = 6;

    /** Piece definitions: 4 cells each as {x,y}, spawning around (4,0). */
    private static final int[][][] PIECES = {
            {{0, 0}, {1, 0}, {2, 0}, {3, 0}}, // I
            {{0, 0}, {1, 0}, {0, 1}, {1, 1}}, // O
            {{0, 0}, {1, 0}, {2, 0}, {1, 1}}, // T
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}}, // Z
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}}, // S
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}}, // J
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}}  // L
    };
    private static final int[] PIECE_COLORS = {
            0xFF3AB3DA, 0xFFFED83D, 0xFF8932B8, 0xFFC93C36,
            0xFF80C71F, 0xFF3C44AA, 0xFFF9801D};

    private final RandomSource random = RandomSource.create();
    /** Settled cells: 0 empty, else piece color. */
    private final int[] well = new int[COLS * ROWS];
    private final java.util.List<Integer> bag = new java.util.ArrayList<>();
    private int[][] piece; // 4 cells, well coords
    private int pieceKind;
    private int lines;
    private int tickCount;
    private boolean over;
    private boolean newBest;

    public StackerScreen(AppView view, boolean returnToTablet) {
        super("stacker", view, returnToTablet);
        reset();
    }

    @Override
    protected int boardW() {
        return COLS * CELL;
    }

    @Override
    protected int boardH() {
        return ROWS * CELL;
    }

    private void reset() {
        java.util.Arrays.fill(well, 0);
        bag.clear();
        lines = 0;
        over = false;
        newBest = false;
        spawn();
    }

    private void spawn() {
        if (bag.isEmpty()) {
            for (int i = 0; i < PIECES.length; i++) {
                bag.add(i);
            }
            java.util.Collections.shuffle(bag);
        }
        pieceKind = bag.remove(bag.size() - 1);
        piece = new int[4][];
        for (int i = 0; i < 4; i++) {
            piece[i] = new int[]{PIECES[pieceKind][i][0] + 3, PIECES[pieceKind][i][1]};
        }
        if (collides(piece)) {
            over = true;
            newBest = submitBest(lines, false);
            if (newBest) {
                UISounds.confirm();
            } else {
                UISounds.delete();
            }
        }
    }

    private boolean collides(int[][] cells) {
        for (int[] cell : cells) {
            if (cell[0] < 0 || cell[0] >= COLS || cell[1] >= ROWS) return true;
            if (cell[1] >= 0 && well[cell[1] * COLS + cell[0]] != 0) return true;
        }
        return false;
    }

    private int[][] moved(int dx, int dy) {
        int[][] next = new int[4][];
        for (int i = 0; i < 4; i++) {
            next[i] = new int[]{piece[i][0] + dx, piece[i][1] + dy};
        }
        return next;
    }

    private int[][] rotated() {
        if (pieceKind == 1) return piece; // O doesn't rotate
        // Rotate CW about the second cell (a serviceable pivot)
        int px = piece[1][0];
        int py = piece[1][1];
        int[][] next = new int[4][];
        for (int i = 0; i < 4; i++) {
            int dx = piece[i][0] - px;
            int dy = piece[i][1] - py;
            next[i] = new int[]{px - dy, py + dx};
        }
        return next;
    }

    private boolean tryMove(int[][] next) {
        if (collides(next)) return false;
        piece = next;
        return true;
    }

    private void lockAndClear() {
        for (int[] cell : piece) {
            if (cell[1] >= 0) {
                well[cell[1] * COLS + cell[0]] = PIECE_COLORS[pieceKind];
            }
        }
        int cleared = 0;
        for (int row = ROWS - 1; row >= 0; row--) {
            boolean full = true;
            for (int col = 0; col < COLS; col++) {
                full &= well[row * COLS + col] != 0;
            }
            if (full) {
                cleared++;
                System.arraycopy(well, 0, well, COLS, row * COLS);
                java.util.Arrays.fill(well, 0, COLS, 0);
                row++; // re-check the shifted row
            }
        }
        if (cleared > 0) {
            lines += cleared;
            UISounds.tick(1.2F + cleared * 0.15F);
        } else {
            UISounds.tick(0.9F);
        }
        spawn();
    }

    /** Gravity interval by lines: 10 ticks down to 3. */
    private int dropInterval() {
        return Math.max(3, 10 - lines / 8);
    }

    @Override
    public void tick() {
        if (over) return;
        if (++tickCount % dropInterval() != 0) return;
        if (!tryMove(moved(0, 1))) {
            lockAndClear();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (over && keyCode != 256) {
            UISounds.page();
            reset();
            return true;
        }
        switch (keyCode) {
            case 263, 65 -> tryMove(moved(-1, 0));
            case 262, 68 -> tryMove(moved(1, 0));
            case 264, 83 -> {
                if (!tryMove(moved(0, 1))) lockAndClear();
            }
            case 265, 87 -> tryMove(rotated());
            case 32 -> { // hard drop
                while (tryMove(moved(0, 1))) {
                    // fall
                }
                lockAndClear();
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (over) {
            UISounds.page();
            reset();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderCabinet(graphics, lines + " / " + Math.max(lines, best()));
        int bx = boardX();
        int by = boardY();
        for (int i = 0; i < well.length; i++) {
            if (well[i] == 0) continue;
            int x = bx + (i % COLS) * CELL;
            int y = by + (i / COLS) * CELL;
            graphics.fill(x, y, x + CELL - 1, y + CELL - 1, well[i]);
        }
        if (!over) {
            for (int[] cell : piece) {
                if (cell[1] < 0) continue;
                int x = bx + cell[0] * CELL;
                int y = by + cell[1] * CELL;
                graphics.fill(x, y, x + CELL - 1, y + CELL - 1, PIECE_COLORS[pieceKind]);
            }
        }
        if (over) {
            renderOverlay(graphics, "TOPPED OUT",
                    (newBest ? "new best: " : "lines: ") + lines, "tap to restart");
        }
    }
}
