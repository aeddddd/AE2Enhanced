package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess;

/**
 * 网络样板索引（按 CraftingGridCache 实例缓存，recalculateCraftingPatterns 后失效重建）.
 * <p>维护三类倒排：主输出键的快照查询（{@link #patternsFor}）、副产物生产者倒排、
 * 消费者倒排；并以 输出键 → 输入键 的键图做 Tarjan SCC 划分，作为 LP 计划器
 * 求解单元划分与成环判定（{@link #isCycleStep}）的基础.</p>
 * <p>构建与查询在线程池线程上并发执行：构建由 MixinCraftingGridCache 同步惰性触发,
 * memo 使用并发容器；键图数据构建后不可变.</p>
 */
public final class NetworkPatternIndex {

    /** canon 输出键 → 以它为非主索引输出的样板. */
    private final Map<IAEItemStack, List<ICraftingPatternDetails>> byproduct;
    /** canon 输入键 → 消费它的全部样板（单元独占原料判定:消费者是否同属一个求解单元）. */
    private final Map<IAEItemStack, List<ICraftingPatternDetails>> consumers;
    /** canon 键 → SCC 编号（边：样板输出键 → 样板输入键）. */
    private final Map<IAEItemStack, Integer> sccId;
    /** SCC 编号 → 键数(巨型分量识别:预检用其判定"蛛网子树"爆炸风险). */
    private final Map<Integer, Integer> sccSizes;
    /** SCC 编号 → 分量键集(只读;LP 模型构建器按分量收集键). */
    private final Map<Integer, List<IAEItemStack>> sccKeys;
    private final Map<IAEItemStack, Boolean> detectorMemo = new ConcurrentHashMap<>();
    private final Map<ICraftingPatternDetails, Boolean> cycleStepMemo = new ConcurrentHashMap<>();

    /** 构建时的一致性样板快照(canon 键 → 主索引样板表),求解期 patternsFor 用. */
    private final Map<IAEItemStack, List<ICraftingPatternDetails>> patternSnapshot;
    /** 发射台判定回调(快照口径,不受 recalc 重建空窗影响). */
    private final ICraftingGridCacheAccess access;

    private NetworkPatternIndex(Map<IAEItemStack, List<ICraftingPatternDetails>> byproduct,
            Map<IAEItemStack, List<ICraftingPatternDetails>> consumers,
            Map<IAEItemStack, Integer> sccId,
            Map<IAEItemStack, List<ICraftingPatternDetails>> patternSnapshot,
            ICraftingGridCacheAccess access) {
        this.byproduct = byproduct;
        this.consumers = consumers;
        this.sccId = sccId;
        this.patternSnapshot = patternSnapshot;
        this.access = access;
        Map<Integer, Integer> sizes = new HashMap<>();
        Map<Integer, List<IAEItemStack>> keys = new HashMap<>();
        for (Map.Entry<IAEItemStack, Integer> entry : sccId.entrySet()) {
            sizes.merge(entry.getValue(), 1, Integer::sum);
            keys.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }
        this.sccSizes = sizes;
        this.sccKeys = keys;
    }

    /**
     * 取网络的缓存索引（惰性构建）;非本模组缓存实现（如单元测试模拟网格）返回 null,
     * 调用方应回退到逐次扫描.
     */
    @Nullable
    public static NetworkPatternIndex of(ICraftingGrid cc) {
        if (cc instanceof ICraftingGridCacheAccess) {
            return ((ICraftingGridCacheAccess) cc).ae2enhanced$patternIndex();
        }
        return null;
    }

    /**
     * 全量构建（由 MixinCraftingGridCache 在同步块内调用）.
     * <p>并发约定：craftableItems 是就地 clear+重建的无锁 fastutil map（服务器线程持有）,
     * 重建空窗期任何活读都无法区分空窗与真空网络.故数据源只能是 recalc TAIL
     * （服务器线程、重建刚完成）固化的一致性快照,见
     * {@link ICraftingGridCacheAccess#ae2enhanced$craftableSnapshot()}.</p>
     */
    public static NetworkPatternIndex build(ICraftingGrid cc) {
        ICraftingGridCacheAccess access = (ICraftingGridCacheAccess) cc;
        // 快照在重建完成点固化,无竞态;空窗期可能读到的部分/空视图被排除在外
        return buildFromSnapshot(access.ae2enhanced$craftableSnapshot(), access);
    }

