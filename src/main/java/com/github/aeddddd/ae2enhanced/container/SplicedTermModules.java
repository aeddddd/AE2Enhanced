package com.github.aeddddd.ae2enhanced.container;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 可拼接终端的模块清单与 {@code panelMask} 位掩码映射。
 *
 * <p>{@code panelMask} 是阶段 3b 引入的<strong>可见性同步</strong>通道：客户端在本地改布局后把
 * 位掩码发给服务端，服务端据此调整 shift-click 白名单（被关闭的面板，其槽位绝不能接收物品）。
 * 位序固定，只能追加、不能重排——重排会让旧存档里的掩码含义错位。</p>
 *
 * <p>注意这不是布局几何：只有一个"哪些模块开着"的位图，服务端不解析任何坐标
 * （评估文档 §2.3 的架构红线）。</p>
 *
 * <p>槽位组 id（{@link SplicedTermSlotPolicy} 的 {@code MODULE_*}）与模块 id 是两个层次：
 * 一个模块可以拥有多个槽位组（例如 crafting 有矩阵组与输出组，pattern 有可见槽组与样板 in/out 组），
 * 但位掩码只按"模块"粒度开关。</p>
 */
public final class SplicedTermModules {
    /** 位序（固定，只可追加）。 */
    public static final int REPO = 0;
    public static final int PLAYER = 1;
    public static final int VIEWCELL = 2;
    public static final int CRAFTING = 3;
    public static final int STORAGE = 4;
    public static final int UPGRADE = 5;
    public static final int PATTERN = 6;

    /** 可被位掩码管理的模块 id，下标即位号。 */
    private static final List<String> MODULE_IDS = Collections.unmodifiableList(Arrays.asList(
            SplicedTermSlotPolicy.MODULE_REPO,
            SplicedTermSlotPolicy.MODULE_PLAYER,
            SplicedTermSlotPolicy.MODULE_VIEWCELL,
            SplicedTermSlotPolicy.MODULE_CRAFTING_MATRIX,
            SplicedTermSlotPolicy.MODULE_STORAGE,
            SplicedTermSlotPolicy.MODULE_UPGRADE,
            SplicedTermSlotPolicy.MODULE_PATTERN));

    /**
     * 永远可见、不可关闭的模块（仓库与玩家背包）。
     *
     * <p>仓库是终端的主体功能，背包是物品出入的唯一通道；两者被关掉会让终端变得不可用，
     * 因此 {@link #sanitize(int)} 会强制把它们置位。</p>
     */
    public static final int ALWAYS_VISIBLE = bit(REPO) | bit(PLAYER);

    /** 全开。 */
    public static final int ALL = (1 << MODULE_IDS.size()) - 1;

    private SplicedTermModules() {
    }

    public static int bit(int index) {
        return 1 << index;
    }

    public static int bit(String moduleId) {
        int index = indexOf(moduleId);
        return index < 0 ? 0 : bit(index);
    }

    public static int indexOf(String moduleId) {
        return MODULE_IDS.indexOf(moduleId);
    }

    public static String moduleIdAt(int index) {
        return index >= 0 && index < MODULE_IDS.size() ? MODULE_IDS.get(index) : null;
    }

    public static List<String> moduleIds() {
        return MODULE_IDS;
    }

    public static boolean isMaskable(String moduleId) {
        return MODULE_IDS.contains(moduleId);
    }

    public static boolean isVisible(int mask, String moduleId) {
        int b = bit(moduleId);
        return b != 0 && (mask & b) != 0;
    }

    public static boolean isVisible(int mask, int index) {
        return (mask & bit(index)) != 0;
    }

    /** 置位/清位后返回新掩码（不改动入参）。 */
    public static int withVisibility(int mask, String moduleId, boolean visible) {
        int b = bit(moduleId);
        if (b == 0) {
            return mask;
        }
        return visible ? (mask | b) : (mask & ~b);
    }

    /**
     * 归一化：只保留已知位，并强制 {@link #ALWAYS_VISIBLE} 置位。
     *
     * <p>服务端收到客户端发来的掩码时必须先走这里（伪造包、版本差异都会造成越界位）。</p>
     */
    public static int sanitize(int mask) {
        return (mask & ALL) | ALWAYS_VISIBLE;
    }

    /** 掩码里所有置位的模块 id（按位序）。 */
    public static java.util.Set<String> visibleModuleIds(int mask) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<String>();
        for (int i = 0; i < MODULE_IDS.size(); i++) {
            if (isVisible(mask, i)) {
                ids.add(MODULE_IDS.get(i));
            }
        }
        return ids;
    }

    /** 从模块 id 列表构造掩码（未登记的模块忽略）。 */
    public static int maskOf(Iterable<String> visibleModuleIds) {
        int mask = ALWAYS_VISIBLE;
        if (visibleModuleIds != null) {
            for (String moduleId : visibleModuleIds) {
                mask = withVisibility(mask, moduleId, true);
            }
        }
        return sanitize(mask);
    }
}
