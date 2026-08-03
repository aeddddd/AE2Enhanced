package com.github.aeddddd.ae2enhanced.test.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyUpgradeManager;
import com.github.aeddddd.ae2enhanced.blockentity.AssemblyControllerBlockEntity;

/**
 * {@link AssemblyUpgradeManager} 单元测试:升级卡数量到有效属性的换算.
 */
class AssemblyUpgradeManagerTest {

    /** 未接线 patternManager 时全部为默认值. */
    @Test
    void testDefaultsWithoutPatternManager() {
        var manager = new AssemblyUpgradeManager();
        assertThat(manager.getParallelCap()).isEqualTo(64);
        assertThat(manager.getCraftingTicks()).isEqualTo(20);
        assertThat(manager.getPatternPages()).isEqualTo(AssemblyControllerBlockEntity.PATTERN_PAGES_BASE);
        assertThat(manager.hasAutoUploadUpgrade()).isFalse();
    }

    /** 空背包 → 默认值. */
    @Test
    void testDefaultsWithEmptyHandler() {
        var pair = AssemblyTestFixtures.newPair();
        assertThat(pair.upgradeManager().getParallelCap()).isEqualTo(64);
        assertThat(pair.upgradeManager().getCraftingTicks()).isEqualTo(20);
        assertThat(pair.upgradeManager().getPatternPages()).isEqualTo(5);
        assertThat(pair.upgradeManager().hasAutoUploadUpgrade()).isFalse();
    }

    /** 并行升级:0 张 = 64,每张 ×32,3 张 = 2097152,4 张封顶 67108864,5 张 = Long.MAX_VALUE. */
    @Test
    void testParallelCap() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();

        handler.setStackInSlot(0, new ItemStack(AssemblyTestFixtures.PARALLEL, 1));
        assertThat(pair.upgradeManager().getParallelCap()).isEqualTo(64L * 32);

        handler.setStackInSlot(0, new ItemStack(AssemblyTestFixtures.PARALLEL, 3));
        assertThat(pair.upgradeManager().getParallelCap()).isEqualTo(64L * 32 * 32 * 32);

        handler.setStackInSlot(0, new ItemStack(AssemblyTestFixtures.PARALLEL, 4));
        assertThat(pair.upgradeManager().getParallelCap()).isEqualTo(67108864L);

        handler.setStackInSlot(0, new ItemStack(AssemblyTestFixtures.PARALLEL, 5));
        assertThat(pair.upgradeManager().getParallelCap()).isEqualTo(Long.MAX_VALUE);
    }

    /** 槽位 0 放入非并行升级卡 → 并行上限保持默认 64. */
    @Test
    void testParallelCapWithWrongItem() {
        var pair = AssemblyTestFixtures.newPair();
        // 槽位 0 的 isItemValid 拒绝速度卡 → 槽位保持为空
        pair.patternManager().getItemHandler()
                .setStackInSlot(0, new ItemStack(AssemblyTestFixtures.SPEED, 1));
        assertThat(pair.upgradeManager().getParallelCap()).isEqualTo(64);
    }

    /** 速度升级:每张减半,20→10→5→2→1,最低 1 tick. */
    @Test
    void testCraftingTicks() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();

        handler.setStackInSlot(1, new ItemStack(AssemblyTestFixtures.SPEED, 1));
        assertThat(pair.upgradeManager().getCraftingTicks()).isEqualTo(10);

        handler.setStackInSlot(1, new ItemStack(AssemblyTestFixtures.SPEED, 2));
        assertThat(pair.upgradeManager().getCraftingTicks()).isEqualTo(5);

        handler.setStackInSlot(1, new ItemStack(AssemblyTestFixtures.SPEED, 3));
        assertThat(pair.upgradeManager().getCraftingTicks()).isEqualTo(2);

        handler.setStackInSlot(1, new ItemStack(AssemblyTestFixtures.SPEED, 4));
        assertThat(pair.upgradeManager().getCraftingTicks()).isEqualTo(1);

        handler.setStackInSlot(1, new ItemStack(AssemblyTestFixtures.SPEED, 5));
        assertThat(pair.upgradeManager().getCraftingTicks()).isEqualTo(1);
    }

    /** 扩容升级:基础 5 页,每张 +10 页,封顶 100 页. */
    @Test
    void testPatternPages() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();

        handler.setStackInSlot(2, new ItemStack(AssemblyTestFixtures.CAPACITY, 3));
        assertThat(pair.upgradeManager().getPatternPages()).isEqualTo(35);

        handler.setStackInSlot(2, new ItemStack(AssemblyTestFixtures.CAPACITY, 10));
        assertThat(pair.upgradeManager().getPatternPages())
                .isEqualTo(AssemblyControllerBlockEntity.PATTERN_PAGES_MAX);

        // 槽位 2 放入非扩容卡被校验拒绝,原扩容卡保留 → 页数不变
        handler.setStackInSlot(2, new ItemStack(AssemblyTestFixtures.PARALLEL, 1));
        assertThat(pair.upgradeManager().getPatternPages())
                .isEqualTo(AssemblyControllerBlockEntity.PATTERN_PAGES_MAX);

        // 清空槽位 → 回到基础页数
        handler.setStackInSlot(2, ItemStack.EMPTY);
        assertThat(pair.upgradeManager().getPatternPages()).isEqualTo(5);
    }

    /** 自动上传升级:仅槽位 4 放入正确物品时为 true. */
    @Test
    void testAutoUploadUpgrade() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();

        assertThat(pair.upgradeManager().hasAutoUploadUpgrade()).isFalse();

        handler.setStackInSlot(4, new ItemStack(AssemblyTestFixtures.AUTO_UPLOAD, 1));
        assertThat(pair.upgradeManager().hasAutoUploadUpgrade()).isTrue();

        handler.setStackInSlot(4, ItemStack.EMPTY);
        assertThat(pair.upgradeManager().hasAutoUploadUpgrade()).isFalse();
    }
}
