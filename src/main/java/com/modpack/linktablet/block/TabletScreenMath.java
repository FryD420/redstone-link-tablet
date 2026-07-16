package com.modpack.linktablet.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Geometry shared by the tablet's on-screen pip renderers (client) and
 * the placed tablet's click-to-toggle hit test (both sides).
 * <p>
 * Everything works in <b>screen-local texel space</b>: origin at the
 * top-left of the screen artwork (texture col 2, row 1), {@code u}
 * right 0..12, {@code v} down 0..14. The bezel texture has an opaque
 * 1-texel inner ring, so the visible glass is u 1..11, v 1..13 — the
 * pip grid sits inside that.
 * <p>
 * The block's canonical (unrotated) frame is FLOOR/NORTH: screen on the
 * up face, {@code x = (2 + u) / 16}, {@code z = (1 + v) / 16}. All other
 * orientations are the blockstate JSON's x/y model rotations; the angle
 * table here MUST mirror {@code blockstates/tablet.json}. Click mapping
 * inverts those rotations with exact 90° integer swizzles so client and
 * server always agree.
 */
public final class TabletScreenMath {

    /** Densest pip grid; the list layout shows one app per grid row. */
    public static final int COLS = 4;
    public static final int ROWS = 5;
    public static final int MAX_PIPS = COLS * ROWS;
    public static final int LIST_ROWS = ROWS;

    /** Glass area inside the bezel ring, in screen texels. */
    public static final float GLASS_U0 = 1f;
    public static final float GLASS_V0 = 1f;
    public static final float GLASS_U1 = 11f;
    public static final float GLASS_V1 = 13f;

    private TabletScreenMath() {}

    /** Apps visible on the physical screen in the given layout. */
    public static int visibleApps(int appCount, boolean list) {
        return Math.min(appCount, list ? LIST_ROWS : MAX_PIPS);
    }

    /** Grid dimensions for a given app count. */
    public record GridLayout(int cols, int rows) {
        public int cells() {
            return cols * rows;
        }
    }

    /**
     * The pip grid sizes itself to the app count — one app fills the
     * whole glass, few apps get big tiles, converging on the densest
     * {@link #COLS}×{@link #ROWS} grid. Renderer and click hit-test both
     * derive their geometry from this table; never duplicate it.
     */
    public static GridLayout gridLayout(int appCount) {
        if (appCount <= 1) return new GridLayout(1, 1);
        if (appCount == 2) return new GridLayout(1, 2);
        if (appCount <= 4) return new GridLayout(2, 2);
        if (appCount <= 6) return new GridLayout(2, 3);
        if (appCount <= 9) return new GridLayout(3, 3);
        if (appCount <= 12) return new GridLayout(3, 4);
        if (appCount <= 16) return new GridLayout(4, 4);
        return new GridLayout(COLS, ROWS);
    }

    // ------------------------------------------------------------------
    // Screen content rotation (wrench): the layout lives in a "logical"
    // glass whose axes turn with the content — landscape when the
    // rotation is odd. Renderer (pose rotation about the glass center)
    // and hit-test (inverse swizzle in hitPip) both derive from the
    // quarter-turn count and these dimensions; keep them in lockstep.
    // ------------------------------------------------------------------

    /** Logical glass width for a content rotation (0–3 quarter turns CW). */
    public static float glassW(int rot) {
        return (rot & 1) == 0 ? GLASS_U1 - GLASS_U0 : GLASS_V1 - GLASS_V0;
    }

    /** Logical glass height for a content rotation (0–3 quarter turns CW). */
    public static float glassH(int rot) {
        return (rot & 1) == 0 ? GLASS_V1 - GLASS_V0 : GLASS_U1 - GLASS_U0;
    }

    /**
     * Layout for a rotated screen: odd quarter-turns swap cols and rows
     * so tiles keep their portrait/landscape sense (2 apps sit
     * side-by-side on a landscape screen instead of stacked).
     */
    public static GridLayout gridLayout(int appCount, int rot) {
        GridLayout grid = gridLayout(appCount);
        return (rot & 1) == 0 ? grid : new GridLayout(grid.rows(), grid.cols());
    }

    /**
     * Pre-rotation baked into the landscape model geometry, in degrees
     * about the canonical vertical axis (one blockstate-y-style CW
     * step). MUST mirror blockstates/tablet.json: landscape wall
     * variants use tablet_landscape(_lit), whose elements are the
     * portrait model turned 90° about the block center.
     */
    public static int preRot(BlockState state) {
        return state.getValue(TabletBlock.FACE) == AttachFace.WALL
                && state.getValue(TabletBlock.LANDSCAPE) ? 90 : 0;
    }

    /** Blockstate model rotation about X, matching blockstates/tablet.json. */
    public static int xRot(BlockState state) {
        return switch (state.getValue(TabletBlock.FACE)) {
            case FLOOR -> 0;
            case WALL -> 270;
            case CEILING -> 180;
        };
    }

