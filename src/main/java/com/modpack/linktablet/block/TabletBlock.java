package com.modpack.linktablet.block;

import com.modpack.linktablet.client.ClientHooks;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.network.ModNetworking;
import com.modpack.linktablet.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
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

import java.util.ArrayList;
import java.util.List;

/**
 * A tablet mounted on a wall, floor (table), or ceiling. Right-click to
 * open the same app GUI as the item; sneak + right-click with an empty
 * hand to pick it back up with all its data. Transmits from its own
 * position while any app is on (LIT blockstate lights the screen).
 */
public class TabletBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<TabletBlock> CODEC = simpleCodec(TabletBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    // Panel is 12x14x1 pixels, flush against its support
    private static final VoxelShape FLOOR_NS = Block.box(2, 0, 1, 14, 1, 15);
    private static final VoxelShape FLOOR_EW = Block.box(1, 0, 2, 15, 1, 14);
    private static final VoxelShape CEILING_NS = Block.box(2, 15, 1, 14, 16, 15);
    private static final VoxelShape CEILING_EW = Block.box(1, 15, 2, 15, 16, 14);
    private static final VoxelShape WALL_N = Block.box(2, 1, 15, 14, 15, 16);
    private static final VoxelShape WALL_S = Block.box(2, 1, 0, 14, 15, 1);
    private static final VoxelShape WALL_E = Block.box(0, 1, 2, 1, 15, 14);
    private static final VoxelShape WALL_W = Block.box(15, 1, 2, 16, 15, 14);

    public TabletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH)
                .setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(FACE)) {
            case FLOOR -> facing.getAxis() == Direction.Axis.Z ? FLOOR_NS : FLOOR_EW;
            case CEILING -> facing.getAxis() == Direction.Axis.Z ? CEILING_NS : CEILING_EW;
            case WALL -> switch (facing) {
                case SOUTH -> WALL_S;
                case EAST -> WALL_E;
                case WEST -> WALL_W;
                default -> WALL_N;
            };
        };
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
            List<SignalApp> apps = be.getApps();
            int index = TabletScreenMath.hitPip(state, pos, hitResult, apps.size(), be.isScreenList());
            if (index >= 0 && !apps.get(index).momentary()) {
                if (!level.isClientSide) {
                    List<SignalApp> updated = new ArrayList<>(apps);
                    SignalApp app = updated.get(index);
                    updated.set(index, app.withActive(!app.active()));
                    be.setApps(updated);
                    // Unlike the GUI path, the clicker has no UI sound
                    // here, so nobody is excluded.
                    ModNetworking.playToggleClick(level, null, pos, !app.active());
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        if (level.isClientSide) {
            ClientHooks.openTabletBlockScreen(pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
