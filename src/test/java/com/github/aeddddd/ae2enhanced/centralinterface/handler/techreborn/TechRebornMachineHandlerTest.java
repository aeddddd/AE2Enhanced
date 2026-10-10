package com.github.aeddddd.ae2enhanced.centralinterface.handler.techreborn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRef;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.FakeItemHandler;

/**
 * {@link TechRebornMachineHandler} 测试。
 *
 * <p>测试环境没有 Tech Reborn，所有反射路径都不可能成功（{@code AVAILABLE == false}），
 * 因此覆盖面分两块：</p>
 * <ul>
 *     <li><b>不依赖反射的部分</b>：blockId 匹配集合、能力集合、槽位→角色映射
 *     （{@link TechRebornMachineHandler#slotRoles}）、空闲判定的纯逻辑
 *     （{@code machineInProgress} / {@code crafterInProgress}）、
 *     以及把 {@code Inventory#contents} 数组包成 {@code IItemHandler} 的
 *     {@link TechRebornMachineHandler.ReflectedInventory}；</li>
 *     <li><b>依赖目录的部分</b>：用测试替身覆盖 {@code catalogFor}/{@code idleGate}，
 *     把目录换成 {@link FakeItemHandler} 驱动的真实目录，
 *     于是本类覆盖的 canStart / isIdle / hasFinished / collectProducts
 *     与框架默认提供的 pushMaterials / clearOutputs / revertMaterials 全部走生产代码路径。</li>
 * </ul>
 *
 * <h3>真正被覆盖的（这些是回归网）</h3>
 * <ul>
 *     <li>blockId 匹配集合与 getCapabilities；</li>
 *     <li>槽位→角色映射（slotRoles / buildCatalog：输入/输出/电池/UNKNOWN、outputSlots 为 null 的
 *     流体复制机形状、越界下标、目录顺序与下标）；</li>
 *     <li>目录为 null 时的降级口径（isValidTarget/canStart/pushMaterials=false、
 *     collect/clear/revert=空表、isIdle/hasFinished=true）；</li>
 *     <li>两阶段推料原子性：模拟阶段失败不留痕、后一味放不下整批失败、真实阶段失败按记录回滚；</li>
 *     <li>canStart 的输入槽门禁；collectProducts 三阶段（阶段一整槽取走、排除本批输入、不动 UNKNOWN 槽）；</li>
 *     <li>isIdle 的"输出槽非空"口径 + 采纳 idleGate 结论 + 推料保护窗口；</li>
 *     <li>hasFinished 的四条口径（含"输出槽物品与本批材料同型时仍算未完成"这条覆盖框架默认的关键点）；</li>
 *     <li>clearOutputs 只清输出槽、revertMaterials 只回退本批材料；</li>
 *     <li>{@link TechRebornMachineHandler.ReflectedInventory} 的插入/叠加/上限取自库存 stackLimit/
 *     异类拒收/越界/simulate 语义/抽取/整槽取空；</li>
 *     <li>真实变更后必须调用 {@code TileEntity#markDirty()}（模拟阶段不得触发）；</li>
 *     <li>machineInProgress / crafterInProgress 纯逻辑。</li>
 * </ul>
 *
 * <h3>已知覆盖缺口（无头单测到不了，<b>不等于已测</b>）</h3>
 * <ul>
 *     <li><b>"机器处理中不得开工 / 不得判完成"</b>：canStart/hasFinished 的忙判定来自
 *     {@code isProcessing} 的反射读取，需要<b>带真实 TR TE 的实机/集成夹具</b>——
 *     无头环境没有 TR（句柄全为 null），即便注入句柄，Mockito mock 的 {@code TileEntity} 也不是
 *     {@code TileMachine}/{@code TileGenericMachine} 的实例。单测只能覆盖"该信号解析不出来"时的
 *     兜底口径；生产环境里"目录可用 ⇒ 目标是真实 TR 机器 ⇒ isProcessing 可解析"，
 *     所以该组合只有测试替身造得出来，实机不会出现。</li>
 *     <li>{@code isProcessing} 对 {@code TileMachine#progress/operationLength} 与
 *     {@code RecipeCrafter#currentRecipe/currentTickTime} 的实际字段读取与类型分派
 *     （纯逻辑 machineInProgress/crafterInProgress 已覆盖，反射读取没有）；</li>
 *     <li>{@code catalogFor} 的反射解析路径（contents/inputSlots 读取、energySlot、AVAILABLE 门禁）
 *     ——无头环境整条不可达；</li>
 *     <li>{@code isSlotAcceptable} 的"机器自身过滤器拒绝"分支：需要注入
 *     FIELD_SLOT_CONFIGURATION / IS_ITEM_VALID_FOR_SLOT_METHOD，无头环境两者为 null，
 *     只走到了"放行"这一支；</li>
 *     <li>{@code markInventoryChanged} 里 RebornCore {@code Inventory#isDirty/hasChanged}
 *     两个字段的写位（需要真实 {@code reborncore} 类）；能真实驱动的是
 *     {@code TileEntity#markDirty()} 的调用时机。</li>
 * </ul>
 */
