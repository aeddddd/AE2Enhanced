package com.github.aeddddd.ae2enhanced.craftingplan.dag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.MECraftingInventory;
import appeng.util.item.AEItemStack;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;

import com.github.aeddddd.ae2enhanced.specialcrafting.Ae2CraftingReflect;
import com.github.aeddddd.ae2enhanced.specialcrafting.AnalysisBudget;
import com.github.aeddddd.ae2enhanced.specialcrafting.CycleBoundarySolver;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecipeRemainingResolver;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;

/**
 * DAG 拓扑执行器（1.12.2 移植）:两阶段——先物化树结构,再单趟扫描记账.
 * <p><b>1.12.2 关键差异</b>:本版没有独立计划对象,树即计划且是提交载体
 * ({@code submitJob → tree.setJob}).因此 DAG 结果必须物化为原生树:</p>
 * <ul>
 * <li>每个 DAG 节点只挂载一次（首个父节点槽位）,其余父槽位保持空叶子——
 * 共享生产在执行层经 CPU 库存池自然衔接,dive/setJob 不会重复计数;</li>
 * <li>层级由构造保证合法（dive/getAmountCrafted 要求父节点 what 是样板输出之一:
 * 子树节点 what 恒等于子 key,而子 key 就是该 DAG 节点样板的产出）;</li>
 * <li>used 记账在根节点 used 列表（初始提取,网络优先）;容器物回记模拟库存
 * （自返还按 times-1,首个循环不预贷,与 1.20.1 的 4aaa50b3 修复一致）;
 * 零网络来源的自举容器补记 missing=1（原生高水位,防零种子执行死锁）;</li>
 * <li>循环边界委托 {@link CycleBoundarySolver} 以真实 request 模拟记账,
 * 子树根替换进首个父槽位.</li>
 * </ul>
 */
public final class DagExecutor {

    /**
     * 执行结果.
     */
    public static final class Result {
        /** 缺料（规范化键 → 数量）,非空时调用方须置 simulation 标志. */
        public final Map<IAEItemStack, Long> missingItems;
        public final boolean hasCycleBoundary;

        Result(Map<IAEItemStack, Long> missingItems, boolean hasCycleBoundary) {
            this.missingItems = missingItems;
            this.hasCycleBoundary = hasCycleBoundary;
        }
    }

    /** CYCLE 节点的首个父槽位（执行阶段替换为边界子树根）. */
    private static final class ParentSlot {
        final CraftingTreeProcess parentPro;
        final CraftingTreeNode childNode;

        ParentSlot(CraftingTreeProcess parentPro, CraftingTreeNode childNode) {
            this.parentPro = parentPro;
            this.childNode = childNode;
        }
    }

    private DagExecutor() {
    }

