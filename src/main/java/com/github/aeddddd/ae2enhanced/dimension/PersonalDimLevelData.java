package com.github.aeddddd.ae2enhanced.dimension;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.timers.TimerQueue;

/**
 * 个人维度的世界数据：大部分属性委托给主世界数据（游戏时间、游戏规则、难度等）,
 * 但昼夜时间与天气为每个维度独立字段,使时间/天气锁定规则不会污染主世界.
 */
public class PersonalDimLevelData implements ServerLevelData {

    private final ServerLevelData wrapped;

    private long dayTime;
    private int clearWeatherTime;
    private int rainTime;
    private int thunderTime;
    private boolean raining;
    private boolean thundering;
    private boolean initialized = true;

    public PersonalDimLevelData(ServerLevelData wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public String getLevelName() {
        return wrapped.getLevelName();
    }

    @Override
    public int getXSpawn() {
        return wrapped.getXSpawn();
    }

    @Override
    public int getYSpawn() {
        return wrapped.getYSpawn();
    }

    @Override
    public int getZSpawn() {
        return wrapped.getZSpawn();
    }

    @Override
    public float getSpawnAngle() {
        return wrapped.getSpawnAngle();
    }

    @Override
    public void setSpawn(BlockPos spawnPoint, float angle) {
        // 个人维度不支持设置出生点
    }

    @Override
    public void setXSpawn(int x) {
    }

    @Override
    public void setYSpawn(int y) {
    }

    @Override
    public void setZSpawn(int z) {
    }

    @Override
    public void setSpawnAngle(float angle) {
    }

    @Override
    public long getGameTime() {
        return wrapped.getGameTime();
    }

    @Override
    public void setGameTime(long time) {
        // 与原版 DerivedLevelData 一致：写操作为空,gameTime 只读共享主世界,
        // 否则每个个人维度 tick 都会让主世界 gameTime 额外 +1
    }

    @Override
    public long getDayTime() {
        return dayTime;
    }

    @Override
    public void setDayTime(long time) {
        dayTime = time;
    }

    @Override
    public boolean isThundering() {
        return thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        this.thundering = thundering;
    }

    @Override
    public boolean isRaining() {
        return raining;
    }

    @Override
    public void setRaining(boolean isRaining) {
        raining = isRaining;
    }

    @Override
    public int getRainTime() {
        return rainTime;
    }

    @Override
    public void setRainTime(int time) {
        rainTime = time;
    }

    @Override
    public int getThunderTime() {
        return thunderTime;
    }

    @Override
    public void setThunderTime(int time) {
        thunderTime = time;
    }

    @Override
    public int getClearWeatherTime() {
        return clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int time) {
        clearWeatherTime = time;
    }

    @Override
    public boolean isHardcore() {
        return wrapped.isHardcore();
    }

    @Override
    public GameRules getGameRules() {
        return wrapped.getGameRules();
    }

    @Override
    public Difficulty getDifficulty() {
        return wrapped.getDifficulty();
    }

    @Override
    public boolean isDifficultyLocked() {
        return wrapped.isDifficultyLocked();
    }

    @Override
    public GameType getGameType() {
        return wrapped.getGameType();
    }

    @Override
    public void setGameType(GameType gameType) {
        // 个人维度不支持单独修改游戏模式
    }

    @Override
    public boolean getAllowCommands() {
        return wrapped.getAllowCommands();
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    @Override
    public WorldBorder.Settings getWorldBorder() {
        return wrapped.getWorldBorder();
    }

    @Override
    public void setWorldBorder(WorldBorder.Settings settings) {
        // 个人维度边界跟随主世界
    }

    @Override
    public TimerQueue<MinecraftServer> getScheduledEvents() {
        // 与原版 DerivedLevelData 一致,委托给主世界数据
        return wrapped.getScheduledEvents();
    }

    @Override
    public int getWanderingTraderSpawnDelay() {
        return 0;
    }

    @Override
    public void setWanderingTraderSpawnDelay(int delay) {
    }

    @Override
    public int getWanderingTraderSpawnChance() {
        return 0;
    }

    @Override
    public void setWanderingTraderSpawnChance(int chance) {
    }

    @Override
    @Nullable
    public UUID getWanderingTraderId() {
        return null;
    }

    @Override
    public void setWanderingTraderId(UUID id) {
    }
}