public class TechRebornMachineHandlerTest {

    private static final BlockPos POS = new BlockPos(3, 64, 3);

    private TestableHandler handler;
    private World world;
    private TileEntity tile;

    @BeforeAll
    public static void boot() throws Exception {
        // 测试环境"没有安装 TR"：让 Loader.isModLoaded 短路返回 false，
        // 处理器静态初始化于是只填 blockId 表、不做任何反射解析
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
        this.tile = mock(TileEntity.class);
        when(world.getTileEntity(POS)).thenReturn(tile);
    }

    /** 绕过反射目录的测试替身：生产实现全部保留，只有目录/空闲策略由用例注入。 */
    private static final class TestableHandler extends TechRebornMachineHandler {

        private ISlotCatalog catalog;
        private MachineIdle idle = MachineIdle.none();

        void setCatalog(ISlotCatalog catalog) {
            this.catalog = catalog;
        }

        void setIdle(MachineIdle idle) {
            this.idle = idle;
        }

        @Override
        protected ISlotCatalog catalogFor(World world, BlockPos pos) {
            return this.catalog;
        }

        @Override
        protected MachineIdle idleGate(World world, BlockPos pos) {
            return this.idle;
        }
    }

    private static InventoryCrafting crafting(ItemStack... stacks) {
        InventoryCrafting inv = new InventoryCrafting(mock(Container.class), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            inv.setInventorySlotContents(i, stacks[i]);
        }
        return inv;
    }

    /** 工业离心机式布局：输入 {0,1}、输出 {2}、3 号槽不可触碰（电池/流体容器）。 */
    private ISlotCatalog installCentrifugeStyleCatalog(IItemHandler itemHandler) {
        return installCatalog(itemHandler, 4, new int[] {0, 1}, new int[] {2}, 3);
    }

    /** 单输入槽布局：便于构造"放不下"的原子性用例。 */
    private ISlotCatalog installSingleInputCatalog(IItemHandler itemHandler) {
        return installCatalog(itemHandler, 2, new int[] {0}, new int[] {1}, -1);
    }

    private ISlotCatalog installCatalog(IItemHandler itemHandler, int slotCount, int[] inputSlots, int[] outputSlots,
                                        int energySlot) {
        ISlotCatalog catalog = TechRebornMachineHandler.buildCatalog(
                itemHandler, slotCount, inputSlots, outputSlots, energySlot);
        this.handler.setCatalog(catalog);
        return catalog;
    }

    // ------------------------------------------------------------------
    // blockId 匹配与能力
    // ------------------------------------------------------------------

    /** 显式枚举的 19 个机器方块全部匹配。 */
    @Test
    public void testCanHandleMatchesDeclaredMachineIds() {
        String[] ids = {
                "techreborn:alloy_smelter", "techreborn:assembling_machine", "techreborn:chemical_reactor",
                "techreborn:compressor", "techreborn:extractor", "techreborn:grinder",
                "techreborn:industrial_centrifuge", "techreborn:plate_bending_machine",
                "techreborn:solid_canning_machine", "techreborn:wire_mill",
                "techreborn:distillation_tower", "techreborn:fluid_replicator", "techreborn:implosion_compressor",
                "techreborn:industrial_blast_furnace", "techreborn:industrial_grinder",
                "techreborn:industrial_sawmill", "techreborn:vacuum_freezer",
                "techreborn:industrial_electrolyzer", "techreborn:scrapboxinator",
        };
        for (String id : ids) {
            assertThat(handler.canHandle(id)).as(id).isTrue();
        }
    }