    /**
     * @param graph 编译产物
     * @param target 根请求量
     * @param inv 模拟库存（调用方已 ignore 请求物）
     * @param job 缺料/used 记账宿主（availableCheck 已就位,供边界求解 checkUse）
     * @param rootNode 物化根节点（what = 请求物）
     * @param simulation true = 模拟趟(缺料重算):多样板节点首分支不封顶,
     *        镜像原生失败重试的"乐观幻影生产";false = 非模拟趟(分支按供给容量封顶)
     */
    public static Result execute(DagGraph graph, long target, MECraftingInventory inv,
            CraftingJob job, ICraftingGrid cc, World world, CraftingTreeNode rootNode, IActionSource src,
            boolean simulation) throws DagFallback, InterruptedException {
        Map<DagGraph.DagNode, List<CraftingTreeProcess>> prosByNode = new IdentityHashMap<>();
        Map<DagGraph.DagNode, CraftingTreeNode> terminalSlotByNode = new IdentityHashMap<>();
        Map<DagGraph.DagNode, ParentSlot> cycleSlotByNode = new IdentityHashMap<>();

        // ==================== 阶段 1:结构物化(BFS,每节点挂一次) ====================
        if (graph.root.kind == DagGraph.Kind.EMITTER) {
            throw new DagFallback("emitter_root");
        }
        Set<DagGraph.DagNode> attached = Collections.newSetFromMap(new IdentityHashMap<>());
        ArrayDeque<DagGraph.DagNode> queue = new ArrayDeque<>();
        ArrayDeque<CraftingTreeNode> parentQueue = new ArrayDeque<>();
        if (graph.root.kind == DagGraph.Kind.NORMAL) {
            queue.add(graph.root);
            parentQueue.add(rootNode);
        }
        while (!queue.isEmpty()) {
            DagGraph.DagNode node = queue.poll();
            CraftingTreeNode parentTreeNode = parentQueue.poll();
            if (!attached.add(node)) {
                continue; // 已挂载:本父槽位保持空叶子(共享生产)
            }
            // 分支列表:主分支 + 额外候选分支(顺序 = 原生 getCraftingFor 尝试序);
            // 每个分支物化独立 CraftingTreeProcess(原生多样板节点同样逐样板一个 process)
            List<ICraftingPatternDetails> branchPatterns = new ArrayList<>();
            List<List<DagGraph.Edge>> branchEdges = new ArrayList<>();
            branchPatterns.add(node.pattern);
            branchEdges.add(node.edges);
            for (DagGraph.Branch branch : node.extraBranches) {
                branchPatterns.add(branch.pattern);
                branchEdges.add(branch.edges);
            }
            List<CraftingTreeProcess> pros = new ArrayList<>(branchPatterns.size());
            for (int b = 0; b < branchPatterns.size(); b++) {
                CraftingTreeProcess pro = new CraftingTreeProcess(cc, job, branchPatterns.get(b),
                        parentTreeNode, 1);
                // 构造函数不建输入子节点(惰性),物化结构需要立即展开
                Ae2CraftingReflect.processAddProcess(pro);
                Ae2CraftingReflect.addProcessToNode(parentTreeNode, pro);
                pros.add(pro);
                for (DagGraph.Edge edge : branchEdges.get(b)) {
                    CraftingTreeNode childTreeNode = findChildNode(pro, edge.child().key);
                    if (childTreeNode == null) {
                        throw new DagFallback("child_node_missing:" + edge.child().key);
                    }
                    switch (edge.child().kind) {
                        case NORMAL:
                            queue.add(edge.child());
                            parentQueue.add(childTreeNode);
                            break;
                        case CYCLE:
                            cycleSlotByNode.putIfAbsent(edge.child(), new ParentSlot(pro, childTreeNode));
                            break;
                        case TERMINAL:
                            terminalSlotByNode.putIfAbsent(edge.child(), childTreeNode);
                            break;
                        case EMITTER:
                            throw new DagFallback("emitter_node:" + edge.child().key);
                        default:
                            throw new DagFallback("unexpected_node_kind");
                    }
                }
            }
            prosByNode.put(node, pros);
        }

        // ==================== 阶段 2:拓扑单趟记账 ====================
        Map<DagGraph.DagNode, Long> requests = new IdentityHashMap<>();
        requests.put(graph.root, target);
        Map<DagGraph.DagNode, Long> missingByNode = new IdentityHashMap<>();
        Map<IAEItemStack, Long> synthetic = new LinkedHashMap<>(); // 合成侧余额(产出+容器返还)
        Map<IAEItemStack, Long> fundedByCredit = new LinkedHashMap<>(); // 合成侧抵扣量(按 key)
        Map<IAEItemStack, Long> networkSourced = new LinkedHashMap<>(); // 网络实取量(按 key)
        Set<IAEItemStack> containerKeys = new LinkedHashSet<>(); // 收到容器返还的 key
        boolean hasCycleBoundary = false;
        long totalExtracted = 0;
        // 单趟内所有循环边界共享的分析预算(O(n³) 大整数求解总开销封顶,
        // 大 SCC 网络下超预算整单回落原生,与不可解同语义)
        AnalysisBudget analysisBudget = AnalysisBudget.solve();

        for (DagGraph.DagNode node : graph.topoOrder) {
            long need = requests.getOrDefault(node, 0L);
            if (need <= 0) {
                continue;
            }
            // 循环边界:库存/种子语义由边界求解器全权处理,不做预提取
            if (node.kind == DagGraph.Kind.CYCLE) {
                hasCycleBoundary = true;
                CraftingTreeNode subtreeRoot = node == graph.root ? rootNode
                        : new CraftingTreeNode(cc, job, node.key.copy(), null, -1, 0);
                CycleBoundarySolver.BoundaryResult boundary = CycleBoundarySolver.solveInto(cc, job, node.key,
                        need, inv, subtreeRoot, src, world, analysisBudget);
                if (boundary == CycleBoundarySolver.BoundaryResult.FALLBACK) {
                    throw new DagFallback("cycle_boundary_unsolvable:" + node.key);
                }
                if (boundary == CycleBoundarySolver.BoundaryResult.MISSING) {
                    // 天文数字边界需求:O(1) 缺料记账(对齐根路径 missingRoot 语义),
                    // 继续拓扑扫描而非整单回落原生(大网络上回落即高请求计算卡死)
                    missingByNode.merge(node, need, Long::sum);
                    continue;
                }
                if (node != graph.root) {
                    ParentSlot slot = cycleSlotByNode.get(node);
                    if (slot != null) {
                        swapChild(slot.parentPro, slot.childNode, subtreeRoot);
                    }
                }
                continue;
            }

            // 提取:网络优先——物理实取记 used(执行期 CPU 仅吃 used 预取,
            // 返还物随合成渐进可用);实取不足的部分才由合成侧余额抵扣
            // (与原生语义一致:原生计划层逐次建模容器返还,种子高水位 = 0~1)
            long credited = synthetic.getOrDefault(node.key, 0L);
            long realAvailable = Math.max(0L, invAmount(inv, node.key) - credited);
            long extracted = extract(inv, node.key, need, src);
            if (extracted > 0) {
                long fromNetwork = Math.min(extracted, realAvailable);
                long funded = extracted - fromNetwork;
                if (funded > 0) {
                    synthetic.merge(node.key, -funded, Long::sum);
                    fundedByCredit.merge(node.key, funded, Long::sum);
                }
                if (fromNetwork > 0) {
                    networkSourced.merge(node.key, fromNetwork, Long::sum);
                    totalExtracted = SaturatedMath.add(totalExtracted, fromNetwork);
                    IAEItemStack usedStack = node.key.copy();
                    usedStack.setStackSize(fromNetwork);
                    Ae2CraftingReflect.getNodeUsed(rootNode).add(usedStack);
                }
            }
            long remaining = need - extracted;
            if (remaining <= 0) {
                continue;
            }

            switch (node.kind) {
                case TERMINAL:
                    missingByNode.merge(node, remaining, Long::sum);
                    break;
                case NORMAL: {
                    if (!node.extraBranches.isEmpty()) {
                        // 多样板接管:按原生分支顺序批量分配(非模拟趟容量封顶)
                        executeMultiBranch(node, remaining, inv, requests, missingByNode, synthetic,
                                prosByNode.get(node), simulation, src);
                        break;
                    }
                    long times = SaturatedMath.ceilDiv(remaining, node.outputPerCraft);
                    for (DagGraph.Edge edge : node.edges) {
                        long childRequest = SaturatedMath.multiply(edge.perCraft(), times);
                        requests.merge(edge.child(), childRequest, SaturatedMath::add);
                    }
                    // 返还物回记:消耗 N 份输入回记返还——自返还(返还物本身是本样板
                    // 的输入,催化剂型)按 times-1 计(最后一份无法自供,保住种子提取);
                    // 跨样板复用的返还物按全额 times 计(下游拓扑序后提取);零网络来源
                    // 的自举返还由扫描后的高水位修正补记 missing=1(对齐原生).
                    // 可合成样板以配方 getRemainingItems 为准(覆盖 CrT reuse 等
                    // 不消耗实现);其余回退 Item 容器 API(与旧逻辑一致)
                    Map<IAEItemStack, Long> remainingTable = RecipeRemainingResolver
                            .remainingPerCraft(node.pattern);
                    if (remainingTable != null) {
                        for (Map.Entry<IAEItemStack, Long> entry : remainingTable.entrySet()) {
                            long perCraft = entry.getValue();
                            if (perCraft <= 0) {
                                continue;
                            }
                            boolean selfReturn = containsInput(node.pattern, entry.getKey());
                            long creditTimes = selfReturn ? times - 1 : times;
                            if (creditTimes <= 0) {
                                continue;
                            }
                            long credit = SaturatedMath.multiply(perCraft, creditTimes);
                            IAEItemStack creditStack = entry.getKey().copy();
                            creditStack.setStackSize(credit);
                            inv.injectItems(creditStack, Actionable.MODULATE, src);
                            IAEItemStack containerKey = RecursiveCraftingHelper.canon(creditStack);
                            synthetic.merge(containerKey, credit, Long::sum);
                            containerKeys.add(containerKey);
                        }
                    } else {
                        for (IAEItemStack input : node.pattern.getCondensedInputs()) {
                            if (input == null) {
                                continue;
                            }
                            Item item = input.getItem();
                            ItemStack def = input.getDefinition();
                            if (item == null || !item.hasContainerItem(def)) {
                                continue;
                            }
                            ItemStack containerStack = item.getContainerItem(def);
                            if (containerStack.isEmpty()) {
                                continue;
                            }
                            IAEItemStack containerAe = AEItemStack.fromItemStack(containerStack);
                            if (containerAe == null) {
                                continue;
                            }
                            boolean selfReturn = containsInput(node.pattern, containerAe);
                            long creditTimes = selfReturn ? times - 1 : times;
                            if (creditTimes <= 0) {
                                continue;
                            }
                            containerAe = containerAe.copy();
                            long credit = SaturatedMath.multiply(input.getStackSize(), creditTimes);
                            containerAe.setStackSize(credit);
                            inv.injectItems(containerAe, Actionable.MODULATE, src);
                            IAEItemStack containerKey = RecursiveCraftingHelper.canon(containerAe);
                            synthetic.merge(containerKey, credit, Long::sum);
                            containerKeys.add(containerKey);
                        }
                    }
                    for (IAEItemStack output : node.pattern.getCondensedOutputs()) {
                        if (output == null) {
                            continue;
                        }
                        IAEItemStack out = output.copy();
                        out.setStackSize(SaturatedMath.multiply(output.getStackSize(), times));
                        inv.injectItems(out, Actionable.MODULATE, src);
                        synthetic.merge(RecursiveCraftingHelper.canon(out), out.getStackSize(), Long::sum);
                    }
                    List<CraftingTreeProcess> pros = prosByNode.get(node);
                    if (pros == null) {
                        throw new DagFallback("process_not_materialized:" + node.key);
                    }
                    Ae2CraftingReflect.setProcessCrafts(pros.get(0), times);
                    break;
                }
                default:
                    throw new DagFallback("unexpected_node_kind");
            }
        }

        // 种子高水位修正(对齐原生逐次循环):某容器类型的消耗全部由返还抵扣
        // (网络零实取)且无样板可合成时,首份无法自供——原生在首个失败迭代记
        // missing=1 使计划不可提交,避免零种子 CPU 执行死锁
        // (1.12.2 CPU 仅从 CPU 库存取料且不按依赖序执行)
        for (IAEItemStack containerKey : containerKeys) {
            if (fundedByCredit.getOrDefault(containerKey, 0L) <= 0
                    || networkSourced.getOrDefault(containerKey, 0L) > 0) {
                continue;
            }
            for (DagGraph.DagNode node : graph.topoOrder) {
                if (node.kind == DagGraph.Kind.TERMINAL && containerKey.equals(node.key)) {
                    missingByNode.merge(node, 1L, Long::sum);
                    break;
                }
            }
        }

        // bytes 近似:初始提取总量(合成次数的 8 倍由 dive 经 crafts×8 另行记账)
        Ae2CraftingReflect.setNodeBytes(rootNode, totalExtracted);

        // 缺料回填到对应树节点(原生 getPlan 逐节点输出 missing 条目)
        Map<IAEItemStack, Long> missingItems = new LinkedHashMap<>();
        for (Map.Entry<DagGraph.DagNode, Long> entry : missingByNode.entrySet()) {
            DagGraph.DagNode node = entry.getKey();
            long amount = entry.getValue();
            missingItems.merge(node.key, amount, Long::sum);
            CraftingTreeNode slot;
            if (node == graph.root) {
                slot = rootNode;
            } else {
                slot = terminalSlotByNode.get(node);
                if (slot == null) {
                    // 循环边界缺料:回填到物化阶段的占位子节点
                    ParentSlot parentSlot = cycleSlotByNode.get(node);
                    slot = parentSlot == null ? null : parentSlot.childNode;
                }
            }
            if (slot != null) {
                Ae2CraftingReflect.setNodeMissing(slot, amount);
            }
        }
        return new Result(missingItems, hasCycleBoundary);
    }

