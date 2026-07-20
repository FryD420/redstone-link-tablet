package com.modpack.linktablet.block;

import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.compat.TabletTransmitterHandler;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.network.ModNetworking;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/**
 * A tablet mounted on a wall, floor (table), or ceiling. Right-click to
 * open the same app GUI as the item; sneak + right-click with an empty
 * hand to pick it back up with all its data. Transmits from its own
 * position while any app is on (LIT blockstate lights the screen).
 */
public class TabletBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, IWrenchable {

    public static final MapCodec<TabletBlock> CODEC = simpleCodec(TabletBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Wall mounts only: the case itself turned sideways (bezel-wrench). */
    public static final BooleanProperty LANDSCAPE = BooleanProperty.create("landscape");

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

    public TabletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false)
                .setValue(LANDSCAPE, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, LIT, LANDSCAPE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        boolean landscape = state.getValue(LANDSCAPE);
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
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) {
            // Sneak + empty hand: pick the tablet back up, data intact
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
                ItemStack stack = be.toItemStack();
                level.removeBlock(pos, false);
                if (!player.addItem(stack)) {
                    popResource(level, pos, stack);
                }
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.8F, 1.1F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // Tap directly on an app's pip on the screen: toggle it without
        // opening the GUI. Runs identically on both sides (apps are
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
            List<SignalApp> apps = target.getApps();
            TabletScreenMath.PipHit pipHit = TabletScreenMath.hitPipDetailed(state, pos, hitResult,
                    apps.size(), target.isScreenList(), target.effectiveRotation(),
                    be.getSurfaceDx(), be.getSurfaceDy(),
                    target.getSurfaceW(), target.getSurfaceH());

            // Slider click-and-slide: the click grabs the slider and sets
            // the initial value (full glass-width mapping — 16 stops on
            // one tile would be untargetable); from there the CLIENT
            // drives the drag (BlockSliderDrag), projecting the look-ray
            // onto the screen plane every tick so sliding keeps working
            // past the tablet's edge. CONSUME: no arm swing.
            if (pipHit != null && apps.get(pipHit.index()).slider()) {
                int index = pipHit.index();
                if (level.isClientSide) {
                    ClientHooks.startBlockSliderDrag(pos, controllerPos, index);
                } else {
                    SignalApp app = apps.get(index);
                    float[] bar = TabletScreenMath.surfaceSliderBarU(index, apps.size(),
                            target.isScreenList(), target.effectiveRotation(),
                            target.getSurfaceW(), target.getSurfaceH());
                    float frac = net.minecraft.util.Mth.clamp(
                            (pipHit.logicalU() - bar[0]) / (bar[1] - bar[0]), 0.0F, 1.0F);
                    SignalApp updated = app.withSliderValue(app.valueFromFraction(frac));
                    if (updated.strength() != app.strength()) {
                        boolean wasOn = app.strength() > 0;
                        List<SignalApp> updatedApps = new ArrayList<>(apps);
                        updatedApps.set(index, updated);
                        target.setApps(updatedApps);
                        if (wasOn != (updated.strength() > 0)) {
                            ModNetworking.playToggleClick(level, null, pos, updated.strength() > 0);
                        }
                    }
                }
                return InteractionResult.CONSUME;
            }

            if (pipHit != null) {
                int index = pipHit.index();
                SignalApp app = apps.get(index);
                if (app.timed()) {
                    // Tap starts (or restarts) the timed pulse; the
                    // expiry loop ends it and plays the off-click.
                    // SUCCESS (unlike momentary's CONSUME) so the tap
                    // swings the arm like pressing a real button —
                    // holding merely re-taps, which for a timer just
                    // keeps the clock topped up.
                    if (!level.isClientSide) {
                        TabletTransmitterHandler.startTimed(player, true, controllerPos, index,
                                app.frequencies(), app.strength(), app.pulseTicks());
                        ModNetworking.playToggleClick(level, null, pos, true);
                    }
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
                if (app.momentary()) {
                    // Tap-and-hold: holding right-click repeats the use
                    // action, refreshing a self-expiring hold — the
                    // signal drops shortly after letting go. CONSUME
                    // (not SUCCESS) so the repeats don't swing the arm:
                    // feedback is the click sound and the lit pip.
                    if (!level.isClientSide) {
                        boolean newPress = TabletTransmitterHandler.pressBlockPip(player, controllerPos,
                                index, app.frequencies(), app.strength(), level.getGameTime());
                        if (newPress) {
                            ModNetworking.playToggleClick(level, null, pos, true);
                        }
                    }
                    return InteractionResult.CONSUME;
                }
                if (!level.isClientSide) {
                    List<SignalApp> updated = new ArrayList<>(apps);
                    updated.set(index, app.withActive(!app.active()));
                    target.setApps(updated);
                    // Unlike the GUI path, the clicker has no UI sound
                    // here, so nobody is excluded.
                    ModNetworking.playToggleClick(level, null, pos, !app.active());
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
        // Merged members skip the glass branch: content rotation is
        // clamped to 0 on surfaces, so a glass-wrench would be a silent
        // no-op — instead ANY wrench click block-rotates the clicked
        // member, which changes its merge key and splits it out.
        // "Wrenching a video wall restructures it."
        boolean merged = level.getBlockEntity(pos) instanceof TabletBlockEntity tbe && tbe.isMerged();
        boolean onGlass = !merged && TabletScreenMath.isOnGlass(state, pos,
                context.getClickedFace(), context.getClickLocation(), 1.0);

        if (onGlass) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
                be.rotateScreen();
                IWrenchable.playRotateSound(level, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        BlockState rotated = state.getValue(FACE) == AttachFace.WALL
                ? state.cycle(LANDSCAPE)
                : state.setValue(FACING, state.getValue(FACING).getClockWise());
        if (!level.isClientSide) {
            level.setBlock(pos, rotated, 3);
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
        if (be instanceof TabletBlockEntity tablet) {
            return List.of(tablet.toItemStack());
        }
        return List.of(new ItemStack(ModItems.TABLET.get()));
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
}
