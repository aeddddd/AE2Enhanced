package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuAdapter;
import com.github.aeddddd.ae2enhanced.crafting.CreativeEnergyService;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ICreativeEnergyAccess;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** 创造能源注入壳。 */
@Mixin(value = CraftingCPUCluster.class, remap = false)
public abstract class MixinCraftingCPUClusterCreativeEnergy implements ICreativeEnergyAccess {

    @Shadow
    private World getWorld() {
        return null;
    }

    @Shadow
    private IGrid getGrid() {
        return null;
    }

    @Override
    public boolean ae2e$hasCreativeEnergy(IEnergyGrid energy) {
        return CreativeEnergyService.hasCreativeEnergy(CraftingCpuAdapter.of(this).context(), getWorld(), getGrid());
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/energy/IEnergyGrid;extractAEPower(DLappeng/api/config/Actionable;Lappeng/api/config/PowerMultiplier;)D"))
    private double ae2e$skipEnergyChargeWhenCreative(IEnergyGrid energy, double amount, Actionable mode,
            PowerMultiplier multiplier, Operation<Double> original) {
        return CreativeEnergyService.extract(CraftingCpuAdapter.of(this).context(), getWorld(), getGrid(), amount,
                () -> original.call(energy, amount, mode, multiplier));
    }
}
