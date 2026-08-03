package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingLink;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ElapsedTimeTracker;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCpuLogicAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ElapsedTimeTrackerAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ExecutingCraftingJobAccessor;
import com.github.aeddddd.ae2enhanced.specialcrafting.SelfRefOutputGate;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

/**
 * {@link SelfRefOutputGate} 单元测试:自消耗 job 的最终产出交付门控.
 * <p>单元测试环境不加载 mixin,因此以 Mockito {@code extraInterfaces}
 * 为被测对象补上 accessor 接口,模拟游戏内 mixin 注入后的类型结构.</p>
 */
class SelfRefOutputGateTest {

    static {
        MinecraftTestBootstrap.bootstrap();
    }

    private CraftingCpuLogic logic;
    private CraftingCpuLogicAccessor logicAcc;
    private ExecutingCraftingJobAccessor jobAcc;
    private CraftingCPUCluster cluster;
    private CraftingLink link;
    private ListCraftingInventory inventory;
    private ListCraftingInventory waitingFor;

    @BeforeEach
    void setUp() {
        // 为 mock 补上 mixin accessor 接口,模拟游戏内注入结果
        logic = mock(CraftingCpuLogic.class, withSettings().extraInterfaces(CraftingCpuLogicAccessor.class));
        logicAcc = (CraftingCpuLogicAccessor) logic;
        var job = mock(ExecutingCraftingJob.class, withSettings().extraInterfaces(ExecutingCraftingJobAccessor.class));
        jobAcc = (ExecutingCraftingJobAccessor) job;
        cluster = mock(CraftingCPUCluster.class);
        link = mock(CraftingLink.class);
        inventory = new ListCraftingInventory(key -> {
        });
        waitingFor = new ListCraftingInventory(key -> {
        });
        var tracker = mock(ElapsedTimeTracker.class,
                withSettings().extraInterfaces(ElapsedTimeTrackerAccessor.class));

        when(logicAcc.getJob()).thenReturn(job);
        when(logicAcc.getCluster()).thenReturn(cluster);
        when(logic.getInventory()).thenReturn(inventory);
        when(jobAcc.getWaitingFor()).thenReturn(waitingFor);
        when(jobAcc.getTimeTracker()).thenReturn(tracker);
        when(jobAcc.getLink()).thenReturn(link);
    }

    private static GenericStack item(Item item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }

    /** 自消耗样板:1 stone → 2 stone(产出仍是任务输入). */
    private static IPatternDetails selfConsumingPattern() {
        return new ProcessingPatternBuilder(item(Items.STONE, 2))
                .addPreciseInput(1, item(Items.STONE, 1))
                .build();
    }

    /** 普通样板:1 stone → 4 stick(产出不再是输入). */
    private static IPatternDetails normalPattern() {
        return new ProcessingPatternBuilder(item(Items.STICK, 4))
                .addPreciseInput(1, item(Items.STONE, 1))
                .build();
    }

    /** job 为空 → 不接管. */
    @Test
    void testNoJobReturnsNull() {
        when(logicAcc.getJob()).thenReturn(null);
        assertThat(SelfRefOutputGate.handleInsert(logic, AEItemKey.of(Items.STONE), 1, Actionable.MODULATE))
                .isNull();
    }

    /** 回流 key 为 null → 不接管. */
    @Test
    void testNullKeyReturnsNull() {
        assertThat(SelfRefOutputGate.handleInsert(logic, null, 1, Actionable.MODULATE)).isNull();
    }

    /** job 无最终产出 → 不接管. */
    @Test
    void testNoFinalOutputReturnsNull() {
        when(jobAcc.getFinalOutput()).thenReturn(null);
        assertThat(SelfRefOutputGate.handleInsert(logic, AEItemKey.of(Items.STONE), 1, Actionable.MODULATE))
                .isNull();
    }

    /** 回流 key 与最终产出不匹配 → 不接管. */
    @Test
    void testMismatchedKeyReturnsNull() {
        when(jobAcc.getFinalOutput()).thenReturn(item(Items.STICK, 4));
        assertThat(SelfRefOutputGate.handleInsert(logic, AEItemKey.of(Items.STONE), 1, Actionable.MODULATE))
                .isNull();
    }

