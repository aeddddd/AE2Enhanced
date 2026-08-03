package com.github.aeddddd.ae2enhanced.util;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ForceKillHelper} 单元测试.
 * <p>applyEnvironmentDamage / applyForceKill / forceSetHealthViaSyncedData 依赖 mixin
 * accessor（测试 JVM 未应用 mixin）与真实服务端实体,无法单元测试,此处只覆盖：
 * 伤害类型常量、forceSetRemoved 的调用时序,以及各反射扫描辅助方法对普通实体的无异常性.</p>
 */
class ForceKillHelperTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void vacuumDecayDamageTypeKey() {
        assertThat(ForceKillHelper.VACUUM_DECAY_DAMAGE_TYPE.location())
                .isEqualTo(new ResourceLocation("ae2enhanced", "vacuum_decay"));
        assertThat(ForceKillHelper.VACUUM_DECAY_DAMAGE_TYPE.registry())
                .isEqualTo(Registries.DAMAGE_TYPE.location());
    }

    @Test
    void forceSetRemovedFallsBackToSetRemoved() {
        // remove() 后实体仍未移除（子类覆盖 remove 的场景）→ 回退到 public final 的 setRemoved()
        Entity entity = mock(Entity.class);
        when(entity.isRemoved()).thenReturn(false);

        ForceKillHelper.forceSetRemoved(entity);

        verify(entity).remove(Entity.RemovalReason.KILLED);
        verify(entity).setRemoved(Entity.RemovalReason.KILLED);
    }

    @Test
    void forceSetRemovedSkipsSetRemovedWhenAlreadyRemoved() {
        // remove() 已生效 → 不再调用 setRemoved()
        Entity entity = mock(Entity.class);
        when(entity.isRemoved()).thenReturn(true);

        ForceKillHelper.forceSetRemoved(entity);

        verify(entity).remove(Entity.RemovalReason.KILLED);
        verify(entity, never()).setRemoved(any());
    }

    @Test
    void reflectionHelpersTolerateOrdinaryEntities() {
        // 反射扫描对不含保护开关 / 多碰撞箱 / 管理器的普通实体应为无操作且不抛异常
        LivingEntity entity = mock(LivingEntity.class);
        assertThatCode(() -> ForceKillHelper.forceBypassProtection(entity)).doesNotThrowAnyException();
        assertThatCode(() -> ForceKillHelper.removeMultipartChildren(entity)).doesNotThrowAnyException();
        assertThatCode(() -> ForceKillHelper.tryNotifyBossManager(entity)).doesNotThrowAnyException();
    }
}
