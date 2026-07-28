package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.hooks.ticking.TickHandler;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.specialcrafting.Ae2CraftingReflect;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialLog;

/**
 * DAG 合成计算器(阶段 4):以"编译 DAG + 拓扑单趟扫描"取代原生递归树.
 * <p>继承原生 {@link CraftingCalculation} 复用其时间片调度/暂停/线程池骨架;
 * DAG 路径任何回落信号或异常都退回原生 {@code computePlan}(宁可慢不可错).</p>
 * <p>v1 范围:仅 {@link CalculationStrategy#REPORT_MISSING_ITEMS}(CRAFT_LESS 走原生);
 * 环/替代输入/容器物 → 整单回落(深层循环边界委托为后续阶段).</p>
 */
public class DagCraftingCalculation extends CraftingCalculation {

    private final Level level;
    private final appeng.api.networking.crafting.ICraftingService craftingService;
    private final GenericStack outputStack;
    private final CalculationStrategy strategy;

    public DagCraftingCalculation(Level level, IGrid grid, ICraftingSimulationRequester simRequester,
            GenericStack output, CalculationStrategy strategy) {
        super(level, grid, simRequester, output, strategy);
        this.level = level;
        this.craftingService = grid.getCraftingService();
        this.outputStack = output;
        this.strategy = strategy;
    }

    @Override
    public ICraftingPlan run() {
        try {
            TickHandler.instance().registerCraftingSimulation(this.level, this);
            Ae2CraftingReflect.handlePausing(this);

            ICraftingPlan plan = computeDagPlan();
            if (plan == null) {
                plan = Ae2CraftingReflect.computePlan(this);
            }
            return plan;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("DAG 计划异常,回落原生计算: {}", e.toString());
            try {
                return Ae2CraftingReflect.computePlan(this);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        } finally {
            Ae2CraftingReflect.finish(this);
        }
    }

    /**
     * @return DAG 计划;任何不适用情形返回 null(调用方回落原生).
     */
    @Nullable
    private ICraftingPlan computeDagPlan() throws InterruptedException {
        if (this.strategy != CalculationStrategy.REPORT_MISSING_ITEMS) {
            return null; // CRAFT_LESS 等策略 v1 不接管
        }
        DagGraph graph;
        try {
            graph = DagCompiler.compile(craftingService, getOutput());
        } catch (DagFallback fallback) {
            SpecialLog.info("[DAG] 编译回落({}): {}", fallback.reason, getOutput());
            return null;
        }

        var networkInv = Ae2CraftingReflect.getNetworkInv(this);
        var inv = new ChildCraftingSimulationState(networkInv);
        inv.ignore(getOutput()); // 镜像原生:请求物自身库存不参与计划扣除
        try {
            DagExecutor.execute(graph, outputStack.amount(), inv, this, craftingService);
        } catch (DagFallback fallback) {
            SpecialLog.info("[DAG] 执行回落({}): {}", fallback.reason, getOutput());
            return null;
        }
        return CraftingSimulationState.buildCraftingPlan(inv, this, outputStack.amount());
    }
}
