package com.github.aeddddd.ae2enhanced.centralinterface.handler.nuclearcraft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.storage.data.IAEItemStack;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.items.IItemHandler;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRef;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import com.github.aeddddd.ae2enhanced.test.util.FakeItemHandler;

/**
 * {@link NuclearCraftLegacyMachineHandler} 测试。
 *
 * <p>测试环境没有 NuclearCraft，所有反射路径都不可能成功（{@code AVAILABLE == false}），
 * 因此覆盖面分三块：</p>
 * <ul>
 *     <li><b>不依赖反射的部分</b>：blockId 匹配集合、能力集合、槽位→角色映射
 *     （{@link NuclearCraftLegacyMachineHandler#slotRoles} / {@code #buildCatalog}）、
 *     收集/回退/判定的纯逻辑（{@code collect} / {@code revertAllInputs} /
 *     {@code hasFilledOutputSlot} / {@code hasBatchMaterialInInput}）；
 *     处理状态反射回退链（{@link NuclearCraftLegacyMachineHandler#isProcessingNC}）；
 *     以及把 {@code NonNullList} 库存包成 {@code IItemHandler} 的
 *     {@link NuclearCraftLegacyMachineHandler.ReflectedInventory}；</li>
 *     <li><b>依赖目录的部分</b>：用测试替身覆盖 {@code catalogFor}（{@code idleGate} 走生产实现），
 *     把目录换成 {@link FakeItemHandler} 或 {@link NuclearCraftLegacyMachineHandler.ReflectedInventory}
 *     驱动的真实目录，于是框架提供的 pushMaterials / clearOutputs 与本类覆盖的
 *     isIdle / hasFinished / collectProducts / revertMaterials 全部走生产代码路径；</li>
 *     <li><b>目录不可用</b>：无 NC（反射句柄缺失）时目标判为无效，各项按框架契约降级。</li>
 * </ul>
 */
public class NuclearCraftLegacyMachineHandlerTest {

    private static final BlockPos POS = new BlockPos(5, 64, 5);

    private TestableHandler handler;
    private World world;
    private TileEntity tile;

    @BeforeAll
    public static void boot() {
        // 只需注册 MC 的物品注册表（构造 ItemStack 用）；刻意不引导 AE2 的 Api：
        // 本类所有断言都不需要 AE2 单例，预期产物用 IAEItemStack mock 代替（见 expected()），
        // 少一层无头环境下最脆弱的初始化。
        // 测试环境没有安装 NuclearCraft，Loader.isModLoaded 返回 false，
        // 处理器静态初始化因此不做任何反射解析。
        net.minecraft.init.Bootstrap.register();
    }

    @BeforeEach
    public void setUp() {
        this.handler = new TestableHandler();
        this.world = mock(World.class);
        this.tile = mock(TileEntity.class);
        when(world.getTileEntity(POS)).thenReturn(tile);
    }

    /**
     * 绕过反射目录的测试替身：生产实现全部保留，只有"目录从哪来"由用例注入。
     *
     * <p>{@code idleGate} 不注入——它本身就是对 {@link NuclearCraftLegacyMachineHandler#isProcessingNC}
     * 的转发，用例用带 {@code isProcessing()}/{@code getIsProcessing()} 的测试 TileEntity 驱动它。</p>
     */
    private static final class TestableHandler extends NuclearCraftLegacyMachineHandler {

        private ISlotCatalog catalog;

        void setCatalog(ISlotCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        protected ISlotCatalog catalogFor(World world, BlockPos pos) {
            return this.catalog;
        }
    }

    private static InventoryCrafting crafting(ItemStack... stacks) {
        InventoryCrafting inv = new InventoryCrafting(mock(Container.class), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            inv.setInventorySlotContents(i, stacks[i]);
        }
        return inv;
    }

    /** 2.19a 可升级机器式布局：输入 {0,1,2}、输出 {3,4}、末尾两个升级槽 {5,6}。 */
    private ISlotCatalog installLegacyStyleCatalog(IItemHandler itemHandler) {
        return installCatalog(itemHandler, 7, new int[] {0, 1, 2}, new int[] {3, 4});
    }

    /** 单输入槽布局：便于构造"放不下"的原子性用例。 */
    private ISlotCatalog installSingleInputCatalog(IItemHandler itemHandler) {
        return installCatalog(itemHandler, 2, new int[] {0}, new int[] {1});
    }

    private ISlotCatalog installCatalog(IItemHandler itemHandler, int slotCount, int[] inputSlots, int[] outputSlots) {
        ISlotCatalog catalog = NuclearCraftLegacyMachineHandler.buildCatalog(
                itemHandler, slotCount, inputSlots, outputSlots);
        this.handler.setCatalog(catalog);
        return catalog;
    }

    /** 测试用机器：插入合法性只看被屏蔽的槽位，处理状态由字段控制。 */
    public static class ProcessingTile extends TileEntity {

        public int blockedSlot = -1;
        public boolean processing;
        public int dirtyCalls;

        public boolean isItemValidForSlot(int slot, ItemStack stack) {
            return slot != this.blockedSlot;
        }

        public boolean isProcessing() {
            return this.processing;
        }

        @Override
        public void markDirty() {
            this.dirtyCalls++;
        }
    }

    /** Overhauled 式命名：只有 {@code getIsProcessing()}。 */
    public static class OverhauledTile extends TileEntity {

        public boolean processing;

        public boolean getIsProcessing() {
            return this.processing;
        }
    }

    /** 两个方法同时存在时以 {@code isProcessing()} 为准（与旧实现的查找顺序一致）。 */
    public static class BothNamesTile extends TileEntity {

