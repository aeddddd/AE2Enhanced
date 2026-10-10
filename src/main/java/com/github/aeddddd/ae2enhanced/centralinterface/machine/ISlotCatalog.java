package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import java.util.ArrayList;
import java.util.List;

/**
 * 目标机器的槽位目录：框架层访问该机器库存的唯一入口.
 *
 * <p><b>降级契约</b>：无法判定角色的机器返回 {@code rolesKnown() == false}，
 * 框架（{@link ProductFilter} / {@link ItemTransferHelper}）必须退化为宽松策略，
 * 不得在角色未知时按猜测过滤——猜错会收走玩家的物品。</p>
 */
public interface ISlotCatalog {

    /** 全部槽位，顺序稳定（同一目录对象多次遍历顺序一致）。 */
    List<SlotRef> slots();

    /**
     * 是否已判定出至少一个输出槽角色。
     *
     * <p>为 false 时框架走宽松策略。</p>
     */
    boolean rolesKnown();

    /**
     * 目录是否已失效（目标方块被替换 / chunk 重载 / 机器重建）。
     *
     * <p>失效的目录不得继续用于读写；调用方应丢弃并重建。</p>
     */
    boolean isStale();

    /** 按角色筛选。 */
    default List<SlotRef> withRole(SlotRole... roles) {
        List<SlotRef> result = new ArrayList<>();
        for (SlotRef slot : slots()) {
            for (SlotRole role : roles) {
                if (slot.role() == role) {
                    result.add(slot);
                    break;
                }
            }
        }
        return result;
    }

    /** 可接受本批材料的槽位（{@link SlotRole#INPUT} / {@link SlotRole#CONTAINER}）。 */
    default List<SlotRef> inputSlots() {
        return withRole(SlotRole.INPUT, SlotRole.CONTAINER);
    }

    /** 产物槽位。 */
    default List<SlotRef> outputSlots() {
        return withRole(SlotRole.OUTPUT);
    }

    /**
     * 推料目标槽位.
     *
     * <p>角色已知时只推输入槽；角色未知时推全部槽位。</p>
     */
    default List<SlotRef> pushTargets() {
        return rolesKnown() ? inputSlots() : slots();
    }

    /** 新建目录构造器。 */
    static CatalogBuilder builder() {
        return new CatalogBuilder();
    }
}
