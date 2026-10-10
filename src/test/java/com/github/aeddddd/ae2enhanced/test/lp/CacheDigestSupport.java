package com.github.aeddddd.ae2enhanced.test.lp;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.init.Blocks;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.MECraftingInventory;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner;
import com.github.aeddddd.ae2enhanced.specialcrafting.CondensationPlanner.LpPlanOutcome;
import com.github.aeddddd.ae2enhanced.specialcrafting.FlowReconciler;
import com.github.aeddddd.ae2enhanced.specialcrafting.LpPlanCache;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.PruneSettings;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.specialcrafting.StableOrder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpHotStart;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;
import com.github.aeddddd.ae2enhanced.test.support.LpTestSupport;
import com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.MiniWeb;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.ReusePatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 计划缓存等价性测试的夹具与 digest 支持(任务 4).
 *
 * <p>digest 口径与 {@code DeterministicOrderTest} 完全一致:求解统计(单元/LP 单元/迭代/
 * 降级)+ 整数化执行记录(按样板签名排序)+ 缺料(按 canon 键排序)。该口径由任务 1
 * 保证跨 JVM 启动逐位可复现,因此可作变更前后对拍的基线锚点。</p>
 */
final class CacheDigestSupport {

    private CacheDigestSupport() {
    }

    /** 夹具句柄(env 可被再次补货/加样板,用于 V4 等用例). */
    static final class Case {
        final String name;
        final SimulationEnv env;
        final IAEItemStack root;
        final long target;

        Case(String name, SimulationEnv env, IAEItemStack root, long target) {
            this.name = name;
            this.env = env;
            this.root = root;
            this.target = target;
        }
    }

