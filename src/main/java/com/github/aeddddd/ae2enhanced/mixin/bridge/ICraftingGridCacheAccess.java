package com.github.aeddddd.ae2enhanced.mixin.bridge;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;

import java.util.List;
import java.util.Set;

/**
 * CraftingGridCache 样板索引访问接口, 供循环分析副产物边使用.
 * <p>1.12.2 的 {@code getCraftingFor} 只按主产出索引样板; 发现经副产物闭合的环
 * (催化环, 如 1A→1X+1B, 1B→1A) 需要全样板键集做生产者扫描.</p>
 */
public interface ICraftingGridCacheAccess {

    /** 网络当前所有可合成键, 即 craftableItems 索引键, 只读快照. */
    Set<IAEItemStack> ae2enhanced$craftableKeys();

    /**
     * 一致性样板快照, canon 键到该键主索引样板表的映射.
     * AE2-UEL 的 craftableItems 由服务器线程在 recalculateCraftingPatterns 中就地 clear+重建,
     * 计算线程活读会得到 CME 或静默空窗且复核无法区分; 快照在 recalc TAIL 固化, 求解期一律走快照.
     */
    java.util.Map<IAEItemStack, java.util.List<ICraftingPatternDetails>> ae2enhanced$craftableSnapshot();

    /**
     * 发射台判定快照, canon 键与最近一次 recalc 同代, setEmitable 动态增量 copy-on-write.
     * 与 {@code canEmitFor} 同语义, 但不受 recalc 重建空窗影响.
     */
    boolean ae2enhanced$canEmit(IAEItemStack canonKey);

    /**
     * 网络样板缓存索引, 含 SCC 环检测、副产物倒排与 detector memo.
     * 惰性构建, recalculateCraftingPatterns 后失效重建; 计算线程并发安全.
     */
    NetworkPatternIndex ae2enhanced$patternIndex();

    /**
     * 网络中是否存在装配中枢控制器节点.
     * 供合成 CPU 批量结算注入快速早退, 避免无装配中枢时每 tick 对每个任务做 getMediums 的
     * map 查找, 该查找会触发样板深层 NBT 比较.
     */
    boolean ae2enhanced$hasAssemblyHub();

    /**
     * getMediums 的 memo 版本: 按 details 实例身份缓存结果, recalculateCraftingPatterns 时统一失效.
     * 原生 getMediums 是 equals 语义的 HashMap 查找, 每 tick 每 task 重复执行会反复触发样板深层
     * NBT 比较, spark 热点占 16%; memo 命中路径无 equals 调用, 未命中回退原生查找并登记.
     */
    List<ICraftingMedium> ae2enhanced$getMediumsMemo(ICraftingPatternDetails details);
}
