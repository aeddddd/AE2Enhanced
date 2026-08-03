package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;
import com.github.aeddddd.ae2enhanced.specialcrafting.RoutingDecision;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link RoutingDecision} 单元测试:虚拟 CPU 路由判定.
 * <p>判定依据为注册表成员身份,只有登记进 {@link VirtualCraftingCPURegistry}
 * 的集群才被识别为本项目虚拟 CPU.</p>
 */
class RoutingDecisionTest {

    static {
        MinecraftTestBootstrap.bootstrap();
    }

    private final CraftingCPUCluster cluster = new CraftingCPUCluster(BlockPos.ZERO, BlockPos.ZERO);

    @AfterEach
    void cleanup() {
        // 防止静态注册表残留污染其他测试
        VirtualCraftingCPURegistry.unregister(cluster);
    }

    /** 非 CraftingCPUCluster 的 CPU(如第三方实现)→ false. */
    @Test
    void testNonClusterCpuRejected() {
        ICraftingCPU cpu = mock(ICraftingCPU.class);
        assertThat(RoutingDecision.isOurVirtualCpu(cpu)).isFalse();
    }

    /** null → false(不抛异常). */
    @Test
    void testNullCpuRejected() {
        assertThat(RoutingDecision.isOurVirtualCpu(null)).isFalse();
    }

    /** 未注册的普通集群 → false. */
    @Test
    void testUnregisteredClusterRejected() {
        assertThat(RoutingDecision.isOurVirtualCpu(cluster)).isFalse();
    }

    /** 注册后的集群 → true;注销后恢复 false. */
    @Test
    void testRegisteredClusterAccepted() {
        VirtualCraftingCPURegistry.register(cluster);
        assertThat(RoutingDecision.isOurVirtualCpu(cluster)).isTrue();

        VirtualCraftingCPURegistry.unregister(cluster);
        assertThat(RoutingDecision.isOurVirtualCpu(cluster)).isFalse();
    }

    /** 注册只认对象身份:同边界坐标的另一个集群不受影响. */
    @Test
    void testIdentityBasedMatching() {
        var other = new CraftingCPUCluster(BlockPos.ZERO, BlockPos.ZERO);
        VirtualCraftingCPURegistry.register(cluster);
        try {
            assertThat(RoutingDecision.isOurVirtualCpu(cluster)).isTrue();
            assertThat(RoutingDecision.isOurVirtualCpu(other)).isFalse();
        } finally {
            VirtualCraftingCPURegistry.unregister(cluster);
        }
    }
}