    /** Blockstate model rotation about Y, matching blockstates/tablet.json. */
    public static int yRot(BlockState state) {
        Direction facing = state.getValue(TabletBlock.FACING);
        if (state.getValue(TabletBlock.FACE) == AttachFace.FLOOR) {
            return switch (facing) {
                case EAST -> 90;
                case SOUTH -> 180;
                case WEST -> 270;
                default -> 0;
            };
        }
        // Wall and ceiling share the same y-rotation pattern
        return switch (facing) {
            case NORTH -> 180;
            case EAST -> 270;
            case WEST -> 90;
            default -> 0; // SOUTH
        };
    }

    /** World-space direction the screen faces in this orientation. */
    public static Direction screenFace(BlockState state) {
        return switch (state.getValue(TabletBlock.FACE)) {
            case FLOOR -> Direction.UP;
            case CEILING -> Direction.DOWN;
            case WALL -> state.getValue(TabletBlock.FACING);
        };
    }

    /**
     * Screen-local texels {@code {u, v}} under a click, or null when the
     * clicked face isn't the screen. Undoes the blockstate rotation with
     * exact 90° integer swizzles — deterministic across client/server.
     */
    private static double[] screenUV(BlockState state, BlockPos pos, Direction face, Vec3 location) {
        if (face != screenFace(state)) return null;

        double x = location.x - pos.getX();
        double y = location.y - pos.getY();
        double z = location.z - pos.getZ();

        // Undo the blockstate rotation: inverse Y steps first, then X
        // (the model applies X to vertices first, then Y), then the
        // landscape model's baked pre-rotation (applied to vertices
        // before everything, so undone last; same swizzle as a Y step).
        for (int i = yRot(state) / 90; i > 0; i--) {
            double nx = z;
            z = 1 - x;
            x = nx;
        }
        for (int i = xRot(state) / 90; i > 0; i--) {
            double ny = 1 - z;
            z = y;
            y = ny;
        }
        for (int i = preRot(state) / 90; i > 0; i--) {
            double nx = z;
            z = 1 - x;
            x = nx;
        }

        return new double[]{16 * x - 2, 16 * z - 1};
    }

    /**
     * Whether a click lands on the glass (vs the bezel ring / case).
     * {@code inset} shrinks the glass region (texels per side) — the
     * wrench uses 1 so the 1-texel bezel ring is a reachable target.
     */
    public static boolean isOnGlass(BlockState state, BlockPos pos, Direction face, Vec3 location,
                                    double inset) {
        double[] uv = screenUV(state, pos, face, location);
        return uv != null
                && uv[0] >= GLASS_U0 + inset && uv[0] < GLASS_U1 - inset
                && uv[1] >= GLASS_V0 + inset && uv[1] < GLASS_V1 - inset;
    }

    /**
     * App index of the entry under a block click, or -1 for a miss
     * (wrong face, off the glass, or beyond the visible entries). Grid
     * hit cells are each pip expanded a quarter texel per side (making
     * the grid contiguous but leaving the bezel ring a GUI target);
     * list rows are hit across their full width. Pure math on the hit
     * vec — deterministic across client and server.
     *
     * @param rot screen content rotation in quarter turns CW (0–3); must
     *            match the rotation the renderer draws with
     */
    public static int hitPip(BlockState state, BlockPos pos, BlockHitResult hit,
                             int appCount, boolean list, int rot) {
        PipHit detailed = hitPipDetailed(state, pos, hit, appCount, list, rot);
        return detailed == null ? -1 : detailed.index();
    }

    /**
     * A hit entry plus where along the cell's width it landed (0..1,
     * left→right in content space) — the slider apps' click-to-set input.
     */
    public record PipHit(int index, float along) {
    }

    /** Like {@link #hitPip}, but null for a miss and with the along-fraction. */
    public static PipHit hitPipDetailed(BlockState state, BlockPos pos, BlockHitResult hit,
                                        int appCount, boolean list, int rot) {
        if (appCount <= 0) return null;
        double[] uv = screenUV(state, pos, hit.getDirection(), hit.getLocation());
        if (uv == null) return null;
        double u = uv[0];
        double v = uv[1];

        // Only the glass is ever a toggle target; the bezel ring always
        // falls through to the GUI. Cells divide the glass evenly, so
        // they track the renderer's margin/gap layout automatically.
        if (u < GLASS_U0 || u >= GLASS_U1) return null;
        if (v < GLASS_V0 || v >= GLASS_V1) return null;

        // Undo the content rotation: one inverse CW quarter-turn per
        // step, tracking the (possibly swapped) space dimensions.
        double pu = u - GLASS_U0;
        double pv = v - GLASS_V0;
        double w = GLASS_U1 - GLASS_U0;
        double h = GLASS_V1 - GLASS_V0;
        for (int i = 0; i < (rot & 3); i++) {
            double nu = pv;
            double nv = w - pu;
            pu = nu;
            pv = nv;
            double t = w;
            w = h;
            h = t;
        }

        int index;
        double along;
        if (list) {
            index = (int) (pv * LIST_ROWS / h);
            along = pu / w;
        } else {
            GridLayout grid = gridLayout(appCount, rot);
            int row = (int) (pv * grid.rows() / h);
            double colPos = pu * grid.cols() / w;
            int col = (int) colPos;
            index = row * grid.cols() + col;
            along = colPos - col;
        }
        if (index >= visibleApps(appCount, list)) return null;
        return new PipHit(index, (float) Math.clamp(along, 0.0, 1.0));
    }
}
