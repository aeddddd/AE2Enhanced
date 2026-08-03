package com.github.aeddddd.ae2enhanced.test.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyHubBatchCrafting;
import com.github.aeddddd.ae2enhanced.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;

/**
 * {@link AssemblyHubBatchCrafting} 单元测试:虚拟/真实轨道的批量执行与记账语义.
 * <p>枢纽控制器与合成服务为 mock,CPU 库存/等待回流使用真实
 * {@link ListCraftingInventory} 以便断言数量级不变量.</p>
 */
class AssemblyHubBatchCraftingTest {

    static {
        AE2KeyTypeTestBootstrap.bootstrap();
    }

    /** 任务进度持有器(替代各 CPU 实现包级私有的 TaskProgress). */
    private static final class Progress {
        long value;

        Progress(long value) {
            this.value = value;
        }
    }

    private static final AssemblyHubBatchCrafting.TaskProgressAccess ACCESS =
            new AssemblyHubBatchCrafting.TaskProgressAccess() {
                @Override
                public long get(Object progress) {
                    return ((Progress) progress).value;
                }

                @Override
                public void set(Object progress, long value) {
                    ((Progress) progress).value = value;
                }
            };

    private AssemblyControllerBlockEntity hub;
    private CraftingService craftingService;
    private ListCraftingInventory inventory;
    private ListCraftingInventory waitingFor;
    private List<GenericStack> hubPendingOutputs;
    private List<long[]> trackerCalls;
    private List<Object[]> waitingForNotifications;
    private boolean[] markDirtyCalled;
    private IActionSource actionSource;
    private Level level;

    @BeforeEach
    void setUp() {
        hub = mock(AssemblyControllerBlockEntity.class);
        craftingService = mock(CraftingService.class);
        inventory = new ListCraftingInventory(key -> {
        });
        waitingFor = new ListCraftingInventory(key -> {
        });
        hubPendingOutputs = new ArrayList<>();
        trackerCalls = new ArrayList<>();
        waitingForNotifications = new ArrayList<>();
        markDirtyCalled = new boolean[1];
        actionSource = mock(IActionSource.class);
        level = mock(Level.class);

        when(hub.isFormed()).thenReturn(true);
        when(hub.canBatch()).thenReturn(true);
        when(hub.getParallelCap()).thenReturn(Long.MAX_VALUE);
        when(hub.canAcceptRealBatch(anyInt())).thenReturn(true);
        org.mockito.Mockito.doAnswer(inv -> {
            hubPendingOutputs.add(inv.getArgument(0));
            return null;
        }).when(hub).addPendingOutput(any(GenericStack.class));
    }

    private static AEItemKey stone() {
        return AEItemKey.of(Items.STONE);
    }

    private static AEItemKey stick() {
        return AEItemKey.of(Items.STICK);
    }

    private static long amountOf(ListCraftingInventory inv, AEKey key) {
        return inv.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
    }

    private static long pendingOf(AEKey key, List<GenericStack> pending) {
        return pending.stream().filter(s -> s.what().equals(key)).mapToLong(GenericStack::amount).sum();
    }