        public boolean isProcessing() {
            return true;
        }

        public boolean getIsProcessing() {
            return false;
        }
    }

    private static Method isItemValidMethod() throws Exception {
        return ProcessingTile.class.getMethod("isItemValidForSlot", int.class, ItemStack.class);
    }

    private static NonNullList<ItemStack> inventoryOf(int size) {
        return NonNullList.withSize(size, ItemStack.EMPTY);
    }

    private NuclearCraftLegacyMachineHandler.ReflectedInventory reflectedInventory(NonNullList<ItemStack> stacks,
            TileEntity machine) throws Exception {
        return new NuclearCraftLegacyMachineHandler.ReflectedInventory(machine, stacks, isItemValidMethod());
    }

    /**
     * 样板声明的预期产物.
     *
     * <p>生产代码只用到 {@code getStackSize()} 与 {@code createItemStack()}，因此用 mock 即可精确构造；
     * 这样测试不必引导 AE2 的 Api 单例。</p>
     */
    private static IAEItemStack expected(ItemStack template) {
        IAEItemStack stack = mock(IAEItemStack.class);
        when(stack.getStackSize()).thenReturn((long) template.getCount());
        when(stack.createItemStack()).thenReturn(template);
        return stack;
    }

    // ------------------------------------------------------------------
    // blockId 匹配与能力
    // ------------------------------------------------------------------

    /** {@code nuclearcraft:} 命名空间整体匹配：NC 把机器/方块都注册在该前缀下，无法在匹配阶段收窄。 */
    @Test
    public void testCanHandleMatchesNuclearCraftNamespace() {
        assertThat(handler.canHandle("nuclearcraft:processor")).isTrue();
        assertThat(handler.canHandle("nuclearcraft:")).isTrue();
        assertThat(handler.canHandle("minecraft:furnace")).isFalse();
        assertThat(handler.canHandle("nuclearcraftx:processor")).isFalse();
        assertThat(handler.canHandle("")).isFalse();
        assertThat(handler.canHandle(null)).isFalse();
    }

    /** 仅物理发配，不参与虚拟批量。 */
    @Test
    public void testGetCapabilitiesPhysicalOnly() {
        assertThat(handler.getCapabilities()).containsExactly(HandlerCapabilities.PHYSICAL);
        assertThat(handler.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isFalse();
    }

    // ------------------------------------------------------------------
    // 槽位 → 角色映射
    // ------------------------------------------------------------------

    /** 2.19a/Overhauled 可升级机器：输入 → INPUT、输出 → OUTPUT、末尾两个升级槽 → UPGRADE。 */
    @Test
    public void testSlotRolesLegacyLayout() {
        SlotRole[] roles = NuclearCraftLegacyMachineHandler.slotRoles(7, new int[] {0, 1, 2}, new int[] {3, 4});
        assertThat(roles).containsExactly(
                SlotRole.INPUT, SlotRole.INPUT, SlotRole.INPUT,
                SlotRole.OUTPUT, SlotRole.OUTPUT,
                SlotRole.UPGRADE, SlotRole.UPGRADE);
    }

    /** 不可升级机器（如单进单出的加工机）：库存只有输入+输出，没有升级槽。 */
    @Test
    public void testSlotRolesWithoutUpgradeSlots() {
        assertThat(NuclearCraftLegacyMachineHandler.slotRoles(2, new int[] {0}, new int[] {1}))
                .containsExactly(SlotRole.INPUT, SlotRole.OUTPUT);
        assertThat(NuclearCraftLegacyMachineHandler.slotRoles(0, new int[0], new int[0])).isEmpty();
    }

    /** 越界下标（数组比库存长 / 负数）既不得进目录，也不得抛异常。 */
    @Test
    public void testSlotRolesIgnoresOutOfRangeIndices() {
        assertThat(NuclearCraftLegacyMachineHandler.slotRoles(2, new int[] {0, 9, -1}, new int[] {1, 5}))
                .containsExactly(SlotRole.INPUT, SlotRole.OUTPUT);
    }

    /** 同一下标同时出现在两表中时按 INPUT 处理：保住推料目标，非本批物品仍由收集第 3 阶段带走。 */
    @Test
    public void testSlotRolesInputWinsOnOverlap() {
        assertThat(NuclearCraftLegacyMachineHandler.slotRoles(2, new int[] {0}, new int[] {0, 1}))
                .containsExactly(SlotRole.INPUT, SlotRole.OUTPUT);
    }

    /** 槽位表为 null（读不到定义）时不抛异常，全部标为 UPGRADE（= 不触碰）。 */
    @Test
    public void testSlotRolesHandlesNullTables() {
        assertThat(NuclearCraftLegacyMachineHandler.slotRoles(3, null, null))
                .containsExactly(SlotRole.UPGRADE, SlotRole.UPGRADE, SlotRole.UPGRADE);
    }

    /** 目录的槽位顺序稳定、推料目标只有输入槽。 */
    @Test
    public void testCatalogRolesAndPushTargets() {
        ISlotCatalog catalog = installLegacyStyleCatalog(new FakeItemHandler(7));

        assertThat(catalog.rolesKnown()).isTrue();
        assertThat(catalog.inputSlots()).hasSize(3);
        assertThat(catalog.outputSlots()).hasSize(2);
        assertThat(catalog.pushTargets()).hasSize(3);
        assertThat(catalog.slots()).extracting(SlotRef::index).containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(Arrays.asList(
                catalog.slots().get(5).role(), catalog.slots().get(6).role()))
                .containsExactly(SlotRole.UPGRADE, SlotRole.UPGRADE);
    }

    // ------------------------------------------------------------------
    // 目录不可用时的降级语义
    // ------------------------------------------------------------------

    /** 目录为 null（无 NC / 方块类型不符 / 反射句柄缺失）时，框架契约全部按"目标无效"降级。 */
    @Test
    public void testNullCatalogDegradesSafely() {
        handler.setCatalog(null);
        InventoryCrafting inv = crafting(new ItemStack(Items.APPLE, 1));

        assertThat(handler.isValidTarget(world, POS)).isFalse();
        assertThat(handler.canStart(world, POS, inv, null)).isFalse();
        assertThat(handler.pushMaterials(world, POS, inv, null, null)).isFalse();
        assertThat(handler.collectProducts(world, POS, null, null, null, null)).isNotNull().isEmpty();
        assertThat(handler.clearOutputs(world, POS, null, null)).isNotNull().isEmpty();
        assertThat(handler.revertMaterials(world, POS, null, null)).isNotNull().isEmpty();
        assertThat(handler.isIdle(world, POS, null, null)).isTrue();
        assertThat(handler.hasFinished(world, POS, null, null)).isTrue();
    }

    /** 空世界/空坐标不得抛异常（框架契约：目录为 null ⇒ 目标无效）。 */
    @Test
    public void testNullWorldAndPosDegradeSafely() {
        assertThat(handler.isValidTarget(null, POS)).isFalse();
        assertThat(handler.isValidTarget(world, null)).isFalse();
        assertThat(handler.collectProducts(null, null, null, null, null, null)).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------
    // 推料（框架两阶段原子实现，目录由本处理器给出）
    // ------------------------------------------------------------------

    /** 材料只进输入槽；输出槽与升级槽不被写入。 */
    @Test
    public void testPushMaterialsInsertsIntoInputSlotsOnly() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        installLegacyStyleCatalog(itemHandler);

        InventoryCrafting inv = crafting(new ItemStack(Items.APPLE, 2), new ItemStack(Items.COAL, 1));

        assertThat(handler.pushMaterials(world, POS, inv, null, null)).isTrue();
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(2);
        assertThat(itemHandler.getStackInSlot(1).getItem()).isEqualTo(Items.COAL);
        assertThat(itemHandler.getStackInSlot(3).isEmpty()).isTrue();
        assertThat(itemHandler.getStackInSlot(5).isEmpty()).isTrue();
    }

    /** 空配方视为推送成功，不触碰任何槽位。 */
    @Test
    public void testPushMaterialsEmptyIngredientsSucceeds() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        installLegacyStyleCatalog(itemHandler);

        assertThat(handler.pushMaterials(world, POS, crafting(), null, null)).isTrue();
        assertThat(itemHandler.getStackInSlot(0).isEmpty()).isTrue();
    }

