package com.github.aeddddd.ae2enhanced.test.computation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;

import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.computation.cpu.VirtualCraftingCPURegistry;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link VirtualCraftingCPURegistry} 单元测试:虚拟 CPU 集群的登记/注销/查询.
 */
class VirtualCraftingCPURegistryTest {

    static {
        MinecraftTestBootstrap.bootstrap();
    }

    private final CraftingCPUCluster cluster = new CraftingCPUCluster(BlockPos.ZERO, BlockPos.ZERO);

    @AfterEach
    void cleanup() {
        // 静态注册表,防止残留污染其他测试
        VirtualCraftingCPURegistry.unregister(cluster);
    }

    /** 注册后出现在集群集合中. */
    @Test
    void testRegisterAddsToClusters() {
        VirtualCraftingCPURegistry.register(cluster);
        assertThat(VirtualCraftingCPURegistry.getClusters()).contains(cluster);
    }

    /** 注销后从集群集合移除. */
    @Test
    void testUnregisterRemovesFromClusters() {
        VirtualCraftingCPURegistry.register(cluster);
        VirtualCraftingCPURegistry.unregister(cluster);
        assertThat(VirtualCraftingCPURegistry.getClusters()).doesNotContain(cluster);
    }

    /** 重复注册幂等(集合语义). */
    @Test
    void testRegisterIdempotent() {
        VirtualCraftingCPURegistry.register(cluster);
        VirtualCraftingCPURegistry.register(cluster);

        VirtualCraftingCPURegistry.unregister(cluster);
        assertThat(VirtualCraftingCPURegistry.getClusters()).doesNotContain(cluster);
    }

    /** getClusters 返回不可修改视图. */
    @Test
    void testClustersViewUnmodifiable() {
        VirtualCraftingCPURegistry.register(cluster);
        assertThatThrownBy(() -> VirtualCraftingCPURegistry.getClusters().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    /** isOurVirtualCpu:注册成员 → true;未注册/null → false. */
    @Test
    void testIsOurVirtualCpu() {
        assertThat(VirtualCraftingCPURegistry.isOurVirtualCpu(cluster)).isFalse();
        assertThat(VirtualCraftingCPURegistry.isOurVirtualCpu(null)).isFalse();

        VirtualCraftingCPURegistry.register(cluster);
        assertThat(VirtualCraftingCPURegistry.isOurVirtualCpu(cluster)).isTrue();

        // 同坐标但不同对象身份 → false(普通/第三方 CPU 不会被误判)
        var other = new CraftingCPUCluster(BlockPos.ZERO, BlockPos.ZERO);
        assertThat(VirtualCraftingCPURegistry.isOurVirtualCpu(other)).isFalse();
    }
}
