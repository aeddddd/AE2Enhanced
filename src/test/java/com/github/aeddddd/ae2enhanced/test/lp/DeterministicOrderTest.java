package com.github.aeddddd.ae2enhanced.test.lp;

import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.buildMiniWeb;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.canon;
import static com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.key;
import static com.github.aeddddd.ae2enhanced.test.support.SimulationEnv.mult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import appeng.api.AEApi;
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
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import com.github.aeddddd.ae2enhanced.specialcrafting.StableOrder;
import com.github.aeddddd.ae2enhanced.specialcrafting.lp.SccLpSolve.Execution;
import com.github.aeddddd.ae2enhanced.test.support.LpTestSupport.MiniWeb;
import com.github.aeddddd.ae2enhanced.test.support.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 求解链路确定性测试(任务 1:LP 求解链路确定性排序).
 * <p>目标:同一逻辑输入跨 JVM 启动产出逐位确定的求解过程(列序/pivot/迭代数 +
 * 逐执行计数 + 缺料集合).digest 覆盖:</p>
 * <pre>mode units lpUnits iterations degraded + execs(按签名排序的 (签名,整数化次数))
 *  + missing(按 canon 键排序的 (键,数量))</pre>
 * <ul>
 * <li>T1:同 JVM 重复 5 次(每次重建夹具),digest 完全相同;</li>
 * <li>T2:集合哈希序扰动(库存映射插入序打乱 + 样板表插入序逐键反转),digest 不变;</li>
 * <li>T3:样板注册顺序整体打乱,digest 不变;</li>
 * <li>T4:{@link StableOrder} 单元语义(键序与置换无关/NBT 规范编码与插入序无关).</li>
 * </ul>
 * <p>诊断口径(任务 2 起):{@code ae2e.prune} 默认 {@code full},故 digest 里的
 * {@code lpUnits}/{@code iterations} 是<b>剪枝后活跃口径</b>(需求门控跳过的单元不计入);
 * 本类只断言"同口径下逐位可复现",不锁定具体数值。</p>
 */
public class DeterministicOrderTest {

    @BeforeAll
    public static void boot() {
        // 纯 StableOrder 单测也要 MC 注册表(Blocks.*)/AE 通道
        com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap.boot();
    }

    // ===== T1 同 JVM 重复 =====

    @Test
    public void sameJvmRepeatsIdenticalDigest() {
        String first = digestMiniWeb(null) + "\n" + digestChordWeb(null);
        for (int i = 0; i < 4; i++) {
            String again = digestMiniWeb(null) + "\n" + digestChordWeb(null);
            assertEquals(first, again, "第 " + (i + 2) + " 次求解 digest 不一致");
        }
        System.out.println("[DET] T1 digest:\n" + first);
    }

    // ===== T2 哈希序扰动 =====

    @Test
    public void hashOrderPerturbationKeepsDigest() {
        String base = digestMiniWeb(null) + "\n" + digestChordWeb(null);
        String perturbed = digestMiniWeb(0x5EEDL) + "\n" + digestChordWeb(0x5EEDL);
        assertEquals(base, perturbed, "库存映射/样板表插入序扰动改变了 digest");
    }

    // ===== T3 注册顺序打乱 =====

    @Test
    public void registrationOrderShuffleKeepsDigest() {
        String base = digestMiniWeb(null);
        String shuffled = digestMiniWeb(7777L);
        assertEquals(base, shuffled, "样板注册顺序打乱改变了 digest");
    }

    // ===== T4 StableOrder 单元语义 =====

    @Test
    public void sortedKeysInvariantUnderPermutation() {
        List<IAEItemStack> keys = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            keys.add(key(i));
        }
        List<IAEItemStack> expected = StableOrder.sortedKeys(keys);

        List<IAEItemStack> reversed = new ArrayList<>(keys);
        Collections.reverse(reversed);
        assertSameOrder(expected, StableOrder.sortedKeys(reversed), "逆序插入");

        List<IAEItemStack> shuffled = new ArrayList<>(keys);
        Collections.shuffle(shuffled, new Random(20261010L));
        assertSameOrder(expected, StableOrder.sortedKeys(shuffled), "随机置换");

