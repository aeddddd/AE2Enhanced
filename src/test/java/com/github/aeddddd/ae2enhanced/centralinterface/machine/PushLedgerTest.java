package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.centralinterface.DualityCentralInterface;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetBinding;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetOwnershipTracker;
import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.FakeItemHandler;

import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.IItemHandler;

/**
 * 物理推料台账（{@link PushLedger}）与台账口径回退测试.
 *
 * <p>核心场景：槽内<b>原本就有</b>同型物品时，物理推料会被目标容器合并进同一堆叠。
 * 旧口径（按"本批材料快照"整槽取走）会把机器原有的物品一起偷走；台账口径只取回本批数量。</p>
 *
 * <p>全部使用 {@link FakeItemHandler}（严格遵守 simulate 语义、插入与同型堆叠合并），
 * 目录用 {@link CatalogBuilder} 构造，回退时重建目录（模拟真实 tick 里的目录生命周期）。</p>
 */
public class PushLedgerTest {

    private static final BlockPos POS = new BlockPos(1, 64, 1);

    @BeforeAll
    public static void boot() {
        AE2TestBootstrap.boot();
    }

    @BeforeEach
    public void setUp() {
        TargetOwnershipTracker.resetForTesting();
    }

    private static ISlotCatalog inputCatalog(IItemHandler handler, SlotRole role) {
        return ISlotCatalog.builder().addAll(handler, EnumFacing.DOWN, role).build();
    }

    private static InventoryCrafting crafting(ItemStack... stacks) {
        InventoryCrafting inv = new InventoryCrafting(mock(Container.class), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            inv.setInventorySlotContents(i, stacks[i]);
        }
        return inv;
    }

    // ------------------------------------------------------------------
    // 台账记录（真实插入阶段）
    // ------------------------------------------------------------------

    /** 真实插入逐槽记账：每槽记录的是"实际放进去了多少"，而不是请求数量。 */
    @Test
    public void testPushRecordsActualInsertedCountPerSlot() {
        FakeItemHandler handler = new FakeItemHandler(2, 40);
        ISlotCatalog catalog = inputCatalog(handler, SlotRole.INPUT);

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(catalog,
                Collections.singletonList(new ItemStack(Items.APPLE, 64)));

        assertThat(outcome.isSuccess()).isTrue();
        // 槽上限 40：第一槽收 40、第二槽收 24，共两条记录（模拟阶段不得留下记录）
        assertThat(outcome.ledger().entries()).hasSize(2);
        assertThat(outcome.ledger().totalCount()).isEqualTo(64);
        assertThat(outcome.ledger().entries().get(0).count()).isEqualTo(40);
        assertThat(outcome.ledger().entries().get(0).index()).isEqualTo(0);
        assertThat(outcome.ledger().entries().get(1).count()).isEqualTo(24);
        assertThat(outcome.ledger().entries().get(1).index()).isEqualTo(1);
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(40);
        assertThat(handler.getStackInSlot(1).getCount()).isEqualTo(24);
    }

