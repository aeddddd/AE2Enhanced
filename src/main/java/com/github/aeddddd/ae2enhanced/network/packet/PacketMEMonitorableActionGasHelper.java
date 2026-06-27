package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.me.helpers.PlayerSource;
import com.mekeng.github.common.me.storage.IGasStorageChannel;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.mekeng.github.common.me.data.IAEGasStack;
import com.mekeng.github.common.me.data.impl.AEGasStack;
import mekanism.api.gas.GasStack;
import mekanism.api.gas.IGasItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * PacketMEMonitorableAction 的气体处理辅助类.
 *
 * 本类包含所有对 Mekanism / MekanismEnergistics 类的直接引用,
 * 由 {@link PacketMEMonitorableAction} 在确认 mod 存在后通过反射调用,
 * 避免在缺少对应 mod 时触发 NoClassDefFoundError.
 */
public final class PacketMEMonitorableActionGasHelper {

    private PacketMEMonitorableActionGasHelper() {}

    public static void gasWork(PacketMEMonitorableAction message, ItemStack singleHeld,
                                IStorageGrid grid, PlayerSource source, EntityPlayerMP player) {
        ItemStack actualHeld = player.inventory.getItemStack();
        if (!ItemStack.areItemsEqual(singleHeld, actualHeld) || !ItemStack.areItemStackTagsEqual(singleHeld, actualHeld)
                || actualHeld.isEmpty()) {
            return;
        }
        if (!(actualHeld.getItem() instanceof IGasItem)) {
            return;
        }
        IGasItem ig = (IGasItem) actualHeld.getItem();

        GasStack targetGas = null;
        NBTTagCompound nbt = message.getNbt();
        if (nbt != null && !nbt.isEmpty()) {
            targetGas = GasStack.readFromNBT(nbt);
        }
        if (targetGas != null) {
            targetGas.amount = Integer.MAX_VALUE;
        }

        IMEMonitor<IAEGasStack> gasStorage = grid.getInventory(AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class));

        boolean drain = false;
        GasStack allGas = ig.getGas(singleHeld);
        int allAmount = allGas == null ? 0 : allGas.amount;

        if (targetGas != null && allGas != null && allGas.amount > 0 && !allGas.isGasEqual(targetGas)) {
            drain = true;
        } else if (targetGas == null) {
            drain = true;
        }

        if (drain) {
            if (allGas == null) return;
            AEGasStack allAEGas = AEGasStack.of(allGas);
            if (allAEGas == null) return;
            IAEGasStack notInjected = gasStorage.injectItems(allAEGas, Actionable.SIMULATE, source);
            long size = allAEGas.getStackSize() - (notInjected == null ? 0L : notInjected.getStackSize());
            if (size <= 0) return;
            gasStorage.injectItems(allAEGas.setStackSize(size), Actionable.MODULATE, source);
            ig.removeGas(singleHeld, (int) size);
        } else {
            if (targetGas == null) return;
            AEGasStack targetAEGas = AEGasStack.of(targetGas);
            if (targetAEGas == null) return;
            IAEGasStack extracted = gasStorage.extractItems(targetAEGas, Actionable.SIMULATE, source);
            if (extracted == null) return;
            int size = Math.min(ig.getMaxGas(singleHeld) - allAmount, (int) extracted.getStackSize());
            if (size <= 0) return;
            gasStorage.extractItems(targetAEGas.setStackSize(size), Actionable.MODULATE, source);
            targetGas.amount = size + allAmount;
            ig.setGas(singleHeld, targetGas);
        }

        if (actualHeld.getCount() > 1) {
            actualHeld.shrink(1);
            player.inventory.placeItemBackInInventory(player.world, singleHeld);
        } else {
            player.inventory.setItemStack(singleHeld);
        }
        PacketMEMonitorableAction.Handler.updateHeld(player);
    }

    public static void gasOperateWork(PacketMEMonitorableAction message, IStorageGrid grid,
                                       PlayerSource source, EntityPlayerMP player) {
        if (!player.inventory.getItemStack().isEmpty()) return;

        GasStack gas = null;
        NBTTagCompound nbt = message.getNbt();
        if (nbt != null && !nbt.isEmpty()) {
            gas = GasStack.readFromNBT(nbt);
        }
        if (gas == null || gas.getGas() == null) return;
        gas.amount = 1000;

        boolean shift = nbt != null && nbt.getBoolean("shift");

        IMEMonitor<IAEGasStack> gasStorage = grid.getInventory(AEApi.instance().storage().getStorageChannel(IGasStorageChannel.class));
        IAEGasStack extracted = gasStorage.extractItems(AEGasStack.of(gas), Actionable.SIMULATE, source);
        if (extracted == null || extracted.getStackSize() < 1000L) return;

        ItemStack out = ItemGasDrop.createStack(gas);
        if (out.isEmpty()) return;

        gasStorage.extractItems(AEGasStack.of(gas), Actionable.MODULATE, source);

        if (shift) {
            int slot = player.inventory.getFirstEmptyStack();
            if (slot == -1) return;
            player.inventory.setInventorySlotContents(slot, out);
        } else {
            player.inventory.setItemStack(out);
        }
        PacketMEMonitorableAction.Handler.updateHeld(player);
    }
}
