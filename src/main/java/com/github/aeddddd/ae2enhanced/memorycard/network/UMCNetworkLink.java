package com.github.aeddddd.ae2enhanced.memorycard.network;

import javax.annotation.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import appeng.api.features.IGridLinkableHandler;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.storage.MEStorage;
import appeng.util.Platform;

import com.github.aeddddd.ae2enhanced.item.UniversalMemoryCardItem;

/**
 * 通用内存卡的 ME 网络绑定链接.
 *
 * <p>对应 1.12 的「绑定中枢 ME 接口/无线频道发射器」:1.12 中内存卡绑定无线频道发射器坐标,
 * 粘贴缺升级卡时经其解析网络发起提取/合成请求。1.20 改为 AE2 原生的物品-网络绑定机制:
 * 绑定无线访问点(Wireless Access Point)的 {@link GlobalPos},使用时经
 * {@link IWirelessAccessPoint#getGrid()} 解析网格(与全能工具 OmniToolNetworkLink 一致)。
 * 本类同时实现 {@link IGridLinkableHandler} 以支持在 WAP GUI 链接槽中绑定,
 * 并保留对 WAP 直接右键绑定的便捷途径(见 UMCSelectionService).</p>
 */
public final class UMCNetworkLink {

    private static final String NBT_LINK = "AELink";

    private UMCNetworkLink() {
    }

    public static boolean isLinked(ItemStack stack) {
        return getLinkedPos(stack) != null;
    }

    @Nullable
    public static GlobalPos getLinkedPos(ItemStack stack) {
        if (!stack.hasTag() || !stack.getTag().contains(NBT_LINK, Tag.TAG_COMPOUND)) {
            return null;
        }
        return GlobalPos.CODEC.parse(NbtOps.INSTANCE, stack.getTag().getCompound(NBT_LINK))
                .result().orElse(null);
    }

    public static void link(ItemStack stack, GlobalPos pos) {
        GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, pos).result()
                .ifPresent(tag -> stack.getOrCreateTag().put(NBT_LINK, tag));
    }

    public static void unlink(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove(NBT_LINK);
        }
    }

    /**
     * 通过绑定的无线访问点坐标解析 ME 网络,逻辑与 AE2 无线终端一致.
     *
     * @param stack 内存卡物品
     * @param level 当前世界(必须服务端)
     * @return 网格,未绑定或不可用时返回 null
     */
    @Nullable
    public static IGrid getLinkedGrid(ItemStack stack, Level level) {
        GlobalPos linkedPos = getLinkedPos(stack);
        if (linkedPos == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        ServerLevel linkedLevel = serverLevel.getServer().getLevel(linkedPos.dimension());
        if (linkedLevel == null) {
            return null;
        }
        if (Platform.getTickingBlockEntity(linkedLevel, linkedPos.pos()) instanceof IWirelessAccessPoint accessPoint) {
            return accessPoint.getGrid();
        }
        return null;
    }

    /**
     * 获取绑定网络的物品存储.
     */
    @Nullable
    public static MEStorage getItemStorage(ItemStack stack, Level level) {
        IGrid grid = getLinkedGrid(stack, level);
        if (grid == null) {
            return null;
        }
        return grid.getStorageService().getInventory();
    }

    /**
     * WAP GUI 链接槽用的链接处理器,在 common setup 中注册到 GridLinkables.
     */
    public static final IGridLinkableHandler LINKABLE_HANDLER = new IGridLinkableHandler() {
        @Override
        public boolean canLink(ItemStack stack) {
            return stack.getItem() instanceof UniversalMemoryCardItem;
        }

        @Override
        public void link(ItemStack itemStack, GlobalPos pos) {
            UMCNetworkLink.link(itemStack, pos);
        }

        @Override
        public void unlink(ItemStack itemStack) {
            UMCNetworkLink.unlink(itemStack);
        }
    };
}
