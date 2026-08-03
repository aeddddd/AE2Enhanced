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
 * 扇入 fanIn 个上一层的随机输入(跨父共享子树,原生递归树按路径展开,
 * DAG 按 key 合并,二者节点数差距即加速来源);</li>
 * <li>附带子环:loopClusters 个催化环(1A→1X+1B、1B→1A,种子 1A),X 挂到根输入;</li>
 * <li>附带特殊合成:dupStations 个净产出自引用(1S→2S,经 D←1S 挂到根输入,种子 1S);</li>
 * <li>键:<b>必须使用互不相同的原版物品</b>而非同物品 NBT 变体——
 * {@code KeyCounter.findFuzzy} 按物品类型子索引扫描全部变体,
 * 同物品 NBT 键会让原生逐节点模板枚举退化为 O(变体数),污染基准.</li>
 * </ul>
 */
public final class CrossCraftingGraphGenerator {

    /** 原料库存:远大于任何可达需求(最深原料需求 ≤ 目标字节 ≈ 1e14). */
    private static final long RAW_STOCK = Long.MAX_VALUE / 8;

    /**
     * @param env 图所在模拟环境(含全部样板与库存)
     * @param root 根请求物
     * @param unitCost 每单位根产物的原料消耗(路径加权,AE 标准字节主项;环/特殊通道净消耗仅种子,不计)
     * @param normalPatterns 普通样板数(含根)
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
            List<AEItemKey> loopSeedKeys,
            List<AEItemKey> loopOutputKeys,
            List<AEItemKey> dupSeedKeys,
            List<AEItemKey> dupChainKeys) {
    }

    public static Generated generate(int layers, int width, int fanIn, int loopClusters, int dupStations,
            long randomSeed) {
        var rng = new Random(randomSeed);
        var env = new SimulationEnv();
        // 每单位该物品(沿全部父路径加权)折合的原料数,用于反推 ~100T 字节的请求量
        var cost = new HashMap<AEItemKey, Long>();
        var items = itemPool(width * (layers + 1) + 1 + loopClusters * 3 + dupStations * 2);
        int[] cursor = { 0 };
        var normalPatterns = 0;

        // 第 0 层:原料,全部巨额库存
        var previous = new AEItemKey[width];
        for (int i = 0; i < width; i++) {
            previous[i] = key(items, cursor);
            env.addStoredItem(previous[i], RAW_STOCK);
            cost.put(previous[i], 1L);
        }

        // 第 1..layers 层:交叉普通合成(输入为上一层的无放回随机抽样,数量 1~3)
        for (int k = 1; k <= layers; k++) {
            var current = new AEItemKey[width];
            for (int i = 0; i < width; i++) {
                current[i] = key(items, cursor);
                var builder = new ProcessingPatternBuilder(new GenericStack(current[i], 1));
                long nodeCost = 0;
                for (var pick : sample(rng, width, Math.min(fanIn, width))) {
                    long amount = 1 + rng.nextInt(3);
                    builder.addPreciseInput(1, new GenericStack(previous[pick], amount));
                    nodeCost += amount * cost.get(previous[pick]);
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
        for (int j = 0; j < loopClusters; j++) {
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
        for (int j = 0; j < dupStations; j++) {
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
        for (var pick : sample(rng, width, Math.min(fanIn, width))) {
            long amount = 1 + rng.nextInt(3);
            rootBuilder.addPreciseInput(1, new GenericStack(previous[pick], amount));
            rootCost += amount * cost.get(previous[pick]);
        }
        for (var x : loopOutputs) {
            rootBuilder.addPreciseInput(1, new GenericStack(x, 1));
        }
        for (var d : dupChains) {
            rootBuilder.addPreciseInput(1, new GenericStack(d, 1));
        }
        env.addPattern(rootBuilder.build());
        normalPatterns++;

        return new Generated(env, root, rootCost, normalPatterns,
                loopSeeds, loopOutputs, dupSeeds, dupChains);
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
     * 键分配:物品池轮询 + 少量 NBT 变体(每物品最多 pool 用尽后才 +1 变体).
     * 变体数 = ceil(总键数 / 物品数),每物品类型的变体极少(≤3),
     * {@code KeyCounter.findFuzzy} 按物品类型子索引扫描的开销保持 O(变体数),
     * 既突破原版物品数量上限,又不让原生逐节点模板枚举退化.
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
    private static List<Item> itemPool(int needed) {
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
