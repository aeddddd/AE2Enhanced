package com.github.aeddddd.ae2enhanced.test.memorycard;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.memorycard.core.MemoryCardUpgradeHelper;
import com.github.aeddddd.ae2enhanced.memorycard.network.UMCNetworkLink;
import com.github.aeddddd.ae2enhanced.memorycard.upgrade.IUpgradeProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MemoryCardUpgradeHelper} 单元测试.
 *
 * <p>升级槽用内存中的假 IUpgradeProvider;玩家背包用真实 {@link Inventory}
 * (countInInventory/consumeFromInventory 直接读写 items/offhand 列表,mock 会 NPE).
 * 网络回退只覆盖可离线验证的守卫分支;真实网格提取/合成请求依赖服务端世界,不在单测范围.</p>
 */
class MemoryCardUpgradeHelperTest {

    /** 简单的数组实现,供序列化/粘贴流程观察. */
    private static class ArrayUpgradeProvider implements IUpgradeProvider {
        private final ItemStack[] slots;

        ArrayUpgradeProvider(int size) {
            this.slots = new ItemStack[size];
            Arrays.fill(this.slots, ItemStack.EMPTY);
        }

        @Override
        public int getSlotCount() {
            return slots.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slots[slot];
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            slots[slot] = stack;
        }

        @Override
        public void clearSlots() {
            Arrays.fill(slots, ItemStack.EMPTY);
        }
    }

    @BeforeAll
    static void bootstrap() {
        UMCTestSupport.bootstrap();
    }

    /** mock 玩家,主手为空,背包为真实 Inventory. */
    private static Player mockPlayer() {
        Player player = mock(Player.class);
        Inventory inventory = new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        return player;
    }

    // ==================== 序列化 ====================

    @Test
    void testSerializeSkipsEmptySlotsAndKeepsIndices() {
        ArrayUpgradeProvider provider = new ArrayUpgradeProvider(3);
        provider.setStackInSlot(0, new ItemStack(Items.DIAMOND, 2));
        provider.setStackInSlot(2, new ItemStack(Items.STICK));

        ListTag list = MemoryCardUpgradeHelper.serializeUpgrades(provider);
        assertThat(list).hasSize(2);

        CompoundTag first = list.getCompound(0);
        assertThat(first.getInt("Slot")).isEqualTo(0);
        ItemStack firstStack = ItemStack.of(first);
        assertThat(firstStack.is(Items.DIAMOND)).isTrue();
        assertThat(firstStack.getCount()).isEqualTo(2);

        CompoundTag second = list.getCompound(1);
        assertThat(second.getInt("Slot")).isEqualTo(2);
        assertThat(ItemStack.of(second).is(Items.STICK)).isTrue();
    }

    @Test
    void testSerializeEmptyProviderYieldsEmptyList() {
        assertThat(MemoryCardUpgradeHelper.serializeUpgrades(new ArrayUpgradeProvider(4))).isEmpty();
    }

