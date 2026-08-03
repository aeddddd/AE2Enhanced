package com.github.aeddddd.ae2enhanced.test.assembly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyCraftingProcessor;
import com.github.aeddddd.ae2enhanced.assembly.AssemblyPatternManager;
import com.github.aeddddd.ae2enhanced.assembly.AssemblyUpgradeManager;
import com.github.aeddddd.ae2enhanced.blockentity.AssemblyControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.testutil.AE2KeyTypeTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.ForgeConfigTestBootstrap;

/**
 * {@link AssemblyCraftingProcessor} 单元测试:批量节奏、产物缓冲、批量执行与 NBT 持久化.
 * <p>controller/网络层全部 mock;配置以默认值加载(产物缓冲上限 4096).</p>
 */
class AssemblyCraftingProcessorTest {

    static {
        AE2KeyTypeTestBootstrap.bootstrap();
        ForgeConfigTestBootstrap.bootstrap();
    }

    private static final int MAX_PENDING = 4096; // 配置默认值 assembly.maxPendingOutputs

    private AssemblyControllerBlockEntity controller;
    private AssemblyUpgradeManager upgradeManager;
    private AssemblyCraftingProcessor processor;
    private IActionSource machineSource;

    @BeforeEach
    void setUp() {
        controller = mock(AssemblyControllerBlockEntity.class);
        upgradeManager = new AssemblyUpgradeManager(); // 不接线 → 默认 64 并行 / 20 tick
        processor = new AssemblyCraftingProcessor(controller, upgradeManager,
                mock(AssemblyPatternManager.class));
        machineSource = mock(IActionSource.class);
        when(controller.getActionSource()).thenReturn(machineSource);
    }

    private static AEItemKey stone() {
        return AEItemKey.of(Items.STONE);
    }

    /** 读取 pendingOutputs 的 NBT 快照(私有状态的观察口). */
    private ListTag pendingOutputsNbt() {
        var tag = new CompoundTag();
        processor.save(tag);
        return tag.getList("pendingOutputs", CompoundTag.TAG_COMPOUND);
    }

    // ===== 批量节奏 =====

    /** 初始可批量;置忙后不可;tick 后忙标记清除. */
    @Test
    void testBatchBusyCycle() {
        assertThat(processor.canBatch()).isTrue();

        processor.setBatchBusy(true);
        assertThat(processor.canBatch()).isFalse();

        processor.tickJobTimers();
        assertThat(processor.canBatch()).isTrue();
    }

    /** 批量冷却:重置后按合成周期(默认 20 tick)递减,期间不可批量. */
    @Test
    void testBatchCooldown() {
        processor.resetBatchCooldown();
        assertThat(processor.canBatch()).isFalse();

        for (int i = 0; i < 19; i++) {
            processor.tickJobTimers();
        }
        assertThat(processor.canBatch()).as("冷却未结束").isFalse();

        processor.tickJobTimers();
        assertThat(processor.canBatch()).as("20 tick 后冷却结束").isTrue();
    }

    /** 任务计时器逐 tick 递减,归零移除. */
    @Test
    void testJobTimersTickDown() {
        // 直接经 load 注入任务计时器不可行,改由 pushPatternBatch 成功路径产生
        setupFormedWithNetwork(Long.MAX_VALUE);
        var pattern = simplePattern(new GenericStack(stone(), 2));
        assertThat(processor.pushPattern(pattern, new KeyCounter[] { new KeyCounter() })).isTrue();
        assertThat(processor.getJobCount()).isEqualTo(1);

        for (int i = 0; i < 20; i++) {
            processor.tickJobTimers();
        }
        assertThat(processor.getJobCount()).as("20 tick(默认周期)后任务完成").isZero();
    }

    // ===== 产物缓冲 =====

    /** 空堆/零数量产物被忽略. */
    @Test
    void testAddPendingOutputIgnoresEmpty() {
        processor.addPendingOutput(ItemStack.EMPTY);
        processor.addPendingOutput((ItemStack) null);
        processor.addPendingOutput(new GenericStack(stone(), 0));
        processor.addPendingOutput((GenericStack) null);

        assertThat(pendingOutputsNbt()).isEmpty();
    }

