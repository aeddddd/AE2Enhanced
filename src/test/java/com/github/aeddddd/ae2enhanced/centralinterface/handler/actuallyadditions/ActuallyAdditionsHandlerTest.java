package com.github.aeddddd.ae2enhanced.centralinterface.handler.actuallyadditions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ItemTransferHelper;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineIdle;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRef;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.FakeItemHandler;

/**
 * {@link ActuallyAdditionsHandler} 迁移等价性测试.
 *
 * <p><b>为什么主要驱动静态 seam 而不是接口方法</b>：handler 的反射入口（isValidTarget / canStart /
 * pushMaterials / isIdle / 收集回退）都依赖 {@code initReflection()}，而要走到"真正的 Empowerer 目标"
 * 还得构造第三方 mod 的 TileEntity（测试里不这么做：既不引入硬引用，也不依赖 AA 的运行期行为）。
 * 因此本机特有的纯逻辑（目录角色与顺序、启动前置条件、抽取语义、空闲判定）被抽成包内静态方法后
 * 直接驱动；反射入口本身只在"非 Empowerer 目标"这一支上被驱动，见
 * {@link #testEntriesAreSafeForNonEmpowererTarget}（该用例与测试 classpath 耦合，注释里写清了）。</p>
 *
 * <p>推料不覆盖 {@code pushMaterials}（框架默认实现），因此这里测的是"本类构造的目录 + 框架
 * {@link ItemTransferHelper#push}"——即推料路径上本机唯一自有的部分：落位顺序与两阶段原子性。</p>
 */
public class ActuallyAdditionsHandlerTest {

    private static final BlockPos POS = new BlockPos(1, 64, 1);
    private static final int STAND_COUNT = 4;

