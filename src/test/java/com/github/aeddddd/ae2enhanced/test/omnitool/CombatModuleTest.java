package com.github.aeddddd.ae2enhanced.test.omnitool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.module.CombatModule;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link CombatModule} 可在纯单测环境覆盖的部分:禁疗追踪与非生物目标分支.
 */
class CombatModuleTest {

    @BeforeAll
    static void bootstrap() {
        OmniToolTestSupport.bootstrap();
    }

    private static ItemStack newToolStack() {
        return OmniToolTestSupport.newToolStack();
    }

    @Test
    void testAntiHealLifecycle() {
        LivingEntity entity = mock(LivingEntity.class);
        CompoundTag data = new CompoundTag();
        when(entity.getPersistentData()).thenReturn(data);

        assertThat(CombatModule.hasAntiHeal(entity)).isFalse();

        CombatModule.applyAntiHeal(entity);
        assertThat(CombatModule.hasAntiHeal(entity)).isTrue();
        // 施加禁疗后实体进入追踪集合
        assertThat(CombatModule.getAntiHealTracked()).contains(entity);

        CombatModule.clearAntiHeal(entity);
        assertThat(CombatModule.hasAntiHeal(entity)).isFalse();
        assertThat(CombatModule.getAntiHealTracked()).doesNotContain(entity);
    }

    @Test
    void testTrackAntiHealRegistersEntity() {
        LivingEntity entity = mock(LivingEntity.class);
        when(entity.getPersistentData()).thenReturn(new CompoundTag());

        CombatModule.trackAntiHeal(entity);
        assertThat(CombatModule.getAntiHealTracked()).contains(entity);

        CombatModule.clearAntiHeal(entity);
        assertThat(CombatModule.getAntiHealTracked()).doesNotContain(entity);
    }

    @Test
    void testLeftClickNonLivingEntityIsNotHandled() {
        // 目标既不是守护水晶也不是生物:不接管攻击逻辑
        CombatModule module = new CombatModule();
        Entity entity = mock(Entity.class);
        Player player = mock(Player.class);

        assertThat(module.onLeftClickEntity(newToolStack(), player, entity)).isFalse();
    }
}
