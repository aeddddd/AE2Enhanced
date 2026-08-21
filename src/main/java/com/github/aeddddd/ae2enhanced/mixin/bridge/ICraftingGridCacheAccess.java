package com.github.aeddddd.ae2enhanced.mixin.bridge;

import java.util.List;
import java.util.Set;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;

/**
 * CraftingGridCache 样板索引访问接口（循环分析副产物边用）.
 * <p>1.12.2 的 {@code getCraftingFor} 只按主产出索引样板;发现"经副产物闭合的环"
 * （催化环,如 1A→1X+1B、1B→1A）需要全样板键集做生产者扫描.</p>
 */
public interface ICraftingGridCacheAccess {

    /** 网络当前所有可合成键（craftableItems 索引键,只读快照）. */
    Set<IAEItemStack> ae2enhanced$craftableKeys();

    /**
     * 网络样板缓存索引（SCC 环检测 + 副产物倒排 + detector memo）.
     * 惰性构建,recalculateCraftingPatterns 后失效重建;计算线程并发安全.
     */
    NetworkPatternIndex ae2enhanced$patternIndex();

    /**
     * 网络中是否存在装配中枢控制器节点.
     * 供合成 CPU 批量结算注入快速早退,避免无装配中枢时
     * 每 tick 对每个任务执行 getMediums 的 map 查找（触发样板深层 NBT 比较）.
     */
    boolean ae2enhanced$hasAssemblyHub();

    /**
     * getMediums 的 memo 版本:按 details 实例身份缓存结果,
     * recalculateCraftingPatterns 时统一失效.
     * 原生 getMediums 是 equals 语义的 HashMap 查找,每 tick × 每 task 重复执行
     * 会反复触发样板 equals/hashCode 的深层 NBT 比较(spark 热点 16%).
     * memo 命中路径无 equals 调用;未命中回退原生查找并登记.
     */
    List<ICraftingMedium> ae2enhanced$getMediumsMemo(ICraftingPatternDetails details);
}
