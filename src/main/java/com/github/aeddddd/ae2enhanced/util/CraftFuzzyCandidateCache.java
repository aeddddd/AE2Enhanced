package com.github.aeddddd.ae2enhanced.util;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEItemStack;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合成终端 fuzzy 提取候选缓存.
 * 缓存 Platform.extractItemsByRecipe fuzzy 分支(矿物词典/NBT/耐久通配)扫描出的候选物品,
 * 有效期为单个 tick(按 World + totalWorldTime 失效).
 *
 * 背景: shift 点击合成一组时, SlotCraftingTerm.doClick 单 tick 内最多执行 64 次合成,
 * 每次合成对最多 9 个原料格调用 extractItemsByRecipe; 若精确物品缺失且原料注册了矿物词典,
 * fuzzy 分支会对全网络物品列表做 O(N) 遍历, 形成 64×9×N 的级联卡顿.
 * 同一 tick 内网络的候选集合基本稳定, 因此按 (物品列表对象, 模板) 缓存候选即可消除重复扫描.
 */
public final class CraftFuzzyCandidateCache {

    private static final Map<Key, List<IAEItemStack>> CACHE = new HashMap<>();
    private static World lastWorld = null;
    private static long lastTick = Long.MIN_VALUE;

    private CraftFuzzyCandidateCache() {
    }

    /**
     * 查询缓存的候选列表.
     * 返回 null 表示无缓存(需要全量扫描); 返回空列表表示本 tick 已确认不存在任何候选.
     */
    public static List<IAEItemStack> get(World w, IItemList<IAEItemStack> items, AEItemStack template) {
        if (!checkTick(w)) {
            return null;
        }
        return CACHE.get(new Key(items, template));
    }

    /** 写入候选列表(可为空列表, 表示确认无候选). */
    public static void put(World w, IItemList<IAEItemStack> items, AEItemStack template,
                           List<IAEItemStack> candidates) {
        checkTick(w);
        CACHE.put(new Key(items, template), candidates);
    }

    /** 候选全部失效(被抽空)时移除缓存, 下一次调用将全量重扫. */
    public static void invalidate(World w, IItemList<IAEItemStack> items, AEItemStack template) {
        if (checkTick(w)) {
            CACHE.remove(new Key(items, template));
        }
    }

    /** tick 或维度变化时清空全部缓存; 返回缓存是否仍然有效(未发生清空). */
    private static boolean checkTick(World w) {
        if (w != lastWorld || w.getTotalWorldTime() != lastTick) {
            CACHE.clear();
            lastWorld = w;
            lastTick = w.getTotalWorldTime();
            return false;
        }
        return true;
    }

    private static final class Key {
        private final IItemList<IAEItemStack> items;
        private final AEItemStack template;

        private Key(IItemList<IAEItemStack> items, AEItemStack template) {
            this.items = items;
            this.template = template;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }
            Key other = (Key) o;
            // NetworkMonitor.getStorageList() 返回同一 cachedList 对象, 用引用相等即可
            return this.items == other.items && this.template.equals(other.template);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this.items) * 31 + this.template.hashCode();
        }
    }
}
