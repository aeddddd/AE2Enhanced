package com.github.aeddddd.ae2enhanced.client.gui.planview;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.specialcrafting.SpecialPlanClientCache;
import com.github.aeddddd.ae2enhanced.config.ClientPreferences;
import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialPlanInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;

import java.util.*;

/**
 * 合成计划/CPU 状态界面的列表视图助手: 过滤(搜索) + 混合排序 + 模式持久化.
 * <p>visual 列表在每次 postUpdate 后由本类从 storage/active/pending/missing
 * 三类列表整体重建: 合并同名项总量, 应用搜索过滤, 再按当前模式完整排序.</p>
 */
public final class PlanViewHelper {

    private PlanViewHelper() {
    }

    /** 列表统计(在视图重建时顺带计算, 供汇总统计行显示; 不受搜索过滤影响). */
    public static final class ViewStats {
        /** 有总量的物品种类数(三表并集). */
        public int total;
        /** 第一张表(storage)中数量 > 0 的种类数. */
        public int first;
        /** 第二张表(confirm: pending / cpu: active)中数量 > 0 的种类数. */
        public int second;
        /** 第三张表(confirm: missing / cpu: pending)中数量 > 0 的种类数. */
        public int third;
    }

    // ==================== 模式存取(持久化到客户端配置) ====================

    public static PlanSortMode confirmMode() {
        return parse(ClientPreferences.current().craftingDisplay.confirmSortMode, PlanSortMode.CONFIRM_CYCLE);
    }

    public static PlanSortMode cpuMode() {
        return parse(ClientPreferences.current().craftingDisplay.cpuSortMode, PlanSortMode.CPU_CYCLE);
    }

    public static PlanSortMode cycleConfirmMode(boolean backwards) {
        PlanSortMode next = cycle(PlanSortMode.CONFIRM_CYCLE, confirmMode(), backwards);
        ClientPreferences.setConfirmSortMode(next.name());
        ConfigManager.sync(AE2Enhanced.MOD_ID, Config.Type.INSTANCE);
        return next;
    }

    public static PlanSortMode cycleCpuMode(boolean backwards) {
        PlanSortMode next = cycle(PlanSortMode.CPU_CYCLE, cpuMode(), backwards);
        ClientPreferences.setCpuSortMode(next.name());
        ConfigManager.sync(AE2Enhanced.MOD_ID, Config.Type.INSTANCE);
        return next;
    }

