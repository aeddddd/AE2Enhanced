package com.github.aeddddd.ae2enhanced.terminal;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketInventoryAction;
import appeng.helpers.InventoryAction;
import appeng.me.helpers.PlayerSource;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.integration.botaniaapplie.BotaniaApplieCompat;
import com.github.aeddddd.ae2enhanced.integration.fluxapplied.FluxAppliedCompat;
import com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop;
import com.github.aeddddd.ae2enhanced.item.ItemManaDrop;
import com.github.aeddddd.ae2enhanced.item.ItemStarlightDrop;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;
import com.github.aeddddd.ae2enhanced.storage.external.ExternalStackFactory;
import com.github.aeddddd.ae2enhanced.storage.mana.ManaChannelResolver;
import com.github.aeddddd.ae2enhanced.storage.starlight.AEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Method;

/**
 * 非物品资源终端交互的服务器端处理.
 * <p>
 * 与 {@link UnifiedResourceTerminalBridge} 分离，避免服务端加载客户端 GUI 类。
 * </p>
 */
public final class UnifiedResourceTerminalServer {

    private static final Method MANA_ADD_MANA;
    private static final Method MANA_GET_MANA;
    private static final Method MANA_GET_MAX_MANA;

    static {
        Method addMana = null;
        Method getMana = null;
        Method getMaxMana = null;
        if (Loader.isModLoaded("botania")) {
            try {
                Class<?> iManaItem = Class.forName("vazkii.botania.api.mana.IManaItem");
                addMana = iManaItem.getMethod("addMana", ItemStack.class, int.class);
                getMana = iManaItem.getMethod("getMana", ItemStack.class);
                getMaxMana = iManaItem.getMethod("getMaxMana", ItemStack.class);
            } catch (Throwable ignored) {
            }
        }
        MANA_ADD_MANA = addMana;
        MANA_GET_MANA = getMana;
        MANA_GET_MAX_MANA = getMaxMana;
    }

    private UnifiedResourceTerminalServer() {}

    public static void handle(EntityPlayerMP player, ContainerMEMonitorable cme, IStorageGrid grid,
                              PlayerSource source, NBTTagCompound nbt) {
        String resource = nbt.getString("Resource");
        String action = nbt.getString("Action");
        long amount = nbt.getLong("Amount");

        switch (resource) {
            case "energy":
                handleEnergy(player, grid, source, action, amount, nbt);
                break;
            case "mana":
                handleMana(player, grid, source, action, amount, nbt);
                break;
            case "starlight":
                handleStarlight(player, grid, source, action, amount);
                break;
            default:
                AE2Enhanced.LOGGER.warn("[AE2E] Unknown resource action: {} {}", resource, action);
        }
    }

    /* ========================= Energy ========================= */

    private static void handleEnergy(EntityPlayerMP player, IStorageGrid grid, PlayerSource source,
                                     String action, long amount, NBTTagCompound extra) {
        IStorageChannel<?> channel = EnergyChannelResolver.getChannel();
        if (channel == null) return;
        IMEMonitor monitor = grid.getInventory(channel);
        String nbtKey = FluxAppliedCompat.isFluxStorageChannelAvailable() ? "fe" : "Count";

        if (UnifiedResourceTerminalBridge.ACTION_EXTRACT.equals(action)) {
            long extracted = extractFromNetwork(monitor, channel, nbtKey, amount, source);
            if (extracted > 0) {
                givePacketItem(player, ItemEnergyDrop.createStack(extracted), false);
            }
        } else if (UnifiedResourceTerminalBridge.ACTION_DEPOSIT.equals(action)) {
            long notInjected = depositToNetwork(monitor, channel, nbtKey, amount, source);
            consumeHeldPacket(player, amount, notInjected);
        } else if (UnifiedResourceTerminalBridge.ACTION_WORK.equals(action)) {
            ItemStack held = player.inventory.getItemStack();
            IEnergyStorage cap = held.getCapability(CapabilityEnergy.ENERGY, null);
            if (cap == null) return;
            boolean targetEnergy = extra != null && extra.getBoolean("TargetEnergy");

            if (targetEnergy) {
                int canReceive = cap.receiveEnergy(Integer.MAX_VALUE, false);
                if (canReceive <= 0) return;
                long extracted = extractFromNetwork(monitor, channel, nbtKey, canReceive, source);
                if (extracted <= 0) return;
                cap.receiveEnergy((int) Math.min(extracted, Integer.MAX_VALUE), false);
            } else {
                int canDrain = cap.extractEnergy(Integer.MAX_VALUE, false);
                if (canDrain <= 0) return;
                long notInjected = depositToNetwork(monitor, channel, nbtKey, canDrain, source);
                long injected = canDrain - notInjected;
                if (injected > 0) {
                    cap.extractEnergy((int) Math.min(injected, Integer.MAX_VALUE), false);
                }
            }
            finishContainerUse(player, held);
        }
    }