    /** 唯一输入槽被异类物品占满时推送失败，且目标状态原封不动。 */
    @Test
    public void testPushMaterialsAtomicFailureLeavesNoTrace() {
        FakeItemHandler itemHandler = new FakeItemHandler(2);
        itemHandler.setStack(0, new ItemStack(Items.BRICK, 64));
        installSingleInputCatalog(itemHandler);

        assertThat(handler.pushMaterials(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null, null)).isFalse();
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.BRICK);
        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(64);
    }

    /**
     * 前一味材料放得下、后一味放不下时整体失败：模拟阶段不放行任何真实插入，
     * 因此不会出现"推了一半"的中间态（重构前的 {@code pushItemToInventory} 逐味返回 false
     * 会留下前一味，靠调用方的 revertMaterials 兜底清空全部输入槽，结果状态一致）。
     */
    @Test
    public void testPushMaterialsAtomicWhenLaterItemDoesNotFit() {
        FakeItemHandler itemHandler = new FakeItemHandler(2);
        itemHandler.setStack(0, new ItemStack(Items.APPLE, 63));
        installSingleInputCatalog(itemHandler);

        InventoryCrafting inv = crafting(new ItemStack(Items.APPLE, 2), new ItemStack(Items.DIAMOND, 1));

        assertThat(handler.pushMaterials(world, POS, inv, null, null)).isFalse();
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(63);
    }

    /**
     * 机器自身拒绝该槽位时材料落到下一个输入槽（旧实现 {@code pushItemToInventory} 的跳过语义）。
     *
     * <p>用 {@link NuclearCraftLegacyMachineHandler.ReflectedInventory} 驱动：它是真实机器库存
     * 的包装，插入前要过机器自己的 {@code isItemValidForSlot}。</p>
     */
    @Test
    public void testPushMaterialsSkipsSlotRejectedByMachine() throws Exception {
        NonNullList<ItemStack> stacks = inventoryOf(3);
        ProcessingTile machine = new ProcessingTile();
        machine.blockedSlot = 0;
        when(world.getTileEntity(POS)).thenReturn(machine);
        installCatalog(reflectedInventory(stacks, machine), 3, new int[] {0, 1}, new int[] {2});

        InventoryCrafting inv = crafting(new ItemStack(Items.APPLE, 2));

        assertThat(handler.pushMaterials(world, POS, inv, null, null)).isTrue();
        assertThat(stacks.get(0).isEmpty()).isTrue();
        assertThat(stacks.get(1).getItem()).isEqualTo(Items.APPLE);
        assertThat(stacks.get(1).getCount()).isEqualTo(2);
    }