    /**
     * 多样板节点执行(移植自 1.20.1,修复"多样板 key × 极大数量"的 O(数量) 陷阱):
     * 镜像原生多分支语义——按 {@code getCraftingFor} 顺序"分支 1 尽力 → 分支 2",
     * 但把逐次 {@code request(child,1)} 收敛为<b>按供给容量整批</b>:
     * 分支可执行次数 = min(⌈剩余/单次产出⌉, min_e (supplyCap(子) − 已提交需求)/perCraft).
     * <p>容量截断仅在非模拟趟启用(真实"分支尽力"语义);模拟趟镜像原生"乐观幻影生产":
     * 分支 1 包揽全部剩余,缺料沿其原料子树在终端浮现(分支 2 不参与).
     * 容量方向保守(只可能低估):低估只会让更多量落到后续分支,不产生虚假缺料.</p>
     */
    private static void executeMultiBranch(DagGraph.DagNode node, long remaining,
            MECraftingInventory inv, Map<DagGraph.DagNode, Long> requests,
            Map<DagGraph.DagNode, Long> missingByNode, Map<IAEItemStack, Long> synthetic,
            List<CraftingTreeProcess> pros, boolean simulation, IActionSource src) {
        // 本节点本次评估专用 memo:supplyCap 读取的是当前库存,多节点间不复用
        // (库存随执行变化,陈旧 memo 会高估容量、把缺料错误地压在前序分支)
        Map<DagGraph.DagNode, Long> multiMemo = new IdentityHashMap<>();
        for (int b = 0; b < pros.size() && remaining > 0; b++) {
            ICraftingPatternDetails pattern;
            long outPer;
            List<DagGraph.Edge> edges;
            if (b == 0) {
                pattern = node.pattern;
                outPer = node.outputPerCraft;
                edges = node.edges;
            } else {
                DagGraph.Branch branch = node.extraBranches.get(b - 1);
                pattern = branch.pattern;
                outPer = branch.outPer;
                edges = branch.edges;
            }
            long times = SaturatedMath.ceilDiv(remaining, outPer);
            if (!simulation) {
                for (DagGraph.Edge edge : edges) {
                    long available = SaturatedMath.add(supplyCap(edge.child(), inv, multiMemo),
                            -requests.getOrDefault(edge.child(), 0L));
                    times = Math.min(times, Math.max(0L, available / edge.perCraft()));
                }
            }
            if (times <= 0) {
                continue;
            }
            for (DagGraph.Edge edge : edges) {
                requests.merge(edge.child(), SaturatedMath.multiply(edge.perCraft(), times),
                        SaturatedMath::add);
            }
            // 产出注入(含副产物)+ 合成侧余额记账(与单分支路径一致;
            // 多分支节点经编译保证无容器输入,无需容器回记)
            for (IAEItemStack output : pattern.getCondensedOutputs()) {
                if (output == null) {
                    continue;
                }
                IAEItemStack out = output.copy();
                out.setStackSize(SaturatedMath.multiply(output.getStackSize(), times));
                inv.injectItems(out, Actionable.MODULATE, src);
                synthetic.merge(RecursiveCraftingHelper.canon(out), out.getStackSize(), Long::sum);
            }
            Ae2CraftingReflect.setProcessCrafts(pros.get(b), times);
            remaining -= SaturatedMath.multiply(times, outPer);
        }
        if (remaining > 0) {
            // 仅非模拟趟可达(模拟趟首分支不封顶必包揽);调用方据此触发模拟趟重算
            missingByNode.merge(node, remaining, Long::sum);
        }
    }