    /** 其余 techreborn 方块（线缆/储电单元等）不得被本处理器抢走匹配。 */
    @Test
    public void testCanHandleRejectsOtherTechRebornBlocks() {
        assertThat(handler.canHandle("techreborn:cable")).isFalse();
        assertThat(handler.canHandle("techreborn:quantum_chest")).isFalse();
        assertThat(handler.canHandle("techreborn:")).isFalse();
        assertThat(handler.canHandle("minecraft:furnace")).isFalse();
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

    /** 输入槽 → INPUT，输出槽 → OUTPUT，电池槽 → UPGRADE，且推料目标只有输入槽。 */
    @Test
    public void testSlotRoleMappingInputsOutputsAndBatterySlot() {
        SlotRole[] roles = TechRebornMachineHandler.slotRoles(4, new int[] {0, 1}, new int[] {2}, 3);
        assertThat(roles).containsExactly(
                SlotRole.INPUT, SlotRole.INPUT, SlotRole.OUTPUT, SlotRole.UPGRADE);
    }

    /** 不属于输入/输出/电池槽的槽位保持 UNKNOWN（工业磨粉机 6 号槽是 TR 自己的容器产出槽）。 */
    @Test
    public void testSlotRoleMappingLeavesOtherSlotsUnknown() {
        SlotRole[] roles = TechRebornMachineHandler.slotRoles(8, new int[] {0, 1}, new int[] {2, 3, 4, 5}, 7);
        assertThat(roles).containsExactly(
                SlotRole.INPUT, SlotRole.INPUT, SlotRole.OUTPUT, SlotRole.OUTPUT,
                SlotRole.OUTPUT, SlotRole.OUTPUT, SlotRole.UNKNOWN, SlotRole.UPGRADE);

        ISlotCatalog catalog = TechRebornMachineHandler.buildCatalog(new FakeItemHandler(8), 8,
                new int[] {0, 1}, new int[] {2, 3, 4, 5}, 7);
        assertThat(catalog.rolesKnown()).isTrue();
        assertThat(catalog.inputSlots()).hasSize(2);
        assertThat(catalog.outputSlots()).hasSize(4);
        assertThat(catalog.pushTargets()).hasSize(2);
        assertThat(catalog.slots().get(6).role()).isEqualTo(SlotRole.UNKNOWN);
    }

    /**
     * 流体复制机把 {@code RecipeCrafter#outputSlots} 传成 null（产物是流体）：
     * 目录里只有输入槽，输出槽集合为空，不得因此把目标整台判为无效，
     * 否则它的流体产物也收不到了。
     */
    @Test
    public void testSlotRoleMappingWithoutOutputSlots() {
        SlotRole[] roles = TechRebornMachineHandler.slotRoles(4, new int[] {0}, null, 3);
        assertThat(roles).containsExactly(
                SlotRole.INPUT, SlotRole.UNKNOWN, SlotRole.UNKNOWN, SlotRole.UPGRADE);

        ISlotCatalog catalog = TechRebornMachineHandler.buildCatalog(new FakeItemHandler(4), 4,
                new int[] {0}, null, 3);
        assertThat(catalog.rolesKnown()).isTrue();
        assertThat(catalog.outputSlots()).isEmpty();
        assertThat(catalog.inputSlots()).hasSize(1);
    }

    /** 越界下标（槽位数组比库存长 / 负下标 / 电池槽未知时为 -1）不得进目录、也不得抛异常。 */
    @Test
    public void testSlotRoleMappingIgnoresOutOfRangeIndices() {
        SlotRole[] roles = TechRebornMachineHandler.slotRoles(2, new int[] {0, 9, -1}, new int[] {1, 5}, 7);
        assertThat(roles).containsExactly(SlotRole.INPUT, SlotRole.OUTPUT);
    }

    // ------------------------------------------------------------------
    // 目录不可用时的降级语义
    // ------------------------------------------------------------------

    /** 目录为 null（机器类型不符 / 反射句柄缺失）时，框架契约全部按"目标无效"降级。 */
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

    // ------------------------------------------------------------------
    // 推料（框架两阶段原子实现，目录由本处理器给出）
    // ------------------------------------------------------------------

    /** 材料只进输入槽，产物槽与未知角色槽不被写入。 */
    @Test
    public void testPushMaterialsInsertsIntoInputSlotsOnly() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        installCentrifugeStyleCatalog(itemHandler);

        InventoryCrafting inv = crafting(new ItemStack(Items.APPLE, 2), new ItemStack(Items.COAL, 1));

        assertThat(handler.pushMaterials(world, POS, inv, null, null)).isTrue();
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(2);
        assertThat(itemHandler.getStackInSlot(1).getItem()).isEqualTo(Items.COAL);
        assertThat(itemHandler.getStackInSlot(2).isEmpty()).isTrue();
        assertThat(itemHandler.getStackInSlot(3).isEmpty()).isTrue();
    }

    /** 空配方视为推送成功，不触碰任何槽位。 */
    @Test
    public void testPushMaterialsEmptyIngredientsSucceeds() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        installCentrifugeStyleCatalog(itemHandler);