    /**
     * 整堆放不下时不得拆到下一个槽：同型槽只剩 1 个空位而待推 2 个时，
     * 该槽整个拒绝（旧实现即如此），空着的第二个输入槽才能接整堆。
     */
    @Test
    public void testPushMaterialsDoesNotSplitStackAcrossSlots() throws Exception {
        NonNullList<ItemStack> stacks = inventoryOf(3);
        stacks.set(0, new ItemStack(Items.APPLE, 63));
        ProcessingTile machine = new ProcessingTile();
        when(world.getTileEntity(POS)).thenReturn(machine);
        installCatalog(reflectedInventory(stacks, machine), 3, new int[] {0, 1}, new int[] {2});

        assertThat(handler.pushMaterials(world, POS, crafting(new ItemStack(Items.APPLE, 2)), null, null)).isTrue();
        assertThat(stacks.get(0).getCount()).isEqualTo(63);
        assertThat(stacks.get(1).getCount()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // isIdle（覆盖框架默认）
    // ------------------------------------------------------------------

    /** 输出槽为空 → 不可收集；输入槽有料也不算可收集。 */
    @Test
    public void testIsIdleFollowsOutputSlotsOnly() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        installLegacyStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        assertThat(handler.isIdle(world, POS, inputs, null)).isFalse();

        itemHandler.setStack(0, new ItemStack(Items.COAL, 1)); // 输入槽有料、产物还没出
        assertThat(handler.isIdle(world, POS, inputs, null)).isFalse();

        itemHandler.setStack(3, new ItemStack(Items.DIAMOND, 1));
        assertThat(handler.isIdle(world, POS, inputs, null)).isTrue();
        // 只判空，不做任何抽取
        assertThat(itemHandler.getStackInSlot(3).getCount()).isEqualTo(1);
    }

    /** 机器仍在处理时即便输出槽有内容也不可收集（走生产代码的反射回退链）。 */
    @Test
    public void testIsIdleFalseWhileMachineWorking() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(3, new ItemStack(Items.DIAMOND, 1));
        installLegacyStyleCatalog(itemHandler);

        ProcessingTile machine = new ProcessingTile();
        machine.processing = true;
        when(world.getTileEntity(POS)).thenReturn(machine);

        assertThat(handler.isIdle(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /**
     * 输出槽里的物品即便与本批材料同型也算"可收集"。
     *
     * <p>这是覆盖框架默认的关键点：框架默认的产物判据会把与本批材料同型的物品排除，
     * 于是本机会一直判"未空闲"直到超时（而本类的收集会把它收走，两者必须一致）。</p>
     */
    @Test
    public void testIsIdleTrueWhenOutputHoldsBatchMaterial() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(3, new ItemStack(Items.COAL, 1));
        installLegacyStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        assertThat(handler.isIdle(world, POS, inputs, null)).isTrue();
    }

    /** 推料保护窗口内一律判为未空闲（推料与机器开工不在同一 tick）。 */
    @Test
    public void testIsIdleRespectsPushGrace() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(3, new ItemStack(Items.DIAMOND, 1));
        installLegacyStyleCatalog(itemHandler);

        TargetSession session = mock(TargetSession.class);
        when(session.isPushGraceElapsed(anyLong(), anyInt())).thenReturn(false);
        assertThat(handler.isIdle(world, POS, Collections.emptyList(), session)).isFalse();

        when(session.isPushGraceElapsed(anyLong(), anyInt())).thenReturn(true);
        assertThat(handler.isIdle(world, POS, Collections.emptyList(), session)).isTrue();
    }

    // ------------------------------------------------------------------
    // hasFinished（覆盖框架默认）
    // ------------------------------------------------------------------

    /** 机器正在处理时不算完成（NC 此时已把输入搬进内部进度缓冲、产物还没落槽）。 */
    @Test
    public void testHasFinishedFalseWhileMachineWorking() {
        installLegacyStyleCatalog(new FakeItemHandler(7));

        ProcessingTile machine = new ProcessingTile();
        machine.processing = true;
        when(world.getTileEntity(POS)).thenReturn(machine);

        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /** Overhauled 的 {@code getIsProcessing()} 同样被识别为"正在处理"。 */
    @Test
    public void testHasFinishedFalseWhileOverhauledMachineWorking() {
        installLegacyStyleCatalog(new FakeItemHandler(7));

        OverhauledTile machine = new OverhauledTile();
        machine.processing = true;
        when(world.getTileEntity(POS)).thenReturn(machine);

        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /** 输入槽还留着本批材料时不算完成。 */
    @Test
    public void testHasFinishedFalseWithRemainingBatchInput() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(1, new ItemStack(Items.COAL, 2));
        installLegacyStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        assertThat(handler.hasFinished(world, POS, inputs, null)).isFalse();
    }

    /** 输出槽还有残留时不算完成。 */
    @Test
    public void testHasFinishedFalseWithProductLeft() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(3, new ItemStack(Items.DIAMOND, 1));
        installLegacyStyleCatalog(itemHandler);

        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /**
     * 输出槽里的物品即便与本批材料同型，也算"还没收完"。
     *
     * <p>框架默认用 {@code hasCollectableProduct} 判产物（排除与本批材料同型的物品），
     * 此处会返回 true 并提前结束会话，把那个物品永远留在机器里。</p>
     */
    @Test
    public void testHasFinishedFalseWhenOutputHoldsItemMatchingBatchMaterial() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(3, new ItemStack(Items.COAL, 1));
        installLegacyStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        assertThat(handler.hasFinished(world, POS, inputs, null)).isFalse();
    }

    /** 输入槽里的非本批物品不阻止"完成"判定（它由收集的第 3 阶段带走）。 */
    @Test
    public void testHasFinishedTrueWithForeignItemLeftInInputSlot() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(1, new ItemStack(Items.BRICK, 1));
        installLegacyStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        assertThat(handler.hasFinished(world, POS, inputs, null)).isTrue();
    }

