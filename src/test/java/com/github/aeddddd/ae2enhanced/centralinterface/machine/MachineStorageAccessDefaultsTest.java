package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.FakeItemHandler;

/**
 * {@link MachineStorageAccess} 框架默认实现测试（离线）.
 *
 * <p>这些 default 方法就是"通用 handler 模型"里的通用那一半：所有机器族都靠它们跑生命周期的
 * 推料/收集/回退/判定，各族的覆盖方法只是例外。为此用最小骨架子类
 * （{@link TestHandler}，只提供目录与空闲策略两个 seam）把每个默认入口逐条驱动一遍，
 * 覆盖三个家族测试覆盖不到的部分：</p>
 * <ul>
 *   <li><b>角色已知</b>的目录下 {@code isIdle}/{@code hasFinished}/{@code clearOutputs} 的口径
 *       （DefaultSingleBatchHandlerTest 只覆盖角色未知的宽松口径，EIO/NC/TR 又各自覆盖了这两个方法）；</li>
 *   <li>推料保护窗口与"机器处理中"闸门对 {@code isIdle} 的影响（家族测试里同样被各自的覆盖方法挡住）；</li>
 *   <li>能力集合、{@code startProcess}、目录/空闲这两个 seam 的接线。</li>
 * </ul>
 *
 * <p>台账相关的推料/回退精度已由 {@code PushLedgerTest} 覆盖，此处不重复。</p>
 */
public class MachineStorageAccessDefaultsTest {

    private static final BlockPos POS = new BlockPos(2, 64, 2);

    @BeforeAll
    public static void boot() {
        AE2TestBootstrap.boot();
    }

    // ------------------------------------------------------------------
    // 夹具
    // ------------------------------------------------------------------

    /** 最小骨架：只回答"目录是什么"与"什么时候算处理中"（= 生产路径的通用骨架）。 */
    private static final class TestHandler extends AbstractMachineHandler {

        private final ISlotCatalog catalog;
        private final MachineIdle idle;

        TestHandler(ISlotCatalog catalog, MachineIdle idle) {
            this.catalog = catalog;
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

        @Override
        protected MachineSpec defineSpec() {
            return MachineSpec.builder().exactIds("ae2enhanced:test").access(this).build();
        }
    }

    /** 角色已知的目录：0 号输入、1 号输出、2 号升级槽。 */
    private static ISlotCatalog catalog(FakeItemHandler handler) {
        return ISlotCatalog.builder()
                .add(handler, 0, null, SlotRole.INPUT)
                .add(handler, 1, null, SlotRole.OUTPUT)
                .add(handler, 2, null, SlotRole.UPGRADE)
                .build();
    }

    private static TestHandler handler(ISlotCatalog catalog) {
        return new TestHandler(catalog, MachineIdle.none());
    }

    private static InventoryCrafting crafting(ItemStack... stacks) {
        InventoryCrafting inv = new InventoryCrafting(mock(Container.class), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            inv.setInventorySlotContents(i, stacks[i]);
        }
        return inv;
    }

    private static IAEItemStack[] expected(ItemStack stack) {
        return new IAEItemStack[] { AEItemStack.fromItemStack(stack) };
    }

    private static TargetSession session(List<ItemStack> inputs) {
        TargetSession session = mock(TargetSession.class);
        when(session.getInputs()).thenReturn(inputs);
        when(session.getExpectedOutputs()).thenReturn(null);
        when(session.getPushLedger()).thenReturn(null);
        when(session.isPushGraceElapsed(anyLong(), anyInt())).thenReturn(true);
        return session;
    }

    // ------------------------------------------------------------------
    // 目标有效性 / 启动 / 能力 / 匹配
    // ------------------------------------------------------------------

    /** 目标有效性与可启动性都只取决于目录是否构造成功。 */
    @Test
    public void testValidTargetAndCanStartFollowCatalog() {
        World world = mock(World.class);
        ISlotCatalog catalog = catalog(new FakeItemHandler(3));

        assertThat(handler(catalog).isValidTarget(world, POS)).isTrue();
        assertThat(handler(catalog).canStart(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null)).isTrue();

        assertThat(handler(null).isValidTarget(world, POS)).isFalse();
        assertThat(handler(null).canStart(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null)).isFalse();
    }

