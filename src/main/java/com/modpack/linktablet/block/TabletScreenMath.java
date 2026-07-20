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

    /**
     * Uniform breathing room between entries and against the bezel, in
     * texels — layout geometry shared by renderer AND slider hit math.
     */
    public static final float SPACE = 0.25f;

    /** List row mini-switch width / right inset (see renderer's row art). */
    public static final float LIST_SWITCH_W = 2.0f;
    public static final float LIST_SWITCH_MARGIN = 0.35f;

    private TabletScreenMath() {}

    /** Entry size along a span divided into n cells with SPACE gaps. */
    public static float tileSize(float span, int n) {
        return (span - (n + 1) * SPACE) / n;
    }

    public static float tileU0(int col, float tileW) {
        return GLASS_U0 + SPACE + col * (tileW + SPACE);
    }

    public static float tileV0(int row, float tileH) {
        return GLASS_V0 + SPACE + row * (tileH + SPACE);
    }

    /** Inset of a slider tile's value bar from its tile edges. */
    public static float sliderInset(float tileW) {
        return Math.min(0.3f, tileW * 0.1f);
    }

    /**
     * Logical-u span {from, to} of a slider app's value bar — the drag
     * maps the crosshair against exactly this span so the bar's end
     * tracks the crosshair 1:1. MUST stay in lockstep with the
     * renderer's slider art (which draws through these same helpers).
     */
    public static float[] sliderBarU(int index, int appCount, boolean list, int rot) {
        return surfaceSliderBarU(index, appCount, list, rot, 1, 1);
    }

    /** Apps visible on the physical screen in the given layout. */
    public static int visibleApps(int appCount, boolean list) {
        return visibleApps(appCount, list, 1);
    }

    /** Visible cap for a merged surface: every member adds a full screen. */
    public static int visibleApps(int appCount, boolean list, int members) {
        return Math.min(appCount, (list ? LIST_ROWS : MAX_PIPS) * members);
    }

    /** Grid dimensions for a given app count. */
    public record GridLayout(int cols, int rows) {
        public int cells() {
            return cols * rows;
        }
    }

    /**
     * Density ladder for the per-block sub-grid — the ONE table both the
     * single-block grid and merged surfaces walk (in order, first entry
     * whose cell count covers the visible apps).
     */
    private static final int[][] DENSITY_LADDER = {
            {1, 1}, {1, 2}, {2, 2}, {2, 3}, {3, 3}, {3, 4}, {4, 4}, {COLS, ROWS}};

    /**
     * A w×h-block merged surface tiled with a k×m sub-grid PER MEMBER
     * (tiles never straddle the bezel seams — video-wall layout). The
     * surface grid is (k·w)×(m·h), app indices row-major across the
     * WHOLE surface, so a member shows a strided subset, never a
     * contiguous slice. These mapping methods are the ONLY converter
     * between surface indices and member cells: renderer and hit-test
     * both call them — never duplicate the arithmetic.
     */
    public record SurfaceLayout(int blocksW, int blocksH, int k, int m) {
        public int cols() {
            return k * blocksW;
        }

        public int rows() {
            return m * blocksH;
        }

        public int cells() {
            return cols() * rows();
        }

        /** Member block column (0..blocksW-1) showing surface index i. */
        public int blockX(int i) {
            return (i % cols()) / k;
        }

        /** Member block row (0..blocksH-1) showing surface index i. */
        public int blockY(int i) {
            return (i / cols()) / m;
        }

        /** Column inside the member's own k×m sub-grid. */
        public int localCol(int i) {
            return (i % cols()) % k;
        }

        /** Row inside the member's own k×m sub-grid. */
        public int localRow(int i) {
            return (i / cols()) % m;
        }

        /** Inverse mapping: member + local cell → surface index. */
        public int surfaceIndex(int bx, int by, int localCol, int localRow) {
            return (by * m + localRow) * cols() + bx * k + localCol;
        }
    }

    /**
     * Sub-grid density for a surface: smallest ladder entry whose total
     * cell count covers the visible apps — the merged-surface
     * generalization of {@link #gridLayout} (which delegates here).
     */
    public static SurfaceLayout surfaceLayout(int appCount, int w, int h, int rot) {
        int visible = Math.max(1, visibleApps(appCount, false, w * h));
        int k = COLS;
        int m = ROWS;
        for (int[] step : DENSITY_LADDER) {
            if (step[0] * step[1] * w * h >= visible) {
                k = step[0];
                m = step[1];
                break;
            }
        }
        // Rotation only exists on single blocks (merged surfaces clamp
        // to 0); odd quarter-turns swap the sub-grid sense.
        if (w == 1 && h == 1 && (rot & 1) == 1) {
            return new SurfaceLayout(1, 1, m, k);
        }
        return new SurfaceLayout(w, h, k, m);
    }

    /**
     * The pip grid sizes itself to the app count — one app fills the
     * whole glass, few apps get big tiles, converging on the densest
     * {@link #COLS}×{@link #ROWS} grid. Renderer and click hit-test both
     * derive their geometry from this table (via {@link #surfaceLayout});
     * never duplicate it.
     */
    public static GridLayout gridLayout(int appCount) {
        SurfaceLayout sl = surfaceLayout(appCount, 1, 1, 0);
        return new GridLayout(sl.k(), sl.m());
    }

    // ------------------------------------------------------------------
    // List mode on a merged surface: newspaper columns — indices run DOWN
    // each block column (LIST_ROWS per member) before jumping to the next
    // column of blocks. The three helpers below are the only mapping.
    // ------------------------------------------------------------------

    /** Member block column showing list index i on an h-block-tall surface. */
    public static int listBlockX(int i, int h) {
        return i / (LIST_ROWS * h);
    }

    /** Member block row showing list index i. */
    public static int listBlockY(int i, int h) {
        return (i % (LIST_ROWS * h)) / LIST_ROWS;
    }

    /** Row inside the member's own list showing index i. */
    public static int listLocalRow(int i, int h) {
        return i % LIST_ROWS;
    }

    /** Inverse: member + local list row → surface list index. */
    public static int listIndex(int bx, int by, int localRow, int h) {
        return bx * LIST_ROWS * h + by * LIST_ROWS + localRow;
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
     * World direction of screen-space +u (visual RIGHT): the forward
     * image of the canonical +X axis under the model rotation stack
     * (preRot, then xRot, then yRot — the exact rotations {@link
     * #screenUV} inverts, translation-free). Surface formation, member
     * offsets, and the controller render box all derive from this pair;
     * a sign error here is self-consistent (controller merely lands in
     * the wrong visual corner) — verify per orientation class in-game.
     */
    public static Direction screenRight(BlockState state) {
        return transformDirection(state, 1, 0, 0);
    }

    /** World direction of screen-space +v (visual DOWN): forward image of +Z. */
    public static Direction screenDown(BlockState state) {
        return transformDirection(state, 0, 0, 1);
    }

    private static Direction transformDirection(BlockState state, int dx, int dy, int dz) {
        // Forward vertex order: preRot first, then xRot, then yRot.
        // Forward Y quarter-step on directions: (dx, dz) -> (-dz, dx).
        // Forward X quarter-step on directions: (dy, dz) -> (dz, -dy).
        for (int i = preRot(state) / 90; i > 0; i--) {
            int nx = -dz;
            dz = dx;
            dx = nx;
        }
        for (int i = xRot(state) / 90; i > 0; i--) {
            int ny = dz;
            dz = -dy;
            dy = ny;
        }
        for (int i = yRot(state) / 90; i > 0; i--) {
            int nx = -dz;
            dz = dx;
            dx = nx;
        }
        return Direction.fromDelta(dx, dy, dz);
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

    /** World coordinate (on the screen normal's axis) of the glass plane. */
    private static double screenPlaneCoord(BlockState state, BlockPos pos) {
        return switch (state.getValue(TabletBlock.FACE)) {
            case FLOOR -> pos.getY() + 1 / 16.0;
            case CEILING -> pos.getY() + 15 / 16.0;
            case WALL -> switch (state.getValue(TabletBlock.FACING)) {
                case SOUTH -> pos.getZ() + 1 / 16.0;
                case EAST -> pos.getX() + 1 / 16.0;
                case WEST -> pos.getX() + 15 / 16.0;
                default -> pos.getZ() + 15 / 16.0; // NORTH
            };
        };
    }

    /**
     * The logical-u texel (content space, unclamped — may run past the
     * glass) a look-ray points at, intersecting the ray with the INFINITE
     * screen plane — the slider drag keeps following the crosshair even
     * past the tablet's edge. NaN when the ray is parallel to or points
     * away from the plane.
     */
    public static float logicalUFromRay(BlockState state, BlockPos pos, Vec3 eye, Vec3 look, int rot) {
        Direction face = screenFace(state);
        double plane = screenPlaneCoord(state, pos);
        double eyeCoord = face.getAxis().choose(eye.x, eye.y, eye.z);
        double lookCoord = face.getAxis().choose(look.x, look.y, look.z);
        if (Math.abs(lookCoord) < 1.0E-6) return Float.NaN;
        double t = (plane - eyeCoord) / lookCoord;
        if (t <= 0) return Float.NaN;
        double[] uv = screenUV(state, pos, face, eye.add(look.scale(t)));
        if (uv == null) return Float.NaN;

        // Same inverse content-rotation swizzle as hitPipDetailed
        double pu = uv[0] - GLASS_U0;
        double pv = uv[1] - GLASS_V0;
        double w = GLASS_U1 - GLASS_U0;
        double h = GLASS_V1 - GLASS_V0;
        for (int i = 0; i < (rot & 3); i++) {
            double nu = pv;
            double nv = w - pu;
            pu = nu;
            pv = nv;
            double tmp = w;
            w = h;
            h = tmp;
        }
        return (float) (GLASS_U0 + pu);
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
     * A hit entry plus the logical-u texel it landed on (content space) —
     * slider apps map it against their bar span ({@link #sliderBarU}) so
     * the bar's end tracks the click point exactly.
     */
    public record PipHit(int index, float logicalU) {
    }

    /** Like {@link #hitPip}, but null for a miss and with the along-fraction. */
    public static PipHit hitPipDetailed(BlockState state, BlockPos pos, BlockHitResult hit,
                                        int appCount, boolean list, int rot) {
        return hitPipDetailed(state, pos, hit, appCount, list, rot, 0, 0, 1, 1);
    }

    /**
     * Surface-aware hit test (1.7.0): the clicked block does its normal
     * single-glass UV math, then the member's (bx, by) position on the
     * w×h surface maps the local cell to the surface app index through
     * {@link SurfaceLayout}/the list helpers. {@code logicalU} stays
     * MEMBER-local — slider bars live inside one block's glass, and the
     * drag maps against {@link #surfaceSliderBarU}'s member-local span.
     */
    public static PipHit hitPipDetailed(BlockState state, BlockPos pos, BlockHitResult hit,
                                        int appCount, boolean list, int rot,
                                        int bx, int by, int w, int h) {
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
        double gw = GLASS_U1 - GLASS_U0;
        double gh = GLASS_V1 - GLASS_V0;
        for (int i = 0; i < (rot & 3); i++) {
            double nu = pv;
            double nv = gw - pu;
            pu = nu;
            pv = nv;
            double t = gw;
            gw = gh;
            gh = t;
        }

        int index;
        if (list) {
            int localRow = (int) (pv * LIST_ROWS / gh);
            index = listIndex(bx, by, localRow, h);
        } else {
            SurfaceLayout sl = surfaceLayout(appCount, w, h, rot);
            int localRow = (int) (pv * sl.m() / gh);
            int localCol = (int) (pu * sl.k() / gw);
            index = sl.surfaceIndex(bx, by, localCol, localRow);
        }
        if (index >= visibleApps(appCount, list, w * h)) return null;
        return new PipHit(index, (float) (GLASS_U0 + pu));
    }

    /**
     * MEMBER-local u-span of a surface slider's value bar. The single
     * block form ({@link #sliderBarU}) delegates here with a 1×1 surface;
     * for merged surfaces the bar sits inside whichever member shows the
     * tile, and the span uses that member's local column.
     */
    public static float[] surfaceSliderBarU(int surfaceIndex, int appCount, boolean list,
                                            int rot, int w, int h) {
        float gw = glassW(rot);
        if (list) {
            float su1 = GLASS_U0 + gw - SPACE - LIST_SWITCH_MARGIN;
            return new float[]{su1 - LIST_SWITCH_W * 1.6f, su1};
        }
        SurfaceLayout sl = surfaceLayout(appCount, w, h, rot);
        float tileW = tileSize(gw, sl.k());
        float u0 = tileU0(sl.localCol(surfaceIndex), tileW);
        float inset = sliderInset(tileW);
        return new float[]{u0 + inset, u0 + tileW - inset};
    }
}
