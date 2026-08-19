package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.Map;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.MECraftingInventory;

/**
 * CrT 不消耗配方（配方级返还，如 {@code .reuse()} 催化剂）在特殊求解路径的记账助手.
 * <p>背景：特殊路径（自引用/循环链闭式解）以原生 {@code CraftingTreeProcess.request}
 * 整批模拟，而原生只认 {@code Item.hasContainerItem} 的容器返还——CrT 配方级
 * {@code getRemainingItems} 的返还物被全额提取，库存不足即 CraftBranchFailure
 * 误报缺料（运行时执行层实际逐次返还，见 MixinMolecularAssemblerRemaining /
 * MixinCraftingCPUClusterRemaining）.</p>
 * <p>记账法（与自引用种子的贷款/返利语义一致）:模拟前按 (次数-1)×每次返还 预注入
 * "虚拟返还"（<b>不归还</b>——它代表运行时逐次返还中除末次外的部分），使 gross 提取
 * 水位恰好对齐运行时可行性（库存 ≥ 净消耗+单次投入）;成功后由调用方把该键 used
 * 返利为 净消耗+单次投入（gross − 虚拟返还），保证模拟库存余量与网络实取一致.</p>
 * <p>保守边界:
 * <ul>
 * <li>替代品样板（canSubstitute）不处理——模糊/替代品提取的返利条目无法精确对账，
 * 回退原生（维持既有行为）;</li>
 * <li>返还超过投入的键按投入封顶（计划侧不产生净增益，运行时多出的返还属红利）;</li>
 * <li>返还键不是本样板输入（如奶桶→空桶）时不处理——原生容器路径已覆盖，
 * 且非输入返还属副产物，注入会助长幻影库存;</li>
 * <li>已有贷款语义的键（自引用键/环键/交付键）跳过，由既有贷款+返利覆盖.</li>
 * </ul></p>
 */
final class CatalystReturns {

    private CatalystReturns() {
    }

    /**
     * 汇总某样板整批 crafts 次的催化剂记账.
     *
     * @param excluded 已有贷款/返利覆盖的规范化键（自引用键/环键/交付键）,跳过
     * @param injectOut 输出：模拟前预注入量（不归还）
     * @param rebateOut 输出：used 返利目标（净消耗 + 单次投入）
     */
    static void collect(ICraftingPatternDetails pattern, long crafts, Set<IAEItemStack> excluded,
            Map<IAEItemStack, Long> injectOut, Map<IAEItemStack, Long> rebateOut) {
        // crafts==1 无虚拟返还（单次即末次,gross 即种子）;替代品样板见类注释
        if (crafts <= 1 || pattern.canSubstitute()) {
            return;
        }
        Map<IAEItemStack, Long> table = RecipeRemainingResolver.remainingPerCraft(pattern);
        if (table == null || table.isEmpty()) {
            return;
        }
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            IAEItemStack key = RecursiveCraftingHelper.canon(input);
            if (excluded.contains(key)) {
                continue;
            }
            Long remaining = table.get(key);
            if (remaining == null || remaining <= 0) {
                continue;
            }
            long in = input.getStackSize();
            long r = Math.min(remaining, in); // 返还超投入按投入封顶
            if (crafts > Long.MAX_VALUE / in || crafts - 1 > Long.MAX_VALUE / r) {
                continue; // 溢出 → 保守跳过（该键维持原生全消耗记账）
            }
            long inject = (crafts - 1) * r;
            injectOut.merge(key, inject, Long::sum);
            rebateOut.merge(key, crafts * in - inject, Long::sum);
        }
    }

    /**
     * 预注入虚拟返还（不归还）.失败路径无需回滚：根请求路径模拟库存随求解器
     * 丢弃;DAG 边界路径 FALLBACK 即整单回落（DagFallback)，共享库存一并废弃.
     */
    static void inject(Map<IAEItemStack, Long> inject, MECraftingInventory inv, IActionSource src) {
        for (Map.Entry<IAEItemStack, Long> entry : inject.entrySet()) {
            IAEItemStack stack = entry.getKey().copy();
            stack.setStackSize(entry.getValue());
            inv.injectItems(stack, Actionable.MODULATE, src);
        }
    }
}