    private static PlanSortMode parse(String raw, PlanSortMode[] cycle) {
        if (raw != null) {
            try {
                PlanSortMode mode = PlanSortMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
                for (PlanSortMode allowed : cycle) {
                    if (allowed == mode) {
                        return mode;
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // 非法值回落默认
            }
        }
        return cycle[0];
    }

    private static PlanSortMode cycle(PlanSortMode[] cycle, PlanSortMode current, boolean backwards) {
        int idx = 0;
        for (int i = 0; i < cycle.length; i++) {
            if (cycle[i] == current) {
                idx = i;
                break;
            }
        }
        int next = (idx + (backwards ? -1 : 1)) % cycle.length;
        if (next < 0) {
            next += cycle.length;
        }
        return cycle[next];
    }

    // ==================== 视图重建(过滤 + 排序) ====================

    /** 合成确认界面: 从 storage/pending/missing 重建 visual 并排序, 顺带统计. */
    public static void refreshConfirm(List<IAEItemStack> visual, IItemList<IAEItemStack> storage,
            IItemList<IAEItemStack> pending, IItemList<IAEItemStack> missing,
            String filter, PlanSortMode mode, ViewStats stats) {
        collect(visual, storage, pending, missing, filter, stats);
        visual.sort(confirmComparator(mode, visual, missing, pending));
    }

    /** CPU 状态界面: 从 storage/active/pending 重建 visual 并排序, 顺带统计. */
    public static void refreshCpu(List<IAEItemStack> visual, IItemList<IAEItemStack> storage,
            IItemList<IAEItemStack> active, IItemList<IAEItemStack> pending,
            String filter, PlanSortMode mode, CraftingDurationTracker durations, ViewStats stats) {
        collect(visual, storage, active, pending, filter, stats);
        visual.sort(cpuComparator(mode, active, pending, durations));
    }

    /** 三列表并集重建 visual: 总量 > 0 且匹配搜索过滤的项, 代表项 stackSize = 三表总量. */
    private static void collect(List<IAEItemStack> visual, IItemList<IAEItemStack> a,
            IItemList<IAEItemStack> b, IItemList<IAEItemStack> c, String filter, ViewStats stats) {
        visual.clear();
        String f = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        int[] counts = new int[3];
        collectFrom(visual, seen, counts, 0, a, a, b, c, f);
        collectFrom(visual, seen, counts, 1, b, a, b, c, f);
        collectFrom(visual, seen, counts, 2, c, a, b, c, f);
        if (stats != null) {
            // seen 在过滤前加入, 统计不受搜索影响
            stats.total = seen.size();
            stats.first = counts[0];
            stats.second = counts[1];
            stats.third = counts[2];
        }
    }

    private static void collectFrom(List<IAEItemStack> visual, Set<String> seen, int[] counts, int listIdx,
            IItemList<IAEItemStack> list,
            IItemList<IAEItemStack> a, IItemList<IAEItemStack> b, IItemList<IAEItemStack> c, String filter) {
        for (IAEItemStack s : list) {
            if (s.getStackSize() > 0) {
                counts[listIdx]++;
            }
            long total = countOf(a, s) + countOf(b, s) + countOf(c, s);
            if (total <= 0) {
                continue;
            }
            if (!seen.add(keyOf(s))) {
                continue;
            }
            if (!filter.isEmpty() && !displayName(s).toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            IAEItemStack rep = s.copy();
            rep.setStackSize(total);
            visual.add(rep);
        }
    }

    // ==================== 混合排序比较器 ====================

    private static Comparator<IAEItemStack> confirmComparator(PlanSortMode mode, List<IAEItemStack> visual,
            IItemList<IAEItemStack> missing, IItemList<IAEItemStack> pending) {
        NameCache names = new NameCache();
        switch (mode) {
            case TOTAL_DESC:
                return Comparator.comparingLong(IAEItemStack::getStackSize).reversed().thenComparing(names);
            case TO_CRAFT_DESC:
                return Comparator.<IAEItemStack> comparingLong(s -> countOf(pending, s)).reversed()
                        .thenComparing(Comparator.comparingLong(IAEItemStack::getStackSize).reversed())
                        .thenComparing(names);
            case SPECIAL_FIRST: {
                // 预计算: 0=特殊计划(自增殖/循环链), 1=有调用次数的普通样板, 2=其余
                Map<String, Integer> groups = new HashMap<>();
                Map<String, Long> metrics = new HashMap<>();
                for (IAEItemStack s : visual) {
                    int group = 2;
                    long metric = s.getStackSize();
                    SpecialPlanInfo info = SpecialPlanClientCache.infoFor(s.asItemStackRepresentation());
                    if (info != null) {
                        SpecialPlanInfo.Entry entry = info.entryFor(s);
                        if (entry != null) {
                            group = 0;
                            metric = entry.kind == SpecialPlanInfo.KIND_SELF_DUP ? entry.totalCrafts : entry.rounds;
                        } else {
                            long calls = info.callCountOf(s);
                            if (calls > 0) {
                                group = 1;
                                metric = calls;
                            }
                        }
                    }
                    groups.put(keyOf(s), group);
                    metrics.put(keyOf(s), metric);
                }
                return Comparator.<IAEItemStack> comparingInt(s -> groups.get(keyOf(s)))
                        .thenComparing(Comparator.<IAEItemStack> comparingLong(s -> metrics.get(keyOf(s))).reversed())
                        .thenComparing(names);
            }
            case MISSING_FIRST:
            default:
                return Comparator.<IAEItemStack> comparingInt(s -> countOf(missing, s) > 0 ? 0 : 1)
                        .thenComparing(Comparator.comparingLong(IAEItemStack::getStackSize).reversed())
                        .thenComparing(names);
        }
    }

    private static Comparator<IAEItemStack> cpuComparator(PlanSortMode mode,
            IItemList<IAEItemStack> active, IItemList<IAEItemStack> pending,
            CraftingDurationTracker durations) {
        NameCache names = new NameCache();
        switch (mode) {
            case TOTAL_DESC:
                return Comparator.comparingLong(IAEItemStack::getStackSize).reversed().thenComparing(names);
            case DURATION_DESC:
                return Comparator.<IAEItemStack> comparingInt(s -> countOf(active, s) > 0 ? 0 : 1)
                        .thenComparing(Comparator.<IAEItemStack> comparingLong(durations::elapsedMs).reversed())
                        .thenComparing(Comparator.<IAEItemStack> comparingLong(s -> countOf(active, s)
                                + countOf(pending, s)).reversed())
                        .thenComparing(names);
            case ACTIVE_FIRST:
            default:
                return Comparator.<IAEItemStack> comparingInt(s -> countOf(active, s) > 0 ? 0 : 1)
                        .thenComparing(Comparator.<IAEItemStack> comparingLong(s -> countOf(active, s)
                                + countOf(pending, s)).reversed())
                        .thenComparing(names);
        }
    }

    // ==================== 工具 ====================

    /** 标题栏截断: 超出宽度时以 ... 结尾, 为搜索框等控件留位. */
    public static String truncateTitle(String title, int maxWidth) {
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        if (fr.getStringWidth(title) <= maxWidth) {
            return title;
        }
        return fr.trimStringToWidth(title, maxWidth - fr.getStringWidth("...")) + "...";
    }

    /** 物品身份键(注册名 + meta + NBT), 用于跨列表去重与计时跟踪. */
    public static String keyOf(IAEItemStack s) {
        ItemStack is = s.asItemStackRepresentation();
        if (is.isEmpty()) {
            return "empty";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(Item.REGISTRY.getNameForObject(is.getItem()));
        sb.append('@').append(is.getMetadata());
        NBTTagCompound tag = is.getTagCompound();
        if (tag != null) {
            sb.append('#').append(tag);
        }
        return sb.toString();
    }

    private static long countOf(IItemList<IAEItemStack> list, IAEItemStack s) {
        IAEItemStack found = list.findPrecise(s);
        return found == null ? 0L : found.getStackSize();
    }

    private static String displayName(IAEItemStack s) {
        ItemStack is = s.asItemStackRepresentation();
        return is.isEmpty() ? "" : is.getDisplayName();
    }

    /** 显示名缓存比较器(排序兜底, 避免比较时重复构造 ItemStack). */
    private static final class NameCache implements Comparator<IAEItemStack> {
        private final Map<String, String> cache = new HashMap<>();

        @Override
        public int compare(IAEItemStack a, IAEItemStack b) {
            return name(a).compareToIgnoreCase(name(b));
        }

        private String name(IAEItemStack s) {
            String key = keyOf(s);
            String name = cache.get(key);
            if (name == null) {
                name = displayName(s);
                cache.put(key, name);
            }
            return name;
        }
    }
}
