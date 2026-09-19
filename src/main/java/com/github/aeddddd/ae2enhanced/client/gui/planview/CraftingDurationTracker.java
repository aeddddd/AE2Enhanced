package com.github.aeddddd.ae2enhanced.client.gui.planview;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.world.World;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

/**
 * CPU 状态界面进行中子项的客户端持续时间跟踪.
 * <p>物品进入 active 列表(开始发配)时记时, 离开 active 时清除.
 * 跟踪器跨 GUI 会话共享: 关闭并重开界面后计时延续, 不会清零;
 * 检测到世界/服务器切换时自动重置, 避免旧会话计时残留.</p>
 */
public class CraftingDurationTracker {

    private final Map<String, Long> activeSince = new HashMap<>();

    /** 上次 update 时所在世界(引用比较), 用于检测换图/换服. */
    private World lastWorld;

    /** 每次 postUpdate 后调用, 同步 active 列表的开始时间戳. */
    public void update(IItemList<IAEItemStack> active) {
        World world = Minecraft.getMinecraft().world;
        if (world != this.lastWorld) {
            this.activeSince.clear();
            this.lastWorld = world;
        }
        long now = System.currentTimeMillis();
        Set<String> current = new java.util.HashSet<>();
        for (IAEItemStack s : active) {
            if (s.getStackSize() > 0) {
                String key = PlanViewHelper.keyOf(s);
                current.add(key);
                activeSince.putIfAbsent(key, now);
            }
        }
        activeSince.keySet().retainAll(current);
    }

    /** 该物品处于 active 状态的已持续时间(毫秒), 未在 active 中返回 0. */
    public long elapsedMs(IAEItemStack s) {
        Long since = activeSince.get(PlanViewHelper.keyOf(s));
        return since == null ? 0L : Math.max(0L, System.currentTimeMillis() - since);
    }

    /** 时长格式化: 60s 内 "12s", 1h 内 "3:24", 其余 "1:02:33". */
    public static String format(long ms) {
        long sec = ms / 1000L;
        if (sec < 60L) {
            return sec + "s";
        }
        long min = sec / 60L;
        if (min < 60L) {
            return min + ":" + pad(sec % 60L);
        }
        return (min / 60L) + ":" + pad(min % 60L) + ":" + pad(sec % 60L);
    }

    private static String pad(long v) {
        return v < 10L ? "0" + v : Long.toString(v);
    }
}