    /** 非自消耗且 CPU 库存无滞留 → 走原生(返回 null). */
    @Test
    void testNormalJobNotGated() {
        when(jobAcc.getFinalOutput()).thenReturn(item(Items.STICK, 4));
        doReturn(Map.of(normalPattern(), new Object())).when(jobAcc).getTasks();

        assertThat(SelfRefOutputGate.handleInsert(logic, AEItemKey.of(Items.STICK), 4, Actionable.MODULATE))
                .isNull();
        // 门控未接管:库存/等待量均不变
        assertThat(inventory.extract(AEItemKey.of(Items.STICK), Long.MAX_VALUE, Actionable.SIMULATE)).isZero();
        assertThat(waitingFor.extract(AEItemKey.of(Items.STICK), Long.MAX_VALUE, Actionable.SIMULATE)).isZero();
    }

    /** 自消耗 job 但无在途等待 → 与原生一致拒收(返回 0). */
    @Test
    void testNoWaitingForRejects() {
        when(jobAcc.getFinalOutput()).thenReturn(item(Items.STONE, 2));
        doReturn(Map.of(selfConsumingPattern(), new Object())).when(jobAcc).getTasks();

        assertThat(SelfRefOutputGate.handleInsert(logic, AEItemKey.of(Items.STONE), 5, Actionable.MODULATE))
                .isEqualTo(0L);
    }

    /** SIMULATE 模式:仅报告可接受量,不产生任何副作用. */
    @Test
    void testSimulateHasNoSideEffects() {
        when(jobAcc.getFinalOutput()).thenReturn(item(Items.STONE, 2));
        doReturn(Map.of(selfConsumingPattern(), new Object())).when(jobAcc).getTasks();
        waitingFor.insert(AEItemKey.of(Items.STONE), 5, Actionable.MODULATE);

        assertThat(SelfRefOutputGate.handleInsert(logic, AEItemKey.of(Items.STONE), 10, Actionable.SIMULATE))
                .isEqualTo(5L);
        // 无任何记账变化
        assertThat(waitingFor.extract(AEItemKey.of(Items.STONE), Long.MAX_VALUE, Actionable.SIMULATE)).isEqualTo(5);
        assertThat(inventory.extract(AEItemKey.of(Items.STONE), Long.MAX_VALUE, Actionable.SIMULATE)).isZero();
        verify(cluster, never()).markDirty();
    }

    /** MODULATE 模式:接受量按 min(回流,在途) 截断,产出滞留 CPU 库存而非直接交付. */
    @Test
    void testModulateRetainsOutputInCpuInventory() {
        when(jobAcc.getFinalOutput()).thenReturn(item(Items.STONE, 2));
        doReturn(Map.of(selfConsumingPattern(), new Object())).when(jobAcc).getTasks();
        waitingFor.insert(AEItemKey.of(Items.STONE), 5, Actionable.MODULATE);

        assertThat(SelfRefOutputGate.handleInsert(logic, AEItemKey.of(Items.STONE), 3, Actionable.MODULATE))
                .isEqualTo(3L);
        // 在途核销 3,产出滞留 CPU 库存
        assertThat(waitingFor.extract(AEItemKey.of(Items.STONE), Long.MAX_VALUE, Actionable.SIMULATE)).isEqualTo(2);
        assertThat(inventory.extract(AEItemKey.of(Items.STONE), Long.MAX_VALUE, Actionable.SIMULATE)).isEqualTo(3);
        verify(cluster).markDirty();
        // 任务未推送完毕:不收官
        verify(logicAcc, never()).invokeFinishJob(true);
    }

    /** 非自消耗但 CPU 库存已有滞留(任务集已空) → 仍以滞留判据接管. */
    @Test
    void testRetainedOutputStillGated() {
        when(jobAcc.getFinalOutput()).thenReturn(item(Items.STICK, 4));
        when(jobAcc.getTasks()).thenReturn(Map.of()); // 任务集已空,isSelfConsuming 不可推导
        when(jobAcc.getRemainingAmount()).thenReturn(10L);
        inventory.insert(AEItemKey.of(Items.STICK), 3, Actionable.MODULATE); // 曾门控滞留
        waitingFor.insert(AEItemKey.of(Items.STICK), 5, Actionable.MODULATE);

        assertThat(SelfRefOutputGate.handleInsert(logic, AEItemKey.of(Items.STICK), 10, Actionable.MODULATE))
                .isEqualTo(5L);
    }