    /** 空批次不产生任何台账。 */
    @Test
    public void testEmptyBatchYieldsEmptyLedger() {
        FakeItemHandler handler = new FakeItemHandler(1, 64);

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(
                inputCatalog(handler, SlotRole.INPUT), Collections.emptyList());

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.ledger().isEmpty()).isTrue();
    }

    /**
     * 模拟阶段通过、真实阶段失败（机器 simulate 与真实语义不一致）时：
     * 不得留下任何台账，且已插入部分必须回退干净。
     */
    @Test
    public void testSimulateOnlyAcceptanceLeavesEmptyLedger() {
        IItemHandler handler = mock(IItemHandler.class);
        when(handler.getSlots()).thenReturn(1);
        // 模拟阶段全收，真实阶段全退
        when(handler.insertItem(anyInt(), any(ItemStack.class), anyBoolean()))
                .thenAnswer(invocation -> invocation.getArgument(2) ? ItemStack.EMPTY : invocation.getArgument(1));
        ISlotCatalog catalog = inputCatalog(handler, SlotRole.INPUT);

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(catalog,
                Collections.singletonList(new ItemStack(Items.APPLE, 1)));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.ledger().isEmpty()).isTrue();
        assertThat(outcome.unplaced()).hasSize(1);
    }

    /** 无推料目标（全部槽位都不是输入角色）时失败且不产生台账。 */
    @Test
    public void testNoPushTargetYieldsEmptyLedger() {
        FakeItemHandler handler = new FakeItemHandler(1, 64);

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(
                inputCatalog(handler, SlotRole.OUTPUT),
                Collections.singletonList(new ItemStack(Items.APPLE, 1)));

        assertThat(outcome.isSuccess()).isFalse();
        assertThat(outcome.ledger().isEmpty()).isTrue();
        assertThat(handler.getStackInSlot(0).isEmpty()).isTrue();
    }

    // ------------------------------------------------------------------
    // 台账口径回退：只取回本批数量
    // ------------------------------------------------------------------

    /** 槽内原有 32 个、本批推入 32 个 → 回退只取回 32 个，机器原有的 32 个留在槽里。 */
    @Test
    public void testRevertByLedgerKeepsPreexistingSameItem() {
        FakeItemHandler handler = new FakeItemHandler(1, 64);
        handler.setStack(0, new ItemStack(Items.APPLE, 32)); // 机器原有的同类物品

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(
                inputCatalog(handler, SlotRole.INPUT),
                Collections.singletonList(new ItemStack(Items.APPLE, 32)));

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(outcome.ledger().totalCount()).isEqualTo(32);
        // 推料后被合并成一堆 64（这正是旧口径会偷走机器物品的成因）
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(64);

        // 回退时目录是重建的（真实 tick 里每次读写都新建一次目录）
        List<ItemStack> reverted = ItemTransferHelper.revertBatch(
                inputCatalog(handler, SlotRole.INPUT), Collections.emptyList(), outcome.ledger());

        assertThat(reverted).hasSize(1);
        assertThat(reverted.get(0).getItem()).isEqualTo(Items.APPLE);
        assertThat(reverted.get(0).getCount()).isEqualTo(32);
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(32); // 机器原有的 32 个仍在
    }

    /** 多槽场景：每槽都只取回该槽台账记录的数量。 */
    @Test
    public void testRevertByLedgerCapsEverySlotAtItsOwnCount() {
        FakeItemHandler handler = new FakeItemHandler(2, 64);
        handler.setStack(0, new ItemStack(Items.APPLE, 30)); // 槽 0 原有 30

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(
                inputCatalog(handler, SlotRole.INPUT),
                Collections.singletonList(new ItemStack(Items.APPLE, 64)));

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(64); // 30 + 34
        assertThat(handler.getStackInSlot(1).getCount()).isEqualTo(30); // 本批落进空槽

        List<ItemStack> reverted = ItemTransferHelper.revertBatch(
                inputCatalog(handler, SlotRole.INPUT), Collections.emptyList(), outcome.ledger());

        assertThat(reverted).hasSize(2);
        assertThat(reverted.get(0).getCount()).isEqualTo(30); // 逆序：先取回槽 1
        assertThat(reverted.get(1).getCount()).isEqualTo(34); // 再取回槽 0 的本批部分
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(30);
        assertThat(handler.getStackInSlot(1).isEmpty()).isTrue();
    }

    /**
     * 回退时的目录里 {@code IItemHandler} 是<b>新包装对象</b>（如 Thermal Expansion 每次新建
     * {@code InventoryArrayItemHandler}）：按 (面, 下标, 角色) 仍能定位到同一个槽位。
     */
    @Test
    public void testRevertByLedgerLocatesSlotAcrossHandlerRewrapping() {
        FakeItemHandler backing = new FakeItemHandler(1, 64);
        backing.setStack(0, new ItemStack(Items.APPLE, 10));

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(
                inputCatalog(backing, SlotRole.INPUT),
                Collections.singletonList(new ItemStack(Items.APPLE, 20)));

        assertThat(outcome.isSuccess()).isTrue();
        assertThat(backing.getStackInSlot(0).getCount()).isEqualTo(30);

        IItemHandler rewrapped = new ForwardingHandler(backing);
        List<ItemStack> reverted = ItemTransferHelper.revertBatch(
                inputCatalog(rewrapped, SlotRole.INPUT), Collections.emptyList(), outcome.ledger());

        assertThat(reverted).hasSize(1);
        assertThat(reverted.get(0).getCount()).isEqualTo(20);
        assertThat(backing.getStackInSlot(0).getCount()).isEqualTo(10); // 预置的 10 个没被碰
    }

    /** 槽内物品身份与台账不符（机器换掉了内容）时：不取任何东西，也不抛异常。 */
    @Test
    public void testRevertByLedgerSkipsIdentityMismatch() {
        FakeItemHandler handler = new FakeItemHandler(1, 64);

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(
                inputCatalog(handler, SlotRole.INPUT),
                Collections.singletonList(new ItemStack(Items.APPLE, 8)));
        assertThat(outcome.ledger().totalCount()).isEqualTo(8);

        // 机器把苹果换成了砖头
        handler.setStack(0, new ItemStack(Items.BRICK, 8));

        List<ItemStack> reverted = ItemTransferHelper.revertBatch(
                inputCatalog(handler, SlotRole.INPUT), Collections.emptyList(), outcome.ledger());

        assertThat(reverted).isEmpty();
        assertThat(handler.getStackInSlot(0).getItem()).isEqualTo(Items.BRICK);
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(8);
    }

    /** 槽内本批材料已被机器消耗（槽空）时：什么都不取，不抛异常。 */
    @Test
    public void testRevertByLedgerSkipsConsumedSlot() {
        FakeItemHandler handler = new FakeItemHandler(1, 64);

        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(
                inputCatalog(handler, SlotRole.INPUT),
                Collections.singletonList(new ItemStack(Items.APPLE, 8)));

        handler.setStack(0, ItemStack.EMPTY); // 机器消耗掉了

        List<ItemStack> reverted = ItemTransferHelper.revertBatch(
                inputCatalog(handler, SlotRole.INPUT), Collections.emptyList(), outcome.ledger());

        assertThat(reverted).isEmpty();
    }

    /**
     * 旧签名（无台账）保持原口径：按"本批材料快照"整槽取走。
     *
     * <p>该口径在"槽内混有机器原有同型物品"时会多取——这正是台账口径要修的问题；
     * 但 handler 自行推料（不走框架）时只能沿用旧口径，故此处把它的行为钉住，
     * 防止后续重构无意改变其它调用点的语义。</p>
     */
    @Test
    public void testLegacySignatureKeepsSnapshotSemantics() {
        FakeItemHandler handler = new FakeItemHandler(1, 64);
        handler.setStack(0, new ItemStack(Items.APPLE, 32));

        ItemTransferHelper.push(inputCatalog(handler, SlotRole.INPUT),
                Collections.singletonList(new ItemStack(Items.APPLE, 32)));
        assertThat(handler.getStackInSlot(0).getCount()).isEqualTo(64);

        List<ItemStack> reverted = ItemTransferHelper.revertBatch(
                inputCatalog(handler, SlotRole.INPUT),
                Collections.singletonList(new ItemStack(Items.APPLE, 32)));

        assertThat(reverted).hasSize(1);
        assertThat(reverted.get(0).getCount()).isEqualTo(64); // 旧口径：整槽取走（含机器原有的）
        assertThat(handler.getStackInSlot(0).isEmpty()).isTrue();
    }

    /** 空台账退化为旧口径：拿不到台账时行为与旧签名一致。 */
    @Test
    public void testEmptyLedgerFallsBackToSnapshotSemantics() {
        FakeItemHandler handler = new FakeItemHandler(1, 64);
        handler.setStack(0, new ItemStack(Items.APPLE, 4));

        List<ItemStack> reverted = ItemTransferHelper.revertBatch(
                inputCatalog(handler, SlotRole.INPUT),
                Collections.singletonList(new ItemStack(Items.APPLE, 1)),
                PushLedger.EMPTY);

        assertThat(reverted).hasSize(1);
        assertThat(reverted.get(0).getCount()).isEqualTo(4);
    }

    // ------------------------------------------------------------------
    // 框架默认 handler 的接线：推料写台账、回退按台账取回
    // ------------------------------------------------------------------

    /** {@code AbstractMachineHandler} 把台账写进 session，回退时按台账只取回本批数量。 */
    @Test
    public void testAbstractMachineHandlerWiresLedgerIntoSession() {
        FakeItemHandler itemHandler = new FakeItemHandler(1, 64);
        itemHandler.setStack(0, new ItemStack(Items.APPLE, 32)); // 机器原有
        ISlotCatalog catalog = inputCatalog(itemHandler, SlotRole.INPUT);
        CatalogHandler handler = new CatalogHandler(catalog);

        TargetSession session = newSession();
        assertThat(session.beginPush(null)).isTrue();
        // dispatcher 在推料前登记本批材料快照（旧口径的识别依据）
        session.recordPushedBatch(Collections.singletonList(new ItemStack(Items.APPLE, 32)), null);

        assertThat(handler.pushMaterials(null, POS, crafting(new ItemStack(Items.APPLE, 32)), null, session)).isTrue();
        assertThat(session.getPushLedger()).isNotNull();
        assertThat(session.getPushLedger().totalCount()).isEqualTo(32);
        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(64);

        List<ItemStack> reverted = handler.revertMaterials(null, POS, null, session);

        assertThat(reverted).hasSize(1);
        assertThat(reverted.get(0).getCount()).isEqualTo(32);
        assertThat(itemHandler.getStackInSlot(0).getCount()).isEqualTo(32);
    }

    /** 台账不进 NBT、reset 清空；跨 commitPush 保留（处理中/收集中的超时回退仍需要它）。 */
    @Test
    public void testSessionLedgerLifecycle() {
        TargetSession session = newSession();
        assertThat(session.getPushLedger()).isNull();

        assertThat(session.beginPush(null)).isTrue();
        PushLedger ledger = ledgerOfOneApple();
        session.recordPushLedger(ledger);
        assertThat(session.getPushLedger()).isSameAs(ledger);

        session.commitPush(null, Collections.singletonList(new ItemStack(Items.APPLE, 1)), null, 100L);
        assertThat(session.getPushLedger()).isSameAs(ledger); // 处理阶段仍需台账

        session.reset();
        assertThat(session.getPushLedger()).isNull();
    }

    /** 新一批 beginPush 会丢弃上一批的台账，避免本批失败时按旧台账取回物品。 */
    @Test
    public void testBeginPushClearsPreviousLedger() {
        TargetSession session = newSession();
        assertThat(session.beginPush(null)).isTrue();
        session.recordPushLedger(ledgerOfOneApple());
        session.commitPush(null, null, null, 10L);
        session.beginCollect();
        session.finishCollect(true); // 回到 IDLE

        assertThat(session.beginPush(null)).isTrue();
        assertThat(session.getPushLedger()).isNull();
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private static TargetSession newSession() {
        DualityCentralInterface owner = mock(DualityCentralInterface.class);
        when(owner.isAlive()).thenReturn(true);
        return new TargetSession(new TargetBinding(POS, 0, "ae2enhanced:test"), owner);
    }

    private static PushLedger ledgerOfOneApple() {
        FakeItemHandler handler = new FakeItemHandler(1, 64);
        ItemTransferHelper.PushOutcome outcome = ItemTransferHelper.push(
                inputCatalog(handler, SlotRole.INPUT),
                Collections.singletonList(new ItemStack(Items.APPLE, 1)));
        assertThat(outcome.isSuccess()).isTrue();
        return outcome.ledger();
    }

    /** 只回答"目录是什么"的最小框架 handler；世界参数不参与，故可传 null。 */
    private static final class CatalogHandler extends AbstractMachineHandler {

        private final ISlotCatalog catalog;

        CatalogHandler(ISlotCatalog catalog) {
            this.catalog = catalog;
        }

        @Override
        protected ISlotCatalog catalogFor(World world, BlockPos pos) {
            return this.catalog;
        }

        @Override
        protected MachineIdle idleGate(World world, BlockPos pos) {
            return MachineIdle.none();
        }

        /** 夹具不参与 blockId 匹配：声明一条兜底表项（等价于重构前的 canHandle 恒 false）。 */
        @Override
        protected MachineSpec defineSpec() {
            return MachineSpec.builder().fallback().access(this).build();
        }
    }

    /** 每次构造都是新实例的 handler 包装（模拟"能力对象每次重新包装"的机器）。 */
    private static final class ForwardingHandler implements IItemHandler {

        private final IItemHandler delegate;

        ForwardingHandler(IItemHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return delegate.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }
    }
}