    /** 输入已耗尽、输入槽无本批残留、输出槽为空 → 完成。 */
    @Test
    public void testHasFinishedTrueWhenDrained() {
        installLegacyStyleCatalog(new FakeItemHandler(7));

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        assertThat(handler.hasFinished(world, POS, inputs, null)).isTrue();
    }

    // ------------------------------------------------------------------
    // collectProducts（覆盖框架默认：三阶段 + 阶段一整槽取走）
    // ------------------------------------------------------------------

    /** 阶段一按"整槽取走"处理，不按样板声明的数量截断（框架默认会拆成两条）。 */
    @Test
    public void testCollectProductsTakesWholeExpectedSlot() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(3, new ItemStack(Items.APPLE, 5));
        installLegacyStyleCatalog(itemHandler);

        IAEItemStack[] expected = {expected(new ItemStack(Items.APPLE, 3))};
        List<ItemStack> collected = handler.collectProducts(world, POS, expected, Collections.emptyList(), null, null);

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(collected.get(0).getCount()).isEqualTo(5);
        assertThat(itemHandler.getStackInSlot(3).isEmpty()).isTrue();
    }

    /**
     * 阶段二收输出槽残余，阶段三收输入槽里不属于本批输入的物品（机器把产物吐回输入槽的情形），
     * 而本批材料绝不被当作产物收走；升级槽不被触碰。
     */
    @Test
    public void testCollectProductsThreePhases() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(0, new ItemStack(Items.COAL, 1));    // 本批材料残留 → 保留
        itemHandler.setStack(1, new ItemStack(Items.APPLE, 1));   // 输入槽异物 → 阶段 3 收走
        itemHandler.setStack(3, new ItemStack(Items.DIAMOND, 2)); // 输出槽残余 → 阶段 2 收走
        itemHandler.setStack(5, new ItemStack(Items.BRICK, 1));   // 升级槽 → 不碰
        installLegacyStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        List<ItemStack> collected = handler.collectProducts(world, POS, null, inputs, null, null);

        assertThat(collected).hasSize(2);
        assertThat(collected.get(0).getItem()).isEqualTo(Items.DIAMOND);
        assertThat(collected.get(1).getItem()).isEqualTo(Items.APPLE);
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.COAL);
        assertThat(itemHandler.getStackInSlot(5).getItem()).isEqualTo(Items.BRICK);
    }

    /** 预期产物优先于副产物出现在返回列表前部。 */
    @Test
    public void testCollectProductsExpectedBeforeRemaining() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(3, new ItemStack(Items.APPLE, 1));
        itemHandler.setStack(4, new ItemStack(Items.STICK, 1));
        installLegacyStyleCatalog(itemHandler);

        IAEItemStack[] expected = {expected(new ItemStack(Items.APPLE, 1))};
        List<ItemStack> collected = handler.collectProducts(world, POS, expected, null, null, null);

        assertThat(collected).hasSize(2);
        assertThat(collected.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(collected.get(1).getItem()).isEqualTo(Items.STICK);
    }

    /** 输出槽里的物品与本批输入同型时同样被收走（与 {@link #testIsIdleTrueWhenOutputHoldsBatchMaterial} 对称）。 */
    @Test
    public void testCollectProductsTakesOutputItemMatchingBatchMaterial() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(3, new ItemStack(Items.COAL, 1));
        installLegacyStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        List<ItemStack> collected = handler.collectProducts(world, POS, null, inputs, null, null);

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getItem()).isEqualTo(Items.COAL);
        assertThat(itemHandler.getStackInSlot(3).isEmpty()).isTrue();
    }

    /** 没有任何内容时返回空列表（而非 null），且不做任何改动。 */
    @Test
    public void testCollectProductsEmptyWhenNothingToCollect() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        installLegacyStyleCatalog(itemHandler);

        assertThat(handler.collectProducts(world, POS, null, null, null, null)).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------
    // revertMaterials / clearOutputs
    // ------------------------------------------------------------------

    /**
     * 回退清空<b>全部</b>输入槽，不按本批材料快照过滤——这是重构前的行为（已知缺陷），
     * 本次迁移刻意保持等价：输入槽里的异物（机器从相邻容器拉来的料、玩家预置的物品）
     * 也会被一并取回。输出槽与升级槽不受影响。
     */
    @Test
    public void testRevertMaterialsClearsAllInputSlotsUnfiltered() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(0, new ItemStack(Items.COAL, 2));
        itemHandler.setStack(1, new ItemStack(Items.APPLE, 1));   // 异物 → 旧实现同样取回
        itemHandler.setStack(3, new ItemStack(Items.DIAMOND, 1));
        itemHandler.setStack(5, new ItemStack(Items.BRICK, 1));
        installLegacyStyleCatalog(itemHandler);

        TargetSession session = mock(TargetSession.class);
        // 本批快照里只有煤炭；苹果不是本批材料，但旧实现仍把它取回
        when(session.getInputs()).thenReturn(Collections.singletonList(new ItemStack(Items.COAL, 1)));

        List<ItemStack> reverted = handler.revertMaterials(world, POS, null, session);

        assertThat(reverted).hasSize(2);
        assertThat(reverted.get(0).getItem()).isEqualTo(Items.COAL);
        assertThat(reverted.get(0).getCount()).isEqualTo(2);
        assertThat(reverted.get(1).getItem()).isEqualTo(Items.APPLE);
        assertThat(itemHandler.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(itemHandler.getStackInSlot(1).isEmpty()).isTrue();
        // 输出槽/升级槽不动
        assertThat(itemHandler.getStackInSlot(3).getItem()).isEqualTo(Items.DIAMOND);
        assertThat(itemHandler.getStackInSlot(5).getItem()).isEqualTo(Items.BRICK);
    }

    /** 清空只发生在输出槽：输入槽残留与升级槽不被动（与重构前 clearOutputs 一致）。 */
    @Test
    public void testClearOutputsClearsOnlyOutputSlots() {
        FakeItemHandler itemHandler = new FakeItemHandler(7);
        itemHandler.setStack(0, new ItemStack(Items.COAL, 1));
        itemHandler.setStack(3, new ItemStack(Items.DIAMOND, 3));
        itemHandler.setStack(5, new ItemStack(Items.BRICK, 1));
        installLegacyStyleCatalog(itemHandler);

        TargetSession session = mock(TargetSession.class);
        when(session.getInputs()).thenReturn(Collections.singletonList(new ItemStack(Items.COAL, 1)));

        List<ItemStack> cleared = handler.clearOutputs(world, POS, null, session);

        assertThat(cleared).hasSize(1);
        assertThat(cleared.get(0).getItem()).isEqualTo(Items.DIAMOND);
        assertThat(cleared.get(0).getCount()).isEqualTo(3);
        assertThat(itemHandler.getStackInSlot(3).isEmpty()).isTrue();
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.COAL);
        assertThat(itemHandler.getStackInSlot(5).getItem()).isEqualTo(Items.BRICK);
    }

    // ------------------------------------------------------------------
    // 处理状态反射回退链
    // ------------------------------------------------------------------

    /** {@code isProcessing()} → {@code getIsProcessing()} → false 的回退顺序。 */
    @Test
    public void testIsProcessingFallbackChain() {
        assertThat(NuclearCraftLegacyMachineHandler.isProcessingNC(null)).isFalse();
        assertThat(NuclearCraftLegacyMachineHandler.isProcessingNC(tile)).isFalse();

        ProcessingTile legacy = new ProcessingTile();
        legacy.processing = true;
        assertThat(NuclearCraftLegacyMachineHandler.isProcessingNC(legacy)).isTrue();
        legacy.processing = false;
        assertThat(NuclearCraftLegacyMachineHandler.isProcessingNC(legacy)).isFalse();

        OverhauledTile overhauled = new OverhauledTile();
        overhauled.processing = true;
        assertThat(NuclearCraftLegacyMachineHandler.isProcessingNC(overhauled)).isTrue();
        overhauled.processing = false;
        assertThat(NuclearCraftLegacyMachineHandler.isProcessingNC(overhauled)).isFalse();
    }

    /** 两个方法同时存在时以 {@code isProcessing()} 为准（与重构前的查找顺序一致）。 */
    @Test
    public void testIsProcessingPrefersLegacyMethodName() {
        assertThat(NuclearCraftLegacyMachineHandler.isProcessingNC(new BothNamesTile())).isTrue();
    }

    // ------------------------------------------------------------------
    // 反射库存包装（真实机器库存 → IItemHandler）
    // ------------------------------------------------------------------

    /** 空槽插入：过机器校验后整堆写入，并补脏标记。 */
    @Test
    public void testReflectedInventoryInsertsWholeStackIntoEmptySlot() throws Exception {
        NonNullList<ItemStack> stacks = inventoryOf(2);
        ProcessingTile machine = new ProcessingTile();
        NuclearCraftLegacyMachineHandler.ReflectedInventory inventory = reflectedInventory(stacks, machine);

        assertThat(inventory.getSlots()).isEqualTo(2);
        assertThat(inventory.getSlotLimit(0)).isEqualTo(64);
        assertThat(inventory.getStackInSlot(9)).isSameAs(ItemStack.EMPTY);

        assertThat(inventory.insertItem(0, new ItemStack(Items.APPLE, 5), false).isEmpty()).isTrue();
        assertThat(stacks.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(stacks.get(0).getCount()).isEqualTo(5);
        assertThat(machine.dirtyCalls).isEqualTo(1);
    }

    /** 同型槽在堆叠上限内合并；越上限则整个槽位拒绝（不填充到上限、也不拆分）。 */
    @Test
    public void testReflectedInventoryMergeAndLimit() throws Exception {
        NonNullList<ItemStack> stacks = inventoryOf(2);
        stacks.set(0, new ItemStack(Items.APPLE, 60));
        ProcessingTile machine = new ProcessingTile();
        NuclearCraftLegacyMachineHandler.ReflectedInventory inventory = reflectedInventory(stacks, machine);

        assertThat(inventory.insertItem(0, new ItemStack(Items.APPLE, 4), false).isEmpty()).isTrue();
        assertThat(stacks.get(0).getCount()).isEqualTo(64);

        ItemStack rejected = inventory.insertItem(0, new ItemStack(Items.APPLE, 1), false);
        assertThat(rejected.getCount()).isEqualTo(1);
        assertThat(stacks.get(0).getCount()).isEqualTo(64);
    }

    /** 异类物品不得混入已有堆，且原样退回。 */
    @Test
    public void testReflectedInventoryRejectsDifferentItem() throws Exception {
        NonNullList<ItemStack> stacks = inventoryOf(2);
        stacks.set(0, new ItemStack(Items.APPLE, 1));
        ProcessingTile machine = new ProcessingTile();
        NuclearCraftLegacyMachineHandler.ReflectedInventory inventory = reflectedInventory(stacks, machine);

        ItemStack rejected = inventory.insertItem(0, new ItemStack(Items.BRICK, 1), false);

        assertThat(rejected.getCount()).isEqualTo(1);
        assertThat(stacks.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(machine.dirtyCalls).isZero();
    }

    /** 机器拒绝该槽位时原样退回（旧实现遇到校验失败即跳过该槽）。 */
    @Test
    public void testReflectedInventoryRejectsBlockedSlot() throws Exception {
        NonNullList<ItemStack> stacks = inventoryOf(2);
        ProcessingTile machine = new ProcessingTile();
        machine.blockedSlot = 0;
        NuclearCraftLegacyMachineHandler.ReflectedInventory inventory = reflectedInventory(stacks, machine);

        ItemStack rejected = inventory.insertItem(0, new ItemStack(Items.APPLE, 1), false);

        assertThat(rejected.getCount()).isEqualTo(1);
        assertThat(stacks.get(0).isEmpty()).isTrue();
    }

    /** 校验方法缺失（NC 未安装时的句柄缺失）→ 不接受任何物品。 */
    @Test
    public void testReflectedInventoryWithoutValidationMethodRejectsAll() {
        NonNullList<ItemStack> stacks = inventoryOf(1);
        NuclearCraftLegacyMachineHandler.ReflectedInventory inventory =
                new NuclearCraftLegacyMachineHandler.ReflectedInventory(new ProcessingTile(), stacks, null);

        assertThat(inventory.insertItem(0, new ItemStack(Items.APPLE, 1), false).getCount()).isEqualTo(1);
        assertThat(stacks.get(0).isEmpty()).isTrue();
    }

    /** simulate 不得改动内容，真实插入才写脏标记。 */
    @Test
    public void testReflectedInventorySimulateDoesNotMutate() throws Exception {
        NonNullList<ItemStack> stacks = inventoryOf(2);
        stacks.set(1, new ItemStack(Items.APPLE, 3));
        ProcessingTile machine = new ProcessingTile();
        NuclearCraftLegacyMachineHandler.ReflectedInventory inventory = reflectedInventory(stacks, machine);

        assertThat(inventory.insertItem(1, new ItemStack(Items.APPLE, 1), true).isEmpty()).isTrue();
        assertThat(stacks.get(1).getCount()).isEqualTo(3);
        assertThat(inventory.insertItem(0, new ItemStack(Items.APPLE, 1), true).isEmpty()).isTrue();
        assertThat(stacks.get(0).isEmpty()).isTrue();
        assertThat(machine.dirtyCalls).isZero();
    }

    /** 抽取按"请求量与现有量的较小值"，取空后写回空堆；simulate 不改状态。 */
    @Test
    public void testReflectedInventoryExtractSemantics() throws Exception {
        NonNullList<ItemStack> stacks = inventoryOf(1);
        stacks.set(0, new ItemStack(Items.APPLE, 6));
        ProcessingTile machine = new ProcessingTile();
        NuclearCraftLegacyMachineHandler.ReflectedInventory inventory = reflectedInventory(stacks, machine);

        assertThat(inventory.extractItem(0, 4, true).getCount()).isEqualTo(4);
        assertThat(stacks.get(0).getCount()).isEqualTo(6);

        assertThat(inventory.extractItem(0, 4, false).getCount()).isEqualTo(4);
        assertThat(stacks.get(0).getCount()).isEqualTo(2);

        assertThat(inventory.extractItem(0, 64, false).getCount()).isEqualTo(2);
        assertThat(stacks.get(0)).isSameAs(ItemStack.EMPTY);

        assertThat(inventory.extractItem(0, 1, false).isEmpty()).isTrue();
        assertThat(inventory.extractItem(9, 1, false).isEmpty()).isTrue();
        assertThat(inventory.extractItem(0, 0, false).isEmpty()).isTrue();
    }

    // ------------------------------------------------------------------
    // 输出罐判据（产物是流体的机器：熔融机这类没有物品输出槽）
    // ------------------------------------------------------------------

    /**
     * 重制版口径：输出罐由 {@code IProcessor#getFluidOutputs()} 暴露，罐为空即无产物。
     *
     * <p>替身的方法名与 NC 完全一致，生产实现按 {@code Class#getMethod} 反射查找——
     * 测试环境没有 NC 也不影响这条路径（{@code isProcessingNC} 的同款做法）。</p>
     */
    @Test
    public void testHasProductFluidFromOverhauledFluidOutputs() {
        FluidOutputTile tile = new FluidOutputTile();

        assertThat(NuclearCraftLegacyMachineHandler.hasProductFluid(tile)).isFalse();

        tile.addOutput(new FluidStack(FluidRegistry.WATER, 250));
        assertThat(NuclearCraftLegacyMachineHandler.hasProductFluid(tile)).isTrue();
    }

    /**
     * 2.19a 口径：{@code getTanks()} 返回全部罐，{@code fluidInputSize} 之前的都是输入罐。
     *
     * <p>输入罐里可能正装着本批推入的流体，把它当产物会让会话立刻判定"可收集"，
     * 而收集阶段又抽不动它（sorption 为 IN）→ 会话在 COLLECTING/PROCESSING 之间空转。</p>
     */
    @Test
    public void testHasProductFluidIgnoresInputTanks() {
        LegacyTanksTile tile = new LegacyTanksTile(1, 1);

        tile.setTank(0, new FluidStack(FluidRegistry.WATER, 1000));
        assertThat(NuclearCraftLegacyMachineHandler.hasProductFluid(tile)).isFalse();

        tile.setTank(1, new FluidStack(FluidRegistry.LAVA, 100));
        assertThat(NuclearCraftLegacyMachineHandler.hasProductFluid(tile)).isTrue();
    }

    /** 没有输出罐（只有输入罐）的机器永远不因流体判空闲。 */
    @Test
    public void testHasProductFluidWithOnlyInputTanksIsFalse() {
        LegacyTanksTile tile = new LegacyTanksTile(2, 0);
        tile.setTank(0, new FluidStack(FluidRegistry.WATER, 1000));
        tile.setTank(1, new FluidStack(FluidRegistry.WATER, 1000));

        assertThat(NuclearCraftLegacyMachineHandler.hasProductFluid(tile)).isFalse();
    }

    /** 读不到罐 API（非 NC 方块 / 反射失败）与 null 目标一律按"没有流体产物"处理，不抛异常。 */
    @Test
    public void testHasProductFluidWithoutTankApiIsFalse() {
        assertThat(NuclearCraftLegacyMachineHandler.hasProductFluid(null)).isFalse();
        assertThat(NuclearCraftLegacyMachineHandler.hasProductFluid(new ProcessingTile())).isFalse();
    }

    /** 罐对象不是 Forge FluidTank 时按 0 处理（不硬引用 NC 类型带来的兜底）。 */
    @Test
    public void testHasProductFluidIgnoresForeignTankTypes() {
        assertThat(NuclearCraftLegacyMachineHandler.hasProductFluid(new ForeignTankTile())).isFalse();
    }

    /**
     * 熔融机式目标（唯一的槽是输入槽、产物只在输出罐）：
     * 罐里有流体 → 空闲可收集；会话未完成（流体还在罐里）。
     */
    @Test
    public void testIsIdleTrueForFluidOnlyProductMachine() {
        installCatalog(new FakeItemHandler(1), 1, new int[] {0}, new int[0]);
        FluidOutputTile tile = new FluidOutputTile();
        tile.addOutput(new FluidStack(FluidRegistry.LAVA, 300));
        when(world.getTileEntity(POS)).thenReturn(tile);

        assertThat(handler.isIdle(world, POS, Collections.emptyList(), null)).isTrue();
        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /** 罐空且输入已耗尽 → 完成（此时不会再空等超时）。 */
    @Test
    public void testHasFinishedTrueWhenFluidProductAlreadyCollected() {
        installCatalog(new FakeItemHandler(1), 1, new int[] {0}, new int[0]);
        when(world.getTileEntity(POS)).thenReturn(new FluidOutputTile());

        assertThat(handler.isIdle(world, POS, Collections.emptyList(), null)).isFalse();
        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isTrue();
    }

    /** 机器仍在处理时，罐里的流体不能让它被判空闲（"处理中"优先于"有流体"）。 */
    @Test
    public void testIsIdleFalseWhileWorkingWithFluidInTank() {
        installCatalog(new FakeItemHandler(1), 1, new int[] {0}, new int[0]);
        WorkingFluidOutputTile tile = new WorkingFluidOutputTile();
        tile.addOutput(new FluidStack(FluidRegistry.LAVA, 300));
        when(world.getTileEntity(POS)).thenReturn(tile);

        assertThat(handler.isIdle(world, POS, Collections.emptyList(), null)).isFalse();
        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /** 输入槽还留着本批材料时，罐里的流体不改变"未完成"结论（两种闸门同时成立）。 */
    @Test
    public void testHasFinishedFalseWhileBatchMaterialRemainsWithFluid() {
        FakeItemHandler itemHandler = new FakeItemHandler(1);
        itemHandler.setStack(0, new ItemStack(Items.COAL, 1));
        installCatalog(itemHandler, 1, new int[] {0}, new int[0]);
        FluidOutputTile tile = new FluidOutputTile();
        tile.addOutput(new FluidStack(FluidRegistry.LAVA, 300));
        when(world.getTileEntity(POS)).thenReturn(tile);

        assertThat(handler.hasFinished(world, POS,
                Collections.singletonList(new ItemStack(Items.COAL, 1)), null)).isFalse();
    }

    /** 产物是流体的机器替身（重制版口径）：输出罐由 {@code getFluidOutputs()} 暴露。 */
    public static class FluidOutputTile extends TileEntity {

        private final List<FluidTank> outputs = new ArrayList<>();

        public FluidOutputTile addOutput(FluidStack fluid) {
            FluidTank tank = new FluidTank(1000);
            tank.setFluid(fluid);
            this.outputs.add(tank);
            return this;
        }

        public List<FluidTank> getFluidOutputs() {
            return this.outputs;
        }
    }

    /** 处理中的流体产物机器。 */
    public static class WorkingFluidOutputTile extends FluidOutputTile {

        public boolean isProcessing() {
            return true;
        }
    }

    /** 2.19a 口径替身：{@code getTanks()} 全部罐 + {@code fluidInputSize} 字段。 */
    public static class LegacyTanksTile extends TileEntity {

        public final int fluidInputSize;
        private final List<FluidTank> tanks = new ArrayList<>();

        public LegacyTanksTile(int inputSize, int outputSize) {
            this.fluidInputSize = inputSize;
            for (int i = 0; i < inputSize + outputSize; i++) {
                this.tanks.add(new FluidTank(1000));
            }
        }

        public List<FluidTank> getTanks() {
            return this.tanks;
        }

        public void setTank(int index, FluidStack fluid) {
            this.tanks.get(index).setFluid(fluid);
        }
    }

    /** 罐对象不是 Forge FluidTank（NC 换实现时的兜底路径）。 */
    public static class ForeignTankTile extends TileEntity {

        public final int fluidInputSize = 0;
        private final List<String> tanks = Collections.singletonList("not-a-tank");

        public List<String> getTanks() {
            return this.tanks;
        }
    }
}
