package com.github.aeddddd.ae2enhanced.test.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.crafting.IPatternDetails;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.core.definitions.AEItems;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyPatternManager;
import com.github.aeddddd.ae2enhanced.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link AssemblyPatternManager} 单元测试:样板过滤、槽位校验、脏标记刷新与容量守卫.
 * <p>controller 为 mock(level 为 null),SavedData 相关路径自然短路.</p>
 */
class AssemblyPatternManagerTest {

    static {
        MinecraftTestBootstrap.bootstrap();
    }

    // ===== 样板类型过滤 =====

    /** 分子装配室可执行的样板(合成/锻造台/切石机)→ true,处理样板与普通物品 → false. */
    @Test
    void testSupportedPatternStacks() {
        assertThat(AssemblyPatternManager.isSupportedPattern(AEItems.CRAFTING_PATTERN.stack())).isTrue();
        assertThat(AssemblyPatternManager.isSupportedPattern(AEItems.SMITHING_TABLE_PATTERN.stack())).isTrue();
        assertThat(AssemblyPatternManager.isSupportedPattern(AEItems.STONECUTTING_PATTERN.stack())).isTrue();

        assertThat(AssemblyPatternManager.isSupportedPattern(AEItems.PROCESSING_PATTERN.stack())).isFalse();
        assertThat(AssemblyPatternManager.isSupportedPattern(new ItemStack(Items.STONE))).isFalse();
        assertThat(AssemblyPatternManager.isSupportedPattern(ItemStack.EMPTY)).isFalse();
    }

    /** 解码后样板:实现 IMolecularAssemblerSupportedPattern → true. */
    @Test
    void testSupportedPatternDetails() {
        var supported = mock(IMolecularAssemblerSupportedPattern.class);
        var unsupported = mock(IPatternDetails.class);
        assertThat(AssemblyPatternManager.isSupportedPattern(supported)).isTrue();
        assertThat(AssemblyPatternManager.isSupportedPattern(unsupported)).isFalse();
    }

    // ===== 槽位数量 =====

    /** 默认 5 页 → 5 × 102 = 510 个样板槽. */
    @Test
    void testPatternSlotCountDefault() {
        var pair = AssemblyTestFixtures.newPair();
        assertThat(pair.patternManager().getPatternSlotCount())
                .isEqualTo(5 * AssemblyControllerBlockEntity.PATTERN_SLOTS_PER_PAGE);
    }

    /** 背包以最大容量预分配,扩容无需再分配. */
    @Test
    void testHandlerPreallocatedAtMaxCapacity() {
        var pair = AssemblyTestFixtures.newPair();
        assertThat(pair.patternManager().getItemHandler().getSlots())
                .isEqualTo(AssemblyControllerBlockEntity.TOTAL_SLOTS_MAX);

        pair.patternManager().getItemHandler()
                .setStackInSlot(2, new ItemStack(AssemblyTestFixtures.CAPACITY, 10));
        pair.patternManager().ensurePatternCapacity();
        assertThat(pair.patternManager().getItemHandler().getSlots())
                .isEqualTo(AssemblyControllerBlockEntity.TOTAL_SLOTS_MAX);
    }

    // ===== 槽位校验 =====

    /** 升级槽只接受对应升级卡;样板槽只接受支持的样板. */
    @Test
    void testSlotValidation() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();
        int patternSlot = AssemblyControllerBlockEntity.UPGRADE_SLOTS;

        assertThat(handler.isItemValid(0, new ItemStack(AssemblyTestFixtures.PARALLEL))).isTrue();
        assertThat(handler.isItemValid(0, new ItemStack(AssemblyTestFixtures.SPEED))).isFalse();
        assertThat(handler.isItemValid(1, new ItemStack(AssemblyTestFixtures.SPEED))).isTrue();
        assertThat(handler.isItemValid(2, new ItemStack(AssemblyTestFixtures.CAPACITY))).isTrue();
        assertThat(handler.isItemValid(3, new ItemStack(AssemblyTestFixtures.PARALLEL))).isFalse();
        assertThat(handler.isItemValid(4, new ItemStack(AssemblyTestFixtures.AUTO_UPLOAD))).isTrue();
        assertThat(handler.isItemValid(5, new ItemStack(AssemblyTestFixtures.PARALLEL))).isFalse();