    /** 正常产物进入缓冲并随 save 持久化. */
    @Test
    void testAddPendingOutput() {
        processor.addPendingOutput(new ItemStack(Items.STONE, 5));

        var list = pendingOutputsNbt();
        assertThat(list).hasSize(1);
        var stack = GenericStack.readTag(list.getCompound(0));
        assertThat(stack.what()).isEqualTo(stone());
        assertThat(stack.amount()).isEqualTo(5);
    }

    /** 缓冲上限边界:剩余容量恰好够 → true,超 1 → false. */
    @Test
    void testCanAcceptRealBatchBoundary() {
        assertThat(processor.canAcceptRealBatch(MAX_PENDING)).isTrue();
        assertThat(processor.canAcceptRealBatch(MAX_PENDING + 1)).isFalse();
    }

    /** 缓冲溢出时丢弃多余产物(记 error 日志,不抛异常). */
    @Test
    void testPendingOutputsOverflowDrops() {
        for (int i = 0; i < MAX_PENDING; i++) {
            processor.addPendingOutput(new GenericStack(stone(), 1));
        }
        processor.addPendingOutput(new GenericStack(stone(), 1)); // 溢出,被丢弃

        assertThat(pendingOutputsNbt()).hasSize(MAX_PENDING);
    }

    // ===== 样板批量信息缓存 =====

    /** 无剩余物输入 → 虚拟轨道;有剩余物 → 真实轨道;同一样板命中缓存. */
    @Test
    void testPatternBatchInfoClassification() {
        var virtualPattern = mock(IPatternDetails.class);
        when(virtualPattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);

        var realInput = mock(IPatternDetails.IInput.class);
        when(realInput.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(stone(), 1) });
        when(realInput.getRemainingKey(any())).thenReturn(stone());
        var realPattern = mock(IPatternDetails.class);
        when(realPattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { realInput });

        assertThat(processor.getPatternBatchInfo(virtualPattern).virtual).isTrue();
        assertThat(processor.getPatternBatchInfo(realPattern).virtual).isFalse();

