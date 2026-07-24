package com.github.aeddddd.ae2enhanced.mixin.accessor;

import java.util.Map;
import java.util.concurrent.Executor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 访问 {@link MinecraftServer} 的私有成员,用于运行时动态创建个人维度.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {

    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> getLevels();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess getStorageSource();

    @Accessor("executor")
    Executor getExecutor();
}
