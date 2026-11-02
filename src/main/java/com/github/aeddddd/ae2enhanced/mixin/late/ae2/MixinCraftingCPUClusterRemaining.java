package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuAdapter;
import com.github.aeddddd.ae2enhanced.crafting.RemainingItemsService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 容器返还预期注入壳。 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUClusterRemaining {

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean ae2enhanced$capturePushContext(ICraftingMedium medium, ICraftingPatternDetails details,
            InventoryCrafting inventory, Operation<Boolean> original) {
        RemainingItemsService.beginPush(CraftingCpuAdapter.of(this).context(), details, inventory);
        return original.call(medium, details, inventory);
    }

    @Redirect(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/util/Platform;getContainerItem(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack ae2enhanced$expectRecipeRemaining(ItemStack stack) {
        return RemainingItemsService.expected(CraftingCpuAdapter.of(this).context(), stack);
    }
}
