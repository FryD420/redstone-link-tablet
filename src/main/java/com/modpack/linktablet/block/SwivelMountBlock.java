package com.modpack.linktablet.block;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * The empty swivel stand (1.8.1) — the Swivel Mount placed on its own,
 * waiting for a tablet. Right-click it with a tablet to mount the tablet
 * on it (aimed at your eyes, exactly like installing the mount onto a
 * placed tablet). Sneak + empty hand or sneak-wrench takes the stand
 * back. The chunk model is the same standalone-baked swivel_stand the
 * BER uses for mounted tablets, posed by blockstate rotations.
 */
public class SwivelMountBlock extends FaceAttachedHorizontalDirectionalBlock implements IWrenchable {

    public static final MapCodec<SwivelMountBlock> CODEC = simpleCodec(SwivelMountBlock::new);

    // Base plate plus ball, per attach face (stand is symmetric — the
    // wall boxes only depend on which wall carries the base plate)
    private static final VoxelShape FLOOR = Block.box(6, 0, 6, 10, 7, 10);
    private static final VoxelShape CEILING = Block.box(6, 9, 6, 10, 16, 10);
    private static final VoxelShape WALL_N = Block.box(6, 6, 9, 10, 10, 16);
    private static final VoxelShape WALL_S = Block.box(6, 6, 0, 10, 10, 7);
    private static final VoxelShape WALL_E = Block.box(0, 6, 6, 7, 10, 10);
    private static final VoxelShape WALL_W = Block.box(9, 6, 6, 16, 10, 10);

    public SwivelMountBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACE, AttachFace.FLOOR)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR;
            case CEILING -> CEILING;
            case WALL -> switch (state.getValue(FACING)) {
                case SOUTH -> WALL_S;
                case EAST -> WALL_E;
                case WEST -> WALL_W;
                default -> WALL_N;
            };
        };
    }

    /**
     * Tablet meets stand: the stand block becomes a mounted tablet in
     * place — same conversion the mount item does on a placed tablet,
     * just approached from the other side.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hitResult) {
        if (stack.is(ModItems.TABLET.get())) {
            if (!level.isClientSide) {
                BlockState mounted = ModBlocks.TABLET.get().defaultBlockState()
                        .setValue(FACE, state.getValue(FACE))
                        .setValue(FACING, state.getValue(FACING))
                        .setValue(TabletBlock.MOUNTED, true);
                level.setBlock(pos, mounted, 3);
                if (level.getBlockEntity(pos) instanceof TabletBlockEntity be) {
                    be.loadFromItem(stack);
                    be.aimAt(player.getEyePosition());
                }
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE,
                        SoundSource.BLOCKS, 0.8F, 1.3F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) {
            // Sneak + empty hand: take the stand back, like a tablet
            if (!level.isClientSide) {
                level.removeBlock(pos, false);
                ItemStack mount = new ItemStack(ModItems.SWIVEL_MOUNT.get());
                if (!player.addItem(mount)) {
                    popResource(level, pos, mount);
                }
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK,
                        SoundSource.BLOCKS, 0.8F, 1.1F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
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
        // No loot table, matching TabletBlock: the drop is always the item
        return List.of(new ItemStack(ModItems.SWIVEL_MOUNT.get()));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.SWIVEL_MOUNT.get());
    }
}
