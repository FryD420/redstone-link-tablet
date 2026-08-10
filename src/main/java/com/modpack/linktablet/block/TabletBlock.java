package com.modpack.linktablet.block;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.compat.TabletTransmitterHandler;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.registry.ModBlocks;
import com.modpack.linktablet.registry.ModItems;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/**
 * A tablet mounted on a wall, floor (table), or ceiling. Right-click to
 * open the same signal GUI as the item; sneak + right-click with an empty
 * hand to pick it back up with all its data. Transmits from its own
 * position while any signal is on (LIT blockstate lights the screen).
 */
public class TabletBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<TabletBlock> CODEC = simpleCodec(TabletBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Wall mounts only: the case itself turned sideways (bezel-wrench). */
    public static final BooleanProperty LANDSCAPE = BooleanProperty.create("landscape");
    /**
     * Swivel mount (1.8.0): the tablet sits on a ball-joint stand and
     * aims at stored BE pitch/yaw. The chunk model goes EMPTY (blockstate
     * JSON can't tilt off-axis) — the BER draws stand, case, and screen.
     * Mounted tablets never merge; wrench = re-aim at the clicker's eyes.
     */
    public static final BooleanProperty MOUNTED = BooleanProperty.create("mounted");

    // Panel is 12x14x1 pixels, flush against its support
    private static final VoxelShape FLOOR_NS = Block.box(2, 0, 1, 14, 1, 15);
    private static final VoxelShape FLOOR_EW = Block.box(1, 0, 2, 15, 1, 14);
    private static final VoxelShape CEILING_NS = Block.box(2, 15, 1, 14, 16, 15);
    private static final VoxelShape CEILING_EW = Block.box(1, 15, 2, 15, 16, 14);
    private static final VoxelShape WALL_N = Block.box(2, 1, 15, 14, 15, 16);
    private static final VoxelShape WALL_S = Block.box(2, 1, 0, 14, 15, 1);
    private static final VoxelShape WALL_E = Block.box(0, 1, 2, 1, 15, 14);
    private static final VoxelShape WALL_W = Block.box(15, 1, 2, 16, 15, 14);
    // 14x12 when the wall case is turned sideways
    private static final VoxelShape WALL_N_LAND = Block.box(1, 2, 15, 15, 14, 16);
    private static final VoxelShape WALL_S_LAND = Block.box(1, 2, 0, 15, 14, 1);
    private static final VoxelShape WALL_E_LAND = Block.box(0, 2, 1, 1, 14, 15);
    private static final VoxelShape WALL_W_LAND = Block.box(15, 2, 1, 16, 14, 15);
    // Mounted: one coarse box per attach face — VoxelShapes can't tilt,
    // so this just has to contain the stand plus the swiveling panel
    private static final VoxelShape MOUNT_FLOOR = Block.box(2, 0, 2, 14, 11, 14);
    private static final VoxelShape MOUNT_CEILING = Block.box(2, 5, 2, 14, 16, 14);
    private static final VoxelShape MOUNT_WALL_N = Block.box(2, 2, 5, 14, 14, 16);
    private static final VoxelShape MOUNT_WALL_S = Block.box(2, 2, 0, 14, 14, 11);
    private static final VoxelShape MOUNT_WALL_E = Block.box(0, 2, 2, 11, 14, 14);
    private static final VoxelShape MOUNT_WALL_W = Block.box(5, 2, 2, 16, 14, 14);

    public TabletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false)
                .setValue(LANDSCAPE, false)
                .setValue(MOUNTED, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, LIT, LANDSCAPE, MOUNTED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean landscape = state.getValue(LANDSCAPE);
        if (state.getValue(MOUNTED)) {
            return switch (state.getValue(FACE)) {
                case FLOOR -> MOUNT_FLOOR;
                case CEILING -> MOUNT_CEILING;
                case WALL -> switch (facing) {
                    case SOUTH -> MOUNT_WALL_S;
                    case EAST -> MOUNT_WALL_E;
                    case WEST -> MOUNT_WALL_W;
                    default -> MOUNT_WALL_N;
                };
            };
        }
        return switch (state.getValue(FACE)) {
            case FLOOR -> facing.getAxis() == Direction.Axis.Z ? FLOOR_NS : FLOOR_EW;
            case CEILING -> facing.getAxis() == Direction.Axis.Z ? CEILING_NS : CEILING_EW;
            case WALL -> switch (facing) {
                case SOUTH -> landscape ? WALL_S_LAND : WALL_S;
                case EAST -> landscape ? WALL_E_LAND : WALL_E;
                case WEST -> landscape ? WALL_W_LAND : WALL_W;
                default -> landscape ? WALL_N_LAND : WALL_N;
            };
        };
    }

    /**
     * Merge-friendly placement (1.7.0): floor/ceiling tablets face
     * wherever the PLAYER faced at placement, so hand-placing a grid
     * naturally produces mixed FACINGs — different merge keys, no
     * surface. Adopting a coplanar neighbor's orientation makes
     * "slap tablets next to each other" just work; wall tablets
     * similarly adopt a neighbor's LANDSCAPE flip.
     */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (state.getValue(FACE) == AttachFace.WALL) {
            Direction facing = state.getValue(FACING);
            for (Direction dir : Direction.values()) {
                if (dir.getAxis() == facing.getAxis()) continue;
                BlockState nb = level.getBlockState(pos.relative(dir));
                if (nb.getBlock() instanceof TabletBlock
                        && nb.getValue(FACE) == AttachFace.WALL
                        && nb.getValue(FACING) == facing) {
                    return state.setValue(LANDSCAPE, nb.getValue(LANDSCAPE));
                }
            }
        } else {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockState nb = level.getBlockState(pos.relative(dir));
                if (nb.getBlock() instanceof TabletBlock
                        && nb.getValue(FACE) == state.getValue(FACE)) {
                    return state.setValue(FACING, nb.getValue(FACING));
                }
            }
        }
        return state;
    }

    // ------------------------------------------------------------------
    // Multiblock surface formation (1.7.0). onPlace/onRemove only
    // SCHEDULE a scan tick: onPlace fires before the BE exists (and
    // before TabletItem.useOn's loadFromItem), and LIT flips re-enter
    // onPlace — running the scanner synchronously would recurse. The
    // merge-key guard below is load-bearing: LIT-only state changes MUST
    // NOT schedule, or updateLit -> setBlock -> onPlace would loop.
    // ------------------------------------------------------------------

    /** Whether two states of this block differ in a merge-relevant way. */
    private static boolean mergeKeyChanged(BlockState a, BlockState b) {
        return a.getValue(FACE) != b.getValue(FACE)
                || a.getValue(FACING) != b.getValue(FACING)
                || a.getValue(LANDSCAPE) != b.getValue(LANDSCAPE);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos,
                           BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide) return;
        if (oldState.is(this) && !mergeKeyChanged(state, oldState)) return; // LIT-only flip
        level.scheduleTick(pos, this, 0);
        if (oldState.is(this)) {
            // Orientation changed in place (wrench): old neighbors may
            // need to reform without this block
            scheduleNeighborScans(level, pos, oldState);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && (!newState.is(this) || mergeKeyChanged(state, newState))) {
            scheduleNeighborScans(level, pos, state);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Schedules scans on the coplanar tablet neighbors of a vanished member. */
    private void scheduleNeighborScans(Level level, BlockPos pos, BlockState state) {
        Direction right = TabletScreenMath.screenRight(state);
        Direction down = TabletScreenMath.screenDown(state);
        for (Direction dir : new Direction[]{right, right.getOpposite(), down, down.getOpposite()}) {
            BlockPos next = pos.relative(dir);
            if (level.isLoaded(next) && level.getBlockState(next).is(this)) {
                level.scheduleTick(next, this, 0);
            }
        }
    }

    @Override
    protected void tick(BlockState state, net.minecraft.server.level.ServerLevel level,
                        BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.is(this)) {
            TabletSurfaceScanner.rescan(level, pos, state);
        }
    }

    /**
     * Wrenches never reach their own {@code useOn} unless the block
     * steps aside — the block interaction runs first and this block
     * consumes every click (pip toggle or GUI). Skipping here lets
     * {@code WrenchItem.useOn} route into {@link #onWrenched}.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (stack.is(Tags.Items.TOOLS_WRENCH)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        // Swivel mount install (1.8.0): stand appears, tablet aims at
        // the installer's eyes. Merged surfaces must be split first.
        if (stack.is(ModItems.SWIVEL_MOUNT.get()) && !state.getValue(MOUNTED)) {
            if (level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
                if (be.isMerged()) {
                    if (!level.isClientSide) {
                        player.displayClientMessage(net.minecraft.network.chat.Component
                                .translatable("message.linktablet.mount_merged"), true);
                    }
                    return ItemInteractionResult.FAIL;
                }
                if (!level.isClientSide) {
                    level.setBlock(pos, state.setValue(MOUNTED, true), 3);
                    if (level.getBlockEntity(pos) instanceof TabletBlockEntity mounted) {
                        mounted.aimAt(player.getEyePosition());
                    }
                    if (!player.getAbilities().instabuild) {
                        stack.shrink(1);
                    }
                    level.playSound(null, audiblePos(level, pos), SoundEvents.AMETHYST_BLOCK_PLACE,
                            SoundSource.BLOCKS, 0.8F, 1.3F);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) {
            // Sneak + empty hand: pick the tablet back up, data intact.
            // Mounted (1.8.1): grabbing the PANEL takes just the tablet
            // and leaves the stand as its own block for the next tablet;
            // grabbing the stand (or anywhere off-panel) takes both.
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
                ItemStack stack = be.toItemStack();
                boolean mounted = state.getValue(MOUNTED);
                boolean tabletOnly = false;
                if (mounted) {
                    Vec3[] ray = mountedRay(level, pos, player, hitResult.getLocation());
                    tabletOnly = mountedOnPanel(
                            TabletScreenMath.mountedUV(be.mountBasis(), ray[0], ray[1]));
                }
                if (tabletOnly) {
                    level.setBlock(pos, standState(state), 3);
                } else {
                    level.removeBlock(pos, false);
                }
                if (!player.addItem(stack)) {
                    popResource(level, audiblePos(level, pos), stack);
                }
                if (mounted && !tabletOnly) {
                    ItemStack mount = new ItemStack(ModItems.SWIVEL_MOUNT.get());
                    if (!player.addItem(mount)) {
                        popResource(level, audiblePos(level, pos), mount);
                    }
                }
                level.playSound(null, audiblePos(level, pos), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 1.1F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // Tap directly on a signal's pip on the screen: toggle it without
        // opening the GUI. Runs identically on both sides (signals are
        // synced and the server receives the client's exact hit vec),
        // so client and server always agree on pip-vs-GUI.
        if (level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
            // Merged surfaces (1.7.0): every action lands on the
            // CONTROLLER's data; the clicked member only contributes its
            // position on the surface to the hit test. Orphaned parts
            // (controller unloaded/stale) are inert.
            TabletBlockEntity target = be.resolveController();
            if (target == null) {
                return InteractionResult.CONSUME;
            }
            BlockPos controllerPos = target.getBlockPos();

            // Kiosk launcher (1.10.0): the screen's current program
            // routes the tap, derived ONLY from synced BE state so both
            // sides route identically (the pip invariant, extended).
            // The launcher face renders through the exact signal-grid
            // pipeline with the roster as its entries, so its hit test
            // IS the pip hit test with the roster count — one layout
            // table, nothing to drift.
            if (target.currentProgram() == Program.LAUNCHER) {
                // The tile grid is the TABLET's home roster (1.10.0
                // settable roster) — synced BE data, so both sides
                // compute the same layout
                // List mode counts here too (test pass 2): the renderer
                // draws roster rows through the same list table
                java.util.List<com.modpack.linktablet.api.TabletProgram> home = target.getHomeApps();
                TabletScreenMath.PipHit tile = be.isMounted()
                        ? TabletScreenMath.mountedHitPip(be.mountBasis(),
                                com.modpack.linktablet.compat.SableCompat.localizeNear(
                                        level, pos, player.getEyePosition()),
                                com.modpack.linktablet.compat.SableCompat.localizeNear(
                                        level, pos, hitResult.getLocation()),
                                home.size(), target.isScreenList(),
                                target.effectiveRotation())
                        : TabletScreenMath.hitPipDetailed(state, pos, hitResult,
                                home.size(), target.isScreenList(), target.effectiveRotation(),
                                be.getSurfaceDx(), be.getSurfaceDy(),
                                target.getSurfaceW(), target.getSurfaceH());
                if (tile != null) {
                    if (!level.isClientSide) {
                        target.setCurrentProgram(home.get(tile.index()));
                        ModNetworking.playToggleClick(level, null, pos, true);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                // Off-tile launcher taps (bezel, glass margins) open the
                // LAUNCHER GUI — the device settings menu, never the
                // signals grid (user decision after the first kiosk
                // test). MUST return here: falling through would
                // hit-test the INVISIBLE signals.
                if (level.isClientSide) {
                    ClientHooks.openBlockHome(controllerPos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else if (be.isMounted()
                    ? mountedOnBezel(be, player, hitResult)
                    : TabletScreenMath.hitBezel(state, pos, hitResult,
                            be.getSurfaceDx(), be.getSurfaceDy(),
                            target.getSurfaceW(), target.getSurfaceH())) {
                // A program showing: a bezel tap goes Home (1.10.0
                // gesture — glass is content, the ring is nav). Wrench
                // paths are separate methods and keep their bezel
                // meanings.
                if (!level.isClientSide) {
                    target.setCurrentProgram(Program.LAUNCHER);
                    ModNetworking.playToggleClick(level, null, pos, false);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            } else if (target.currentProgram() != Program.SIGNALS) {
                // Non-signals program face (1.10.0 suite — clock etc.):
                // the glass is a window into a client-side program, so a
                // tap opens its screen client-side (the secret-game
                // precedent) — MUST return here, or the tap would
                // hit-test the INVISIBLE signals underneath.
                if (level.isClientSide) {
                    ClientHooks.openBlockProgram(target.currentProgram(), controllerPos);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            List<Signal> signals = target.getSignals();
            // Mounted tablets (1.8.0): the hit location sits on the
            // COARSE voxel box, so re-intersect the eye ray with the
            // actual angled glass plane instead (both sides derive the
            // basis from the same synced pitch/yaw).
            TabletScreenMath.PipHit pipHit = be.isMounted()
                    ? TabletScreenMath.mountedHitPip(be.mountBasis(),
                            com.modpack.linktablet.compat.SableCompat.localizeNear(
                                    level, pos, player.getEyePosition()),
                            com.modpack.linktablet.compat.SableCompat.localizeNear(
                                    level, pos, hitResult.getLocation()),
                            signals.size(), target.isScreenList(),
                            target.effectiveRotation())
                    : TabletScreenMath.hitPipDetailed(state, pos, hitResult,
                            signals.size(), target.isScreenList(), target.effectiveRotation(),
                            be.getSurfaceDx(), be.getSurfaceDy(),
                            target.getSurfaceW(), target.getSurfaceH());

            // 🕹️ Secret-game shortcuts are client-side programs, not
            // signals: their pip is inert on the server, and the client
            // opens the game GUI. Checked before every signal type so the
            // disguise works whatever shape the signal was saved in.
            if (pipHit != null) {
                String game = signals.get(pipHit.index()).secretGameId();
                if (game != null) {
                    if (level.isClientSide) {
                        ClientHooks.openSecretGame(game, controllerPos);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }

            // Slider click-and-slide: the click grabs the slider and sets
            // the initial value (full glass-width mapping — 16 stops on
            // one tile would be untargetable); from there the CLIENT
            // drives the drag (BlockSliderDrag), projecting the look-ray
            // onto the screen plane every tick so sliding keeps working
            // past the tablet's edge. CONSUME: no arm swing.
            if (pipHit != null && signals.get(pipHit.index()).slider()) {
                int index = pipHit.index();
                if (level.isClientSide) {
                    ClientHooks.startBlockSliderDrag(pos, controllerPos, index);
                } else {
                    Signal signal = signals.get(index);
                    float[] bar = TabletScreenMath.surfaceSliderBarU(index, signals.size(),
                            target.isScreenList(), target.effectiveRotation(),
                            target.getSurfaceW(), target.getSurfaceH());
                    float frac = net.minecraft.util.Mth.clamp(
                            (pipHit.logicalU() - bar[0]) / (bar[1] - bar[0]), 0.0F, 1.0F);
                    Signal updated = signal.withSliderValue(signal.valueFromFraction(frac));
                    if (updated.strength() != signal.strength()) {
                        boolean wasOn = signal.strength() > 0;
                        List<Signal> updatedSignals = new ArrayList<>(signals);
                        updatedSignals.set(index, updated);
                        target.setSignals(updatedSignals);
                        if (wasOn != (updated.strength() > 0)) {
                            ModNetworking.playToggleClick(level, null, pos, updated.strength() > 0);
                        }
                    }
                }
                return InteractionResult.CONSUME;
            }

            if (pipHit != null) {
                int index = pipHit.index();
                Signal signal = signals.get(index);
                if (signal.timed()) {
                    // Tap starts (or restarts) the timed pulse; the
                    // expiry loop ends it and plays the off-click.
                    // SUCCESS (unlike momentary's CONSUME) so the tap
                    // swings the arm like pressing a real button —
                    // holding merely re-taps, which for a timer just
                    // keeps the clock topped up.
                    if (!level.isClientSide) {
                        TabletTransmitterHandler.startTimed(player, true, controllerPos, index,
                                signal.frequencies(), signal.strength(), signal.pulseTicks());
                        ModNetworking.playToggleClick(level, null, pos, true);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                if (signal.momentary()) {
                    // Tap-and-hold: holding right-click repeats the use
                    // action, refreshing a self-expiring hold — the
                    // signal drops shortly after letting go. CONSUME
                    // (not SUCCESS) so the repeats don't swing the arm:
                    // feedback is the click sound and the lit pip.
                    if (!level.isClientSide) {
                        boolean newPress = TabletTransmitterHandler.pressBlockPip(player, controllerPos,
                                index, signal.frequencies(), signal.strength(), level.getGameTime());
                        if (newPress) {
                            ModNetworking.playToggleClick(level, null, pos, true);
                        }
                    }
                    return InteractionResult.CONSUME;
                }
                if (!level.isClientSide) {
                    List<Signal> updated = new ArrayList<>(signals);
                    updated.set(index, signal.withActive(!signal.active()));
                    // Signal links (1.10.0): the tap also drives linked
                    // targets — ONE shared helper with handleToggle
                    com.modpack.linktablet.frequency.SignalLinks.propagate(updated, index,
                            (timedIndex, timedTarget) -> com.modpack.linktablet.compat
                                    .TabletTransmitterHandler.startTimed(player, true,
                                            controllerPos, timedIndex, timedTarget.frequencies(),
                                            timedTarget.strength(), timedTarget.pulseTicks()));
                    target.setSignals(updated);
                    // Unlike the GUI path, the clicker has no UI sound
                    // here, so nobody is excluded.
                    ModNetworking.playToggleClick(level, null, pos, !signal.active());
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (level.isClientSide) {
                ClientHooks.openTabletBlockScreen(controllerPos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            ClientHooks.openTabletBlockScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Create wrench: clicking the glass rotates the screen content 90°
     * CW; clicking the bezel (or any edge/back face) rotates the tablet
     * itself — floor/ceiling spin on their support, wall mounts flip the
     * case portrait ↔ landscape. The wrench's glass region is inset one
     * texel so the 1-texel bezel ring is actually hittable. Sneak-wrench
     * pickup is the {@link IWrenchable} default — {@link #getDrops}
     * already returns the tablet with its data.
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // Mounted (1.8.0): wrench on the GLASS re-aims the screen at the
        // clicker's eyes; wrench on the BEZEL ring flips portrait ↔
        // landscape — on ANY attach face, not just walls (the mount
        // basis honors LANDSCAPE everywhere). Stand/case clicks re-aim
        // too. Content rotation parks until unmounted (sneak-wrench
        // pickup returns tablet + mount).
        if (state.getValue(MOUNTED)) {
            if (!level.isClientSide && context.getPlayer() != null
                    && level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
                double[] uv = mountedWrenchUV(be, context);
                if (mountedOnPanel(uv) && !mountedOnGlass(uv)) {
                    level.setBlock(pos, state.cycle(LANDSCAPE), 3);
                } else {
                    be.aimAt(context.getPlayer().getEyePosition());
                }
                IWrenchable.playRotateSound(level, audiblePos(level, pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // Merged surfaces: the continuous panel hides where the member
        // bezels physically are, so bezel clicks would be invisible
        // unmerge traps — a plain wrench ANYWHERE on a merged surface
        // rotates the content (square surfaces get quarter turns,
        // oblong ones half turns). Restructuring is deliberate only:
        // sneak-wrench pickup or breaking a member.
        boolean merged = level.getBlockEntity(pos) instanceof TabletBlockEntity clicked
                && clicked.isMerged();
        boolean onGlass = merged || TabletScreenMath.isOnGlass(state, pos,
                context.getClickedFace(), context.getClickLocation(), 1.0);

        if (onGlass) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
                TabletBlockEntity target = be.resolveController();
                if (target != null) {
                    if (target.isMerged()) {
                        target.rotateScreenSurface();
                    } else {
                        target.rotateScreen();
                    }
                    IWrenchable.playRotateSound(level, audiblePos(level, pos));
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockState rotated = state.getValue(FACE) == AttachFace.WALL
                ? state.cycle(LANDSCAPE)
                : state.setValue(FACING, state.getValue(FACING).getClockWise());
        if (!level.isClientSide) {
            level.setBlock(pos, rotated, 3);
            IWrenchable.playRotateSound(level, audiblePos(level, pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Mounted sneak-wrench (1.8.0, regions split in 1.8.1): on the GLASS
     * it rotates the screen content 90° — the one gesture the mounted
     * wrench map had left (plain glass = aim, bezel = landscape). On the
     * BEZEL ring it pops just the tablet off, leaving the empty stand
     * block for the next tablet (the easier big-target version of that
     * detach is sneak + empty hand on the panel). Anywhere else — the
     * stand — keeps the IWrenchable default: pickup, tablet + mount.
     */
    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (state.getValue(MOUNTED) && context.getPlayer() != null
                && level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
            double[] uv = mountedWrenchUV(be, context);
            if (mountedOnGlass(uv)) {
                if (!level.isClientSide) {
                    be.rotateScreen();
                    IWrenchable.playRotateSound(level, audiblePos(level, pos));
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (mountedOnPanel(uv)) {
                if (!level.isClientSide) {
                    ItemStack tablet = be.toItemStack();
                    level.setBlock(pos, standState(state), 3);
                    context.getPlayer().getInventory().placeItemBackInInventory(tablet);
                    level.playSound(null, audiblePos(level, pos), SoundEvents.AMETHYST_BLOCK_BREAK,
                            SoundSource.BLOCKS, 0.8F, 1.1F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return IWrenchable.super.onSneakWrenched(state, context);
    }

    /** The empty stand left behind when only the tablet is taken (1.8.1). */
    private static BlockState standState(BlockState state) {
        return ModBlocks.SWIVEL_MOUNT.get().defaultBlockState()
                .setValue(FACE, state.getValue(FACE))
                .setValue(FACING, state.getValue(FACING));
    }

    /** Eye-ray screen texels under a wrench click on a mounted tablet. */
    /**
     * Sounds and item drops for a plot block must land where the block
     * APPEARS in the world, not at its Sable plot coordinates (20M
     * blocks from any ear). Identity for normal tablets.
     */
    private static BlockPos audiblePos(Level level, BlockPos pos) {
        return com.modpack.linktablet.compat.SableCompat.worldBlockPos(level, pos);
    }

    /**
     * Sable sub-level (1.10.1): a physicalized tablet's blocks live in
     * the plot frame while the player stays in world space — every
     * mounted ray must localize its eye AND target point before the
     * math. Returns {@code {eye, delta}} in the BLOCK's frame; identity
     * (and free) for normal tablets.
     */
    private static Vec3[] mountedRay(Level level, BlockPos pos, Player player, Vec3 target) {
        Vec3 eye = com.modpack.linktablet.compat.SableCompat
                .localizeNear(level, pos, player.getEyePosition());
        Vec3 hit = com.modpack.linktablet.compat.SableCompat
                .localizeNear(level, pos, target);
        return new Vec3[]{eye, hit.subtract(eye)};
    }

    private static double[] mountedWrenchUV(TabletBlockEntity be, UseOnContext context) {
        Vec3[] ray = mountedRay(context.getLevel(), context.getClickedPos(),
                context.getPlayer(), context.getClickLocation());
        return TabletScreenMath.mountedUV(be.mountBasis(), ray[0], ray[1]);
    }

    /** Panel is texels u 0..12, v 0..14. */
    private static boolean mountedOnPanel(double[] uv) {
        return uv != null && uv[0] >= 0 && uv[0] < 12 && uv[1] >= 0 && uv[1] < 14;
    }

    /** Glass with the wrench's 1-texel inset, so the bezel ring is targetable. */
    private static boolean mountedOnGlass(double[] uv) {
        return uv != null
                && uv[0] >= TabletScreenMath.GLASS_U0 + 1 && uv[0] < TabletScreenMath.GLASS_U1 - 1
                && uv[1] >= TabletScreenMath.GLASS_V0 + 1 && uv[1] < TabletScreenMath.GLASS_V1 - 1;
    }

    /** Mounted bezel ring for the bare-hand Home tap (1.10.0): on the
     * panel but off the (wrench-inset) glass — the same band the wrench
     * uses for its landscape flip, so the two gestures agree on edges. */
    private static boolean mountedOnBezel(TabletBlockEntity be, Player player,
                                          BlockHitResult hitResult) {
        Vec3[] ray = mountedRay(be.getLevel(), be.getBlockPos(), player,
                hitResult.getLocation());
        double[] uv = TabletScreenMath.mountedUV(be.mountBasis(), ray[0], ray[1]);
        return mountedOnPanel(uv) && !mountedOnGlass(uv);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        // No loot table: the drop is always the tablet item with its data,
        // including when the supporting block is broken.
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        ItemStack tabletStack = be instanceof TabletBlockEntity tablet
                ? tablet.toItemStack()
                : new ItemStack(ModItems.TABLET.get());
        // The swivel mount comes back as its own item
        if (state.getValue(MOUNTED)) {
            return List.of(tabletStack, new ItemStack(ModItems.SWIVEL_MOUNT.get()));
        }
        return List.of(tabletStack);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof TabletBlockEntity be
                ? be.toItemStack()
                : new ItemStack(ModItems.TABLET.get());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TabletBlockEntity(pos, state);
    }

    // ------------------------------------------------------------------
    // Redstone follow mode (1.10.0): a POWERED mounted tablet tracks the
    // nearest player. Power is a transient BE flag (re-derived on load);
    // the ticker exists ONLY for mounted states — the chunk rebuilds its
    // ticker on every blockstate change, so plain tablets never tick.
    // ------------------------------------------------------------------

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos,
                                   net.minecraft.world.level.block.Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
            be.setFollowPowered(level.hasNeighborSignal(pos));
        }
    }

    @Override
    @org.jetbrains.annotations.Nullable
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide || !state.getValue(MOUNTED)
                || type != com.modpack.linktablet.registry.ModBlockEntities.TABLET.get()) {
            return null;
        }
        return (tickLevel, tickPos, tickState, be) -> ((TabletBlockEntity) be).followTick();
    }
}