        // 键序与数量/标志位无关(canon 语义)
        IAEItemStack big = keys.get(3).copy();
        big.setStackSize(12345);
        big.setCraftable(true);
        assertEquals(StableOrder.keyOf(keys.get(3)), StableOrder.keyOf(big), "keyOf 应忽略数量/标志");
    }

    @Test
    public void canonicalNbtInvariantUnderInsertionOrder() {
        NBTTagCompound a = new NBTTagCompound();
        a.setInteger("zeta", 7);
        a.setString("alpha", "x");
        NBTTagCompound nested = new NBTTagCompound();
        nested.setByte("b", (byte) 1);
        nested.setByteArray("arr", new byte[] { 1, 2, 3 });
        a.setTag("mid", nested);
        NBTTagList list = new NBTTagList();
        list.appendTag(new NBTTagString("first"));
        list.appendTag(new NBTTagString("second"));
        a.setTag("list", list);

        NBTTagCompound b = new NBTTagCompound();
        b.setTag("list", list.copy());
        b.setTag("mid", nested.copy());
        b.setString("alpha", "x");
        b.setInteger("zeta", 7);

        assertEquals(StableOrder.canonicalNbt(a), StableOrder.canonicalNbt(b),
                "插入序不同但内容相同的 NBT 规范编码应相同");

        // 类型区分:NBTTagByte(1) 与 NBTTagInt(1) 不得同码
        NBTTagCompound byteOne = new NBTTagCompound();
        byteOne.setByte("k", (byte) 1);
        NBTTagCompound intOne = new NBTTagCompound();
        intOne.setInteger("k", 1);
        assertNotEquals(StableOrder.canonicalNbt(byteOne), StableOrder.canonicalNbt(intOne),
                "NBT 类型 id 未参与编码");

        // 数组内容参与编码
        NBTTagCompound arrA = new NBTTagCompound();
        arrA.setByteArray("k", new byte[] { 1, 2 });
        NBTTagCompound arrB = new NBTTagCompound();
        arrB.setByteArray("k", new byte[] { 2, 1 });
        assertNotEquals(StableOrder.canonicalNbt(arrA), StableOrder.canonicalNbt(arrB), "byte[] 内容未参与编码");

        // 列表保序
        NBTTagList swapped = new NBTTagList();
        swapped.appendTag(new NBTTagString("second"));
        swapped.appendTag(new NBTTagString("first"));
        NBTTagCompound listB = new NBTTagCompound();
        listB.setTag("list", swapped);
        NBTTagCompound listA = new NBTTagCompound();
        listA.setTag("list", list.copy());
        assertNotEquals(StableOrder.canonicalNbt(listA), StableOrder.canonicalNbt(listB), "列表应保序");

        // 空/无 NBT
        assertEquals("", StableOrder.canonicalNbt(null));
        assertEquals("", StableOrder.canonicalNbt(new NBTTagCompound()));
    }

    @Test
    public void keyOfIgnoresInsertionOrderAndCoversNbt() {
        IAEItemStack plain = key(5);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("marker", 42);
        // 用带 NBT 的独立 ItemStack 构造(AEItemStackRegistry 按 (item,damage,nbt) 分桶,
        // 不改写 key(5) 的共享定义栈)
        ItemStack def = new ItemStack(Blocks.GRAVEL);
        def.setTagCompound(tag);
        IAEItemStack tagged = AEItemStack.fromItemStack(def);
        assertNotEquals(StableOrder.keyOf(plain), StableOrder.keyOf(tagged), "NBT 差异应体现在键串");
        assertEquals(StableOrder.keyOf(canon(tagged)), StableOrder.keyOf(canon(tagged.copy())),
                "同内容键串应一致");
    }

    @Test
    public void canonicalNbtTruncationStaysDeterministic() {
        String huge = repeat('a', 5000);
        NBTTagCompound a = new NBTTagCompound();
        a.setString("huge", huge);
        NBTTagCompound b = new NBTTagCompound();
        b.setString("huge", huge);
        String encA = StableOrder.canonicalNbt(a);
        assertEquals(encA, StableOrder.canonicalNbt(b), "超长 NBT 编码应确定");
        assertTrue(encA.length() <= StableOrder.MAX_NBT_CHARS + 20, "超长 NBT 应被截断: " + encA.length());

        NBTTagCompound c = new NBTTagCompound();
        c.setString("huge", huge + "b");
        assertNotEquals(encA, StableOrder.canonicalNbt(c), "截断后仍应区分内容差异(哈希后缀)");
    }

    // ===== 夹具与 digest =====

    /**
     * 夹具 A(迷你蛛网):50 键环 + dup@5/25/45,种子 K25=1,请求 K0 × 200.
     *
     * @param stockSeed 非 null 时:样板表逐键反转注册 + 库存映射插入序按该种子打乱
     */
    private static String digestMiniWeb(Long stockSeed) {
        if (stockSeed == null) {
            SimulationEnv env = new SimulationEnv();
            MiniWeb web = buildMiniWeb(env);
            env.addStoredItem(web.keys[25]);
            return digest(env, web.keys[0], 200, null);
        }
        // 反转注册序:样板对象先在临时 env 中构造,再按逆序注册进被测 env
        MiniWeb src = buildMiniWeb(new SimulationEnv());
        SimulationEnv reordered = new SimulationEnv();
        for (int i = src.ring.length - 1; i >= 0; i--) {
            reordered.addPattern(src.ring[i]);
        }
        reordered.addPattern(src.dup45);
        reordered.addPattern(src.dup25);
        reordered.addPattern(src.dup5);
        reordered.addStoredItem(src.keys[25]);
        return digest(reordered, src.keys[0], 200, stockSeed);
    }

    /**
     * 夹具 B(弦环网):48 键主环 + 弦 {5,17} + dup@24,种子 K24=1,请求 K0 × 1e9.
     *
     * @param permuteSeed 非 null 时样板注册顺序按该种子整体打乱
     */
    private static String digestChordWeb(Long permuteSeed) {
        int n = 48;
        int[] offsets = { 5, 17 };
        List<ICraftingPatternDetails> patterns = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            patterns.add(new ProcessingPatternBuilder(key((i + 1) % n))
                    .addPreciseInput(1, key(i)).build());
            for (int off : offsets) {
                patterns.add(new ProcessingPatternBuilder(key((i + off) % n))
                        .addPreciseInput(1, key(i)).build());
            }
        }
        patterns.add(new ProcessingPatternBuilder(mult(key(24), 2))
                .addPreciseInput(1, key(24)).build());
        if (permuteSeed != null) {
            Collections.shuffle(patterns, new Random(permuteSeed));
        }
        SimulationEnv env = new SimulationEnv();
        for (ICraftingPatternDetails p : patterns) {
            env.addPattern(p);
        }
        env.addStoredItem(key(24));
        return digest(env, key(0), 1_000_000_000L, permuteSeed);
    }

    /**
     * 单行 digest:求解过程(单元/迭代/降级) + 整数化执行计数(按样板签名排序)
     * + 缺料(按 canon 键排序).与 {@code LpCraftingJob} 同口径(同一库存来源
     * 分别喂求解器与对账重演).
     */
    private static String digest(SimulationEnv env, IAEItemStack what, long target, Long stockSeed) {
        ICraftingGrid cc = env.craftingGrid();
        NetworkPatternIndex index = NetworkPatternIndex.of(cc);
        Map<IAEItemStack, Long> stock = stockOf(env, stockSeed);
        LpPlanOutcome out = CondensationPlanner.solve(cc, index, what, target, stock);

        // 对账重演:库存快照复制一份(与生产同构,避免污染夹具)
        IItemList<IAEItemStack> snapshot = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createList();
        for (IAEItemStack stack : env.networkStorage()) {
            snapshot.add(stack.copy());
        }
        MECraftingInventory inv = new MECraftingInventory(new MECraftingInventory(snapshot), true, false, true);
        CraftingTreeNode root = new CraftingTreeNode(cc, env.newLpJob(what), what.copy(), null, -1, 0);
        IActionSource src = mock(IActionSource.class);
        FlowReconciler.Reconciled reconciled = FlowReconciler.reconcile(cc, index, what, target, out, inv,
                root, src);

        StringBuilder sb = new StringBuilder(256);
        sb.append("mode=").append(out.allOptimal ? "OPTIMAL" : "DEGRADED")
                .append(" units=").append(out.units)
                .append(" lpUnits=").append(out.lpUnits)
                .append(" iterations=").append(out.iterations)
                .append(" degraded=").append(out.degradedUnits);
        sb.append(" execs=[");
        List<Execution> execs = new ArrayList<>(reconciled.times.keySet());
        execs.sort((a, b) -> execSignature(a).compareTo(execSignature(b)));
        boolean first = true;
        for (Execution exec : execs) {
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(execSignature(exec)).append('=').append(reconciled.times.get(exec));
        }
        sb.append("] missing=[");
        first = true;
        for (Map.Entry<IAEItemStack, Long> e : StableOrder.sortedEntries(reconciled.missing)) {
            if (!first) {
                sb.append(';');
            }
            first = false;
            sb.append(StableOrder.keyOf(e.getKey())).append('=').append(e.getValue());
        }
        sb.append(']');
        return sb.toString();
    }

    private static String execSignature(Execution exec) {
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

    /** 库存映射:canon 键聚合;stockSeed 非 null 时打乱插入序(哈希序扰动). */
    private static Map<IAEItemStack, Long> stockOf(SimulationEnv env, Long stockSeed) {
        List<Map.Entry<IAEItemStack, Long>> entries = new ArrayList<>();
        Map<IAEItemStack, Long> aggregated = new LinkedHashMap<>();
        for (IAEItemStack stack : env.networkStorage()) {
            if (stack.getStackSize() > 0) {
                aggregated.merge(canon(stack), stack.getStackSize(), Long::sum);
            }
        }
        entries.addAll(aggregated.entrySet());
        if (stockSeed != null) {
            Collections.shuffle(entries, new Random(stockSeed));
        }
        Map<IAEItemStack, Long> stock = new LinkedHashMap<>();
        for (Map.Entry<IAEItemStack, Long> e : entries) {
            stock.put(e.getKey(), e.getValue());
        }
        return stock;
    }

    private static void assertSameOrder(List<IAEItemStack> expected, List<IAEItemStack> actual, String label) {
        assertEquals(expected.size(), actual.size(), label + " 元素数不同");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(StableOrder.keyOf(expected.get(i)), StableOrder.keyOf(actual.get(i)),
                    label + " 第 " + i + " 位不同");
        }
    }

    private static String repeat(char c, int times) {
        StringBuilder sb = new StringBuilder(times);
        for (int i = 0; i < times; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
