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

    /** Pip grid dimensions; the list layout shows one app per grid row. */
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
     * App index of the entry under a block click, or -1 for a miss
     * (wrong face, off the glass, or beyond the visible entries). Grid
     * hit cells are each pip expanded a quarter texel per side (making
     * the grid contiguous but leaving the bezel ring a GUI target);
     * list rows are hit across their full width. Pure math on the hit
     * vec — deterministic across client and server.
     */
    public static int hitPip(BlockState state, BlockPos pos, BlockHitResult hit, int appCount, boolean list) {
        if (appCount <= 0 || hit.getDirection() != screenFace(state)) return -1;

        Vec3 loc = hit.getLocation();
        double x = loc.x - pos.getX();
        double y = loc.y - pos.getY();
        double z = loc.z - pos.getZ();

        // Undo the blockstate rotation: inverse Y steps first, then X
        // (the model applies X to vertices first, then Y).
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

        double u = 16 * x - 2;
        double v = 16 * z - 1;

        // Only the glass is ever a toggle target; the bezel ring always
        // falls through to the GUI. Cells divide the glass evenly, so
        // they track the renderer's margin/gap layout automatically.
        if (u < GLASS_U0 || u >= GLASS_U1) return -1;
        if (v < GLASS_V0 || v >= GLASS_V1) return -1;

        int row = (int) ((v - GLASS_V0) * ROWS / (GLASS_V1 - GLASS_V0));
        int index = list
                ? row
                : row * COLS + (int) ((u - GLASS_U0) * COLS / (GLASS_U1 - GLASS_U0));
        return index < visibleApps(appCount, list) ? index : -1;
    }
}
