/*
 * 移植自 Applied Energistics 2 (15.3.4 / 1.20.1)
 * 源文件:src/test/java/appeng/crafting/simulation/helpers/SimulationEnv.java
 * 仅调整包名,逻辑保持一致,作为原生合成模拟回归基线的测试环境.
 */
package com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.CrashReportCategory;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeService;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridVisitor;
import appeng.api.networking.events.GridEvent;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.AEKeyFilter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.crafting.CraftingCalculation;
import appeng.me.helpers.BaseActionSource;

public class SimulationEnv {
    private final Map<AEKey, List<IPatternDetails>> patterns = new HashMap<>();
    private final KeyCounter craftableItemsList = new KeyCounter();
    private final Set<AEKey> emitableItems = new HashSet<>();
    private final KeyCounter networkStorage = new KeyCounter();

    public IPatternDetails addPattern(IPatternDetails pattern) {
        var output = pattern.getPrimaryOutput();
        patterns.computeIfAbsent(output.what(), s -> new ArrayList<>()).add(pattern);
        craftableItemsList.add(output.what(), 1);
        return pattern;
    }

    public void addEmitable(AEKey stack) {
        emitableItems.add(stack);
    }

    public void addStoredItem(AEKey key, long amount) {
        this.networkStorage.add(key, amount);
    }

    public void addStoredItem(GenericStack stack) {
        this.networkStorage.add(stack.what(), stack.amount());
    }

    public SimulationEnv copy() {
        var copy = new SimulationEnv();
        for (var entry : patterns.entrySet()) {
            for (var pattern : entry.getValue()) {
                copy.addPattern(pattern);
            }
        }
        for (var emitable : emitableItems) {
            copy.addEmitable(emitable);
        }
        for (var stack : networkStorage) {
            copy.addStoredItem(stack.getKey(), stack.getLongValue());
        }
        return copy;
    }

    /** 原生计算墙钟超时(步进喂时间片后仍未完成;计算线程已被中断,大树不会继续生长). */
    public static final class SimulationTimeoutException extends RuntimeException {
        public SimulationTimeoutException(String message) {
            super(message);
        }
    }

    public ICraftingPlan runSimulation(GenericStack what, CalculationStrategy strategy) {
        return runSimulation(what, strategy, 1000);
    }

    /**
     * 带墙钟超时的原生模拟(AE2Enhanced 扩展,大规模基准用):
     * 小步进喂时间片(原生 handlePausing 每 ~101 次调用检查一次预算,暂停后由本线程
     * 继续喂),直到计算完成或超过 timeoutMs;超时经 shutdownNow 中断计算线程
     * (handlePausing 响应中断),防止超大递归树在后台继续生长耗尽堆.
     */
    public ICraftingPlan runSimulation(GenericStack what, CalculationStrategy strategy, long timeoutMs) {
        var calculation = new CraftingCalculation(mock(Level.class), gridMock, simulationRequester, what, strategy);
        var executor = Executors.newSingleThreadExecutor();
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        try {
            var calculationFuture = executor.submit(calculation::run);
            while (true) {
                calculation.simulateFor(100_000); // 100ms 预算/步
                if (calculationFuture.isDone()) {
                    return calculationFuture.get(1000, TimeUnit.MILLISECONDS);
                }
                if (System.nanoTime() >= deadline) {
                    throw new SimulationTimeoutException("原生模拟超过墙钟超时 " + timeoutMs + " ms");
                }
            }
        } catch (SimulationTimeoutException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
            unregisterCraftingSimulation(calculation);
        }
    }

