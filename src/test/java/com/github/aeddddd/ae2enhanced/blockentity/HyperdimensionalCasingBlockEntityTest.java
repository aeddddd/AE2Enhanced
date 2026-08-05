package com.github.aeddddd.ae2enhanced.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import appeng.api.util.AECableType;

import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;

/**
 * {@link HyperdimensionalCasingBlockEntity} 单元测试.
 * <p>外壳仅作为网格节点,覆盖构造与线缆连接类型.</p>
 */
class HyperdimensionalCasingBlockEntityTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrap() {
        BlockEntityTestSupport.bootstrap();
    }

    /** 两个构造器均可离线构造,主节点随之创建. */
    @Test
    void testConstruct() {
        HyperdimensionalCasingBlockEntity be = new HyperdimensionalCasingBlockEntity(POS,
                Blocks.STONE.defaultBlockState());
        assertNotNull(be.getMainNode());

        HyperdimensionalCasingBlockEntity typed = new HyperdimensionalCasingBlockEntity(
                ModBlockEntities.HYPERDIMENSIONAL_CASING.get(), POS, Blocks.STONE.defaultBlockState());
        assertNotNull(typed.getMainNode());
    }

    /** 线缆连接类型恒为 SMART（任意外壳方块均可并网）. */
    @Test
    void testCableConnectionType() {
        HyperdimensionalCasingBlockEntity be = new HyperdimensionalCasingBlockEntity(POS,
                Blocks.STONE.defaultBlockState());
        for (Direction dir : Direction.values()) {
            assertEquals(AECableType.SMART, be.getCableConnectionType(dir));
        }
    }
}
