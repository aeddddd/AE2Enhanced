package com.github.aeddddd.ae2enhanced.util.placement;

import java.util.UUID;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlacementToolHelper} 单元测试.
 * <p>placeSingle / placeBulk / placeCableBetween 依赖 ME 网络与方块放置运行时,无法单元测试；
 * 此处覆盖纯查询逻辑 {@link PlacementToolHelper#findMatchingStack} 与 undoLast 的提前返回分支.</p>
 */
class PlacementToolHelperTest {

    @BeforeAll
    static void bootstrap() {
        AE2KeyTypeTestBootstrap.bootstrap();
    }

    private static MEStorage storageWith(AEItemKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        MEStorage storage = mock(MEStorage.class);
        when(storage.getAvailableStacks()).thenReturn(counter);
        return storage;
    }

    // ========== findMatchingStack ==========

    @Test
    void findMatchingStackExactMatch() {
        AEItemKey stone = AEItemKey.of(new ItemStack(Items.STONE));
        MEStorage storage = storageWith(stone, 64);

        assertThat(PlacementToolHelper.findMatchingStack(storage, new ItemStack(Items.STONE)))
                .isEqualTo(stone);
    }

    @Test
    void findMatchingStackFallsBackToItemOnlyMatch() {
        // 网络里的石头带 NBT,目标不带 NBT：精确匹配失败,退化为忽略 NBT 的物品匹配
        ItemStack tagged = new ItemStack(Items.STONE);
        tagged.getOrCreateTag().putInt("custom", 1);
        AEItemKey taggedStone = AEItemKey.of(tagged);
        MEStorage storage = storageWith(taggedStone, 64);

        assertThat(PlacementToolHelper.findMatchingStack(storage, new ItemStack(Items.STONE)))
                .isEqualTo(taggedStone);
    }

    @Test
    void findMatchingStackPrefersExactOverFuzzy() {
        // 同时存在带 NBT 与不带 NBT 的石头时,优先精确匹配
        ItemStack tagged = new ItemStack(Items.STONE);
        tagged.getOrCreateTag().putInt("custom", 1);
        AEItemKey taggedStone = AEItemKey.of(tagged);
        AEItemKey plainStone = AEItemKey.of(new ItemStack(Items.STONE));

        KeyCounter counter = new KeyCounter();
        counter.add(taggedStone, 64);
        counter.add(plainStone, 32);
        MEStorage storage = mock(MEStorage.class);
        when(storage.getAvailableStacks()).thenReturn(counter);

        assertThat(PlacementToolHelper.findMatchingStack(storage, new ItemStack(Items.STONE)))
                .isEqualTo(plainStone);
    }

    @Test
    void findMatchingStackReturnsNullWhenAbsent() {
        MEStorage storage = storageWith(AEItemKey.of(new ItemStack(Items.STONE)), 64);
        assertThat(PlacementToolHelper.findMatchingStack(storage, new ItemStack(Items.DIRT))).isNull();
    }

    // ========== undoLast 提前返回分支 ==========

    @Test
    void undoLastReturnsTrueOnClientSide() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        Player player = mock(Player.class);

        assertThat(PlacementToolHelper.undoLast(player, level, new ItemStack(Items.STICK))).isTrue();
    }

    @Test
    void undoLastFailsWhenNothingRecorded() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        Player player = mock(Player.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID());

        assertThat(PlacementToolHelper.undoLast(player, level, new ItemStack(Items.STICK))).isFalse();
        // 提示“没有可撤销的操作”
        verify(player).displayClientMessage(any(), eq(false));
    }
}
