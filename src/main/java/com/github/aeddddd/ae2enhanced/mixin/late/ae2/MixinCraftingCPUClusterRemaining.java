package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.container.ContainerNull;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.github.aeddddd.ae2enhanced.mixin.bridge.IPatternHelperAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

/**
 * CPU 集群的返还物预期(waitingFor)改按<b>配方实际剩余物</b>登记,
 * 与 {@link MixinMolecularAssemblerRemaining} 的执行侧返还保持一致.
 *
 * <p>原生在派发可合成样板时按 {@code Platform.getContainerItem} 向 waitingFor
 * 登记预期返还物;CPU 只接受 waitingFor 内的回收物({@code canAccept}),
 * 预期外的返还物会落入网络存储而非 CPU 库存——CrT 不消耗配方(reuse)下
 * 催化剂永远回不到 CPU,下一次合成提取失败,任务卡死.本 mixin 让预期表
 * 与执行侧使用同一份 {@code getRemainingItems} 结果,回收闭环.</p>
 *
 * <p>实现:WrapOperation 包装 pushPattern 捕获当前 (details, ic) 上下文
 * （与 MixinCraftingCPUClusterVirtualBatch 的 WrapOperation 链式共存),
 * redirect getContainerItem 逐槽返回配方剩余物(原样进入 waitingFor 登记).
 * 原版配方下二者结果一致,行为不变;非 PatternHelper 实现或配方异常时回退原生.</p>
 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUClusterRemaining {

    @Unique
    private ICraftingPatternDetails ae2enhanced$expectDetails;

    @Unique
    private InventoryCrafting ae2enhanced$expectInv;

    /** 本次派发逐槽计算的剩余物预期表(9 次调用一轮,算一次缓存一轮). */
    @Unique
    private NonNullList<ItemStack> ae2enhanced$expectRemaining;

    @Unique
    private int ae2enhanced$expectSlot;

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean ae2enhanced$capturePushContext(ICraftingMedium medium, ICraftingPatternDetails details,
            InventoryCrafting ic, Operation<Boolean> original) {
        this.ae2enhanced$expectDetails = details;
        this.ae2enhanced$expectInv = ic;
        this.ae2enhanced$expectRemaining = null;
        this.ae2enhanced$expectSlot = 0;
        return original.call(medium, details, ic);
    }

    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/util/Platform;getContainerItem(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"),
            require = 0)
    private ItemStack ae2enhanced$expectRecipeRemaining(ItemStack stack) {
        ICraftingPatternDetails details = this.ae2enhanced$expectDetails;
        InventoryCrafting ic = this.ae2enhanced$expectInv;
        if (details == null || ic == null || !(details instanceof IPatternHelperAccess)) {
            return Platform.getContainerItem(stack);
        }
        try {
            if (this.ae2enhanced$expectRemaining == null) {
                IRecipe recipe = ((IPatternHelperAccess) details).ae2enhanced$standardRecipe();
                if (recipe == null) {
                    return Platform.getContainerItem(stack);
                }
                // getRemainingItems 会 shrink 传入栏位,必须用拷贝(ic 已派发给 medium,不得污染)
                InventoryCrafting copy = new InventoryCrafting(new ContainerNull(), 3, 3);
                for (int i = 0; i < ic.getSizeInventory() && i < copy.getSizeInventory(); i++) {
                    ItemStack s = ic.getStackInSlot(i);
                    copy.setInventorySlotContents(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
                }
                this.ae2enhanced$expectRemaining = recipe.getRemainingItems(copy);
                this.ae2enhanced$expectSlot = 0;
            }
            int slot = this.ae2enhanced$expectSlot;
            this.ae2enhanced$expectSlot++;
            if (this.ae2enhanced$expectSlot >= ic.getSizeInventory()) {
                this.ae2enhanced$expectRemaining = null; // 本轮结算完毕,下次派发重算
            }
            ItemStack rem = slot < this.ae2enhanced$expectRemaining.size()
                    ? this.ae2enhanced$expectRemaining.get(slot)
                    : ItemStack.EMPTY;
            return rem.isEmpty() ? ItemStack.EMPTY : rem.copy();
        } catch (Throwable t) {
            this.ae2enhanced$expectRemaining = null;
            return Platform.getContainerItem(stack);
        }
    }
}
