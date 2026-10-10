package com.github.aeddddd.ae2enhanced.centralinterface.machine;

/**
 * 槽位在机器中的角色.
 *
 * <p>框架层反复制（{@link ProductFilter}）与原子推料（{@link ItemTransferHelper}）都依赖这个分类：
 * 只有能判定角色，才能在"产物与输入同型"时区分二者，也才能只回收本批材料。</p>
 *
 * <p>{@link #UNKNOWN} 是合法结果，不是错误：一部分机器的侧面对外只暴露一个笼统的
 * {@code IItemHandler}，无法区分输入/输出。此时框架退化为"非本批输入即产物"的宽松策略，而不是猜错把玩家的物品收走。</p>
 */
public enum SlotRole {

    /** 仅可插入的输入槽。 */
    INPUT,

    /** 机器的产物槽。 */
    OUTPUT,

    /** 燃料槽等消耗型输入（推料时可接受材料，但收集时不得当产物）。 */
    FUEL,

    /** 容器/空桶返还槽。 */
    CONTAINER,

    /** 升级、电池等不应被触碰的槽。 */
    UPGRADE,

    /** 无法判定角色；框架对这类槽采取宽松策略。 */
    UNKNOWN;

    /** 是否为"可接受本批材料"的槽位角色。 */
    public boolean acceptsInput() {
        return this == INPUT || this == CONTAINER;
    }

    /** 是否为"可产出产物"的槽位角色。 */
    public boolean producesOutput() {
        return this == OUTPUT;
    }
}