    /* ========================= Mana ========================= */

    private static void handleMana(EntityPlayerMP player, IStorageGrid grid, PlayerSource source,
                                   String action, long amount, NBTTagCompound extra) {
        if (MANA_ADD_MANA == null) return;
        IStorageChannel<?> channel = ManaChannelResolver.getChannel();
        if (channel == null) return;
        IMEMonitor monitor = grid.getInventory(channel);
        String nbtKey = BotaniaApplieCompat.isManaStorageChannelAvailable() ? "mana" : "Count";

        if (UnifiedResourceTerminalBridge.ACTION_EXTRACT.equals(action)) {
            long extracted = extractFromNetwork(monitor, channel, nbtKey, amount, source);
            if (extracted > 0) {
                givePacketItem(player, ItemManaDrop.createStack(extracted), false);
            }
        } else if (UnifiedResourceTerminalBridge.ACTION_DEPOSIT.equals(action)) {
            long notInjected = depositToNetwork(monitor, channel, nbtKey, amount, source);
            consumeHeldPacket(player, amount, notInjected);
        } else if (UnifiedResourceTerminalBridge.ACTION_WORK.equals(action)) {
            ItemStack held = player.inventory.getItemStack();
            boolean targetMana = extra != null && extra.getBoolean("TargetMana");
            int stored = getMana(held);
            int max = getMaxMana(held);
            if (stored < 0 || max <= 0) return;

            if (targetMana) {
                int canReceive = max - stored;
                if (canReceive <= 0) return;
                long extracted = extractFromNetwork(monitor, channel, nbtKey, canReceive, source);
                if (extracted <= 0) return;
                addMana(held, (int) Math.min(extracted, Integer.MAX_VALUE));
            } else {
                if (stored <= 0) return;
                long notInjected = depositToNetwork(monitor, channel, nbtKey, stored, source);
                long injected = stored - notInjected;
                if (injected > 0) {
                    addMana(held, -(int) Math.min(injected, Integer.MAX_VALUE));
                }
            }
            finishContainerUse(player, held);
        }
    }

