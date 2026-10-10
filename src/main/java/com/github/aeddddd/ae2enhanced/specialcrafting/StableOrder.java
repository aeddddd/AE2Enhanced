package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByte;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLong;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;

/**
 * 求解链路的确定性排序工具(跨 JVM 启动逐位可复现).
 *
 * <p>背景:{@code IAEItemStack.hashCode} 含 {@code Item} 身份哈希,每次 JVM 启动不同,
 * 因此 HashMap 迭代序随启动漂移;而 LP 的列序/行序取自集合迭代序,退化模型的 pivot
 * 平局决胜又被列序支配,同一逻辑输入的迭代数会漂移数十倍.本类提供与该漂移无关的
 * <b>内容序</b>:键序按 {@code 注册名@meta#规范化NBT},样板序按
 * {@code 优先级降序 → 内容签名升序}.</p>
 *
 * <p><b>使用约束(重要)</b>:{@link #KEY_COMPARATOR} 只用于"已由 Map/Set 按 AE
 * {@code equals} 去重后的集合"排序,<b>禁止</b>用作 TreeSet/TreeMap/去重键——
 * canonical NBT 编码比 AE 的 {@code equals} 更细(list/数组内容参与编码,AE 相等
 * 只按 NBT 结构比较),用它去重会把 AE 视为同一物的键拆成多个,破坏库存守恒口径.</p>
 *
 * <p>本类触碰 {@code IAEItemStack}/{@code NBTTagCompound},故不放在纯数学的 {@code lp}
 * 包内.全部方法无状态副作用(仅 memo),线程安全.</p>
 */
public final class StableOrder {

    /** NBT 规范编码长度上限;超长截断并附全串哈希,避免极端 NBT 拖垮排序(护栏). */
    public static final int MAX_NBT_CHARS = 4096;

    /** keyOf memo 容量上限;超限整体清空(纯 memo,清空不影响确定性). */
    private static final int MEMO_LIMIT = 1 << 16;

    /** canon 键排序:注册名@meta#规范化NBT 的字典序. */
    public static final Comparator<IAEItemStack> KEY_COMPARATOR =
            Comparator.comparing(StableOrder::keyOf);

    /** 样板排序:优先级降序 → 内容签名升序(同样内容/优先级的并列由门控断言检出). */
    public static final Comparator<ICraftingPatternDetails> PATTERN_COMPARATOR =
            (a, b) -> {
                int pa = a.getPriority();
                int pb = b.getPriority();
                if (pa != pb) {
                    return Integer.compare(pb, pa); // 优先级降序
                }
                return signatureOf(a).compareTo(signatureOf(b));
            };

    /** 键 → 规范键串(热路径 memo;键为 AE 去重口径,同物共享). */
    private static final Map<IAEItemStack, String> KEY_MEMO = new ConcurrentHashMap<>();
    /** 样板 → 内容签名(memo;弱键,样板随索引失效回收后不滞留). */
    private static final Map<ICraftingPatternDetails, String> SIGNATURE_MEMO =
            Collections.synchronizedMap(new WeakHashMap<>());

    private StableOrder() {
    }

    // ===== 键序 =====

    /**
     * 键的规范串:{@code 注册名@damage#规范化NBT}(忽略数量/可合成/可请求标志,canon 语义).
     * 未注册物品回退到类名(仍为启动无关的确定值).
     */
    public static String keyOf(IAEItemStack stack) {
        if (stack == null) {
            return "";
        }
        String cached = KEY_MEMO.get(stack);
        if (cached != null) {
            return cached;
        }
        String key = computeKey(stack);
        if (KEY_MEMO.size() > MEMO_LIMIT) {
            KEY_MEMO.clear();
        }
        KEY_MEMO.put(stack, key);
        return key;
    }

    private static String computeKey(IAEItemStack stack) {
        ItemStack def = stack.getDefinition();
        Item item = def == null ? null : def.getItem();
        String name;
        if (item == null) {
            name = "null";
        } else if (item.getRegistryName() != null) {
            name = item.getRegistryName().toString();
        } else {
            name = item.getClass().getName();
        }
        StringBuilder sb = new StringBuilder(name.length() + 24);
        sb.append(name).append('@').append(def == null ? 0 : def.getItemDamage()).append('#');
        if (def != null && def.hasTagCompound()) {
            sb.append(canonicalNbt(def.getTagCompound()));
        }
        return sb.toString();
    }

