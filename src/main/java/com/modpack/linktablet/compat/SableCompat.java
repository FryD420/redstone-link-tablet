package com.modpack.linktablet.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * Sable (Create: Aeronautics) sub-level compat, 1.10.1. Sable
 * "physicalizes" contraptions by moving their blocks into a hidden plot
 * region of the SAME level (~20M blocks out) and rendering them back
 * through a pose transform — so a mounted tablet's {@code worldPosition}
 * is plot-space while the player's eye stays world-space. Every mounted
 * ray computation must bring the player into the PLOT frame first
 * (verified live 2026-08-03: eye→pivot distance 28.9M, aim pinned at the
 * 75° clamp).
 *
 * <p>SOFT dependency by reflection, no compile-time Sable: the classes we
 * need are partly jar-in-jar ({@code Pose3dc} ships inside Sable's
 * companion jar) and Sable is alpha — if any handle fails to resolve the
 * shim disables itself for the session and the distance guard in
 * {@code TabletBlockEntity.computeAim} keeps garbage out of the stored
 * angles. Resolved chain (javap'd from sable 2.0.3, all public API):
 * {@code SubLevelContainer.getContainer(Level)} → {@code inBounds(BlockPos)}
 * → {@code getPlot(ChunkPos)} → {@code LevelPlot.getSubLevel()} →
 * {@code SubLevel.logicalPose()} → {@code Pose3dc.transformPosition[Inverse]
 * (Vec3)} / {@code transformNormalInverse(Vec3)}.
 *
 * <p>All helpers are identity passthroughs (SAME reference back) when
 * Sable is absent, the shim is disabled, or the position isn't in a plot
 * — callers may {@code ==} the result to learn whether a transform
 * happened (the slider drag uses that to decide about the look vector).
 */
public final class SableCompat {

    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger("linktablet-sable-compat");

    /**
     * A point farther than this from the target block is in the WRONG
     * frame ({@link #localizeNear}'s auto-detect): reach is ~5 blocks,
     * follow range 8, slider drag 8 — while a frame mismatch is millions.
     * Generous so creative-reach mods stay inside it.
     */
    private static final double SAME_FRAME_DIST_SQ = 16 * 16;

    private SableCompat() {
    }

    // ---- Lazy reflective binding -------------------------------------
    // Holder idiom: nothing Sable-shaped is touched until the first call,
    // and a resolution failure downgrades to "not loaded" forever.

    private static final class Handles {
        static final MethodHandle GET_CONTAINER;   // (Level) -> SubLevelContainer
        static final MethodHandle IN_BOUNDS;       // (container, BlockPos) -> boolean
        static final MethodHandle GET_PLOT;        // (container, ChunkPos) -> LevelPlot
        static final MethodHandle GET_SUB_LEVEL;   // (plot) -> SubLevel
        static final MethodHandle LOGICAL_POSE;    // (subLevel) -> Pose3dc
        static final MethodHandle TO_LOCAL_POINT;  // (pose, Vec3) -> Vec3
        static final MethodHandle TO_LOCAL_DIR;    // (pose, Vec3) -> Vec3
        static final MethodHandle TO_WORLD_POINT;  // (pose, Vec3) -> Vec3
        static final boolean READY;

        static {
            MethodHandle getContainer = null, inBounds = null, getPlot = null,
                    getSubLevel = null, logicalPose = null,
                    toLocalPoint = null, toLocalDir = null, toWorldPoint = null;
            boolean ready = false;
            if (net.neoforged.fml.ModList.get().isLoaded("sable")) {
                try {
                    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                    Class<?> container = Class.forName(
                            "dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                    Class<?> plot = Class.forName(
                            "dev.ryanhcode.sable.sublevel.plot.LevelPlot");
                    Class<?> subLevel = Class.forName(
                            "dev.ryanhcode.sable.sublevel.SubLevel");
                    Class<?> pose = Class.forName(
                            "dev.ryanhcode.sable.companion.math.Pose3dc");
                    getContainer = lookup.unreflect(
                            container.getMethod("getContainer", Level.class));
                    inBounds = lookup.unreflect(
                            container.getMethod("inBounds", BlockPos.class));
                    getPlot = lookup.unreflect(
                            container.getMethod("getPlot", ChunkPos.class));
                    getSubLevel = lookup.unreflect(plot.getMethod("getSubLevel"));
                    logicalPose = lookup.unreflect(subLevel.getMethod("logicalPose"));
                    toLocalPoint = lookup.unreflect(
                            pose.getMethod("transformPositionInverse", Vec3.class));
                    toLocalDir = lookup.unreflect(
                            pose.getMethod("transformNormalInverse", Vec3.class));
                    toWorldPoint = lookup.unreflect(
                            pose.getMethod("transformPosition", Vec3.class));
                    ready = true;
                    LOG.info("Sable detected — sub-level coordinate compat active");
                } catch (Throwable t) {
                    // Alpha API moved under us: run without the transform,
                    // the computeAim guard keeps stored aims safe.
                    LOG.warn("Sable present but compat failed to bind — mounted "
                            + "tablets on sub-levels will hold their aim instead "
                            + "of tracking", t);
                }
            }
            GET_CONTAINER = getContainer;
            IN_BOUNDS = inBounds;
            GET_PLOT = getPlot;
            GET_SUB_LEVEL = getSubLevel;
            LOGICAL_POSE = logicalPose;
            TO_LOCAL_POINT = toLocalPoint;
            TO_LOCAL_DIR = toLocalDir;
            TO_WORLD_POINT = toWorldPoint;
            READY = ready;
        }
    }

    /** The pose owning {@code pos}'s plot, or null when there isn't one. */
    private static Object poseAt(Level level, BlockPos pos) {
        if (!Handles.READY) return null;
        try {
            Object container = Handles.GET_CONTAINER.invoke(level);
            if (container == null || !(boolean) Handles.IN_BOUNDS.invoke(container, pos)) {
                return null;
            }
            Object plot = Handles.GET_PLOT.invoke(container, new ChunkPos(pos));
            if (plot == null) return null;
            Object subLevel = Handles.GET_SUB_LEVEL.invoke(plot);
            return subLevel == null ? null : Handles.LOGICAL_POSE.invoke(subLevel);
        } catch (Throwable t) {
            LOG.warn("Sable compat lookup failed at {}", pos, t);
            return null;
        }
    }

    // ---- Call-site helpers -------------------------------------------

    /**
     * Brings a world-space POINT (eye, hit location) into the frame of
     * the block at {@code pos} — identity if the point is already near
     * the block (same frame: normal tablets never pay for a Sable
     * lookup, and points Sable pre-localized stay untouched), or if no
     * transform is available. The one auto-detect rule for every mounted
     * ray site; directions can't self-identify, see {@link #toLocalDir}.
     */
    public static Vec3 localizeNear(Level level, BlockPos pos, Vec3 point) {
        if (point.distanceToSqr(Vec3.atCenterOf(pos)) < SAME_FRAME_DIST_SQ) {
            return point;
        }
        Object pose = poseAt(level, pos);
        if (pose == null) return point;
        try {
            return (Vec3) Handles.TO_LOCAL_POINT.invoke(pose, point);
        } catch (Throwable t) {
            return point;
        }
    }

    /**
     * World-space DIRECTION → plot frame. No auto-detect is possible for
     * a direction: call it exactly when {@link #localizeNear} transformed
     * the paired point ({@code localEye != eye}).
     */
    public static Vec3 toLocalDir(Level level, BlockPos pos, Vec3 dir) {
        Object pose = poseAt(level, pos);
        if (pose == null) return dir;
        try {
            return (Vec3) Handles.TO_LOCAL_DIR.invoke(pose, dir);
        } catch (Throwable t) {
            return dir;
        }
    }

    /**
     * Plot-space point → world space: where a sub-level block ACTUALLY is
     * for things that live in world space — follow mode's player search,
     * sound playback, item drops.
     */
    public static Vec3 toWorldPoint(Level level, BlockPos pos, Vec3 point) {
        Object pose = poseAt(level, pos);
        if (pose == null) return point;
        try {
            return (Vec3) Handles.TO_WORLD_POINT.invoke(pose, point);
        } catch (Throwable t) {
            return point;
        }
    }

    /** {@link #toWorldPoint} for a block's center, as a BlockPos (sounds/drops). */
    public static BlockPos worldBlockPos(Level level, BlockPos pos) {
        Vec3 world = toWorldPoint(level, pos, Vec3.atCenterOf(pos));
        return BlockPos.containing(world);
    }
}