        assertThat(handler.pushMaterials(world, POS, crafting(), null, null)).isTrue();
        assertThat(itemHandler.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(itemHandler.getStackInSlot(1).isEmpty()).isTrue();
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
     * 因此不会出现"推了一半"的中间态。
     */
    @Test
    public void testPushMaterialsAtomicWhenLaterItemDoesNotFit() {
        FakeItemHandler itemHandler = new FakeItemHandler(2);
        itemHandler.setStack(0, new ItemStack(Items.APPLE, 63));
        installSingleInputCatalog(itemHandler);

        InventoryCrafting inv = crafting(new ItemStack(Items.APPLE, 1), new ItemStack(Items.DIAMOND, 1));

        assertThat(handler.pushMaterials(world, POS, inv, null, null)).isFalse();
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(63);
    }

    /**
     * 真实插入阶段意外失败时，框架必须把已经写进机器的材料按记录回退掉（两阶段原子性的第二阶段回滚）。
     *
     * <p>用"模拟阶段放行、真实阶段拒收"的库存制造 simulate 与 modulate 不一致的场景
     * （{@link ItemTransferHelper#push} 注释里明确承认这类机器存在）：模拟全过，
     * 真实阶段第二味材料被拒，框架逆序回退第一味已经写进去的部分。</p>
     */
    @Test
    public void testPushMaterialsRollsBackWhenRealInsertFailsAfterSuccessfulSimulation() {
        RejectOnRealInsertHandler itemHandler = new RejectOnRealInsertHandler(2, Items.DIAMOND);
        installCatalog(itemHandler, 2, new int[] {0, 1}, null, -1);

        InventoryCrafting inv = crafting(new ItemStack(Items.APPLE, 1), new ItemStack(Items.DIAMOND, 1));

        assertThat(handler.pushMaterials(world, POS, inv, null, null)).isFalse();
        // 计数证明苹果确实被真实写入过（不是"从未插入"的空跑），随后被回滚
        assertThat(itemHandler.getAcceptedRealInserts()).isEqualTo(1);
        assertThat(itemHandler.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(itemHandler.getStackInSlot(1).isEmpty()).isTrue();
    }

    /** 模拟阶段放行、真实阶段拒收指定物品的库存：用于驱动框架推料的第二阶段回滚。 */
    private static final class RejectOnRealInsertHandler implements IItemHandler {

        private final FakeItemHandler delegate;
        private final Item rejected;
        private int acceptedRealInserts;

        RejectOnRealInsertHandler(int slots, Item rejected) {
            this.delegate = new FakeItemHandler(slots);
            this.rejected = rejected;
        }

        int getAcceptedRealInserts() {
            return this.acceptedRealInserts;
        }

        @Override
        public int getSlots() {
            return this.delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!simulate && stack.getItem() == this.rejected) {
                return stack;
            }
            ItemStack left = this.delegate.insertItem(slot, stack, simulate);
            if (!simulate && left.getCount() < stack.getCount()) {
                this.acceptedRealInserts++;
            }
            return left;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return this.delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return this.delegate.getSlotLimit(slot);
        }
    }

    // ------------------------------------------------------------------
    // canStart（覆盖框架默认：输入槽不得预置异物；"机器忙不得开工"一支为已知覆盖缺口）
    // ------------------------------------------------------------------

    /**
     * 目标不是 TR 机器（或"是否在处理"解析不出来）时，{@code canStart} 的忙判定按兜底口径返回
     * "不忙"，是否放行完全由输入槽门禁决定。
     *
     * <p><b>兜底口径为什么是"不忙"</b>：{@code isProcessing} 依赖 {@code TileMachine}/
     * {@code TileGenericMachine} 的类型与字段句柄；句柄缺失（无 TR）或目标不是真实 TR 实例时，
     * 两条分支都取不到值，方法返回 false。这是重构前就有的行为——原实现的
     * {@code getCrafter}/字段读取失败同样退化为"未在处理"，本类逐字保留。</p>
     *
     * <p><b>已知覆盖缺口（不等于已测）</b>："机器正在处理上一批时拒收新材料"这一支需要
     * <b>带真实 TR TE 的实机/集成夹具</b>才能到达：无头环境没有 TR，{@code TILE_MACHINE_CLASS}
     * 等句柄全为 null；即便注入了句柄，Mockito mock 的 {@code TileEntity} 也不是
     * {@code TileMachine}/{@code TileGenericMachine} 的实例，{@code isInstance} 恒 false。
     * 生产环境里"目录可用 ⇒ 目标是真实 TR 机器 ⇒ isProcessing 可解析"，因此
     * "目录非空但忙判定不可解析"只是测试替身造出来的组合，实机不会出现。</p>
     */
    @Test
    public void testCanStartWithoutResolvableBusySignalUsesFallback() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        installCentrifugeStyleCatalog(itemHandler);

        // 忙判定解析不出来 → 退化为"不忙"；输入槽为空 → 放行
        assertThat(handler.canStart(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null)).isTrue();

        // 同一兜底口径下，输入槽门禁（可达的那条）仍然能拒收
        itemHandler.setStack(0, new ItemStack(Items.BRICK, 1));
        assertThat(handler.canStart(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null)).isFalse();
    }

    /** 输入槽里已有本批之外的物品（玩家放入/上一批残留）时拒收。 */
    @Test
    public void testCanStartFalseWhenInputSlotHoldsForeignItem() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(0, new ItemStack(Items.BRICK, 1));
        installCentrifugeStyleCatalog(itemHandler);

