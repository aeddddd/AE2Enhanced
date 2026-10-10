package com.github.aeddddd.ae2enhanced.util;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.ItemSorters;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;

import java.util.Comparator;
import java.util.List;

/**
 * 终端分页排序的安全入口（AE2 {@code ItemSorters} 的兜底封装）。
 *
 * <p>背景：R3 分页的排序完全依赖 AE2 的 {@code ItemSorters.CONFIG_BASED_SORT_BY_*}，而 NAME 比较器
 * 经 {@code Platform.getItemDisplayName} 调 {@code AEItemStack.getDisplayName()}。一旦这个方法在
 * 运行期解析不到（专用服务端 JVM 上实测到 {@code NoSuchMethodError}，详见 E 系验收记录），
 * <strong>整个分页请求都会失败</strong>：终端仓库直接空白（旧的 {@code fallbackPageQuery}）或搜索失败，
 * 而不是"排序退化"。</p>
 *
 * <p>因此这里统一收口：先按 AE2 原语义排序；只有在比较器本身抛错时才退回到本类的比较器
 * （名字用物品栈的显示名，取不到再用注册名；MOD 用命名空间；AMOUNT 用数量），并只记一条日志。
 * 语义在原路径可用时<strong>完全不变</strong>，只是不再让"排序器不可用"升级成"仓库空白"。</p>
 */
public final class TerminalItemSort {

    /** 只提醒一次：排序退化比刷屏更重要。 */
    private static boolean warned;

    private TerminalItemSort() {
    }

    /**
     * 按 AE2 语义排序；AE2 比较器不可用时退回到本地比较器。
     *
     * @param list   待排序列表（原地排序）
     * @param sortBy 排序字段
     * @param dir    排序方向
     */
    public static void sort(List<IAEItemStack> list, SortOrder sortBy, SortDir dir) {
        if (list == null || list.size() < 2) {
            return;
        }
        try {
            ItemSorters.setDirection(dir == SortDir.DESCENDING ? SortDir.DESCENDING : SortDir.ASCENDING);
            ItemSorters.init();
            list.sort(aeComparator(sortBy));
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                AE2Enhanced.LOGGER.warn("[AE2E] AE2 ItemSorters 不可用（{}），终端分页改用回退比较器（名字/注册名 + 数量）", t.toString());
            }
            list.sort(fallbackComparator(sortBy, dir));
        }
    }

    private static Comparator<IAEItemStack> aeComparator(SortOrder sortBy) {
        if (sortBy == SortOrder.MOD) {
            return ItemSorters.CONFIG_BASED_SORT_BY_MOD;
        }
        if (sortBy == SortOrder.AMOUNT) {
            return ItemSorters.CONFIG_BASED_SORT_BY_SIZE;
        }
        if (sortBy == SortOrder.INVTWEAKS) {
            return ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS;
        }
        return ItemSorters.CONFIG_BASED_SORT_BY_NAME;
    }

    /**
     * 回退比较器：与 AE2 比较器同序（MOD=命名空间、AMOUNT=数量、其余=名字）。
     *
     * <p><b>必须全程零分配</b>：这里跑在服务端的每一次分页请求里（n log n 次比较），
     * 一旦每次比较都 {@code asItemStackRepresentation()} 造新 ItemStack，就会触发 Forge 的
     * {@code gatherCapabilities} 事件 —— 实测能把一个 server tick 卡到 60s 被看门狗强杀
     * （见验收文档 I 节）。因此名字类排序一律用 {@link IAEItemStack#getItem()} 的注册名，
     * 不碰任何会 new ItemStack 的接口。</p>
     */
    private static Comparator<IAEItemStack> fallbackComparator(SortOrder sortBy, SortDir dir) {
        Comparator<IAEItemStack> base;
        if (sortBy == SortOrder.MOD) {
            base = Comparator.comparing(TerminalItemSort::namespaceOf, String.CASE_INSENSITIVE_ORDER);
        } else if (sortBy == SortOrder.AMOUNT) {
            base = Comparator.comparingLong(IAEItemStack::getStackSize);
        } else {
            // NAME 与 INVTWEAKS 都退回"注册名序"（无 ItemStack 就没有本地化显示名可用）
            base = Comparator.comparing(TerminalItemSort::registryNameOf, String.CASE_INSENSITIVE_ORDER);
        }
        base = base.thenComparing(TerminalItemSort::registryNameOf);
        return dir == SortDir.DESCENDING ? base.reversed() : base;
    }

    private static String namespaceOf(IAEItemStack stack) {
        String registry = registryNameOf(stack);
        int split = registry.indexOf(':');
        return split > 0 ? registry.substring(0, split) : registry;
    }

    /** 注册名（零分配：只用 {@code getItem()}/{@code getItemDamage()}）。 */
    private static String registryNameOf(IAEItemStack stack) {
        if (stack == null) {
            return "";
        }
        try {
            net.minecraft.item.Item item = stack.getItem();
            if (item == null || item.getRegistryName() == null) {
                return "";
            }
            String name = item.getRegistryName().toString();
            int damage = stack.getItemDamage();
            return damage == 0 ? name : name + "@" + damage;
        } catch (Throwable ignored) {
            return "";
        }
    }

}
