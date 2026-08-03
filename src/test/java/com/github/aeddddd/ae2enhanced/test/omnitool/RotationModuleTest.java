package com.github.aeddddd.ae2enhanced.test.omnitool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.module.RotationModule;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RotationModule} 旋转逻辑测试(以熔炉/石头为样本方块).
 */
class RotationModuleTest {

    private static final BlockPos POS = new BlockPos(1, 64, 1);

    private final RotationModule module = new RotationModule();

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testGetMode() {
        assertThat(module.getMode()).isEqualTo(AdvancedMEOmniToolItem.MODE_ROTATE);
    }

    private UseOnContext mockContext(Level level, Direction face) {
        UseOnContext context = mock(UseOnContext.class);
        when(context.getLevel()).thenReturn(level);
        when(context.getClickedPos()).thenReturn(POS);
        when(context.getClickedFace()).thenReturn(face);
        when(context.getPlayer()).thenReturn(null);
        return context;
    }

    @Test
    void testClientSideReturnsSuccessWithoutTouchingWorld() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);

        assertThat(module.onItemUse(mockContext(level, Direction.NORTH)))
                .isEqualTo(InteractionResult.SUCCESS);
        verify(level, never()).getBlockState(any(BlockPos.class));
    }

    @Test
    void testRotatesFurnaceFacingOnSideClick() {
        // 熔炉 FACING 默认 NORTH,点击侧面后应旋转为 EAST
        BlockState furnace = Blocks.FURNACE.defaultBlockState();
        assertThat(furnace.getValue(FurnaceBlock.FACING)).isEqualTo(Direction.NORTH);

        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        when(level.getBlockState(POS)).thenReturn(furnace);

        assertThat(module.onItemUse(mockContext(level, Direction.NORTH)))
                .isEqualTo(InteractionResult.SUCCESS);
        verify(level).setBlock(eq(POS),
                argThat(state -> state.getBlock() == Blocks.FURNACE
                        && state.getValue(FurnaceBlock.FACING) == Direction.EAST),
                eq(Block.UPDATE_ALL));
    }

    @Test
    void testNonRotatableBlockReturnsPass() {
        // 石头没有任何朝向属性,旋转无效果
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        when(level.getBlockState(POS)).thenReturn(Blocks.STONE.defaultBlockState());

        assertThat(module.onItemUse(mockContext(level, Direction.NORTH)))
                .isEqualTo(InteractionResult.PASS);
        verify(level, never()).setBlock(any(BlockPos.class), any(BlockState.class), anyInt());
    }
}
