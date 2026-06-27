package com.github.aeddddd.ae2enhanced.client.gui.util;

import appeng.container.slot.AppEngSlot;
import net.minecraft.inventory.Slot;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 槽位位置管理器.
 * <p>
 * 抽象 GUI 中常见的"保存原始槽位坐标 → 恢复 → 按条件偏移"逻辑,
 * 用于处理动态高度 GUI(如 TALL 终端样式)中槽位随 extraHeight 下移的场景.
 * <p>
 * 典型用法：
 * <pre>
 *   SlotPositionManager mgr = new SlotPositionManager();
 *   mgr.captureAll(slots);                 // 首次 initGui 保存
 *   mgr.restoreAndOffset(slots, extra,    // 后续 initGui 恢复并偏移
 *       s -> !(s instanceof ViewCellSlot));
 * </pre>
 */
public class SlotPositionManager {

    private final Map<Slot, Integer> originalY = new IdentityHashMap<>();
    private boolean captured = false;

    /**
     * 一次性捕获所有槽位的原始 y 坐标.
     * 对 {@link AppEngSlot} 使用 {@link AppEngSlot#getY()}(即 defY)保存,
     * 以绕过其他代码对 yPos 的临时修改.
     */
    public void captureAll(Iterable<Slot> slots) {
        if (this.captured) return;
        for (Slot s : slots) {
            if (s instanceof AppEngSlot) {
                this.originalY.put(s, ((AppEngSlot) s).getY());
            } else {
                this.originalY.put(s, s.yPos);
            }
        }
        this.captured = true;
    }

    /**
     * 恢复所有槽位到原始 y 坐标,并对满足 {@code shouldOffset} 条件的槽位追加偏移量.
     *
     * @param slots        当前槽位列表
     * @param offset       要追加的 y 偏移(通常为 extraHeight)
     * @param shouldOffset 判断该槽位是否需要追加偏移；viewCell 等固定位置槽位应排除
     */
    public void restoreAndOffset(Iterable<Slot> slots, int offset, Predicate<Slot> shouldOffset) {
        for (Slot s : slots) {
            Integer original = this.originalY.get(s);
            if (original != null) {
                s.yPos = original;
            }
            if (offset != 0 && shouldOffset != null && shouldOffset.test(s)) {
                s.yPos += offset;
            }
        }
    }

    /**
     * 同 {@link #restoreAndOffset(Iterable, int, Predicate)},但额外提供一组始终排除的槽位引用.
     */
    public void restoreAndOffset(Iterable<Slot> slots, int offset, Predicate<Slot> shouldOffset, Set<Slot> excluded) {
        for (Slot s : slots) {
            Integer original = this.originalY.get(s);
            if (original != null) {
                s.yPos = original;
            }
            if (offset != 0 && shouldOffset != null && shouldOffset.test(s)
                    && (excluded == null || !excluded.contains(s))) {
                s.yPos += offset;
            }
        }
    }

    public boolean isCaptured() {
        return this.captured;
    }

    public void clear() {
        this.originalY.clear();
        this.captured = false;
    }
}
