package com.github.aeddddd.ae2enhanced.integration.terminal.tii.starlight;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemStarlightDrop;
import com.github.aeddddd.ae2enhanced.storage.starlight.AEStarlightStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import nyonio.terminal_interaction_integration.api.IPacketType;

/**
 * TII Astral Sorcery Starlight 数据包类型.
 * <p>
 * 使用 AE2E 的 {@link ItemStarlightDrop} 作为数据包物品.
 * </p>
 */
public class StarlightPacketType implements IPacketType {

    private static final String NAME = "ae2enhanced:starlight";
    private static final String DISPLAY_NAME = TextFormatting.BLUE + "Starlight";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public boolean isPacket(ItemStack stack) {
        return stack != null && !stack.isEmpty() && ItemStarlightDrop.isStarlightDrop(stack);
    }

    @Override
    public long getAmount(ItemStack stack) {
        if (!isPacket(stack)) {
            return 0;
        }
        return ItemStarlightDrop.getAmount(stack);
    }

    @Override
    public IAEItemStack createAEStack(long amount) {
        ItemStack template = ItemStarlightDrop.createStack(amount);
        IAEItemStack ae = AEItemStack.fromItemStack(template);
        if (ae != null) {
            ae.setStackSize(amount);
        }
        return ae;
    }

    @Override
    public ItemStack createItemStack(long amount) {
        return ItemStarlightDrop.createStack(amount);
    }

    @Override
    public IAEStack<?> createResourceStack(long amount) {
        return AEStarlightStack.create(amount);
    }
}
