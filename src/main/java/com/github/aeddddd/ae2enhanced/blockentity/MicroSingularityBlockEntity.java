package com.github.aeddddd.ae2enhanced.blockentity;

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
import com.github.aeddddd.ae2enhanced.util.ForceKillHelper;

/**
 * 微型奇点的方块实体.
 * 默认 300 秒（6000 ticks）后自动坍缩消失；喂入燃料可追加存在时间,
 * 当剩余存在时间超过 {@link Integer#MAX_VALUE} tick 时,奇点转变为永久存在,不再倒计时.
 * 期间对 3×3×3 范围内的生物执行稳定击杀.
 * 周期性吸入附近可参与黑洞合成的物品实体,并并行完成所有匹配配方；
 * 玩家右键方块也可主动触发一次合成.
 */
public class MicroSingularityBlockEntity extends BlockEntity {

    public static final int DEFAULT_LIFE_TICKS = 6000;
    private static final String NBT_LIFE_TICKS = "LifeTicks";
    private static final String NBT_PERMANENT = "Permanent";
    private static final int HORIZON_RADIUS = 1;
    /** 自动吸入/合成节流间隔（tick） */
    private static final int AUTO_CRAFT_INTERVAL = 10;

    private int lifeTicks = DEFAULT_LIFE_TICKS;
    private boolean permanent = false;

    public MicroSingularityBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MICRO_SINGULARITY.get(), pos, state);
    }

    public void setLifetimeTicks(int ticks) {
        this.lifeTicks = ticks > 0 ? ticks : DEFAULT_LIFE_TICKS;
        setChanged();
    }

    public int getLifetimeTicks() {
        return lifeTicks;
    }

    /**
     * 追加存在时间（燃料喂入）.
     * 追加后剩余时间超过 {@link Integer#MAX_VALUE} tick 时,奇点转变为永久存在.
     */
    public void addLifetimeTicks(int ticks) {
        if (this.permanent) {
            return;
        }
        long total = (long) this.lifeTicks + Math.max(0, ticks);
        if (total > Integer.MAX_VALUE) {
            setPermanent(true);
            onBecomePermanent();
        } else {
            this.lifeTicks = (int) total;
            setChanged();
        }
    }

    /** 剩余时间溢出转为永久存在时的反馈：音效 + 粒子. */
    private void onBecomePermanent() {
        if (level == null || level.isClientSide()) {
            return;
        }
        Vec3 center = Vec3.atCenterOf(worldPosition);
        ((ServerLevel) level).sendParticles(
                net.minecraft.core.particles.ParticleTypes.END_ROD,
                center.x, center.y, center.z, 64, 1.0, 1.0, 1.0, 0.05);
        level.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.BLOCKS, 1.5f, 1.0f);
    }

    public boolean isPermanent() {
        return permanent;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
        setChanged();
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
            DamageSource vacuumDecay = ForceKillHelper.vacuumDecay(level);
            for (LivingEntity living : entities) {
                if (!living.isAlive()) {
                    continue;
                }
                if (AE2EnhancedConfig.COMMON.blackHoleDamageMode.get() == AE2EnhancedConfig.BlackHoleDamageMode.NON_CREATIVE) {
                    if (living instanceof Player player && player.isCreative()) {
                        continue;
                    }
                }
                // 真空衰变环境强杀：玩家与非玩家分策略,受保护实体也可被彻底移除
                ForceKillHelper.applyEnvironmentDamage(living, vacuumDecay, Float.MAX_VALUE);
            }
        }

        // 自动吸入与并行合成（节流）
        if (level.getGameTime() % AUTO_CRAFT_INTERVAL == 0) {
            BlackHoleCraftingHelper.suckMatchingItems(level, pos);
            BlackHoleCraftingHelper.craftAllAvailable(level, pos, pos.above(2));
        }

        // 倒计时（永久奇点不坍缩）
        if (!entity.permanent && --entity.lifeTicks <= 0) {
            entity.collapse();
        }
    }

    /**
     * 玩家右键微型奇点时调用：主动触发一次并行黑洞合成.
     */
    public void activateCrafting() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlackHoleCraftingHelper.craftAllAvailable(level, worldPosition, worldPosition.above(2));
    }

    @Override
    public AABB getRenderBoundingBox() {
        // 吸积盘半径约 1 格,超出方块包围盒,需扩大避免视锥裁剪
        return new AABB(worldPosition).inflate(2.0);
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
        this.permanent = tag.getBoolean(NBT_PERMANENT);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(NBT_LIFE_TICKS, this.lifeTicks);
        tag.putBoolean(NBT_PERMANENT, this.permanent);
    }
}
