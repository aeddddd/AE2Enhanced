package com.github.aeddddd.ae2enhanced.centralinterface.machine;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 机器"是否正在处理本批材料"的判定策略.
 *
 * <p><b>为什么必须有 grace</b>：推料与"机器开始处理"不在同一 tick —— 材料刚进机器的那个 tick，
 * {@code progress} 往往仍是 0。若不做保护，框架会立刻判定"已空闲"并去收产物，
 * 把刚推入的材料当产物收回网络。</p>
 */
@FunctionalInterface
public interface MachineIdle {

    /**
     * 机器当前是否正在处理（true = 不能收集产物）。
     *
     * @param te 目标 TileEntity；可能为 null（调用方应视为"无法判定"）
     */
    boolean isWorking(World world, BlockPos pos, TileEntity te);

    /**
     * 推料后的保护 tick 数：此窗口内一律判定为"未空闲"。
     *
     * <p>默认 2；条件启动型、进度回零较慢的机器应放大。</p>
     */
    default int graceTicks() {
        return 2;
    }

    /** 永不处理中（材料插入即自动开工的简单机器，如熔炉、通用加工机）。 */
    static MachineIdle none() {
        return (world, pos, te) -> false;
    }
}
