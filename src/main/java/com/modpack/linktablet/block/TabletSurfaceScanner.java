package com.modpack.linktablet.block;

import com.modpack.linktablet.compat.TabletTransmitterHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Multiblock surface formation (1.7.0): adjacent, coplanar tablets with
 * an identical merge key (FACE, FACING, LANDSCAPE) merge into one large
 * screen when — and only when — the connected component exactly fills
 * its bounding rectangle within {@link #MAX_W}×{@link #MAX_H}
 * (screen-space). Anything else (L-shapes, over-long rows) dissolves the
 * whole component to standalone tablets — filled-rectangle-or-nothing,
 * always lossless (every member keeps its own dormant data).
 *
 * <p>Runs ONLY from scheduled block ticks (see {@link TabletBlock}
 * onPlace/onRemove): scheduling decouples the scan from block-state
 * churn — the BE exists, {@code loadFromItem} has run, and LIT flips
 * can't recurse into it. Redundant rescans are cheap: role assignment
 * no-ops when nothing changed.
 */
public final class TabletSurfaceScanner {

    public static final int MAX_W = 4;
    public static final int MAX_H = 3;

    /** BFS guard: nothing legal has more members than a 4×3 could touch. */
    private static final int MAX_WALK = 48;

    /** Re-evaluates the surface containing {@code origin}. */
    public static void rescan(ServerLevel level, BlockPos origin, BlockState refState) {
        Direction right = TabletScreenMath.screenRight(refState);
        Direction down = TabletScreenMath.screenDown(refState);

        // A solo origin never merges; if it still carries a stale role,
        // shed it (former co-members reform from their own ticks)
        if (level.getBlockEntity(origin) instanceof TabletBlockEntity originBe
                && originBe.isSoloScreen()) {
            if (originBe.isMerged()) {
                TabletTransmitterHandler.clearHeldForBlock(level, originBe.getControllerPos());
                originBe.setSurfaceRole(0, 0, 1, 1);
            }
            return;
        }

        // Flood the coplanar component with an identical merge key
        List<BlockPos> members = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(origin);
        seen.add(origin);
        boolean overflow = false;
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!sameMergeKey(refState, state)) continue;
            if (!(level.getBlockEntity(pos) instanceof TabletBlockEntity be)
                    || be.isSoloScreen()) continue;
            members.add(pos);
            if (members.size() > MAX_WALK) {
                overflow = true;
                break;
            }
            for (Direction dir : new Direction[]{right, right.getOpposite(), down, down.getOpposite()}) {
                BlockPos next = pos.relative(dir);
                if (seen.add(next)) {
                    queue.add(next);
                }
            }
        }
        if (members.isEmpty()) return;

        // Project onto screen space and test the filled rectangle
        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;
        int minD = Integer.MAX_VALUE, maxD = Integer.MIN_VALUE;
        for (BlockPos pos : members) {
            int dR = project(pos, origin, right);
            int dD = project(pos, origin, down);
            minR = Math.min(minR, dR);
            maxR = Math.max(maxR, dR);
            minD = Math.min(minD, dD);
            maxD = Math.max(maxD, dD);
        }
        int w = maxR - minR + 1;
        int h = maxD - minD + 1;
        boolean valid = !overflow && w <= MAX_W && h <= MAX_H
                && members.size() == w * h && members.size() > 1;
        // Old controllers first — their holds must clear either way
        Set<BlockPos> holdTargets = new HashSet<>();
        for (BlockPos pos : members) {
            if (level.getBlockEntity(pos) instanceof TabletBlockEntity be && be.isMerged()) {
                holdTargets.add(be.getControllerPos());
            }
        }

        BlockPos controller = valid
                ? origin.relative(right, minR).relative(down, minD)
                : null;
        boolean anyChange = false;
        for (BlockPos pos : members) {
            if (!(level.getBlockEntity(pos) instanceof TabletBlockEntity be)) continue;
            boolean wasMerged = be.isMerged();
            if (valid) {
                int dR = project(pos, origin, right) - minR;
                int dD = project(pos, origin, down) - minD;
                be.setSurfaceRole(dR, dD, w, h);
            } else {
                be.setSurfaceRole(0, 0, 1, 1);
            }
            anyChange |= wasMerged != be.isMerged();
        }

        if (controller != null) {
            holdTargets.add(controller);
        }
        for (BlockPos pos : holdTargets) {
            TabletTransmitterHandler.clearHeldForBlock(level, pos);
        }
        if (controller != null
                && level.getBlockEntity(controller) instanceof TabletBlockEntity be) {
            be.updateSurfaceLit();
        }

        // Soft chime when the arrangement actually changed shape
        if (anyChange) {
            level.playSound(null, origin,
                    valid ? SoundEvents.AMETHYST_BLOCK_PLACE : SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.BLOCKS, 0.5F, valid ? 1.4F : 1.1F);
        }
    }

    /**
     * GUI link toggle (1.7.0). Unlinking a merged surface dissolves it
     * and marks EVERY member solo — nothing re-merges next tick, and
     * each tablet can be re-linked individually later. Unlinking a
     * standalone tablet just flags it; re-linking clears the flag and
     * lets a normal rescan re-form whatever fits.
     */
    public static void setLinked(ServerLevel level, BlockPos pos, boolean linked) {
        if (!(level.getBlockEntity(pos) instanceof TabletBlockEntity be)) return;
        if (linked) {
            be.setSoloScreen(false);
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 0);
            return;
        }
        TabletBlockEntity controller = be.resolveController();
        if (controller != null && controller.isSurfaceController()) {
            BlockState state = controller.getBlockState();
            Direction right = TabletScreenMath.screenRight(state);
            Direction down = TabletScreenMath.screenDown(state);
            BlockPos origin = controller.getBlockPos();
            for (int dx = 0; dx < controller.getSurfaceW(); dx++) {
                for (int dy = 0; dy < controller.getSurfaceH(); dy++) {
                    BlockPos memberPos = origin.relative(right, dx).relative(down, dy);
                    if (level.getBlockEntity(memberPos) instanceof TabletBlockEntity member) {
                        member.setSoloScreen(true);
                        member.setSurfaceRole(0, 0, 1, 1);
                    }
                }
            }
            TabletTransmitterHandler.clearHeldForBlock(level, origin);
            level.playSound(null, origin, SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.BLOCKS, 0.5F, 1.1F);
        } else {
            be.setSoloScreen(true);
            // Sweep any stale role via the solo-origin path
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 0);
        }
    }

    /** Whether two states can share a surface (coplanar, same orientation). */
    private static boolean sameMergeKey(BlockState a, BlockState b) {
        if (!(b.getBlock() instanceof TabletBlock) || !(a.getBlock() instanceof TabletBlock)) return false;
        if (a.getValue(TabletBlock.FACE) != b.getValue(TabletBlock.FACE)) return false;
        if (a.getValue(TabletBlock.FACING) != b.getValue(TabletBlock.FACING)) return false;
        return a.getValue(TabletBlock.FACE) != AttachFace.WALL
                || a.getValue(TabletBlock.LANDSCAPE) == b.getValue(TabletBlock.LANDSCAPE);
    }

    private static int project(BlockPos pos, BlockPos origin, Direction axis) {
        return (pos.getX() - origin.getX()) * axis.getStepX()
                + (pos.getY() - origin.getY()) * axis.getStepY()
                + (pos.getZ() - origin.getZ()) * axis.getStepZ();
    }

    private TabletSurfaceScanner() {
    }
}