    /** 由一致性快照构建索引(快照:canon 键 → 该键主索引样板表). */
    private static NetworkPatternIndex buildFromSnapshot(
            Map<IAEItemStack, List<ICraftingPatternDetails>> snapshot, ICraftingGridCacheAccess access) {
        Map<IAEItemStack, Set<ICraftingPatternDetails>> byproductSets = new HashMap<>();
        Map<IAEItemStack, Set<ICraftingPatternDetails>> consumerSets = new HashMap<>();
        Map<IAEItemStack, Set<IAEItemStack>> adj = new HashMap<>();
        Set<ICraftingPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<IAEItemStack, List<ICraftingPatternDetails>> snapshotEntry : snapshot.entrySet()) {
            IAEItemStack craftableKey = snapshotEntry.getKey();
            for (ICraftingPatternDetails pattern : snapshotEntry.getValue()) {
                if (seen.add(pattern)) {
                    // 键图边：输出键 → 输入键（"被产生"回溯关系）
                    for (IAEItemStack output : pattern.getCondensedOutputs()) {
                        if (output == null) {
                            continue;
                        }
                        Set<IAEItemStack> targets = adj.computeIfAbsent(
                                RecursiveCraftingHelper.canon(output), k -> new LinkedHashSet<>());
                        for (IAEItemStack input : pattern.getCondensedInputs()) {
                            if (input == null || input.getStackSize() <= 0) {
                                continue;
                            }
                            targets.add(RecursiveCraftingHelper.canon(input));
                        }
                    }
                    // 消费者倒排（输入键 → 样板,身份去重）
                    for (IAEItemStack input : pattern.getCondensedInputs()) {
                        if (input == null || input.getStackSize() <= 0) {
                            continue;
                        }
                        consumerSets.computeIfAbsent(RecursiveCraftingHelper.canon(input),
                                k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(pattern);
                    }
                }
                // 副产物倒排：跳过该样板的主索引键
                for (IAEItemStack output : pattern.getCondensedOutputs()) {
                    if (output == null) {
                        continue;
                    }
                    IAEItemStack outKey = RecursiveCraftingHelper.canon(output);
                    if (outKey.equals(craftableKey)) {
                        continue;
                    }
                    byproductSets.computeIfAbsent(outKey,
                            k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(pattern);
                }
            }
        }
        Map<IAEItemStack, List<ICraftingPatternDetails>> byproduct = new HashMap<>();
        for (Map.Entry<IAEItemStack, Set<ICraftingPatternDetails>> entry : byproductSets.entrySet()) {
            List<ICraftingPatternDetails> list = new ArrayList<>(entry.getValue());
            list.sort(PATTERN_CONTENT_ORDER); // 确定性序:避免身份桶序随重建漂移传导到 LP 访问序
            byproduct.put(entry.getKey(), Collections.unmodifiableList(list));
        }
        Map<IAEItemStack, List<ICraftingPatternDetails>> consumers = new HashMap<>();
        for (Map.Entry<IAEItemStack, Set<ICraftingPatternDetails>> entry : consumerSets.entrySet()) {
            List<ICraftingPatternDetails> list = new ArrayList<>(entry.getValue());
            list.sort(PATTERN_CONTENT_ORDER);
            consumers.put(entry.getKey(), Collections.unmodifiableList(list));
        }
        return new NetworkPatternIndex(byproduct, consumers, tarjanScc(adj), snapshot, access);
    }

    /** 按内容(凝聚输出+输入)排序:同网络状态下跨索引重建保持稳定. */
    private static final java.util.Comparator<ICraftingPatternDetails> PATTERN_CONTENT_ORDER =
            java.util.Comparator.comparing(p -> java.util.Arrays.toString(p.getCondensedOutputs())
                    + "|" + java.util.Arrays.toString(p.getCondensedInputs()));

    /** 副产物生产者倒排（只读）. */
    public Map<IAEItemStack, List<ICraftingPatternDetails>> byproductMap() {
        return this.byproduct;
    }

    /**
     * canon 键的主索引样板表(构建时一致性快照,与 getCraftingFor 同语义).
     * 求解期读取一律走此方法——禁止回调 cc.getCraftingFor(recalc 重建空窗竞态).
     */
    public List<ICraftingPatternDetails> patternsFor(IAEItemStack canonKey) {
        return this.patternSnapshot.getOrDefault(canonKey, Collections.emptyList());
    }

    /**
     * 发射台判定快照(与 canEmitFor 同语义,不受 recalc 重建空窗影响).
     * 委托 access 的 volatile 快照:setEmitable 动态增量即时生效.
     */
    public boolean canEmit(IAEItemStack canonKey) {
        return this.access.ae2enhanced$canEmit(canonKey);
    }

    /** 消费某 canon 输入键的全部样板（只读;无消费者返回空表）. */
    public List<ICraftingPatternDetails> consumersOf(IAEItemStack canonKey) {
        return this.consumers.getOrDefault(canonKey, Collections.emptyList());
    }

    /** 键所属 SCC 编号（不在键图中返回 null）;供环枚举的同 SCC 剪枝. */
    @Nullable
    public Integer sccIdOf(IAEItemStack canonKey) {
        return this.sccId.get(canonKey);
    }

    /** SCC 分量的全部键（只读;不存在返回空表）;供 LP 模型构建器按分量收集键. */
    public List<IAEItemStack> keysOfScc(int id) {
        List<IAEItemStack> keys = this.sccKeys.get(id);
        return keys == null ? Collections.emptyList() : Collections.unmodifiableList(keys);
    }

    /** 键图全部键（只读视图;冷凝分层驱动器构建求解单元时枚举）. */
    public Set<IAEItemStack> keyGraphKeys() {
        return Collections.unmodifiableSet(this.sccId.keySet());
    }

    /** 键所属 SCC 的规模(键数;不在键图中返回 0);供"蛛网子树"爆炸预检. */
    public int sccSizeOf(IAEItemStack canonKey) {
        Integer id = this.sccId.get(canonKey);
        Integer size = id == null ? null : this.sccSizes.get(id);
        return size == null ? 0 : size;
    }

    /**
     * 样板是否成环步骤：某输入键与某输出键处于同一 SCC
     * （输入键可经"被产生"边回到输出键 ⇔ 同 SCC，因本样板自带 输出→输入 边）.
     */
    public boolean isCycleStep(ICraftingPatternDetails pattern) {
        Boolean memo = this.cycleStepMemo.get(pattern);
        if (memo != null) {
            return memo;
        }
        boolean result = this.computeIsCycleStep(pattern);
        this.cycleStepMemo.put(pattern, result);
        return result;
    }

    private boolean computeIsCycleStep(ICraftingPatternDetails pattern) {
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input == null || input.getStackSize() <= 0) {
                continue;
            }
            Integer inId = this.sccId.get(RecursiveCraftingHelper.canon(input));
            if (inId == null) {
                continue;
            }
            for (IAEItemStack output : pattern.getCondensedOutputs()) {
                if (output == null) {
                    continue;
                }
                if (inId.equals(this.sccId.get(RecursiveCraftingHelper.canon(output)))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** detector 判定 memo:键为 canon(请求物);结果仅依赖样板集,随索引一并失效.
     * 当前无写入方,保留供未来路由层复用 */
    @Nullable
    public Boolean detectorVerdict(IAEItemStack canonKey) {
        return this.detectorMemo.get(canonKey);
    }

    public void memoDetectorVerdict(IAEItemStack canonKey, boolean verdict) {
        this.detectorMemo.put(canonKey, verdict);
    }

    /**
     * 迭代 Tarjan SCC（合成线程无大栈保证，递归实现在大网络上会爆栈）.
     */
    private static Map<IAEItemStack, Integer> tarjanScc(Map<IAEItemStack, Set<IAEItemStack>> adj) {
        Set<IAEItemStack> vertices = new LinkedHashSet<>(adj.keySet());
        for (Set<IAEItemStack> targets : adj.values()) {
            vertices.addAll(targets);
        }
        Map<IAEItemStack, Integer> index = new HashMap<>();
        Map<IAEItemStack, Integer> low = new HashMap<>();
        Map<IAEItemStack, Integer> scc = new HashMap<>();
        ArrayDeque<IAEItemStack> stack = new ArrayDeque<>();
        Set<IAEItemStack> onStack = new HashSet<>();
        int counter = 0;
        int sccCount = 0;
        for (IAEItemStack start : vertices) {
            if (index.containsKey(start)) {
                continue;
            }
            ArrayDeque<IAEItemStack> work = new ArrayDeque<>();
            ArrayDeque<Iterator<IAEItemStack>> iters = new ArrayDeque<>();
            index.put(start, counter);
            low.put(start, counter);
            counter++;
            stack.push(start);
            onStack.add(start);
            work.push(start);
            iters.push(adj.getOrDefault(start, Collections.emptySet()).iterator());
            while (!work.isEmpty()) {
                IAEItemStack v = work.peek();
                Iterator<IAEItemStack> it = iters.peek();
                if (it.hasNext()) {
                    IAEItemStack w = it.next();
                    if (!index.containsKey(w)) {
                        index.put(w, counter);
                        low.put(w, counter);
                        counter++;
                        stack.push(w);
                        onStack.add(w);
                        work.push(w);
                        iters.push(adj.getOrDefault(w, Collections.emptySet()).iterator());
                    } else if (onStack.contains(w)) {
                        low.put(v, Math.min(low.get(v), index.get(w)));
                    }
                } else {
                    work.pop();
                    iters.pop();
                    if (!work.isEmpty()) {
                        IAEItemStack parent = work.peek();
                        low.put(parent, Math.min(low.get(parent), low.get(v)));
                    }
                    if (low.get(v).equals(index.get(v))) {
                        IAEItemStack w;
                        do {
                            w = stack.pop();
                            onStack.remove(w);
                            scc.put(w, sccCount);
                        } while (!w.equals(v));
                        sccCount++;
                    }
                }
            }
        }
        return scc;
    }
}
