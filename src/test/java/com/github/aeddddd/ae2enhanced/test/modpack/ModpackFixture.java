package com.github.aeddddd.ae2enhanced.test.modpack;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.CraftEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.FurnaceEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.MachineEntry;
import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot.StackRef;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.test.specialcrafting.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.specialcrafting.ReusePatternBuilder;
import com.github.aeddddd.ae2enhanced.test.specialcrafting.SimulationEnv;

/**
 * 整合包配方快照 → 虚拟测试环境（对应工作流规划文档阶段 2）.
 * <p>建模口径:</p>
 * <ul>
 * <li><b>dummy Item</b>:按注册名合成 {@code new Item() + setRegistryName},ItemStack
 * 只持引用,无需注册进 GameData;矿词通配 meta(32767)归一化为 0;NBT 忽略
 * （AE 合成计划以 item+meta 为键,NBT 敏感配方属已知边界,见规划文档 §6.1）;</li>
 * <li><b>合成台配方</b> → 可合成样板:槽位首候选为编码输入,其余为替代候选
 * （canSubstitute 按"任一槽多候选"置位,getSubstituteInputs 恒返回
 * [编码输入,...候选] 的原生口径由 ReusePatternBuilder 复刻）;</li>
 * <li><b>熔炉/机器配方</b> → 处理样板（精确输入,无替代——与原生 processing
 * 样板语义一致）;机器输入槽取首候选（矿词成分的代表）;</li>
 * <li><b>终端库存</b>:所有"作为输入出现但无任何样板产出"的键视为原材料,
 * 库存放大到 100 万——普查/回放只关心计划结构与求解正确性,不关心真实存量.</li>
 * </ul>
 */
public final class ModpackFixture {

    /** 快照目录（项目根相对路径;gradlew test 的工作目录即项目根）. */
    public static final String SNAPSHOT_DIR = "research/harvest";
    /** 终端原材料库存放大系数. */
    public static final long TERMINAL_STOCK = 1_000_000L;

    private ModpackFixture() {
    }

    /** 最新快照文件（无则 null）. */
    @Nullable
    public static File latestSnapshot() {
        File dir = new File(SNAPSHOT_DIR);
        File[] files = dir.listFiles((d, name) -> name.startsWith("harvest-") && name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return null;
        }
        Arrays.sort(files, (a, b) -> a.getName().compareTo(b.getName())); // 文件名含时间戳
        return files[files.length - 1];
    }

    public static boolean available() {
        return latestSnapshot() != null;
    }

    public static Loaded loadLatest() throws IOException {
        File file = latestSnapshot();
        if (file == null) {
            throw new IOException("无整合包快照: " + SNAPSHOT_DIR);
        }
        return load(file);
    }

    public static Loaded load(File file) throws IOException {
        HarvestSnapshot snapshot = HarvestSnapshot.load(file);
        SimulationEnv env = new SimulationEnv();
        Loaded loaded = new Loaded(snapshot, env);

        for (CraftEntry entry : snapshot.crafting) {
            buildCraftingPattern(loaded, entry);
        }
        for (FurnaceEntry entry : snapshot.furnace) {
            buildFurnacePattern(loaded, entry);
        }
        for (MachineEntry entry : snapshot.machines) {
            buildMachinePattern(loaded, entry);
        }
        stockTerminals(loaded);
        return loaded;
    }

