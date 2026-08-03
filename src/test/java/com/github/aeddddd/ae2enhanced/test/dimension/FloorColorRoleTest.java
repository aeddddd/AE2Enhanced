package com.github.aeddddd.ae2enhanced.test.dimension;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorRole;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FloorColorRole} 单元测试.
 */
class FloorColorRoleTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void shouldDeclareThreeRoles() {
        assertThat(FloorColorRole.values())
                .containsExactly(
                        FloorColorRole.ROAD_BASE,
                        FloorColorRole.ROAD_LINE,
                        FloorColorRole.PLATFORM_BASE);
    }

    @Test
    void roadBaseShouldUseGrayConcrete() {
        assertThat(FloorColorRole.ROAD_BASE.getPlaceholder()).isSameAs(Blocks.GRAY_CONCRETE);
        assertThat(FloorColorRole.ROAD_BASE.getDefaultColor()).isEqualTo(DyeColor.GRAY);
    }

    @Test
    void roadLineShouldUseWhiteConcrete() {
        assertThat(FloorColorRole.ROAD_LINE.getPlaceholder()).isSameAs(Blocks.WHITE_CONCRETE);
        assertThat(FloorColorRole.ROAD_LINE.getDefaultColor()).isEqualTo(DyeColor.WHITE);
    }

    @Test
    void platformBaseShouldUseBlackConcrete() {
        assertThat(FloorColorRole.PLATFORM_BASE.getPlaceholder()).isSameAs(Blocks.BLACK_CONCRETE);
        assertThat(FloorColorRole.PLATFORM_BASE.getDefaultColor()).isEqualTo(DyeColor.BLACK);
    }

    @Test
    void placeholdersShouldBeDistinct() {
        assertThat(FloorColorRole.values())
                .extracting(FloorColorRole::getPlaceholder)
                .doesNotHaveDuplicates();
    }
}
