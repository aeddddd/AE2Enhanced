package com.github.aeddddd.ae2enhanced.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.items.ItemStackHandler;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AECableType;

/**
 * {@link AssemblyControllerBlockEntity} 单元测试.
 * <p>离线环境无真实 AE2 网格,主节点由 AE2 在构造期创建但不会 ready,
 * 因此此处覆盖：初始状态、成形/投影状态机、升级卡到属性的委托、
 * 产物缓冲与批量门禁、样板批量信息分类缓存、动作来源回退、
 * NBT 与客户端同步标签的往返,以及 serverTick 的离线安全分支.</p>
 */
class AssemblyControllerBlockEntityTest {

    private static final BlockPos POS = new BlockPos(10, 64, -10);

    @BeforeAll
    static void bootstrap() {
        BlockEntityTestSupport.bootstrap();
    }

    private static AssemblyControllerBlockEntity newController() {
        return new AssemblyControllerBlockEntity(POS, Blocks.STONE.defaultBlockState());
    }

    /** 未成形、无升级卡时的默认状态. */
    @Test
    void testInitialState() {
        AssemblyControllerBlockEntity be = newController();
        assertFalse(be.isFormed());
        assertFalse(be.isShowingStructureProjection());
        assertFalse(be.isNetworkActive());
        assertFalse(be.isNetworkPowered());
        assertFalse(be.isVisibleInTerminal());
        assertEquals(0, be.getJobCount());
        assertEquals(64, be.getParallelCap());
        assertEquals(20, be.getCraftingTicks());
        assertEquals(AssemblyControllerBlockEntity.PATTERN_PAGES_BASE, be.getPatternPages());
        assertEquals(AssemblyControllerBlockEntity.PATTERN_PAGES_BASE
                * AssemblyControllerBlockEntity.PATTERN_SLOTS_PER_PAGE, be.getPatternSlotCount());
        assertFalse(be.hasAutoUploadUpgrade());
        assertTrue(be.canBatch());
        assertFalse(be.isBusy());
        assertEquals(POS, be.getControllerPos());
        assertEquals(AssemblyControllerBlockEntity.TOTAL_SLOTS_MAX, be.getItemHandler().getSlots());
    }

    /** 线缆连接类型恒为 SMART（任意结构方块均可并网）. */
    @Test
    void testCableConnectionType() {
        AssemblyControllerBlockEntity be = newController();
        for (Direction dir : Direction.values()) {
            assertEquals(AECableType.SMART, be.getCableConnectionType(dir));
        }
    }

    /** level 为 null 时结构查询返回 null,网格句柄为 null（节点未就绪）. */
    @Test
    void testStructureAndGridWithoutLevel() {
        AssemblyControllerBlockEntity be = newController();
        assertNull(be.getStructure());
        assertNull(be.getGrid());
        assertNull(be.resolveNode(null));
        // 终端样板背包视图可离线构造
        assertNotNull(be.getTerminalPatternInventory());
    }

    /** 结构投影切换：未成形时翻转,成形后强制关闭. */
    @Test
    void testToggleStructureProjection() {
        AssemblyControllerBlockEntity be = newController();
        be.toggleStructureProjection();
        assertTrue(be.isShowingStructureProjection());
        be.toggleStructureProjection();
        assertFalse(be.isShowingStructureProjection());

        // 成形后投影被强制关闭且不再可切换
        be.toggleStructureProjection();
        be.setFormed(true);
        assertTrue(be.isShowingStructureProjection());
        be.toggleStructureProjection();
        assertFalse(be.isShowingStructureProjection());
        be.toggleStructureProjection();
        assertFalse(be.isShowingStructureProjection());
    }

    /** 成形状态机：assemble/disassemble 幂等,disassemble 清空批量状态. */
    @Test
    void testAssembleDisassemble() {
        AssemblyControllerBlockEntity be = newController();
        // 未成形时 disassemble 为无操作
        be.disassemble();
        assertFalse(be.isFormed());

        be.assemble();
        assertTrue(be.isFormed());
        assertTrue(be.isVisibleInTerminal());

        // 重复 assemble 不报错且状态不变
        be.assemble();
        assertTrue(be.isFormed());

        // disassemble 触发 clearState:batchBusy 被重置
        be.setBatchBusy(true);
        assertFalse(be.canBatch());
        be.disassemble();
        assertFalse(be.isFormed());
        assertTrue(be.canBatch());
    }

