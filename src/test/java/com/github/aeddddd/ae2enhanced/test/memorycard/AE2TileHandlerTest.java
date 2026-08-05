package com.github.aeddddd.ae2enhanced.test.memorycard;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.helpers.IPriorityHost;

import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.memorycard.handler.ae2.AE2TileHandler;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * {@link AE2TileHandler} 单元测试.
 *
 * <p>与 {@link AE2PartHandlerTest} 同套路:方块设备用 mock + extraInterfaces 附加
 * AE2 能力接口,配置导入导出走 AE2 官方 MemoryCardItem 静态方法的真实实现.</p>
 */
class AE2TileHandlerTest {

    private final AE2TileHandler handler = new AE2TileHandler();

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private static AEBaseBlockEntity mockPriorityTile(int priority) {
        AEBaseBlockEntity tile = mock(AEBaseBlockEntity.class,
                withSettings().extraInterfaces(IPriorityHost.class));
        when(((IPriorityHost) tile).getPriority()).thenReturn(priority);
        return tile;
    }

    private static IUpgradeInventory mockUpgradeInventory(int size, ItemStack... stacks) {
        IUpgradeInventory inventory = mock(IUpgradeInventory.class);
        when(inventory.size()).thenReturn(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = i < stacks.length ? stacks[i] : ItemStack.EMPTY;
            when(inventory.getStackInSlot(i)).thenReturn(stack);
        }
        when(inventory.iterator()).thenAnswer(inv -> List.of(stacks).iterator());
        return inventory;
    }

