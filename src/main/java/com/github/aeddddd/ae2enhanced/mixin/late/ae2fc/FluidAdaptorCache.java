package com.github.aeddddd.ae2enhanced.mixin.late.ae2fc;

import appeng.helpers.DualityInterface;
import appeng.tile.misc.TileInterface;
import com.glodblock.github.interfaces.FCDualityInterface;
import appeng.tile.networking.TileCableBus;
import appeng.api.parts.IPart;
import appeng.helpers.IInterfaceHost;
import appeng.util.InventoryAdaptor;
import com.glodblock.github.common.tile.TileDualInterface;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * FluidConvertingInventoryAdaptor.wrap 结果缓存.
 *
 * <p>背景：ae2fc 的 MixinDualityInterface 把 AE2 接口 pushPattern/isBusy/pushItemsOut
 * 中的 InventoryAdaptor.getAdaptor 全部重定向到 FluidConvertingInventoryAdaptor.wrap,
 * 每次调用都新建适配器（getTileEntity + 3 次 capability 查询 + 对象分配），
 * 频率 = 每 tick × 每接口 × 每方向（spark 采样 ~5.8%）。</p>
 *
 * <p>适配器实例无运行时状态（全部字段 final），可安全复用。缓存项记录
 * wrap 的全部输入派生量（目标 TE、inter TE、onmi、fluidPacket），
 * 命中校验逐项比对,任一变化即回退原始 wrap 并覆盖缓存项：</p>
 * <ul>
 *   <li>目标/inter 的 TE 身份（方块破坏替换后实例改变,自然失效）</li>
 *   <li>onmi（接口 GUI 改目标方向数）与 fluidPacket（流体封包模式开关）每次重算</li>
 * </ul>
 *
 * <p>本类仅在 ae2fc 存在时经条件 mixin 配置加载，可安全引用 ae2fc 类。
 * 仅限服务端线程访问（wrap 的调用方均在 tick 线程）。</p>
 */
public final class FluidAdaptorCache {

    private FluidAdaptorCache() {
    }

    private static final class Entry {
        TileEntity target;
        TileEntity inter;
        boolean onmi;
        boolean fluidPacket;
        InventoryAdaptor adaptor;
    }

    /** World -> (pos long -> 按 face.ordinal() 索引的缓存项). */
    private static final Map<World, Long2ObjectOpenHashMap<Entry[]>> CACHE = new IdentityHashMap<>();

    /**
     * 查询缓存.返回 null 表示未命中或校验失败（调用方应执行原始 wrap）。
     */
    @Nullable
    public static InventoryAdaptor get(ICapabilityProvider capProvider, EnumFacing face) {
        if (!(capProvider instanceof TileEntity)) {
            return null;
        }
        TileEntity cap = (TileEntity) capProvider;
        World world = cap.getWorld();
        if (world == null) {
            return null;
        }
        Long2ObjectOpenHashMap<Entry[]> worldMap = CACHE.get(world);
        if (worldMap == null) {
            return null;
        }
        Entry[] byFace = worldMap.get(cap.getPos().toLong());
        if (byFace == null) {
            return null;
        }
        Entry e = byFace[face.ordinal()];
        if (e == null) {
            return null;
        }
        TileEntity inter = world.getTileEntity(cap.getPos().offset(face.getOpposite()));
        if (e.target != cap || cap.isInvalid() || e.inter != inter) {
            return null;
        }
        // 运行时可切换的配置位必须每次重算
        if (e.onmi != computeOnmi(inter) || e.fluidPacket != computeFluidPacket(inter, face)) {
            return null;
        }
        return e.adaptor;
    }

    public static void put(ICapabilityProvider capProvider, EnumFacing face, @Nullable InventoryAdaptor adaptor) {
        if (!(capProvider instanceof TileEntity) || adaptor == null) {
            return;
        }
        TileEntity cap = (TileEntity) capProvider;
        World world = cap.getWorld();
        if (world == null) {
            return;
        }
        TileEntity inter = world.getTileEntity(cap.getPos().offset(face.getOpposite()));
        Entry e = new Entry();
        e.target = cap;
        e.inter = inter;
        e.onmi = computeOnmi(inter);
        e.fluidPacket = computeFluidPacket(inter, face);
        e.adaptor = adaptor;
        CACHE.computeIfAbsent(world, w -> new Long2ObjectOpenHashMap<>())
                .computeIfAbsent(cap.getPos().toLong(), k -> new Entry[6])[face.ordinal()] = e;
        sweep();
    }

    /** 与 FluidConvertingInventoryAdaptor.wrap 完全一致的 onmi 计算. */
    private static boolean computeOnmi(@Nullable TileEntity inter) {
        if (inter instanceof TileInterface) {
            return ((TileInterface) inter).getTargets().size() > 1;
        }
        if (inter instanceof TileDualInterface) {
            return ((TileDualInterface) inter).getTargets().size() > 1;
        }
        return false;
    }

    /** 与 FluidConvertingInventoryAdaptor.wrap 完全一致的 fluidPacket 判定. */
    private static boolean computeFluidPacket(@Nullable TileEntity inter, EnumFacing face) {
        IInterfaceHost host = getInterfaceHost(inter, face);
        DualityInterface duality = host != null ? host.getInterfaceDuality() : null;
        return duality != null && ((FCDualityInterface) duality).isFluidPacket();
    }

    /** 复制 ae2fc 的 getInterfaceTE 逻辑（其私有静态方法,无法直接调用）. */
    @Nullable
    private static IInterfaceHost getInterfaceHost(@Nullable TileEntity te, EnumFacing face) {
        if (te instanceof IInterfaceHost) {
            return (IInterfaceHost) te;
        }
        if (te instanceof TileCableBus) {
            IPart part = ((TileCableBus) te).getPart(face.getOpposite());
            if (part instanceof IInterfaceHost) {
                return (IInterfaceHost) part;
            }
        }
        return null;
    }

    /**
     * 清理已卸载世界的缓存桶（缓存项经 TileEntity 强引用 World,
     * WeakHashMap 无法回收,必须在 put 的冷路径上定期清扫）.
     */
    private static void sweep() {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            CACHE.clear();
            return;
        }
        List<World> loaded = new ArrayList<>();
        for (WorldServer ws : server.worlds) {
            loaded.add(ws);
        }
        CACHE.keySet().retainAll(loaded);
    }
}
