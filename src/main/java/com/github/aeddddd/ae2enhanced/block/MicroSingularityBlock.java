package com.github.aeddddd.ae2enhanced.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.github.aeddddd.ae2enhanced.blockentity.MicroSingularityBlockEntity;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityFuelRecipe;
import com.github.aeddddd.ae2enhanced.item.MicroSingularityItem;
import com.github.aeddddd.ae2enhanced.item.SingularityConstrictorItem;

/**
 * 微型奇点 — 仪式召唤的临时黑洞方块.
 * 不可破坏、发光、无碰撞.
 * 右键：触发一次黑洞合成；手持燃料物品右键：延长存在时间或使其永久；
 * 手持奇点约束器右键：将其约束为物品形态（扔出后可恢复）.
 */
public class MicroSingularityBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = Block.box(4.0, 4.0, 4.0, 12.0, 12.0, 12.0);

    public MicroSingularityBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide()
                && level.getBlockEntity(pos) instanceof MicroSingularityBlockEntity microSingularity) {
            ItemStack held = player.getItemInHand(hand);
            if (held.getItem() instanceof SingularityConstrictorItem) {
                convertToItem(level, pos, player, held, microSingularity);
            } else {
                SingularityFuelRecipe fuel = SingularityFuelRecipe.findFor(level, held);
                if (fuel != null) {
                    feedFuel(level, pos, player, held, microSingularity, fuel);
                } else {
                    microSingularity.activateCrafting();
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 奇点约束器右键：移除方块,约束器转化为携带剩余寿命与永久标记的奇点物品.
     */
    private static void convertToItem(Level level, BlockPos pos, Player player, ItemStack constrictor,
            MicroSingularityBlockEntity microSingularity) {
        ItemStack singularityStack = MicroSingularityItem.createStack(
                microSingularity.getLifetimeTicks(), microSingularity.isPermanent());
        if (!player.isCreative()) {
            constrictor.shrink(1);
        }
        level.removeBlock(pos, false);
        if (!player.getInventory().add(singularityStack)) {
            player.drop(singularityStack, false);
        }
        level.playSound(null, pos, SoundEvents.ENDER_EYE_DEATH, SoundSource.BLOCKS, 1.0f, 0.6f);
    }

    /**
     * 喂入燃料：延长存在时间,或使奇点永久存在.
     */
    private static void feedFuel(Level level, BlockPos pos, Player player, ItemStack held,
            MicroSingularityBlockEntity microSingularity, SingularityFuelRecipe fuel) {
        // 已永久存在的奇点无需再喂燃料,避免白白消耗
        if (microSingularity.isPermanent()) {
            return;
        }
        if (!player.isCreative()) {
            held.shrink(1);
        }
        if (fuel.isPermanent()) {
            microSingularity.setPermanent(true);
        } else {
            microSingularity.addLifetimeTicks(fuel.getTicks());
        }
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.2, 0.2, 0.2, 0.02);
        }
        level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.8f, 1.5f);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MicroSingularityBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null : (lvl, pos, st, be) -> {
            if (be instanceof MicroSingularityBlockEntity microSingularity) {
                MicroSingularityBlockEntity.tick(lvl, pos, st, microSingularity);
            }
        };
    }
}
