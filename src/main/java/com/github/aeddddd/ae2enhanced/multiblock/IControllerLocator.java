package com.github.aeddddd.ae2enhanced.multiblock;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;

/**
 * 控制器定位器。
 * <p>当通用 ME 接口丢失控制器坐标时，按三种结构的相对坐标扫描可能的控制器位置，
 * 用于 chunk 加载顺序不同或 NBT 异常时的容错恢复。</p>
 */
public interface IControllerLocator {

    /**
     * 根据接口位置尝试定位所属控制器。
     *
     * @param level        世界
     * @param interfacePos 通用 ME 接口位置
     * @return 找到的有效控制器位置；未找到返回 {@code null}
     */
    @Nullable
    BlockPos locateController(Level level, BlockPos interfacePos);

    /**
     * 默认实现：扫描三种结构在所有水平朝向下对应的控制器位置，并验证结构完整性。
     */
    static IControllerLocator defaultLocator() {
        return (level, interfacePos) -> {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                // 超维度仓储：接口相对控制器坐标 (0,0,4)
                BlockPos hd = interfacePos.subtract(HyperdimensionalStructure.rotate(new BlockPos(0, 0, 4), facing));
                if (level.getBlockState(hd).is(ModBlocks.HYPERDIMENSIONAL_CONTROLLER.get())
                        && HyperdimensionalStructure.validate(level, hd)) {
                    return hd;
                }

                // 装配枢纽：几何中心为原点，控制器在 (0,0,-7)，接口在 PART1_SET 中
                for (BlockPos rel : AssemblyStructure.PART1_SET) {
                    BlockPos asm = interfacePos.subtract(AssemblyStructure.rotate(rel.offset(0, 0, 7), facing));
                    if (level.getBlockState(asm).is(ModBlocks.ASSEMBLY_CONTROLLER.get())
                            && AssemblyStructure.validate(level, asm)) {
                        return asm;
                    }
                }

                // 超因果计算核心：接口相对控制器坐标 (0,0,-6)
                BlockPos sc = interfacePos.subtract(SupercausalStructure.rotate(new BlockPos(0, 0, -6), facing));
                if (level.getBlockState(sc).is(ModBlocks.COMPUTATION_CONTROLLER.get())
                        && SupercausalStructure.validate(level, sc).passed) {
                    return sc;
                }
            }
            return null;
        };
    }
}
