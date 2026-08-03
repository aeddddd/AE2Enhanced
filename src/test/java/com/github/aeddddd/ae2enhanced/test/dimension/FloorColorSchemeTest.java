package com.github.aeddddd.ae2enhanced.test.dimension;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorRole;
import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorScheme;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FloorColorScheme} 单元测试.
 */
class FloorColorSchemeTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    private static BlockState concrete(DyeColor color) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .get(new net.minecraft.resources.ResourceLocation("minecraft", color.getName() + "_concrete"))
                .defaultBlockState();
    }

    @Test
    void defaultSchemeShouldLeaveStatesUnchanged() {
        FloorColorScheme scheme = FloorColorScheme.createDefault();

        BlockState gray = Blocks.GRAY_CONCRETE.defaultBlockState();
        assertThat(scheme.apply(gray)).isSameAs(gray);
        assertThat(scheme.apply(Blocks.STONE.defaultBlockState()))
                .isSameAs(Blocks.STONE.defaultBlockState());
    }

    @Test
    void applyShouldPassThroughNull() {
        FloorColorScheme scheme = FloorColorScheme.ofConcrete(DyeColor.RED, DyeColor.BLUE, DyeColor.LIME);

        assertThat(scheme.apply(null)).isNull();
    }

    @Test
    void ofConcreteShouldOverrideAllThreeRoles() {
        FloorColorScheme scheme = FloorColorScheme.ofConcrete(DyeColor.RED, DyeColor.BLUE, DyeColor.LIME);

        assertThat(scheme.apply(Blocks.GRAY_CONCRETE.defaultBlockState()))
                .isEqualTo(concrete(DyeColor.RED));
        assertThat(scheme.apply(Blocks.WHITE_CONCRETE.defaultBlockState()))
                .isEqualTo(concrete(DyeColor.BLUE));
        assertThat(scheme.apply(Blocks.BLACK_CONCRETE.defaultBlockState()))
                .isEqualTo(concrete(DyeColor.LIME));
        // 非占位方块不受影响
        assertThat(scheme.apply(Blocks.STONE.defaultBlockState()))
                .isSameAs(Blocks.STONE.defaultBlockState());
    }

    @Test
    void setConcreteWithDefaultColorShouldClearOverride() {
        FloorColorScheme scheme = FloorColorScheme.createDefault()
                .setConcrete(FloorColorRole.ROAD_BASE, DyeColor.RED);
        assertThat(scheme.getConcreteColor(FloorColorRole.ROAD_BASE)).isEqualTo(DyeColor.RED);

        scheme.setConcrete(FloorColorRole.ROAD_BASE, DyeColor.GRAY);

        assertThat(scheme.getConcreteColor(FloorColorRole.ROAD_BASE)).isEqualTo(DyeColor.GRAY);
        // 覆盖被清除后 apply 原样返回
        BlockState gray = Blocks.GRAY_CONCRETE.defaultBlockState();
        assertThat(scheme.apply(gray)).isSameAs(gray);
    }

    @Test
    void setConcreteShouldBeChainable() {
        FloorColorScheme scheme = FloorColorScheme.createDefault()
                .setConcrete(FloorColorRole.ROAD_BASE, DyeColor.ORANGE)
                .setConcrete(FloorColorRole.PLATFORM_BASE, DyeColor.PURPLE);

        assertThat(scheme.getConcreteColor(FloorColorRole.ROAD_BASE)).isEqualTo(DyeColor.ORANGE);
        assertThat(scheme.getConcreteColor(FloorColorRole.PLATFORM_BASE)).isEqualTo(DyeColor.PURPLE);
        // 未设置的角色保持默认
        assertThat(scheme.getConcreteColor(FloorColorRole.ROAD_LINE)).isEqualTo(DyeColor.WHITE);
    }

    @Test
    void putShouldAllowArbitraryBlockMapping() {
        FloorColorScheme scheme = FloorColorScheme.createDefault()
                .put(Blocks.STONE, Blocks.DIAMOND_BLOCK.defaultBlockState());

        assertThat(scheme.apply(Blocks.STONE.defaultBlockState()))
                .isEqualTo(Blocks.DIAMOND_BLOCK.defaultBlockState());
        assertThat(scheme.apply(Blocks.DIRT.defaultBlockState()))
                .isSameAs(Blocks.DIRT.defaultBlockState());
    }

    @Test
    void getConcreteColorShouldFallBackToDefaultWhenOverrideIsNotConcrete() {
        FloorColorScheme scheme = FloorColorScheme.createDefault()
                .put(Blocks.GRAY_CONCRETE, Blocks.STONE.defaultBlockState());

        // 覆盖目标不是混凝土,按默认色处理
        assertThat(scheme.getConcreteColor(FloorColorRole.ROAD_BASE)).isEqualTo(DyeColor.GRAY);
    }

    @Test
    void copyShouldBeIndependent() {
        FloorColorScheme scheme = FloorColorScheme.ofConcrete(DyeColor.RED, DyeColor.WHITE, DyeColor.BLACK);

        FloorColorScheme copy = scheme.copy();
        copy.setConcrete(FloorColorRole.ROAD_BASE, DyeColor.BLUE);

        assertThat(copy).isNotSameAs(scheme);
        assertThat(scheme.getConcreteColor(FloorColorRole.ROAD_BASE)).isEqualTo(DyeColor.RED);
        assertThat(copy.getConcreteColor(FloorColorRole.ROAD_BASE)).isEqualTo(DyeColor.BLUE);
    }

    @Test
    void nbtRoundTripShouldPreserveOverrides() {
        FloorColorScheme scheme = FloorColorScheme.ofConcrete(DyeColor.RED, DyeColor.YELLOW, DyeColor.BLUE)
                .put(Blocks.STONE, Blocks.GOLD_BLOCK.defaultBlockState());

        FloorColorScheme restored = FloorColorScheme.createDefault();
        restored.readFromNBT(scheme.writeToNBT());

        assertThat(restored.getConcreteColor(FloorColorRole.ROAD_BASE)).isEqualTo(DyeColor.RED);
        assertThat(restored.getConcreteColor(FloorColorRole.ROAD_LINE)).isEqualTo(DyeColor.YELLOW);
        assertThat(restored.getConcreteColor(FloorColorRole.PLATFORM_BASE)).isEqualTo(DyeColor.BLUE);
        assertThat(restored.apply(Blocks.STONE.defaultBlockState()))
                .isEqualTo(Blocks.GOLD_BLOCK.defaultBlockState());
    }

    @Test
    void readFromNbtShouldSkipUnknownOrMalformedBlocks() {
        // 三个角色都设为非默认色,确保三条覆盖均写入 NBT
        FloorColorScheme scheme = FloorColorScheme.ofConcrete(DyeColor.RED, DyeColor.BLUE, DyeColor.LIME);
        CompoundTag tag = scheme.writeToNBT();
        ListTag list = tag.getList("overrides", 10);
        // 不存在的方块名应被跳过
        CompoundTag bad = new CompoundTag();
        bad.putString("from", "minecraft:no_such_block");
        bad.putString("to", "minecraft:stone");
        list.add(bad);
        // 非法 ResourceLocation 应被跳过
        CompoundTag malformed = new CompoundTag();
        malformed.putString("from", "???");
        malformed.putString("to", "minecraft:stone");
        list.add(malformed);

        FloorColorScheme restored = FloorColorScheme.createDefault();
        restored.readFromNBT(tag);

        // 原有 3 条覆盖保留,坏数据被丢弃
        assertThat(restored.writeToNBT().getList("overrides", 10)).hasSize(3);
    }

    @Test
    void readFromNbtShouldClearPreviousOverrides() {
        FloorColorScheme scheme = FloorColorScheme.ofConcrete(DyeColor.RED, DyeColor.WHITE, DyeColor.BLACK);

        scheme.readFromNBT(new CompoundTag());

        assertThat(scheme.writeToNBT().getList("overrides", 10)).isEmpty();
        assertThat(scheme.getConcreteColor(FloorColorRole.ROAD_BASE)).isEqualTo(DyeColor.GRAY);
    }

    @Test
    void allDyeColorsShouldRoundTripThroughConcrete() {
        // concreteOf/dyeColorOf 对全部 16 色应互逆
        for (DyeColor color : DyeColor.values()) {
            FloorColorScheme scheme = FloorColorScheme.createDefault()
                    .setConcrete(FloorColorRole.ROAD_BASE, color);
            assertThat(scheme.getConcreteColor(FloorColorRole.ROAD_BASE)).isEqualTo(color);
        }
    }
}
