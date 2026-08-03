package com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;

/**
 * 大规模交叉合成图生成器(性能基准用):
 * <ul>
 * <li>主体:分层交叉普通合成——每层 width 个物品、每物品单一样板、
 * 扇入 [minFanIn,maxFanIn](≤81,AE2 处理样板输入槽上限)个上一层的随机输入
 * (跨父共享子树,原生递归树按路径展开,DAG 按 key 合并);</li>
 * <li>数量:单输入数量对数均匀分布于 [1,maxAmount](可达 {@link Integer#MAX_VALUE}),
 * 并按"需求预算"钳制——任一物品的聚合需求(路径加权和)精确地不超过
 * 2×{@link #COST_CAP},保证双方引擎 long 记账不溢出且原料库存足够;</li>
 * <li>副产物:byproductChance 概率给样板附带 1~2 个<b>专属垃圾副产物</b>
 * (不被任何样板消耗,避免"副产物供兄弟分支"的拓扑序依赖,保持逐字段 parity);</li>
 * <li>附带子环:loopClusters 个催化环(1A→1X+1B、1B→1A,种子 1A),X 挂到根输入;</li>
 * <li>附带特殊合成:dupStations 个净产出自引用(1S→2S,经 D←1S 挂到根输入,种子 1S);</li>
 * <li>键:<b>不同原版物品 × 少量 NBT 变体</b>——{@code KeyCounter.findFuzzy}
 * 按物品类型子索引扫描全部变体,同物品海量变体会让模板枚举退化;
 * 本生成器每物品最多 ceil(总键数/物品数) 个变体(通常 ≤3),扫描开销 O(变体数).</li>
 * </ul>
 */
public final class CrossCraftingGraphGenerator {

    /** 原料库存:远大于任何可达需求(任一物品聚合成本 ≤ {@link #COST_CAP} = 5e15 ≪ 本值). */
    private static final long RAW_STOCK = Long.MAX_VALUE / 8;

    /**
     * 单物品聚合成本(路径加权原料需求)预算:5e15.
     * 加输入时按运行和钳制:amt ≤ (COST_CAP - sum)/cost[child],预算装不下的输入
     * <b>直接截断</b>(深层高成本分支扇入被动收窄;数量下限 1 不可作预算地板,
     * 否则宽扇入跨层复利会冲破库存上界)——不变式:任一物品成本 ≤ COST_CAP,
     * 精确无饱和,保证双方引擎 long 记账不溢出且原料库存足够;
     * 大数量(至 int 上限)只在预算允许处(浅层/低成本分支)出现.
     */
    private static final long COST_CAP = 5_000_000_000_000_000L;

    /**
     * 图参数.
     *
     * @param minFanIn 每样板输入种类下限(≥1)
     * @param maxFanIn 每样板输入种类上限(≤81,实际受上一层宽度限制)
     * @param maxAmount 单输入数量上限(≤{@link Integer#MAX_VALUE}),对数均匀 + 需求预算钳制
     * @param byproductChance 每样板附带 1~2 个专属垃圾副产物的概率 [0,1]
     */
    public record Params(
            int layers, int width,
            int minFanIn, int maxFanIn,
            long maxAmount,
            double byproductChance,
            int loopClusters, int dupStations,
            long seed) {
        public Params {
            if (minFanIn < 1 || maxFanIn > 81 || minFanIn > maxFanIn) {
                throw new IllegalArgumentException("扇入范围非法: [" + minFanIn + "," + maxFanIn + "]");
            }
            if (maxAmount < 1 || maxAmount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("数量上限非法: " + maxAmount);
            }
        }

        /** 标准参数:固定扇入、数量 1..3、无副产物. */
        public static Params standard(int layers, int width, int fanIn, int loopClusters, int dupStations,
                long seed) {
            return new Params(layers, width, fanIn, fanIn, 3, 0, loopClusters, dupStations, seed);
        }
    }

    /**
     * @param env 图所在模拟环境(含全部样板与库存)
     * @param root 根请求物
     * @param unitCost 每单位根产物的原料消耗(路径加权聚合需求,饱和钳制;AE 标准字节主项;
     * 环/特殊通道净消耗仅种子,不计)
     * @param normalPatterns 普通样板数(含根)
     * @param maxInputAmount 全图最大单输入数量(检验"大数量"是否真实出现)
     * @param loopSeedKeys 催化环种子(A)
     * @param loopOutputKeys 催化环环外产物(X)
     * @param dupSeedKeys 自引用增殖种子(S)
     * @param dupChainKeys 特殊链中间物(D,根直接消耗)
     */
    public record Generated(
            SimulationEnv env,
            AEItemKey root,
            long unitCost,
            int normalPatterns,
            long maxInputAmount,
            List<AEItemKey> loopSeedKeys,
            List<AEItemKey> loopOutputKeys,
            List<AEItemKey> dupSeedKeys,
            List<AEItemKey> dupChainKeys) {
    }

