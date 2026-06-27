package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.DimensionType;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.biome.BiomeProviderSingle;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.IChunkGenerator;

import javax.annotation.Nullable;

/**
 * 个人维度 WorldProvider。
 */
public class WorldProviderPersonalDim extends WorldProvider {

    @Override
    protected void init() {
        this.hasSkyLight = true;
        this.biomeProvider = new BiomeProviderSingle(Biomes.PLAINS);
        super.init();
    }

    @Override
    public DimensionType getDimensionType() {
        // 与 PersonalWorlds 保持一致：返回 OVERWORLD 以避免自定义 DimensionType
        // 在客户端/服务端生命周期不同阶段出现 null 或兼容性异常。
        // 维度身份判定以 DimensionManager 中注册的 DimensionType 为准，
        // WorldProvider 返回什么类型不影响 isPersonalDimension 的判断。
        return DimensionType.OVERWORLD;
    }

    @Override
    public IChunkGenerator createChunkGenerator() {
        return new ChunkGeneratorPersonalDim(world);
    }

    @Override
    public boolean canRespawnHere() {
        return false;
    }

    @Override
    public int getRespawnDimension(EntityPlayerMP player) {
        return 0;
    }

    @Override
    public BlockPos getSpawnPoint() {
        return new BlockPos(0, AE2EnhancedConfig.personalDimension.entryY, 0);
    }

    @Override
    public BlockPos getRandomizedSpawnPoint() {
        return getSpawnPoint();
    }

    @Override
    public boolean isSurfaceWorld() {
        return true;
    }

    @Override
    public boolean doesWaterVaporize() {
        return false;
    }

    @Override
    public float getCloudHeight() {
        return 192.0F;
    }

    @Override
    public double getHorizon() {
        return AE2EnhancedConfig.personalDimension.floorY;
    }

    @Override
    public int getAverageGroundLevel() {
        return AE2EnhancedConfig.personalDimension.floorY;
    }

    @Nullable
    @Override
    public String getSaveFolder() {
        return "AE2E_PersonalDim_" + getDimension();
    }

    @Nullable
    private PersonalDimensionRules getRules() {
        if (this.world == null) return null;
        PlayerDimEntry entry = PersonalDimensionManager.getEntryByDimension(getDimension());
        return entry != null ? entry.rules : null;
    }

    @Override
    public long getWorldTime() {
        PersonalDimensionRules rules = getRules();
        if (rules != null && rules.lockTime) {
            return rules.timeValue;
        }
        return super.getWorldTime();
    }

    @Override
    public void setWorldTime(long time) {
        PersonalDimensionRules rules = getRules();
        if (rules != null && (rules.lockTime || !rules.daylightCycle)) {
            // 锁定时间或不开启日夜循环时，忽略客户端/服务端的自动推进
            return;
        }
        super.setWorldTime(time);
    }

    @Override
    public boolean isDaytime() {
        PersonalDimensionRules rules = getRules();
        if (rules != null && rules.lockTime) {
            long time = rules.timeValue % 24000L;
            return time >= 0 && time < 12000L;
        }
        return super.isDaytime();
    }

    @Override
    public boolean canDoRainSnowIce(Chunk chunk) {
        PersonalDimensionRules rules = getRules();
        if (rules != null && rules.lockWeather) {
            return false;
        }
        return super.canDoRainSnowIce(chunk);
    }

    @Override
    public boolean canDoLightning(Chunk chunk) {
        PersonalDimensionRules rules = getRules();
        if (rules != null && rules.lockWeather) {
            return false;
        }
        return super.canDoLightning(chunk);
    }
}