    /** 物品插入后机器自行开工是默认语义（条件启动型机器覆盖）。 */
    @Test
    public void testStartProcessDefaultsToTrue() {
        assertThat(handler(null).startProcess(mock(World.class), POS, null, null)).isTrue();
        assertThat(handler(catalog(new FakeItemHandler(3))).startProcess(mock(World.class), POS, null, null)).isTrue();
    }

    /** 机器族默认只支持物理发配。 */
    @Test
    public void testCapabilitiesDefaultToPhysicalOnly() {
        TestHandler handler = handler(null);

        assertThat(handler.getCapabilities()).containsExactly(HandlerCapabilities.PHYSICAL);
        assertThat(handler.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isFalse();
    }

    /** canHandle 由表项裁定，处理器自身不再手写匹配规则。 */
    @Test
    public void testCanHandleFollowsSpec() {
        TestHandler handler = handler(null);

        assertThat(handler.canHandle("ae2enhanced:test")).isTrue();
        assertThat(handler.canHandle("ae2enhanced:other")).isFalse();
        assertThat(handler.canHandle(null)).isFalse();
    }

    /** 目录与空闲两个 seam 被如实接线（子类覆盖 seam 即改变框架行为）。 */
    @Test
    public void testSeamsAreWiredToSkeleton() {
        World world = mock(World.class);
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        TestHandler handler = handler(catalog(itemHandler));

        assertThat(handler.catalog(world, POS).slots()).hasSize(3);
        assertThat(handler.idle(world, POS).graceTicks()).isEqualTo(2);
        assertThat(handler.idle(world, POS).isWorking(world, POS, null)).isFalse();
    }

    // ------------------------------------------------------------------
    // collectProducts
    // ------------------------------------------------------------------

    /** 阶段 1 按预期产物优先、阶段 2 收输出槽剩余；升级槽与输入槽内容不动。 */
    @Test
    public void testCollectProductsPrefersExpectedThenOutputSlots() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        itemHandler.setStack(0, new ItemStack(Items.COAL, 1));    // 输入槽残留（本批材料）
        itemHandler.setStack(1, new ItemStack(Items.APPLE, 3));   // 输出槽 = 预期产物
        itemHandler.setStack(2, new ItemStack(Items.BRICK, 1));   // 升级槽

        List<ItemStack> collected = handler(catalog(itemHandler)).collectProducts(
                mock(World.class), POS, expected(new ItemStack(Items.APPLE, 3)),
                Collections.singletonList(new ItemStack(Items.COAL, 1)), null, null);

        assertThat(collected).hasSize(1);
        assertThat(collected.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(collected.get(0).getCount()).isEqualTo(3);
        // 输入槽残留与升级槽都不动
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.COAL);
        assertThat(itemHandler.getStackInSlot(2).getItem()).isEqualTo(Items.BRICK);
        assertThat(itemHandler.getStackInSlot(1).isEmpty()).isTrue();
    }

    /** 角色已知时，落在输出槽里、"与本批输入同型"的物品仍不被当产物收走（反复制）。 */
    @Test
    public void testCollectProductsExcludesBatchInputsEvenInOutputSlot() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        itemHandler.setStack(1, new ItemStack(Items.COAL, 2));

        List<ItemStack> collected = handler(catalog(itemHandler)).collectProducts(
                mock(World.class), POS, null,
                Collections.singletonList(new ItemStack(Items.COAL, 2)), null, null);

        assertThat(collected).isEmpty();
        assertThat(itemHandler.getStackInSlot(1).getCount()).isEqualTo(2);
    }

    /** 目录为 null（目标无效）时收集结果为空表。 */
    @Test
    public void testCollectProductsNullCatalogYieldsEmpty() {
        assertThat(handler(null).collectProducts(mock(World.class), POS, null, null, null, null))
                .isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------
    // isIdle
    // ------------------------------------------------------------------

    /** 目录为 null 即"无需等待"（目标已不存在，交由 hasFinished 结束会话）。 */
    @Test
    public void testIsIdleTrueWhenCatalogMissing() {
        assertThat(handler(null).isIdle(mock(World.class), POS, null, null)).isTrue();
    }

    /** 推料保护窗口内一律判未空闲，无论机器状态与产物如何。 */
    @Test
    public void testIsIdleRespectsPushGrace() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        itemHandler.setStack(1, new ItemStack(Items.APPLE, 1));
        TestHandler handler = handler(catalog(itemHandler));

        TargetSession session = session(Collections.emptyList());
        when(session.isPushGraceElapsed(anyLong(), anyInt())).thenReturn(false);
        assertThat(handler.isIdle(mock(World.class), POS, Collections.emptyList(), session)).isFalse();

        when(session.isPushGraceElapsed(anyLong(), anyInt())).thenReturn(true);
        assertThat(handler.isIdle(mock(World.class), POS, Collections.emptyList(), session)).isTrue();
    }