    /** 升级卡槽位到并行/速度/页数/自动上传属性的委托换算. */
    @Test
    void testUpgradeDelegation() {
        AssemblyControllerBlockEntity be = newController();
        ItemStackHandler handler = be.getItemHandler();

        handler.setStackInSlot(0, new ItemStack(BlockEntityTestSupport.parallelUpgrade, 3));
        assertEquals(64L * 32 * 32 * 32, be.getParallelCap());

        handler.setStackInSlot(1, new ItemStack(BlockEntityTestSupport.speedUpgrade, 2));
        assertEquals(5, be.getCraftingTicks());

        handler.setStackInSlot(2, new ItemStack(BlockEntityTestSupport.capacityUpgrade, 2));
        assertEquals(25, be.getPatternPages());
        assertEquals(25 * AssemblyControllerBlockEntity.PATTERN_SLOTS_PER_PAGE, be.getPatternSlotCount());

        handler.setStackInSlot(4, new ItemStack(BlockEntityTestSupport.autoUploadUpgrade, 1));
        assertTrue(be.hasAutoUploadUpgrade());
    }

    /** 升级卡槽位拒绝错误物品,槽位保持为空,属性维持默认. */
    @Test
    void testUpgradeSlotRejectsWrongItem() {
        AssemblyControllerBlockEntity be = newController();
        ItemStackHandler handler = be.getItemHandler();

        // 槽位 0 只接受并行升级卡
        handler.setStackInSlot(0, new ItemStack(BlockEntityTestSupport.speedUpgrade, 1));
        assertTrue(handler.getStackInSlot(0).isEmpty());
        assertEquals(64, be.getParallelCap());

        // 样板槽拒绝普通物品
        handler.setStackInSlot(AssemblyControllerBlockEntity.UPGRADE_SLOTS, new ItemStack(Items.DIAMOND, 1));
        assertTrue(handler.getStackInSlot(AssemblyControllerBlockEntity.UPGRADE_SLOTS).isEmpty());
    }

    /** 批量门禁：batchBusy 与批量冷却共同决定 canBatch. */
    @Test
    void testBatchGate() {
        AssemblyControllerBlockEntity be = newController();
        assertTrue(be.canBatch());

        be.setBatchBusy(true);
        assertFalse(be.canBatch());
        be.setBatchBusy(false);
        assertTrue(be.canBatch());

        // 重置批量冷却(0 张速度卡 = 20 tick)期间不再接受新批量
        be.resetBatchCooldown();
        assertFalse(be.canBatch());
    }

    /** 产物缓冲：空堆/零数量被忽略,容量上限按配置(默认 4096)判定. */
    @Test
    void testPendingOutputs() {
        AssemblyControllerBlockEntity be = newController();
        assertTrue(be.canAcceptRealBatch(4096));
        assertFalse(be.canAcceptRealBatch(4097));

        // 空堆与零数量 GenericStack 不入缓冲
        be.addPendingOutput(ItemStack.EMPTY);
        be.addPendingOutput(new GenericStack(AEItemKey.of(Items.DIAMOND), 0));
        assertTrue(be.canAcceptRealBatch(4096));

        be.addPendingOutput(new ItemStack(Items.DIAMOND, 3));
        assertFalse(be.canAcceptRealBatch(4096));
        assertTrue(be.canAcceptRealBatch(4095));
    }

    /** 样板批量信息：无剩余物输入为虚拟轨道,含容器物/剩余物为真实轨道,结果按样板缓存. */
    @Test
    void testPatternBatchInfoClassificationAndCache() {
        AssemblyControllerBlockEntity be = newController();

        // 无输入 → 虚拟轨道
        IPatternDetails virtual = mock(IPatternDetails.class);
        when(virtual.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        AssemblyControllerBlockEntity.PatternBatchInfo virtualInfo = be.getPatternBatchInfo(virtual);
        assertTrue(virtualInfo.virtual);
        // 同一样板第二次查询命中缓存,返回同一实例
        assertSame(virtualInfo, be.getPatternBatchInfo(virtual));

        // 输入存在剩余物(如水桶配方返回空桶) → 真实轨道
        IPatternDetails.IInput input = mock(IPatternDetails.IInput.class);
        GenericStack possible = new GenericStack(AEItemKey.of(Items.WATER_BUCKET), 1);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { possible });
        when(input.getRemainingKey(any())).thenReturn(AEItemKey.of(Items.BUCKET));
        IPatternDetails real = mock(IPatternDetails.class);
        when(real.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });

