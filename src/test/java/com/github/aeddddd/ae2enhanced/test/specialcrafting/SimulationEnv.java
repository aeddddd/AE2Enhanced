package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

import net.minecraft.world.World;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridStorage;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingJob;
import appeng.me.cache.GridStorageCache;
import appeng.me.helpers.PlayerSource;

import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialCraftingJob;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * 1.12.2 版合成模拟环境（对应 1.20.1 的 SimulationEnv）.
 * <p>提供:</p>
 * <ul>
 * <li>匿名 {@link ICraftingGrid}:样板索引 + canEmitFor;</li>
 * <li>Mockito mock 的 {@link IGrid}/{@link GridStorageCache}/{@link IMEMonitor}:
 * 网络库存快照(每次 getStorageList 返回新副本);</li>
 * <li>{@link #runSpecial}/{@link #runNative}:提交 job::run 到单线程执行器后立即
 * simulateFor(模拟 TickHandler 的唤醒),等待完成.</li>
 * </ul>
 */
public class SimulationEnv {

    private final Map<IAEItemStack, List<ICraftingPatternDetails>> patterns = new LinkedHashMap<>();
    private final IItemList<IAEItemStack> networkStorage;
    private final java.util.Set<IAEItemStack> emitables = new java.util.HashSet<>();

    private final ICraftingGrid craftingGrid;
    private final IGrid grid;
    private final World world;
    private final IActionSource actionSource;

    public SimulationEnv() {
        AE2TestBootstrap.boot();
        this.networkStorage = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList();
        this.craftingGrid = this.createCraftingGrid();
        this.grid = this.createGrid();
        this.world = mock(World.class);
        // 玩家源:与合成确认终端的请求形态一致——原生失败路径会为玩家请求
        // 重跑模拟以记账缺失物品(机器源不记账)
        net.minecraft.entity.player.EntityPlayer player = mock(net.minecraft.entity.player.EntityPlayer.class);
        appeng.api.networking.security.IActionHost host = mock(appeng.api.networking.security.IActionHost.class);
        this.actionSource = new PlayerSource(player, host);
    }

    public ICraftingPatternDetails addPattern(ICraftingPatternDetails pattern) {
        this.patterns.computeIfAbsent(RecursiveCraftingHelper.canon(pattern.getPrimaryOutput()),
                k -> new ArrayList<>()).add(pattern);
        return pattern;
    }

    public void addStoredItem(IAEItemStack stack) {
        this.networkStorage.add(stack.copy());
    }

    /** 标记某物可由发射台提供(level emitter). */
    public void addEmitable(IAEItemStack stack) {
        this.emitables.add(RecursiveCraftingHelper.canon(stack));
    }

    public ICraftingGrid craftingGrid() {
        return this.craftingGrid;
    }

    /**
     * 以 {@link SpecialCraftingJob} 运行模拟（路由命中后的实际执行路径）.
     */
    public CraftingJob runSpecial(IAEItemStack what) {
        return this.runJob(this.newSpecialJob(what));
    }

    /**
     * 以原生 {@link CraftingJob} 运行模拟（回归基线）.
     */
    public CraftingJob runNative(IAEItemStack what) {
        return this.runJob(this.newNativeJob(what));
    }

    /**
     * 以 {@link com.github.aeddddd.ae2enhanced.craftingplan.dag.DagCraftingJob} 运行模拟
     * （DAG 计划引擎默认路径）.
     */
    public CraftingJob runDag(IAEItemStack what) {
        return this.runJob(this.newDagJob(what));
    }

    /** 构造原生 {@link CraftingJob}（不执行,供基准测试自行计时）. */
    public CraftingJob newNativeJob(IAEItemStack what) {
        return new CraftingJob(this.world, this.grid, this.actionSource, what, null);
    }

    /** 构造 {@link SpecialCraftingJob}（不执行,供基准测试自行计时）. */
    public CraftingJob newSpecialJob(IAEItemStack what) {
        return new SpecialCraftingJob(this.world, this.grid, this.actionSource, what, null);
    }

    /** 构造 {@link com.github.aeddddd.ae2enhanced.craftingplan.dag.DagCraftingJob}（不执行）. */
    public CraftingJob newDagJob(IAEItemStack what) {
        return new com.github.aeddddd.ae2enhanced.craftingplan.dag.DagCraftingJob(this.world, this.grid,
                this.actionSource, what, null);
    }

    /**
     * 基准测试用:在超时预算内执行 job 并返回;超时则中断 job 线程并返回 null.
     * <p>使用守护线程:超时后被中断仍不退出的残留 job 不会阻止 JVM 退出.</p>
     */
    public CraftingJob runJobTimed(CraftingJob job, long timeout, TimeUnit unit) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ae2e-timed-job");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<?> future = executor.submit(job);
            job.simulateFor(Integer.MAX_VALUE);
            future.get(timeout, unit);
            return job;
        } catch (java.util.concurrent.TimeoutException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    public World world() {
        return this.world;
    }

    private CraftingJob runJob(CraftingJob job) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> future = executor.submit(job);
            // 机器源请求需要 simulateFor 唤醒(模拟 TickHandler 的时间片调度)
            job.simulateFor(Integer.MAX_VALUE);
            future.get(30, TimeUnit.SECONDS);
            return job;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdownNow();
        }
    }

    // ===== mock 基础设施 =====

    private ICraftingGrid createCraftingGrid() {
        return new AnonymousCraftingGrid();
    }

    /**
     * 匿名样板索引网格:同时实现 {@code ICraftingGridCacheAccess}
     * （循环分析的全样板键扫描——副产物边/催化环发现依赖它）.
     */
    private class AnonymousCraftingGrid implements ICraftingGrid,
            com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess {

        @Override
        public java.util.Set<IAEItemStack> ae2enhanced$craftableKeys() {
            return new java.util.HashSet<>(patterns.keySet());
        }

        @Override
        public com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex ae2enhanced$patternIndex() {
            // 测试环境样板集可随时增删,每次重建(规模小,不计成本)
            return com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex.build(this);
        }

        @Override
        public boolean ae2enhanced$hasAssemblyHub() {
            // 测试环境无装配中枢
            return false;
        }

        @Override
        public java.util.List<appeng.api.networking.crafting.ICraftingMedium> ae2enhanced$getMediumsMemo(
                appeng.api.networking.crafting.ICraftingPatternDetails details) {
            // 测试环境无合成介质
            return java.util.Collections.emptyList();
        }

        @Override
        public ImmutableCollection<ICraftingPatternDetails> getCraftingFor(IAEItemStack whatToCraft,
                ICraftingPatternDetails details, int slotIndex, World world) {
            List<ICraftingPatternDetails> list = patterns.get(RecursiveCraftingHelper.canon(whatToCraft));
            return list == null ? ImmutableList.of() : ImmutableList.copyOf(list);
        }

            @Override
            public Future<ICraftingJob> beginCraftingJob(World world, IGrid grid, IActionSource actionSrc,
                    IAEItemStack slotItem, ICraftingCallback cb) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ICraftingLink submitJob(ICraftingJob job, ICraftingRequester requestingMachine,
                    ICraftingCPU target, boolean prioritizePower, IActionSource src) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ImmutableSet<ICraftingCPU> getCpus() {
                return ImmutableSet.of();
            }

            @Override
            public boolean canEmitFor(IAEItemStack what) {
                return emitables.contains(RecursiveCraftingHelper.canon(what));
            }

            @Override
            public boolean isRequesting(IAEItemStack what) {
                return false;
            }

            @Override
            public long requesting(IAEItemStack what) {
                return 0;
            }

            @Override
            public void onUpdateTick() {
            }

            @Override
            public void removeNode(@Nonnull IGridNode gridNode, @Nonnull IGridHost machine) {
            }

            @Override
            public void addNode(@Nonnull IGridNode gridNode, @Nonnull IGridHost machine) {
            }

            @Override
            public void onSplit(@Nonnull IGridStorage destinationStorage) {
            }

            @Override
            public void onJoin(@Nonnull IGridStorage sourceStorage) {
            }

            @Override
            public void populateGridStorage(@Nonnull IGridStorage destinationStorage) {
            }
    }

    @SuppressWarnings("unchecked")
    private IGrid createGrid() {
        IGrid gridMock = mock(IGrid.class);
        GridStorageCache storageMock = mock(GridStorageCache.class);
        IMEMonitor<IAEItemStack> monitorMock = mock(IMEMonitor.class);
        when(gridMock.getCache(ICraftingGrid.class)).thenReturn(this.craftingGrid);
        when(gridMock.getCache(IStorageGrid.class)).thenReturn(storageMock);
        org.mockito.Mockito.doReturn(monitorMock).when(storageMock).getInventory(any());
        // 网络库存快照:每次调用返回新副本(MECraftingInventory 直接包装该列表)
        when(monitorMock.getStorageList()).thenAnswer(invocation -> {
            IItemList<IAEItemStack> snapshot = AEApi.instance().storage()
                    .getStorageChannel(IItemStorageChannel.class).createList();
            for (IAEItemStack stack : this.networkStorage) {
                snapshot.add(stack.copy());
            }
            return snapshot;
        });
        return gridMock;
    }
}