    @BeforeAll
    public static void boot() {
        // 用例中构造 ItemStack / InventoryCrafting，需无头引导
        AE2TestBootstrap.boot();
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** Empowerer 与 DisplayStand 的槽上限均为 1（AA 覆写 getMaxStackSize）。 */
    private static FakeItemHandler inventory() {
        return new FakeItemHandler(1, 1);
    }

    private static FakeItemHandler[] stands() {
        FakeItemHandler[] stands = new FakeItemHandler[STAND_COUNT];
        for (int i = 0; i < STAND_COUNT; i++) {
            stands[i] = inventory();
        }
        return stands;
    }

    private static InventoryCrafting crafting(ItemStack... stacks) {
        InventoryCrafting inv = new InventoryCrafting(mock(Container.class), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            inv.setInventorySlotContents(i, stacks[i]);
        }
        return inv;
    }

    // ------------------------------------------------------------------
    // 匹配集合与能力（迁移前后必须完全一致）
    // ------------------------------------------------------------------

    /** canHandle 只匹配 empowerer；能力仍为 physical-only。 */
    @Test
    public void testCanHandleAndCapabilities() {
        ActuallyAdditionsHandler handler = new ActuallyAdditionsHandler();

        assertThat(handler.canHandle("actuallyadditions:block_empowerer")).isTrue();
        assertThat(handler.canHandle("actuallyadditions:block_display_stand")).isFalse();
        assertThat(handler.canHandle("")).isFalse();
        assertThat(handler.canHandle(null)).isFalse();

        assertThat(handler.getCapabilities()).containsExactly(HandlerCapabilities.PHYSICAL);
        assertThat(handler.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isFalse();
    }

    // ------------------------------------------------------------------
    // 目录：角色与顺序
    // ------------------------------------------------------------------

    /** 目录 = 主槽 + 4 个 Stand 槽，顺序固定"主槽 → Stand 依次"，角色全为 INPUT。 */
    @Test
    public void testCatalogRolesAndOrder() {
        FakeItemHandler main = inventory();
        FakeItemHandler[] stands = stands();

        ISlotCatalog catalog = ActuallyAdditionsHandler.buildCatalog(main, stands);
        List<SlotRef> slots = catalog.slots();

        assertThat(catalog.rolesKnown()).isTrue();
        assertThat(slots).hasSize(1 + STAND_COUNT);
        assertThat(slots.get(0).handler()).isSameAs(main);
        assertThat(slots.get(0).index()).isZero();
        assertThat(slots.get(0).role()).isEqualTo(SlotRole.INPUT);
        for (int i = 0; i < STAND_COUNT; i++) {
            assertThat(slots.get(1 + i).handler()).isSameAs(stands[i]);
            assertThat(slots.get(1 + i).index()).isZero();
            assertThat(slots.get(1 + i).role()).isEqualTo(SlotRole.INPUT);
        }
        // 推料目标顺序即落位顺序：主槽在前，Stand 按 getNearbyStands() 的枚举顺序在后
        assertThat(catalog.pushTargets()).containsExactlyElementsOf(slots);
    }

    /** Stand 结构不完整时目录只含 Empowerer 自身的槽，且 canStart 拒绝推料。 */
    @Test
    public void testCatalogWithoutCompleteStandStructure() {
        ISlotCatalog catalog = ActuallyAdditionsHandler.buildCatalog(inventory(), null);

        assertThat(catalog).isNotNull();
        assertThat(catalog.slots()).hasSize(1);
        assertThat(ActuallyAdditionsHandler.canStart(inventory(), null,
                crafting(new ItemStack(Items.APPLE, 1)))).isFalse();
    }

    /** 取不到 Empowerer 库存时目录为 null（框架据此判目标无效）。 */
    @Test
    public void testCatalogNullInventory() {
        assertThat(ActuallyAdditionsHandler.buildCatalog(null, stands())).isNull();
        assertThat(ActuallyAdditionsHandler.buildCatalog(null, null)).isNull();
    }

    // ------------------------------------------------------------------
    // 推料：落位与原子性
    // ------------------------------------------------------------------

    /** 主材料进 Empowerer 槽，辅助材料按序进 Stand 槽（等价原实现的按位置映射）。 */
    @Test
    public void testPushPlacesMainThenAuxiliaryInStandOrder() {
        FakeItemHandler main = inventory();
        FakeItemHandler[] stands = stands();

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(
                ActuallyAdditionsHandler.buildCatalog(main, stands), Arrays.asList(
                        new ItemStack(Items.APPLE, 1),
                        new ItemStack(Items.COAL, 1),
                        new ItemStack(Items.REDSTONE, 1)));

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.unplaced()).isEmpty();
        assertThat(main.getStackInSlot(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(stands[0].getStackInSlot(0).getItem()).isEqualTo(Items.COAL);
        assertThat(stands[1].getStackInSlot(0).getItem()).isEqualTo(Items.REDSTONE);
        assertThat(stands[2].getStackInSlot(0).isEmpty()).isTrue();
        assertThat(stands[3].getStackInSlot(0).isEmpty()).isTrue();
    }

    /** 任一材料放不下即整体失败，且目标不留痕（模拟阶段不改变任何槽位）。 */
    @Test
    public void testPushAtomicFailureLeavesNoTrace() {
        FakeItemHandler main = inventory();
        FakeItemHandler[] stands = stands();
        for (FakeItemHandler stand : stands) {
            stand.setStack(0, new ItemStack(Items.BRICK, 1));
        }

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(
                ActuallyAdditionsHandler.buildCatalog(main, stands), Arrays.asList(
                        new ItemStack(Items.APPLE, 1),
                        new ItemStack(Items.COAL, 1)));

        assertThat(outcome.isSuccess()).isFalse();
        // 失败时框架回报整批材料（含已模拟"放得下"的那部分）
        assertThat(outcome.unplaced()).hasSize(2);
        assertThat(main.getStackInSlot(0).isEmpty()).isTrue();
        for (FakeItemHandler stand : stands) {
            assertThat(stand.getStackInSlot(0).getItem()).isEqualTo(Items.BRICK);
        }
    }

    // ------------------------------------------------------------------
    // canStart：原实现的前置条件
    // ------------------------------------------------------------------

    /** 主槽非空即拒绝（框架默认只判目录非 null，会把主材料顶到 Stand 槽）。 */
    @Test
    public void testCanStartRequiresEmptyMainSlot() {
        FakeItemHandler main = inventory();
        main.setStack(0, new ItemStack(Items.BRICK, 1));

        assertThat(ActuallyAdditionsHandler.canStart(main, stands(), crafting(
                new ItemStack(Items.APPLE, 1), new ItemStack(Items.COAL, 1)))).isFalse();
    }

    /** 结构不完整、取不到库存、库存无槽位一律拒绝。 */
    @Test
    public void testCanStartRejectsIncompleteStructure() {
        InventoryCrafting grid = crafting(new ItemStack(Items.APPLE, 1), new ItemStack(Items.COAL, 1));

        assertThat(ActuallyAdditionsHandler.canStart(null, stands(), grid)).isFalse();
        assertThat(ActuallyAdditionsHandler.canStart(inventory(), null, grid)).isFalse();
        assertThat(ActuallyAdditionsHandler.canStart(new FakeItemHandler(0, 1), stands(), grid)).isFalse();
    }

    /** 只检查"前 auxCount 个 Stand"，多出来的 Stand 有物品不影响启动条件。 */
    @Test
    public void testCanStartChecksOnlyStandsCoveringTheBatch() {
        FakeItemHandler main = inventory();
        InventoryCrafting oneAux = crafting(new ItemStack(Items.APPLE, 1), new ItemStack(Items.COAL, 1));

        // 1 个辅助材料 → 第 1 个 Stand 必须为空
        FakeItemHandler[] firstStandOccupied = stands();
        firstStandOccupied[0].setStack(0, new ItemStack(Items.BRICK, 1));
        assertThat(ActuallyAdditionsHandler.canStart(main, firstStandOccupied, oneAux)).isFalse();

        // 本批用不到的第 4 个 Stand 有物品时不拒绝
        FakeItemHandler[] laterStandOccupied = stands();
        laterStandOccupied[3].setStack(0, new ItemStack(Items.BRICK, 1));
        assertThat(ActuallyAdditionsHandler.canStart(main, laterStandOccupied, oneAux)).isTrue();

        // 只有主材料、没有辅助材料 → 不检查任何 Stand
        FakeItemHandler[] allStandsOccupied = stands();
        for (FakeItemHandler stand : allStandsOccupied) {
            stand.setStack(0, new ItemStack(Items.BRICK, 1));
        }
        assertThat(ActuallyAdditionsHandler.canStart(main, allStandsOccupied,
                crafting(new ItemStack(Items.APPLE, 1)))).isTrue();
    }

    /**
     * 主材料必须能被主槽完整接受（原 pushMaterials 的模拟预检，槽上限为 1）：
     * 不放行的话框架的贪心推料会把主材料塞进 Stand，机器拿到错位布局。
     */
    @Test
    public void testCanStartRequiresMainIngredientFitsMainSlot() {
        FakeItemHandler main = inventory();

        // 主材料数量 2 > 槽上限 1 → 与迁移前的模拟预检一致，直接拒绝
        assertThat(ActuallyAdditionsHandler.canStart(main, stands(),
                crafting(new ItemStack(Items.APPLE, 2)))).isFalse();

        // 恰好放下时通过，且预检是纯模拟（不改变主槽内容）
        assertThat(ActuallyAdditionsHandler.canStart(main, stands(),
                crafting(new ItemStack(Items.APPLE, 1)))).isTrue();
        assertThat(main.getStackInSlot(0).isEmpty()).isTrue();
    }

    /** 辅助材料计数从槽 1 起（槽 0 是主材料）；null 与空配方为 0。 */
    @Test
    public void testAuxiliaryMaterialCount() {
        assertThat(ActuallyAdditionsHandler.countAuxiliaryMaterials(null)).isZero();
        assertThat(ActuallyAdditionsHandler.countAuxiliaryMaterials(crafting())).isZero();
        assertThat(ActuallyAdditionsHandler.countAuxiliaryMaterials(
                crafting(new ItemStack(Items.APPLE, 1)))).isZero();
        assertThat(ActuallyAdditionsHandler.countAuxiliaryMaterials(crafting(
                new ItemStack(Items.APPLE, 1),
                new ItemStack(Items.COAL, 1),
                new ItemStack(Items.REDSTONE, 1)))).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // 收集 / 清理 / 回退：同一份"取空所有槽位"语义
    // ------------------------------------------------------------------

    /** 主槽在前、Stand 在后逐槽抽干，顺序与迁移前一致。 */
    @Test
    public void testDrainTargetExtractsAllSlotsInOrder() {
        FakeItemHandler main = inventory();
        FakeItemHandler[] stands = stands();
        main.setStack(0, new ItemStack(Items.APPLE, 1));
        stands[0].setStack(0, new ItemStack(Items.COAL, 1));
        stands[2].setStack(0, new ItemStack(Items.REDSTONE, 1));

        List<ItemStack> drained = ActuallyAdditionsHandler.drainTarget(
                ActuallyAdditionsHandler.buildCatalog(main, stands));

        assertThat(drained).hasSize(3);
        assertThat(drained.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(drained.get(1).getItem()).isEqualTo(Items.COAL);
        assertThat(drained.get(2).getItem()).isEqualTo(Items.REDSTONE);
        assertThat(main.getStackInSlot(0).isEmpty()).isTrue();
        assertThat(stands[0].getStackInSlot(0).isEmpty()).isTrue();
        assertThat(stands[2].getStackInSlot(0).isEmpty()).isTrue();
    }

    /** 抽不出来的槽被跳过（Empowerer 拒绝移出"仍是合法输入"的物品），不影响其它槽且不抛异常。 */
    @Test
    public void testDrainTargetSkipsUnextractableSlot() {
        IItemHandler locked = mock(IItemHandler.class);
        when(locked.getSlots()).thenReturn(1);
        when(locked.getStackInSlot(0)).thenReturn(new ItemStack(Items.APPLE, 1));
        when(locked.extractItem(anyInt(), anyInt(), anyBoolean())).thenReturn(ItemStack.EMPTY);
        FakeItemHandler[] stands = stands();
        stands[1].setStack(0, new ItemStack(Items.COAL, 1));

        List<ItemStack> drained = ActuallyAdditionsHandler.drainTarget(
                ActuallyAdditionsHandler.buildCatalog(locked, stands));

        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).getItem()).isEqualTo(Items.COAL);
        verify(locked).extractItem(0, 1, false);
    }

    /** 抽取量为槽内数量、模式为真实抽取（simulate=false），与原实现的 extractItem(槽, 64, false) 同族。 */
    @Test
    public void testDrainTargetUsesRealModeAndSlotCount() {
        IItemHandler source = mock(IItemHandler.class);
        when(source.getSlots()).thenReturn(1);
        when(source.getStackInSlot(0)).thenReturn(new ItemStack(Items.APPLE, 8));
        when(source.extractItem(0, 8, false)).thenReturn(new ItemStack(Items.APPLE, 8));

        List<ItemStack> drained = ActuallyAdditionsHandler.drainTarget(
                ActuallyAdditionsHandler.buildCatalog(source, null));

        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).getCount()).isEqualTo(8);
        verify(source).extractItem(0, 8, false);
    }

    /** 目录为 null 时抽取结果为空（框架对 null 目录的约定）。 */
    @Test
    public void testDrainTargetNullCatalog() {
        assertThat(ActuallyAdditionsHandler.drainTarget(null)).isEmpty();
    }

    // ------------------------------------------------------------------
    // 空闲判定
    // ------------------------------------------------------------------

    /**
     * grace 为 2（迁移前的常量）；processTime 读不可用时一律按"仍在处理"处理.
     *
     * <p>读不可用有三种来源：没装 AA（{@code CLASS_EMPOWERER} 为 null，字段查找即 NPE）、
     * 传入的 TE 不是真正的 Empowerer（对实例字段取值抛 IllegalArgumentException）、
     * te 为 null（NPE）。三者都被 {@code readProcessTimeWorking} 归为"处理中"，
     * 与原 isIdle 返回 false（未空闲）一致。</p>
     */
    @Test
    public void testIdleGateGraceAndUnreadableProcessTime() {
        MachineIdle gate = ActuallyAdditionsHandler.EMPOWERER_IDLE;

        assertThat(gate.graceTicks()).isEqualTo(2);
        assertThat(gate.isWorking(mock(World.class), POS, mock(TileEntity.class))).isTrue();
        assertThat(gate.isWorking(mock(World.class), POS, null)).isTrue();
    }

    /** processTime 判定：读取失败(null)与任何非 0 值都是"处理中"，只有 0 才空闲。 */
    @Test
    public void testIsWorkingProcessTime() {
        assertThat(ActuallyAdditionsHandler.isWorkingProcessTime(null)).isTrue();
        assertThat(ActuallyAdditionsHandler.isWorkingProcessTime(0)).isFalse();
        assertThat(ActuallyAdditionsHandler.isWorkingProcessTime(1)).isTrue();
        assertThat(ActuallyAdditionsHandler.isWorkingProcessTime(-1)).isTrue();
    }

    /** 空闲判定核心：grace 未过或机器仍在处理都不可收集，且判定用的是当前 tick 的 TileEntity。 */
    @Test
    public void testIsCollectableCombinesGraceAndWorkingState() {
        World world = mock(World.class);
        TileEntity te = mock(TileEntity.class);

        assertThat(ActuallyAdditionsHandler.isCollectable(false, MachineIdle.none(), world, POS, te)).isFalse();
        assertThat(ActuallyAdditionsHandler.isCollectable(true, MachineIdle.none(), world, POS, te)).isTrue();
        // 本机 gate：读不到有效 processTime（mock 的 TE 不是真正的 Empowerer）→ 视为处理中
        assertThat(ActuallyAdditionsHandler.isCollectable(true, ActuallyAdditionsHandler.EMPOWERER_IDLE,
                world, POS, te)).isFalse();

        final TileEntity[] seen = new TileEntity[1];
        MachineIdle probe = (w, p, t) -> {
            seen[0] = t;
            return false;
        };
        assertThat(ActuallyAdditionsHandler.isCollectable(true, probe, world, POS, te)).isTrue();
        assertThat(seen[0]).isSameAs(te);
    }

    // ------------------------------------------------------------------
    // 反射入口对"非 Empowerer 目标"的行为（与测试 classpath 耦合，见用例注释）
    // ------------------------------------------------------------------

    /**
     * 反射入口对"非 Empowerer 目标"一律安全返回，且反射初始化可重复调用（幂等）.
     *
     * <p><b>本用例与测试 classpath 耦合，改测试环境前先读这段</b>：ActuallyAdditions 在
     * `build.gradle` 里是 {@code compileOnly}，但 ForgeGradle 会把 compileOnly 的 jar 一并挂进
     * test 运行时（实测如此——"测试期必然 {@code ClassNotFoundException}"的假设已被证伪）。
     * 据此分两支断言：</p>
     * <ul>
     *   <li><b>AA 在测试运行时可达（当前环境）</b>：{@code initReflection()} 成功，因此断言各入口
     *       对非 Empowerer 目标返回 false / true / 空列表且不抛异常；</li>
     *   <li><b>AA 确实不在 classpath</b>（例如将来收紧了测试 classpath）：{@code initReflection()}
     *       会按原语义抛 {@link RuntimeException}（生产侧由 {@code PhysicalDispatcher} 的
     *       try/catch 隔离），此时断言该异常成立——不要为了让用例变绿而改生产代码。</li>
     * </ul>
     *
     * <p>目标只用 mock {@link World}（{@code getTileEntity} 返回 null，或返回一个 mock 的非 Empowerer
     * {@link TileEntity}），不构造任何第三方 mod 对象，因此两支都不依赖额外夹具。</p>
     */
    @Test
    public void testEntriesAreSafeForNonEmpowererTarget() {
        ActuallyAdditionsHandler handler = new ActuallyAdditionsHandler();
        World world = mock(World.class);

        if (!actuallyAdditionsPresent()) {
            assertThatThrownBy(() -> handler.isValidTarget(world, POS)).isInstanceOf(RuntimeException.class);
            return;
        }

        // 目标位置没有 TileEntity：目录为 null，各入口按框架约定返回，且不得抛异常
        assertThat(handler.isValidTarget(world, POS)).isFalse();
        assertThat(handler.canStart(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null)).isFalse();
        assertThat(handler.pushMaterials(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null, null)).isFalse();
        assertThat(handler.isIdle(world, POS, null, null)).isTrue();
        assertThat(handler.hasFinished(world, POS, null, null)).isTrue();
        assertThat(handler.collectProducts(world, POS, null, null, null, null)).isEmpty();
        assertThat(handler.clearOutputs(world, POS, null, null)).isEmpty();
        assertThat(handler.revertMaterials(world, POS, null, null)).isEmpty();

        // 目标存在但不是 Empowerer（mock 的 TileEntity）：同样判无效
        World otherWorld = mock(World.class);
        when(otherWorld.getTileEntity(POS)).thenReturn(mock(TileEntity.class));
        assertThat(handler.isValidTarget(otherWorld, POS)).isFalse();

        // 反射初始化幂等：同实例重复调用与本类新实例调用结果一致
        assertThat(handler.isValidTarget(world, POS)).isFalse();
        assertThat(new ActuallyAdditionsHandler().isValidTarget(world, POS)).isFalse();
    }

    /** 测试运行时能否加载 AA 的 Empowerer 类（决定上面用例走哪一支，见其注释）。 */
    private static boolean actuallyAdditionsPresent() {
        try {
            Class.forName("de.ellpeck.actuallyadditions.mod.tile.TileEntityEmpowerer");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }
}
