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

    private PaintCanvas() {
    }
}