    /** 键集合 → 按 {@link #KEY_COMPARATOR} 升序的列表(新列表,不改入参). */
    public static <K extends IAEItemStack> List<K> sortedKeys(Collection<K> keys) {
        List<K> out = new ArrayList<>(keys);
        out.sort(KEY_COMPARATOR);
        return out;
    }

    // ===== 样板序 =====

    /**
     * 样板内容签名(同优先级下排序的判据):优先级/可合成/凝聚输出/凝聚输入/
     * 替代槽候选/每次返还/主产出.<b>不含</b> {@code expandVariants} 结果(昂贵),
     * 不使用 {@code identityHashCode} 决胜.
     */
    public static String signatureOf(ICraftingPatternDetails pattern) {
        if (pattern == null) {
            return "";
        }
        String cached = SIGNATURE_MEMO.get(pattern);
        if (cached != null) {
            return cached;
        }
        String sig = computeSignature(pattern);
        SIGNATURE_MEMO.put(pattern, sig);
        return sig;
    }

    private static String computeSignature(ICraftingPatternDetails pattern) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(pattern.getPriority()).append('|').append(pattern.isCraftable() ? 1 : 0);
        appendStackArray(sb, pattern.getCondensedOutputs());
        appendStackArray(sb, pattern.getCondensedInputs());
        if (pattern.canSubstitute()) {
            IAEItemStack[] slots = pattern.getInputs();
            for (int slot = 0; slot < slots.length; slot++) {
                sb.append('|').append(slot).append('[');
                List<IAEItemStack> subs = pattern.getSubstituteInputs(slot);
                if (subs != null) {
                    boolean first = true;
                    for (IAEItemStack sub : subs) {
                        if (!first) {
                            sb.append(',');
                        }
                        first = false;
                        sb.append(keyOf(sub));
                    }
                }
                sb.append(']');
            }
        }
        // 每次返还(条目按 canon 键升序;返回值本身也可能乱序)
        sb.append("|ret{");
        List<Map.Entry<IAEItemStack, Long>> returns =
                sortedEntries(FlowReconciler.returnsPerCraft(pattern));
        boolean firstRet = true;
        for (Map.Entry<IAEItemStack, Long> e : returns) {
            if (!firstRet) {
                sb.append(',');
            }
            firstRet = false;
            sb.append(e.getValue()).append('x').append(keyOf(e.getKey()));
        }
        sb.append('}');
        // 主产出
        IAEItemStack primary = null;
        IAEItemStack[] outs = pattern.getCondensedOutputs();
        if (outs.length > 0) {
            primary = pattern.getPrimaryOutput();
            if (primary == null) {
                primary = outs[0];
            }
        }
        return sb.append("|P").append(keyOf(primary)).toString();
    }

    private static void appendStackArray(StringBuilder sb, IAEItemStack[] stacks) {
        sb.append('|');
        if (stacks == null) {
            sb.append("-");
            return;
        }
        sb.append(stacks.length).append('[');
        for (int i = 0; i < stacks.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            IAEItemStack s = stacks[i];
            if (s == null) {
                sb.append("null");
            } else {
                sb.append(s.getStackSize()).append('x').append(keyOf(s));
            }
        }
        sb.append(']');
    }

    /** 样板集合 → 按 {@link #PATTERN_COMPARATOR} 升序的列表(签名逐元素只算一次). */
    public static <P extends ICraftingPatternDetails> List<P> sortedPatterns(Collection<P> patterns) {
        List<P> out = new ArrayList<>(patterns);
        if (out.size() > 1) {
            Map<P, String> sigs = new java.util.IdentityHashMap<>();
            for (P p : out) {
                sigs.put(p, signatureOf(p));
            }
            out.sort((a, b) -> {
                int pa = a.getPriority();
                int pb = b.getPriority();
                if (pa != pb) {
                    return Integer.compare(pb, pa);
                }
                return sigs.get(a).compareTo(sigs.get(b));
            });
        }
        return out;
    }

    /**
     * 映射条目按 canon 键升序(键序确定化的公共 helper;返回新列表).
     */
    public static <V> List<Map.Entry<IAEItemStack, V>> sortedEntries(
            Map<IAEItemStack, V> map) {
        List<Map.Entry<IAEItemStack, V>> out = new ArrayList<>(map.entrySet());
        out.sort((a, b) -> KEY_COMPARATOR.compare(a.getKey(), b.getKey()));
        return out;
    }

    /**
     * 样板 → 执行数映射条目按样板签名升序(跨集合序无关的确定性遍历).
     */
    public static List<Map.Entry<ICraftingPatternDetails, Double>> sortedPatternEntries(
            Map<ICraftingPatternDetails, Double> map) {
        List<Map.Entry<ICraftingPatternDetails, Double>> out = new ArrayList<>(map.entrySet());
        out.sort((a, b) -> PATTERN_COMPARATOR.compare(a.getKey(), b.getKey()));
        return out;
    }

    // ===== NBT 规范编码 =====

    /**
     * NBT 规范编码:复合键按 {@code String.compareTo} 升序递归,列表保序,
     * byte[]/int[] 按内容,叶值写 NBT 类型 id 前缀(区分 {@code NBTTagInt(1)}
     * 与 {@code NBTTagByte(1)});插入序不同但内容相同的 NBT 编码相同.
     */
    public static String canonicalNbt(NBTTagCompound tag) {
        if (tag == null || tag.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(256);
        appendTag(sb, tag);
        String full = sb.toString();
        if (full.length() <= MAX_NBT_CHARS) {
            return full;
        }
        return full.substring(0, MAX_NBT_CHARS) + '~' + Integer.toHexString(full.hashCode());
    }

    private static void appendTag(StringBuilder sb, NBTBase tag) {
        if (tag == null) {
            sb.append("0:");
            return;
        }
        int id = tag.getId();
        sb.append(id).append(':');
        switch (id) {
            case 1:
                sb.append(((NBTTagByte) tag).getByte());
                break;
            case 2:
                sb.append(((NBTTagShort) tag).getShort());
                break;
            case 3:
                sb.append(((NBTTagInt) tag).getInt());
                break;
            case 4:
                sb.append(((NBTTagLong) tag).getLong());
                break;
            case 5:
                sb.append(((NBTTagFloat) tag).getFloat());
                break;
            case 6:
                sb.append(((NBTTagDouble) tag).getDouble());
                break;
            case 7:
                appendByteArray(sb, ((NBTTagByteArray) tag).getByteArray());
                break;
            case 8:
                appendQuoted(sb, ((NBTTagString) tag).getString());
                break;
            case 9:
                int count = ((NBTTagList) tag).tagCount();
                sb.append('[');
                for (int i = 0; i < count; i++) {
                    appendTag(sb, ((NBTTagList) tag).get(i));
                }
                sb.append(']');
                break;
            case 10:
                sb.append('{');
                NBTTagCompound compound = (NBTTagCompound) tag;
                List<String> keys = new ArrayList<>(compound.getKeySet());
                Collections.sort(keys); // String.compareTo 升序(非 CASE_INSENSITIVE)
                for (String key : keys) {
                    // 键长前缀:键含分隔符时不产生歧义
                    sb.append(key.length()).append(':').append(key).append('=');
                    appendTag(sb, compound.getTag(key));
                }
                sb.append('}');
                break;
            case 11:
                appendIntArray(sb, ((NBTTagIntArray) tag).getIntArray());
                break;
            default:
                // 未知/END:只有 id,内容无编码(1.12 无 12 号标签)
                break;
        }
    }

    private static void appendByteArray(StringBuilder sb, byte[] data) {
        sb.append('[');
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(data[i]);
        }
        sb.append(']');
    }

    private static void appendIntArray(StringBuilder sb, int[] data) {
        sb.append('[');
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(data[i]);
        }
        sb.append(']');
    }

    private static void appendQuoted(StringBuilder sb, String value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' || c == '"') {
                sb.append('\\');
            }
            sb.append(c);
        }
        sb.append('"');
    }
}