        // 缓存:同一样板返回同一实例
        assertThat(processor.getPatternBatchInfo(realPattern))
                .isSameAs(processor.getPatternBatchInfo(realPattern));
    }

    // ===== 动作来源 =====

    /** 优先使用 mixin 设置的临时来源,否则回退机器源. */
    @Test
    void testEffectiveActionSource() {
        assertThat(processor.getEffectiveActionSource()).isSameAs(machineSource);

        var temp = mock(IActionSource.class);
        processor.setCurrentActionSource(temp);
        assertThat(processor.getEffectiveActionSource()).isSameAs(temp);

        processor.setCurrentActionSource(null);
        assertThat(processor.getEffectiveActionSource()).isSameAs(machineSource);
    }

    // ===== 缓冲注入网络 =====

    /** 无网络节点时产物保留在缓冲中. */
    @Test
    void testInjectPendingOutputsRetainedWithoutNode() {
        when(controller.resolveNode(any())).thenReturn(null);
        processor.addPendingOutput(new GenericStack(stone(), 5));

        processor.tryInjectPendingOutputs();

        assertThat(pendingOutputsNbt()).hasSize(1);
    }

    /** 网络全量接收:同 key 合并为一次插入,缓冲清空. */
    @Test
    void testInjectPendingOutputsFullInsert() {
        var storage = setupFormedWithNetwork(Long.MAX_VALUE);
        processor.addPendingOutput(new GenericStack(stone(), 3));
        processor.addPendingOutput(new GenericStack(stone(), 4));

        processor.tryInjectPendingOutputs();

        verify(storage).insert(stone(), 7L, Actionable.MODULATE, machineSource);
        assertThat(pendingOutputsNbt()).isEmpty();
    }

    /** 网络部分接收:未插入余量退回缓冲. */
    @Test
    void testInjectPendingOutputsPartialInsert() {
        var storage = mock(MEStorage.class);
        setupNetwork(storage);
        // 首次收 5,随后拒收(返回 0):注入循环在拒收时中断,余量退回缓冲
        when(storage.insert(any(), anyLong(), any(), any())).thenReturn(5L, 0L);
        processor.addPendingOutput(new GenericStack(stone(), 10));

        processor.tryInjectPendingOutputs();

        var list = pendingOutputsNbt();
        assertThat(list).hasSize(1);
        assertThat(GenericStack.readTag(list.getCompound(0)).amount()).isEqualTo(5);
    }

    // ===== 批量执行 =====

    /** 前置条件:level 为 null / 客户端 / 未成型 / batchSize <= 0 → false. */
    @Test
    void testPushPatternBatchPreconditions() {
        var pattern = simplePattern(new GenericStack(stone(), 1));
        var inputs = new KeyCounter[] { new KeyCounter() };

        // level 为 null
        assertThat(processor.pushPatternBatch(pattern, inputs, null, 1)).isFalse();

        // 客户端
        var clientLevel = mock(Level.class);
        when(clientLevel.isClientSide()).thenReturn(true);
        when(controller.getLevel()).thenReturn(clientLevel);
        assertThat(processor.pushPatternBatch(pattern, inputs, null, 1)).isFalse();

        // 未成型
        var serverLevel = mock(Level.class);
        when(controller.getLevel()).thenReturn(serverLevel);
        when(controller.isFormed()).thenReturn(false);
        assertThat(processor.pushPatternBatch(pattern, inputs, null, 1)).isFalse();

        // batchSize 非法
        when(controller.isFormed()).thenReturn(true);
        assertThat(processor.pushPatternBatch(pattern, inputs, null, 0)).isFalse();
    }

    /** 忙碌(上一个批量未完成)时拒绝新批量. */
    @Test
    void testPushPatternBatchRejectedWhenBusy() {
        setupFormedWithNetwork(Long.MAX_VALUE);
        processor.setBatchBusy(true);

        var pattern = simplePattern(new GenericStack(stone(), 1));
        assertThat(processor.pushPatternBatch(pattern, new KeyCounter[] { new KeyCounter() }, null, 1))
                .isFalse();
    }

    /** 成功批量:产物按倍率入缓冲,任务计时器启动,busy 置位. */
    @Test
    void testPushPatternBatchSuccess() {
        setupFormedWithNetwork(Long.MAX_VALUE);
        var pattern = simplePattern(new GenericStack(stone(), 2));

        assertThat(processor.pushPatternBatch(pattern, new KeyCounter[] { new KeyCounter() }, null, 3))
                .isTrue();

        var list = pendingOutputsNbt();
        assertThat(list).hasSize(1);
        assertThat(GenericStack.readTag(list.getCompound(0)).amount()).isEqualTo(6); // 2 × 3
        assertThat(processor.getJobCount()).isEqualTo(1);
        assertThat(processor.isBusy()).isTrue();
    }

    /** 催化剂(剩余物与输入一致)立即返还网络,不进入缓冲. */
    @Test
    void testPushPatternBatchCatalystReturnedToNetwork() {
        var storage = setupFormedWithNetwork(Long.MAX_VALUE);

        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(stone(), 1) });
        when(input.getMultiplier()).thenReturn(1L);
        when(input.getRemainingKey(any())).thenAnswer(inv -> inv.getArgument(0)); // 剩余物 = 输入本身
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
        when(pattern.getOutputs()).thenReturn(new GenericStack[0]);

        // inputs 为单副本输入:每次合成投入 1 个催化剂
        var inputs = new KeyCounter[] { new KeyCounter() };
        inputs[0].add(stone(), 1);

        assertThat(processor.pushPatternBatch(pattern, inputs, null, 3)).isTrue();
        // 1 个催化剂/次 × 批量 3 = 3 返还网络
        verify(storage).insert(stone(), 3L, Actionable.MODULATE, machineSource);
        assertThat(pendingOutputsNbt()).isEmpty();
    }

    /** 非催化剂剩余物(容器物)进入缓冲而非直接返还网络. */
    @Test
    void testPushPatternBatchContainerRemainderBuffered() {
        var storage = setupFormedWithNetwork(Long.MAX_VALUE);

        var bucket = AEItemKey.of(Items.BUCKET);
        var input = mock(IPatternDetails.IInput.class);
        when(input.getPossibleInputs()).thenReturn(new GenericStack[] { new GenericStack(stone(), 1) });
        when(input.getMultiplier()).thenReturn(1L);
        when(input.getRemainingKey(any())).thenReturn(bucket); // 剩余物 ≠ 输入
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[] { input });
        when(pattern.getOutputs()).thenReturn(new GenericStack[0]);

        // inputs 为单副本输入:每次合成投入 1 个(产 1 个桶)
        var inputs = new KeyCounter[] { new KeyCounter() };
        inputs[0].add(stone(), 1);

        assertThat(processor.pushPatternBatch(pattern, inputs, null, 3)).isTrue();
        verify(storage, never()).insert(eq(bucket), anyLong(), any(), any());
        var list = pendingOutputsNbt();
        assertThat(list).hasSize(1);
        var remainder = GenericStack.readTag(list.getCompound(0));
        assertThat(remainder.what()).isEqualTo(bucket);
        assertThat(remainder.amount()).isEqualTo(3); // 1 桶/次 × 3
    }

    // ===== NBT 持久化 =====

    /** save/load 往返:产物缓冲与黑洞缓冲内容一致. */
    @Test
    void testSaveLoadRoundTrip() {
        var data = new CompoundTag();
        var outputs = new ListTag();
        outputs.add(GenericStack.writeTag(new GenericStack(stone(), 64)));
        data.put("pendingOutputs", outputs);
        var buffer = new ListTag();
        var entry = new CompoundTag();
        entry.putString("key", "minecraft:stone");
        entry.putInt("count", 7);
        buffer.add(entry);
        data.put("blackHoleBuffer", buffer);

        processor.load(data);

        var out = new CompoundTag();
        processor.save(out);
        var outOutputs = out.getList("pendingOutputs", CompoundTag.TAG_COMPOUND);
        assertThat(outOutputs).hasSize(1);
        assertThat(GenericStack.readTag(outOutputs.getCompound(0)).amount()).isEqualTo(64);
        var outBuffer = out.getList("blackHoleBuffer", CompoundTag.TAG_COMPOUND);
        assertThat(outBuffer).hasSize(1);
        assertThat(outBuffer.getCompound(0).getString("key")).isEqualTo("minecraft:stone");
        assertThat(outBuffer.getCompound(0).getInt("count")).isEqualTo(7);
    }

    /** clearState 清空缓冲/计时器/缓存与节奏状态. */
    @Test
    void testClearState() {
        processor.addPendingOutput(new GenericStack(stone(), 5));
        processor.setBatchBusy(true);
        processor.resetBatchCooldown();

        processor.clearState();

        assertThat(pendingOutputsNbt()).isEmpty();
        assertThat(processor.getJobCount()).isZero();
        assertThat(processor.canBatch()).isTrue();
        assertThat(processor.isBusy()).isFalse();
    }

    // ===== 工装 =====

    /** 构造单输出、无输入的样板. */
    private static IPatternDetails simplePattern(GenericStack output) {
        var pattern = mock(IPatternDetails.class);
        when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[0]);
        when(pattern.getOutputs()).thenReturn(new GenericStack[] { output });
        return pattern;
    }

    /** 装配"服务端已成型 + 网络可用"场景,返回网络存储 mock. */
    private MEStorage setupFormedWithNetwork(long insertResult) {
        var storage = mock(MEStorage.class);
        when(storage.insert(any(), anyLong(), any(), any())).thenAnswer(inv -> {
            if (insertResult == Long.MAX_VALUE) {
                return inv.getArgument(1); // 全额接收
            }
            return insertResult;
        });
        setupNetwork(storage);
        var level = mock(Level.class);
        when(controller.getLevel()).thenReturn(level);
        when(controller.isFormed()).thenReturn(true);
        return storage;
    }

    /** 仅装配网络层(controller.resolveNode → node → grid → storage). */
    private void setupNetwork(MEStorage storage) {
        var node = mock(IManagedGridNode.class);
        var grid = mock(IGrid.class);
        var storageService = mock(IStorageService.class);
        when(controller.resolveNode(any())).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        when(grid.getStorageService()).thenReturn(storageService);
        when(storageService.getInventory()).thenReturn(storage);
    }
}