    /** 兼容入口:标准参数(固定扇入、数量 1..3、无副产物). */
    public static Generated generate(int layers, int width, int fanIn, int loopClusters, int dupStations,
            long randomSeed) {
        return generate(Params.standard(layers, width, fanIn, loopClusters, dupStations, randomSeed));
    }

    public static Generated generate(Params p) {
        var rng = new Random(p.seed());
        var env = new SimulationEnv();
        // 每单位该物品(沿全部父路径加权)折合的原料数,饱和钳制;用于反推请求量与数量预算
        var cost = new HashMap<AEItemKey, Long>();
        var items = itemPool();
        int[] cursor = { 0 };
        long[] maxInputAmount = { 1 };
        var normalPatterns = 0;

        // 第 0 层:原料,全部巨额库存
        var previous = new AEItemKey[p.width()];
        for (int i = 0; i < p.width(); i++) {
            previous[i] = key(items, cursor);
            env.addStoredItem(previous[i], RAW_STOCK);
            cost.put(previous[i], 1L);
        }

        // 第 1..layers 层:交叉普通合成(输入为上一层的无放回随机抽样)
        for (int k = 1; k <= p.layers(); k++) {
            var current = new AEItemKey[p.width()];
            for (int i = 0; i < p.width(); i++) {
                current[i] = key(items, cursor);
                var byproducts = rollByproducts(rng, p.byproductChance(), items, cursor);
                var outputs = new GenericStack[1 + byproducts.length];
                outputs[0] = new GenericStack(current[i], 1);
                System.arraycopy(byproducts, 0, outputs, 1, byproducts.length);
                var builder = new ProcessingPatternBuilder(outputs);
                long nodeCost = 0;
                int fanIn = p.minFanIn() == p.maxFanIn() ? p.minFanIn()
                        : p.minFanIn() + rng.nextInt(p.maxFanIn() - p.minFanIn() + 1);
                for (var pick : sample(rng, p.width(), Math.min(fanIn, p.width()))) {
                    var child = previous[pick];
                    long childCost = cost.get(child);
                    long allowance = (COST_CAP - nodeCost) / childCost;
                    if (allowance < 1) {
                        continue; // 预算耗尽:截断(首个输入必然装入,见 COST_CAP 不变式)
                    }
                    long amount = drawAmount(rng, p.maxAmount(), allowance);
                    builder.addPreciseInput(1, new GenericStack(child, amount));
                    nodeCost += amount * childCost; // ≤ COST_CAP,精确
                    if (amount > maxInputAmount[0]) {
                        maxInputAmount[0] = amount;
                    }
                }
                cost.put(current[i], nodeCost);
                env.addPattern(builder.build());
                normalPatterns++;
            }
            previous = current;
        }

        // 附带子环:催化环(1A→1X+1B、1B→1A,种子 1A),形状同 M 组催化通道
        var loopSeeds = new ArrayList<AEItemKey>();
        var loopOutputs = new ArrayList<AEItemKey>();
        for (int j = 0; j < p.loopClusters(); j++) {
            var a = key(items, cursor);
            var b = key(items, cursor);
            var x = key(items, cursor);
            env.addPattern(new ProcessingPatternBuilder(new GenericStack(x, 1), new GenericStack(b, 1))
                    .addPreciseInput(1, new GenericStack(a, 1)).build());
            env.addPattern(new ProcessingPatternBuilder(new GenericStack(a, 1))
                    .addPreciseInput(1, new GenericStack(b, 1)).build());
            env.addStoredItem(a, 1);
            loopSeeds.add(a);
            loopOutputs.add(x);
        }

        // 附带特殊合成:净产出自引用(1S→2S,经 D←1S 挂根,种子 1S),形状同 M 组深层 dup 通道
        var dupSeeds = new ArrayList<AEItemKey>();
        var dupChains = new ArrayList<AEItemKey>();
        for (int j = 0; j < p.dupStations(); j++) {
            var s = key(items, cursor);
            var d = key(items, cursor);
            env.addPattern(new ProcessingPatternBuilder(new GenericStack(d, 1))
                    .addPreciseInput(1, new GenericStack(s, 1)).build());
            env.addPattern(new ProcessingPatternBuilder(new GenericStack(s, 2))
                    .addPreciseInput(1, new GenericStack(s, 1)).build());
            env.addStoredItem(s, 1);
            dupSeeds.add(s);
            dupChains.add(d);
        }

        // 根:顶层普通输入 + 全部环产物 + 全部特殊链产物(普通输入在前,
        // 原生失败/模拟两趟尝试均先展开主体大树,计时不被特殊通道提前截断)
        var root = key(items, cursor);
        var rootBuilder = new ProcessingPatternBuilder(new GenericStack(root, 1));
        long rootCost = 0;
        for (var pick : sample(rng, p.width(), Math.min(p.minFanIn(), p.width()))) {
            var child = previous[pick];
            long childCost = cost.get(child);
            long allowance = (COST_CAP - rootCost) / childCost;
            if (allowance < 1) {
                continue; // 预算耗尽:截断
            }
            long amount = drawAmount(rng, p.maxAmount(), allowance);
            rootBuilder.addPreciseInput(1, new GenericStack(child, amount));
            rootCost += amount * childCost; // ≤ COST_CAP,精确
            if (amount > maxInputAmount[0]) {
                maxInputAmount[0] = amount;
            }
        }
        for (var x : loopOutputs) {
            rootBuilder.addPreciseInput(1, new GenericStack(x, 1));
        }
        for (var d : dupChains) {
            rootBuilder.addPreciseInput(1, new GenericStack(d, 1));
        }
        env.addPattern(rootBuilder.build());
        normalPatterns++;

        return new Generated(env, root, rootCost, normalPatterns, maxInputAmount[0],
                loopSeeds, loopOutputs, dupSeeds, dupChains);
    }