    /** 单输入样板:候选/倍率/剩余物可配. */
    private static IPatternDetails pattern(GenericStack input, long multiplier, AEKey remainder,
            GenericStack... outputs) {
        var in = mock(IPatternDetails.IInput.class);
        when(in.getPossibleInputs()).thenReturn(new GenericStack[] { input });
        when(in.getMultiplier()).thenReturn(multiplier);
        when(in.isValid(any(), any())).thenReturn(true);
        when(in.getRemainingKey(any())).thenReturn(remainder);
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { in });
        when(pattern.getOutputs()).thenReturn(outputs);
        return pattern;
    }

    /** 把枢纽登记为样板的唯一 provider,并按轨道配置批量信息. */
    private void registerHub(IPatternDetails pattern, boolean virtual) {
        when(craftingService.getProviders(pattern)).thenReturn(List.of(hub));
        var info = new AssemblyControllerBlockEntity.PatternBatchInfo();
        info.virtual = virtual;
        when(hub.getPatternBatchInfo(pattern)).thenReturn(info);
    }

    private void process(Map<IPatternDetails, Progress> tasks, GenericStack finalOutput) {
        AssemblyHubBatchCrafting.processHubBatches(tasks, ACCESS, inventory, waitingFor,
                (amount, type) -> trackerCalls.add(new long[] { amount }), finalOutput, actionSource,
                () -> markDirtyCalled[0] = true, craftingService, level,
                (key, amount) -> waitingForNotifications.add(new Object[] { key, amount }));
    }

    // ===== 虚拟轨道 =====

    /** 虚拟轨道全量批量:扣料、最终产出登记 waitingFor 并交枢纽缓冲、进度清零. */
    @Test
    void testVirtualBatchFull() {
        var pattern = pattern(new GenericStack(stone(), 2), 1, null, new GenericStack(stick(), 4));
        registerHub(pattern, true);
        inventory.insert(stone(), 100, Actionable.MODULATE);
        var progress = new Progress(5);

        process(Map.of(pattern, progress), new GenericStack(stick(), 4));

        assertThat(progress.value).isZero();
        assertThat(amountOf(inventory, stone())).isEqualTo(90); // 2 × 5 扣料
        assertThat(amountOf(waitingFor, stick())).isEqualTo(20); // 4 × 5 在途
        assertThat(pendingOf(stick(), hubPendingOutputs)).isEqualTo(20);
        assertThat(waitingForNotifications).containsExactly(new Object[] { stick(), 20L });
        assertThat(markDirtyCalled[0]).isTrue();

        // 批量成功后枢纽置忙并重置冷却;动作来源设置后复位
        var order = inOrder(hub);
        order.verify(hub).setCurrentActionSource(actionSource);
        order.verify(hub).setBatchBusy(true);
        order.verify(hub).resetBatchCooldown();
        order.verify(hub).setCurrentActionSource(null);
    }

    /** 库存不足整批时缩减到可执行批量,余量留给后续 tick. */
    @Test
    void testVirtualBatchShrinksToAvailable() {
        var pattern = pattern(new GenericStack(stone(), 2), 1, null, new GenericStack(stick(), 4));
        registerHub(pattern, true);
        inventory.insert(stone(), 6, Actionable.MODULATE); // 仅够 3 份
        var progress = new Progress(10);

        process(Map.of(pattern, progress), new GenericStack(stick(), 4));

        // 首轮执行 3 份;后续轮次库存耗尽不再推进
        assertThat(progress.value).isEqualTo(7);
        assertThat(amountOf(inventory, stone())).isZero();
        assertThat(amountOf(waitingFor, stick())).isEqualTo(12);
    }

    /** 递归合成(产物即原料):非最后批次回留种子,最后一批全部经网络回流. */
    @Test
    void testVirtualBatchRecursiveSeedRetention() {
        var pattern = pattern(new GenericStack(stone(), 1), 1, null, new GenericStack(stone(), 2));
        registerHub(pattern, true);
        when(hub.getParallelCap()).thenReturn(2L); // 强制分批
        inventory.insert(stone(), 10, Actionable.MODULATE);
        var progress = new Progress(3);

        process(Map.of(pattern, progress), new GenericStack(stone(), 2));

        assertThat(progress.value).isZero();
        // 批 1(2 份,非最后):扣 2 留 2 种子,净回流 2;批 2(1 份,最后):扣 1 全回流 2
        assertThat(amountOf(inventory, stone())).isEqualTo(9);
        assertThat(amountOf(waitingFor, stone())).isEqualTo(4);
        assertThat(pendingOf(stone(), hubPendingOutputs)).isEqualTo(4);
    }

    // ===== 真实轨道 =====

    /** 真催化剂(剩余物 = 输入):借用 1 份并立即返还 CPU 库存,产物经网络回流. */
    @Test
    void testRealBatchCatalystReturned() {
        var pattern = pattern(new GenericStack(stick(), 1), 1, stick(), new GenericStack(stone(), 1));
        registerHub(pattern, false);
        inventory.insert(stick(), 5, Actionable.MODULATE);
        var progress = new Progress(3);

        process(Map.of(pattern, progress), null);

        assertThat(progress.value).isZero();
        assertThat(amountOf(inventory, stick())).isEqualTo(5); // 借用 1 份后返还,净变化 0
        assertThat(amountOf(waitingFor, stone())).isEqualTo(3);
        assertThat(pendingOf(stone(), hubPendingOutputs)).isEqualTo(3);
        // 催化剂返还算作可用物品递减记账
        assertThat(trackerCalls).containsExactly(new long[] { 1L });
    }

    /** 普通容器物(剩余物为不同物品):按批量产出并登记 waitingFor. */
    @Test
    void testRealBatchContainerRemainder() {
        var bucket = AEItemKey.of(Items.BUCKET);
        var pattern = pattern(new GenericStack(stick(), 1), 1, bucket, new GenericStack(stone(), 1));
        registerHub(pattern, false);
        inventory.insert(stick(), 10, Actionable.MODULATE);
        var progress = new Progress(2);

        process(Map.of(pattern, progress), null);

        assertThat(progress.value).isZero();
        assertThat(amountOf(inventory, stick())).isEqualTo(8);
        assertThat(amountOf(waitingFor, stone())).isEqualTo(2);
        assertThat(amountOf(waitingFor, bucket)).isEqualTo(2);
        assertThat(pendingOf(bucket, hubPendingOutputs)).isEqualTo(2);
    }

    /** 消耗性转换(同物品不同 NBT):强制逐份处理. */
    @Test
    void testRealBatchDamageTransformForcedSerial() {
        var damagedStack = new ItemStack(Items.STICK);
        var tag = new CompoundTag();
        tag.putInt("Damage", 1);
        damagedStack.setTag(tag);
        var damaged = AEItemKey.of(damagedStack);

        var pattern = pattern(new GenericStack(stick(), 1), 1, damaged, new GenericStack(stone(), 1));
        registerHub(pattern, false);
        inventory.insert(stick(), 10, Actionable.MODULATE);
        var progress = new Progress(5);

        process(Map.of(pattern, progress), null);

        // 逐份:首轮 1 份;后续轮次继续逐份,直至完成(库存充足)
        assertThat(progress.value).isZero();
        assertThat(amountOf(waitingFor, damaged)).isEqualTo(5);
        assertThat(pendingOf(damaged, hubPendingOutputs)).isEqualTo(5);
        assertThat(amountOf(waitingFor, stone())).isEqualTo(5);
    }

    // ===== 跳过路径 =====

    /** 无 provider / 枢纽未成型 / 枢纽不可批量 → 任务原样保留(回退原生逐份路径). */
    @Test
    void testSkippedTasksLeftUntouched() {
        var noProvider = pattern(new GenericStack(stone(), 1), 1, null, new GenericStack(stick(), 1));
        when(craftingService.getProviders(noProvider)).thenReturn(List.of());

        var notFormed = pattern(new GenericStack(stone(), 1), 1, null, new GenericStack(stick(), 1));
        when(craftingService.getProviders(notFormed)).thenReturn(List.of(hub));
        var notFormedInfo = new AssemblyControllerBlockEntity.PatternBatchInfo();
        when(hub.getPatternBatchInfo(notFormed)).thenReturn(notFormedInfo);

        inventory.insert(stone(), 100, Actionable.MODULATE);
        when(hub.isFormed()).thenReturn(false);

        var p1 = new Progress(3);
        var p2 = new Progress(4);
        process(Map.of(noProvider, p1, notFormed, p2), null);

        assertThat(p1.value).isEqualTo(3);
        assertThat(p2.value).isEqualTo(4);
        verify(hub, never()).setBatchBusy(true);
        assertThat(hubPendingOutputs).isEmpty();
    }

    /** 枢纽 canBatch 为 false(冷却中)时不抢占任务. */
    @Test
    void testHubOnCooldownSkipped() {
        var pattern = pattern(new GenericStack(stone(), 1), 1, null, new GenericStack(stick(), 1));
        registerHub(pattern, true);
        inventory.insert(stone(), 100, Actionable.MODULATE);
        when(hub.canBatch()).thenReturn(false);

        var progress = new Progress(3);
        process(Map.of(pattern, progress), null);

        assertThat(progress.value).isEqualTo(3);
        assertThat(amountOf(inventory, stone())).isEqualTo(100);
    }

    /** 中间产物(非最终产出)直接进入 CPU 库存并递减记账,不占网络缓冲. */
    @Test
    void testVirtualBatchIntermediateGoesToInventory() {
        var gravel = AEItemKey.of(Items.GRAVEL);
        var pattern = pattern(new GenericStack(stone(), 1), 1, null,
                new GenericStack(stick(), 1), new GenericStack(gravel, 3));
        registerHub(pattern, true);
        inventory.insert(stone(), 10, Actionable.MODULATE);
        var progress = new Progress(2);

        process(Map.of(pattern, progress), new GenericStack(stick(), 1));

        assertThat(progress.value).isZero();
        assertThat(amountOf(inventory, gravel)).isEqualTo(6); // 中间产物直进 CPU 库存
        assertThat(amountOf(waitingFor, stick())).isEqualTo(2); // 仅最终产出走回流
        assertThat(pendingOf(gravel, hubPendingOutputs)).isZero();
        assertThat(trackerCalls).containsExactly(new long[] { 6L });
    }
}
