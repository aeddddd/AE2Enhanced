package com.github.aeddddd.ae2enhanced.client.gui.panel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.inventory.Slot;

import com.github.aeddddd.ae2enhanced.client.gui.dock.ModulePanel;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;

/**
 * 承接容器固定槽位的模块基类。
 *
 * <p>职责边界很窄：把"这批槽位属于哪个模块组"登记给 {@code SplicedTermSlotPolicy}，并在面板不可见时
 * 把槽位挪到屏幕外。几何（{@code layoutSlots()}）与渲染仍由子类自己实现——不同功能的格子排布完全不同，
 * 强行抽象只会变成参数堆。</p>
 *
 * <p><strong>可见性 → 槽位登记</strong>是"单一可见性来源"的落点：面板不可见时注销模块组，
 * shift-click 判决（{@code canShiftClickFrom/Into}）随之拒绝这些槽位，不再需要
 * {@code isSlotEnabled} / {@code xPos=-9000} / {@code visible} 三套并行机制。</p>
 */
public abstract class SlotHostPanel extends ModulePanel {

    protected final ContainerSplicedTerm container;
    /** 模块组 id → 该组槽位（LinkedHashMap 保序）。 */
    private final Map<String, List<Slot>> slotGroups = new LinkedHashMap<String, List<Slot>>();

    protected SlotHostPanel(ContainerSplicedTerm container) {
        this.container = container;
    }

    /**
     * 登记一个模块组（构造期调用一次）。
     *
     * @param moduleId 组 id，与 {@code SplicedTermSlotPolicy} 的模块常量对应
     */
    protected final void addSlotGroup(String moduleId, List<Slot> slots) {
        List<Slot> copy = new ArrayList<Slot>();
        if (slots != null) {
            for (Slot slot : slots) {
                if (slot == null) {
                    continue;
                }
                copy.add(slot);
                registerSlot(slot); // 归属：dock 的命中测试与遮挡判定依赖 ownsSlot()
            }
        }
        slotGroups.put(moduleId, copy);
    }

    /** 全部自有槽位（构造期固定，不随可见性变化）。 */
    protected final List<Slot> ownedSlotList() {
        List<Slot> all = new ArrayList<Slot>();
        for (List<Slot> group : slotGroups.values()) {
            all.addAll(group);
        }
        return all;
    }

    /** 面板可见 → 登记本面板的模块组；不可见 → 注销（槽位随之不参与交互）。 */
    protected final void syncHosting() {
        for (Map.Entry<String, List<Slot>> entry : slotGroups.entrySet()) {
            container.slotPolicy().setModuleSlots(entry.getKey(),
                    visible ? entry.getValue() : Collections.<Slot>emptyList());
        }
    }

    /** 面板不可见时把所有自有槽位挪到屏幕外。 */
    protected final void hideOwnedSlots() {
        for (List<Slot> group : slotGroups.values()) {
            for (Slot slot : group) {
                hideSlot(slot);
            }
        }
    }

    /** 可见性 + 槽位登记的统一入口：子类的 {@code layoutSlots()} 第一件事就是调用它。 */
    protected final boolean prepareLayout() {
        syncHosting();
        if (visible) {
            return true;
        }
        hideOwnedSlots();
        return false;
    }
}
