package com.github.aeddddd.ae2enhanced.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * {@link MicroSingularityBlockEntity} 单元测试.
 * <p>覆盖寿命状态机（默认值、设置、追加、溢出转永久）、NBT 往返、渲染包围盒,
 * 以及静态 tick 的客户端早退、倒计时递减、坍缩移除方块与永久奇点豁免.
 * 事件视界击杀与黑洞合成吸入依赖真实实体/配方环境,不在离线覆盖范围内.</p>
 */
class MicroSingularityBlockEntityTest {

    private static final BlockPos POS = new BlockPos(3, 70, -5);

    @BeforeAll
    static void bootstrap() {
        BlockEntityTestSupport.bootstrap();
    }

    private static MicroSingularityBlockEntity newEntity() {
        return new MicroSingularityBlockEntity(POS, Blocks.STONE.defaultBlockState());
    }

    /**
     * 构造跳过事件视界伤害与自动合成的服务端 mock level:
     * 伤害源查询返回哑实例,游戏时间避开 10 tick 合成节流点.
     */
    private static ServerLevel mockServerLevel() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.isClientSide()).thenReturn(false);
        DamageSources damageSources = mock(DamageSources.class);
        when(damageSources.source(any(ResourceKey.class))).thenReturn(mock(DamageSource.class));
        when(level.damageSources()).thenReturn(damageSources);
        when(level.getGameTime()).thenReturn(3L);
        return level;
    }

    /** 新实体默认 6000 tick 寿命,非永久. */
    @Test
    void testInitialState() {
        MicroSingularityBlockEntity be = newEntity();
        assertEquals(MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS, be.getLifetimeTicks());
        assertFalse(be.isPermanent());
    }

    /** setLifetimeTicks：正数生效,0/负数回退默认. */
    @Test
    void testSetLifetimeTicks() {
        MicroSingularityBlockEntity be = newEntity();
        be.setLifetimeTicks(100);
        assertEquals(100, be.getLifetimeTicks());

        be.setLifetimeTicks(0);
        assertEquals(MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS, be.getLifetimeTicks());

        be.setLifetimeTicks(-50);
        assertEquals(MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS, be.getLifetimeTicks());
    }

    /** addLifetimeTicks：正常累加；负数按 0 处理不扣减. */
    @Test
    void testAddLifetimeTicksAccumulates() {
        MicroSingularityBlockEntity be = newEntity();
        be.setLifetimeTicks(100);
        be.addLifetimeTicks(50);
        assertEquals(150, be.getLifetimeTicks());

        be.addLifetimeTicks(-999);
        assertEquals(150, be.getLifetimeTicks());
        assertFalse(be.isPermanent());
    }

    /** 追加后剩余时间溢出 int 上限 → 转为永久存在,寿命不再变化. */
    @Test
    void testAddLifetimeTicksOverflowBecomesPermanent() {
        MicroSingularityBlockEntity be = newEntity();
        be.setLifetimeTicks(Integer.MAX_VALUE);
        be.addLifetimeTicks(100);
        assertTrue(be.isPermanent());
        // 寿命停留在溢出前的值,不再倒计时语义
        assertEquals(Integer.MAX_VALUE, be.getLifetimeTicks());

        // 永久后追加为无操作
        be.addLifetimeTicks(1000);
        assertEquals(Integer.MAX_VALUE, be.getLifetimeTicks());
    }

    /** 恰好到达 Integer.MAX_VALUE 不触发永久（严格大于才转换）. */
    @Test
    void testAddLifetimeTicksExactMaxDoesNotOverflow() {
        MicroSingularityBlockEntity be = newEntity();
        be.setLifetimeTicks(Integer.MAX_VALUE - 10);
        be.addLifetimeTicks(10);
        assertFalse(be.isPermanent());
        assertEquals(Integer.MAX_VALUE, be.getLifetimeTicks());
    }

    /** NBT 往返：寿命与永久标志持久化；缺失寿命键时回退默认. */
    @Test
    void testNbtRoundTrip() {
        MicroSingularityBlockEntity source = newEntity();
        source.setLifetimeTicks(1234);
        source.setPermanent(true);

        CompoundTag tag = new CompoundTag();
        source.saveAdditional(tag);

        MicroSingularityBlockEntity target = newEntity();
        target.load(tag);
        assertEquals(1234, target.getLifetimeTicks());
        assertTrue(target.isPermanent());

        // 旧存档无 LifeTicks 键 → 默认 6000
        CompoundTag legacy = new CompoundTag();
        legacy.putBoolean("Permanent", false);
        MicroSingularityBlockEntity legacyTarget = newEntity();
        legacyTarget.setLifetimeTicks(42);
        legacyTarget.load(legacy);
        assertEquals(MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS, legacyTarget.getLifetimeTicks());
    }

    /** 渲染包围盒向外扩 2 格（吸积盘超出方块范围）. */
    @Test
    void testRenderBoundingBox() {
        MicroSingularityBlockEntity be = newEntity();
        AABB box = be.getRenderBoundingBox();
        assertEquals(POS.getX() - 2.0, box.minX, 1e-6);
        assertEquals(POS.getX() + 1 + 2.0, box.maxX, 1e-6);
        assertEquals(POS.getY() - 2.0, box.minY, 1e-6);
        assertEquals(POS.getZ() + 1 + 2.0, box.maxZ, 1e-6);
    }

    /** 客户端 tick 直接早退,不触碰服务端逻辑. */
    @Test
    void testTickSkipsOnClientSide() {
        MicroSingularityBlockEntity be = newEntity();
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);

        MicroSingularityBlockEntity.tick(level, POS, be.getBlockState(), be);

        assertEquals(MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS, be.getLifetimeTicks());
        verify(level, never()).getGameTime();
    }

    /** 服务端 tick：寿命逐 tick 递减,未到 0 不坍缩. */
    @Test
    void testTickDecrementsLifetime() {
        MicroSingularityBlockEntity be = newEntity();
        be.setLifetimeTicks(100);
        ServerLevel level = mockServerLevel();

        MicroSingularityBlockEntity.tick(level, POS, be.getBlockState(), be);
        assertEquals(99, be.getLifetimeTicks());
        verify(level, never()).removeBlock(any(BlockPos.class), anyBoolean());
    }

    /** 寿命倒数到 0 → 坍缩并移除方块. */
    @Test
    void testTickCollapsesAtZero() {
        MicroSingularityBlockEntity be = newEntity();
        be.setLifetimeTicks(1);
        ServerLevel level = mockServerLevel();
        // collapse 使用的是方块实体自身的 level 字段,而非 tick 参数
        be.setLevel(level);

        MicroSingularityBlockEntity.tick(level, POS, be.getBlockState(), be);
        assertEquals(0, be.getLifetimeTicks());
        verify(level).removeBlock(eq(POS), eq(false));
    }

    /** 永久奇点不倒计时、不坍缩. */
    @Test
    void testTickPermanentNeverCollapses() {
        MicroSingularityBlockEntity be = newEntity();
        be.setLifetimeTicks(5);
        be.setPermanent(true);
        ServerLevel level = mockServerLevel();

        for (int i = 0; i < 20; i++) {
            MicroSingularityBlockEntity.tick(level, POS, be.getBlockState(), be);
        }
        assertEquals(5, be.getLifetimeTicks());
        verify(level, never()).removeBlock(any(BlockPos.class), anyBoolean());
    }
}
