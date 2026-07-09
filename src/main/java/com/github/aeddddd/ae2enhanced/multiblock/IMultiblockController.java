package com.github.aeddddd.ae2enhanced.multiblock;

import net.minecraft.core.BlockPos;

import appeng.api.networking.security.IActionSource;

/**
 * 多方块控制器标记接口。
 * <p>所有控制器方块实体（超维度、装配、计算核心）均实现此接口，供通用 ME 接口查找与委托。</p>
 */
public interface IMultiblockController {

    /**
     * @return 多方块是否已成形。
     */
    boolean isFormed();

    /**
     * @return 控制器所在位置。
     */
    BlockPos getControllerPos();

    /**
     * 结构装配时调用，记录某个接口位置属于本控制器。
     */
    void attachInterface(BlockPos interfacePos);

    /**
     * 结构拆解时调用，移除接口位置记录。
     */
    void detachInterface(BlockPos interfacePos);

    /**
     * 结构装配成功、状态即将置为成形时调用。
     * <p>子类可在此初始化存储、CPU 池等资源。</p>
     */
    default void onAssemble() {
    }

    /**
     * 结构拆解、状态即将置为未成形时调用。
     * <p>子类可在此释放存储、CPU 池等资源。</p>
     */
    default void onDisassemble() {
    }

    /**
     * 返回是否可作为虚拟 Crafting CPU 源。
     * <p>默认返回 false；仅超因果计算核心等实际提供虚拟 CPU 的控制器应返回 true。</p>
     */
    default boolean isVirtualCpuAvailable() {
        return false;
    }

    /**
     * 返回虚拟 CPU 的并行上限。
     */
    default int getVirtualCpuParallelLimit() {
        return 0;
    }

    /**
     * 返回用于 AE2 网络操作的动作来源。
     * <p>默认由绑定的通用 ME 接口节点提供；没有可用接口时返回空源。</p>
     */
    default IActionSource getActionSource() {
        return IActionSource.empty();
    }
}