    /** 机器仍在处理时即使输出槽已有产物也不可收集。 */
    @Test
    public void testIsIdleFalseWhileGateWorking() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        itemHandler.setStack(1, new ItemStack(Items.APPLE, 1));
        TestHandler working = new TestHandler(catalog(itemHandler), (w, p, te) -> true);

        assertThat(working.isIdle(mock(World.class), POS, Collections.emptyList(), null)).isFalse();
    }

    /** 只认"产物确实已经出现"：输出槽空即未空闲（输入被搬进内部缓冲不算空闲依据）。 */
    @Test
    public void testIsIdleRequiresCollectableProduct() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);

        assertThat(handler(catalog(itemHandler)).isIdle(mock(World.class), POS, Collections.emptyList(), null))
                .isFalse();
    }

    /** 输出槽有物品（且不属于本批输入）即可收集。 */
    @Test
    public void testIsIdleTrueWithOutputProduct() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        itemHandler.setStack(1, new ItemStack(Items.APPLE, 1));
        TestHandler handler = handler(catalog(itemHandler));

        assertThat(handler.isIdle(mock(World.class), POS, Collections.emptyList(), null)).isTrue();
        // isIdle 只做探测，不修改目标内容
        assertThat(itemHandler.getStackInSlot(1).getCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------
    // hasFinished
    // ------------------------------------------------------------------

    /** 目录为 null → 直接算完成（目标已不存在）。 */
    @Test
    public void testHasFinishedTrueWhenCatalogMissing() {
        assertThat(handler(null).hasFinished(mock(World.class), POS, null, null)).isTrue();
    }

    /** 输入槽仍留着本批材料 → 未完成。 */
    @Test
    public void testHasFinishedFalseWithRemainingBatchInput() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        itemHandler.setStack(0, new ItemStack(Items.COAL, 1));
        TestHandler handler = handler(catalog(itemHandler));

        assertThat(handler.hasFinished(mock(World.class), POS,
                Collections.singletonList(new ItemStack(Items.COAL, 1)), null)).isFalse();
    }

    /** 产物还留在输出槽 → 未完成（角色已知时按角色判定，不看是否与本批同型）。 */
    @Test
    public void testHasFinishedFalseWithProductLeft() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        itemHandler.setStack(1, new ItemStack(Items.APPLE, 1));
        TestHandler handler = handler(catalog(itemHandler));

        assertThat(handler.hasFinished(mock(World.class), POS, Collections.emptyList(), null)).isFalse();
    }

    /** 输入耗尽且无产物残留 → 完成。 */
    @Test
    public void testHasFinishedTrueWhenDrained() {
        TestHandler handler = handler(catalog(new FakeItemHandler(3)));

        assertThat(handler.hasFinished(mock(World.class), POS,
                Collections.singletonList(new ItemStack(Items.COAL, 1)), null)).isTrue();
        assertThat(handler.hasFinished(mock(World.class), POS, Collections.emptyList(), null)).isTrue();
    }

    // ------------------------------------------------------------------
    // clearOutputs
    // ------------------------------------------------------------------

    /** 角色已知时只清输出槽；输入残留与升级槽不被动。 */
    @Test
    public void testClearOutputsOnlyTouchesOutputRole() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        itemHandler.setStack(0, new ItemStack(Items.COAL, 1));
        itemHandler.setStack(1, new ItemStack(Items.APPLE, 2));
        itemHandler.setStack(2, new ItemStack(Items.BRICK, 1));

        List<ItemStack> cleared = handler(catalog(itemHandler)).clearOutputs(
                mock(World.class), POS, null, session(Collections.singletonList(new ItemStack(Items.COAL, 1))));

        assertThat(cleared).hasSize(1);
        assertThat(cleared.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(itemHandler.getStackInSlot(0).getItem()).isEqualTo(Items.COAL);
        assertThat(itemHandler.getStackInSlot(2).getItem()).isEqualTo(Items.BRICK);
        assertThat(itemHandler.getStackInSlot(1).isEmpty()).isTrue();
    }

    /** 目录为 null 时清理结果为空表。 */
    @Test
    public void testClearOutputsNullCatalogYieldsEmpty() {
        assertThat(handler(null).clearOutputs(mock(World.class), POS, null, null)).isNotNull().isEmpty();
    }

    /** session 为 null 时按空快照处理（回退/清理都不取走任何东西）。 */
    @Test
    public void testRevertWithNullSessionRevertsNothing() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        itemHandler.setStack(0, new ItemStack(Items.APPLE, 2));

        List<ItemStack> reverted = handler(catalog(itemHandler)).revertMaterials(mock(World.class), POS, null, null);

        assertThat(reverted).isEmpty();
        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(2);
    }

    /** 非空输入的推料路径至少要在"目录可用 + 材料放得下"时成功（台账精度见 PushLedgerTest）。 */
    @Test
    public void testPushMaterialsSuccessAndFailure() {
        World world = mock(World.class);

        assertThat(handler(catalog(new FakeItemHandler(3)))
                .pushMaterials(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null, null)).isTrue();

        assertThat(handler(null)
                .pushMaterials(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null, null)).isFalse();

        FakeItemHandler single = new FakeItemHandler(3);
        single.setStack(0, new ItemStack(Items.BRICK, 64));
        assertThat(handler(catalog(single))
                .pushMaterials(world, POS, crafting(new ItemStack(Items.APPLE, 1)), null, null)).isFalse();
    }

    /** 空配方视为推送成功且不触碰任何槽位（与通用兜底处理器一致）。 */
    @Test
    public void testPushMaterialsEmptyIngredientsSucceeds() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);

        assertThat(handler(catalog(itemHandler))
                .pushMaterials(mock(World.class), POS, crafting(), null, null)).isTrue();
        for (int i = 0; i < 3; i++) {
            assertThat(itemHandler.getStackInSlot(i).isEmpty()).isTrue();
        }
    }

    /** 输入槽是目录里唯一的推料目标（输出/升级槽不得被写入）。 */
    @Test
    public void testPushMaterialsTargetsInputSlotsOnly() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);

        assertThat(handler(catalog(itemHandler)).pushMaterials(
                mock(World.class), POS, crafting(new ItemStack(Items.APPLE, 5)), null, null)).isTrue();

        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(5);
        assertThat(itemHandler.getStackInSlot(1).isEmpty()).isTrue();
        assertThat(itemHandler.getStackInSlot(2).isEmpty()).isTrue();
    }

    /**
     * 角色未知（降级）时推料目标是全部槽位，与重构前的"遍历六面任意槽插入"一致。
     *
     * <p>本用例同时锁住"目录角色未知 → 框架走宽松策略"这条降级契约的实际含义。</p>
     */
    @Test
    public void testPushMaterialsFallsBackToAllSlotsWhenRolesUnknown() {
        FakeItemHandler itemHandler = new FakeItemHandler(1);
        ISlotCatalog unknown = ISlotCatalog.builder().add(itemHandler, 0, null, SlotRole.UNKNOWN).build();

        assertThat(handler(unknown).pushMaterials(
                mock(World.class), POS, crafting(new ItemStack(Items.APPLE, 4)), null, null)).isTrue();

        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(4);
    }

    /** 输入快照按槽位顺序快照为拷贝（{@code pushMaterials} 不消费 ingredients 本体）。 */
    @Test
    public void testPushMaterialsDoesNotConsumeIngredients() {
        FakeItemHandler itemHandler = new FakeItemHandler(3);
        InventoryCrafting inv = crafting(new ItemStack(Items.APPLE, 2));

        assertThat(handler(catalog(itemHandler))
                .pushMaterials(mock(World.class), POS, inv, null, null)).isTrue();

        assertThat(inv.getStackInSlot(0).getCount()).isEqualTo(2);
        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(2);
    }
}
