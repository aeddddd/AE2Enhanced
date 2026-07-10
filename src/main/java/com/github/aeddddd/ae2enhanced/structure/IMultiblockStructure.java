package com.github.aeddddd.ae2enhanced.structure;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 多方块结构的统一接口。
 * <p>所有多方块结构（装配枢纽、超维度仓储、超因果计算核心）均实现此接口，
 * 使结构验证、装配、拆解、缺失统计、一键放置等逻辑拥有统一扩展点。</p>
 */
public interface IMultiblockStructure {

    /**
     * 验证结构完整性。
     * <p>若任一期望方块所在区块未加载，或任一位置方块类型不匹配，均返回 {@code false}。</p>
     *
     * @return 是否完整
     */
    boolean validate(Level level, BlockPos controllerPos);

    /**
     * 验证结构并返回详细结果，包括缺失方块统计与区块加载状态。
     */
    ValidationResult validateDetailed(Level level, BlockPos controllerPos);

    /**
     * 装配结构：更新控制器与接口状态。
     */
    void assemble(Level level, BlockPos controllerPos);

    /**
     * 拆解结构：清理控制器与接口状态。
     */
    void disassemble(Level level, BlockPos controllerPos);

    /**
     * 统计缺失方块。
     *
     * @return 缺失方块类型到数量的映射
     */
    Map<Block, Integer> getMissingMap(Level level, BlockPos controllerPos);

    /**
     * 创造模式：直接放置所有缺失方块并装配。
     */
    void placeMissingBlocks(Level level, BlockPos controllerPos, @Nullable Player player);

    /**
     * 生存模式：检查玩家背包并消耗材料，放置缺失方块后装配。
     *
     * @return 是否成功
     */
    boolean tryConsumeAndPlace(Level level, BlockPos controllerPos, Player player);

    /**
     * 获取结构相对坐标应旋转的方向。
     * <p>注意：不同结构的坐标约定可能不同。装配枢纽与超维度仓储以控制器朝向为基准，
     * 超因果计算核心以控制器背向为基准（结构向背后延伸）。</p>
     *
     * @return 旋转方向
     */
    Direction getRotation(Level level, BlockPos controllerPos);

    /**
     * 获取结构包含的所有相对坐标（用于区块加载检查）。
     */
    Set<BlockPos> getAllPositions();

    /**
     * 获取结构所需全部材料清单。
     *
     * @return 方块类型到所需数量的映射
     */
    Map<Block, Integer> getRequiredMaterials();

    /**
     * 获取按当前控制器朝向旋转后的期望方块相对坐标集合。
     *
     * @param level 当前维度
     * @param controllerPos 控制器位置
     * @return (旋转后的相对坐标, 方块类型) 集合
     */
    Set<Map.Entry<BlockPos, Block>> getExpectedBlocks(Level level, BlockPos controllerPos);

    /**
     * 获取通用 ME 接口相对坐标；若该结构不使用通用 ME 接口则返回 {@code null}。
     */
    @Nullable
    BlockPos getInterfaceRelativePos();
}
