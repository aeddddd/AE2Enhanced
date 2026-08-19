package com.github.aeddddd.ae2enhanced.mixin.bridge;

import java.util.Set;

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
}
