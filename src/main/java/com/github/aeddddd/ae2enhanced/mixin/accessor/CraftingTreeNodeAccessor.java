package com.github.aeddddd.ae2enhanced.mixin.accessor;

import java.util.ArrayList;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 访问 {@link CraftingTreeNode} 的内部字段与私有方法，
 * 供递归合成（净产出自引用样板）的计算层接管逻辑使用。
 */
@Mixin(value = CraftingTreeNode.class, remap = false)
public interface CraftingTreeNodeAccessor {

    @Accessor("what")
    AEKey getWhat();

    @Accessor("amount")
    long getAmount();

    @Accessor("parent")
    CraftingTreeProcess getParent();

    @Accessor("job")
    CraftingCalculation getJob();

    @Accessor("nodes")
    ArrayList<CraftingTreeProcess> getNodes();

    @Accessor("canEmit")
    boolean isCanEmit();

    @Invoker("buildChildPatterns")
    void invokeBuildChildPatterns();

    @Invoker("getValidItemTemplates")
    Iterable<InputTemplate> invokeGetValidItemTemplates(ICraftingInventory inv);

    @Invoker("addContainerItems")
    void invokeAddContainerItems(AEKey template, long multiplier, KeyCounter outputList);
}