    @Test
    void testDeserializeSkipsEmptyEntries() {
        ListTag list = new ListTag();
        list.add(new ItemStack(Items.DIAMOND).save(new CompoundTag()));
        list.add(new CompoundTag()); // 空条目 -> ItemStack.EMPTY -> 跳过

        List<ItemStack> result = MemoryCardUpgradeHelper.deserializeUpgrades(list);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).is(Items.DIAMOND)).isTrue();
    }

    @Test
    void testSerializeDeserializeRoundTrip() {
        ArrayUpgradeProvider provider = new ArrayUpgradeProvider(2);
        ItemStack diamond = new ItemStack(Items.DIAMOND, 3);
        provider.setStackInSlot(1, diamond);

        List<ItemStack> restored = MemoryCardUpgradeHelper.deserializeUpgrades(
                MemoryCardUpgradeHelper.serializeUpgrades(provider));
        assertThat(restored).hasSize(1);
        assertThat(ItemStack.isSameItemSameTags(restored.get(0), diamond)).isTrue();
        assertThat(restored.get(0).getCount()).isEqualTo(3);
    }

    // ==================== applyUpgrades ====================

    @Test
    void testApplyUpgradesWithEmptyNeededClearsSlots() {
        ArrayUpgradeProvider provider = new ArrayUpgradeProvider(2);
        provider.setStackInSlot(0, new ItemStack(Items.STICK));

        PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(provider, List.of(), mockPlayer());

        assertThat(result).isEqualTo(PasteResult.SUCCESS);
        assertThat(provider.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(provider.getStackInSlot(1).isEmpty()).isTrue();
    }

    @Test
    void testApplyUpgradesInstallsNeededAndReturnsOldToInventory() {
        ArrayUpgradeProvider provider = new ArrayUpgradeProvider(4);
        ItemStack oldUpgrade = new ItemStack(Items.STICK);
        provider.setStackInSlot(0, oldUpgrade);

        Player player = mockPlayer();
        player.getInventory().items.set(0, new ItemStack(Items.DIAMOND));

        PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(
                provider, List.of(new ItemStack(Items.DIAMOND)), player);

        assertThat(result).isEqualTo(PasteResult.SUCCESS);
        // 新升级已安装
        assertThat(provider.getStackInSlot(0).is(Items.DIAMOND)).isTrue();
        // 新升级已从背包消耗
        assertThat(MemoryCardUpgradeHelper.countInInventory(player, new ItemStack(Items.DIAMOND))).isZero();
        // 旧升级已返还背包
        assertThat(MemoryCardUpgradeHelper.countInInventory(player, new ItemStack(Items.STICK))).isEqualTo(1);
    }

    @Test
    void testApplyUpgradesMissingWithoutNetworkKeepsProviderUntouched() {
        ArrayUpgradeProvider provider = new ArrayUpgradeProvider(2);
        provider.setStackInSlot(0, new ItemStack(Items.STICK));

        Player player = mockPlayer(); // 背包空,主手非内存卡 -> 网络回退不可用

        PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(
                provider, List.of(new ItemStack(Items.DIAMOND)), player);

        assertThat(result).isEqualTo(PasteResult.MISSING_UPGRADES);
        // 验证失败发生在弹出旧升级之前,槽位保持原样
        assertThat(provider.getStackInSlot(0).is(Items.STICK)).isTrue();
    }

    // ==================== ensureAvailable ====================

    @Test
    void testEnsureAvailableTrueWhenInventorySufficient() {
        Player player = mockPlayer();
        player.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 5));

        assertThat(MemoryCardUpgradeHelper.ensureAvailable(player, List.of(new ItemStack(Items.DIAMOND, 3))))
                .isTrue();
    }

    @Test
    void testEnsureAvailableFalseWhenMissingAndNoNetwork() {
        Player player = mockPlayer();
        player.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 1));

        assertThat(MemoryCardUpgradeHelper.ensureAvailable(player, List.of(new ItemStack(Items.DIAMOND, 3))))
                .isFalse();
    }

    // ==================== 背包计数与消耗 ====================

    @Test
    void testCountInInventoryMatchesItemAndTagsAcrossCompartments() {
        Player player = mockPlayer();
        player.getInventory().items.set(0, new ItemStack(Items.DIAMOND, 2));
        ItemStack tagged = new ItemStack(Items.DIAMOND);
        tagged.getOrCreateTag().putInt("marker", 1);
        player.getInventory().items.set(1, tagged);
        player.getInventory().offhand.set(0, new ItemStack(Items.DIAMOND));

        // 无 NBT 的钻石只统计无 NBT 的堆叠(主手 2 + 副手 1)
        assertThat(MemoryCardUpgradeHelper.countInInventory(player, new ItemStack(Items.DIAMOND))).isEqualTo(3);
        // 带 NBT 的钻石单独统计
        assertThat(MemoryCardUpgradeHelper.countInInventory(player, tagged)).isEqualTo(1);
    }

    @Test
    void testConsumeFromInventoryShrinksAcrossSlotsThenOffhand() {
        Player player = mockPlayer();
        Inventory inventory = player.getInventory();
        inventory.items.set(0, new ItemStack(Items.DIAMOND));
        inventory.items.set(1, new ItemStack(Items.DIAMOND, 2));
        inventory.offhand.set(0, new ItemStack(Items.DIAMOND));

        MemoryCardUpgradeHelper.consumeFromInventory(player, new ItemStack(Items.DIAMOND, 3));
        assertThat(inventory.items.get(0).isEmpty()).isTrue();
        assertThat(inventory.items.get(1).isEmpty()).isTrue();
        assertThat(inventory.offhand.get(0).getCount()).isEqualTo(1);

        MemoryCardUpgradeHelper.consumeFromInventory(player, new ItemStack(Items.DIAMOND));
        assertThat(inventory.offhand.get(0).isEmpty()).isTrue();
    }

    // ==================== 网络回退守卫 ====================

    @Test
    void testTryPullFromNetworkFailsWithoutCardInHand() {
        Player player = mockPlayer();
        assertThat(MemoryCardUpgradeHelper.tryPullFromNetwork(player, List.of(new ItemStack(Items.DIAMOND))))
                .isEqualTo(MemoryCardUpgradeHelper.NetworkPullResult.FAILED);
    }

    @Test
    void testTryPullFromNetworkFailsWhenCardNotLinked() {
        Player player = mockPlayer();
        when(player.getMainHandItem()).thenReturn(UMCTestSupport.newCardStack());

        assertThat(MemoryCardUpgradeHelper.tryPullFromNetwork(player, List.of(new ItemStack(Items.DIAMOND))))
                .isEqualTo(MemoryCardUpgradeHelper.NetworkPullResult.FAILED);
    }

    @Test
    void testTryPullFromNetworkFailsWhenGridUnavailable() {
        Player player = mockPlayer();
        ItemStack card = UMCTestSupport.newCardStack();
        UMCNetworkLink.link(card, GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 64, 1)));
        when(player.getMainHandItem()).thenReturn(card);
        // 非 ServerLevel 的 mock 世界 -> getLinkedGrid 直接返回 null
        when(player.level()).thenReturn(mock(Level.class));

        assertThat(MemoryCardUpgradeHelper.tryPullFromNetwork(player, List.of(new ItemStack(Items.DIAMOND))))
                .isEqualTo(MemoryCardUpgradeHelper.NetworkPullResult.FAILED);
    }
}
