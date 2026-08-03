package com.github.aeddddd.ae2enhanced.util.placement;

import net.minecraft.core.Direction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PlacementRestriction} 单元测试.
 */
class PlacementRestrictionTest {

    @Test
    void fromOrdinalValidValues() {
        assertThat(PlacementRestriction.fromOrdinal(0)).isEqualTo(PlacementRestriction.NO_LOCK);
        assertThat(PlacementRestriction.fromOrdinal(1)).isEqualTo(PlacementRestriction.HORIZONTAL);
        assertThat(PlacementRestriction.fromOrdinal(2)).isEqualTo(PlacementRestriction.VERTICAL);
        assertThat(PlacementRestriction.fromOrdinal(3)).isEqualTo(PlacementRestriction.NORTH_SOUTH);
        assertThat(PlacementRestriction.fromOrdinal(4)).isEqualTo(PlacementRestriction.EAST_WEST);
    }

    @Test
    void fromOrdinalOutOfRangeFallsBackToNoLock() {
        assertThat(PlacementRestriction.fromOrdinal(-1)).isEqualTo(PlacementRestriction.NO_LOCK);
        assertThat(PlacementRestriction.fromOrdinal(5)).isEqualTo(PlacementRestriction.NO_LOCK);
        assertThat(PlacementRestriction.fromOrdinal(Integer.MIN_VALUE)).isEqualTo(PlacementRestriction.NO_LOCK);
    }

    @Test
    void nextCyclesThroughAllValues() {
        assertThat(PlacementRestriction.NO_LOCK.next()).isEqualTo(PlacementRestriction.HORIZONTAL);
        assertThat(PlacementRestriction.HORIZONTAL.next()).isEqualTo(PlacementRestriction.VERTICAL);
        assertThat(PlacementRestriction.VERTICAL.next()).isEqualTo(PlacementRestriction.NORTH_SOUTH);
        assertThat(PlacementRestriction.NORTH_SOUTH.next()).isEqualTo(PlacementRestriction.EAST_WEST);
        // 末尾回绕到开头
        assertThat(PlacementRestriction.EAST_WEST.next()).isEqualTo(PlacementRestriction.NO_LOCK);
    }

    @Test
    void nextFullCycleReturnsToStart() {
        PlacementRestriction current = PlacementRestriction.NO_LOCK;
        for (int i = 0; i < PlacementRestriction.values().length; i++) {
            current = current.next();
        }
        assertThat(current).isEqualTo(PlacementRestriction.NO_LOCK);
    }

    @Test
    void noLockAllowsEverything() {
        for (Direction dir : Direction.values()) {
            assertThat(PlacementRestriction.NO_LOCK.allows(dir)).isTrue();
        }
    }

    @Test
    void horizontalAllowsOnlyHorizontalDirections() {
        assertThat(PlacementRestriction.HORIZONTAL.allows(Direction.NORTH)).isTrue();
        assertThat(PlacementRestriction.HORIZONTAL.allows(Direction.SOUTH)).isTrue();
        assertThat(PlacementRestriction.HORIZONTAL.allows(Direction.EAST)).isTrue();
        assertThat(PlacementRestriction.HORIZONTAL.allows(Direction.WEST)).isTrue();
        assertThat(PlacementRestriction.HORIZONTAL.allows(Direction.UP)).isFalse();
        assertThat(PlacementRestriction.HORIZONTAL.allows(Direction.DOWN)).isFalse();
    }

    @Test
    void verticalAllowsOnlyVerticalDirections() {
        assertThat(PlacementRestriction.VERTICAL.allows(Direction.UP)).isTrue();
        assertThat(PlacementRestriction.VERTICAL.allows(Direction.DOWN)).isTrue();
        assertThat(PlacementRestriction.VERTICAL.allows(Direction.NORTH)).isFalse();
        assertThat(PlacementRestriction.VERTICAL.allows(Direction.EAST)).isFalse();
    }

    @Test
    void northSouthAllowsOnlyZAxis() {
        assertThat(PlacementRestriction.NORTH_SOUTH.allows(Direction.NORTH)).isTrue();
        assertThat(PlacementRestriction.NORTH_SOUTH.allows(Direction.SOUTH)).isTrue();
        assertThat(PlacementRestriction.NORTH_SOUTH.allows(Direction.EAST)).isFalse();
        assertThat(PlacementRestriction.NORTH_SOUTH.allows(Direction.WEST)).isFalse();
        assertThat(PlacementRestriction.NORTH_SOUTH.allows(Direction.UP)).isFalse();
    }

    @Test
    void eastWestAllowsOnlyXAxis() {
        assertThat(PlacementRestriction.EAST_WEST.allows(Direction.EAST)).isTrue();
        assertThat(PlacementRestriction.EAST_WEST.allows(Direction.WEST)).isTrue();
        assertThat(PlacementRestriction.EAST_WEST.allows(Direction.NORTH)).isFalse();
        assertThat(PlacementRestriction.EAST_WEST.allows(Direction.SOUTH)).isFalse();
        assertThat(PlacementRestriction.EAST_WEST.allows(Direction.UP)).isFalse();
    }

    @Test
    void nameKeyFollowsLangConvention() {
        assertThat(PlacementRestriction.NO_LOCK.getNameKey())
                .isEqualTo("gui.ae2enhanced.placement_restriction.no_lock");
        assertThat(PlacementRestriction.NORTH_SOUTH.getNameKey())
                .isEqualTo("gui.ae2enhanced.placement_restriction.north_south");
    }
}
