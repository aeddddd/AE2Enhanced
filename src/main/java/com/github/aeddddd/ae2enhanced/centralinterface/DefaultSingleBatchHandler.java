package com.github.aeddddd.ae2enhanced.centralinterface;

import com.github.aeddddd.ae2enhanced.centralinterface.machine.AbstractMachineHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.CatalogBuilder;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.ISlotCatalog;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineIdle;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineSpec;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 通用兜底处理器：目标方块没有匹配到任何专用 handler 时接管的机器.
 *
 * <p>适用场景：熔炉、通用加工机器等"具备 {@code IItemHandler} 能力、物品插入后自动开始处理、
 * 产物留在输出槽"的设备。</p>
 *
 * <p>本类是通用骨架 {@link AbstractMachineHandler} 的一个表项最简形态：自带一条
 * <b>兜底表项</b>（{@link MachineSpec}，不匹配任何 blockId），只回答"目录怎么来"（六面 capability）
 * 与"什么时候算处理中"（永远不是）。推料（两阶段原子）、收集（两阶段 + 反复制过滤）、回退（按台账
 * 精确取回）、清空输出槽、空闲与完成判定全部由框架提供。</p>
 *
 * <p>通用机器的六面对外只暴露一个笼统的 {@code IItemHandler}，无法区分输入/输出槽，因此目录角色全为
 * {@link com.github.aeddddd.ae2enhanced.centralinterface.machine.SlotRole#UNKNOWN}，
 * 框架对这类机器按"非本批输入即产物"的宽松策略处理。
 * 不去猜角色——猜错会把玩家的燃料/工具收进 ME 网络。</p>
 */
public class DefaultSingleBatchHandler extends AbstractMachineHandler {

    /**
     * 兜底表项：不匹配任何 blockId，仅由 {@code HandlerRegistry} 作为默认返回。
     *
     * <p>不能用 {@code exactIds()} 表达"什么都不匹配"——那样会被误当成匹配了空字符串的普通表项，
     * 故用具名的 {@link MachineSpec.Builder#fallback()}。</p>
     */
    @Override
    protected MachineSpec defineSpec() {
        return MachineSpec.builder().fallback().access(this).build();
    }

    @Override
    protected ISlotCatalog catalogFor(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        return CatalogBuilder.facesOf(world.getTileEntity(pos));
    }

    @Override
    protected MachineIdle idleGate(World world, BlockPos pos) {
        // 通用机器把材料插入后即自行开工，没有可观测的"处理中"信号
        return MachineIdle.none();
    }
}
