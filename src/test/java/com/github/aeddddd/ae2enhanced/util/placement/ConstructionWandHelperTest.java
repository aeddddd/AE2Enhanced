package com.github.aeddddd.ae2enhanced.util.placement;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link ConstructionWandHelper} 单元测试.
 * <p>Level 用 Mockito mock,方块状态用 map 模拟,方块本身使用真实原版 BlockState.</p>
 */
class ConstructionWandHelperTest {

    static {
        // 静态字段引用 Blocks,类初始化即触发注册表访问,必须在静态块最前引导
        MinecraftTestBootstrap.bootstrap();
    }

    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /**
     * 构造一个以 map 为方块存储的假世界,缺省为空气.
     */
    private static Level fakeLevel(Map<BlockPos, BlockState> blocks) {
        Level level = mock(Level.class);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(true);
        when(level.getBlockState(any(BlockPos.class)))
                .thenAnswer(inv -> blocks.getOrDefault(inv.getArgument(0), AIR));
        return level;
    }

    private static Map<BlockPos, BlockState> stoneRow(int fromX, int toX) {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (int x = fromX; x <= toX; x++) {
            blocks.put(new BlockPos(x, 0, 0), STONE);
        }
        return blocks;
    }

    @Test
    void unloadedPositionReturnsEmpty() {
        Level level = mock(Level.class);
        when(level.isLoaded(any(BlockPos.class))).thenReturn(false);
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.UP, PlacementRestriction.NO_LOCK);
        assertThat(result).isEmpty();
    }

    @Test
    void clickedAirReturnsEmpty() {
        Level level = fakeLevel(Map.of());
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.UP, PlacementRestriction.NO_LOCK);
        assertThat(result).isEmpty();
    }

    @Test
    void singleStoneExtendsOneLayer() {
        Level level = fakeLevel(stoneRow(0, 0));
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.UP, PlacementRestriction.NO_LOCK);
        assertThat(result).containsExactly(new BlockPos(0, 1, 0));
    }

    @Test
    void contiguousRowExtendsWholeRegion() {
        // 3 个连续石头,点击顶面 → 整排向上延伸一层,顺序从点击点开始
        Level level = fakeLevel(stoneRow(0, 2));
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.UP, PlacementRestriction.NO_LOCK);
        assertThat(result).containsExactly(
                new BlockPos(0, 1, 0),
                new BlockPos(1, 1, 0),
                new BlockPos(2, 1, 0));
    }

    @Test
    void differentBlockTypeBreaksRegion() {
        // 中间夹泥土 → 区域被截断
        Map<BlockPos, BlockState> blocks = stoneRow(0, 2);
        blocks.put(new BlockPos(1, 0, 0), DIRT);
        Level level = fakeLevel(blocks);
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.UP, PlacementRestriction.NO_LOCK);
        assertThat(result).containsExactly(new BlockPos(0, 1, 0));
    }

    @Test
    void occupiedTargetIsSkipped() {
        // 点击点上方的目标位已被石头占据 → 跳过该目标
        Map<BlockPos, BlockState> blocks = stoneRow(0, 1);
        blocks.put(new BlockPos(0, 1, 0), STONE);
        Level level = fakeLevel(blocks);
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.UP, PlacementRestriction.NO_LOCK);
        // 注意：上方的石头不在点击面平面（Y=0）上,不参与区域扩展,仅占据目标位
        assertThat(result).containsExactly(new BlockPos(1, 1, 0));
    }

    @Test
    void verticalRestrictionBlocksHorizontalExpansion() {
        // 顶面（Y 轴法向）的平面方向全是水平方向,VERTICAL 锁全部禁止 → 只剩点击点本身
        Level level = fakeLevel(stoneRow(0, 2));
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.UP, PlacementRestriction.VERTICAL);
        assertThat(result).containsExactly(new BlockPos(0, 1, 0));
    }

    @Test
    void northSouthRestrictionFiltersXAxisExpansion() {
        // 沿 X 轴排列,NORTH_SOUTH 锁禁止东西扩展 → 只剩点击点本身
        Level level = fakeLevel(stoneRow(0, 2));
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.UP, PlacementRestriction.NORTH_SOUTH);
        assertThat(result).containsExactly(new BlockPos(0, 1, 0));
    }

    @Test
    void eastWestRestrictionAllowsXAxisExpansion() {
        // 沿 X 轴排列,EAST_WEST 锁允许扩展 → 整排延伸
        Level level = fakeLevel(stoneRow(0, 2));
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.UP, PlacementRestriction.EAST_WEST);
        assertThat(result).hasSize(3);
    }

    @Test
    void verticalWallWithVerticalRestriction() {
        // 点击北面（Z 轴法向）,石头是竖直排列的墙：VERTICAL 锁允许 UP/DOWN 扩展
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(new BlockPos(0, 0, 0), STONE);
        blocks.put(new BlockPos(0, 1, 0), STONE);
        blocks.put(new BlockPos(0, 2, 0), STONE);
        Level level = fakeLevel(blocks);
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.NORTH, PlacementRestriction.VERTICAL);
        assertThat(result).containsExactlyInAnyOrder(
                new BlockPos(0, 0, -1),
                new BlockPos(0, 1, -1),
                new BlockPos(0, 2, -1));
    }

    @Test
    void verticalWallWithHorizontalRestriction() {
        // 同一面墙,HORIZONTAL 锁禁止 UP/DOWN → 只剩点击点
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        blocks.put(new BlockPos(0, 0, 0), STONE);
        blocks.put(new BlockPos(0, 1, 0), STONE);
        Level level = fakeLevel(blocks);
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.NORTH, PlacementRestriction.HORIZONTAL);
        assertThat(result).containsExactly(new BlockPos(0, 0, -1));
    }

    @Test
    void resultCappedAtMaxBlocks() {
        // 超过 512 个源方块时结果截断到 MAX_BLOCKS
        Level level = fakeLevel(stoneRow(0, ConstructionWandHelper.MAX_BLOCKS + 10));
        var result = ConstructionWandHelper.calculatePositions(
                level, BlockPos.ZERO, Direction.UP, PlacementRestriction.EAST_WEST);
        assertThat(result).hasSize(ConstructionWandHelper.MAX_BLOCKS);
    }

    @Test
    void maxBlocksConstantMatchesConfig() {
        assertThat(ConstructionWandHelper.MAX_BLOCKS).isEqualTo(PlacementConfig.BULK_MAX_BLOCKS);
    }
}
