package com.github.aeddddd.ae2enhanced.util.inv;

import appeng.util.InventoryAdaptor;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.ItemSlot;
import appeng.api.config.FuzzyMode;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraft.server.MinecraftServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 原版 {@link InventoryAdaptor#getAdaptor(TileEntity, EnumFacing)} 结果缓存.
 *
 * <p>仅在 ae2fc 未安装时由条件 mixin（MixinVanillaInventoryAdaptor）引用：
 * ae2fc 安装时其 MixinDualityInterface 会把接口热路径的 getAdaptor 全部重定向到
 * FluidConvertingInventoryAdaptor.wrap（已由 FluidAdaptorCache 覆盖），本缓存无需加载。</p>
 *
 * <p>getAdaptor 每次调用 = 2 次 capability 查询 + 1 次适配器分配，频率为
 * 每 tick × 每接口 × 每朝向（pushPattern/isBusy/pushItemsOut）。
 * 适配器本身无运行时状态（AdaptorItemHandler 为 IItemHandler 的纯包装），
 * 按 (world, 目标位置, 朝向) 缓存，以目标 TE 身份 + isInvalid 校验失效；
 * 无 capability 的邻居同样缓存（负缓存，NONE 哨兵）。</p>
 *
 * <p>仅限服务端线程访问（getAdaptor 的调用方均在 tick 线程）。</p>
 */
public final class VanillaAdaptorCache {

    private VanillaAdaptorCache() {
    }

    /** 负缓存哨兵：表示该 (位置, 朝向) 已确认无任何物品适配器。 */
    public static final InventoryAdaptor NONE = new InventoryAdaptor() {
        @Override
        public ItemStack removeItems(int howMany, ItemStack filter, IInventoryDestination destination) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack simulateRemove(int howMany, ItemStack filter, IInventoryDestination destination) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeSimilarItems(int howMany, ItemStack filter, FuzzyMode fuzzyMode,
                IInventoryDestination destination) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack simulateSimilarRemove(int howMany, ItemStack filter, FuzzyMode fuzzyMode,
                IInventoryDestination destination) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack addItems(ItemStack items) {
            return items;
        }

        @Override
        public ItemStack simulateAdd(ItemStack items) {
            return items;
        }

        @Override
        public boolean containsItems() {
            return false;
        }

        @Override
        public boolean hasSlots() {
            return false;
        }

        @Override
        public Iterator<ItemSlot> iterator() {
            return java.util.Collections.emptyIterator();
        }
    };

    private static final class Entry {
        TileEntity target;
        /** null 表示负缓存（该朝向无适配器）。 */
        InventoryAdaptor adaptor;
    }

    /** World -> (pos long -> 按 face.ordinal() 索引的缓存项). */
    private static final Map<World, Long2ObjectOpenHashMap<Entry[]>> CACHE = new IdentityHashMap<>();

    /**
     * 查询缓存.返回 null 表示未命中（调用方应执行原始 getAdaptor）；
     * 返回 {@link #NONE} 表示负缓存命中。
     */
    @Nullable
    public static InventoryAdaptor get(TileEntity te, EnumFacing face) {
        World world = te.getWorld();
        if (world == null) {
            return null;
        }
        Long2ObjectOpenHashMap<Entry[]> worldMap = CACHE.get(world);
        if (worldMap == null) {
            return null;
        }
        Entry[] byFace = worldMap.get(te.getPos().toLong());
        if (byFace == null) {
            return null;
        }
        Entry e = byFace[face.ordinal()];
        if (e == null) {
            return null;
        }
        // TE 身份变化（方块破坏替换）或失效即缓存失效
        if (e.target != te || te.isInvalid()) {
            return null;
        }
        return e.adaptor == null ? NONE : e.adaptor;
    }

    public static void put(TileEntity te, EnumFacing face, @Nullable InventoryAdaptor adaptor) {
        World world = te.getWorld();
        if (world == null) {
            return;
        }
        Entry e = new Entry();
        e.target = te;
        e.adaptor = adaptor;
        // 注意:不得使用 fastutil 8.x 新增的 Long2ObjectOpenHashMap.computeIfAbsent,
        // 运行时环境存在 fastutil 7.x(无此方法),会抛 NoSuchMethodError
        Long2ObjectOpenHashMap<Entry[]> worldMap = CACHE.computeIfAbsent(world, w -> new Long2ObjectOpenHashMap<>());
        long posLong = te.getPos().toLong();
        Entry[] byFace = worldMap.get(posLong);
        if (byFace == null) {
            byFace = new Entry[6];
            worldMap.put(posLong, byFace);
        }
        byFace[face.ordinal()] = e;
        sweep();
    }

    /**
     * 清理已卸载世界的缓存桶（缓存项经 TileEntity 强引用 World,
     * 必须在 put 的冷路径上定期清扫）.
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
