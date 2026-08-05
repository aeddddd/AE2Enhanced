package com.github.aeddddd.ae2enhanced.test.memorycard;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import appeng.api.parts.IPart;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.IPriorityHost;
import appeng.items.parts.PartItem;

import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.memorycard.handler.ae2.AE2PartHandler;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * {@link AE2PartHandler} 单元测试.
 *
 * <p>部件用 mock + extraInterfaces 附加 AE2 能力接口(IPriorityHost / IUpgradeableObject),
 * 配置导入导出走 AE2 官方 MemoryCardItem 静态方法的真实实现;玩家背包用真实 Inventory.
 * 缺升级时向绑定网络请求合成的路径依赖服务端世界,不在单测范围.</p>
 */
class AE2PartHandlerTest {

    private final AE2PartHandler handler = new AE2PartHandler();

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    /** 带优先级的部件 mock. */
    private static IPart mockPriorityPart(int priority) {
        IPart part = mock(IPart.class, withSettings().extraInterfaces(IPriorityHost.class));
        when(((IPriorityHost) part).getPriority()).thenReturn(priority);
        return part;
    }

    /** 升级库存 mock:size 个槽位,stacks 为各槽内容(不足补 EMPTY). */
    private static IUpgradeInventory mockUpgradeInventory(int size, ItemStack... stacks) {
        IUpgradeInventory inventory = mock(IUpgradeInventory.class);
        when(inventory.size()).thenReturn(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = i < stacks.length ? stacks[i] : ItemStack.EMPTY;
            when(inventory.getStackInSlot(i)).thenReturn(stack);
        }
        // AE2 官方导出(storeUpgrades)经 Iterable 遍历升级库存
        when(inventory.iterator()).thenAnswer(inv -> List.of(stacks).iterator());
        return inventory;
    }

    /** 主手为空、背包真实的 mock 玩家. */
    private static Player mockPlayer() {
        Player player = mock(Player.class);
        Inventory inventory = new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        return player;
    }

    // ==================== canHandle ====================

    @Test
    void testCanHandleMatchesPartOnly() {
        assertThat(handler.canHandle(mock(IPart.class))).isTrue();
        assertThat(handler.canHandle(new Object())).isFalse();
    }

    // ==================== copy ====================

    @Test
    void testCopyPlainPartYieldsEmptyTag() {
        assertThat(handler.copy(mock(IPart.class)).isEmpty()).isTrue();
    }

    @Test
    void testCopyPriorityPartWritesPriority() {
        CompoundTag output = handler.copy(mockPriorityPart(5));

        assertThat(output.getInt("priority")).isEqualTo(5);
        // 无升级槽时不应出现升级键
        assertThat(output.contains("upgrades")).isFalse();
        assertThat(output.contains("ae2e:upgrades")).isFalse();
    }

    @Test
    void testCopyUpgradeablePartWritesSlotBasedUpgrades() {
        IUpgradeInventory inventory = mockUpgradeInventory(2, new ItemStack(Items.DIAMOND, 2), ItemStack.EMPTY);
        IPart part = mock(IPart.class, withSettings().extraInterfaces(IUpgradeableObject.class));
        when(((IUpgradeableObject) part).getUpgrades()).thenReturn(inventory);

        CompoundTag output = handler.copy(part);

        // 官方的 itemId->count 聚合键被移除,替换为按槽位序列化的 ae2e:upgrades
        assertThat(output.contains("upgrades")).isFalse();
        assertThat(output.contains("ae2e:upgrades")).isTrue();
        ListTag list = output.getList("ae2e:upgrades", Tag.TAG_COMPOUND);
        assertThat(list).hasSize(1);
        CompoundTag entry = list.getCompound(0);
        assertThat(entry.getInt("Slot")).isEqualTo(0);
        ItemStack restored = ItemStack.of(entry);
        assertThat(restored.is(Items.DIAMOND)).isTrue();
        assertThat(restored.getCount()).isEqualTo(2);
    }

    @Test
    void testCopySurvivesUpgradeSerializationFailure() {
        // size() 抛异常时升级序列化失败应被吞掉,基础配置仍正常返回
        IUpgradeInventory broken = mock(IUpgradeInventory.class);
        when(broken.iterator()).thenReturn(List.<ItemStack>of().iterator());
        when(broken.size()).thenThrow(new RuntimeException("模拟升级库存故障"));
        IPart part = mock(IPart.class, withSettings()
                .extraInterfaces(IUpgradeableObject.class, IPriorityHost.class));
        when(((IUpgradeableObject) part).getUpgrades()).thenReturn(broken);
        when(((IPriorityHost) part).getPriority()).thenReturn(4);

        CompoundTag output = handler.copy(part);

        assertThat(output.getInt("priority")).isEqualTo(4);
        assertThat(output.contains("ae2e:upgrades")).isFalse();
    }

    // ==================== paste ====================

