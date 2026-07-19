package com.github.aeddddd.ae2enhanced.crafting.singularity;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import com.github.aeddddd.ae2enhanced.blackhole.blockentity.MicroSingularityBlockEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SingularityRecipe} 单元测试（构造与取值逻辑）。
 * <p>{@code matches}/{@code craft} 依赖 {@code Level}，属于集成测试范畴，此处不覆盖。</p>
 */
class SingularityRecipeTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testSimpleConstructorDefaults() {
        SingularityRecipe recipe = new SingularityRecipe("ae2enhanced:simple",
                List.of(new ItemStack(Items.STONE, 5)));

        assertEquals("ae2enhanced:simple", recipe.getId());
        assertEquals(1, recipe.getInputs().size());
        assertEquals(Items.STONE, recipe.getInputs().get(0).getItem());
        // 默认无手持要求、无目标方块、使用默认存在时间
        assertTrue(recipe.getHeldItem().isEmpty());
        assertNull(recipe.getTargetBlock());
        assertEquals(MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS, recipe.getLifetimeTicks());
    }

    @Test
    void testFullConstructor() {
        ItemStack held = new ItemStack(Items.DIAMOND);
        SingularityRecipe recipe = new SingularityRecipe("ae2enhanced:full",
                List.of(new ItemStack(Items.DIRT, 2)), held, Blocks.STONE, 1200);

        assertEquals("ae2enhanced:full", recipe.getId());
        assertEquals(1, recipe.getInputs().size());
        assertSame(held.getItem(), recipe.getHeldItem().getItem());
        assertSame(Blocks.STONE, recipe.getTargetBlock());
        assertEquals(1200, recipe.getLifetimeTicks());
    }

    @Test
    void testNullInputsBecomeEmpty() {
        SingularityRecipe recipe = new SingularityRecipe("ae2enhanced:null_inputs", null);
        assertTrue(recipe.getInputs().isEmpty());
    }

    @Test
    void testNullHeldItemBecomesEmpty() {
        SingularityRecipe recipe = new SingularityRecipe("ae2enhanced:null_held",
                List.of(), null, null, 100);
        assertTrue(recipe.getHeldItem().isEmpty());
        assertNull(recipe.getTargetBlock());
    }

    @Test
    void testNonPositiveLifetimeFallsBackToDefault() {
        SingularityRecipe zero = new SingularityRecipe("ae2enhanced:zero", List.of(), ItemStack.EMPTY, null, 0);
        SingularityRecipe negative = new SingularityRecipe("ae2enhanced:negative", List.of(), ItemStack.EMPTY, null,
                -10);

        assertEquals(MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS, zero.getLifetimeTicks());
        assertEquals(MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS, negative.getLifetimeTicks());
    }

    @Test
    void testDefaultLifeTicksValue() {
        // 与 Java 端约定的默认存在时间：6000 tick（5 分钟）
        assertEquals(6000, MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS);
    }
}
