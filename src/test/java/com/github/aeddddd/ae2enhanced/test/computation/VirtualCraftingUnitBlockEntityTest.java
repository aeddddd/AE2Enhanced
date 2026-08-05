package com.github.aeddddd.ae2enhanced.test.computation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;

import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingUnitBlockEntity;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link VirtualCraftingUnitBlockEntity} 单元测试:容量/线程数覆盖与 actionable node 转发.
 * <p>单元测试 JVM 未应用 AE2 的 access transformer,AEBlocks/AEBlockEntities 静态初始化
 * 会抛出 IllegalAccessError(AE2 代码访问原版私有方法),无法走真实构造器.
 * 因此用 Mockito {@code CALLS_REAL_METHODS} mock 绕过构造器(不触发任何 super 调用),
 * 反射注入三个字段后仍调用真实的方法实现.</p>
 */
class VirtualCraftingUnitBlockEntityTest {

    static {
        MinecraftTestBootstrap.bootstrap();
    }

    /**
     * 构造一个绕过构造器的 {@link VirtualCraftingUnitBlockEntity} 实例,
     * 字段按构造器语义注入,方法走真实实现.
     */
    private static VirtualCraftingUnitBlockEntity newUnit(IManagedGridNode interfaceNode, int parallel,
            long storageBytes) {
        var unit = mock(VirtualCraftingUnitBlockEntity.class, withSettings()
                .defaultAnswer(CALLS_REAL_METHODS));
        setField(unit, "interfaceNode", interfaceNode);
        setField(unit, "parallel", parallel);
        setField(unit, "storageBytes", storageBytes);
        return unit;
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = VirtualCraftingUnitBlockEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法注入字段: " + name, e);
        }
    }

    /**
     * 存储容量原样返回注入值;父类实现会读取世界中的方块类型(level 为 null 时回退 0),
     * 覆盖方法必须完全不依赖世界.多单元场景中非首个单元传 0,防止集群存储累加溢出.
     */
    @Test
    void testStorageBytesOverridesWorldLookup() {
        assertThat(newUnit(mock(IManagedGridNode.class), 1, 1024).getStorageBytes()).isEqualTo(1024);
        assertThat(newUnit(mock(IManagedGridNode.class), 1, 0).getStorageBytes()).isZero();
        // 默认(首个单元)语义:无限容量
        assertThat(newUnit(mock(IManagedGridNode.class), 1, Long.MAX_VALUE).getStorageBytes())
                .isEqualTo(Long.MAX_VALUE);
    }

    /** 加速器线程数原样返回注入值,不读取世界中的方块类型. */
    @Test
    void testAcceleratorThreadsOverridesWorldLookup() {
        assertThat(newUnit(mock(IManagedGridNode.class), 7, 0).getAcceleratorThreads()).isEqualTo(7);
        assertThat(newUnit(mock(IManagedGridNode.class), 16, 0).getAcceleratorThreads()).isEqualTo(16);
    }

    /** getActionableNode 转发到通用 ME 接口节点(对象身份一致). */
    @Test
    void testActionableNodeDelegatesToInterfaceNode() {
        IManagedGridNode interfaceNode = mock(IManagedGridNode.class);
        IGridNode node = mock(IGridNode.class);
        when(interfaceNode.getNode()).thenReturn(node);

        assertThat(newUnit(interfaceNode, 1, 0).getActionableNode()).isSameAs(node);
    }

    /** 接口节点尚未创建网格节点时,getActionableNode 返回 null(与接口节点行为一致). */
    @Test
    void testActionableNodeNullWhenInterfaceNodeNotCreated() {
        IManagedGridNode interfaceNode = mock(IManagedGridNode.class);
        when(interfaceNode.getNode()).thenReturn(null);

        assertThat(newUnit(interfaceNode, 1, 0).getActionableNode()).isNull();
    }

    /** breakCluster 为空实现:不抛异常,不与接口节点交互(虚拟方块不在真实世界,无掉落). */
    @Test
    void testBreakClusterIsNoOp() {
        IManagedGridNode interfaceNode = mock(IManagedGridNode.class);
        var unit = newUnit(interfaceNode, 1, 0);

        assertThatCode(unit::breakCluster).doesNotThrowAnyException();
        verifyNoInteractions(interfaceNode);
    }
}
