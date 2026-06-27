package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionHost;
import appeng.me.helpers.IGridProxyable;
import net.minecraft.tileentity.TileEntity;

import java.util.List;

/**
 * 中枢 ME 接口的宿主契约.
 *
 * 设计目标：与 AE2 的 {@link appeng.helpers.IInterfaceHost} 保持同构,
 * 但将推送目标从相邻面改为远程绑定的 {@link TargetBinding} 列表.
 */
public interface ICentralInterfaceHost extends IGridProxyable, ICraftingProvider, IActionHost {

    /**
     * 获取宿主的世界内 TileEntity(用于 Duality 访问 world/pos).
     */
    TileEntity getTileEntity();

    /**
     * 获取当前所有有效的远程绑定目标.
     *
     * 返回的 BlockPos 均位于 {@link #getTileEntity()} 的同一个维度.
     */
    List<TargetBinding> getTargets();

    /**
     * 获取中枢接口的 Duality 实例(持有核心逻辑).
     */
    DualityCentralInterface getInterfaceDuality();

    /**
     * 当物品被 return 到网络时的回调(用于 storage slot 溢出回收).
     */
    default void onStackReturnNetwork(appeng.api.storage.data.IAEItemStack stack) {
        this.getInterfaceDuality().onStackReturnedToNetwork(stack);
    }

    /**
     * 保存 NBT 变更标记.
     */
    void saveChanges();
}
