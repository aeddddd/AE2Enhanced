package com.github.aeddddd.ae2enhanced.mixin.late.world;

import com.github.aeddddd.ae2enhanced.collector.CollectorRegistry;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截容器破坏时掉落物品的总入口 InventoryHelper.dropInventoryItems.
 *
 * 覆盖箱子、漏斗、熔炉、投掷器、发射器等所有 IInventory 容器.
 * 在循环创建 EntityItem 之前,直接把所有物品交给先进 ME 收集器处理.
 */
@Mixin(value = net.minecraft.inventory.InventoryHelper.class, remap = true)
public class MixinInventoryHelperDropItems {

    @Inject(method = "dropInventoryItems", at = @At("HEAD"), cancellable = true)
    private static void ae2e$onDropInventoryItems(World worldIn, BlockPos pos, IInventory inventory, CallbackInfo ci) {
        if (worldIn == null || worldIn.isRemote || inventory == null) return;
        if (!CollectorRegistry.hasCollectors(worldIn)) return;

        TileAdvancedMECollector collector = CollectorRegistry.findBestCollector(worldIn, pos);
        if (collector == null) return;

        boolean allHandled = true;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            ItemStack remaining = collector.tryCollectStackForced(stack);
            if (remaining.isEmpty()) {
                inventory.setInventorySlotContents(i, ItemStack.EMPTY);
            } else {
                inventory.setInventorySlotContents(i, remaining);
                allHandled = false;
            }
        }

        if (allHandled) {
            ci.cancel();
        }
    }
}
