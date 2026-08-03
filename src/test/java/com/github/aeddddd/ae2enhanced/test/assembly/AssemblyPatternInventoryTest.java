package com.github.aeddddd.ae2enhanced.test.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.core.definitions.AEItems;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyPatternInventory;
import com.github.aeddddd.ae2enhanced.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link AssemblyPatternInventory} 单元测试:样板槽区域到 AE2 InternalInventory 的映射.
 */
class AssemblyPatternInventoryTest {

    static {
        MinecraftTestBootstrap.bootstrap();
    }

    /** size 等于当前可用样板槽数(默认 5 页 = 510;扩容后增长). */
    @Test
    void testSizeFollowsPatternPages() {
        var pair = AssemblyTestFixtures.newPair();
        var inventory = new AssemblyPatternInventory(pair.patternManager());

        assertThat(inventory.size()).isEqualTo(510);

        pair.patternManager().getItemHandler()
                .setStackInSlot(2, new ItemStack(AssemblyTestFixtures.CAPACITY, 10));
        assertThat(inventory.size()).isEqualTo(100 * AssemblyControllerBlockEntity.PATTERN_SLOTS_PER_PAGE);
    }

    /** 槽位映射:对外槽位 i 对应背包槽位 UPGRADE_SLOTS + i. */
    @Test
    void testSlotOffsetMapping() {
        var pair = AssemblyTestFixtures.newPair();
        var inventory = new AssemblyPatternInventory(pair.patternManager());
        var pattern = AEItems.CRAFTING_PATTERN.stack();

        inventory.setItemDirect(0, pattern);
        // 写入落在了背包的样板区首槽
        assertThat(pair.patternManager().getItemHandler()
                .getStackInSlot(AssemblyControllerBlockEntity.UPGRADE_SLOTS).is(AEItems.CRAFTING_PATTERN.asItem()))
                .isTrue();
        assertThat(inventory.getStackInSlot(0).is(AEItems.CRAFTING_PATTERN.asItem())).isTrue();

        // 升级槽内容不会从样板视图泄漏
        pair.patternManager().getItemHandler()
                .setStackInSlot(0, new ItemStack(AssemblyTestFixtures.PARALLEL, 1));
        assertThat(inventory.getStackInSlot(0).is(AEItems.CRAFTING_PATTERN.asItem())).isTrue();
    }

    /** setItemDirect 触发脏标记(经 setStackInSlot 通道). */
    @Test
    void testSetItemDirectMarksDirty() {
        var pair = AssemblyTestFixtures.newPair();
        var inventory = new AssemblyPatternInventory(pair.patternManager());

        assertThat(pair.patternManager().isPatternsDirty()).isFalse();
        inventory.setItemDirect(0, AEItems.CRAFTING_PATTERN.stack());
        assertThat(pair.patternManager().isPatternsDirty()).isTrue();
    }

    /** 槽位上限恒为 1;校验委托给底层背包. */
    @Test
    void testSlotLimitAndValidation() {
        var pair = AssemblyTestFixtures.newPair();
        var inventory = new AssemblyPatternInventory(pair.patternManager());

        assertThat(inventory.getSlotLimit(0)).isEqualTo(1);
        assertThat(inventory.isItemValid(0, AEItems.CRAFTING_PATTERN.stack())).isTrue();
        assertThat(inventory.isItemValid(0, AEItems.PROCESSING_PATTERN.stack())).isFalse();
        assertThat(inventory.isItemValid(0, new ItemStack(Items.STONE))).isFalse();
    }

    /** 迭代器遍历 size 个槽位,内容与逐槽读取一致. */
    @Test
    void testIterator() {
        var pair = AssemblyTestFixtures.newPair();
        var inventory = new AssemblyPatternInventory(pair.patternManager());
        inventory.setItemDirect(0, AEItems.CRAFTING_PATTERN.stack());
        inventory.setItemDirect(5, AEItems.SMITHING_TABLE_PATTERN.stack());

        int count = 0;
        int nonEmpty = 0;
        for (ItemStack stack : inventory) {
            count++;
            if (!stack.isEmpty()) {
                nonEmpty++;
            }
        }
        assertThat(count).isEqualTo(inventory.size());
        assertThat(nonEmpty).isEqualTo(2);
    }
}
