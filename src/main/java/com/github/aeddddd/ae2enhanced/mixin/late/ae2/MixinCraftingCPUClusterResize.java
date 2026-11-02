package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.github.aeddddd.ae2enhanced.crafting.CraftingBufferResizeService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 合成表扩容注入壳；priority 1100 保持 ae2fc 包装在内层。 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1100)
public class MixinCraftingCPUClusterResize {

    @WrapOperation(method = "executeCrafting", at = @At(value = "NEW",
            target = "net/minecraft/inventory/InventoryCrafting", remap = true))
    private InventoryCrafting ae2enhanced$resizeCraftingBuffer(Container container, int width, int height,
            Operation<InventoryCrafting> original, @Local ICraftingPatternDetails details) {
        InventoryCrafting table = original.call(container, width, height);
        return CraftingBufferResizeService.resize(table, container, details);
    }
}
