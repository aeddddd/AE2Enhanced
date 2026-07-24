package com.github.aeddddd.ae2enhanced.omnitool.network;

import javax.annotation.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
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

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;

/**
 * 全能工具的 ME 网络链接.
 *
 * <p>1.12 中放置模式通过安全终端加密钥(ILocatableRegistry)解析网络、DROP_AE 通过
 * 无线频道发射器坐标解析网络。AE2 15.x 已移除安全终端方块与通用 locatable 注册表,
 * 原生的物品-网络绑定机制改为:在无线访问点(Wireless Access Point)GUI 中放入可链接
 * 物品,将访问点的 {@link GlobalPos} 写入物品 NBT,使用时经
 * {@link IWirelessAccessPoint#getGrid()} 解析网格(与无线终端一致)。本类同时实现
 * {@link IGridLinkableHandler} 以支持 WAP GUI 链接槽,并保留潜行右键 WAP 直接绑定的
 * 便捷途径(对应 1.12 潜行右键安全终端)。</p>
 */
public final class OmniToolNetworkLink {

    private static final String NBT_LINK = "AELink";

    private OmniToolNetworkLink() {}

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
     * @param stack 工具物品
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
            return stack.getItem() instanceof AdvancedMEOmniToolItem;
        }

        @Override
        public void link(ItemStack itemStack, GlobalPos pos) {
            OmniToolNetworkLink.link(itemStack, pos);
        }

        @Override
        public void unlink(ItemStack itemStack) {
            OmniToolNetworkLink.unlink(itemStack);
        }
    };
}
