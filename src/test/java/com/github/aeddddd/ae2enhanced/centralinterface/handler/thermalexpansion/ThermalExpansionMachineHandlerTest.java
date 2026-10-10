package com.github.aeddddd.ae2enhanced.centralinterface.handler.thermalexpansion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ItemTransferHelper;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link ThermalExpansionMachineHandler} 迁移到 {@code AbstractMachineHandler} 后的特征测试。
 *
 * <p><b>测试环境没有 Thermal Expansion</b>：{@code cofh.*} 类全部不可加载，反射路径
 * （{@code catalogFor} 读内部数组）无法在这里成功。因此本测试只覆盖两类内容：</p>
 * <ol>
 *   <li>不依赖反射的纯逻辑：角色映射（{@link ThermalExpansionMachineHandler#slotRoles}）、
 *       逐槽插入判据（{@link InventoryArrayItemHandler}）——通过包内可见的
 *       {@code catalogOf} 用真实 {@code IInventory} + 原始 {@code ItemStack[]} 驱动，
 *       等价于反射拿到数组之后的全部行为；</li>
 *   <li>框架交互语义：原子推料不留痕、收集排除本批输入、回退只退本批材料、
 *       空闲/完成判定的流体与处理中闸门（用只覆盖 {@code catalogFor} 的测试子类注入目录）。</li>
 * </ol>
 *
 * <p>未能覆盖的部分见类尾注释。</p>
 */
public class ThermalExpansionMachineHandlerTest {

    private static final BlockPos POS = new BlockPos(3, 70, 3);

    private ThermalExpansionMachineHandler handler;
    private World world;

    @BeforeAll
    public static void boot() {
        AE2TestBootstrap.boot();
    }

    @BeforeEach
    public void setUp() {
        this.handler = new ThermalExpansionMachineHandler();
        this.world = mock(World.class);
    }

    // ------------------------------------------------------------------
    // 基本契约（与重构前逐条对应）
    // ------------------------------------------------------------------

    /** canHandle 仍按命名空间前缀匹配整个 TE 方块集。 */
    @Test
    public void testCanHandleMatchesThermalExpansionNamespace() {
        assertThat(handler.canHandle("thermalexpansion:machine_pulverizer")).isTrue();
        assertThat(handler.canHandle("thermalexpansion:")).isTrue();
        assertThat(handler.canHandle("minecraft:furnace")).isFalse();
        assertThat(handler.canHandle(null)).isFalse();
    }

    /** getCapabilities 仍为物理发配 only。 */
    @Test
    public void testGetCapabilitiesPhysicalOnly() {
        assertThat(handler.getCapabilities()).containsExactly(HandlerCapabilities.PHYSICAL);
        assertThat(handler.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isFalse();
    }

    /** 目标无 TileEntity（含未装 TE 的环境）时全部入口退化为"无效目标"契约。 */
    @Test
    public void testInvalidTargetDegradesToFrameworkContract() {
        assertThat(handler.isValidTarget(world, POS)).isFalse();
        assertThat(handler.canStart(world, POS, crafting(), null)).isFalse();
        assertThat(handler.pushMaterials(world, POS, crafting(new ItemStack(Items.APPLE)), null, null)).isFalse();
        assertThat(handler.collectProducts(world, POS, null, null, null, null)).isNotNull().isEmpty();
        assertThat(handler.clearOutputs(world, POS, null, null)).isNotNull().isEmpty();
        assertThat(handler.revertMaterials(world, POS, null, null)).isNotNull().isEmpty();
        assertThat(handler.isIdle(world, POS, null, null)).isTrue();
        assertThat(handler.hasFinished(world, POS, null, null)).isTrue();
    }

    // ------------------------------------------------------------------
    // 角色映射（步骤 1 清单的固化）
    // ------------------------------------------------------------------

    /**
     * 粉碎机式 4 槽布局：输入标志只开 0 号，抽取标志开 1/2 号。
     *
     * <p>3 号（充电槽）重构前因"后 1/3 启发式"同样可被收集，故如实标为 OUTPUT。</p>
     */
    @Test
    public void testSlotRolesFollowSlotConfigFlags() {
        SlotRole[] roles = ThermalExpansionMachineHandler.slotRoles(4, 4,
                new boolean[] { true, false, false, false },
                new boolean[] { false, true, true, false });

        assertThat(roles).containsExactly(
                SlotRole.INPUT, SlotRole.OUTPUT, SlotRole.OUTPUT, SlotRole.OUTPUT);
    }

    /** 既不在输入标志也不在抽取标志内的中部槽位（如合成器充电槽以外的不透明槽）保持 UNKNOWN。 */
    @Test
    public void testSlotRolesMarksMiddleSlotsWithoutFlagsAsUnknown() {
        SlotRole[] roles = ThermalExpansionMachineHandler.slotRoles(6, 6,
                new boolean[] { true, false, false, false, false, false },
                new boolean[] { false, false, true, false, false, false });

        // 4/5 号落在后 1/3 → OUTPUT；1/3 号两标志皆 false 且不在后 1/3 → UNKNOWN
        assertThat(roles).containsExactly(
                SlotRole.INPUT, SlotRole.UNKNOWN, SlotRole.OUTPUT, SlotRole.UNKNOWN,
                SlotRole.OUTPUT, SlotRole.OUTPUT);
    }

    /** slotConfig 不可用（字段缺失）时，重构前的两条 2/3 启发式互为补集。 */
    @Test
    public void testSlotRolesWhenSlotConfigUnavailableUsesTwoThirdsHeuristic() {
        SlotRole[] roles = ThermalExpansionMachineHandler.slotRoles(3, 3, null, null);

        assertThat(roles).containsExactly(SlotRole.INPUT, SlotRole.INPUT, SlotRole.OUTPUT);
    }

    /** 同时允许插入与抽取的槽位（合成器网格）取 INPUT：取 OUTPUT 会让该机器没有推料目标。 */
    @Test
    public void testSlotRolesWithBothFlagsTruePrefersInput() {
        SlotRole[] roles = ThermalExpansionMachineHandler.slotRoles(3, 3,
                new boolean[] { true, true, false },
                new boolean[] { true, true, true });

        assertThat(roles).containsExactly(SlotRole.INPUT, SlotRole.INPUT, SlotRole.OUTPUT);
    }

    /**
     * 顺序制造机式布局：内部数组 30 槽但 {@code getSizeInventory()} 为 20。
     *
     * <p>启发式基数必须是 20（重构前正是用它算 2/3），否则 19 号充电槽与 20-29 号输出缓冲
     * 的角色判定会偏离重构前。</p>
     */
    @Test
    public void testSlotRolesForSequentialFabricatorUsesDeclaredSize() {
        boolean[] insertion = new boolean[20];
        boolean[] extraction = new boolean[20];
        Arrays.fill(insertion, 0, 18, true);
        Arrays.fill(extraction, 0, 19, true);

        SlotRole[] roles = ThermalExpansionMachineHandler.slotRoles(30, 20, insertion, extraction);

        assertThat(roles).hasSize(30);
        for (int slot = 0; slot < 18; slot++) {
            assertThat(roles[slot]).as("slot %d", slot).isEqualTo(SlotRole.INPUT);
        }
        for (int slot = 18; slot < 30; slot++) {
            assertThat(roles[slot]).as("slot %d", slot).isEqualTo(SlotRole.OUTPUT);
        }
    }

    /** 数组比标志表长（词典 11 槽 / 标志 2 项）时，越界槽位退化为后 1/3 判据 —— 与重构前一致。 */
    @Test
    public void testSlotRolesFallsBackForOutOfRangeFlagIndices() {
        SlotRole[] roles = ThermalExpansionMachineHandler.slotRoles(11, 2,
                new boolean[] { true, false },
                new boolean[] { false, true });

        assertThat(roles[0]).isEqualTo(SlotRole.INPUT);
        for (int slot = 1; slot < 11; slot++) {
            assertThat(roles[slot]).as("slot %d", slot).isEqualTo(SlotRole.OUTPUT);
        }
    }

    /** 真实 TE 布局下角色总是可判定（rolesKnown 恒为 true，不会走框架的宽松降级）。 */
    @Test
    public void testRolesAreAlwaysKnownForRealMachineLayout() {
        ItemStack[] inventory = new ItemStack[4];
        IInventory inv = mockInventory(4);

        ISlotCatalog catalog = ThermalExpansionMachineHandler.catalogOf(inv, inventory,
                new boolean[] { true, false, false, false },
                new boolean[] { false, true, true, false });

        assertThat(catalog).isNotNull();
        assertThat(catalog.rolesKnown()).isTrue();
        assertThat(catalog.inputSlots()).hasSize(1);
        assertThat(catalog.outputSlots()).hasSize(3);
        assertThat(catalog.pushTargets()).hasSize(1);
    }

    /** 零槽位（纯流体机器）仍返回目录：原 isValidTarget 为 true，降级为 null 会丢掉流体接管能力。 */
    @Test
    public void testZeroSlotMachineStillYieldsCatalog() {
        ISlotCatalog catalog = ThermalExpansionMachineHandler.catalogOf(
                mockInventory(0), new ItemStack[0], null, null);

        assertThat(catalog).isNotNull();
        assertThat(catalog.slots()).isEmpty();
        assertThat(catalog.rolesKnown()).isFalse();
        // 空配方仍视为推送成功（与原 pushMaterials 空 toPush 直接返回 true 一致）
        assertThat(ItemTransferHelper.push(catalog, Collections.emptyList()).isSuccess()).isTrue();
        // 非空材料必然失败（没有推料目标）
        assertThat(ItemTransferHelper.push(catalog,
                Collections.singletonList(new ItemStack(Items.APPLE))).isSuccess()).isFalse();
    }

    // ------------------------------------------------------------------
    // 逐槽插入判据（原 pushItemToInventory）
    // ------------------------------------------------------------------

    /** 空槽整堆写入（原实现即整堆写入，不做部分填充）。 */
    @Test
    public void testInsertWholeStackIntoEmptySlot() {
        ItemStack[] inventory = new ItemStack[1];
        InventoryArrayItemHandler access = new InventoryArrayItemHandler(mockInventory(1), inventory);

        assertThat(access.insertItem(0, new ItemStack(Items.APPLE, 12), false).isEmpty()).isTrue();
        assertThat(inventory[0].getItem()).isEqualTo(Items.APPLE);
        assertThat(inventory[0].getCount()).isEqualTo(12);
    }

    /** 同物品合并到物品自身最大堆叠为止；超出则整堆退回（不部分插入）。 */
    @Test
    public void testInsertMergesOnlyWhenWholeStackFits() {
        ItemStack[] inventory = new ItemStack[1];
        IInventory inv = mockInventory(1);
        InventoryArrayItemHandler access = new InventoryArrayItemHandler(inv, inventory);
        inventory[0] = new ItemStack(Items.APPLE, 60);

        ItemStack rejected = access.insertItem(0, new ItemStack(Items.APPLE, 8), false);
        assertThat(rejected.getCount()).isEqualTo(8);
        assertThat(inventory[0].getCount()).isEqualTo(60);
        // 被拒绝的插入不得写盘
        verify(inv, never()).markDirty();

        // 60 + 4 == 64，整堆放得下 → 合并写入
        assertThat(access.insertItem(0, new ItemStack(Items.APPLE, 4), false).isEmpty()).isTrue();
        assertThat(inventory[0].getCount()).isEqualTo(64);
    }

    /** 异种物品不得写入已占用的槽位。 */
    @Test
    public void testInsertRejectsDifferentItem() {
        ItemStack[] inventory = new ItemStack[1];
        inventory[0] = new ItemStack(Items.BRICK, 1);
        InventoryArrayItemHandler access = new InventoryArrayItemHandler(mockInventory(1), inventory);

        ItemStack rejected = access.insertItem(0, new ItemStack(Items.APPLE, 1), false);

        assertThat(rejected.getCount()).isEqualTo(1);
        assertThat(inventory[0].getItem()).isEqualTo(Items.BRICK);
    }

    /** 机器拒绝该物品（isItemValidForSlot=false）时不得写入。 */
    @Test
    public void testInsertRejectsWhenMachineRejectsItem() {
        ItemStack[] inventory = new ItemStack[2];
        IInventory inv = mockInventory(2);
        when(inv.isItemValidForSlot(eq(0), any(ItemStack.class))).thenReturn(false);
        InventoryArrayItemHandler access = new InventoryArrayItemHandler(inv, inventory);

        assertThat(access.insertItem(0, new ItemStack(Items.APPLE, 1), false).getCount()).isEqualTo(1);
        assertThat(inventory[0]).isNull();

        assertThat(access.insertItem(1, new ItemStack(Items.APPLE, 1), false).isEmpty()).isTrue();
        assertThat(inventory[1].getItem()).isEqualTo(Items.APPLE);
    }

    /** simulate 阶段不得改动数组。 */
    @Test
    public void testInsertSimulateDoesNotMutate() {
        ItemStack[] inventory = new ItemStack[2];
        inventory[0] = new ItemStack(Items.APPLE, 10);
        InventoryArrayItemHandler access = new InventoryArrayItemHandler(mockInventory(2), inventory);

        assertThat(access.insertItem(0, new ItemStack(Items.APPLE, 5), true).isEmpty()).isTrue();
        assertThat(inventory[0].getCount()).isEqualTo(10);
        assertThat(access.insertItem(1, new ItemStack(Items.COAL, 3), true).isEmpty()).isTrue();
        assertThat(inventory[1]).isNull();
    }

    /** 抽取按请求数量裁剪，取空后槽位置为 EMPTY（框架循环抽取依赖此点终止）。 */
    @Test
    public void testExtractRespectsAmountAndEmptiesSlot() {
        ItemStack[] inventory = new ItemStack[1];
        inventory[0] = new ItemStack(Items.APPLE, 5);
        InventoryArrayItemHandler access = new InventoryArrayItemHandler(mockInventory(1), inventory);

        assertThat(access.extractItem(0, 2, false).getCount()).isEqualTo(2);
        assertThat(inventory[0].getCount()).isEqualTo(3);
        assertThat(access.extractItem(0, 3, false).getCount()).isEqualTo(3);
        assertThat(inventory[0].isEmpty()).isTrue();

        inventory[0] = new ItemStack(Items.APPLE, 4);
        assertThat(access.extractItem(0, 4, true).getCount()).isEqualTo(4);
        assertThat(inventory[0].getCount()).isEqualTo(4);
    }

    // ------------------------------------------------------------------
    // 原子推料（框架 ItemTransferHelper.push）
    // ------------------------------------------------------------------

    /** 推料按目录顺序落到 INPUT 槽位，OUTPUT 槽位不受影响。 */
    @Test
    public void testPushPlacesItemsIntoInputSlotsOnly() {
        ItemStack[] inventory = new ItemStack[3];
        IInventory inv = mockInventory(3);
        ISlotCatalog catalog = ThermalExpansionMachineHandler.catalogOf(inv, inventory,
                new boolean[] { true, true, false },
                new boolean[] { false, false, true });

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(catalog,
                Arrays.asList(new ItemStack(Items.APPLE, 2), new ItemStack(Items.COAL, 1)));

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(inventory[0].getItem()).isEqualTo(Items.APPLE);
        assertThat(inventory[0].getCount()).isEqualTo(2);
        assertThat(inventory[1].getItem()).isEqualTo(Items.COAL);
        assertThat(inventory[2]).isNull();
    }

    /** 第一个 INPUT 槽位放不下整堆时换下一个 INPUT 槽位（与重构前逐槽扫描一致）。 */
    @Test
    public void testPushFallsThroughToNextInputSlot() {
        ItemStack[] inventory = new ItemStack[2];
        IInventory inv = mockInventory(2);
        when(inv.isItemValidForSlot(eq(0), any(ItemStack.class))).thenReturn(false);
        ISlotCatalog catalog = ThermalExpansionMachineHandler.catalogOf(inv, inventory,
                new boolean[] { true, true }, new boolean[] { false, false });

        assertThat(ItemTransferHelper.push(catalog,
                Collections.singletonList(new ItemStack(Items.APPLE, 4))).isSuccess()).isTrue();
        assertThat(inventory[0]).isNull();
        assertThat(inventory[1].getItem()).isEqualTo(Items.APPLE);
        assertThat(inventory[1].getCount()).isEqualTo(4);
    }

    /** 模拟阶段就放不下：返回失败且目标一个字节都没动（连 markDirty 都不该发生）。 */
    @Test
    public void testPushFailureLeavesNoTrace() {
        ItemStack[] inventory = new ItemStack[1];
        inventory[0] = new ItemStack(Items.BRICK, 64);
        IInventory inv = mockInventory(1);
        ISlotCatalog catalog = ThermalExpansionMachineHandler.catalogOf(inv, inventory,
                new boolean[] { true }, new boolean[] { false });

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(catalog,
                Collections.singletonList(new ItemStack(Items.APPLE, 1)));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.unplaced()).extracting(ItemStack::getItem).containsExactly(Items.APPLE);
        assertThat(inventory[0].getItem()).isEqualTo(Items.BRICK);
        assertThat(inventory[0].getCount()).isEqualTo(64);
        verify(inv, never()).markDirty();
    }

    /**
     * 真实阶段中途失败：已插入的部分必须回退（重构前此处会把前一批材料留在机器里，
     * 违反 IRemoteHandler#pushMaterials 的原子性契约，框架改为真正原子）。
     */
    @Test
    public void testPushRollsBackPartialInsertion() {
        ItemStack[] inventory = new ItemStack[1];
        IInventory inv = mockInventory(1);
        ISlotCatalog catalog = ThermalExpansionMachineHandler.catalogOf(inv, inventory,
                new boolean[] { true }, new boolean[] { false });

        // 两个异种物品只有 1 个输入槽：模拟阶段各自都能放入，真实阶段第二个必然失败
        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(catalog,
                Arrays.asList(new ItemStack(Items.APPLE, 1), new ItemStack(Items.BRICK, 1)));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(inventory[0].isEmpty()).isTrue();
    }

    /** 没有可推料角色时不得写入任何槽位。 */
    @Test
    public void testPushNeverWritesToOutputOrUnknownSlots() {
        ItemStack[] inventory = new ItemStack[3];
        IInventory inv = mockInventory(3);
        ISlotCatalog catalog = ThermalExpansionMachineHandler.catalogOf(inv, inventory,
                new boolean[] { false, false, false },
                new boolean[] { false, false, true });

        assertThat(catalog.pushTargets()).isEmpty();
        assertThat(ItemTransferHelper.push(catalog,
                Collections.singletonList(new ItemStack(Items.APPLE, 1))).isSuccess()).isFalse();
        assertThat(inventory[0]).isNull();
        assertThat(inventory[1]).isNull();
        assertThat(inventory[2]).isNull();
        verify(inv, never()).markDirty();
    }

    // ------------------------------------------------------------------
    // 收集 / 清理 / 回退
    // ------------------------------------------------------------------

    /** 收集：预期产物被取走，本批输入（无论在 INPUT 还是 OUTPUT 槽）一律留下，INPUT 槽内容不被当产物。 */
    @Test
    public void testCollectProductsExcludesBatchInputs() {
        ItemStack[] inventory = new ItemStack[3];
        inventory[0] = new ItemStack(Items.APPLE, 2);   // INPUT：本批输入
        inventory[1] = new ItemStack(Items.APPLE, 1);   // OUTPUT：与本批输入同型 → 不得收集
        inventory[2] = new ItemStack(Items.DIAMOND, 1); // OUTPUT：预期产物
        ISlotCatalog catalog = ThermalExpansionMachineHandler.catalogOf(mockInventory(3), inventory,
                new boolean[] { true, false, false },
                new boolean[] { false, true, true });

        IAEItemStack[] expected = { AEItemStack.fromItemStack(new ItemStack(Items.DIAMOND, 1)) };
        List<ItemStack> collected = ItemTransferHelper.collectProducts(catalog, expected,
                Collections.singletonList(new ItemStack(Items.APPLE, 1)));

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getItem()).isEqualTo(Items.DIAMOND);
        assertThat(inventory[0].getItem()).isEqualTo(Items.APPLE);
        assertThat(inventory[1].getItem()).isEqualTo(Items.APPLE);
        assertThat(inventory[2].isEmpty()).isTrue();
    }

    /**
     * 清空输出槽：角色已知时只清 OUTPUT 槽。
     *
     * <p>注意与重构前的差异：重构前的 {@code clearOutputs} 用的是"抽取标志"单判据，
     * 不含后 1/3 启发式，因此尾部充电槽里的电池不会被它清掉（但会在机器空闲时被
     * {@code collectProducts} 收走）。迁移后两个方法共用同一份角色，充电槽在这里也会被清。
     * 这是本次迁移唯一无法同时满足两处判据的地方，已在迁移说明中列明。</p>
     */
    @Test
    public void testClearOutputsClearsOnlyOutputSlots() {
        ItemStack[] inventory = new ItemStack[3];
        inventory[0] = new ItemStack(Items.BRICK, 1);   // INPUT：非产物,不得清
        inventory[1] = new ItemStack(Items.DIAMOND, 1); // OUTPUT
        inventory[2] = new ItemStack(Items.GOLD_INGOT, 1); // OUTPUT
        ISlotCatalog catalog = ThermalExpansionMachineHandler.catalogOf(mockInventory(3), inventory,
                new boolean[] { true, false, false },
                new boolean[] { false, true, true });

        List<ItemStack> cleared = ItemTransferHelper.clearOutputs(catalog, null, null);

        assertThat(cleared).extracting(ItemStack::getItem)
                .containsExactlyInAnyOrder(Items.DIAMOND, Items.GOLD_INGOT);
        assertThat(inventory[0].getItem()).isEqualTo(Items.BRICK);
        assertThat(inventory[1].isEmpty()).isTrue();
        assertThat(inventory[2].isEmpty()).isTrue();
    }

    /** 回退：只回收 INPUT 槽中属于本批快照的物品（重构前是无条件清空全部输入槽）。 */
    @Test
    public void testRevertCollectsOnlyBatchInputsFromInputSlots() {
        ItemStack[] inventory = new ItemStack[3];
        inventory[0] = new ItemStack(Items.APPLE, 2);   // INPUT：本批
        inventory[1] = new ItemStack(Items.BRICK, 1);   // INPUT：非本批 → 保留
        inventory[2] = new ItemStack(Items.DIAMOND, 1); // OUTPUT：不动
        ISlotCatalog catalog = ThermalExpansionMachineHandler.catalogOf(mockInventory(3), inventory,
                new boolean[] { true, true, false },
                new boolean[] { false, false, true });

        List<ItemStack> reverted = ItemTransferHelper.revertBatch(catalog,
                Collections.singletonList(new ItemStack(Items.APPLE, 1)));

        assertThat(reverted).hasSize(1);
        assertThat(reverted.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(reverted.get(0).getCount()).isEqualTo(2);
        assertThat(inventory[0].isEmpty()).isTrue();
        assertThat(inventory[1].getItem()).isEqualTo(Items.BRICK);
        assertThat(inventory[2].getItem()).isEqualTo(Items.DIAMOND);
    }

    // ------------------------------------------------------------------
    // isIdle / hasFinished（含 TE 特有的两口闸门）
    // ------------------------------------------------------------------

    /** 输出槽有产物 → 空闲；判定过程只做模拟抽取，不改动目标。 */
    @Test
    public void testIsIdleWithProductInOutputSlot() {
        ItemStack[] inventory = new ItemStack[2];
        inventory[1] = new ItemStack(Items.DIAMOND, 1);
        FixedCatalogHandler subject = new FixedCatalogHandler(
                pulverizerCatalog(inventory), tile());

        assertThat(subject.isIdle(world, POS, Collections.emptyList(), null)).isTrue();
        assertThat(inventory[1].getCount()).isEqualTo(1);
    }

    /** 槽内只剩本批材料 → 不空闲（不能把还没被机器消耗的输入当产物收走）。 */
    @Test
    public void testIsIdleWithOnlyBatchInputsIsFalse() {
        ItemStack[] inventory = new ItemStack[2];
        inventory[0] = new ItemStack(Items.APPLE, 2);
        FixedCatalogHandler subject = new FixedCatalogHandler(
                pulverizerCatalog(inventory), tile());

        assertThat(subject.isIdle(world, POS,
                Collections.singletonList(new ItemStack(Items.APPLE, 1)), null)).isFalse();
        assertThat(inventory[0].getCount()).isEqualTo(2);
    }

    /** TE 特有：只要罐内有流体就判空闲（流体产物不由槽位承载），槽位为空也一样。 */
    @Test
    public void testIsIdleWithTankFluidEvenWithoutItemProducts() {
        FixedCatalogHandler subject = new FixedCatalogHandler(
                pulverizerCatalog(new ItemStack[2]),
                tileWithFluid(new FluidStack(FluidRegistry.WATER, 100)));

        assertThat(subject.isIdle(world, POS, Collections.emptyList(), null)).isTrue();
    }

    /** 无流体、无产物、无输入 → 不空闲。 */
    @Test
    public void testIsIdleWithEmptyTankIsFalse() {
        FixedCatalogHandler subject = new FixedCatalogHandler(
                pulverizerCatalog(new ItemStack[2]), tileWithFluid(null));

        assertThat(subject.isIdle(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /** 推料保护期内的 slot 判定被框架 grace 窗口压住（原实现没有 grace，最多晚 2 tick 收集）。 */
    @Test
    public void testIsIdleSuppressedDuringPushGrace() {
        ItemStack[] inventory = new ItemStack[2];
        inventory[1] = new ItemStack(Items.DIAMOND, 1);
        FixedCatalogHandler subject = new FixedCatalogHandler(
                pulverizerCatalog(inventory), tile());
        TargetSession session = mock(TargetSession.class);
        when(session.isPushGraceElapsed(anyLong(), anyInt())).thenReturn(false);

        assertThat(subject.isIdle(world, POS, Collections.emptyList(), session)).isFalse();
    }

    /** 目标无效（catalogFor 返回 null）→ isIdle/hasFinished 均为 true。 */
    @Test
    public void testIdleAndFinishedAreTrueForInvalidTarget() {
        FixedCatalogHandler subject = new FixedCatalogHandler(null, tile());

        assertThat(subject.isIdle(world, POS, null, null)).isTrue();
        assertThat(subject.hasFinished(world, POS, null, null)).isTrue();
    }

    /** 本批输入尚未耗尽 → 未完成。 */
    @Test
    public void testHasFinishedWithRemainingBatchInputIsFalse() {
        ItemStack[] inventory = new ItemStack[2];
        inventory[0] = new ItemStack(Items.APPLE, 2);
        FixedCatalogHandler subject = new FixedCatalogHandler(
                pulverizerCatalog(inventory), tile());

        assertThat(subject.hasFinished(world, POS,
                Collections.singletonList(new ItemStack(Items.APPLE, 1)), null)).isFalse();
    }

    /** 输入已耗尽但产物还留在输出槽 → 未完成（等这一轮收集）。 */
    @Test
    public void testHasFinishedWithProductResidueIsFalse() {
        ItemStack[] inventory = new ItemStack[2];
        inventory[1] = new ItemStack(Items.DIAMOND, 1);
        FixedCatalogHandler subject = new FixedCatalogHandler(
                pulverizerCatalog(inventory), tile());

        assertThat(subject.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /** TE 特有：槽位已清空但罐内仍有流体 → 未完成（否则残留流体会卡在机器里）。 */
    @Test
    public void testHasFinishedWithTankFluidIsFalse() {
        FixedCatalogHandler subject = new FixedCatalogHandler(
                pulverizerCatalog(new ItemStack[2]),
                tileWithFluid(new FluidStack(FluidRegistry.LAVA, 500)));

        assertThat(subject.hasFinished(world, POS, Collections.emptyList(), null)).isFalse();
    }

    /** 输入耗尽、无产物、罐内空 → 完成。 */
    @Test
    public void testHasFinishedWhenDrained() {
        FixedCatalogHandler subject = new FixedCatalogHandler(
                pulverizerCatalog(new ItemStack[2]), tile());

        assertThat(subject.hasFinished(world, POS, Collections.emptyList(), null)).isTrue();
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private static InventoryCrafting crafting(ItemStack... stacks) {
        InventoryCrafting inv = new InventoryCrafting(mock(Container.class), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            inv.setInventorySlotContents(i, stacks[i]);
        }
        return inv;
    }

    private static IInventory mockInventory(int declaredSize) {
        IInventory inv = mock(IInventory.class);
        when(inv.getSizeInventory()).thenReturn(declaredSize);
        when(inv.getInventoryStackLimit()).thenReturn(64);
        when(inv.isItemValidForSlot(anyInt(), any(ItemStack.class))).thenReturn(true);
        return inv;
    }

    /** 粉碎机式目录（1 输入 + 1 输出），用于空闲/完成判定用例。 */
    private static ISlotCatalog pulverizerCatalog(ItemStack[] inventory) {
        return ThermalExpansionMachineHandler.catalogOf(mockInventory(2), inventory,
                new boolean[] { true, false },
                new boolean[] { false, true });
    }

    private static TileEntity tile() {
        return mock(TileEntity.class);
    }

    /** 带流体 Capability 的 TE（null 面，与原 getTankFluid 一致）。 */
    private static TileEntity tileWithFluid(FluidStack contents) {
        TileEntity te = mock(TileEntity.class);
        IFluidHandler fluidHandler = mock(IFluidHandler.class);
        IFluidTankProperties properties = mock(IFluidTankProperties.class);
        when(properties.getContents()).thenReturn(contents);
        when(fluidHandler.getTankProperties()).thenReturn(new IFluidTankProperties[] { properties });
        when(te.hasCapability(eq(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY), isNull())).thenReturn(true);
        when(te.getCapability(eq(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY), isNull())).thenReturn(fluidHandler);
        return te;
    }

    /**
     * 只覆盖 {@code catalogFor} 的测试子类：把目录与 TE 注入生产实现的
     * {@code isIdle}/{@code hasFinished} 组合逻辑，绕开不可加载的 {@code cofh.*} 反射路径。
     * 生产语义不变（{@code catalogFor} 本就是给子类实现的钩子）。
     */
    private final class FixedCatalogHandler extends ThermalExpansionMachineHandler {

        private final ISlotCatalog catalog;

        FixedCatalogHandler(ISlotCatalog catalog, TileEntity te) {
            this.catalog = catalog;
            // isIdle/hasFinished 会经 world.getTileEntity 取 TE 做"处理中/罐内流体"判定
            when(world.getTileEntity(POS)).thenReturn(te);
        }

        @Override
        protected ISlotCatalog catalogFor(World world, BlockPos pos) {
            return this.catalog;
        }
    }

    /*
     * 未覆盖部分（环境限制，非语义缺口）：
     * 1. 反射路径本身：catalogFor 读取 TileInventory#inventory / slotConfig 的三个 Field；
     * 2. 处理中闸门：TileMachineBase#processRem 的取值（PROCESS_REM_FIELD 为 null 时恒 false，
     *    测试环境正是该分支）；
     * 3. 真机上"充电槽电池被 clearOutputs 顺手清走"的实际表现（角色映射的直接后果）。
     * 三点都需要带 Thermal Expansion 的实机验证（见迁移回报）。
     */
}
