package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.crafting.AggregateCraftingService;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuAdapter;
import com.github.aeddddd.ae2enhanced.crafting.access.IComputationCoreAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ICreativeEnergyAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialCpuAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.inventory.InventoryCrafting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 聚合发配注入壳；执行逻辑见 AggregateCraftingService。 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUClusterAggregate {

    private CraftingCpuAdapter ae2enhanced$aggregateCpu() {
        return CraftingCpuAdapter.of(this);
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void ae2e$ecHead(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        AggregateCraftingService.begin(ae2enhanced$aggregateCpu().context());
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"))
    private void ae2e$ecReturn(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        AggregateCraftingService.end(ae2enhanced$aggregateCpu().context());
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingMedium;pushPattern(Lappeng/api/networking/crafting/ICraftingPatternDetails;Lnet/minecraft/inventory/InventoryCrafting;)Z"))
    private boolean ae2e$aggregateDispatch(ICraftingMedium medium, ICraftingPatternDetails details,
            InventoryCrafting table, Operation<Boolean> original, @Local IEnergyGrid eg) {
        boolean core = ((IComputationCoreAccess) this).ae2enhanced$getComputationCore() != null;
        return AggregateCraftingService.dispatch(ae2enhanced$aggregateCpu(), core, medium, details, table, eg,
                (ICreativeEnergyAccess) this,
                (target, pattern, input) -> original.call(target, pattern, input),
                (target, pattern, input) -> original.call(target, pattern, input),
                (target, pattern, input) -> original.call(target, pattern, input));
    }
}