    /**
     * 夹具集(名 → 构造器;构造器只在取用时才建 env,故按名取夹具不会连带构造其余夹具):
     * 链/自举环/降级环/容器返还/替代候选/迷你蛛网/弦环网/库存全覆盖/千键弦环.
     * <p>顺序即 V8 基线锚点顺序({@code BASELINE_SHA} 与本表一一对应).</p>
     */
    static Map<String, Supplier<Case>> fixtures() {
        Map<String, Supplier<Case>> cases = new LinkedHashMap<>();

        // 1) 跨单元链(原料赤字)
        cases.put("链-赤字", () -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack r = block(Blocks.SAND);
            IAEItemStack a = block(Blocks.STONE);
            IAEItemStack b = block(Blocks.COBBLESTONE);
            env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, r).build());
            env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build());
            return new Case("链-赤字", env, b, 10);
        });

        // 2) 有种子自增环(种子自举重解路径)
        cases.put("种子自增环", () -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack a = block(Blocks.STONE);
            env.addPattern(new ProcessingPatternBuilder(mult(a, 2)).addPreciseInput(1, a).build());
            env.addStoredItem(a.copy());
            return new Case("种子自增环", env, a, 10);
        });

        // 3) 无种子自增环(LP 自举解被拦截 → 降级/截断路径)
        cases.put("无种子自增环-降级", () -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack a = block(Blocks.STONE);
            env.addPattern(new ProcessingPatternBuilder(mult(a, 2)).addPreciseInput(1, a).build());
            return new Case("无种子自增环-降级", env, a, 10);
        });

        // 4) 容器返还(自返还催化剂)
        cases.put("容器返还", () -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack catalyst = block(Blocks.STONE);
            IAEItemStack raw = block(Blocks.SAND);
            IAEItemStack out = block(Blocks.COBBLESTONE);
            env.addPattern(new ReusePatternBuilder(out)
                    .addPreciseInput(1, catalyst)
                    .addPreciseInput(1, raw)
                    .reused(catalyst)
                    .canSubstitute(false)
                    .build());
            env.addStoredItem(raw.copy());
            env.addStoredItem(catalyst.copy());
            return new Case("容器返还", env, out, 8);
        });

        // 5) 替代候选在另一单元
        cases.put("替代候选异单元", () -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack a = block(Blocks.STONE);
            IAEItemStack alt = block(Blocks.DIRT);
            IAEItemStack y = block(Blocks.SAND);
            IAEItemStack b = block(Blocks.COBBLESTONE);
            env.addPattern(new ProcessingPatternBuilder(alt).addPreciseInput(1, y).build());
            env.addPattern(new ReusePatternBuilder(b)
                    .addPreciseInput(1, a)
                    .nativeStyleSubstituteList()
                    .substituteAlternatives(a, alt)
                    .canSubstitute(true)
                    .build());
            env.addStoredItem(mult(y, 100));
            return new Case("替代候选异单元", env, b, 5);
        });

        // 6) 迷你蛛网(50 键环 + 3 dup,种子 K25=1)
        cases.put("迷你蛛网", () -> {
            SimulationEnv env = new SimulationEnv();
            MiniWeb web = LpTestSupport.buildMiniWeb(env);
            env.addStoredItem(web.keys[25].copy());
            return new Case("迷你蛛网", env, web.keys[0], 200);
        });

        // 7) 48 键弦环网(单大 SCC)+ 种子,需求 1e9
        cases.put("弦环网-大SCC", () -> {
            SimulationEnv env = new SimulationEnv();
            int n = 48;
            for (int i = 0; i < n; i++) {
                env.addPattern(new ProcessingPatternBuilder(LpTestSupport.key((i + 1) % n))
                        .addPreciseInput(1, LpTestSupport.key(i)).build());
                for (int off : new int[] { 5, 17 }) {
                    env.addPattern(new ProcessingPatternBuilder(LpTestSupport.key((i + off) % n))
                            .addPreciseInput(1, LpTestSupport.key(i)).build());
                }
            }
            env.addPattern(new ProcessingPatternBuilder(mult(LpTestSupport.key(24), 2))
                    .addPreciseInput(1, LpTestSupport.key(24)).build());
            env.addStoredItem(LpTestSupport.key(24).copy());
            return new Case("弦环网-大SCC", env, LpTestSupport.key(0), 1_000_000_000L);
        });

        // 8) 库存全覆盖(需求门控/低 LP 场景)
        cases.put("库存全覆盖", () -> {
            SimulationEnv env = new SimulationEnv();
            IAEItemStack r = block(Blocks.SAND);
            IAEItemStack a = block(Blocks.STONE);
            IAEItemStack b = block(Blocks.COBBLESTONE);
            env.addPattern(new ProcessingPatternBuilder(a).addPreciseInput(1, r).build());
            env.addPattern(new ProcessingPatternBuilder(b).addPreciseInput(1, a).build());
            env.addStoredItem(mult(a, 100));
            env.addStoredItem(mult(r, 100));
            return new Case("库存全覆盖", env, b, 5);
        });

        // 9) 千键弦环网(W4 同构;V10 计时夹具)
        cases.put("千键弦环网", () -> {
            SimulationEnv env = new SimulationEnv();
            buildW4(env);
            env.addStoredItem(LpTestSupport.key(500).copy());
            return new Case("千键弦环网", env, LpTestSupport.key(0), 1_000_000L);
        });

        return cases;
    }

    /** W4 夹具:1000 键主环 + 弦 @133 + 自增源 @500(与 PruningEquivalenceTest 同构). */
    static void buildW4(SimulationEnv env) {
        int n = 1000;
        for (int i = 0; i < n; i++) {
            env.addPattern(new ProcessingPatternBuilder(LpTestSupport.key((i + 1) % n))
                    .addPreciseInput(1, LpTestSupport.key(i)).build());
            env.addPattern(new ProcessingPatternBuilder(LpTestSupport.key((i + 133) % n))
                    .addPreciseInput(1, LpTestSupport.key(i)).build());
        }
        env.addPattern(new ProcessingPatternBuilder(mult(LpTestSupport.key(500), 2))
                .addPreciseInput(1, LpTestSupport.key(500)).build());
    }

    /**
     * 全量库存映射(canon 键聚合;与生产 {@code JobStockSource.fullStock} 同口径,
     * 供诊断与离线对比).
     */
    static Map<IAEItemStack, Long> fullStock(SimulationEnv env) {
        Map<IAEItemStack, Long> stock = new LinkedHashMap<>();
        for (IAEItemStack stack : env.networkStorage()) {
            if (stack.getStackSize() > 0) {
                stock.merge(RecursiveCraftingHelper.canon(stack), stack.getStackSize(), Long::sum);
            }
        }
        return stock;
    }

    /** 求解统计 + 执行记录 + 缺料的单行 digest(跨 JVM 稳定口径). */
    static String outcomeDigest(LpPlanOutcome out, Map<Execution, Long> times,
            Map<IAEItemStack, Long> missing) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("mode=").append(out.allOptimal ? "OPTIMAL" : "DEGRADED")
                .append(" units=").append(out.units)
                .append(" lpUnits=").append(out.lpUnits)
                .append(" iterations=").append(out.iterations)
                .append(" degraded=").append(out.degradedUnits);
        sb.append(" execs=[");
        List<Execution> execs = new ArrayList<>(times.keySet());
        execs.sort((a, b) -> execSignature(a).compareTo(execSignature(b)));
        boolean first = true;
        for (Execution exec : execs) {
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(execSignature(exec)).append('=').append(times.get(exec));
        }
        sb.append("] missing=[");
        first = true;
        for (Map.Entry<IAEItemStack, Long> e : StableOrder.sortedEntries(missing)) {
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(StableOrder.keyOf(e.getKey())).append('=').append(e.getValue());
        }
        sb.append(']');
        return sb.toString();
    }

    /** 执行记录签名(样板内容签名 + 变体覆盖). */
    static String execSignature(Execution exec) {
        StringBuilder sb = new StringBuilder(StableOrder.signatureOf(exec.pattern));
        sb.append("@v{");
        boolean first = true;
        for (Map.Entry<IAEItemStack, Long> e : StableOrder.sortedEntries(exec.variantInputs)) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(e.getValue()).append('x').append(StableOrder.keyOf(e.getKey()));
        }
        return sb.append('}').toString();
    }

    static IAEItemStack block(net.minecraft.block.Block b) {
        return AEItemStack.fromItemStack(new net.minecraft.item.ItemStack(b));
    }

    static IAEItemStack mult(IAEItemStack template, long multiplier) {
        return SimulationEnv.mult(template, multiplier);
    }

    /** 夹具名 → 夹具(只构造该夹具的 env;V 组用例按名取用). */
    static Case fixture(String name) {
        Supplier<Case> supplier = fixtures().get(name);
        if (supplier == null) {
            throw new IllegalArgumentException("无此夹具: " + name);
        }
        return supplier.get();
    }

    /** 夹具名清单(fixtures() 声明顺序;V8 基线对齐用). */
    static List<String> fixtureNames() {
        return new ArrayList<>(fixtures().keySet());
    }

    /**
     * 生产口径库存来源(与 {@code LpCraftingJob.JobStockSource} 同构):
     * 稀疏 = 逐键向网络库存副本 SIMULATE 查询(请求量 {@code Long.MAX_VALUE});
     * 全量 = 遍历库存副本(旧路径).
     */
    static final class EnvStockSource implements CondensationPlanner.StockSource {
        private final MECraftingInventory original;
        private final IActionSource src = mock(IActionSource.class);

        EnvStockSource(SimulationEnv env) {
            this.original = new MECraftingInventory(storageList(env));
        }

        @Override
        public long amountOf(IAEItemStack canonKey) {
            IAEItemStack request = canonKey.copy();
            request.setStackSize(Long.MAX_VALUE);
            IAEItemStack got = this.original.extractItems(request, Actionable.SIMULATE, this.src);
            return got == null ? 0L : got.getStackSize();
        }

        @Override
        public Map<IAEItemStack, Long> fullStock() {
            MECraftingInventory copy = new MECraftingInventory(this.original, true, false, true);
            Map<IAEItemStack, Long> stock = new LinkedHashMap<>();
            for (IAEItemStack stack : copy.getItemList()) {
                if (stack.getStackSize() > 0) {
                    stock.merge(RecursiveCraftingHelper.canon(stack), stack.getStackSize(), Long::sum);
                }
            }
            return stock;
        }
    }

    /** 网络库存快照副本(与生产 {@code CraftingJob.original} 同源). */
    static IItemList<IAEItemStack> storageList(SimulationEnv env) {
        IItemList<IAEItemStack> snapshot = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createList();
        for (IAEItemStack stack : env.networkStorage()) {
            snapshot.add(stack.copy());
        }
        return snapshot;
    }

    /** 一次求解(缓存口径)的观测结果. */
    static final class Probe {
        final String digest;
        final CondensationPlanner.LpPlanOutcome outcome;
        final long nanos;

        Probe(String digest, CondensationPlanner.LpPlanOutcome outcome, long nanos) {
            this.digest = digest;
            this.outcome = outcome;
            this.nanos = nanos;
        }
    }

    /**
     * 缓存口径探针:StockSource + 显式开关,与生产 {@code LpCraftingJob} 同构
     * (求解 → 对账重演 → digest).
     */
    static Probe probe(SimulationEnv env, IAEItemStack what, long target, PruneSettings prune,
            LpPlanCache.Settings cache) {
        return probe(new EnvStockSource(env), env, what, target, prune, cache);
    }

    /** 缓存口径探针(库存来源由调用方给定:同一来源可复用/替换以模拟补货). */
    static Probe probe(CondensationPlanner.StockSource source, SimulationEnv env, IAEItemStack what,
            long target, PruneSettings prune, LpPlanCache.Settings cache) {
        ICraftingGrid cc = env.craftingGrid();
        NetworkPatternIndex index = NetworkPatternIndex.of(cc);
        long t0 = System.nanoTime();
        CondensationPlanner.LpPlanOutcome out = CondensationPlanner.solve(cc, index, what, target, source,
                prune, com.github.aeddddd.ae2enhanced.specialcrafting.lp.LpHotStart.Chain
                        .fromSystemProperties(),
                cache);
        long nanos = System.nanoTime() - t0;
        // 对账重演(与生产同构:库存副本 + 根节点)
        MECraftingInventory inv = new MECraftingInventory(new MECraftingInventory(storageList(env)), true,
                false, true);
        CraftingTreeNode root = new CraftingTreeNode(cc, env.newLpJob(what), what.copy(), null, -1, 0);
        FlowReconciler.Reconciled reconciled = FlowReconciler.reconcile(cc, index, what, target, out, inv,
                root, mock(IActionSource.class));
        return new Probe(outcomeDigest(out, reconciled.times, reconciled.missing), out, nanos);
    }

    /** digest 的 SHA-256(基线锚点;失败信息里连同原文一起打印). */
    static String sha256(String text) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