    private static int getMana(ItemStack stack) {
        if (MANA_GET_MANA == null) return -1;
        try {
            return (int) MANA_GET_MANA.invoke(stack.getItem(), stack);
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private static int getMaxMana(ItemStack stack) {
        if (MANA_GET_MAX_MANA == null) return 0;
        try {
            return (int) MANA_GET_MAX_MANA.invoke(stack.getItem(), stack);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static void addMana(ItemStack stack, int amount) {
        if (MANA_ADD_MANA == null) return;
        try {
            MANA_ADD_MANA.invoke(stack.getItem(), stack, amount);
        } catch (Throwable ignored) {
        }
    }

    /* ========================= Starlight ========================= */

    private static void handleStarlight(EntityPlayerMP player, IStorageGrid grid, PlayerSource source,
                                        String action, long amount) {
        IStorageChannel<?> channel = AEApi.instance().storage().getStorageChannel(IStarlightStorageChannel.class);
        if (channel == null) return;
        IMEMonitor monitor = grid.getInventory(channel);

        if (UnifiedResourceTerminalBridge.ACTION_EXTRACT.equals(action)) {
            long extracted = extractFromNetwork(monitor, channel, "Count", amount, source);
            if (extracted > 0) {
                givePacketItem(player, ItemStarlightDrop.createStack(extracted), false);
            }
        } else if (UnifiedResourceTerminalBridge.ACTION_DEPOSIT.equals(action)) {
            IAEStack input = AEStarlightStack.create(amount);
            IAEStack notInjected = monitor.injectItems(input, Actionable.MODULATE, source);
            long notInjectedSize = notInjected != null ? notInjected.getStackSize() : 0;
            consumeHeldPacket(player, amount, notInjectedSize);
        }
    }

    /* ========================= Helpers ========================= */

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static long extractFromNetwork(IMEMonitor monitor, IStorageChannel channel, String nbtKey,
                                           long amount, IActionSource source) {
        IAEStack request = createChannelStack(channel, nbtKey, amount);
        if (request == null) return 0;
        IAEStack notExtracted = monitor.extractItems(request, Actionable.MODULATE, source);
        long notExtractedSize = notExtracted != null ? notExtracted.getStackSize() : 0;
        return amount - notExtractedSize;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static long depositToNetwork(IMEMonitor monitor, IStorageChannel channel, String nbtKey,
                                         long amount, IActionSource source) {
        IAEStack input = createChannelStack(channel, nbtKey, amount);
        if (input == null) return amount;
        IAEStack notInjected = monitor.injectItems(input, Actionable.MODULATE, source);
        return notInjected != null ? notInjected.getStackSize() : 0;
    }

    private static IAEStack createChannelStack(IStorageChannel<?> channel, String nbtKey, long amount) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong(nbtKey, amount);
        IAEStack stack = ExternalStackFactory.createFromNBT(channel, nbt);
        if (stack != null) {
            stack.setStackSize(amount);
            return stack;
        }
        return null;
    }

    private static void givePacketItem(EntityPlayerMP player, ItemStack packet, boolean shift) {
        if (packet.isEmpty()) return;
        if (shift) {
            player.inventory.placeItemBackInInventory(player.world, packet);
        } else {
            player.inventory.setItemStack(packet);
        }
        updateHeld(player);
    }

    private static void consumeHeldPacket(EntityPlayerMP player, long sent, long notInjected) {
        ItemStack held = player.inventory.getItemStack();
        if (held.isEmpty()) return;
        long consumed = sent - notInjected;
        if (consumed <= 0) return;
        long remaining = getPacketAmount(held) - consumed;
        ItemStack back;
        if (remaining > 0) {
            back = held.copy();
            back.setCount(1);
            back.setTagCompound(new NBTTagCompound());
            back.getTagCompound().setLong("Amount", remaining);
        } else {
            back = ItemStack.EMPTY;
        }
        player.inventory.setItemStack(back);
        updateHeld(player);
    }

    private static long getPacketAmount(ItemStack stack) {
        if (stack.hasTagCompound() && stack.getTagCompound().hasKey("Amount")) {
            return stack.getTagCompound().getLong("Amount");
        }
        return stack.getCount();
    }

    private static void finishContainerUse(EntityPlayerMP player, ItemStack original) {
        ItemStack current = player.inventory.getItemStack();
        if (current.isEmpty()) return;
        // 容器的 NBT 可能已被修改，当前 stack 即修改后的结果
        if (original.getCount() > 1) {
            ItemStack modified = current.copy();
            modified.setCount(1);
            original.shrink(1);
            player.inventory.placeItemBackInInventory(player.world, modified);
        }
        updateHeld(player);
    }

    private static void updateHeld(EntityPlayerMP player) {
        if (Platform.isServer()) {
            try {
                NetworkHandler.instance().sendTo(
                        new PacketInventoryAction(InventoryAction.UPDATE_HAND, 0,
                                AEItemStack.fromItemStack(player.inventory.getItemStack())),
                        player);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to sync held item", e);
            }
        }
    }
}