    @Test
    void testPasteWithoutRecognizedSettingsReturnsInvalidMachine() {
        assertThat(handler.paste(mock(IPart.class), new CompoundTag(), mockPlayer()))
                .isEqualTo(PasteResult.INVALID_MACHINE);
    }

    @Test
    void testPasteAppliesPriority() {
        IPart part = mockPriorityPart(0);
        CompoundTag data = new CompoundTag();
        data.putInt("priority", 9);

        assertThat(handler.paste(part, data, mockPlayer())).isEqualTo(PasteResult.SUCCESS);
        verify((IPriorityHost) part).setPriority(9);
    }

    @Test
    void testPasteInstallsUpgradesFromInventory() {
        IUpgradeInventory inventory = mockUpgradeInventory(4);
        IPart part = mock(IPart.class, withSettings()
                .extraInterfaces(IUpgradeableObject.class, IPriorityHost.class));
        when(((IUpgradeableObject) part).getUpgrades()).thenReturn(inventory);

        // 配置:优先级 + 1 个钻石"升级卡";玩家背包里有 1 个钻石
        CompoundTag data = new CompoundTag();
        data.putInt("priority", 1);
        ListTag upgrades = new ListTag();
        CompoundTag entry = new ItemStack(Items.DIAMOND).save(new CompoundTag());
        entry.putInt("Slot", 0);
        upgrades.add(entry);
        data.put("ae2e:upgrades", upgrades);

        Player player = mockPlayer();
        player.getInventory().items.set(0, new ItemStack(Items.DIAMOND));

        assertThat(handler.paste(part, data, player)).isEqualTo(PasteResult.SUCCESS);

        // 升级卡被安装到 0 号槽(clearSlots 会先把所有槽置空)
        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory, org.mockito.Mockito.atLeastOnce()).setItemDirect(eq(0), captor.capture());
        // 最后一次写 0 号槽的是钻石升级卡
        List<ItemStack> writes = captor.getAllValues();
        assertThat(writes.get(writes.size() - 1).is(Items.DIAMOND)).isTrue();
        // 背包里的钻石已被消耗
        assertThat(player.getInventory().items.get(0).isEmpty()).isTrue();
        verify((IPriorityHost) part).setPriority(1);
    }

    @Test
    void testPasteMissingUpgradesReturnsMissingAndDoesNotTouchSlots() {
        IUpgradeInventory inventory = mockUpgradeInventory(4);
        IPart part = mock(IPart.class, withSettings().extraInterfaces(IUpgradeableObject.class));
        when(((IUpgradeableObject) part).getUpgrades()).thenReturn(inventory);

        CompoundTag data = new CompoundTag();
        ListTag upgrades = new ListTag();
        CompoundTag entry = new ItemStack(Items.DIAMOND).save(new CompoundTag());
        entry.putInt("Slot", 0);
        upgrades.add(entry);
        data.put("ae2e:upgrades", upgrades);

        // 背包空且主手非内存卡 -> 无网络回退
        assertThat(handler.paste(part, data, mockPlayer())).isEqualTo(PasteResult.MISSING_UPGRADES);
        verify(inventory, never()).setItemDirect(anyInt(), any(ItemStack.class));
    }

    @Test
    void testPasteSkipsUpgradesForNonUpgradeablePart() {
        // 数据含升级键但部件不可升级 -> 跳过升级处理,其余配置照常导入
        IPart part = mockPriorityPart(0);
        CompoundTag data = new CompoundTag();
        data.putInt("priority", 2);
        ListTag upgrades = new ListTag();
        CompoundTag entry = new ItemStack(Items.DIAMOND).save(new CompoundTag());
        entry.putInt("Slot", 0);
        upgrades.add(entry);
        data.put("ae2e:upgrades", upgrades);

        assertThat(handler.paste(part, data, mockPlayer())).isEqualTo(PasteResult.SUCCESS);
        verify((IPriorityHost) part).setPriority(2);
    }

    // ==================== getDisplayName ====================

    @Test
    void testGetDisplayNameFallsBackToClassNameForNonPart() {
        assertThat(handler.getDisplayName(new Object())).isEqualTo("Object");
    }

    @Test
    void testGetDisplayNameUsesPartItemHoverName() {
        // 部件显示名来自其 PartItem 的悬浮名;mock 物品缺少 intrusive holder 无法注册,用真实 PartItem
        PartItem<IPart> partItem = new PartItem<IPart>(new net.minecraft.world.item.Item.Properties(),
                IPart.class, item -> null);
        ForgeRegistries.ITEMS.register(new ResourceLocation("ae2enhanced", "test_umc_display_part"), partItem);

        IPart part = mock(IPart.class);
        org.mockito.Mockito.doReturn(partItem).when(part).getPartItem();

        assertThat(handler.getDisplayName(part))
                .isEqualTo(new ItemStack(partItem).getHoverName().getString());
    }
}
