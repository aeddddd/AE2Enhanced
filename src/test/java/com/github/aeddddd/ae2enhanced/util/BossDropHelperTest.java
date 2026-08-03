package com.github.aeddddd.ae2enhanced.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BossDropHelper} 单元测试.
 * <p>掉落方法反射调用路径需要一个类名含 boss 提示词的真实 LivingEntity 实例
 * （构造原版实体需要完整服务端运行时）,无法单元测试；此处覆盖三个提前返回分支.</p>
 */
class BossDropHelperTest {

    @BeforeAll
    static void bootstrap() {
        // mock LivingEntity 会触发实体类静态初始化,必须先完成原版引导
        MinecraftTestBootstrap.bootstrap();
    }

    private static LivingEntity deadEntity(Level level) {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.level()).thenReturn(level);
        when(entity.isAlive()).thenReturn(false);
        return entity;
    }

    @Test
    void doesNothingOnClientSide() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        LivingEntity entity = deadEntity(level);

        BossDropHelper.trySpawnBossDrops(entity, null, null, 0);

        verify(level, never()).addFreshEntity(any(Entity.class));
    }

    @Test
    void doesNothingWhenEntityStillAlive() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        LivingEntity entity = deadEntity(level);
        when(entity.isAlive()).thenReturn(true);

        BossDropHelper.trySpawnBossDrops(entity, null, null, 0);

        verify(level, never()).addFreshEntity(any(Entity.class));
    }

    @Test
    void doesNothingForNonBossEntity() {
        // mock 类名不含 gaia/boss/guardian 等提示词,isBossLike 判定为 false
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        LivingEntity entity = deadEntity(level);

        BossDropHelper.trySpawnBossDrops(entity, null, null, 0);

        verify(level, never()).addFreshEntity(any(Entity.class));
    }
}
