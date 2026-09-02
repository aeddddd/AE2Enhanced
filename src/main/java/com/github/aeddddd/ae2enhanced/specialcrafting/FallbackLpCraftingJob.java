package com.github.aeddddd.ae2enhanced.specialcrafting;

import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCallback;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.MECraftingInventory;
import appeng.hooks.TickHandler;

/**
 * FALLBACK 模式合成计算器：原生先算,得出缺料（模拟）计划时 LP 重算,
 * 更优（非模拟）则采用,否则保留原生缺料报告.
 * <p>复制原生 {@code CraftingJob.run()} 骨架（去 finish）以实现"先原生后决策":
 * 原生成功 → 直接 dive 收尾;原生失败 → LP 重算;LP 也失败 →
 * 按原生失败路径产出缺料计划（玩家请求时模拟模式重跑收集 missing）.</p>
 */
public class FallbackLpCraftingJob extends LpCraftingJob {

    public FallbackLpCraftingJob(World w, IGrid grid, IActionSource actionSrc, IAEItemStack what,
            ICraftingCallback callback) {
        super(w, grid, actionSrc, what, callback);
    }

    @Override
    public void run() {
        long planStart = System.nanoTime();
        // 标记是否经 super.run() 回落原生——该路径已由 MixinCraftingJob 的 run RETURN 钩子计数,避免重复
        boolean delegatedToNative = false;
        IActionSource src = Ae2CraftingReflect.getActionSrc(this);
        try {
            TickHandler.INSTANCE.registerCraftingSimulation(this.world, this);
            Ae2CraftingReflect.handlePausing(this);
            Ae2CraftingReflect.setAvailableCheck(this,
                    new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), false, false, false));

            boolean nativeOk = false;
            // 原生首趟挂计算预算:病态计划的原生递归可能永不结束(见 NativeCalcBudget);
            // 超预算中断会钉模拟态并经 InterruptedException 走下方取消分支
            NativeCalcBudget.arm(this);
            try {
                MECraftingInventory inv = new MECraftingInventory(Ae2CraftingReflect.getOriginal(this),
                        true, false, true);
                Ae2CraftingReflect.invIgnore(inv, this.getOutput());
                Ae2CraftingReflect.nodeRequest(this.getTree(), inv, this.getOutput().getStackSize(), src);
                nativeOk = true;
            } catch (CraftBranchFailure failure) {
                SpecialLog.info("[LP计划] FALLBACK:原生缺料({}),LP 重算", failure.toString());
            }

            if (nativeOk) {
                Ae2CraftingReflect.nodeDive(this.getTree(), this);
            } else {
                CraftingTreeNode lpRoot = null;
                try {
                    lpRoot = this.computeLpPlan();
                } catch (Throwable t) {
                    SpecialLog.warn("[LP计划] FALLBACK:LP 重算异常({}),保留原生缺料报告", t.toString());
                }
                if (lpRoot != null && !this.isSimulation()) {
                    // 仅当 LP 真正解出(非模拟)才替换
                    Ae2CraftingReflect.setTree(this, lpRoot);
                    Ae2CraftingReflect.nodeDive(lpRoot, this);
                    if (this.hasCycleBoundary) {
                        SpecialPlanMarker.mark(this);
                    }
                } else {
                    // 保留原生缺料报告(与原生失败路径一致,仅玩家请求重跑模拟)
                    Ae2CraftingReflect.setSimulate(this, true);
                    if (src.player().isPresent()) {
                        MECraftingInventory inv2 = new MECraftingInventory(
                                Ae2CraftingReflect.getOriginal(this), true, false, true);
                        Ae2CraftingReflect.invIgnore(inv2, this.getOutput());
                        Ae2CraftingReflect.treeSetSimulate(this.getTree());
                        Ae2CraftingReflect.setAvailableCheck(this,
                                new MECraftingInventory(Ae2CraftingReflect.getOriginal(this), false, false,
                                        false));
                        try {
                            Ae2CraftingReflect.nodeRequest(this.getTree(), inv2,
                                    this.getOutput().getStackSize(), src);
                        } catch (CraftBranchFailure | appeng.crafting.CraftingCalculationFailure ignored) {
                            // 与原生一致:忽略,保留已记账的 missing
                        }
                        Ae2CraftingReflect.nodeDive(this.getTree(), this);
                    }
                }
            }
            SpecialPlanDisplayHook.sendPlanInfo(this);
            Ae2CraftingReflect.finish(this);
        } catch (InterruptedException e) {
            SpecialLog.info("[LP计划] FALLBACK:计算被取消");
            NativeCalcBudget.warnIfAborted(this);
            Ae2CraftingReflect.finish(this);
        } catch (Throwable t) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER
                    .warn("LP FALLBACK 异常,回落原生计算: {}", t.toString());
            Ae2CraftingReflect.setAvailableCheck(this, null);
            NativeCalcBudget.arm(this);
            delegatedToNative = true;
            super.run();
            NativeCalcBudget.warnIfAborted(this);
        } finally {
            if (!delegatedToNative) {
                com.github.aeddddd.ae2enhanced.diag.plan.PlanTracker.onJobComputed(this,
                        System.nanoTime() - planStart);
            }
        }
    }
}