    /** 合成台配方 → 可合成样板（逐槽首候选为编码输入,其余为替代候选）. */
    private static void buildCraftingPattern(Loaded loaded, CraftEntry entry) {
        if (entry.output == null || entry.slots.isEmpty()) {
            loaded.skipped++;
            return;
        }
        IAEItemStack output = loaded.toAe(entry.output);
        if (output == null) {
            loaded.skipped++;
            return;
        }
        ReusePatternBuilder builder = new ReusePatternBuilder(output).nativeStyleSubstituteList();
        boolean anySubstitute = false;
        boolean anyInput = false;
        java.util.List<IAEItemStack> encodedBySlot = new java.util.ArrayList<>();
        for (java.util.List<StackRef> slot : entry.slots) {
            if (slot == null || slot.isEmpty()) {
                encodedBySlot.add(null);
                continue;
            }
            anyInput = true;
            IAEItemStack encoded = loaded.toAe(slot.get(0));
            encodedBySlot.add(encoded);
            if (encoded == null) {
                continue;
            }
            builder.addPreciseInput(Math.max(1, slot.get(0).count), encoded);
            for (int i = 1; i < slot.size(); i++) {
                IAEItemStack alt = loaded.toAe(slot.get(i));
                if (alt != null) {
                    builder.substituteAlternatives(encoded, alt);
                    anySubstitute = true;
                }
            }
        }
        if (!anyInput) {
            loaded.skipped++;
            return;
        }
        builder.canSubstitute(anySubstitute);
        // 逐槽返还(容器物/CrT .reuse()/工具损耗):快照 v2 实采标记 → 配方级剩余物
        if (entry.returnedSlots != null) {
            for (int slotIndex : entry.returnedSlots) {
                if (slotIndex >= 0 && slotIndex < encodedBySlot.size()) {
                    IAEItemStack encoded = encodedBySlot.get(slotIndex);
                    if (encoded != null) {
                        builder.reused(encoded);
                    }
                }
            }
        }
        loaded.env.addPattern(builder.build());
        loaded.patternsCrafting++;
    }

    /** 熔炉配方 → 处理样板(1 入 1 出,精确). */
    private static void buildFurnacePattern(Loaded loaded, FurnaceEntry entry) {
        IAEItemStack input = loaded.toAe(entry.input);
        IAEItemStack output = loaded.toAe(entry.output);
        if (input == null || output == null) {
            loaded.skipped++;
            return;
        }
        loaded.env.addPattern(new ProcessingPatternBuilder(output)
                .addPreciseInput(Math.max(1, entry.input.count), input).build());
        loaded.patternsFurnace++;
    }

    /** 机器配方 → 处理样板（输入槽取首候选代表,数量保留）. */
    private static void buildMachinePattern(Loaded loaded, MachineEntry entry) {
        if (entry.outputs.isEmpty() || entry.inputs.isEmpty()) {
            loaded.skipped++;
            return;
        }
        java.util.List<IAEItemStack> outputs = new java.util.ArrayList<>();
        for (StackRef ref : entry.outputs) {
            IAEItemStack out = loaded.toAe(ref);
            if (out != null) {
                outputs.add(out);
            }
        }
        if (outputs.isEmpty()) {
            loaded.skipped++;
            return;
        }
        ProcessingPatternBuilder builder = new ProcessingPatternBuilder(
                outputs.toArray(new IAEItemStack[0]));
        boolean anyInput = false;
        for (java.util.List<StackRef> slot : entry.inputs) {
            if (slot == null || slot.isEmpty()) {
                continue;
            }
            IAEItemStack input = loaded.toAe(slot.get(0));
            if (input == null) {
                continue;
            }
            builder.addPreciseInput(Math.max(1, slot.get(0).count), input);
            anyInput = true;
        }
        if (!anyInput) {
            loaded.skipped++;
            return;
        }
        loaded.env.addPattern(builder.build());
        loaded.patternsMachine++;
    }

