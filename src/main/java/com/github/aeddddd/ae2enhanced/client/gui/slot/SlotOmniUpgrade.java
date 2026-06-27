package com.github.aeddddd.ae2enhanced.client.gui.slot;

import appeng.api.implementations.items.IUpgradeModule;
import appeng.container.slot.AppEngSlot;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import com.github.aeddddd.ae2enhanced.item.ItemOmniUpgradeCard;
import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * Omni Terminal 右侧升级卡槽位 —— 支持 AE2 标准升级卡、频道接收卡和 Omni 专用升级卡.
 * 每张升级卡只能堆叠 1 个.
 */
public class SlotOmniUpgrade extends AppEngSlot {

    public SlotOmniUpgrade(IItemHandler inv, int idx, int x, int y) {
        super(inv, idx, x, y);
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return stack.getItem() instanceof IUpgradeModule
                || stack.getItem() instanceof ItemChannelReceiverCard
                || stack.getItem() instanceof ItemOmniUpgradeCard;
    }

    @Override
    public int func_178170_b(@Nonnull ItemStack stack) {
        return 1;
    }
}
