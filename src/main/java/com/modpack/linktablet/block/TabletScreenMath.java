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
        return visibleApps(appCount, list, 1, 1);
    }

    /**
     * Visible cap on a w×h surface. Grid: every member adds a full
     * screen's worth of pips. List: rows run the full surface width, so
     * only the HEIGHT adds rows (a 4×3 wall is 15 long rows, not 60).
     */
    public static int visibleApps(int appCount, boolean list, int w, int h) {
        return Math.min(appCount, list ? LIST_ROWS * h : MAX_PIPS * w * h);
    }

    /**
     * Continuous glass span of a merged surface along u (1.7.0): the
     * raised surface panel covers interior bezels, so content space runs
     * unbroken from the first member's glass edge to the last's —
     * 10 + 16·(w−1) texels.
     */
    public static float surfaceGlassW(int w) {
        return (GLASS_U1 - GLASS_U0) + 16f * (w - 1);
    }

    /** Continuous glass span along v: 12 + 16·(h−1) texels. */
    public static float surfaceGlassH(int h) {
        return (GLASS_V1 - GLASS_V0) + 16f * (h - 1);
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
     * Grid density for a w×h merged surface: (k·w)×(m·h) UNIFORM cells
     * over the continuous glass span ({@link #surfaceGlassW}) — the
     * raised surface panel covers interior bezels (1.7.0), so tiles flow
     * straight across block seams like one big screen. App indices are
     * plain row-major over {@link #cols}×{@link #rows}.
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
    }

    /**
     * Cell density for a surface: smallest ladder entry whose total cell
     * count covers the visible apps — the merged-surface generalization
     * of {@link #gridLayout} (which delegates here).
     */
    public static SurfaceLayout surfaceLayout(int appCount, int w, int h, int rot) {
        int visible = Math.max(1, visibleApps(appCount, false, w, h));
        int k = COLS;
        int m = ROWS;
        for (int[] step : DENSITY_LADDER) {
            if (step[0] * step[1] * w * h >= visible) {
                k = step[0];
                m = step[1];
                break;
            }
        }
        // Odd quarter-turns swap the cell sense — only possible when the
        // glass is square in blocks (1×1 or n×n; oblong surfaces clamp
        // to half-turns, which don't swap).
        if ((rot & 1) == 1 && w == h) {
            return new SurfaceLayout(w, h, m, k);
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
        return logicalSurfaceUFromRay(state, pos, eye, look, rot, 0, 0, 1, 1);
    }

    /**
     * Surface-aware ray→logical-u (1.7.0): the ray intersects the
     * clicked MEMBER's plane; its member offset converts to the
     * continuous surface space before the content-rotation swizzle, so
     * the result matches {@link #surfaceSliderBarU}'s spans on merged
     * (and merged-rotated) surfaces.
     */
    public static float logicalSurfaceUFromRay(BlockState state, BlockPos pos, Vec3 eye, Vec3 look,
                                               int rot, int bx, int by, int w, int h) {
        Direction face = screenFace(state);
        double plane = screenPlaneCoord(state, pos);
        double eyeCoord = face.getAxis().choose(eye.x, eye.y, eye.z);
        double lookCoord = face.getAxis().choose(look.x, look.y, look.z);
        if (Math.abs(lookCoord) < 1.0E-6) return Float.NaN;
        double t = (plane - eyeCoord) / lookCoord;
        if (t <= 0) return Float.NaN;
        double[] uv = screenUV(state, pos, face, eye.add(look.scale(t)));
        if (uv == null) return Float.NaN;
        return logicalUFromSurfaceUV(uv[0], uv[1], rot, bx, by, w, h);
    }

    /** Shared tail of the ray→logical-u paths (quantized and mounted). */
    private static float logicalUFromSurfaceUV(double u, double v, int rot,
                                               int bx, int by, int w, int h) {
        // Same inverse content-rotation swizzle as hitPipDetailed, over
        // the continuous surface spans
        double pu = u + 16.0 * bx - GLASS_U0;
        double pv = v + 16.0 * by - GLASS_V0;
        double gw = w * h == 1 ? GLASS_U1 - GLASS_U0 : surfaceGlassW(w);
        double gh = w * h == 1 ? GLASS_V1 - GLASS_V0 : surfaceGlassH(h);
        for (int i = 0; i < (rot & 3); i++) {
            double nu = pv;
            double nv = gw - pu;
            pu = nu;
            pv = nv;
            double tmp = gw;
            gw = gh;
            gh = tmp;
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
     * Surface-aware hit test (1.7.0): the clicked member's UV plus its
     * 16-texel block offset gives a coordinate in the surface's
     * CONTINUOUS glass span; cells divide that span evenly, exactly
     * like the renderer's uniform layout. Interior bezel strips are
     * part of the span (the raised panel covers them), so clicks there
     * hit cells too; only the surface's outer bezel falls through to
     * the GUI. {@code logicalU} is CONTINUOUS (surface space) — the
     * slider drag adds the member offset the same way.
     */
    public static PipHit hitPipDetailed(BlockState state, BlockPos pos, BlockHitResult hit,
                                        int appCount, boolean list, int rot,
                                        int bx, int by, int w, int h) {
        if (appCount <= 0) return null;
        double[] uv = screenUV(state, pos, hit.getDirection(), hit.getLocation());
        if (uv == null) return null;
        return pipAtSurfaceUV(uv[0] + 16.0 * bx, uv[1] + 16.0 * by, appCount, list, rot, w, h);
    }

    /** Shared tail of the hit-test paths (quantized and mounted). */
    private static PipHit pipAtSurfaceUV(double cu, double cv,
                                         int appCount, boolean list, int rot, int w, int h) {
        double spanW = w * h == 1 ? GLASS_U1 - GLASS_U0 : surfaceGlassW(w);
        double spanH = w * h == 1 ? GLASS_V1 - GLASS_V0 : surfaceGlassH(h);
        if (cu < GLASS_U0 || cu >= GLASS_U0 + spanW) return null;
        if (cv < GLASS_V0 || cv >= GLASS_V0 + spanH) return null;

        // Undo the content rotation (single blocks only — surfaces clamp
        // rot to 0): one inverse CW quarter-turn per step, tracking the
        // (possibly swapped) space dimensions.
        double pu = cu - GLASS_U0;
        double pv = cv - GLASS_V0;
        double gw = spanW;
        double gh = spanH;
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
            index = (int) (pv * LIST_ROWS * h / gh);
        } else {
            SurfaceLayout sl = surfaceLayout(appCount, w, h, rot);
            int row = (int) (pv * sl.rows() / gh);
            int col = (int) (pu * sl.cols() / gw);
            index = row * sl.cols() + col;
        }
        if (index >= visibleApps(appCount, list, w, h)) return null;
        return new PipHit(index, (float) (GLASS_U0 + pu));
    }

    /**
     * CONTINUOUS u-span of a surface slider's value bar (surface space —
     * same coordinates {@link #hitPipDetailed} returns and the drag
     * maps with). The single-block form ({@link #sliderBarU}) delegates
     * here with a 1×1 surface.
     */
    public static float[] surfaceSliderBarU(int surfaceIndex, int appCount, boolean list,
                                            int rot, int w, int h) {
        // Logical span: odd (square-only) rotations swap the axes
        float gw = w * h == 1 ? glassW(rot)
                : (rot & 1) == 0 ? surfaceGlassW(w) : surfaceGlassH(h);
        if (list) {
            float su1 = GLASS_U0 + gw - SPACE - LIST_SWITCH_MARGIN;
            return new float[]{su1 - LIST_SWITCH_W * 1.6f, su1};
        }
        SurfaceLayout sl = surfaceLayout(appCount, w, h, rot);
        float tileW = tileSize(gw, sl.cols());
        float u0 = tileU0(surfaceIndex % sl.cols(), tileW);
        float inset = sliderInset(tileW);
        return new float[]{u0 + inset, u0 + tileW - inset};
    }

    // ------------------------------------------------------------------
    // Swivel mount (1.8.0): a mounted tablet sits on a ball joint at an
    // arbitrary pitch/yaw, so its geometry runs through a real VECTOR
    // basis instead of the quantized rotation tables above. ONE
    // derivation ({@link #mountBasis}) feeds the renderer AND both hit
    // paths — the same one-source-of-truth rule as the quantized stack.
    // Mounted hit locations come from the COARSE voxel box, so the tap
    // test and the drag both re-intersect the EYE RAY with the actual
    // glass plane instead of trusting the hit location.
    // ------------------------------------------------------------------

    /** Ball-joint pivot height above the attach face, in blocks. */
    public static final float MOUNT_PIVOT = 5 / 16f;
    /** Ball center → panel center, along the screen normal, in blocks. */
    public static final float MOUNT_PANEL_OFF = 2 / 16f;
    /** Max tilt away from the attach face's normal, in degrees. */
    public static final float MOUNT_MAX_TILT = 75f;

    /**
     * Screen frame of a mounted tablet: {@code normal} points out of the
     * glass toward the viewer, {@code right}/{@code down} are +u/+v in
     * world space, {@code anchor} is the texel origin (u=0, v=0) ON the
     * glass plane.
     */
    public record MountBasis(Vec3 normal, Vec3 right, Vec3 down, Vec3 anchor) {
        /** World point of the ball-joint pivot for a mounted tablet. */
        public static Vec3 pivot(BlockPos pos, Direction attachNormal) {
            return Vec3.atCenterOf(pos).add(
                    Vec3.atLowerCornerOf(attachNormal.getNormal()).scale(MOUNT_PIVOT - 0.5));
        }
    }

    /** Screen normal for stored mount angles (vanilla pitch/yaw semantics). */
    public static Vec3 mountNormal(float pitch, float yaw) {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    public static MountBasis mountBasis(BlockPos pos, Direction attachNormal,
                                        float pitch, float yaw, boolean landscape) {
        Vec3 normal = mountNormal(pitch, yaw);
        // v-down = world-down projected into the screen plane. A screen
        // aimed straight along world-Y falls back to the yaw's
        // horizontal (phone flat on a desk: content bottom toward the
        // viewer). Matches the quantized frame on every axis-aligned
        // aim, so un/mounting never flips the content.
        Vec3 down = new Vec3(0, -1, 0).subtract(normal.scale(-normal.y));
        down = down.lengthSqr() < 1.0E-4 ? mountNormal(0, yaw) : down.normalize();
        Vec3 right = normal.cross(down).normalize();
        // A landscape case stays landscape on the ball: the same 90°
        // case turn the wall mount bakes (preRot) — the image of the
        // old +u axis is the old +v axis. Content space stays portrait,
        // exactly like the quantized landscape path.
        if (landscape) {
            Vec3 oldRight = right;
            right = down;
            down = oldRight.scale(-1);
        }
        Vec3 glassCenter = MountBasis.pivot(pos, attachNormal)
                .add(normal.scale(MOUNT_PANEL_OFF + 0.5 / 16.0));
        Vec3 anchor = glassCenter.subtract(right.scale(6 / 16.0)).subtract(down.scale(7 / 16.0));
        return new MountBasis(normal, right, down, anchor);
    }

    /**
     * Screen texels under the eye ray through {@code lookOrDelta}
     * (a look DIRECTION, or {@code hitLocation - eye}), or null when the
     * ray misses the front of the glass plane. Unclamped — callers do
     * their own span checks.
     */
    public static double[] mountedUV(MountBasis basis, Vec3 eye, Vec3 lookOrDelta) {
        double denom = lookOrDelta.dot(basis.normal());
        if (denom >= -1.0E-8) return null; // parallel, or facing the back
        double t = basis.anchor().subtract(eye).dot(basis.normal()) / denom;
        if (t <= 0) return null;
        Vec3 delta = eye.add(lookOrDelta.scale(t)).subtract(basis.anchor());
        return new double[]{16 * delta.dot(basis.right()), 16 * delta.dot(basis.down())};
    }

    /** Mounted tap: {@link #hitPipDetailed} for the ball-joint frame. */
    public static PipHit mountedHitPip(MountBasis basis, Vec3 eye, Vec3 hitLocation,
                                       int appCount, boolean list, int rot) {
        if (appCount <= 0) return null;
        double[] uv = mountedUV(basis, eye, hitLocation.subtract(eye));
        if (uv == null) return null;
        return pipAtSurfaceUV(uv[0], uv[1], appCount, list, rot, 1, 1);
    }

    /** Mounted drag: {@link #logicalUFromRay} for the ball-joint frame. */
    public static float mountedLogicalUFromRay(MountBasis basis, Vec3 eye, Vec3 look, int rot) {
        double[] uv = mountedUV(basis, eye, look);
        if (uv == null) return Float.NaN;
        return logicalUFromSurfaceUV(uv[0], uv[1], rot, 0, 0, 1, 1);
    }
}