        assertThat(handler.isItemValid(patternSlot, AEItems.CRAFTING_PATTERN.stack())).isTrue();
        assertThat(handler.isItemValid(patternSlot, AEItems.PROCESSING_PATTERN.stack())).isFalse();
        assertThat(handler.isItemValid(patternSlot, new ItemStack(Items.STONE))).isFalse();
        assertThat(handler.isItemValid(patternSlot, ItemStack.EMPTY)).isTrue();

        assertThat(handler.isItemValid(-1, AEItems.CRAFTING_PATTERN.stack())).isFalse();
        assertThat(handler.isItemValid(handler.getSlots(), AEItems.CRAFTING_PATTERN.stack())).isFalse();
    }

    /** setStackInSlot 对非法物品静默拒绝. */
    @Test
    void testSetStackInSlotRejectsInvalid() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();
        int patternSlot = AssemblyControllerBlockEntity.UPGRADE_SLOTS;

        handler.setStackInSlot(patternSlot, new ItemStack(Items.STONE));
        assertThat(handler.getStackInSlot(patternSlot).isEmpty()).isTrue();

        handler.setStackInSlot(patternSlot, AEItems.CRAFTING_PATTERN.stack());
        assertThat(handler.getStackInSlot(patternSlot).is(AEItems.CRAFTING_PATTERN.asItem())).isTrue();
    }

    /** 槽位上限:升级槽按物品最大堆叠,样板槽恒为 1. */
    @Test
    void testSlotLimits() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();

        assertThat(handler.getSlotLimit(0)).isEqualTo(64); // 空槽默认 64
        handler.setStackInSlot(0, new ItemStack(AssemblyTestFixtures.PARALLEL, 1));
        assertThat(handler.getSlotLimit(0)).isEqualTo(64); // 测试物品最大堆叠 64
        assertThat(handler.getSlotLimit(AssemblyControllerBlockEntity.UPGRADE_SLOTS)).isEqualTo(1);
    }

    // ===== 脏标记与刷新 =====

    /** 样板槽变动置脏;升级槽变动不置脏. */
    @Test
    void testPatternsDirtyFlag() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();

        assertThat(pair.patternManager().isPatternsDirty()).isFalse();

        handler.setStackInSlot(0, new ItemStack(AssemblyTestFixtures.PARALLEL, 1));
        assertThat(pair.patternManager().isPatternsDirty()).as("升级槽变动不置脏").isFalse();

        handler.setStackInSlot(AssemblyControllerBlockEntity.UPGRADE_SLOTS, AEItems.CRAFTING_PATTERN.stack());
        assertThat(pair.patternManager().isPatternsDirty()).as("样板槽变动置脏").isTrue();
    }

    /** 置脏后下一 tick 清标记,再下一 tick 通知控制器刷新接口样板列表. */
    @Test
    void testTickRefreshNotifiesController() {
        var pair = AssemblyTestFixtures.newPair();

        pair.patternManager().tickRefresh();
        verify(pair.controller(), never()).refreshInterfaceServices();

        pair.patternManager().markPatternsDirty();
        pair.patternManager().tickRefresh(); // 消费脏标记,启动 1 tick 倒计时
        assertThat(pair.patternManager().isPatternsDirty()).isFalse();
        verify(pair.controller(), never()).refreshInterfaceServices();

        pair.patternManager().tickRefresh(); // 倒计时归零,通知刷新
        verify(pair.controller(), times(1)).refreshInterfaceServices();
    }

    // ===== 扩容卡取出守卫 =====

    /** 缩容会丢非空样板槽时拒绝取出扩容卡. */
    @Test
    void testExtractCapacityCardGuardedByOccupiedSlots() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();
        handler.setStackInSlot(2, new ItemStack(AssemblyTestFixtures.CAPACITY, 3)); // 35 页

        // 在第 30 页放一块样板(取出 2 张卡后仅剩 15 页,该槽会被截掉)
        int occupiedSlot = AssemblyControllerBlockEntity.UPGRADE_SLOTS
                + 30 * AssemblyControllerBlockEntity.PATTERN_SLOTS_PER_PAGE;
        handler.setStackInSlot(occupiedSlot, AEItems.CRAFTING_PATTERN.stack());

        // 取出 2 张(3→1,35 页 → 15 页)会丢样板 → 拒绝
        assertThat(handler.extractItem(2, 2, false).isEmpty()).isTrue();
        assertThat(handler.getStackInSlot(2).getCount()).isEqualTo(3);

        // simulate 模式不做守卫(与常规物品处理器语义一致)
        assertThat(handler.extractItem(2, 2, true).getCount()).isEqualTo(2);
    }

    /** 缩容区域无样板时允许取出扩容卡. */
    @Test
    void testExtractCapacityCardAllowedWhenSlotsEmpty() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();
        handler.setStackInSlot(2, new ItemStack(AssemblyTestFixtures.CAPACITY, 3)); // 35 页

        var extracted = handler.extractItem(2, 2, false);
        assertThat(extracted.getCount()).isEqualTo(2);
        assertThat(handler.getStackInSlot(2).getCount()).isEqualTo(1);
    }

    // ===== NBT 序列化 =====

    /** serializeNBT 按设计返回空 NBT(真实数据写入 SavedData,level 为 null 时短路). */
    @Test
    void testSerializeNbtReturnsEmpty() {
        var pair = AssemblyTestFixtures.newPair();
        pair.patternManager().getItemHandler()
                .setStackInSlot(AssemblyControllerBlockEntity.UPGRADE_SLOTS, AEItems.CRAFTING_PATTERN.stack());

        assertThat(pair.patternManager().getItemHandler().serializeNBT().isEmpty()).isTrue();
    }

    /** deserializeNBT:Size 按基础/上限钳制,非法物品在加载时被过滤. */
    @Test
    void testDeserializeNbtClampsAndFilters() {
        var pair = AssemblyTestFixtures.newPair();
        var handler = pair.patternManager().getItemHandler();

        // Size 缺失 → 基础槽数
        handler.deserializeNBT(new CompoundTag());
        assertThat(handler.getSlots()).isEqualTo(AssemblyControllerBlockEntity.TOTAL_SLOTS_BASE);

        // Size 超上限 → 钳到最大
        var huge = new CompoundTag();
        huge.putInt("Size", Integer.MAX_VALUE);
        handler.deserializeNBT(huge);
        assertThat(handler.getSlots()).isEqualTo(AssemblyControllerBlockEntity.TOTAL_SLOTS_MAX);

        // 样板槽中的非法物品(普通物品)在加载时被过滤
        var withInvalid = new CompoundTag();
        withInvalid.putInt("Size", AssemblyControllerBlockEntity.TOTAL_SLOTS_BASE);
        var list = new net.minecraft.nbt.ListTag();
        var entry = new CompoundTag();
        entry.putInt("Slot", AssemblyControllerBlockEntity.UPGRADE_SLOTS);
        new ItemStack(Items.STONE).save(entry);
        list.add(entry);
        withInvalid.put("Items", list);
        handler.deserializeNBT(withInvalid);
        assertThat(handler.getStackInSlot(AssemblyControllerBlockEntity.UPGRADE_SLOTS).isEmpty()).isTrue();
    }

    /** level 为 null(未入世界)时可用样板列表为空. */
    @Test
    void testAvailablePatternsEmptyWithoutLevel() {
        var pair = AssemblyTestFixtures.newPair();
        pair.patternManager().getItemHandler()
                .setStackInSlot(AssemblyControllerBlockEntity.UPGRADE_SLOTS, AEItems.CRAFTING_PATTERN.stack());

        assertThat(pair.patternManager().getAvailablePatterns()).isEmpty();
    }

    /** load/save 走方块实体 NBT 通道:save 写入 items 子标签,load 读回(level 为 null 时直接反序列化). */
    @Test
    void testLoadSaveRoundTrip() {
        var pair = AssemblyTestFixtures.newPair();
        var data = new CompoundTag();
        pair.patternManager().save(data);
        assertThat(data.contains("items", CompoundTag.TAG_COMPOUND)).isTrue();

        // serializeNBT 按设计为空 → load 后容量回到基础槽数
        pair.patternManager().load(data);
        assertThat(pair.patternManager().getItemHandler().getSlots())
                .isEqualTo(AssemblyControllerBlockEntity.TOTAL_SLOTS_BASE);
    }
}