        assertThat(handler.canStart(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null)).isFalse();
    }

    /**
     * 输入槽里只有本批材料（或为空）时允许开工；输出槽残留不参与 canStart 判定（重构前如此，
     * 残留由发配前的 clearOutputs 清理）。
     *
     * <p>本用例覆盖的是 canStart 的输入槽门禁；"机器忙"门禁的覆盖缺口见上方
     * {@link #testCanStartWithoutResolvableBusySignalUsesFallback()} 的说明。</p>
     */
    @Test
    public void testCanStartTrueWhenInputSlotsHoldOnlyBatchMaterials() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(0, new ItemStack(Items.APPLE, 4));
        itemHandler.setStack(2, new ItemStack(Items.DIAMOND, 1)); // 产物槽残留在旧实现里不参与判定
        installCentrifugeStyleCatalog(itemHandler);

        assertThat(handler.canStart(world, POS, crafting(new ItemStack(Items.APPLE, 4)), null)).isTrue();
    }

    // ------------------------------------------------------------------
    // collectProducts（覆盖框架默认：三阶段 + 阶段一整槽取走）
    // ------------------------------------------------------------------

    /** 阶段一按"整槽取走"处理，不按样板声明的数量截断（框架默认会拆成两条）。 */
    @Test
    public void testCollectProductsTakesWholeExpectedSlot() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(2, new ItemStack(Items.APPLE, 5));
        installCentrifugeStyleCatalog(itemHandler);

        IAEItemStack[] expected = {AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 3))};
        List<ItemStack> collected = handler.collectProducts(world, POS, expected, Collections.emptyList(), null, null);

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(collected.get(0).getCount()).isEqualTo(5);
        assertThat(itemHandler.getStackInSlot(2).isEmpty()).isTrue();
    }

    /**
     * 阶段二收输出槽残余，阶段三收输入槽里不属于本批输入的物品，
     * 而本批材料（输入槽里的那部分）绝不被当作产物收走。
     */
    @Test
    public void testCollectProductsSkipsBatchInputButTakesForeignInput() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(0, new ItemStack(Items.COAL, 1));    // 本批材料残留 → 保留
        itemHandler.setStack(1, new ItemStack(Items.APPLE, 1));   // 与"产物"同型的输入槽异物 → 收走
        itemHandler.setStack(2, new ItemStack(Items.DIAMOND, 2)); // 输出槽残余 → 收走
        itemHandler.setStack(3, new ItemStack(Items.BRICK, 1));   // UNKNOWN 角色 → 不碰
        installCentrifugeStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        List<ItemStack> collected = handler.collectProducts(world, POS, null, inputs, null, null);

        // 先输出槽（阶段 2）再输入槽异物（阶段 3）
        assertThat(collected).hasSize(2);
        assertThat(collected.get(0).getItem()).isEqualTo(Items.DIAMOND);
        assertThat(collected.get(1).getItem()).isEqualTo(Items.APPLE);
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.COAL);
        assertThat(itemHandler.getStackInSlot(3).getItem()).isEqualTo(Items.BRICK);
    }

    /** 预期产物优先于副产物出现在返回列表前部。 */
    @Test
    public void testCollectProductsExpectedBeforeRemaining() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(2, new ItemStack(Items.APPLE, 1));
        itemHandler.setStack(1, new ItemStack(Items.STICK, 1)); // 输入槽异物
        installCentrifugeStyleCatalog(itemHandler);

        IAEItemStack[] expected = {AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 1))};
        List<ItemStack> collected = handler.collectProducts(world, POS, expected, null, null, null);

        assertThat(collected).hasSize(2);
        assertThat(collected.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(collected.get(1).getItem()).isEqualTo(Items.STICK);
    }

    // ------------------------------------------------------------------
    // isIdle / hasFinished（覆盖框架默认）
    // ------------------------------------------------------------------

    /** 输出槽有内容即可收集；输入槽有内容但输出槽为空时不可收集。 */
    @Test
    public void testIsIdleFollowsOutputSlotsOnly() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        installCentrifugeStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        assertThat(handler.isIdle(world, POS, inputs, null)).isFalse();

        itemHandler.setStack(1, new ItemStack(Items.COAL, 1)); // 输入槽有料、产物还没出
        assertThat(handler.isIdle(world, POS, inputs, null)).isFalse();

        itemHandler.setStack(2, new ItemStack(Items.DIAMOND, 1));
        assertThat(handler.isIdle(world, POS, inputs, null)).isTrue();
        // 只判空，不做任何抽取
        assertThat(itemHandler.getStackInSlot(2).getCount()).isEqualTo(1);
    }

    /**
     * isIdle 确实采纳 idleGate 的"正在处理"结论：即便输出槽已有产物，策略说"在处理"就不判空闲。
     *
     * <p>与 canStart/hasFinished 不同，isIdle 走的是框架规定的 {@code idleGate} 钩子，所以这里注入
     * 一个恒 true 的策略即可到达该分支——覆盖的是<b>本类的接线</b>（gate 被询问、结论被采纳）。</p>
     *
     * <p><b>已知覆盖缺口</b>：生产环境 idleGate 背后的真实判定
     * （{@code TileMachine#progress/operationLength}、{@code RecipeCrafter#currentRecipe/currentTickTime}
     * 的反射读取）仍需要带真实 TR TE 的实机/集成夹具才能覆盖。</p>
     */
    @Test
    public void testIsIdleConsultsIdleGate() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(2, new ItemStack(Items.DIAMOND, 1));
        installCentrifugeStyleCatalog(itemHandler);
        handler.setIdle((w, p, te) -> true);

        assertThat(handler.isIdle(world, POS, Collections.emptyList(), null)).isFalse();

        // 同一内容下把策略换成"不忙"，结论随之变为可收集
        handler.setIdle(MachineIdle.none());
        assertThat(handler.isIdle(world, POS, Collections.emptyList(), null)).isTrue();
    }

    /** 推料保护窗口内一律判为未空闲（推料与机器开工不在同一 tick）。 */
    @Test
    public void testIsIdleRespectsPushGrace() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(2, new ItemStack(Items.DIAMOND, 1));
        installCentrifugeStyleCatalog(itemHandler);

        TargetSession session = mock(TargetSession.class);
        when(session.isPushGraceElapsed(anyLong(), anyInt())).thenReturn(false);
        assertThat(handler.isIdle(world, POS, Collections.emptyList(), session)).isFalse();

        when(session.isPushGraceElapsed(anyLong(), anyInt())).thenReturn(true);
        assertThat(handler.isIdle(world, POS, Collections.emptyList(), session)).isTrue();
    }

    /**
     * 目标不是 TR 机器（或"是否在处理"解析不出来）时，{@code hasFinished} 的忙判定按兜底口径返回
     * "不忙"，是否完成完全由"输入槽无本批材料 + 输出槽为空"决定。
     *
     * <p><b>兜底口径为什么是"不忙"</b>：{@code isProcessing} 依赖 {@code TileMachine}/
     * {@code TileGenericMachine} 的类型与字段句柄，句柄缺失（无 TR）或目标不是真实 TR 实例时
     * 两条分支都取不到值、返回 false（重构前原实现同样如此，本类逐字保留）。</p>
     *
     * <p><b>已知覆盖缺口（不等于已测）</b>："机器还在处理时不得判完成"（TileMachine 开工首 tick
     * 输入已搬进 {@code itemOutputsBuffer}、输入槽已空而产物还没落槽，此刻若判完成就会把产物丢掉）
     * 需要<b>带真实 TR TE 的实机/集成夹具</b>：无头环境 {@code TILE_MACHINE_CLASS} 等句柄全为 null，
     * 且 Mockito mock 的 {@code TileEntity} 不是 {@code TileMachine}/{@code TileGenericMachine} 的实例，
     * {@code isInstance} 恒 false。生产环境里"目录可用 ⇒ 目标是真实 TR 机器 ⇒ isProcessing 可解析"，
     * 因此该组合只有测试替身能造出来。</p>
     */
    @Test
    public void testHasFinishedWithoutResolvableBusySignalUsesFallback() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        installCentrifugeStyleCatalog(itemHandler);

        // 输入槽无本批残留、输出槽为空 → 兜底口径判定"已完成"
        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isTrue();

        // 产物还在输出槽 → 即使忙判定不可解析，也仍然不算完成（可达的真实覆盖）
        itemHandler.setStack(2, new ItemStack(Items.DIAMOND, 1));
        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /** 输入槽还留着本批材料时不算完成。 */
    @Test
    public void testHasFinishedFalseWithRemainingBatchInput() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(1, new ItemStack(Items.COAL, 2));
        installCentrifugeStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        assertThat(handler.hasFinished(world, POS, inputs, null)).isFalse();
    }

    /** 输出槽还有残留时不算完成。 */
    @Test
    public void testHasFinishedFalseWithProductLeft() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(2, new ItemStack(Items.DIAMOND, 1));
        installCentrifugeStyleCatalog(itemHandler);

        assertThat(handler.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /**
     * 输出槽里的物品即便与本批材料同型，也算"还没收完"。
     *
     * <p>这是覆盖框架默认的关键点：框架默认的产物判据会把与本批材料同型的物品排除，
     * 于是此处会返回 true 并提前结束会话，把那个物品永远留在机器里。</p>
     */
    @Test
    public void testHasFinishedFalseWhenOutputHoldsItemMatchingBatchMaterial() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(2, new ItemStack(Items.COAL, 1));
        installCentrifugeStyleCatalog(itemHandler);

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        assertThat(handler.hasFinished(world, POS, inputs, null)).isFalse();
    }

    /** 输入已耗尽且输入槽无本批残留、输出槽为空 → 完成。 */
    @Test
    public void testHasFinishedTrueWhenDrained() {
        installCentrifugeStyleCatalog(new FakeItemHandler(4));

        List<ItemStack> inputs = Collections.singletonList(new ItemStack(Items.COAL, 1));
        assertThat(handler.hasFinished(world, POS, inputs, null)).isTrue();
    }

    // ------------------------------------------------------------------
    // clearOutputs / revertMaterials（框架默认实现，角色由目录裁定）
    // ------------------------------------------------------------------

    /** 清空只发生在输出槽：输入槽残留与未知角色槽（电池/流体容器）不被动。 */
    @Test
    public void testClearOutputsClearsOnlyOutputSlots() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(0, new ItemStack(Items.COAL, 1));
        itemHandler.setStack(2, new ItemStack(Items.DIAMOND, 3));
        itemHandler.setStack(3, new ItemStack(Items.BRICK, 1));
        installCentrifugeStyleCatalog(itemHandler);

        TargetSession session = mock(TargetSession.class);
        when(session.getInputs()).thenReturn(Collections.singletonList(new ItemStack(Items.COAL, 1)));

        List<ItemStack> cleared = handler.clearOutputs(world, POS, null, session);

        assertThat(cleared).hasSize(1);
        assertThat(cleared.get(0).getItem()).isEqualTo(Items.DIAMOND);
        assertThat(cleared.get(0).getCount()).isEqualTo(3);
        assertThat(itemHandler.getStackInSlot(2).isEmpty()).isTrue();
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.COAL);
        assertThat(itemHandler.getStackInSlot(3).getItem()).isEqualTo(Items.BRICK);
    }

    /** 回退只取本批材料：输入槽里的异物与输出槽的产物都不动。 */
    @Test
    public void testRevertMaterialsOnlyTakesBatchInputs() {
        FakeItemHandler itemHandler = new FakeItemHandler(4);
        itemHandler.setStack(0, new ItemStack(Items.COAL, 2));
        itemHandler.setStack(1, new ItemStack(Items.APPLE, 1));
        itemHandler.setStack(2, new ItemStack(Items.DIAMOND, 1));
        installCentrifugeStyleCatalog(itemHandler);

        TargetSession session = mock(TargetSession.class);
        when(session.getInputs()).thenReturn(Collections.singletonList(new ItemStack(Items.COAL, 1)));

        List<ItemStack> reverted = handler.revertMaterials(world, POS, null, session);

        assertThat(reverted).hasSize(1);
        assertThat(reverted.get(0).getItem()).isEqualTo(Items.COAL);
        assertThat(reverted.get(0).getCount()).isEqualTo(2);
        assertThat(itemHandler.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(itemHandler.getStackInSlot(1).getItem()).isEqualTo(Items.APPLE);
        assertThat(itemHandler.getStackInSlot(2).getItem()).isEqualTo(Items.DIAMOND);
    }

    // ------------------------------------------------------------------
    // 空闲判定纯逻辑与库存包装
    // ------------------------------------------------------------------

    /** TileMachine 体系：progress 首 tick 为 0，operationLength 非零同样表示在处理。 */
    @Test
    public void testMachineInProgressPrimitive() {
        assertThat(TechRebornMachineHandler.machineInProgress(0, 0)).isFalse();
        assertThat(TechRebornMachineHandler.machineInProgress(1, 0)).isTrue();
        assertThat(TechRebornMachineHandler.machineInProgress(0, 200)).isTrue();
    }

    /** TileGenericMachine 体系：已选出配方、或已走过进度都算在处理。 */
    @Test
    public void testCrafterInProgressPrimitive() {
        assertThat(TechRebornMachineHandler.crafterInProgress(false, 0)).isFalse();
        assertThat(TechRebornMachineHandler.crafterInProgress(true, 0)).isTrue();
        assertThat(TechRebornMachineHandler.crafterInProgress(false, 3)).isTrue();
    }

    /** 反射库存包装：同类叠加、异类拒收、上限取自库存 stackLimit、simulate 不改状态。 */
    @Test
    public void testReflectedInventoryInsertSemantics() {
        ItemStack[] contents = {ItemStack.EMPTY, new ItemStack(Items.APPLE, 10)};
        TechRebornMachineHandler.ReflectedInventory inventory =
                new TechRebornMachineHandler.ReflectedInventory(contents, new Object(), tile, 16);

        assertThat(inventory.getSlots()).isEqualTo(2);
        assertThat(inventory.getSlotLimit(0)).isEqualTo(16);

        assertThat(inventory.insertItem(0, new ItemStack(Items.COAL, 8), false).isEmpty()).isTrue();
        assertThat(contents[0].getCount()).isEqualTo(8);

        // 上限 16：还能再放 8 个，余下 12 个退回
        ItemStack leftover = inventory.insertItem(0, new ItemStack(Items.COAL, 20), false);
        assertThat(leftover.getCount()).isEqualTo(12);
        assertThat(contents[0].getCount()).isEqualTo(16);

        // 异类物品不得混入已有堆
        ItemStack rejected = inventory.insertItem(0, new ItemStack(Items.BRICK, 1), false);
        assertThat(rejected.getCount()).isEqualTo(1);
        assertThat(contents[0].getItem()).isEqualTo(Items.COAL);

        // simulate 不改变内容
        assertThat(inventory.insertItem(1, new ItemStack(Items.APPLE, 5), true).isEmpty()).isTrue();
        assertThat(contents[1].getCount()).isEqualTo(10);

        // 越界下标原样退回（目录不会给出越界引用，这里只验证不越界访问）
        assertThat(inventory.insertItem(9, new ItemStack(Items.APPLE, 1), false).getCount()).isEqualTo(1);
    }

    /** 反射库存包装：抽取按数量与现有量的较小值，整槽取空后写回 ItemStack.EMPTY。 */
    @Test
    public void testReflectedInventoryExtractSemantics() {
        ItemStack[] contents = {new ItemStack(Items.APPLE, 6)};
        TechRebornMachineHandler.ReflectedInventory inventory =
                new TechRebornMachineHandler.ReflectedInventory(contents, new Object(), tile, 64);

        ItemStack part = inventory.extractItem(0, 4, false);
        assertThat(part.getCount()).isEqualTo(4);
        assertThat(contents[0].getCount()).isEqualTo(2);

        assertThat(inventory.extractItem(0, 64, false).getCount()).isEqualTo(2);
        assertThat(contents[0]).isSameAs(ItemStack.EMPTY);

        assertThat(inventory.extractItem(0, 1, false).isEmpty()).isTrue();
        assertThat(inventory.extractItem(0, 0, false).isEmpty()).isTrue();
    }

    /**
     * 反射库存包装必须在<b>真实</b>变更后补脏标记：TR 的 {@code RecipeCrafter#updateEntity} 只在
     * 库存脏或当前无配方时才重扫配方，漏掉这一步推入的材料要等机器自身 20 tick 兜底改脏才被识别。
     *
     * <p>能真实驱动的部分是 {@code TileEntity#markDirty()} 的调用时机（模拟阶段不能触发）；
     * {@code markInventoryChanged} 里 RebornCore {@code Inventory#isDirty/hasChanged} 两个字段的写位
     * 需要真实 {@code reborncore} 类与句柄，属已知覆盖缺口（见类注释）。</p>
     */
    @Test
    public void testReflectedInventoryMarksTargetDirtyOnlyOnRealMutation() {
        ItemStack[] contents = {new ItemStack(Items.APPLE, 16), ItemStack.EMPTY};
        TechRebornMachineHandler.ReflectedInventory inventory =
                new TechRebornMachineHandler.ReflectedInventory(contents, new Object(), tile, 64);

        // 模拟阶段（框架推料的第一阶段）不得留痕，也不得标脏
        inventory.insertItem(0, new ItemStack(Items.COAL, 1), true);
        inventory.extractItem(0, 1, true);
        verify(tile, never()).markDirty();

        // 真实写入/取出各标脏一次
        assertThat(inventory.insertItem(1, new ItemStack(Items.COAL, 1), false).isEmpty()).isTrue();
        verify(tile, times(1)).markDirty();

        assertThat(inventory.extractItem(0, 1, false).getCount()).isEqualTo(1);
        verify(tile, times(2)).markDirty();

        // 放不下/空槽抽取都不产生变更，也不标脏
        assertThat(inventory.insertItem(1, new ItemStack(Items.DIAMOND, 1), false).getCount()).isEqualTo(1);
        inventory.extractItem(1, 1, true);
        verify(tile, times(2)).markDirty();
    }

    /** 目录里槽位顺序稳定，且 SlotRef 的 index/role 与槽位定义一致。 */
    @Test
    public void testCatalogSlotOrderAndIndices() {
        ISlotCatalog catalog = TechRebornMachineHandler.buildCatalog(
                new FakeItemHandler(4), 4, new int[] {1, 0}, new int[] {2}, 3);

        List<SlotRef> slots = catalog.slots();
        assertThat(slots).hasSize(4);
        assertThat(slots).extracting(SlotRef::index).containsExactly(0, 1, 2, 3);
        // 角色按输入/输出/电池的定义写入，与下标顺序无关
        assertThat(Arrays.asList(SlotRole.INPUT, SlotRole.INPUT, SlotRole.OUTPUT, SlotRole.UPGRADE))
                .containsExactlyElementsOf(Arrays.asList(
                        slots.get(0).role(), slots.get(1).role(), slots.get(2).role(), slots.get(3).role()));
    }
}
