package com.github.aeddddd.ae2enhanced.centralinterface;

/**
 * 单个绑定目标的执行模式.
 *
 * <p>由 {@link #resolve(IRemoteHandler, boolean)} 依据 handler 声明的能力与"升级槽中是否装有
 * 虚拟并行卡"推导，{@link DualityCentralInterface#pushPattern} 据此在物理发配与虚拟批量之间分派。</p>
 *
 * <p>语义：<b>装卡就纯虚拟，拿走卡自然回归物理</b>，不提供额外开关；
 * 而"不存在物理交互空间"的工作台类设备（如 Extended Crafting / 终焉工作台）常驻虚拟，
 * 与是否装卡无关。</p>
 */
public enum ExecutionMode {

    /**
     * 物理发配：把材料实际推入目标机器，随后按 tick 收集产物.
     *
     * <p>覆盖两种情形：① handler 只声明 {@link HandlerCapabilities#PHYSICAL}；
     * ② handler 两种能力都具备，但<b>未装虚拟并行卡</b>。</p>
     */
    PHYSICAL,

    /**
     * 设备本机只有虚拟语义，装不装卡都走虚拟批量.
     *
     * <p>用于"不存在物理交互空间"的设备：其物理方法全部是空实现，若按 {@link #PHYSICAL}
     * 处理会导致订单永远无法完成。并行数取其
     * {@link IVirtualBatchCraftingHandler#getDefaultParallel()}（装卡时可被卡等级抬高）。</p>
     */
    VIRTUAL_NATIVE,

    /**
     * 双能力设备 + 已装虚拟并行卡 → 该接口进入纯虚拟模式.
     *
     * <p>并行数直接取卡等级（再受 CPU 传入的任务剩余数上限钳制），
     * {@link IVirtualBatchCraftingHandler#getDefaultParallel()} 不参与。</p>
     */
    VIRTUAL_CARDED;

    /**
     * 推导执行模式.
     *
     * @param handler handler，可为 {@code null}（视为不具备任何能力）
     * @param hasCard 升级槽中是否装有虚拟并行卡
     * @return 执行模式，永不为 {@code null}
     */
    public static ExecutionMode resolve(IRemoteHandler handler, boolean hasCard) {
        if (handler == null) {
            return PHYSICAL;
        }
        boolean virtual = handler.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)
                && handler instanceof IVirtualBatchCraftingHandler;
        if (!virtual) {
            // 无虚拟能力（含"声称支持虚拟但未实现接口"的退化情况）：一律物理发配，虚拟卡对其不生效。
            // 不回退失败、也不跳过该目标 —— 避免装卡后整台接口对这类目标静默停摆。
            return PHYSICAL;
        }
        if (!handler.hasCapability(HandlerCapabilities.PHYSICAL)) {
            // 纯虚拟设备（工作台类）：不存在物理交互空间，常驻虚拟。
            return VIRTUAL_NATIVE;
        }
        return hasCard ? VIRTUAL_CARDED : PHYSICAL;
    }

    /**
     * 是否为虚拟路径（{@link #VIRTUAL_NATIVE} 或 {@link #VIRTUAL_CARDED}）。
     */
    public boolean isVirtual() {
        return this != PHYSICAL;
    }
}
