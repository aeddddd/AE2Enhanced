package com.github.aeddddd.ae2enhanced.util.placement;

import appeng.api.AEApi;
import appeng.api.features.ILocatable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.core.localization.PlayerMessages;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * 安全终端绑定辅助类。
 * 为独立放置工具和先进 ME 工具提供统一的 AE2 安全终端绑定、网络获取逻辑。
 */
public final class SecurityTerminalBindingHelper {

    private static final String NBT_ENCRYPTION_KEY = "encryptionKey";

    private SecurityTerminalBindingHelper() {}

    public static boolean isLinked(ItemStack stack) {
        return !getEncryptionKey(stack).isEmpty();
    }

    public static String getEncryptionKey(ItemStack stack) {
        if (!stack.hasTagCompound()) return "";
        return stack.getTagCompound().getString(NBT_ENCRYPTION_KEY);
    }

    public static void setEncryptionKey(ItemStack stack, String key) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        }
        stack.getTagCompound().setString(NBT_ENCRYPTION_KEY, key);
    }

    public static void clearEncryptionKey(ItemStack stack) {
        if (stack.hasTagCompound()) {
            stack.getTagCompound().removeTag(NBT_ENCRYPTION_KEY);
        }
    }

    /**
     * 通过加密密钥获取绑定的 ME 网络。
     *
     * @param stack  工具物品
     * @param world  当前世界
     * @param player 可选玩家，用于发送提示信息
     * @return 网格，未绑定或不可用时返回 null
     */
    @Nullable
    public static IGrid getLinkedGrid(ItemStack stack, World world, @Nullable EntityPlayer player) {
        String key = getEncryptionKey(stack);
        if (key.isEmpty()) {
            if (player != null) {
                player.sendMessage(PlayerMessages.DeviceNotLinked.get());
            }
            return null;
        }

        long encKey;
        try {
            encKey = Long.parseLong(key);
        } catch (NumberFormatException e) {
            if (player != null) {
                player.sendMessage(PlayerMessages.DeviceNotLinked.get());
            }
            return null;
        }

        if (!world.isRemote) {
            ILocatable locatable = AEApi.instance().registries().locatable().getLocatableBy(encKey);
            if (locatable instanceof IActionHost) {
                IGridNode node = ((IActionHost) locatable).getActionableNode();
                if (node != null && node.getGrid() != null) {
                    return node.getGrid();
                }
            }
        }

        if (player != null) {
            player.sendMessage(PlayerMessages.StationCanNotBeLocated.get());
        }
        return null;
    }

    /**
     * 获取网络的物品存储监控器。
     */
    @Nullable
    public static IMEMonitor<IAEItemStack> getItemMonitor(IGrid grid) {
        if (grid == null) return null;
        IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
        if (storageGrid == null) return null;
        IStorageChannel<IAEItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        return storageGrid.getInventory(channel);
    }

    /**
     * 创建以玩家为来源的 IActionSource。
     */
    public static IActionSource createPlayerSource(final EntityPlayer player) {
        return new IActionSource() {
            @Override
            public Optional<EntityPlayer> player() {
                return Optional.of(player);
            }

            @Override
            public Optional<IActionHost> machine() {
                return Optional.empty();
            }

            @Override
            public <T> Optional<T> context(Class<T> key) {
                return Optional.empty();
            }
        };
    }

    /**
     * 尝试让物品实现 INetworkEncodable 时委托使用的工具方法。
     */
    public static String getEncryptionKeyForEncodable(ItemStack item) {
        return getEncryptionKey(item);
    }

    public static void setEncryptionKeyForEncodable(ItemStack item, String encKey, String name) {
        setEncryptionKey(item, encKey);
    }
}
