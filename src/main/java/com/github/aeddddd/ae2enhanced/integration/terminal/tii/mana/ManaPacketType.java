package com.github.aeddddd.ae2enhanced.integration.terminal.tii.mana;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.integration.botaniaapplie.BotaniaApplieCompat;
import com.github.aeddddd.ae2enhanced.item.ItemManaDrop;
import com.github.aeddddd.ae2enhanced.storage.external.ExternalStackFactory;
import com.github.aeddddd.ae2enhanced.storage.mana.AEManaStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import nyonio.terminal_interaction_integration.api.IPacketType;

/**
 * TII Botania Mana 数据包类型.
 * <p>
 * 若 Botania_Applie 的 Mana 数据包可用则优先使用,否则回退到 AE2E 的 {@link ItemManaDrop}.
 * </p>
 */
public class ManaPacketType implements IPacketType {

    private static final String NAME = "ae2enhanced:mana";
    private static final String DISPLAY_NAME = TextFormatting.AQUA + "Mana";

    private final IStorageChannel<?> channel;

    public ManaPacketType(IStorageChannel<?> channel) {
        this.channel = channel;
    }

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
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (BotaniaApplieCompat.isLoaded() && BotaniaApplieCompat.isManaPacket(stack)) {
            return true;
        }
        return ItemManaDrop.isManaDrop(stack);
    }

    @Override
    public long getAmount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        if (BotaniaApplieCompat.isLoaded() && BotaniaApplieCompat.isManaPacket(stack)) {
            return BotaniaApplieCompat.getManaPacketAmount(stack);
        }
        if (ItemManaDrop.isManaDrop(stack)) {
            return ItemManaDrop.getAmount(stack);
        }
        return 0;
    }

    @Override
    public IAEItemStack createAEStack(long amount) {
        if (BotaniaApplieCompat.isLoaded()) {
            IAEItemStack mana = BotaniaApplieCompat.createManaAE(amount);
            if (mana != null) {
                return mana;
            }
        }
        ItemStack template = ItemManaDrop.createStack(amount);
        IAEItemStack ae = AEItemStack.fromItemStack(template);
        if (ae != null) {
            ae.setStackSize(amount);
        }
        return ae;
    }

    @Override
    public ItemStack createItemStack(long amount) {
        if (BotaniaApplieCompat.isLoaded()) {
            ItemStack mana = BotaniaApplieCompat.createManaPacket(amount);
            if (!mana.isEmpty()) {
                return mana;
            }
        }
        return ItemManaDrop.createStack(amount);
    }

    private static final String NBT_KEY_MANA = "mana";

    @SuppressWarnings("unchecked")
    @Override
    public IAEStack<?> createResourceStack(long amount) {
        if (BotaniaApplieCompat.isManaStorageChannelAvailable() && channel != null) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setLong(NBT_KEY_MANA, amount);
            IAEStack external = ExternalStackFactory.createFromNBT(channel, nbt);
            if (external != null) {
                return external;
            }
        }
        return AEManaStack.create(amount);
    }
}
