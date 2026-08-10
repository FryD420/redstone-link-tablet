package com.modpack.linktablet;

/**
 * Paint canvas geometry (1.11.0 "paint on walls", tester idea): every
 * tablet owns a fixed COLS×ROWS slice; merged walls stitch member
 * slices into a continuous picture at edit/render time — there is no
 * merged canvas object (the slice rule: merge/split/pickup can never
 * lose art). This class is the ONE home for the dimensions and the
 * continuous↔member cell mapping — GUI, server stroke handler, and
 * face renderer all map through here; never fork the math.
 */
public final class PaintCanvas {

    public static final int COLS = 20;
    public static final int ROWS = 14;
    public static final int CELLS = COLS * ROWS;
    /** Palette indices 1..16 paint; 0 is blank. */
    public static final int MAX_COLOR = 16;

    public static boolean isBlank(byte[] canvas) {
        for (byte cell : canvas) {
            if (cell != 0) return false;
        }
        return true;
    }

    public static byte[] blank() {
        return new byte[CELLS];
    }

    /** Continuous cell index for a surface of {@code surfaceW} blocks:
     * column-major-free, plain row-major over the stitched grid. */
    public static int contIndex(int contX, int contY, int surfaceW) {
        return contY * (surfaceW * COLS) + contX;
    }

    /** Which member block (dx, dy from the controller, surface space)
     * owns this continuous cell. */
    public static int memberDx(int contX) {
        return contX / COLS;
    }

    public static int memberDy(int contY) {
        return contY / ROWS;
    }

    /** The cell's index inside its owning member's local 20×14. */
    public static int localIndex(int contX, int contY) {
        return (contY % ROWS) * COLS + (contX % COLS);
    }

    /** Receives each cell of a {@link #line}, {@link #rectOutline}, or flood walk. */
    public interface CellVisitor {
        void cell(int x, int y);
    }

    /**
     * Visits every cell of a straight segment from (x0,y0) to (x1,y1),
     * inclusive (Bresenham). A fast mouse drag delivers cursor positions
     * several cells apart — the GUI stroke handler bridges each pair of
     * consecutive positions through here so strokes never leave gaps
     * (cell-walk geometry stays in this class, per the class doc).
     */
    public static void line(int x0, int y0, int x1, int y1, CellVisitor visitor) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            visitor.cell(x0, y0);
            if (x0 == x1 && y0 == y1) return;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /**
     * Continuous indices of the 4-neighbor connected region containing
     * {@code start} whose cells all equal {@code grid[start]} — bucket
     * fill's shape, computed on the STITCHED grid so regions cross
     * merged-wall seams for free. Out-of-range start returns empty.
     */
    public static int[] floodRegion(int[] grid, int cols, int rows, int start) {
        if (start < 0 || start >= cols * rows) return new int[0];
        int match = grid[start];
        boolean[] seen = new boolean[cols * rows];
        java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
        java.util.ArrayList<Integer> region = new java.util.ArrayList<>();
        seen[start] = true;
        queue.add(start);
        while (!queue.isEmpty()) {
            int idx = queue.poll();
            region.add(idx);
            int x = idx % cols, y = idx / cols;
            if (x > 0) floodVisit(grid, seen, queue, idx - 1, match);
            if (x < cols - 1) floodVisit(grid, seen, queue, idx + 1, match);
            if (y > 0) floodVisit(grid, seen, queue, idx - cols, match);
            if (y < rows - 1) floodVisit(grid, seen, queue, idx + cols, match);
        }
        int[] out = new int[region.size()];
        for (int i = 0; i < out.length; i++) out[i] = region.get(i);
        return out;
    }

    private static void floodVisit(int[] grid, boolean[] seen,
                                   java.util.ArrayDeque<Integer> queue, int idx, int match) {
        if (!seen[idx] && grid[idx] == match) {
            seen[idx] = true;
            queue.add(idx);
        }
    }

    /** Visits each perimeter cell of the corner-normalized rectangle
     * exactly once (the rectangle tool; degenerate rows/columns don't
     * double-visit). */
    public static void rectOutline(int x0, int y0, int x1, int y1, CellVisitor visitor) {
        int left = Math.min(x0, x1), right = Math.max(x0, x1);
        int top = Math.min(y0, y1), bottom = Math.max(y0, y1);
        for (int x = left; x <= right; x++) {
            visitor.cell(x, top);
            if (bottom != top) visitor.cell(x, bottom);
        }
        for (int y = top + 1; y < bottom; y++) {
            visitor.cell(left, y);
            if (right != left) visitor.cell(right, y);
        }
    }

    private PaintCanvas() {
    }
}