    /** 按概率掷 1~2 个专属垃圾副产物(不被任何样板消耗,保持 parity 无拓扑序依赖). */
    private static GenericStack[] rollByproducts(Random rng, double chance, List<Item> items, int[] cursor) {
        if (chance <= 0 || rng.nextDouble() >= chance) {
            return new GenericStack[0];
        }
        var out = new GenericStack[1 + rng.nextInt(2)];
        for (int i = 0; i < out.length; i++) {
            out[i] = new GenericStack(key(items, cursor), 1 + rng.nextInt(3));
        }
        return out;
    }

    /** 对数均匀抽取 [1, min(maxAmount, allowance)]:小数量为主,大数量可达 int 上限. */
    private static long drawAmount(Random rng, long maxAmount, long allowance) {
        long cap = Math.min(maxAmount, allowance);
        if (cap <= 1) {
            return 1;
        }
        long v = (long) Math.exp(rng.nextDouble() * Math.log((double) cap));
        return Math.max(1, Math.min(cap, v));
    }

    /** 无放回抽样 count 个 [0,bound) 下标. */
    private static int[] sample(Random rng, int bound, int count) {
        var picks = new int[count];
        var used = new HashSet<Integer>();
        for (int i = 0; i < count; i++) {
            int v;
            do {
                v = rng.nextInt(bound);
            } while (!used.add(v));
            picks[i] = v;
        }
        return picks;
    }

    /**
     * 键分配:物品池轮询 + 少量 NBT 变体(池用尽后才 +1 变体).
     * 每物品类型的变体极少(通常 ≤3),{@code KeyCounter.findFuzzy} 按物品类型
     * 子索引扫描的开销保持 O(变体数),既突破原版物品数量上限,又不让模板枚举退化.
     */
    private static AEItemKey key(List<Item> pool, int[] cursor) {
        int index = cursor[0]++;
        var stack = new ItemStack(pool.get(index % pool.size()));
        int variant = index / pool.size();
        if (variant > 0) {
            stack.getOrCreateTag().putInt("xgv", variant);
        }
        return AEItemKey.of(stack);
    }

    /** 原版物品池(跳过空气);注册表顺序稳定,键集可复现.键数超出池大小时由 NBT 变体补足(见 key). */
    private static List<Item> itemPool() {
        var items = new ArrayList<Item>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                items.add(item);
            }
        }
        if (items.isEmpty()) {
            throw new IllegalStateException("原版物品注册表为空,无法建图");
        }
        return items;
    }
}