    /**
     * 从 TickHandler 注销已结束的合成计算(AE2Enhanced 扩展,防测试泄漏):
     * 原生仅经 level tick 的 simulateCraftingJobs 清理 craftingJobs,
     * JUnit 环境永无 tick——不注销的话每个计算(连同其递归树,大图下达数 GB)
     * 都永久滞留注册表,跨用例累积撑爆堆.
     */
    private static void unregisterCraftingSimulation(CraftingCalculation calculation) {
        try {
            var handler = appeng.hooks.ticking.TickHandler.instance();
            var field = appeng.hooks.ticking.TickHandler.class.getDeclaredField("craftingJobs");
            field.setAccessible(true);
            var jobs = (com.google.common.collect.Multimap<?, ?>) field.get(handler);
            synchronized (jobs) {
                jobs.values().remove(calculation);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("注销合成模拟失败", e);
        }
    }

    // ===== 以下为 AE2Enhanced 扩展（特殊配方求解器测试用,非 AE2 原内容）=====

    /**
     * 暴露网格的合成服务,供 detector 测试直接调用.
     */
    public ICraftingService craftingService() {
        return gridMock.getCraftingService();
    }

    /** 暴露网格 mock,供按量尝试测试构造原生计算. */
    public IGrid grid() {
        return gridMock;
    }

    /** 暴露模拟请求方,供按量尝试测试构造原生计算. */
    public ICraftingSimulationRequester requester() {
        return simulationRequester;
    }

    /**
     * 以 {@link SpecialCraftingCalculation} 运行模拟（路由命中后的实际执行路径）.
     */
    public ICraftingPlan runSpecialSimulation(GenericStack what, CalculationStrategy strategy) {
        var calculation = new com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingCalculation(
                mock(Level.class), gridMock, simulationRequester, what, strategy);
        try {
            var calculationFuture = Executors.newSingleThreadExecutor().submit(calculation::run);
            calculation.simulateFor(1000000000);
            return calculationFuture.get(1000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 以 DAG 引擎执行按量尝试,策略流程镜像原生 computePlan:
     * 全量尝试 → (CRAFT_LESS:二分搜索最大可产量) → 模拟尝试收缺料;FALLBACK 走原生.
     * 与游戏内 DEFAULT 模式的尝试级 hook 同路径(mixin 在单测中不生效,直接调助手).
     */
    public ICraftingPlan runDagSimulation(GenericStack what, CalculationStrategy strategy) {
        var calculation = new appeng.crafting.CraftingCalculation(
                mock(Level.class), gridMock, simulationRequester, what, strategy);

        var result = attempt(calculation, what, what.amount(), false);
        if (result.outcome() == com.github.aeddddd.ae2enhanced.craftingplan.dag.DagPlanAttempt.Outcome.SUCCESS) {
            return result.plan();
        }
        if (result.outcome() == com.github.aeddddd.ae2enhanced.craftingplan.dag.DagPlanAttempt.Outcome.FALLBACK) {
            return runSimulation(what, strategy);
        }
        // INFEASIBLE:CRAFT_LESS 二分搜索最大可产量(与原生 computePlan 同构)
        if (strategy == CalculationStrategy.CRAFT_LESS) {
            long successfulAmount = 0;
            ICraftingPlan successfulPlan = null;
            for (long increment = Long.highestOneBit(what.amount()); increment > 0; increment /= 2) {
                long testAmount = successfulAmount + increment;
                if (testAmount < what.amount()) {
                    var r = attempt(calculation, what, testAmount, false);
                    if (r.outcome() == com.github.aeddddd.ae2enhanced.craftingplan.dag.DagPlanAttempt.Outcome.SUCCESS) {
                        successfulAmount = testAmount;
                        successfulPlan = r.plan();
                    } else if (r.outcome() == com.github.aeddddd.ae2enhanced.craftingplan.dag.DagPlanAttempt.Outcome.FALLBACK) {
                        return runSimulation(what, strategy);
                    }
                }
            }
            if (successfulPlan != null) {
                return successfulPlan;
            }
        }
        // 模拟尝试收缺料
        result = attempt(calculation, what, what.amount(), true);
        if (result.outcome() == com.github.aeddddd.ae2enhanced.craftingplan.dag.DagPlanAttempt.Outcome.SUCCESS) {
            return result.plan();
        }
        return runSimulation(what, strategy); // FALLBACK:原生完整计算
    }

    private com.github.aeddddd.ae2enhanced.craftingplan.dag.DagPlanAttempt.Result attempt(
            appeng.crafting.CraftingCalculation calculation, GenericStack what, long amount, boolean simulate) {
        return withSimulationFeed(calculation,
                () -> com.github.aeddddd.ae2enhanced.craftingplan.dag.DagPlanAttempt.tryPlan(
                        calculation, craftingService(), what.what(), amount, simulate));
    }

    /**
     * 直接调用按量尝试助手时的双线程时间片契约:
     * {@code simulateFor} 会阻塞到预算被消费或计算 finish,因此由 feeder 线程循环喂时间,
     * 主线程执行计算体,结束后经 {@code finish} 唤醒 feeder 退出.
     */
    public static <T> T withSimulationFeed(appeng.crafting.CraftingCalculation calculation,
            java.util.function.Supplier<T> body) {
        var feeder = new Thread(() -> {
            while (calculation.simulateFor(1000000)) {
                // 循环喂时间直到 finish(done 后 simulateFor 返回 false)
            }
        });
        feeder.setDaemon(true);
        feeder.start();
        try {
            return body.get();
        } finally {
            com.github.aeddddd.ae2enhanced.specialcrafting.Ae2CraftingReflect.finish(calculation);
            try {
                feeder.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private final IGrid gridMock = createGridMock();
    private final IGridNode nodeMock = createNodeMock();
    private final ICraftingSimulationRequester simulationRequester = new ICraftingSimulationRequester() {
        @Override
        public IActionSource getActionSource() {
            return new BaseActionSource();
        }

        @Override
        public IGridNode getGridNode() {
            return nodeMock;
        }
    };

    private IGrid createGridMock() {
        return new StubGrid();
    }

    /**
     * 手写 IGrid 桩(AE2Enhanced 扩展,大规模基准用):Mockito 内联 mock 每次调用
     * 都要捕获栈帧,原生递归树每节点多次网格调用在大图下既拖慢计时又会撑爆堆.
     * 仅合成/仓储服务可用(经 {@link IGrid#getService} 默认方法分发),其余一律不支持.
     */
    private final class StubGrid implements IGrid {
        private final ICraftingService craftingService = createCraftingServiceMock();
        private final IStorageService storageService = createStorageServiceMock();

        @SuppressWarnings("unchecked")
        @Override
        public <C extends IGridService> C getService(Class<C> iface) {
            if (iface == ICraftingService.class) {
                return (C) craftingService;
            }
            if (iface == IStorageService.class) {
                return (C) storageService;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public <T extends GridEvent> T postEvent(T ev) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<Class<?>> getMachineClasses() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<IGridNode> getMachineNodes(Class<?> machineClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Set<T> getMachines(Class<T> machineClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Set<T> getActiveMachines(Class<T> machineClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<IGridNode> getNodes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isEmpty() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IGridNode getPivot() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int size() {
            throw new UnsupportedOperationException();
        }
    }

    private ICraftingService createCraftingServiceMock() {
        return new ICraftingService() {
            @Override
            public ImmutableCollection<IPatternDetails> getCraftingFor(AEKey whatToCraft) {
                var list = patterns.get(whatToCraft);
                if (list == null) {
                    return ImmutableList.of();
                }
                return ImmutableList.copyOf(list);
            }

            @Nullable
            @Override
            public AEKey getFuzzyCraftable(AEKey whatToCraft, AEKeyFilter filter) {
                for (var fuzzy : craftableItemsList.findFuzzy(whatToCraft, FuzzyMode.IGNORE_ALL)) {
                    if (filter.matches(fuzzy.getKey())) {
                        return fuzzy.getKey();
                    }
                }
                return null;
            }

            @Override
            public Future<ICraftingPlan> beginCraftingCalculation(Level level,
                    ICraftingSimulationRequester simRequester, AEKey craftWhat, long amount,
                    CalculationStrategy strat) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ICraftingSubmitResult submitJob(ICraftingPlan job, ICraftingRequester requestingMachine,
                    ICraftingCPU target,
                    boolean prioritizePower, IActionSource src) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ImmutableSet<ICraftingCPU> getCpus() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Set<AEKey> getCraftables(AEKeyFilter filter) {
                return craftableItemsList.keySet().stream().filter(filter::matches).collect(Collectors.toSet());
            }

            @Override
            public boolean canEmitFor(AEKey what) {
                return emitableItems.contains(what);
            }

            @Override
            public boolean isRequesting(AEKey what) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long getRequestedAmount(AEKey what) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean isRequestingAny() {
                return false;
            }

            @Override
            public void refreshNodeCraftingProvider(IGridNode node) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private IStorageService createStorageServiceMock() {
        MEStorage monitor = createMonitorMock();
        return new IStorageService() {
            @Override
            public void addGlobalStorageProvider(IStorageProvider cc) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void removeGlobalStorageProvider(IStorageProvider cc) {
                throw new UnsupportedOperationException();
            }

            @Override
            public MEStorage getInventory() {
                return monitor;
            }

            @Override
            public KeyCounter getCachedInventory() {
                return getInventory().getAvailableStacks();
            }

            @Override
            public void invalidateCache() {
            }

            @Override
            public void refreshNodeStorageProvider(IGridNode node) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void refreshGlobalStorageProvider(IStorageProvider provider) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private MEStorage createMonitorMock() {
        return new MEStorage() {
            @Override
            public void getAvailableStacks(KeyCounter out) {
                for (var entry : networkStorage) {
                    out.add(entry.getKey(), entry.getLongValue());
                }
            }

            @Override
            public Component getDescription() {
                return Component.empty();
            }

            @Override
            public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
                throw new UnsupportedOperationException();
            }

            @Override
            public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
                if (mode == Actionable.SIMULATE) {
                    var stored = networkStorage.get(what);
                    return Math.min(amount, stored);
                } else {
                    throw new UnsupportedOperationException();
                }
            }
        };
    }

    private IGridNode createNodeMock() {
        return new StubGridNode();
    }

    /** 手写 IGridNode 桩:仅 {@link #getGrid} 可用,动机见 {@link StubGrid}. */
    private final class StubGridNode implements IGridNode {
        @Override
        public IGrid getGrid() {
            return gridMock;
        }

        @Override
        public <T extends IGridNodeService> T getService(Class<T> serviceClass) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object getOwner() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void beginVisit(IGridVisitor visitor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServerLevel getLevel() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Set<Direction> getConnectedSides() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<Direction, IGridConnection> getInWorldConnections() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<IGridConnection> getConnections() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasGridBooted() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isPowered() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean meetsChannelRequirements() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean hasFlag(GridFlags flag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getOwningPlayerId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public UUID getOwningPlayerProfileId() {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getIdlePowerUsage() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AEItemKey getVisualRepresentation() {
            throw new UnsupportedOperationException();
        }

        @Override
        public AEColor getGridColor() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void fillCrashReportCategory(CrashReportCategory category) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getMaxChannels() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getUsedChannels() {
            throw new UnsupportedOperationException();
        }
    }
}
