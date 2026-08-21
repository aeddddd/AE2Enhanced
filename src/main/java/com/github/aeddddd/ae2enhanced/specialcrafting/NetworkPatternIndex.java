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
 * <ul>
 * <li>键图 SCC（迭代 Tarjan）:{@link #isCycleStep} 的 O(输入×输出) 查表，
 * 取代原先逐节点 budget=512 的 DFS（旧实现 O(节点数×512×样板扫描),
 * 是数千节点计划编译的主瓶颈）;</li>
 * <li>副产物生产者倒排：全样板扫描一次建成，供 {@link CycleAnalyzer.ProducerIndex} 共享
 * （旧实现每个请求/每个候选样板各建一次）;</li>
 * <li>detector 判定 memo:{@code mayInvolveSpecialRecipes} 结果按请求键记忆——
 * 判定只依赖样板集（与库存无关）,样板集不变即可复用.</li>
 * </ul>
 * 计算在线程池线程上并发执行：构建由 MixinCraftingGridCache 同步惰性触发,
 * memo 使用并发容器；键图数据构建后不可变.
 */
public final class NetworkPatternIndex {

    /** canon 输出键 → 以它为非主索引输出的样板（与旧 ProducerIndex.byproductIndex 同语义）. */
    private final Map<IAEItemStack, List<ICraftingPatternDetails>> byproduct;
    /** canon 键 → SCC 编号（边：样板输出键 → 样板输入键）. */
    private final Map<IAEItemStack, Integer> sccId;
    private final Map<IAEItemStack, Boolean> detectorMemo = new ConcurrentHashMap<>();
    private final Map<ICraftingPatternDetails, Boolean> cycleStepMemo = new ConcurrentHashMap<>();
    /** 环分析 memo:环签名 → 分析结果(含 null=已确认不可解);随样板集一并失效. */
    private final Map<CycleAnalyzer.CycleSignature, java.util.Optional<CycleAnalyzer.Analysis>> analysisMemo = new ConcurrentHashMap<>();

    private NetworkPatternIndex(Map<IAEItemStack, List<ICraftingPatternDetails>> byproduct,
            Map<IAEItemStack, Integer> sccId) {
        this.byproduct = byproduct;
        this.sccId = sccId;
    }

    /**
     * 取网络的缓存索引（惰性构建）;非本模组缓存实现（如单元测试模拟网格）返回 null,
     * 调用方应回退到逐次扫描的旧路径.
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
     * 注：CraftingGridCache.getCraftingFor 实现为纯 map 查询，不使用 world 参数，故传 null.
     */
    public static NetworkPatternIndex build(ICraftingGrid cc) {
        ICraftingGridCacheAccess access = (ICraftingGridCacheAccess) cc;
        Map<IAEItemStack, Set<ICraftingPatternDetails>> byproductSets = new HashMap<>();
        Map<IAEItemStack, Set<IAEItemStack>> adj = new HashMap<>();
        Set<ICraftingPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IAEItemStack craftable : access.ae2enhanced$craftableKeys()) {
            IAEItemStack craftableKey = RecursiveCraftingHelper.canon(craftable);
            for (ICraftingPatternDetails pattern : cc.getCraftingFor(craftable, null, -1, null)) {
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
                }
                // 副产物倒排（与旧实现一致：跳过该样板的主索引键）
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
            byproduct.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return new NetworkPatternIndex(byproduct, tarjanScc(adj));
    }

    /** 副产物生产者倒排（只读）. */
    public Map<IAEItemStack, List<ICraftingPatternDetails>> byproductMap() {
        return this.byproduct;
    }

    /**
     * 样板是否成环步骤：某输入键与某输出键处于同一 SCC
     * （输入键可经"被产生"边回到输出键 ⇔ 同 SCC，因本样板自带 输出→输入 边）.
     * 与旧 budget DFS 语义等价（且不受 512 截断影响）.
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

    /** detector 判定 memo:键为 canon(请求物);结果仅依赖样板集,随索引一并失效. */
    @Nullable
    public Boolean detectorVerdict(IAEItemStack canonKey) {
        return this.detectorMemo.get(canonKey);
    }

    public void memoDetectorVerdict(IAEItemStack canonKey, boolean verdict) {
        this.detectorMemo.put(canonKey, verdict);
    }

    /**
     * 环分析 memo:签名命中即复用(含"已确认不可解"的空结果),未命中调用 solver
     * 计算并记忆.Analysis 内部数组调用方只读,可安全共享.
     */
    @Nullable
    CycleAnalyzer.Analysis analysisMemo(CycleAnalyzer.CycleSignature signature,
            java.util.function.Supplier<CycleAnalyzer.Analysis> solver) {
        java.util.Optional<CycleAnalyzer.Analysis> cached = this.analysisMemo.get(signature);
        if (cached != null) {
            return cached.orElse(null);
        }
        CycleAnalyzer.Analysis solved = solver.get();
        this.analysisMemo.putIfAbsent(signature, java.util.Optional.ofNullable(solved));
        return solved;
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
