package com.github.aeddddd.ae2enhanced.util.placement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PlacementMode} 单元测试.
 */
class PlacementModeTest {

    @Test
    void fromOrdinalValidValues() {
        assertThat(PlacementMode.fromOrdinal(0)).isEqualTo(PlacementMode.SINGLE);
        assertThat(PlacementMode.fromOrdinal(1)).isEqualTo(PlacementMode.BULK);
        assertThat(PlacementMode.fromOrdinal(2)).isEqualTo(PlacementMode.CABLE);
    }

    @Test
    void fromOrdinalOutOfRangeFallsBackToSingle() {
        // 越界（包括负数）一律回退 SINGLE
        assertThat(PlacementMode.fromOrdinal(-1)).isEqualTo(PlacementMode.SINGLE);
        assertThat(PlacementMode.fromOrdinal(3)).isEqualTo(PlacementMode.SINGLE);
        assertThat(PlacementMode.fromOrdinal(Integer.MAX_VALUE)).isEqualTo(PlacementMode.SINGLE);
    }

    @Test
    void roundTripThroughOrdinal() {
        for (PlacementMode mode : PlacementMode.values()) {
            assertThat(PlacementMode.fromOrdinal(mode.ordinal())).isEqualTo(mode);
        }
    }
}
