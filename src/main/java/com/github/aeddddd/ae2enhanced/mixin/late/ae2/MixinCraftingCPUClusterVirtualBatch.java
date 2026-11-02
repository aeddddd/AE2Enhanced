package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuAdapter;
import com.github.aeddddd.ae2enhanced.crafting.VirtualBatchCraftingService;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.inventory.InventoryCrafting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Central 虚拟批量注入壳；四段协议与 CPU 记账见 {@link VirtualBatchCraftingService}。 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public abstract class MixinCraftingCPUClusterVirtualBatch {

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean ae2enhanced$wrapPushPatternForVirtualBatch(ICraftingMedium medium,
            ICraftingPatternDetails details, InventoryCrafting table, Operation<Boolean> original) {
        return VirtualBatchCraftingService.dispatch(CraftingCpuAdapter.of(this),
                medium instanceof TileCentralMEInterface
                        ? ((TileCentralMEInterface) medium).getInterfaceDuality() : null,
                details, () -> original.call(medium, details, table));
    }
}
