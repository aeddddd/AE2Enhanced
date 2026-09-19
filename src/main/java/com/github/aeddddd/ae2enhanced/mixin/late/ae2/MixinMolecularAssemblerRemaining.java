package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.container.ContainerNull;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.mixin.bridge.IPatternHelperAccess;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 分子装配室合成收官时按配方实际剩余物(IRecipe.getRemainingItems)替代原生
 * Platform.getContainerItem 处理输入槽.
 * 原生只认 Item.hasContainerItem, CraftTweaker .reuse() 等配方级不消耗实现下物品会被静默销毁,
 * 且留在槽位会让 CPU 下一次合成无法重取该物而卡死; 本 mixin 把剩余物立即推回 CPU 库存,
 * 使下一次合成可正常重取. CPU 侧 waitingFor 预期由 MixinCraftingCPUClusterRemaining 登记, 两端一致.
 * 原版配方剩余物与容器物一致, 行为不变; 非 PatternHelper 实现(如 ae2fc 流体样板)或配方异常时回退原生.
 */
@Mixin(value = TileMolecularAssembler.class, remap = false)
public abstract class MixinMolecularAssemblerRemaining {

    @Shadow
    private ICraftingPatternDetails myPlan;

    @Shadow
    private InventoryCrafting craftingInv;

    @Shadow
    private void pushOut(ItemStack output) {
    }

    /** 本次合成逐槽计算的剩余物表(9 次调用一轮,算一次缓存一轮). */
    @Unique
    private NonNullList<ItemStack> ae2enhanced$remaining;

    @Unique
    private int ae2enhanced$slot;

    @Redirect(method = "tickingRequest", at = @At(value = "INVOKE",
            target = "Lappeng/util/Platform;getContainerItem(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"),
            require = 0)
    private ItemStack ae2enhanced$returnRecipeRemaining(ItemStack stack) {
        ICraftingPatternDetails plan = this.myPlan;
        if (plan == null || !plan.isCraftable() || !(plan instanceof IPatternHelperAccess)) {
            return Platform.getContainerItem(stack);
        }
        try {
            if (this.ae2enhanced$remaining == null) {
                IRecipe recipe = ((IPatternHelperAccess) plan).ae2enhanced$standardRecipe();
                if (recipe == null) {
                    return Platform.getContainerItem(stack);
                }
                // getRemainingItems 会 shrink 传入栏位,必须用拷贝
                InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);
                for (int i = 0; i < this.craftingInv.getSizeInventory() && i < ic.getSizeInventory(); i++) {
                    ItemStack s = this.craftingInv.getStackInSlot(i);
                    ic.setInventorySlotContents(i, s.isEmpty() ? ItemStack.EMPTY : s.copy());
                }
                this.ae2enhanced$remaining = recipe.getRemainingItems(ic);
                this.ae2enhanced$slot = 0;
            }
            int slot = this.ae2enhanced$slot;
            this.ae2enhanced$slot++;
            if (this.ae2enhanced$slot >= this.craftingInv.getSizeInventory()) {
                this.ae2enhanced$remaining = null; // 本轮 9 槽结算完毕,下次合成重算
            }
            ItemStack rem = slot < this.ae2enhanced$remaining.size()
                    ? this.ae2enhanced$remaining.get(slot)
                    : ItemStack.EMPTY;
            if (rem.isEmpty()) {
                return ItemStack.EMPTY;
            }
            // 立即推回(CPU 库存经 waitingFor 接受),推送受阻的剩余由 pushOut 留在输出槽下 tick 重试
            this.pushOut(rem.copy());
            return ItemStack.EMPTY;
        } catch (Throwable t) {
            this.ae2enhanced$remaining = null;
            return Platform.getContainerItem(stack);
        }
    }
}
