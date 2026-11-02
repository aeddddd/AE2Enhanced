package com.github.aeddddd.ae2enhanced.integration;

import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;

import com.github.aeddddd.ae2enhanced.api.CountedStackView;
import com.github.aeddddd.ae2enhanced.api.RecipeMatchView;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MicroSingularityApiImplTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @Test
    void matchStacksReturnsRecipeAndMaximumBatchesWithoutMutation() {
        BlackHoleRecipe recipe = new BlackHoleRecipe("test:conversion",
                Collections.singletonMap(BlackHoleRecipe.keyOf(new ItemStack(Blocks.COBBLESTONE)), 2),
                new ItemStack(Items.DIAMOND));
        MicroSingularityApiImpl api = new MicroSingularityApiImpl(() -> Collections.singletonList(recipe));
        List<CountedStackView> inputs = Arrays.asList(
                new CountedStackView(new ItemStack(Blocks.COBBLESTONE), 3),
                new CountedStackView(new ItemStack(Blocks.COBBLESTONE), 2));

        List<RecipeMatchView> matches = api.matchStacks(inputs);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getRecipeId()).isEqualTo("test:conversion");
        assertThat(matches.get(0).getMaxBatches()).isEqualTo(2);
        assertThat(matches.get(0).getInputs().get(0).getCount()).isEqualTo(2);
        assertThat(matches.get(0).getOutput().getItem()).isEqualTo(Items.DIAMOND);
        assertThat(inputs.get(0).getCount()).isEqualTo(3);
        assertThat(inputs.get(1).getCount()).isEqualTo(2);
    }

    @Test
    void insufficientInputsDoNotMatch() {
        BlackHoleRecipe recipe = new BlackHoleRecipe("test:conversion",
                Collections.singletonMap(BlackHoleRecipe.keyOf(new ItemStack(Blocks.COBBLESTONE)), 2),
                new ItemStack(Items.DIAMOND));
        MicroSingularityApiImpl api = new MicroSingularityApiImpl(() -> Collections.singletonList(recipe));

        assertThat(api.matchStacks(Collections.singletonList(
                new CountedStackView(new ItemStack(Blocks.COBBLESTONE), 1)))).isEmpty();
    }

    @Test
    void returnedStacksAreDefensiveCopies() {
        BlackHoleRecipe recipe = new BlackHoleRecipe("test:conversion",
                Collections.singletonMap(BlackHoleRecipe.keyOf(new ItemStack(Blocks.COBBLESTONE)), 1),
                new ItemStack(Items.DIAMOND));
        MicroSingularityApiImpl api = new MicroSingularityApiImpl(() -> Collections.singletonList(recipe));
        RecipeMatchView match = api.matchStacks(Collections.singletonList(
                new CountedStackView(new ItemStack(Blocks.COBBLESTONE), 1))).get(0);

        ItemStack output = match.getOutput();
        output.setCount(64);
        assertThat(match.getOutput().getCount()).isEqualTo(1);
        assertThatThrownBy(() -> match.getInputs().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void fuelAndConstrainedItemViewsPreserveTheirDistinctLifetimeSemantics() {
        com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRecipe fuel =
                new com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRecipe(
                        "test:api_fuel", new ItemStack(Items.NETHER_STAR), 1200, false);
        com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRegistry.register(fuel);
        try {
            MicroSingularityApiImpl api = new MicroSingularityApiImpl(Collections::emptyList);
            assertThat(api.matchFuel(new ItemStack(Items.NETHER_STAR))).hasValueSatisfying(view -> {
                assertThat(view.getRecipeId()).isEqualTo("test:api_fuel");
                assertThat(view.getAddedTicks()).isEqualTo(1200);
                assertThat(view.isPermanent()).isFalse();
            });
            ItemStack constrained = new ItemStack(
                    new com.github.aeddddd.ae2enhanced.ring.ItemConstrainedMicroSingularity());
            net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
            tag.setInteger("LifeTicks", 345);
            constrained.setTagCompound(tag);
            assertThat(api.constrainedSnapshot(constrained)).hasValueSatisfying(view -> {
                assertThat(view.getRemainingTicks()).isEqualTo(345);
                assertThat(view.getCountdownDivisor()).isEqualTo(30);
                assertThat(view.isPermanent()).isFalse();
            });
        } finally {
            com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRegistry.removeById("test:api_fuel");
        }
    }
}