        assertFalse(be.getPatternBatchInfo(real).virtual);
        assertNotSame(virtualInfo, be.getPatternBatchInfo(real));
    }

    /** 动作来源：默认回退到机器源,Mixin 设置的临时来源优先. */
    @Test
    void testActionSourceFallback() {
        AssemblyControllerBlockEntity be = newController();
        IActionSource machineSource = be.getEffectiveActionSource();
        assertNotNull(machineSource);

        IActionSource override = mock(IActionSource.class);
        be.setCurrentActionSource(override);
        assertSame(override, be.getEffectiveActionSource());

        be.setCurrentActionSource(null);
        assertNotNull(be.getEffectiveActionSource());
    }

    /** resolveNode：优先使用已就绪的外部节点,否则回退自身主节点(未就绪时为 null). */
    @Test
    void testResolveNode() {
        AssemblyControllerBlockEntity be = newController();

        IManagedGridNode notReady = mock(IManagedGridNode.class);
        when(notReady.isReady()).thenReturn(false);
        assertNull(be.resolveNode(notReady));

        IManagedGridNode ready = mock(IManagedGridNode.class);
        when(ready.isReady()).thenReturn(true);
        assertSame(ready, be.resolveNode(ready));
    }

    /** 非分子装配室样板(处理样板等)被防御性过滤,无需网格即可拒绝. */
    @Test
    void testPushPatternRejectsUnsupportedPattern() {
        AssemblyControllerBlockEntity be = newController();
        IPatternDetails unsupported = mock(IPatternDetails.class);
        assertFalse(be.pushPattern(unsupported, null));
        assertFalse(be.pushPattern(unsupported, null, null));
        assertFalse(be.pushPatternBatch(unsupported, null, null, 16));
        // 被拒绝的样板不产生任务
        assertEquals(0, be.getJobCount());
    }

    /** NBT 往返：成形/投影/网络状态/产物缓冲全部持久化. */
    @Test
    void testNbtRoundTrip() {
        AssemblyControllerBlockEntity source = newController();
        // 网络状态仅经客户端同步标签写入,借此设置后验证持久化
        CompoundTag sync = new CompoundTag();
        sync.putBoolean("networkActive", true);
        sync.putBoolean("networkPowered", true);
        source.handleUpdateTag(sync);
        // 注意 handleUpdateTag 经 super.load → loadTag 会按缺失键重置 formed,需在之后再置成形
        source.setFormed(true);
        source.addPendingOutput(new ItemStack(Items.DIAMOND, 7));

        CompoundTag tag = new CompoundTag();
        source.saveAdditional(tag);
        assertTrue(tag.getBoolean("formed"));
        assertTrue(tag.getBoolean("networkActive"));

        AssemblyControllerBlockEntity target = newController();
        target.loadTag(tag);
        assertTrue(target.isFormed());
        assertFalse(target.isShowingStructureProjection());
        assertTrue(target.isNetworkActive());
        assertTrue(target.isNetworkPowered());
        // pendingOutputs 随 NBT 恢复,缓冲占用 1 格
        assertFalse(target.canAcceptRealBatch(4096));
        assertTrue(target.canAcceptRealBatch(4095));
    }

    /** getUpdateTag/handleUpdateTag 客户端同步往返. */
    @Test
    void testUpdateTagRoundTrip() {
        AssemblyControllerBlockEntity source = newController();
        source.setFormed(true);

        CompoundTag updateTag = source.getUpdateTag();
        assertTrue(updateTag.getBoolean("formed"));
        assertFalse(updateTag.getBoolean("networkPowered"));

        AssemblyControllerBlockEntity target = newController();
        target.handleUpdateTag(updateTag);
        assertTrue(target.isFormed());
        assertFalse(target.isShowingStructureProjection());
        assertFalse(target.isNetworkActive());
    }

    /** handleUpdateTag 经 super.load → loadTag 具有覆盖语义：空标签等价于全量默认值. */
    @Test
    void testHandleUpdateTagWithEmptyTagResetsState() {
        AssemblyControllerBlockEntity be = newController();
        be.setFormed(true);
        // super.handleUpdateTag → load → loadTag 对缺失布尔键按 false 覆盖
        be.handleUpdateTag(new CompoundTag());
        assertFalse(be.isFormed());
    }

    /** serverTick 离线安全：level 为 null 直接返回；mock 服务端 level 下未成形不触碰结构/网格. */
    @Test
    void testServerTickOffline() {
        AssemblyControllerBlockEntity be = newController();
        // level 为 null:无操作
        be.serverTick();
        assertFalse(be.isFormed());

        // mock 服务端 level:控制器位置非本模组控制器方块 → getStructure 为 null,跳过结构校验
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        when(level.getBlockState(any())).thenReturn(Blocks.AIR.defaultBlockState());
        be.setLevel(level);

        be.serverTick();
        be.serverTick();
        assertFalse(be.isFormed());
        assertFalse(be.isNetworkActive());
    }
}
