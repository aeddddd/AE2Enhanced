package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.client.gui.GuiDisplayWall;
import com.github.aeddddd.ae2enhanced.client.gui.jei.GhostIngredientTarget;
import mezz.jei.api.gui.IGhostIngredientHandler;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 趋势幕墙配置 GUI 的 JEI Ghost Ingredient Handler.
 * 支持将 JEI 中的物品/流体拖放到 8 个监控槽位(流体自动转换为 FluidDrop 假物品).
 */
public class DisplayWallGhostHandler implements IGhostIngredientHandler<GuiDisplayWall> {

    @Nonnull
    @Override
    public <I> List<Target<I>> getTargets(@Nonnull GuiDisplayWall gui, @Nonnull I ingredient, boolean doStart) {
        // 仅接受物品与流体, 幕墙只采样这两种; 气体/源质无采样逻辑, 不放行
        if (!(ingredient instanceof ItemStack) && !(ingredient instanceof FluidStack)) {
            return Collections.emptyList();
        }
        List<Target<I>> targets = new ArrayList<>();
        for (Slot slot : gui.inventorySlots.inventorySlots) {
            if (slot instanceof appeng.container.slot.SlotFake) {
                @SuppressWarnings("unchecked")
                Target<I> target = (Target<I>) new GhostIngredientTarget(
                        gui.getGuiLeft(), gui.getGuiTop(), slot);
                targets.add(target);
            }
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }
}
