package com.github.aeddddd.ae2enhanced.diag.plansnapshot;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.specialcrafting.FlowReconciler;
import com.github.aeddddd.ae2enhanced.specialcrafting.NetworkPatternIndex;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 合成计划完整快照:把网络实际编码样板 + 全网络库存 + 根请求一次性落成 JSON,
 * 测试侧 {@code NetworkPlanReplayTest} 加载后可离线复现失败订单.
 * <p>与 harvest 快照的区别:harvest 采的是注册表配方,不含玩家编码样板与流体.
 * 触发:求解器诚实失败(降级/截断)时自动落盘;快照仅为配方/库存数据,
 * 不含玩家敏感信息.</p>
 */
public final class PlanSnapshot {

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyyMMdd-HHmmss");

    public int formatVersion = 1;
    public String timestamp;
    /** 根请求(数量 = 订单量). */
    public StackRef root;
    /** 全网络库存(含 NBT). */
    public List<StackRef> stock = new ArrayList<>();
    /** 全部网络样板(主索引 + 副产物,身份去重). */
    public List<PatternEntry> patterns = new ArrayList<>();

    /** 物品引用(注册名 + meta + 数量 + SNBT). */
    public static final class StackRef {
        public String id;
        public int meta;
        public long count;
        public String nbt;
    }

    /** 单个样板的完整求解面(凝聚输入/输出 + 原始槽位 + 替代 + 容器返还 + 标志). */
    public static final class PatternEntry {
        public List<StackRef> inputs = new ArrayList<>();
        public List<StackRef> outputs = new ArrayList<>();
        public boolean craftable;
        public boolean canSubstitute;
        public int priority;
        /** 原始槽位输入(getInputs 口径;仅 craftable&&canSubstitute 时采集——变体展开用). */
        public List<StackRef> rawSlots;
        /** 替代槽 → 候选(仅 canSubstitute 时采集). */
        public Map<Integer, List<StackRef>> substitutes;
        /** 每次合成的容器/配方返还(returnsPerCraft 口径;非空才采集). */
        public List<StackRef> returns;
    }

    private PlanSnapshot() {
    }

    /** IAEItemStack → StackRef(空/未注册返回 null). */
    private static StackRef toRef(IAEItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemStack def = stack.getDefinition();
        if (def.isEmpty() || def.getItem().getRegistryName() == null) {
            return null;
        }
        StackRef ref = new StackRef();
        ref.id = def.getItem().getRegistryName().toString();
        ref.meta = def.getMetadata();
        ref.count = stack.getStackSize();
        NBTTagCompound tag = def.getTagCompound();
        if (tag != null && !tag.isEmpty()) {
            ref.nbt = tag.toString();
        }
        return ref;
    }

    /** 采集:网络全部样板 + 库存 + 根请求. */
    public static PlanSnapshot capture(ICraftingGrid cc, NetworkPatternIndex index, IAEItemStack root,
            Map<IAEItemStack, Long> stock) {
        PlanSnapshot snap = new PlanSnapshot();
        snap.timestamp = TS.format(new Date());
        StackRef rootRef = toRef(root);
        snap.root = rootRef;
        for (Map.Entry<IAEItemStack, Long> e : stock.entrySet()) {
            StackRef ref = toRef(e.getKey());
            if (ref != null && e.getValue() > 0) {
                ref.count = e.getValue();
                snap.stock.add(ref);
            }
        }
        java.util.Set<ICraftingPatternDetails> seen = java.util.Collections
                .newSetFromMap(new java.util.IdentityHashMap<>());
        for (IAEItemStack key : index.keyGraphKeys()) {
            for (ICraftingPatternDetails p : index.patternsFor(key)) {
                if (seen.add(p)) {
                    snap.patterns.add(toEntry(p));
                }
            }
            for (ICraftingPatternDetails p : index.byproductMap().getOrDefault(key,
                    java.util.Collections.emptyList())) {
                if (seen.add(p)) {
                    snap.patterns.add(toEntry(p));
                }
            }
        }
        return snap;
    }

    /** 单个样板的完整求解面采集. */
    private static PatternEntry toEntry(ICraftingPatternDetails p) {
        PatternEntry e = new PatternEntry();
        for (IAEItemStack in : p.getCondensedInputs()) {
            StackRef ref = toRef(in);
            if (ref != null) {
                e.inputs.add(ref);
            }
        }
        for (IAEItemStack out : p.getCondensedOutputs()) {
            StackRef ref = toRef(out);
            if (ref != null) {
                e.outputs.add(ref);
            }
        }
        e.craftable = p.isCraftable();
        e.canSubstitute = p.canSubstitute();
        e.priority = p.getPriority();
        if (e.craftable && e.canSubstitute) {
            e.rawSlots = new ArrayList<>();
            IAEItemStack[] slots = p.getInputs();
            for (int s = 0; s < slots.length; s++) {
                e.rawSlots.add(toRef(slots[s]));
            }
            e.substitutes = new LinkedHashMap<>();
            for (int s = 0; s < slots.length; s++) {
                List<IAEItemStack> subs = p.getSubstituteInputs(s);
                if (subs == null || subs.isEmpty()) {
                    continue;
                }
                List<StackRef> cands = new ArrayList<>();
                for (IAEItemStack sub : subs) {
                    StackRef ref = toRef(sub);
                    if (ref != null) {
                        cands.add(ref);
                    }
                }
                if (!cands.isEmpty()) {
                    e.substitutes.put(s, cands);
                }
            }
        }
        Map<IAEItemStack, Long> returns = FlowReconciler.returnsPerCraft(p);
        if (!returns.isEmpty()) {
            e.returns = new ArrayList<>();
            for (Map.Entry<IAEItemStack, Long> r : returns.entrySet()) {
                StackRef ref = toRef(r.getKey());
                if (ref != null) {
                    ref.count = r.getValue();
                    e.returns.add(ref);
                }
            }
        }
        return e;
    }

    /** 落盘到 logs/ae2enhanced/plansnapshot-*.json(IO 失败静默,求解路径不可因诊断崩). */
    public static File dump(ICraftingGrid cc, NetworkPatternIndex index, IAEItemStack root,
            Map<IAEItemStack, Long> stock) {
        try {
            PlanSnapshot snap = capture(cc, index, root, stock);
            File dir = new File("logs/ae2enhanced");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            File file = new File(dir, "plansnapshot-" + snap.timestamp + ".json");
            snap.save(file);
            AE2Enhanced.LOGGER.info("[LP计划] 计划快照已写出: {} (样板 {}, 库存键 {})",
                    file.getAbsolutePath(), snap.patterns.size(), snap.stock.size());
            return file;
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.warn("[LP计划] 计划快照写出失败(不影响求解)", t);
            return null;
        }
    }

    public void save(File file) throws IOException {
        if (this.root != null && this.root.count == 0) {
            this.root.count = 1;
        }
        try (Writer w = new java.io.OutputStreamWriter(Files.newOutputStream(file.toPath()),
                StandardCharsets.UTF_8)) {
            GSON.toJson(this, w);
        }
    }

    public static PlanSnapshot load(File file) throws IOException {
        try (Reader r = new java.io.InputStreamReader(Files.newInputStream(file.toPath()),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, PlanSnapshot.class);
        }
    }
}
