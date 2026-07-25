package com.github.aeddddd.ae2enhanced.test.specialcrafting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;

import com.github.aeddddd.ae2enhanced.specialcrafting.SpecialRecipeDetector;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.ProcessingPatternBuilder;
import com.github.aeddddd.ae2enhanced.test.crafting.simulation.helpers.SimulationEnv;
import com.github.aeddddd.ae2enhanced.test.util.BootstrapMinecraft;

/**
 * D 组:特殊配方预扫描(detector)命中判定测试.
 */
@BootstrapMinecraft
public class SpecialRecipeDetectorTest {

    /** D1:候选样板含净产出自引用 → 命中. */
    @Test
    public void testSelfRefPatternHits() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        env.addPattern(new ProcessingPatternBuilder(mult(stone, 2)).addPreciseInput(1, stone).build());

        assertThat(SpecialRecipeDetector.mayInvolveSpecialRecipes(env.craftingService(), stone.what())).isTrue();
    }

    /** D2:仅普通样板 → 未命中. */
    @Test
    public void testNormalPatternMisses() {
        var env = new SimulationEnv();
        var cobble = item(Items.COBBLESTONE);
        var stone = item(Items.STONE);
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, cobble).build());

        assertThat(SpecialRecipeDetector.mayInvolveSpecialRecipes(env.craftingService(), stone.what())).isFalse();
    }

    /** D3:自引用样板存在但与请求 key 无关 → 未命中. */
    @Test
    public void testUnrelatedSelfRefMisses() {
        var env = new SimulationEnv();
        var cobble = item(Items.COBBLESTONE);
        var stone = item(Items.STONE);
        var dirt = item(Items.DIRT);
        // cobble 有自引用样板,但请求的是 stone
        env.addPattern(new ProcessingPatternBuilder(mult(cobble, 2)).addPreciseInput(1, cobble).build());
        env.addPattern(new ProcessingPatternBuilder(stone).addPreciseInput(1, dirt).build());

        assertThat(SpecialRecipeDetector.mayInvolveSpecialRecipes(env.craftingService(), stone.what())).isFalse();
    }

    /** D4:无任何候选样板 → 未命中. */
    @Test
    public void testNoPatternMisses() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);

        assertThat(SpecialRecipeDetector.mayInvolveSpecialRecipes(env.craftingService(), stone.what())).isFalse();
    }

    /** D5:流体 key 自引用 → 命中(key 类型无关). */
    @Test
    public void testFluidSelfRefHits() {
        var env = new SimulationEnv();
        var water = fluid(Fluids.WATER, 1000);
        env.addPattern(new ProcessingPatternBuilder(mult(water, 2)).addPreciseInput(1, water).build());

        assertThat(SpecialRecipeDetector.mayInvolveSpecialRecipes(env.craftingService(), water.what())).isTrue();
    }

    /** 催化剂型(进出等量)→ 命中(原生 limitQty 逐份展开会在超大订单挂起,必须路由). */
    @Test
    public void testCatalystPatternHits() {
        var env = new SimulationEnv();
        var stone = item(Items.STONE);
        var stick = item(Items.STICK);
        // A -> A + B:stone 进出等量
        env.addPattern(new ProcessingPatternBuilder(stone, stick).addPreciseInput(1, stone).build());

        assertThat(SpecialRecipeDetector.mayInvolveSpecialRecipes(env.craftingService(), stone.what())).isTrue();
    }

    private static GenericStack item(Item item) {
        return GenericStack.fromItemStack(new ItemStack(item));
    }

    private static GenericStack fluid(Fluid fluid, int amount) {
        return new GenericStack(AEFluidKey.of(fluid), amount * AEFluidKey.AMOUNT_BUCKET / 1000);
    }

    private static GenericStack mult(GenericStack template, long multiplier) {
        return new GenericStack(template.what(), template.amount() * multiplier);
    }
}
