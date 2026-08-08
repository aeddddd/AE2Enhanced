package com.github.aeddddd.ae2enhanced.test.computation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.blockentity.ComputationCoreBlockEntity;
import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPU;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link VirtualCraftingCPU} 单元测试:各访问/状态方法对内部集群与接口节点的委托.
 * <p>构造器(并行度按 16 线程拆分、容量只计首个单元)无法离线覆盖,原因有二:
 * <ol>
 * <li>构造器通过 mixin invoker 调用 CraftingCPUCluster 包级私有方法 addBlockEntity,
 * 单元测试 JVM 不应用 mixin;Mockito mockConstruction 基于 JVMTI 类重定义,
 * 无法为重定义类追加 invoker 接口,该强转必然 ClassCastException(且未被捕获).</li>
 * <li>构造器内引用 AEBlocks.CRAFTING_UNIT,其静态初始化依赖 AE2 access transformer,
 * 纯 JUnit 环境未应用,必然 IllegalAccessError.</li>
 * </ol>
 * 因此这里用 {@code CALLS_REAL_METHODS} mock 绕过构造器、反射注入字段,
 * 验证所有非构造行为的委托语义.</p>
 */
class VirtualCraftingCPUTest {

    static {
        MinecraftTestBootstrap.bootstrap();
    }

    /**
     * 构造一个绕过构造器的 {@link VirtualCraftingCPU} 实例,
     * 字段按构造器语义注入,方法走真实实现.
     */
    private static VirtualCraftingCPU newCpu(ComputationCoreBlockEntity host, IManagedGridNode interfaceNode,
            CraftingCPUCluster cluster) {
        var cpu = mock(VirtualCraftingCPU.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        setField(cpu, "host", host);
        setField(cpu, "interfaceNode", interfaceNode);
        setField(cpu, "cluster", cluster);
        return cpu;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = VirtualCraftingCPU.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法注入字段: " + name, e);
        }
    }

    /** getCluster / getHost 原样返回内部字段. */
    @Test
    void testFieldAccessors() {
        ComputationCoreBlockEntity host = mock(ComputationCoreBlockEntity.class);
        CraftingCPUCluster cluster = mock(CraftingCPUCluster.class);

        var cpu = newCpu(host, mock(IManagedGridNode.class), cluster);

        assertThat(cpu.getCluster()).isSameAs(cluster);
        assertThat(cpu.getHost()).isSameAs(host);
    }

    /** getGrid 委托给接口节点. */
    @Test
    void testGetGridDelegatesToInterfaceNode() {
        IManagedGridNode interfaceNode = mock(IManagedGridNode.class);
        IGrid grid = mock(IGrid.class);
        when(interfaceNode.getGrid()).thenReturn(grid);

        var cpu = newCpu(mock(ComputationCoreBlockEntity.class), interfaceNode,
                mock(CraftingCPUCluster.class));

        assertThat(cpu.getGrid()).isSameAs(grid);
        verify(interfaceNode).getGrid();
    }

    /** isActive 委托给接口节点. */
    @Test
    void testIsActiveDelegatesToInterfaceNode() {
        IManagedGridNode interfaceNode = mock(IManagedGridNode.class);
        when(interfaceNode.isActive()).thenReturn(true);

        var cpu = newCpu(mock(ComputationCoreBlockEntity.class), interfaceNode,
                mock(CraftingCPUCluster.class));

        assertThat(cpu.isActive()).isTrue();
        verify(interfaceNode).isActive();
    }

    /** isDestroyed / isBusy 委托给内部集群. */
    @Test
    void testClusterStateDelegatesToCluster() {
        CraftingCPUCluster cluster = mock(CraftingCPUCluster.class);
        when(cluster.isDestroyed()).thenReturn(true);
        when(cluster.isBusy()).thenReturn(true);

        var cpu = newCpu(mock(ComputationCoreBlockEntity.class), mock(IManagedGridNode.class), cluster);

        assertThat(cpu.isDestroyed()).isTrue();
        assertThat(cpu.isBusy()).isTrue();
    }

    /** destroy 转发为集群的 destroy. */
    @Test
    void testDestroyDelegatesToCluster() {
        CraftingCPUCluster cluster = mock(CraftingCPUCluster.class);

        var cpu = newCpu(mock(ComputationCoreBlockEntity.class), mock(IManagedGridNode.class), cluster);
        cpu.destroy();

        verify(cluster).destroy();
    }

    /** hasStoredItems 反映集群合成库存是否为空(池回收/解绑销毁前的防丢守卫). */
    @Test
    void testHasStoredItemsFollowsClusterInventory() {
        CraftingCPUCluster cluster = mock(CraftingCPUCluster.class);
        // mock 不初始化字段,反射注入真实 CraftingCpuLogic(构造器仅赋值,离线安全)
        var logic = new appeng.crafting.execution.CraftingCpuLogic(cluster);
        try {
            Field field = CraftingCPUCluster.class.getField("craftingLogic");
            field.setAccessible(true);
            field.set(cluster, logic);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法注入 craftingLogic 字段", e);
        }

        var cpu = newCpu(mock(ComputationCoreBlockEntity.class), mock(IManagedGridNode.class), cluster);
        assertThat(cpu.hasStoredItems()).isFalse();

        // 直接操作 KeyCounter,避免触发 inventory 的 postChange 监听器
        logic.getInventory().list.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STONE), 3);
        assertThat(cpu.hasStoredItems()).isTrue();
    }
}
