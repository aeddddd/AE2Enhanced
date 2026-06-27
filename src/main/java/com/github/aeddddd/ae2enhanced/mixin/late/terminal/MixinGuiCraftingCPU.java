package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.implementations.GuiCraftingCPU;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;

/**
 * 修改 Crafting CPU 状态界面中物品列表的排序：
 * 1. 合成中(active > 0)
 * 2. 计划合成(pending > 0)
 * 3. 现存(storage only)
 */
@Mixin(value = GuiCraftingCPU.class, remap = false)
public class MixinGuiCraftingCPU {

    @Shadow
    private List<IAEItemStack> visual;

    @Shadow
    private IItemList<IAEItemStack> active;

    @Shadow
    private IItemList<IAEItemStack> pending;

    @Inject(
        method = "postUpdate",
        at = @At(value = "INVOKE", target = "Lappeng/client/gui/implementations/GuiCraftingCPU;setScrollBar()V")
    )
    private void ae2enhanced$sortVisualByCraftingStatus(List<IAEItemStack> list, byte ref, CallbackInfo ci) {
        if (this.visual == null || this.visual.size() <= 1) {
            return;
        }

        this.visual.sort(Comparator.comparingInt(this::getStatusPriority)
                .thenComparing(this::getItemDisplayName));
    }

    private int getStatusPriority(IAEItemStack stack) {
        IAEItemStack activeStack = this.active.findPrecise(stack);
        if (activeStack != null && activeStack.getStackSize() > 0) {
            return 0; // 合成中 — 最高优先级
        }
        IAEItemStack pendingStack = this.pending.findPrecise(stack);
        if (pendingStack != null && pendingStack.getStackSize() > 0) {
            return 1; // 计划合成
        }
        return 2; // 现存 — 最低优先级
    }

    private String getItemDisplayName(IAEItemStack stack) {
        ItemStack itemStack = stack.createItemStack();
        return itemStack.getDisplayName();
    }
}
