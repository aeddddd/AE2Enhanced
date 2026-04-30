package com.github.aeddddd.ae2enhanced.tile;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleCraftingHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * 微型奇点的 TileEntity。
 * 300 秒（6000 ticks）后自动坍缩消失。
 * 期间对 3×3×3 范围内的生物执行稳定击杀，物品不会受影响。
 * 黑洞合成由玩家右键方块主动触发，而非自动吸入。
 */
public class TileMicroSingularity extends TileEntity implements ITickable {

    private int lifeTicks = 6000;

    private static final DamageSource SPACETIME = new DamageSource("spacetime") {
        @Override
        public ITextComponent getDeathMessage(EntityLivingBase entityLivingBaseIn) {
            return new TextComponentTranslation("death.spacetime.blackHole", entityLivingBaseIn.getDisplayName());
        }
    }.setDamageBypassesArmor();

    @Override
    public void update() {
        if (world.isRemote) {
            // 客户端：生成紫色粒子
            if (world.rand.nextInt(4) == 0) {
                double ox = pos.getX() + 0.5;
                double oy = pos.getY() + 0.5;
                double oz = pos.getZ() + 0.5;
                world.spawnParticle(EnumParticleTypes.PORTAL,
                        ox + (world.rand.nextDouble() - 0.5) * 0.8,
                        oy + (world.rand.nextDouble() - 0.5) * 0.8,
                        oz + (world.rand.nextDouble() - 0.5) * 0.8,
                        (world.rand.nextDouble() - 0.5) * 0.2,
                        (world.rand.nextDouble() - 0.5) * 0.2,
                        (world.rand.nextDouble() - 0.5) * 0.2);
            }
            return;
        }

        // 事件视界：根据配置决定是否伤害生物
        if (AE2EnhancedConfig.blackHole.damageMode != AE2EnhancedConfig.DamageMode.NONE) {
            BlockPos origin = pos;
            AxisAlignedBB horizon = new AxisAlignedBB(
                    origin.getX() - 1, origin.getY() - 1, origin.getZ() - 1,
                    origin.getX() + 2, origin.getY() + 2, origin.getZ() + 2
            );
            for (EntityLivingBase entity : world.getEntitiesWithinAABB(EntityLivingBase.class, horizon)) {
                if (!entity.isEntityAlive()) continue;

                // 非创造模式过滤
                if (AE2EnhancedConfig.blackHole.damageMode == AE2EnhancedConfig.DamageMode.NON_CREATIVE) {
                    if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isCreative()) {
                        continue;
                    }
                }

                // 混合击杀：先尝试正常伤害，失败则暴力补刀
                entity.hurtResistantTime = 0;
                entity.hurtTime = 0;
                boolean killed = false;
                if (entity.attackEntityFrom(SPACETIME, Float.MAX_VALUE)) {
                    if (!entity.isEntityAlive()) killed = true;
                }
                if (!killed) {
                    entity.setHealth(0);
                    entity.onDeath(SPACETIME);
                }
            }
        }

        // 倒计时（黑洞合成不再自动触发，改由玩家右键主动触发）
        if (--lifeTicks <= 0) {
            collapse();
        }
    }

    /**
     * 玩家右键微型奇点时调用。
     * 主动触发一次黑洞合成尝试；配方不匹配时保留物品，不会销毁。
     */
    public void activateCrafting() {
        if (world == null || world.isRemote) return;
        // 产物生成在扫描范围外（y+2），防止产物被再次吸入作为材料
        // 循环处理直到没有可匹配配方，右键一次完成所有合成
        BlackHoleCraftingHelper.tryCraftAll(world, pos, pos.add(0, 2, 0), false, 100);
    }

    private void collapse() {
        world.spawnParticle(EnumParticleTypes.EXPLOSION_HUGE,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 0, 0, 0);
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE,
                SoundCategory.BLOCKS, 2.0f, 0.5f);
        world.setBlockToAir(pos);
    }
}
