package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuAdapter;
import com.github.aeddddd.ae2enhanced.crafting.PushCooldownService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** 推送失败冷却注入壳。 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUClusterPushCooldown {

    @Shadow
    private World getWorld() {
        return null;
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;isBusy()Z"))
    private boolean ae2e$treatCooledMediumAsBusy(ICraftingMedium medium, Operation<Boolean> original) {
        return PushCooldownService.isBusy(CraftingCpuAdapter.of(this).context(), medium, getWorld(),
                () -> original.call(medium));
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean ae2e$recordPushFailure(ICraftingMedium medium, ICraftingPatternDetails details,
            InventoryCrafting table, Operation<Boolean> original) {
        boolean result = original.call(medium, details, table);
        return PushCooldownService.pushed(CraftingCpuAdapter.of(this).context(), medium, getWorld(), result);
    }
}
