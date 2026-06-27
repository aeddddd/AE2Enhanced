package com.github.aeddddd.ae2enhanced.recycler;

import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 维护“机器位置 → 回收节点 handler”的绑定关系，用于机器产物源头直注。
 *
 * <p>当机器完成运行、产物即将写入输出槽时，通过本注册表找到负责该机器的回收节点，
 * 并尝试把产物直接注入其所在网络的超维度仓储中枢。</p>
 */
public class RecyclerBindingRegistry {

    private static final RecyclerBindingRegistry INSTANCE = new RecyclerBindingRegistry();

    public static RecyclerBindingRegistry getInstance() {
        return INSTANCE;
    }

    private final Map<TargetManager.TargetRef, Entry> exactMap = new ConcurrentHashMap<>();
    // 辅助索引：dimId -> BlockPos -> Entry 列表（忽略 face，方便机器 Hook 查询）
    private final Map<Integer, Map<BlockPos, List<Entry>>> byPosition = new ConcurrentHashMap<>();

    private RecyclerBindingRegistry() {
    }

    /**
     * 注册一个绑定关系。
     *
     * @param ref     机器位置引用
     * @param handler 负责回收的 RecyclerNetworkHandler
     */
    public void register(TargetManager.TargetRef ref, RecyclerNetworkHandler handler) {
        unregister(ref); // 先移除旧的同名绑定
        Entry entry = new Entry(ref, handler);
        exactMap.put(ref, entry);
        byPosition
                .computeIfAbsent(ref.dimId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(ref.pos, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(entry);
    }

    /**
     * 注销一个绑定关系。
     */
    public void unregister(TargetManager.TargetRef ref) {
        Entry removed = exactMap.remove(ref);
        if (removed == null) return;

        Map<BlockPos, List<Entry>> dimMap = byPosition.get(ref.dimId);
        if (dimMap == null) return;

        List<Entry> list = dimMap.get(ref.pos);
        if (list == null) return;

        list.remove(removed);
        if (list.isEmpty()) {
            dimMap.remove(ref.pos);
        }
        if (dimMap.isEmpty()) {
            byPosition.remove(ref.dimId);
        }
    }

    /**
     * 更新某个绑定当前缓存的超维度物品适配器。
     */
    public void updateAdapter(TargetManager.TargetRef ref, @Nullable ItemStorageAdapter adapter) {
        Entry entry = exactMap.get(ref);
        if (entry != null) {
            entry.cachedAdapter = adapter;
        }
    }

    /**
     * 根据维度+位置查找绑定项，忽略 face。
     *
     * @return 第一个 handler 仍存活的 Entry；没有则返回 null
     */
    @Nullable
    public Entry find(int dimId, BlockPos pos) {
        Map<BlockPos, List<Entry>> dimMap = byPosition.get(dimId);
        if (dimMap == null) return null;

        List<Entry> list = dimMap.get(pos);
        if (list == null || list.isEmpty()) return null;

        for (Entry entry : list) {
            if (entry.getHandler() != null) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 清除某个 handler 的所有绑定（通常在回收节点失效/解绑时调用）。
     */
    public void unregisterAll(RecyclerNetworkHandler handler) {
        List<TargetManager.TargetRef> toRemove = new ArrayList<>();
        for (Map.Entry<TargetManager.TargetRef, Entry> e : exactMap.entrySet()) {
            if (e.getValue().getHandler() == handler) {
                toRemove.add(e.getKey());
            }
        }
        for (TargetManager.TargetRef ref : toRemove) {
            unregister(ref);
        }
    }

    /**
     * 绑定项。
     */
    public static final class Entry {
        public final TargetManager.TargetRef ref;
        private final WeakReference<RecyclerNetworkHandler> handlerRef;
        public volatile ItemStorageAdapter cachedAdapter;

        public Entry(TargetManager.TargetRef ref, RecyclerNetworkHandler handler) {
            this.ref = ref;
            this.handlerRef = new WeakReference<>(handler);
        }

        @Nullable
        public RecyclerNetworkHandler getHandler() {
            return handlerRef.get();
        }
    }
}
