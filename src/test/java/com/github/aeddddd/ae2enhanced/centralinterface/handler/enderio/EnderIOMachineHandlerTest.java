package com.github.aeddddd.ae2enhanced.centralinterface.handler.enderio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ItemTransferHelper;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineIdle;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.FakeItemHandler;

/**
 * {@link EnderIOMachineHandler} 测试。
 *
 * <p>测试环境没有 Ender IO / EnderCore（两者都是 {@code compileOnly}），任何 {@code Class.forName}
 * 路径都不可能成功，因此 {@code AVAILABLE == false}，框架的"目录为 null 即目标无效"分支与
 * 本类覆盖的方法全部走生产代码。覆盖分四块：</p>
 * <ul>
 *     <li><b>匹配与能力</b>：blockId 前缀集合、能力集合；</li>
 *     <li><b>纯逻辑</b>：legacy 槽位角色映射
 *     （{@link EnderIOMachineHandler#legacyRoles(int, int[])}，参数取自 EIO 5.3.72 各机器真实使用的
 *     {@code SlotDefinition} 构造结果）；{@code inventory[]} 适配器
 *     {@link EnderIOMachineHandler.LegacyInventoryAccess}；空闲/完成判据
 *     （{@code isIdleState}/{@code hasFinishedState}）；</li>
 *     <li><b>目录 + 框架协同</b>：{@link EnderIOMachineHandler#legacyCatalog} 造出的真实目录驱动框架的
 *     两阶段推料与目录收窄后的收集，验证角色映射、推料原子性、收集排除本批材料、队列追加顺序；</li>
 *     <li><b>实例方法</b>：用测试替身替换 {@code catalogFor}（{@code idleGate} 也替换，因为生产实现
 *     只能作用于真实 EIO 类），让 pushMaterials / collectProducts / clearOutputs / revertMaterials /
 *     isIdle / hasFinished 走生产代码。</li>
 * </ul>
 *
 * <p><b>已知覆盖缺口（这些不是"已测"，只是"结构上可用"）</b>：需要 EIO 在位才成立的层——
 * 反射句柄初始化、EnderInventory 视图目录、legacy 区间字段读取，以及
 * {@code AbstractMachineEntity#isActive()} / {@code getOutputQueue()} 的<b>真实反射调用</b>——
 * 在无头环境下不可达（{@code METHOD_IS_ACTIVE == null}、{@code METHOD_GET_OUTPUT_QUEUE == null}），
 * 必须由<b>带真实 EIO TileEntity 的实机/集成夹具</b>覆盖。具体地：</p>
 * <ul>
 *     <li>"机器正在处理（{@code isActive()} 为真）时不得判完成 / 不得判空闲"这一支，本测试只能通过
 *     框架接缝 {@code idleGate} 注入状态来覆盖（见 {@code TestableHandler}），
 *     即"经接缝可达"而非"经真实反射可达"；</li>
 *     <li>输出队列（{@code NNList<ItemStack>}）在测试里恒为空，队列分支只用可注入的队列供应器
 *     在静态实现上验证（{@code collectFrom}/{@code clearFrom}/{@code revertFrom} 与
 *     {@code isIdleState}/{@code hasFinishedState} 的 {@code queuedOutput} 参数），
 *     真实 {@code drainOutputQueue} 未被本测试执行。</li>
 * </ul>
 * <p>状态不可解析时的<b>兜底口径</b>另有覆盖（不经接缝、走生产 idleGate）：
 * {@link #testHasFinishedFallbackWhenMachineStateUnresolvable}。</p>
 */
public class EnderIOMachineHandlerTest {

    private static final BlockPos POS = new BlockPos(3, 64, 3);

    private TestableHandler handler;
    private World world;

    @BeforeAll
    public static void boot() throws Exception {
        // 测试环境"没有安装 Ender IO"：让 Loader.isModLoaded 短路返回 false，
        // 处理器静态初始化于是不做任何反射解析（也就不碰 AE2Enhanced.LOGGER）
        stubLoader();
        AE2TestBootstrap.boot();
    }