    /** 收官:link 可用时从库存一次性交付并完成任务. */
    @Test
    void testSettleDeliversFromInventory() {
        var stick = AEItemKey.of(Items.STICK);
        when(jobAcc.getFinalOutput()).thenReturn(item(Items.STICK, 4));
        when(jobAcc.getTasks()).thenReturn(Map.of());
        when(jobAcc.getRemainingAmount()).thenReturn(13L);
        inventory.insert(stick, 8, Actionable.MODULATE);
        waitingFor.insert(stick, 5, Actionable.MODULATE);
        when(link.insert(eq(stick), eq(1L), eq(Actionable.SIMULATE))).thenReturn(1L);
        when(link.insert(eq(stick), eq(13L), eq(Actionable.MODULATE))).thenReturn(13L);

        assertThat(SelfRefOutputGate.handleInsert(logic, stick, 5, Actionable.MODULATE)).isEqualTo(5L);
        // 本次回流 5 + 原滞留 8 = 13,全部直付 link
        verify(link).insert(stick, 13L, Actionable.MODULATE);
        assertThat(inventory.extract(stick, Long.MAX_VALUE, Actionable.SIMULATE)).isZero();
        verify(jobAcc).setRemainingAmount(0);
        verify(logicAcc).invokeFinishJob(true);
        verify(cluster).updateOutput(null);
    }

    /** 收官:link 完全拒收时不抽取库存,交付留给 storeItems 兜底,仍按完成处理. */
    @Test
    void testSettleWithRefusingLinkKeepsStock() {
        var stick = AEItemKey.of(Items.STICK);
        when(jobAcc.getFinalOutput()).thenReturn(item(Items.STICK, 4));
        when(jobAcc.getTasks()).thenReturn(Map.of());
        when(jobAcc.getRemainingAmount()).thenReturn(10L);
        inventory.insert(stick, 8, Actionable.MODULATE);
        waitingFor.insert(stick, 2, Actionable.MODULATE);
        when(link.insert(any(), anyLong(), any())).thenReturn(0L); // link 恒拒收

        assertThat(SelfRefOutputGate.handleInsert(logic, stick, 2, Actionable.MODULATE)).isEqualTo(2L);
        // 拒收部分留在 CPU 库存(回流 2 + 原滞留 8 = 10),由 finishJob → storeItems 兜底
        assertThat(inventory.extract(stick, Long.MAX_VALUE, Actionable.SIMULATE)).isEqualTo(10);
        verify(jobAcc).setRemainingAmount(0);
        verify(logicAcc).invokeFinishJob(true);
    }

    /** 收官:link 部分拒收时,拒收余量回灌 CPU 库存. */
    @Test
    void testSettlePartialRefusalReinsertsRefused() {
        var stick = AEItemKey.of(Items.STICK);
        when(jobAcc.getFinalOutput()).thenReturn(item(Items.STICK, 4));
        when(jobAcc.getTasks()).thenReturn(Map.of());
        when(jobAcc.getRemainingAmount()).thenReturn(8L);
        // 预滞留 1 个,作为曾被门控的判据(任务集已空时自消耗不再可推导)
        inventory.insert(stick, 1, Actionable.MODULATE);
        waitingFor.insert(stick, 8, Actionable.MODULATE);
        when(link.insert(eq(stick), eq(1L), eq(Actionable.SIMULATE))).thenReturn(1L);
        when(link.insert(eq(stick), eq(8L), eq(Actionable.MODULATE))).thenReturn(5L); // 拒收 3

        assertThat(SelfRefOutputGate.handleInsert(logic, stick, 8, Actionable.MODULATE)).isEqualTo(8L);
        // 库存 1+8=9,直付 8 中 5 成功,拒收 3 回灌:9-8+3=4
        assertThat(inventory.extract(stick, Long.MAX_VALUE, Actionable.SIMULATE)).isEqualTo(4);
        verify(jobAcc).setRemainingAmount(0);
        verify(logicAcc).invokeFinishJob(true);
    }

    /** 待交付量为 0 时不收官(任务已空、无在途但 remainingAmount 已为 0). */
    @Test
    void testNoSettleWhenNothingRemaining() {
        var stick = AEItemKey.of(Items.STICK);
        when(jobAcc.getFinalOutput()).thenReturn(item(Items.STICK, 4));
        when(jobAcc.getTasks()).thenReturn(Map.of());
        when(jobAcc.getRemainingAmount()).thenReturn(0L);
        inventory.insert(stick, 3, Actionable.MODULATE);
        waitingFor.insert(stick, 2, Actionable.MODULATE);

        assertThat(SelfRefOutputGate.handleInsert(logic, stick, 2, Actionable.MODULATE)).isEqualTo(2L);
        verify(logicAcc, never()).invokeFinishJob(true);
    }
}
