package com.github.aeddddd.ae2enhanced.multiblock;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;

import com.github.aeddddd.ae2enhanced.structure.IMultiblockStructure;

import appeng.api.networking.security.IActionSource;

/**
 * 多方块控制器标记接口.
 * <p>所有控制器方块实体（超维度、装配、计算核心）均实现此接口,
 * 控制器自身作为 AE2 网络节点,任意结构方块均可并网.</p>
 */
public interface IMultiblockController {

    /**
     * @return 多方块是否已成形.
     */
    boolean isFormed();

    /**
     * @return 控制器所在位置.
     */
    BlockPos getControllerPos();

    /**
     * @return 是否正在显示结构投影.
     */
    boolean isShowingStructureProjection();

    /**
     * 切换结构投影显示状态.
     */
    void toggleStructureProjection();

    /**
     * 获取当前控制器对应的多方块结构定义.
     */
    @Nullable
    IMultiblockStructure getStructure();

    /**
     * 装配结构：触发 {@link #onAssemble()} 并将成形状态置为 true.
     */
    void assemble();

    /**
     * 拆解结构：触发 {@link #onDisassemble()} 并将成形状态置为 false.
     */
    void disassemble();

    /**
     * 设置成形状态.
     */
    void setFormed(boolean formed);

    /**
     * 结构装配成功、状态即将置为成形时调用.
     * <p>子类可在此初始化存储、CPU 池等资源.</p>
     */
    default void onAssemble() {
    }

    /**
     * 结构拆解、状态即将置为未成形时调用.
     * <p>子类可在此释放存储、CPU 池等资源.</p>
     */
    default void onDisassemble() {
    }

    /**
     * 返回是否可作为虚拟 Crafting CPU 源.
     * <p>默认返回 false；仅超因果计算核心等实际提供虚拟 CPU 的控制器应返回 true.</p>
     */
    default boolean isVirtualCpuAvailable() {
        return false;
    }

    /**
     * 返回虚拟 CPU 的并行上限.
     */
    default int getVirtualCpuParallelLimit() {
        return 0;
    }

    /**
     * 返回用于 AE2 网络操作的动作来源.
     * <p>默认返回空源；作为网络节点的控制器应返回自身的机器源.</p>
     */
    default IActionSource getActionSource() {
        return IActionSource.empty();
    }
}