    private static void stubLoader() throws Exception {
        Field instanceField = Loader.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        Object loader = instanceField.get(null);
        if (loader == null) {
            Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) theUnsafe.get(null);
            loader = unsafe.allocateInstance(Loader.class);
            instanceField.set(null, loader);
        }
        Field namedMods = Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        if (namedMods.get(loader) == null) {
            namedMods.set(loader, new HashMap<String, Object>());
        }
    }

    @BeforeEach
    public void setUp() {
        this.handler = new TestableHandler();
        this.world = mock(World.class);
    }

    /**
     * 测试替身：替换"目录从哪来"，并注入"机器是否处理中"这一状态信号。
     *
     * <p>生产 {@code idleGate} 返回的 lambda 体是 {@code isMachineActive(te)}，其反射句柄在无头环境为
     * null，无法用假 TileEntity 驱动；而 {@code idleGate} 是框架指定的机器状态接缝
     * （{@code isIdle} 与 {@code hasFinished} 都必须经它取状态），因此测试从这条接缝注入。
     * 这不代表 {@code AbstractMachineEntity#isActive()} 的真实反射支被测到了——见类注释的覆盖缺口。</p>
     */
    private static final class TestableHandler extends EnderIOMachineHandler {

        private ISlotCatalog catalog;
        private boolean working;

        void setCatalog(ISlotCatalog catalog) {
            this.catalog = catalog;
        }

        void setWorking(boolean working) {
            this.working = working;
        }

        @Override
        protected ISlotCatalog catalogFor(World world, BlockPos pos) {
            return this.catalog;
        }

        @Override
        protected MachineIdle idleGate(World world, BlockPos pos) {
            return (w, p, te) -> this.working;
        }
    }

    /**
     * 只注入目录、保留<b>生产</b> {@code idleGate} 的替身.
     *
     * <p>用于验证"状态解析不到（{@code isActive()} 反射不可用）时的兜底口径"，即无头环境下
     * 该 handler 的真实行为，而非经接缝注入的行为。</p>
     */
    private static final class RealGateHandler extends EnderIOMachineHandler {

        private ISlotCatalog catalog;

        void setCatalog(ISlotCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        protected ISlotCatalog catalogFor(World world, BlockPos pos) {
            return this.catalog;
        }
    }

    /**
     * 一台 legacy 机器的测试替身：{@code inventory[]} + SlotDefinition 区间 + 注入的机器判定。
     *
     * <p>与生产侧的区别只有"区间从字段反射读到"还是"直接给"。</p>
     */
    private static final class LegacyMachine {

        final ItemStack[] inventory;
        final int[] ranges;
        int dirtyCalls;
        java.util.function.BiPredicate<Integer, ItemStack> accepts = (slot, stack) -> true;
        java.util.function.IntUnaryOperator limit = slot -> 64;

        LegacyMachine(int size, int[] ranges) {
            this.inventory = new ItemStack[size];
            Arrays.fill(this.inventory, ItemStack.EMPTY);
            this.ranges = ranges;
        }

        ISlotCatalog catalog() {
            return EnderIOMachineHandler.legacyCatalog(this.ranges, this.inventory,
                    this.accepts, this.limit, () -> this.dirtyCalls++);
        }

        ItemStack get(int slot) {
            return this.inventory[slot];
        }

        void set(int slot, ItemStack stack) {
            this.inventory[slot] = stack;
        }

        /** EIO {@code SlotDefinition(numInputs, numOutputs)}：输入 0..n-1，输出 n..n+m-1。 */
        static int[] inputOutput(int inputs, int outputs) {
            return new int[] {0, inputs - 1, inputs, inputs + outputs - 1};
        }

        static int[] ranges(int minIn, int maxIn, int minOut, int maxOut) {
            return new int[] {minIn, maxIn, minOut, maxOut};
        }
    }

    private static InventoryCrafting crafting(ItemStack... stacks) {
        InventoryCrafting inv = new InventoryCrafting(mock(Container.class), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            inv.setInventorySlotContents(i, stacks[i]);
        }
        return inv;
    }

    private static ItemStack stack(Item item, int count) {
        return new ItemStack(item, count);
    }

    private static IAEItemStack[] expected(Item item, int count) {
        return new IAEItemStack[] { AEItemStack.fromItemStack(new ItemStack(item, count)) };
    }

    private static List<ItemStack> noQueue() {
        return Collections.emptyList();
    }

    private static List<String> itemNames(List<ItemStack> stacks) {
        List<String> names = new ArrayList<>();
        for (ItemStack stack : stacks) {
            names.add(stack.getItem().getRegistryName() + "x" + stack.getCount());
        }
        return names;
    }

    // ------------------------------------------------------------------
    // 匹配与能力
    // ------------------------------------------------------------------

    /** canHandle 匹配整个 enderio 命名空间（与重构前一致，不因类结构细分而收窄）。 */
    @Test
    public void testCanHandleEnderioNamespace() {
        assertThat(handler.canHandle("enderio:machine_alloy_smelter")).isTrue();
        assertThat(handler.canHandle("enderio:")).isTrue();
        assertThat(handler.canHandle("enderio")).isFalse();
        assertThat(handler.canHandle("enderios:block")).isFalse();
        assertThat(handler.canHandle("minecraft:furnace")).isFalse();
        assertThat(handler.canHandle("")).isFalse();
        assertThat(handler.canHandle(null)).isFalse();
    }

    /** 能力集合仅 PHYSICAL（EIO 机器不走虚拟批量）。 */
    @Test
    public void testCapabilitiesPhysicalOnly() {
        assertThat(handler.getCapabilities()).containsExactly(HandlerCapabilities.PHYSICAL);
        assertThat(handler.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isFalse();
    }

    // ------------------------------------------------------------------
    // 目录不可用（无 Ender IO）：框架契约的降级分支
    // ------------------------------------------------------------------

    /** AVAILABLE=false 时目标无效，且各方法按框架契约降级，不触碰 World。 */
    @Test
    public void testModMissingDegradesToFrameworkContract() {
        assertThat(handler.isValidTarget(world, POS)).isFalse();
        assertThat(handler.canStart(world, POS, crafting(stack(Items.APPLE, 1)), null)).isFalse();
        assertThat(handler.pushMaterials(world, POS, crafting(stack(Items.APPLE, 1)), null, null)).isFalse();
        assertThat(handler.collectProducts(world, POS, null, null, null, null)).isNotNull().isEmpty();
        assertThat(handler.revertMaterials(world, POS, null, null)).isNotNull().isEmpty();
        assertThat(handler.clearOutputs(world, POS, null, null)).isNotNull().isEmpty();
        assertThat(handler.isIdle(world, POS, null, null)).isTrue();
        assertThat(handler.hasFinished(world, POS, null, null)).isTrue();
    }

    // ------------------------------------------------------------------
    // legacy 槽位角色映射（参数取自 EIO 5.3.72 各机器实际构造的 SlotDefinition）
    // ------------------------------------------------------------------

    /** Sag Mill：{@code SlotDefinition(2, 4)} → 槽 0-1 输入、2-5 输出、6 升级槽（未纳入目录）。 */
    @Test
    public void testLegacyRolesSagMillStyle() {
        SlotRole[] roles = EnderIOMachineHandler.legacyRoles(7, LegacyMachine.inputOutput(2, 4));

        assertThat(roles).containsExactly(SlotRole.INPUT, SlotRole.INPUT,
                SlotRole.OUTPUT, SlotRole.OUTPUT, SlotRole.OUTPUT, SlotRole.OUTPUT, SlotRole.UNKNOWN);
    }

    /** Alloy Smelter：{@code SlotDefinition(3, 1, 1)} → 槽 0-2 输入、3 输出、4 升级槽。 */
    @Test
    public void testLegacyRolesAlloySmelterStyle() {
        SlotRole[] roles = EnderIOMachineHandler.legacyRoles(5, LegacyMachine.ranges(0, 2, 3, 3));

        assertThat(roles).containsExactly(SlotRole.INPUT, SlotRole.INPUT, SlotRole.INPUT,
                SlotRole.OUTPUT, SlotRole.UNKNOWN);
    }

    /** Stirling 发电机：{@code SlotDefinition(1, 0, 1)} → 槽 0 输入、1 升级槽（输出区间 -10/-11 为空）。 */
    @Test
    public void testLegacyRolesGeneratorStyle() {
        SlotRole[] roles = EnderIOMachineHandler.legacyRoles(2, LegacyMachine.ranges(0, 0, -10, -11));

        assertThat(roles).containsExactly(SlotRole.INPUT, SlotRole.UNKNOWN);
    }

    /**
     * 物品缓冲器：{@code SlotDefinition(9)} 输入输出同区间，无法判定角色 → 全 UNKNOWN。
     *
     * <p>旧实现把这段区间同时当输入与输出用，如实标注只能是 UNKNOWN（框架据此降级）。</p>
     */
    @Test
    public void testLegacyRolesInOutSharedSlotsAreUnknown() {
        int[] ranges = LegacyMachine.ranges(0, 8, 0, 8);

        assertThat(EnderIOMachineHandler.legacyRoles(9, ranges))
                .containsExactly(SlotRole.UNKNOWN, SlotRole.UNKNOWN, SlotRole.UNKNOWN, SlotRole.UNKNOWN,
                        SlotRole.UNKNOWN, SlotRole.UNKNOWN, SlotRole.UNKNOWN, SlotRole.UNKNOWN,
                        SlotRole.UNKNOWN);

        LegacyMachine machine = new LegacyMachine(9, ranges);
        assertThat(machine.catalog().rolesKnown()).isFalse();
    }

    /** 物品面板：{@code SlotDefinition(0, 8, 11, 25, 21, 20)} → 9/10 不在任何区间，标 UNKNOWN。 */
    @Test
    public void testLegacyRolesInventoryPanelStyle() {
        SlotRole[] roles = EnderIOMachineHandler.legacyRoles(26, LegacyMachine.ranges(0, 8, 11, 25));

        assertThat(roles[0]).isEqualTo(SlotRole.INPUT);
        assertThat(roles[8]).isEqualTo(SlotRole.INPUT);
        assertThat(roles[9]).isEqualTo(SlotRole.UNKNOWN);
        assertThat(roles[10]).isEqualTo(SlotRole.UNKNOWN);
        assertThat(roles[11]).isEqualTo(SlotRole.OUTPUT);
        assertThat(roles[25]).isEqualTo(SlotRole.OUTPUT);
    }

    /** 储罐：{@code SlotDefinition(0, 2, 3, 4, -1, -1)} → 槽 0-2 输入、3-4 输出。 */
    @Test
    public void testLegacyRolesTankStyle() {
        SlotRole[] roles = EnderIOMachineHandler.legacyRoles(5, LegacyMachine.ranges(0, 2, 3, 4));

        assertThat(roles).containsExactly(SlotRole.INPUT, SlotRole.INPUT, SlotRole.INPUT,
                SlotRole.OUTPUT, SlotRole.OUTPUT);
    }

    /** 读取失败时的退化：整段既算输入也算输出 → 全 UNKNOWN。 */
    @Test
    public void testLegacyRolesFullyOverlappingRangesAreUnknown() {
        assertThat(EnderIOMachineHandler.legacyRoles(3, LegacyMachine.ranges(0, 2, 0, 2)))
                .containsExactly(SlotRole.UNKNOWN, SlotRole.UNKNOWN, SlotRole.UNKNOWN);
    }

    // ------------------------------------------------------------------
    // inventory[] 适配器
    // ------------------------------------------------------------------

    /** 输入区间之外的槽位一律不接受插入（旧 pushToLegacyInputSlots 只遍历输入区间）。 */
    @Test
    public void testAccessRejectsNonInputSlot() {
        LegacyMachine machine = new LegacyMachine(3, LegacyMachine.ranges(0, 0, 1, 2));
        IItemHandler access = accessOf(machine);

        ItemStack remaining = access.insertItem(1, stack(Items.APPLE, 4), false);

        assertThat(remaining.getCount()).isEqualTo(4);
        assertThat(machine.get(1).isEmpty()).isTrue();
        assertThat(machine.dirtyCalls).isZero();
    }

    /** 机器自身拒绝该物品（isMachineItemValidForSlot）时不插入。 */
    @Test
    public void testAccessRejectsWhenMachineDeclines() {
        LegacyMachine machine = new LegacyMachine(2, LegacyMachine.inputOutput(1, 1));
        machine.accepts = (slot, stack) -> stack.getItem() != Items.APPLE;
        IItemHandler access = accessOf(machine);

        assertThat(access.insertItem(0, stack(Items.APPLE, 1), false).getCount()).isEqualTo(1);
        assertThat(access.insertItem(0, stack(Items.COAL, 1), false).isEmpty()).isTrue();
        assertThat(machine.get(0).getItem()).isEqualTo(Items.COAL);
    }

    /** 空槽按单槽上限截断，返回余量（旧 insertIntoLegacySlot 行为）。 */
    @Test
    public void testAccessInsertsUpToSlotLimitOnEmptySlot() {
        LegacyMachine machine = new LegacyMachine(1, LegacyMachine.ranges(0, 0, -10, -11));
        machine.limit = slot -> 16;
        IItemHandler access = accessOf(machine);

        ItemStack remaining = access.insertItem(0, stack(Items.APPLE, 40), false);

        assertThat(machine.get(0).getCount()).isEqualTo(16);
        assertThat(remaining.getCount()).isEqualTo(24);
        assertThat(remaining.getItem()).isEqualTo(Items.APPLE);
    }

    /** 合并进已有堆时取"物品堆叠上限与单槽上限的较小值"。 */
    @Test
    public void testAccessMergesIntoOccupiedSlotUpToMax() {
        LegacyMachine machine = new LegacyMachine(1, LegacyMachine.ranges(0, 0, -10, -11));
        machine.limit = slot -> 20;
        machine.set(0, stack(Items.APPLE, 18));
        IItemHandler access = accessOf(machine);

        ItemStack remaining = access.insertItem(0, stack(Items.APPLE, 5), false);

        assertThat(machine.get(0).getCount()).isEqualTo(20);
        assertThat(remaining.getCount()).isEqualTo(3);
    }

    /** 异种物品不合并（旧 ItemStack.areItemsEqual + areItemStackTagsEqual 判定）。 */
    @Test
    public void testAccessDoesNotMergeDifferentItems() {
        LegacyMachine machine = new LegacyMachine(1, LegacyMachine.ranges(0, 0, -10, -11));
        machine.set(0, stack(Items.APPLE, 1));
        IItemHandler access = accessOf(machine);

        assertThat(access.insertItem(0, stack(Items.COAL, 1), false).getCount()).isEqualTo(1);
        assertThat(machine.get(0).getItem()).isEqualTo(Items.APPLE);
    }

    /** simulate 不改变数组：这是框架两阶段推料原子性的前提。 */
    @Test
    public void testAccessSimulateDoesNotModify() {
        LegacyMachine machine = new LegacyMachine(1, LegacyMachine.ranges(0, 0, -10, -11));
        machine.limit = slot -> 16;
        IItemHandler access = accessOf(machine);

        ItemStack remaining = access.insertItem(0, stack(Items.APPLE, 40), true);

        assertThat(machine.get(0).isEmpty()).isTrue();
        // 模拟仍按单槽上限 16 计算余量（不改变数组，但与真实插入返回同值）
        assertThat(remaining.getCount()).isEqualTo(24);
        assertThat(machine.dirtyCalls).isZero();

        assertThat(access.extractItem(0, 1, true).isEmpty()).isTrue();
    }

    /** 抽取为裸抽取：部分抽取保留余量，抽空即清槽。 */
    @Test
    public void testAccessExtractsPartiallyAndClearsSlot() {
        LegacyMachine machine = new LegacyMachine(2, LegacyMachine.ranges(0, 0, 1, 1));
        machine.set(1, stack(Items.BREAD, 5));
        IItemHandler access = accessOf(machine);

        ItemStack part = access.extractItem(1, 2, false);

        assertThat(part.getCount()).isEqualTo(2);
        assertThat(machine.get(1).getCount()).isEqualTo(3);

        ItemStack rest = access.extractItem(1, 64, false);

        assertThat(rest.getCount()).isEqualTo(3);
        assertThat(machine.get(1).isEmpty()).isTrue();
        assertThat(machine.dirtyCalls).isEqualTo(2);
    }

    /** 单槽上限缺省为 64；未提供谓词时一律允许。 */
    @Test
    public void testAccessDefaults() {
        LegacyMachine machine = new LegacyMachine(1, LegacyMachine.ranges(0, 0, -10, -11));
        machine.limit = null;
        machine.accepts = null;
        IItemHandler access = accessOf(machine);

        assertThat(access.getSlotLimit(0)).isEqualTo(64);
        assertThat(access.getSlots()).isEqualTo(1);
        assertThat(access.insertItem(0, stack(Items.APPLE, 64), false).isEmpty()).isTrue();
    }

    private static IItemHandler accessOf(LegacyMachine machine) {
        return new EnderIOMachineHandler.LegacyInventoryAccess(machine.inventory, machine.ranges[0],
                machine.ranges[1], machine.accepts, machine.limit, () -> machine.dirtyCalls++);
    }

    // ------------------------------------------------------------------
    // 目录 × 框架：推料
    // ------------------------------------------------------------------

    /** 目录角色映射 + 推料只落在输入槽：输出槽与升级槽不接收材料。 */
    @Test
    public void testPushOnlyTargetsInputSlots() {
        LegacyMachine machine = new LegacyMachine(5, LegacyMachine.inputOutput(2, 2)); // 0-1 输入, 2-3 输出, 4 未归类
        ISlotCatalog catalog = machine.catalog();

        assertThat(catalog.rolesKnown()).isTrue();
        assertThat(catalog.pushTargets()).hasSize(2);
        assertThat(catalog.inputSlots()).hasSize(2);
        assertThat(catalog.outputSlots()).hasSize(2);

        boolean ok = ItemTransferHelper.push(catalog, Collections.singletonList(stack(Items.APPLE, 3))).isSuccess();

        assertThat(ok).isTrue();
        assertThat(machine.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(machine.get(0).getCount()).isEqualTo(3);
        assertThat(machine.get(4).isEmpty()).isTrue();
    }

    /** 放不下时不留痕：模拟阶段失败即不插入；实际阶段失败则回退已插入部分。 */
    @Test
    public void testPushAtomicFailureLeavesNoTrace() {
        LegacyMachine machine = new LegacyMachine(1, LegacyMachine.ranges(0, 0, -10, -11));
        ISlotCatalog catalog = machine.catalog();

        // 单槽 64 上限放不下两份 64：模拟逐份通过（同一空槽），真实插入第二份时失败 → 必须回退第一份
        boolean ok = ItemTransferHelper.push(catalog, Arrays.asList(stack(Items.APPLE, 64), stack(Items.APPLE, 64))).isSuccess();

        assertThat(ok).isFalse();
        assertThat(machine.get(0).isEmpty()).isTrue();
    }

    // ------------------------------------------------------------------
    // 目录 × 框架：收集 / 清空 / 回退
    // ------------------------------------------------------------------

    /** 收集排除本批材料：输入槽里的本批材料不动，只取输出槽的内容。 */
    @Test
    public void testCollectExcludesBatchInputs() {
        LegacyMachine machine = new LegacyMachine(4, LegacyMachine.inputOutput(2, 2));
        machine.set(0, stack(Items.APPLE, 2));
        machine.set(2, stack(Items.BREAD, 1));

        List<ItemStack> collected = EnderIOMachineHandler.collectFrom(machine.catalog(), null,
                Collections.singletonList(stack(Items.APPLE, 2)), EnderIOMachineHandlerTest::noQueue);

        assertThat(itemNames(collected)).containsExactly("minecraft:breadx1");
        assertThat(machine.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(machine.get(2).isEmpty()).isTrue();
    }

    /**
     * 阶段 1（按预期产物匹配）只扫输出槽.
     *
     * <p>旧实现只在输出范围里匹配，落在输入槽的同型物品（玩家预置、上一轮余料）从不被当作产物；
     * 框架默认会扫全部槽位，故本类把目录收窄到 OUTPUT 槽。</p>
     */
    @Test
    public void testCollectExpectedMatchOnlyInsideOutputSlots() {
        LegacyMachine machine = new LegacyMachine(4, LegacyMachine.inputOutput(2, 2));
        machine.set(0, stack(Items.APPLE, 1));   // 输入槽里的"同型物品"，不是本批材料
        machine.set(2, stack(Items.APPLE, 1));   // 输出槽里的真实产物

        List<ItemStack> collected = EnderIOMachineHandler.collectFrom(machine.catalog(),
                expected(Items.APPLE, 1), Collections.emptyList(), EnderIOMachineHandlerTest::noQueue);

        assertThat(itemNames(collected)).containsExactly("minecraft:applex1");
        assertThat(machine.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(machine.get(2).isEmpty()).isTrue();
    }

    /** 输出队列排在槽位结果之后（旧实现先槽位、后 drain 队列）。 */
    @Test
    public void testCollectAppendsOutputQueueAfterSlots() {
        LegacyMachine machine = new LegacyMachine(4, LegacyMachine.inputOutput(2, 2));
        machine.set(2, stack(Items.BREAD, 1));

        List<ItemStack> collected = EnderIOMachineHandler.collectFrom(machine.catalog(), null,
                Collections.emptyList(), () -> Collections.singletonList(stack(Items.GOLD_NUGGET, 2)));

        assertThat(itemNames(collected)).containsExactly("minecraft:breadx1", "minecraft:gold_nuggetx2");
    }

    /** 角色未知（输入输出同槽）时降级为"非本批输入即产物"，与旧实现的整段输出范围一致。 */
    @Test
    public void testCollectDegradedCatalogTakesAnyNonBatchItem() {
        LegacyMachine machine = new LegacyMachine(3, LegacyMachine.ranges(0, 2, 0, 2));
        machine.set(0, stack(Items.APPLE, 1));        // 本批材料 → 不收
        machine.set(1, stack(Items.GOLD_INGOT, 1));   // 非本批材料 → 收

        List<ItemStack> collected = EnderIOMachineHandler.collectFrom(machine.catalog(), null,
                Collections.singletonList(stack(Items.APPLE, 1)), EnderIOMachineHandlerTest::noQueue);

        assertThat(itemNames(collected)).containsExactly("minecraft:gold_ingotx1");
        assertThat(machine.get(0).getItem()).isEqualTo(Items.APPLE);
    }

    /** 清空输出：清空输出槽全部内容 + 队列；输入槽里的本批材料不动（旧实现同样只清输出范围）。 */
    @Test
    public void testClearOutputsClearsOutputSlotsAndQueue() {
        LegacyMachine machine = new LegacyMachine(4, LegacyMachine.inputOutput(2, 2));
        machine.set(0, stack(Items.APPLE, 2));
        machine.set(2, stack(Items.BREAD, 1));
        machine.set(3, stack(Items.CARROT, 1));

        List<ItemStack> cleared = EnderIOMachineHandler.clearFrom(machine.catalog(),
                Collections.singletonList(stack(Items.APPLE, 2)), expected(Items.BREAD, 1),
                () -> Collections.singletonList(stack(Items.GOLD_NUGGET, 1)));

        assertThat(itemNames(cleared)).containsExactly("minecraft:breadx1", "minecraft:carrotx1",
                "minecraft:gold_nuggetx1");
        assertThat(machine.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(machine.get(2).isEmpty()).isTrue();
    }

    /**
     * 回退只取本批材料（刻意与旧实现不同）：旧实现无条件清空整个输入范围.
     *
     * <p>那是与非原子推料配套的补偿动作；改原子推料后，清空整个输入范围会把玩家预置或机器自行拉入
     * 输入槽的物品收进 ME 网络。</p>
     */
    @Test
    public void testRevertTakesOnlyBatchMaterialsAndQueue() {
        LegacyMachine machine = new LegacyMachine(4, LegacyMachine.inputOutput(2, 2));
        machine.set(0, stack(Items.APPLE, 2));   // 本批材料
        machine.set(1, stack(Items.COAL, 5));    // 非本批材料（机器自行拉入/玩家预置）→ 不动
        machine.set(3, stack(Items.BREAD, 1));   // 产物 → 不动

        List<ItemStack> reverted = EnderIOMachineHandler.revertFrom(machine.catalog(),
                Collections.singletonList(stack(Items.APPLE, 2)),
                () -> Collections.singletonList(stack(Items.GOLD_NUGGET, 1)));

        assertThat(itemNames(reverted)).containsExactly("minecraft:applex2", "minecraft:gold_nuggetx1");
        assertThat(machine.get(0).isEmpty()).isTrue();
        assertThat(machine.get(1).getCount()).isEqualTo(5);
        assertThat(machine.get(3).getItem()).isEqualTo(Items.BREAD);
    }

    // ------------------------------------------------------------------
    // 空闲 / 完成判据
    // ------------------------------------------------------------------

    /** 空闲判据：处理中不算空闲；队列非空或输出范围内有物品即空闲（不看是否属于本批材料）。 */
    @Test
    public void testIsIdleStateMatrix() {
        LegacyMachine machine = new LegacyMachine(4, LegacyMachine.inputOutput(2, 2));
        ISlotCatalog catalog = machine.catalog();

        assertThat(EnderIOMachineHandler.isIdleState(catalog, true, true)).isFalse();
        assertThat(EnderIOMachineHandler.isIdleState(catalog, false, true)).isTrue();
        assertThat(EnderIOMachineHandler.isIdleState(catalog, false, false)).isFalse();

        machine.set(2, stack(Items.BREAD, 1));
        assertThat(EnderIOMachineHandler.isIdleState(catalog, false, false)).isTrue();

        machine.set(2, ItemStack.EMPTY);
        machine.set(0, stack(Items.APPLE, 1)); // 只有输入槽有东西
        assertThat(EnderIOMachineHandler.isIdleState(catalog, false, false)).isFalse();
    }

    /** 角色未知（同槽机器）时空闲判据看全部槽位——旧实现对该范围不做角色过滤。 */
    @Test
    public void testIsIdleStateDegradedLooksAtAllSlots() {
        LegacyMachine machine = new LegacyMachine(3, LegacyMachine.ranges(0, 2, 0, 2));
        machine.set(0, stack(Items.APPLE, 1));

        assertThat(EnderIOMachineHandler.isIdleState(machine.catalog(), false, false)).isTrue();
    }

    /** 完成判据：处理中、输入未耗尽、结论范围内还有东西，都算未完成。 */
    @Test
    public void testHasFinishedStateMatrix() {
        LegacyMachine machine = new LegacyMachine(4, LegacyMachine.inputOutput(2, 2));
        ISlotCatalog catalog = machine.catalog();
        List<ItemStack> batch = Collections.singletonList(stack(Items.APPLE, 2));

        assertThat(EnderIOMachineHandler.hasFinishedState(catalog, batch, true, false)).isFalse();

        machine.set(0, stack(Items.APPLE, 1));  // 本批材料还留在输入槽
        assertThat(EnderIOMachineHandler.hasFinishedState(catalog, batch, false, false)).isFalse();

        machine.set(0, ItemStack.EMPTY);
        assertThat(EnderIOMachineHandler.hasFinishedState(catalog, batch, false, true)).isFalse();

        machine.set(2, stack(Items.BREAD, 1));  // 产物尚未收集
        assertThat(EnderIOMachineHandler.hasFinishedState(catalog, batch, false, false)).isFalse();

        machine.set(2, ItemStack.EMPTY);
        assertThat(EnderIOMachineHandler.hasFinishedState(catalog, batch, false, false)).isTrue();
    }

    /** 角色未知时输入判据覆盖全部槽位（= 旧的输入范围）。 */
    @Test
    public void testHasFinishedStateDegradedChecksAllSlots() {
        LegacyMachine machine = new LegacyMachine(3, LegacyMachine.ranges(0, 2, 0, 2));
        machine.set(1, stack(Items.APPLE, 1));

        assertThat(EnderIOMachineHandler.hasFinishedState(machine.catalog(),
                Collections.singletonList(stack(Items.APPLE, 1)), false, false)).isFalse();
    }

    // ------------------------------------------------------------------
    // 实例方法（生产路径）
    // ------------------------------------------------------------------

    /** 目录为 null 时 isValidTarget/canStart/pushMaterials 为 false，其余按契约降级。 */
    @Test
    public void testNullCatalogContract() {
        handler.setCatalog(null);

        assertThat(handler.isValidTarget(world, POS)).isFalse();
        assertThat(handler.canStart(world, POS, crafting(stack(Items.APPLE, 1)), null)).isFalse();
        assertThat(handler.pushMaterials(world, POS, crafting(stack(Items.APPLE, 1)), null, null)).isFalse();
        assertThat(handler.collectProducts(world, POS, null, null, null, null)).isEmpty();
        assertThat(handler.revertMaterials(world, POS, null, null)).isEmpty();
        assertThat(handler.clearOutputs(world, POS, null, null)).isEmpty();
        assertThat(handler.isIdle(world, POS, null, null)).isTrue();
        assertThat(handler.hasFinished(world, POS, null, null)).isTrue();
    }

    /** 推料走框架：全部放入返回 true 且 ingredients 不被修改；容量不足返回 false 且不留痕。 */
    @Test
    public void testPushMaterialsThroughFramework() {
        FakeItemHandler target = new FakeItemHandler(2);
        handler.setCatalog(facesLikeCatalog(target));

        InventoryCrafting inv = crafting(stack(Items.APPLE, 2), stack(Items.COAL, 1));
        assertThat(handler.pushMaterials(world, POS, inv, null, null)).isTrue();
        assertThat(target.getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(target.getStackInSlot(1).getItem()).isEqualTo(Items.COAL);
        assertThat(inv.getStackInSlot(0).getCount()).isEqualTo(2);

        FakeItemHandler full = new FakeItemHandler(1);
        full.setStack(0, stack(Items.BRICK, 64));
        handler.setCatalog(facesLikeCatalog(full));
        assertThat(handler.pushMaterials(world, POS, crafting(stack(Items.APPLE, 1)), null, null)).isFalse();
        assertThat(full.getStackInSlot(0).getItem()).isEqualTo(Items.BRICK);
    }

    /** 收集走框架：本批材料被排除，产物被取走；角色未知时不再额外收窄。 */
    @Test
    public void testCollectProductsThroughFramework() {
        FakeItemHandler target = new FakeItemHandler(2);
        target.setStack(0, stack(Items.APPLE, 3));
        target.setStack(1, stack(Items.COAL, 1));
        handler.setCatalog(facesLikeCatalog(target));

        List<ItemStack> collected = handler.collectProducts(world, POS, expected(Items.APPLE, 3),
                Collections.singletonList(stack(Items.COAL, 1)), null, null);

        assertThat(itemNames(collected)).containsExactly("minecraft:applex3");
        assertThat(target.getStackInSlot(1).getItem()).isEqualTo(Items.COAL);
    }

    /** 角色已知时只收输出槽：输入槽里的物品即使匹配预期产物也不动。 */
    @Test
    public void testCollectProductsRestrictedToOutputRoleSlots() {
        FakeItemHandler target = new FakeItemHandler(2);
        target.setStack(0, stack(Items.APPLE, 1)); // 输入角色
        target.setStack(1, stack(Items.APPLE, 1)); // 输出角色
        handler.setCatalog(roleCatalog(target, SlotRole.INPUT, SlotRole.OUTPUT));

        List<ItemStack> collected = handler.collectProducts(world, POS, expected(Items.APPLE, 1),
                Collections.emptyList(), null, null);

        assertThat(itemNames(collected)).containsExactly("minecraft:applex1");
        assertThat(target.getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
    }

    /**
     * 处理中（machine 报"处理中"）时 isIdle 为 false，即使输出槽已有产物.
     *
     * <p>处理中信号由测试替身注入的 {@code idleGate} 提供（框架指定的机器状态接缝）；
     * {@code AbstractMachineEntity#isActive()} 反射层在无头环境不可达，见类注释的覆盖缺口说明。</p>
     */
    @Test
    public void testIsIdleFalseWhileMachineWorking() {
        FakeItemHandler target = new FakeItemHandler(1);
        target.setStack(0, stack(Items.APPLE, 1));
        handler.setCatalog(facesLikeCatalog(target));

        handler.setWorking(true);
        assertThat(handler.isIdle(world, POS, Collections.emptyList(), null)).isFalse();

        handler.setWorking(false);
        assertThat(handler.isIdle(world, POS, Collections.emptyList(), null)).isTrue();
    }

    /**
     * 机器仍在处理时 hasFinished 为 false，即便本批输入已耗尽、槽位与输出队列都没有产物.
     *
     * <p>目标目录刻意留空（而不是"有产物残留"）：这样能把结果压成 false 的唯一变量就是"处理中"这一支。
     * 若在有残留产物时断言，hasFinished 会因产物判据返回 false 而"碰巧为绿"，实际测不到处理中信号
     * ——本用例上半段（未处理中 → 空目录应判完成）正是用来排除这种假绿。</p>
     *
     * <p>处理中信号同样来自测试替身注入的 {@code idleGate}（框架接缝）。
     * {@code AbstractMachineEntity#isActive()} 返回 true 这一真实反射支在无头环境不可达，
     * 需带真实 EIO TileEntity 的实机/集成夹具覆盖，见类注释。</p>
     */
    @Test
    public void testHasFinishedFalseWhileMachineActive() {
        FakeItemHandler emptyTarget = new FakeItemHandler(1);
        handler.setCatalog(facesLikeCatalog(emptyTarget));

        handler.setWorking(false);
        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isTrue();

        handler.setWorking(true);
        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /**
     * 解析不到 EIO 机器状态时的兜底口径（用<b>生产</b> idleGate，不经测试替身）.
     *
     * <p>兜底口径与重构前一致：{@code METHOD_IS_ACTIVE == null}（EIO 未安装或反射失败）时
     * {@code isMachineActive} 返回 false，即"无法判定 ⇒ 不视为处理中"（旧 handler 的
     * {@code isActive(te)} 在抓不到方法时同样返回 false）。此时 hasFinished 的结果完全由
     * "本批输入是否耗尽 + 输出范围内是否还有东西（槽位或队列）"决定：</p>
     * <ul>
     *     <li>输入已耗尽且输出范围空 → 完成（true）；</li>
     *     <li>本批材料仍留在输入槽 → 未完成；</li>
     *     <li>输入耗尽但输出槽有残留 → 未完成。</li>
     * </ul>
     * <p>同时确认 isIdle 在兜底口径下只认"输出范围有东西"，看不到状态信号也不会误判为空闲。</p>
     */
    @Test
    public void testHasFinishedFallbackWhenMachineStateUnresolvable() {
        RealGateHandler raw = new RealGateHandler();
        when(world.getTileEntity(POS)).thenReturn(mock(TileEntity.class));

        LegacyMachine machine = new LegacyMachine(4, LegacyMachine.inputOutput(2, 2));
        raw.setCatalog(machine.catalog());
        List<ItemStack> batch = Collections.singletonList(stack(Items.APPLE, 2));

        assertThat(raw.hasFinished(world, POS, batch, null)).isTrue();

        machine.set(0, stack(Items.APPLE, 2));
        assertThat(raw.hasFinished(world, POS, batch, null)).isFalse();

        machine.set(0, ItemStack.EMPTY);
        machine.set(2, stack(Items.BREAD, 1));
        assertThat(raw.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
        assertThat(raw.isIdle(world, POS, Collections.emptyList(), null)).isTrue();

        machine.set(2, ItemStack.EMPTY);
        assertThat(raw.hasFinished(world, POS, Collections.emptyList(), null)).isTrue();
        assertThat(raw.isIdle(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /** 推料 grace 期内即使已有可收物品也判为未空闲（沿用框架默认 grace）。 */
    @Test
    public void testIsIdleRespectsPushGraceWindow() {
        FakeItemHandler target = new FakeItemHandler(1);
        target.setStack(0, stack(Items.APPLE, 1));
        handler.setCatalog(facesLikeCatalog(target));

        TargetSession inGrace = mock(TargetSession.class);
        when(inGrace.isPushGraceElapsed(anyLong(), anyInt())).thenReturn(false);
        assertThat(handler.isIdle(world, POS, Collections.emptyList(), inGrace)).isFalse();

        TargetSession elapsed = mock(TargetSession.class);
        when(elapsed.isPushGraceElapsed(anyLong(), anyInt())).thenReturn(true);
        assertThat(handler.isIdle(world, POS, Collections.emptyList(), elapsed)).isTrue();
    }

    /** 机器空闲且输出槽有东西时 hasFinished 为 false，收走后才为 true。 */
    @Test
    public void testHasFinishedTracksOutputResidue() {
        FakeItemHandler target = new FakeItemHandler(1);
        target.setStack(0, stack(Items.APPLE, 1));
        handler.setCatalog(facesLikeCatalog(target));

        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();

        target.extractItem(0, 1, false);
        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isTrue();
    }

    /** 清空输出只认 OUTPUT 角色槽：输入/升级槽不被清。 */
    @Test
    public void testClearOutputsOnlyTouchesOutputRoleSlots() {
        FakeItemHandler target = new FakeItemHandler(3);
        target.setStack(0, stack(Items.APPLE, 1));
        target.setStack(1, stack(Items.BREAD, 1));
        target.setStack(2, stack(Items.COAL, 1));
        handler.setCatalog(roleCatalog(target, SlotRole.INPUT, SlotRole.OUTPUT, SlotRole.UPGRADE));

        TargetSession session = mock(TargetSession.class);
        when(session.getInputs()).thenReturn(Collections.singletonList(stack(Items.APPLE, 1)));
        when(session.getExpectedOutputs()).thenReturn(expected(Items.BREAD, 1));

        List<ItemStack> cleared = handler.clearOutputs(world, POS, null, session);

        assertThat(itemNames(cleared)).containsExactly("minecraft:breadx1");
        assertThat(target.getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(target.getStackInSlot(2).getItem()).isEqualTo(Items.COAL);
    }

    /** 回退只取 session 快照内的本批材料；session 为 null 时什么都不回退。 */
    @Test
    public void testRevertMaterialsUsesSessionSnapshot() {
        FakeItemHandler target = new FakeItemHandler(3);
        target.setStack(0, stack(Items.APPLE, 2));
        target.setStack(1, stack(Items.COAL, 4));
        target.setStack(2, stack(Items.BREAD, 1));
        handler.setCatalog(roleCatalog(target, SlotRole.INPUT, SlotRole.INPUT, SlotRole.OUTPUT));

        TargetSession session = mock(TargetSession.class);
        when(session.getInputs()).thenReturn(Collections.singletonList(stack(Items.APPLE, 1)));

        List<ItemStack> reverted = handler.revertMaterials(world, POS, null, session);

        assertThat(itemNames(reverted)).containsExactly("minecraft:applex2");
        assertThat(target.getStackInSlot(1).getCount()).isEqualTo(4);
        assertThat(target.getStackInSlot(2).getItem()).isEqualTo(Items.BREAD);

        assertThat(handler.revertMaterials(world, POS, null, null)).isEmpty();
    }

    /** 与 facesOf 等价的无角色目录：推料落到全部槽位（框架降级）。 */
    private static ISlotCatalog facesLikeCatalog(IItemHandler target) {
        ISlotCatalog catalog = ISlotCatalog.builder().addAll(target, null, SlotRole.UNKNOWN).build();
        assertThat(catalog.rolesKnown()).isFalse();
        return catalog;
    }

    private static ISlotCatalog roleCatalog(IItemHandler target, SlotRole... roles) {
        return ISlotCatalog.builder().addAll(target, null, roles).build();
    }
}
