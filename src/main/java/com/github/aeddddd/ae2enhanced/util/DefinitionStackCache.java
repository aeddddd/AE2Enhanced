package com.github.aeddddd.ae2enhanced.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AE2 物品定义 → "代表物品栈"的小缓存。
 *
 * <p>用途：AE2 的 {@code IItemDefinition.isSameAs(stack)} 每次调用都会 {@code maybeStack(1)} →
 * {@code new ItemStack(...)} → 触发 Forge 的 {@code gatherCapabilities} 事件。这条判据位于
 * shift-click 热路径上，因此把代表栈缓存下来、之后只做物品类型比较。</p>
 *
 * <h3>为什么必须用"稳定的强引用键"</h3>
 * <p>真实事故（验收文档 K 节）：上一版用 {@code WeakHashMap<IItemDefinition, ItemStack>} 做缓存。
 * 定义实例一旦只被弱引用就会被回收 → 缓存每次都 miss（于是每次都新建 ItemStack、每次都发一次
 * 能力事件），同时 map 里堆满失效条目，{@code WeakHashMap.getTable()} 的清理遍历随条目数增长
 * （<b>每次调用比上次更慢</b>）→ 单个 server tick 卡 &gt;60s 被 ServerHangWatchdog 强杀；堆里能看到
 * 175 万 ItemStack / 174 万 AttachCapabilitiesEvent / 614 万个 Object[]。</p>
 *
 * <p>本类改用 {@code identifier()} 字符串键 + {@link ConcurrentHashMap}：强引用、O(1)、线程安全，
 * 条目数恒等于"用到的定义数"（个位数），与调用次数无关。</p>
 */
public final class DefinitionStackCache {

    private static final ConcurrentHashMap<String, Object> CACHE = new ConcurrentHashMap<String, Object>();

    private DefinitionStackCache() {
    }

    /** 取缓存的代表栈；没有或键不可用时返回 {@code null}。 */
    @SuppressWarnings("unchecked")
    public static <T> T get(String identifier) {
        return identifier == null ? null : (T) CACHE.get(identifier);
    }

    /** 存入代表栈（键/值不可用时不缓存）。 */
    public static void put(String identifier, Object representative) {
        if (identifier != null && representative != null) {
            CACHE.put(identifier, representative);
        }
    }

    /** 当前条目数（诊断用：正常是"用到的定义数"，绝不能随调用次数增长）。 */
    public static int size() {
        return CACHE.size();
    }

    /** 仅供单元测试隔离用：清空缓存。 */
    public static void clearForTest() {
        CACHE.clear();
    }
}
