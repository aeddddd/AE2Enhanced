package com.github.aeddddd.ae2enhanced.test.omnitool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.ConformalChargeHandler;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ConformalChargeHandler} 掉落物保护逻辑测试.
 */
class ConformalChargeHandlerTest {

    @BeforeAll
    static void bootstrap() {
        OmniToolTestSupport.bootstrap();
    }

    private static ItemStack newToolStack() {
        return OmniToolTestSupport.newToolStack();
    }

    private static ItemEntity mockItemEntity(ItemStack stack, CompoundTag persistentData) {
        ItemEntity entity = mock(ItemEntity.class);
        when(entity.getItem()).thenReturn(stack);
        when(entity.getPersistentData()).thenReturn(persistentData);
        return entity;
    }

    @Test
    void testNoUpgradeDoesNothing() {
        ItemEntity entity = mockItemEntity(newToolStack(), new CompoundTag());
        assertThat(ConformalChargeHandler.onEntityItemUpdate(entity)).isFalse();
        verify(entity, never()).setInvulnerable(true);
        verify(entity, never()).setUnlimitedLifetime();
        verify(entity, never()).clearFire();
        verify(entity, never()).setNoPickUpDelay();
    }

    @Test
    void testUpgradeProtectsEntity() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setConformalCharge(stack, true);
        ItemEntity entity = mockItemEntity(stack, new CompoundTag());

        assertThat(ConformalChargeHandler.onEntityItemUpdate(entity)).isFalse();
        // 首次 tick:初始化无敌 + 无限寿命,并灭火 + 清除拾取延迟
        verify(entity).setInvulnerable(true);
        verify(entity).setUnlimitedLifetime();
        verify(entity).clearFire();
        verify(entity).setNoPickUpDelay();
    }

    @Test
    void testInitializationRunsOnlyOnce() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setConformalCharge(stack, true);
        ItemEntity entity = mockItemEntity(stack, new CompoundTag());

        ConformalChargeHandler.onEntityItemUpdate(entity);
        ConformalChargeHandler.onEntityItemUpdate(entity);
        ConformalChargeHandler.onEntityItemUpdate(entity);

        // 无敌与无限寿命只在首个 tick 设置一次
        verify(entity, times(1)).setInvulnerable(true);
        verify(entity, times(1)).setUnlimitedLifetime();
        // 灭火与清除拾取延迟每个 tick 都执行
        verify(entity, times(3)).clearFire();
        verify(entity, times(3)).setNoPickUpDelay();
    }
}
