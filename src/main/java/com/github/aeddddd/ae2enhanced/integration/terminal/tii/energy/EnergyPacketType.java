package com.github.aeddddd.ae2enhanced.integration.terminal.tii.energy;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.integration.fluxapplied.FluxAppliedCompat;
import com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.external.ExternalStackFactory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;
import nyonio.terminal_interaction_integration.api.IPacketType;

/**
 * TII RF 能量数据包类型.
 * <p>
 * 若 Flux_Applied 的数据包可用则优先使用,否则回退到 AE2E 的 {@link ItemEnergyDrop}.
 * </p>
 */
public class EnergyPacketType implements IPacketType {

    private static final String NAME = "ae2enhanced:energy";
    private static final String DISPLAY_NAME = TextFormatting.GOLD + "FE";

    private final IStorageChannel<?> channel;

    public EnergyPacketType(IStorageChannel<?> channel) {
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
        if (FluxAppliedCompat.isLoaded() && FluxAppliedCompat.isFluxPacket(stack)) {
            return true;
        }
        return ItemEnergyDrop.isEnergyDrop(stack);
    }

    @Override
    public long getAmount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        if (FluxAppliedCompat.isLoaded() && FluxAppliedCompat.isFluxPacket(stack)) {
            return FluxAppliedCompat.getFluxPacketAmount(stack);
        }
        if (ItemEnergyDrop.isEnergyDrop(stack)) {
            return ItemEnergyDrop.getAmount(stack);
        }
        return 0;
    }

    @Override
    public IAEItemStack createAEStack(long amount) {
        if (FluxAppliedCompat.isLoaded()) {
            IAEItemStack flux = FluxAppliedCompat.createFluxAE(amount);
            if (flux != null) {
                return flux;
            }
        }
        ItemStack template = ItemEnergyDrop.createStack(amount);
        IAEItemStack ae = AEItemStack.fromItemStack(template);
        if (ae != null) {
            ae.setStackSize(amount);
        }
        return ae;
    }

    @Override
    public ItemStack createItemStack(long amount) {
        if (FluxAppliedCompat.isLoaded()) {
            ItemStack flux = FluxAppliedCompat.createFluxPacket(amount);
            if (!flux.isEmpty()) {
                return flux;
            }
        }
        return ItemEnergyDrop.createStack(amount);
    }

    private static final String NBT_KEY_FE = "fe";

    @SuppressWarnings("unchecked")
    @Override
    public IAEStack<?> createResourceStack(long amount) {
        if (FluxAppliedCompat.isFluxStorageChannelAvailable() && channel != null) {
            NBTTagCompound nbt = new NBTTagCompound();
            nbt.setLong(NBT_KEY_FE, amount);
            IAEStack external = ExternalStackFactory.createFromNBT(channel, nbt);
            if (external != null) {
                return external;
            }
        }
        return AEEnergyStack.create(amount);
    }
}