    /**
     * 节点供给上限(多样板分支容量用):当前模拟库存(含已注入的合成侧余额)
     * + 各输入供给上限递归推出的可产量(多样板节点为各分支之和);
     * 发射台/循环边界按乐观无界.
     * 方向保证:只可能高估,高估导致的超计划在终端以缺料形式浮现,
     * 不会产出"看似可行实际缺料"的计划.
     */
    private static long supplyCap(DagGraph.DagNode node, MECraftingInventory inv,
            Map<DagGraph.DagNode, Long> memo) {
        Long cached = memo.get(node);
        if (cached != null) {
            return cached;
        }
        long cap;
        switch (node.kind) {
            case EMITTER:
            case CYCLE:
                cap = Long.MAX_VALUE / 4;
                break;
            case TERMINAL:
                cap = invAmount(inv, node.key);
                break;
            case NORMAL: {
                long acc = invAmount(inv, node.key);
                acc = SaturatedMath.add(acc, branchCap(node.outputPerCraft, node.edges, inv, memo));
                for (DagGraph.Branch branch : node.extraBranches) {
                    acc = SaturatedMath.add(acc, branchCap(branch.outPer, branch.edges, inv, memo));
                }
                cap = acc;
                break;
            }
            default:
                cap = 0;
        }
        memo.put(node, cap);
        return cap;
    }