    private static Player mockPlayer() {
        Player player = mock(Player.class);
        Inventory inventory = new Inventory(player);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getMainHandItem()).thenReturn(ItemStack.EMPTY);
        return player;
    }

    // ==================== canHandle ====================

    @Test
    void testCanHandleMatchesAe2TileOnly() {
        assertThat(handler.canHandle(mock(AEBaseBlockEntity.class))).isTrue();
        assertThat(handler.canHandle(new Object())).isFalse();
    }

    // ==================== copy ====================

    @Test
    void testCopyPlainTileYieldsEmptyTag() {
        assertThat(handler.copy(mock(AEBaseBlockEntity.class)).isEmpty()).isTrue();
    }

    @Test
    void testCopyPriorityTileWritesPriority() {
        CompoundTag output = handler.copy(mockPriorityTile(6));

        assertThat(output.getInt("priority")).isEqualTo(6);
        assertThat(output.contains("upgrades")).isFalse();
        assertThat(output.contains("ae2e:upgrades")).isFalse();
    }

    @Test
    void testCopyUpgradeableTileWritesSlotBasedUpgrades() {
        IUpgradeInventory inventory = mockUpgradeInventory(3, ItemStack.EMPTY, new ItemStack(Items.STICK, 1));
        AEBaseBlockEntity tile = mock(AEBaseBlockEntity.class,
                withSettings().extraInterfaces(IUpgradeableObject.class));
        when(((IUpgradeableObject) tile).getUpgrades()).thenReturn(inventory);

        CompoundTag output = handler.copy(tile);

        assertThat(output.contains("upgrades")).isFalse();
        ListTag list = output.getList("ae2e:upgrades", Tag.TAG_COMPOUND);
        assertThat(list).hasSize(1);
        CompoundTag entry = list.getCompound(0);
        assertThat(entry.getInt("Slot")).isEqualTo(1);
        assertThat(ItemStack.of(entry).is(Items.STICK)).isTrue();
    }

    @Test
    void testCopySurvivesUpgradeSerializationFailure() {
        IUpgradeInventory broken = mock(IUpgradeInventory.class);
        when(broken.iterator()).thenReturn(List.<ItemStack>of().iterator());
        when(broken.size()).thenThrow(new RuntimeException("模拟升级库存故障"));
        AEBaseBlockEntity tile = mock(AEBaseBlockEntity.class, withSettings()
                .extraInterfaces(IUpgradeableObject.class, IPriorityHost.class));
        when(((IUpgradeableObject) tile).getUpgrades()).thenReturn(broken);
        when(((IPriorityHost) tile).getPriority()).thenReturn(8);

        CompoundTag output = handler.copy(tile);

        assertThat(output.getInt("priority")).isEqualTo(8);
        assertThat(output.contains("ae2e:upgrades")).isFalse();
    }

    // ==================== paste ====================

    @Test
    void testPasteWithoutRecognizedSettingsReturnsInvalidMachine() {
        assertThat(handler.paste(mock(AEBaseBlockEntity.class), new CompoundTag(), mockPlayer()))
                .isEqualTo(PasteResult.INVALID_MACHINE);
    }

    @Test
    void testPasteAppliesPriority() {
        AEBaseBlockEntity tile = mockPriorityTile(0);
        CompoundTag data = new CompoundTag();
        data.putInt("priority", 12);

        assertThat(handler.paste(tile, data, mockPlayer())).isEqualTo(PasteResult.SUCCESS);
        verify((IPriorityHost) tile).setPriority(12);
    }

    @Test
    void testPasteInstallsUpgradesFromInventory() {
        IUpgradeInventory inventory = mockUpgradeInventory(2);
        AEBaseBlockEntity tile = mock(AEBaseBlockEntity.class, withSettings()
                .extraInterfaces(IUpgradeableObject.class, IPriorityHost.class));
        when(((IUpgradeableObject) tile).getUpgrades()).thenReturn(inventory);

        CompoundTag data = new CompoundTag();
        data.putInt("priority", 1);
        ListTag upgrades = new ListTag();
        CompoundTag entry = new ItemStack(Items.DIAMOND).save(new CompoundTag());
        entry.putInt("Slot", 0);
        upgrades.add(entry);
        data.put("ae2e:upgrades", upgrades);

        Player player = mockPlayer();
        player.getInventory().items.set(0, new ItemStack(Items.DIAMOND));

        assertThat(handler.paste(tile, data, player)).isEqualTo(PasteResult.SUCCESS);

        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(inventory, org.mockito.Mockito.atLeastOnce()).setItemDirect(eq(0), captor.capture());
        List<ItemStack> writes = captor.getAllValues();
        assertThat(writes.get(writes.size() - 1).is(Items.DIAMOND)).isTrue();
        assertThat(player.getInventory().items.get(0).isEmpty()).isTrue();
    }

    @Test
    void testPasteMissingUpgradesReturnsMissingAndDoesNotTouchSlots() {
        IUpgradeInventory inventory = mockUpgradeInventory(2);
        AEBaseBlockEntity tile = mock(AEBaseBlockEntity.class,
                withSettings().extraInterfaces(IUpgradeableObject.class));
        when(((IUpgradeableObject) tile).getUpgrades()).thenReturn(inventory);

        CompoundTag data = new CompoundTag();
        ListTag upgrades = new ListTag();
        CompoundTag entry = new ItemStack(Items.DIAMOND).save(new CompoundTag());
        entry.putInt("Slot", 0);
        upgrades.add(entry);
        data.put("ae2e:upgrades", upgrades);

        assertThat(handler.paste(tile, data, mockPlayer())).isEqualTo(PasteResult.MISSING_UPGRADES);
        verify(inventory, never()).setItemDirect(anyInt(), any(ItemStack.class));
    }

    // ==================== getDisplayName ====================

    @Test
    void testGetDisplayNameUsesBlockName() {
        AEBaseBlockEntity tile = mock(AEBaseBlockEntity.class);
        when(tile.getBlockState()).thenReturn(Blocks.FURNACE.defaultBlockState());

        assertThat(handler.getDisplayName(tile))
                .isEqualTo(Blocks.FURNACE.getName().getString());
    }

    @Test
    void testGetDisplayNameFallsBackToClassNameForNonTile() {
        assertThat(handler.getDisplayName(new Object())).isEqualTo("Object");
    }
}
