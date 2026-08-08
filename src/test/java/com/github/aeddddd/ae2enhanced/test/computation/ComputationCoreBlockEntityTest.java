package com.github.aeddddd.ae2enhanced.test.computation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPU;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link ComputationCoreBlockEntity} 虚拟 CPU 池管理的单元测试.
 * <p>重点覆盖空闲 CPU 回收（managePool）与解绑销毁（unbindVirtualCpu）时的库存
 * 防丢守卫：任务在网络回流调用栈内完成时 storeItems 会被重入保护静默拒绝,
 * 带残余销毁集群会让材料随集群对象永久丢失.</p>
 * <p>私有方法经反射调用,CPU 池经反射注入（与 {@link VirtualCraftingCPUTest} 相同的
 * 绕过构造器思路）.</p>
 */
class ComputationCoreBlockEntityTest {

    static {
        MinecraftTestBootstrap.bootstrap();
    }

    private static ComputationCoreBlockEntity newEntityWithPool(List<VirtualCraftingCPU> pool) {
        var entity = mock(ComputationCoreBlockEntity.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        try {
            Field field = ComputationCoreBlockEntity.class.getDeclaredField("cpuPool");
            field.setAccessible(true);
            field.set(entity, pool);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法注入 cpuPool 字段", e);
        }
        return entity;
    }

    private static void invokePrivate(ComputationCoreBlockEntity entity, String name) {
        try {
            Method method = ComputationCoreBlockEntity.class.getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(entity);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法调用方法: " + name, e);
        }
    }

    /** mock 集群并注入 mock craftingLogic（mock 不初始化 public final 字段,需反射）. */
    private static CraftingCPUCluster mockClusterWithLogic(CraftingCpuLogic logic) {
        CraftingCPUCluster cluster = mock(CraftingCPUCluster.class);
        try {
            Field field = CraftingCPUCluster.class.getField("craftingLogic");
            field.setAccessible(true);
            field.set(cluster, logic);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法注入 craftingLogic 字段", e);
        }
        return cluster;
    }

    private static VirtualCraftingCPU mockIdleCpu(boolean storedItems) {
        var cpu = mock(VirtualCraftingCPU.class);
        when(cpu.isBusy()).thenReturn(false);
        when(cpu.hasStoredItems()).thenReturn(storedItems);
        when(cpu.getCluster()).thenReturn(mock(CraftingCPUCluster.class));
        return cpu;
    }

    /** 池回收跳过库存非空的空闲 CPU：残余材料需等 tickCraftingLogic 重试归还后再回收. */
    @Test
    void testManagePoolKeepsIdleCpuWithStoredItems() {
        VirtualCraftingCPU withLeftovers = mockIdleCpu(true);
        VirtualCraftingCPU empty = mockIdleCpu(false);
        List<VirtualCraftingCPU> pool = new ArrayList<>(List.of(withLeftovers, empty));
        var entity = newEntityWithPool(pool);

        invokePrivate(entity, "managePool");

        // 空库存的被回收销毁,带残余的保留在池中等待归还
        assertThat(pool).containsExactly(withLeftovers);
        verify(empty).destroy();
        verify(withLeftovers, never()).destroy();
    }

    /** 池回收在仅剩 1 个空闲 CPU 时停止（原有语义不变）. */
    @Test
    void testManagePoolKeepsOneIdleCpu() {
        VirtualCraftingCPU first = mockIdleCpu(false);
        VirtualCraftingCPU second = mockIdleCpu(false);
        List<VirtualCraftingCPU> pool = new ArrayList<>(List.of(first, second));
        var entity = newEntityWithPool(pool);

        invokePrivate(entity, "managePool");

        assertThat(pool).hasSize(1);
    }

    /** 解绑销毁空闲 CPU 前先尽力归还库存（storeItems）. */
    @Test
    void testUnbindStoresItemsBeforeDestroy() {
        var logic = mock(CraftingCpuLogic.class);
        var cpu = mock(VirtualCraftingCPU.class);
        when(cpu.isBusy()).thenReturn(false);
        when(cpu.getCluster()).thenReturn(mockClusterWithLogic(logic));
        List<VirtualCraftingCPU> pool = new ArrayList<>(List.of(cpu));
        var entity = newEntityWithPool(pool);

        invokePrivate(entity, "unbindVirtualCpu");

        verify(logic).storeItems();
        verify(cpu).destroy();
        assertThat(pool).isEmpty();
    }

    /** 忙碌 CPU 解绑时不调用 storeItems（原生对 job != null 有状态检查）,但仍销毁. */
    @Test
    void testUnbindSkipsStoreItemsForBusyCpu() {
        var logic = mock(CraftingCpuLogic.class);
        var cpu = mock(VirtualCraftingCPU.class);
        when(cpu.isBusy()).thenReturn(true);
        when(cpu.getCluster()).thenReturn(mockClusterWithLogic(logic));
        List<VirtualCraftingCPU> pool = new ArrayList<>(List.of(cpu));
        var entity = newEntityWithPool(pool);

        invokePrivate(entity, "unbindVirtualCpu");

        verify(logic, never()).storeItems();
        verify(cpu).destroy();
    }
}
