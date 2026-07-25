package com.github.aeddddd.ae2enhanced.mixin;

import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;

import com.github.aeddddd.ae2enhanced.mixin.accessor.CraftingCalculationAccessor;
import com.github.aeddddd.ae2enhanced.util.RecursiveCraftingHelper;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 递归类合成支持（计算层,第一部分）.
 * <p>原生 {@code runCraftAttempt} 无条件对请求物执行 {@code ignore(output)},
 * 将网络库存中的请求物视为 0.对净产出自引用样板（如 A+2B=2A）而言,
 * 库存中的请求物是启动合成链的必要种子,清零后必然报缺料.
 * 此处仅当请求物的唯一候选样板为净产出自引用样板时跳过 ignore,
 * 其余情况保持原生行为不变.</p>
 * <p><b>职责收窄（阶段 3)</b>:根请求的自引用/循环链已由路由层
 * （{@code specialcrafting} 包）以闭式解接管;本 mixin 保留用于
 * <b>中间节点</b>场景（请求物的间接输入含唯一候选自引用样板,
 * 如"锻造装备间接需要复制模板")及路由求解器的原生回落路径,
 * 在深层支持落地前不予删除.</p>
 */
@Mixin(value = CraftingCalculation.class, remap = false)
public class MixinCraftingCalculation {

    @Redirect(method = "runCraftAttempt",
            at = @At(value = "INVOKE",
                    target = "Lappeng/crafting/inv/ChildCraftingSimulationState;ignore(Lappeng/api/stacks/AEKey;)V"),
            remap = false)
    private void ae2e$ignoreUnlessOnlySelfRef(ChildCraftingSimulationState craftingInventory, AEKey key) {
        CraftingCalculation self = (CraftingCalculation) (Object) this;
        var gridNode = ((CraftingCalculationAccessor) self).getSimRequester().getGridNode();
        if (gridNode != null
                && RecursiveCraftingHelper.isOnlyCandidateSelfRef(gridNode.getGrid().getCraftingService(), key)) {
            return;
        }
        craftingInventory.ignore(key);
    }
}
