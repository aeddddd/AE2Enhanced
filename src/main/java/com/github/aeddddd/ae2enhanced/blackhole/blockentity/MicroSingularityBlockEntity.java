package com.github.aeddddd.ae2enhanced.blackhole.blockentity;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.crafting.blackhole.BlackHoleCraftingHelper;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;

/**
 * 微型奇点的方块实体。
 * 默认 300 秒（6000 ticks）后自动坍缩消失。
 * 期间对 3×3×3 范围内的生物执行稳定击杀。
 * 黑洞合成由玩家右键方块主动触发。
 */
public class MicroSingularityBlockEntity extends BlockEntity {

    public static final int DEFAULT_LIFE_TICKS = 6000;
    private static final String NBT_LIFE_TICKS = "LifeTicks";
    private static final int HORIZON_RADIUS = 1;

    private int lifeTicks = DEFAULT_LIFE_TICKS;

    public MicroSingularityBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MICRO_SINGULARITY.get(), pos, state);
    }

    public void setLifetimeTicks(int ticks) {
        this.lifeTicks = ticks > 0 ? ticks : DEFAULT_LIFE_TICKS;
    }

    public int getLifetimeTicks() {
        return lifeTicks;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MicroSingularityBlockEntity entity) {
        if (level.isClientSide()) {
            return;
        }

        // 事件视界：根据配置决定是否伤害生物
        if (AE2EnhancedConfig.COMMON.blackHoleDamageMode.get() != AE2EnhancedConfig.BlackHoleDamageMode.NONE) {
            AABB horizon = new AABB(
                    pos.getX() - HORIZON_RADIUS, pos.getY() - HORIZON_RADIUS, pos.getZ() - HORIZON_RADIUS,
                    pos.getX() + HORIZON_RADIUS + 1, pos.getY() + HORIZON_RADIUS + 1, pos.getZ() + HORIZON_RADIUS + 1);
            List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, horizon);
            DamageSource spacetime = level.damageSources().generic();
            for (LivingEntity living : entities) {
                if (!living.isAlive()) {
                    continue;
                }
                if (AE2EnhancedConfig.COMMON.blackHoleDamageMode.get() == AE2EnhancedConfig.BlackHoleDamageMode.NON_CREATIVE) {
                    if (living instanceof Player player && player.isCreative()) {
                        continue;
                    }
                }
                // 强制击杀：造成极大伤害
                living.hurt(spacetime, Float.MAX_VALUE);
            }
        }

        // 倒计时
        if (--entity.lifeTicks <= 0) {
            entity.collapse();
        }
    }

    /**
     * 玩家右键微型奇点时调用：主动触发黑洞合成。
     */
    public void activateCrafting() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlackHoleCraftingHelper.tryCraftAll(level, worldPosition, worldPosition.above(2), false, 100);
    }

    private void collapse() {
        if (level == null || level.isClientSide()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(worldPosition);
        ((ServerLevel) level).sendParticles(
                net.minecraft.core.particles.ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 2.0f, 0.5f);
        level.removeBlock(worldPosition, false);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.lifeTicks = tag.contains(NBT_LIFE_TICKS) ? tag.getInt(NBT_LIFE_TICKS) : DEFAULT_LIFE_TICKS;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(NBT_LIFE_TICKS, this.lifeTicks);
    }
}
