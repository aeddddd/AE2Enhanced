package com.github.aeddddd.ae2enhanced.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.assembly.AssemblyHubBatchCrafting;
import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCpuLogicAccessor;
import com.github.aeddddd.ae2enhanced.mixin.accessor.ExecutingCraftingJobAccessor;
import com.github.aeddddd.ae2enhanced.specialcrafting.RoundQuotaScheduler;
import com.github.aeddddd.ae2enhanced.specialcrafting.SelfRefOutputGate;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 {@link CraftingCpuLogic#executeCrafting} 头部注入装配枢纽的批量合成处理,
 * 复刻主分支 1.12 对装配枢纽任务的虚拟/真实轨道分批处理.
 * <p>具体批量逻辑见 {@link AssemblyHubBatchCrafting},此处仅做 AE2 原版 CPU 的字段适配.</p>
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public class MixinCraftingCpuLogic {

    @Inject(method = "executeCrafting", at = @At("HEAD"), remap = false)
    private void ae2e$batchProcessAssemblyHubTasks(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> cir) {
        CraftingCpuLogic logic = (CraftingCpuLogic) (Object) this;
        CraftingCpuLogicAccessor logicAccessor = (CraftingCpuLogicAccessor) logic;
        CraftingCPUCluster cluster = logicAccessor.getCluster();
        ExecutingCraftingJob job = logicAccessor.getJob();
        if (job == null) {
            return;
        }
        ExecutingCraftingJobAccessor jobAccessor = (ExecutingCraftingJobAccessor) job;
        AssemblyHubBatchCrafting.processHubBatches(jobAccessor.getTasks(), AssemblyHubBatchCrafting.AE2_TASK_ACCESS,
                logic.getInventory(), jobAccessor.getWaitingFor(),
                AssemblyHubBatchCrafting.ae2TimeTracker(jobAccessor.getTimeTracker()),
                jobAccessor.getFinalOutput(), cluster.getSrc(), cluster::markDirty, craftingService, level, null);
    }

    /**
     * 自消耗 job（自引用/循环链计划,最终产出仍是任务输入）的交付门控:
     * 最终产出先入 CPU 库存,全部任务收官后一次性交付,防止边产边交付饿死合成链.
     * 普通计划判定为 false 时零影响;{@code require = 0} 防第三方改写该方法时崩溃.
     */
    @Inject(method = "insert", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void ae2e$gateSelfConsumingOutput(AEKey what, long amount, Actionable type,
            CallbackInfoReturnable<Long> cir) {
        Long result = SelfRefOutputGate.handleInsert((CraftingCpuLogic) (Object) this, what, amount, type);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }

    /**
     * 任务提交成功时快照 patternTimes,供超轮配额调度器恢复轮次.
     */
    @Inject(method = "trySubmitJob", at = @At("RETURN"), require = 0, remap = false)
    private void ae2e$snapshotJobTotals(IGrid grid, ICraftingPlan plan, IActionSource src,
            ICraftingRequester requester, CallbackInfoReturnable<ICraftingSubmitResult> cir) {
        if (cir.getReturnValue() != null && cir.getReturnValue().successful()) {
            var job = ((CraftingCpuLogicAccessor) this).getJob();
            if (job != null) {
                RoundQuotaScheduler.snapshot(job, plan);
            }
        }
    }

    /**
     * 超轮配额调度:逐次推送否决——超配额的闭包 pattern 令其输入提取返回 null,
     * 原生视同"输入不足"自然跳过,下一拍配额前进后自动恢复.
     * 仅对我们的虚拟 CPU 上的自消耗 job 生效;两个调用点(首次提取 + 续推重备)全部拦截.
     */
    @Redirect(method = "executeCrafting",
            at = @At(value = "INVOKE",
                    target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/api/networking/crafting/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;",
                    remap = false),
            require = 0, remap = false)
    private KeyCounter[] ae2e$vetoPushOverQuota(IPatternDetails details, ICraftingInventory sourceInv, Level level,
            KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
        if (RoundQuotaScheduler.shouldVetoPush((CraftingCpuLogic) (Object) this, details)) {
            return null; // 超配额:视同输入不足,本拍跳过该 pattern
        }
        return CraftingCpuHelper.extractPatternInputs(details, sourceInv, level, expectedOutputs,
                expectedContainerItems);
    }
}