    /** 终端原材料库存:有消耗无产出的键全部放大库存. */
    private static void stockTerminals(Loaded loaded) {
        Set<IAEItemStack> produced = new HashSet<>();
        Set<IAEItemStack> consumed = new HashSet<>();
        com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess cacheAccess =
                (com.github.aeddddd.ae2enhanced.mixin.bridge.ICraftingGridCacheAccess) loaded.env.craftingGrid();
        produced.addAll(cacheAccess.ae2enhanced$craftableKeys());
        // 经网络级索引的键图:副产物也计入"有产出"
        com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex index =
                com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex.of(loaded.env.craftingGrid());
        if (index != null) {
            produced.addAll(index.byproductMap().keySet());
        }
        for (Map.Entry<String, IAEItemStack> e : loaded.byKey.entrySet()) {
            IAEItemStack canon = RecursiveCraftingHelper.canon(e.getValue());
            if (consumed.contains(canon)) {
                continue;
            }
            consumed.add(canon);
            if (!produced.contains(canon)) {
                IAEItemStack stock = canon.copy();
                stock.setStackSize(TERMINAL_STOCK);
                loaded.env.addStoredItem(stock);
                loaded.terminalStockKeys++;
            }
        }
        // dup 种子库存:净增自引用键(如 Alkahestry 宝典 1→17 增殖)预置启动种子,
        // 模拟真实玩家持有增殖种子下单的场景,让 dup 闭式路径可解;
        // 否则测试环境 0 种子必回落环盲,真实环境中本应走通的 dup 路径永远测不到
        for (IAEItemStack craftable : cacheAccess.ae2enhanced$craftableKeys()) {
            IAEItemStack canon = RecursiveCraftingHelper.canon(craftable);
            for (appeng.api.networking.crafting.ICraftingPatternDetails pattern : loaded.env
                    .craftingGrid().getCraftingFor(canon, null, -1, loaded.env.world())) {
                long inPer = RecursiveCraftingHelper.selfInputPerCraft(pattern, canon);
                if (inPer > 0 && RecursiveCraftingHelper.selfOutputPerCraft(pattern, canon) > inPer) {
                    IAEItemStack seed = canon.copy();
                    seed.setStackSize(inPer);
                    loaded.env.addStoredItem(seed);
                    break;
                }
            }
        }
    }

    /** 加载结果:环境 + 快照 + 物品解析表 + 统计. */
    public static final class Loaded {

        public final HarvestSnapshot snapshot;
        public final SimulationEnv env;
        /** "id:meta" → 物品实例（stackSize 1 的规范引用）. */
        public final Map<String, IAEItemStack> byKey = new HashMap<>();
        private final Map<String, Item> dummyItems = new HashMap<>();
        public int patternsCrafting;
        public int patternsFurnace;
        public int patternsMachine;
        public int skipped;
        public long terminalStockKeys;

        Loaded(HarvestSnapshot snapshot, SimulationEnv env) {
            this.snapshot = snapshot;
            this.env = env;
        }

        /** StackRef → AE 物品（dummy Item 合成;通配 meta 归一化为 0;NBT 忽略）. */
        @Nullable
        IAEItemStack toAe(StackRef ref) {
            if (ref == null || ref.id == null) {
                return null;
            }
            Item item = this.dummyItems.computeIfAbsent(ref.id, id -> {
                Item it = new Item();
                it.setRegistryName(id);
                return it;
            });
            int meta = ref.meta == 32767 ? 0 : ref.meta;
            IAEItemStack ae = AEItemStack.fromItemStack(new ItemStack(item, Math.max(1, ref.count), meta));
            if (ae == null) {
                return null;
            }
            IAEItemStack canon = ae.copy();
            canon.setStackSize(1);
            this.byKey.putIfAbsent(ref.id + ":" + meta, canon);
            return ae;
        }

        /** 按注册名取规范物品（stackSize 1）;快照中不存在返回 null. */
        @Nullable
        public IAEItemStack item(String id) {
            IAEItemStack found = this.byKey.get(id + ":0");
            if (found != null) {
                return found;
            }
            // meta 不为 0 的退化匹配
            for (Map.Entry<String, IAEItemStack> e : this.byKey.entrySet()) {
                if (e.getKey().startsWith(id + ":")) {
                    return e.getValue();
                }
            }
            return null;
        }
    }
}
