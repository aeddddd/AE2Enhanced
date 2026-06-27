package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGridNode;
import appeng.api.util.AEPartLocation;
import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.me.helpers.IGridProxyable;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import javax.annotation.Nonnull;
import java.util.EnumSet;

/**
 * 拥有独立 AE 网络代理的控制器 TileEntity 基类.
 *
 * 统一封装 proxy 生命周期(create/get/validate/invalidate/onChunkUnload),
 * 消除 TileAssemblyController / TileHyperdimensionalController / TileComputationCore
 * 中各自重复的 ~40 行代理样板代码.
 *
 * 子类只需提供：
 * - {@link #getProxyName()}        代理标识名
 * - {@link #getProxyRepresentation()} 代理代表物品(用于 AE 网络显示)
 */
public abstract class TileAENetworkBase extends TileEntity implements IGridProxyable {

    protected AENetworkProxy proxy;
    protected boolean needsReady = false;

    // ---- 子类必须实现 ----

    protected abstract String getProxyName();

    protected abstract ItemStack getProxyRepresentation();

    // ---- 可选覆盖 ----

    /**
     * proxy invalidate 时的额外清理回调.
     * TileHyperdimensionalController 覆盖此方法调用 closeStorage().
     */
    protected void onProxyInvalidate() {
    }

    /**
     * proxy chunk unload 时的额外清理回调.
     */
    protected void onProxyChunkUnload() {
    }

    // ---- IGridProxyable 实现 ----

    private AENetworkProxy createProxy() {
        AENetworkProxy p = new AENetworkProxy(this, getProxyName(), getProxyRepresentation(), true);
        p.setValidSides(EnumSet.allOf(EnumFacing.class));
        return p;
    }

    @Override
    public AENetworkProxy getProxy() {
        if (proxy == null) {
            proxy = createProxy();
        }
        return proxy;
    }

    @Override
    public DimensionalCoord getLocation() {
        return new DimensionalCoord(this);
    }

    @Override
    public void gridChanged() {
    }

    @Override
    public IGridNode getGridNode(@Nonnull AEPartLocation dir) {
        return getProxy().getNode();
    }

    // ---- 生命周期 ----

    @Override
    public void validate() {
        super.validate();
        needsReady = true;
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (proxy != null) {
            proxy.invalidate();
        }
        onProxyInvalidate();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (proxy != null) {
            proxy.onChunkUnload();
        }
        onProxyChunkUnload();
    }

    // ---- 结构控制 ----

    public abstract void disassemble();

    // ---- 状态访问 ----

    public boolean needsReady() {
        return needsReady;
    }

    public void clearNeedsReady() {
        needsReady = false;
    }
}
