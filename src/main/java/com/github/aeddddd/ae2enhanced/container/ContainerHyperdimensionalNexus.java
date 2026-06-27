package com.github.aeddddd.ae2enhanced.container;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;

/**
 * 超维度仓储中枢信息面板的 Container.
 * 包含玩家背包和快捷栏,与 2.png 纹理布局匹配.
 */
public class ContainerHyperdimensionalNexus extends Container {

    private static final int INV_X = 8;
    private static final int INV_Y = 108;
    private static final int HOTBAR_Y = 166;

    public ContainerHyperdimensionalNexus(IInventory playerInv) {
        // 玩家背包 3行×9列
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlotToContainer(new Slot(playerInv, col + row * 9 + 9,
                    INV_X + col * 18, INV_Y + row * 18));
            }
        }

        // 玩家快捷栏
        for (int col = 0; col < 9; ++col) {
            this.addSlotToContainer(new Slot(playerInv, col,
                INV_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        return true;
    }
}