    /** 单分支可产量:min_e supplyCap(子)/perCraft × 单次产出. */
    private static long branchCap(long outPer, List<DagGraph.Edge> edges, MECraftingInventory inv,
            Map<DagGraph.DagNode, Long> memo) {
        long crafts = Long.MAX_VALUE / 4;
        for (DagGraph.Edge edge : edges) {
            crafts = Math.min(crafts, supplyCap(edge.child(), inv, memo) / edge.perCraft());
        }
        return SaturatedMath.multiply(crafts, outPer);
    }

    /** 在 pro 的输入子节点中找到 what 等于 key 的节点(挂载点). */
    private static CraftingTreeNode findChildNode(CraftingTreeProcess pro, IAEItemStack key) {
        for (CraftingTreeNode child : Ae2CraftingReflect.getProcessNodes(pro).keySet()) {
            IAEItemStack what = Ae2CraftingReflect.getNodeWhat(child);
            if (what != null && key.equals(what)) {
                return child;
            }
        }
        return null;
    }

    /** 把父 process 的子槽位从占位节点替换为边界子树根（保持 perCraft 计数值）. */
    private static void swapChild(CraftingTreeProcess parentPro, CraftingTreeNode oldChild,
            CraftingTreeNode newChild) {
        Object2LongArrayMap<CraftingTreeNode> nodes = Ae2CraftingReflect.getProcessNodes(parentPro);
        long value = nodes.getLong(oldChild);
        nodes.removeLong(oldChild);
        nodes.put(newChild, value);
    }

    /** 模拟库存中某 key 的当前总量（含网络余量与已注入的合成侧余额）. */
    private static long invAmount(MECraftingInventory inv, IAEItemStack key) {
        IAEItemStack entry = inv.getItemList().findPrecise(key);
        return entry == null ? 0L : entry.getStackSize();
    }

    /** 从模拟库存提取（精确匹配）,返回实际提取量. */
    private static long extract(MECraftingInventory inv, IAEItemStack key, long amount, IActionSource src) {
        IAEItemStack request = key.copy();
        request.setStackSize(amount);
        IAEItemStack result = inv.extractItems(request, Actionable.MODULATE, src);
        return result == null ? 0 : result.getStackSize();
    }

    /** 容器键是否同时是本样板的输入（自返还/催化剂型判定）. */
    private static boolean containsInput(ICraftingPatternDetails pattern, IAEItemStack containerKey) {
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            if (input != null && containerKey.equals(input)) {
                return true;
            }
        }
        return false;
    }
}
