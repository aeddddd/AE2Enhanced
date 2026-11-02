package com.github.aeddddd.ae2enhanced.chamber;

import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class RecipeCatalogTest {

    private static final String DEFAULT_ID = "catalog_test:default";
    private static final String SCRIPTED_ID = "catalog_test:scripted";
    private static final String LATE_ID = "catalog_test:late";
    private static final String CHAMBER_ID = "catalog_test:chamber";

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static BlackHoleRecipe recipe(String id) {
        return new BlackHoleRecipe(id, Collections.singletonMap("catalog_test:input", 1),
                new ItemStack(Items.APPLE));
    }

    @AfterEach
    void cleanup() {
        BlackHoleRecipeRegistry.removeById(DEFAULT_ID);
        BlackHoleRecipeRegistry.removeById(SCRIPTED_ID);
        BlackHoleRecipeRegistry.removeById(LATE_ID);
        RecipeCatalog.removeScriptedChamber(CHAMBER_ID);
    }

    @Test
    void defaultsScriptedChangesAndFinalizedViewsShareOneCatalog() {
        RecipeCatalog.beginDefaults();
        RecipeCatalog.addDefault(recipe(DEFAULT_ID));
        RecipeCatalog.completeDefaults();
        assertThat(RecipeCatalog.phase()).isEqualTo(RecipeCatalog.Phase.SCRIPTED_CHANGES);

        RecipeCatalog.addScripted(recipe(SCRIPTED_ID));
        RecipeCatalog.removeScriptedBlackHole(DEFAULT_ID);
        RecipeCatalog.addScripted(ChamberRecipe.builder(CHAMBER_ID)
                .input(new ItemStack(Items.APPLE), 1)
                .output(new ItemStack(Items.APPLE))
                .time(1)
                .build());
        RecipeCatalog.finalizeRecipes();

        assertThat(RecipeCatalog.phase()).isEqualTo(RecipeCatalog.Phase.FINALIZED);
        assertThat(RecipeCatalog.blackHoleRecipes())
                .extracting(BlackHoleRecipe::getId)
                .doesNotContain(DEFAULT_ID)
                .contains(SCRIPTED_ID);
        assertThat(RecipeCatalog.customChamberRecipes())
                .extracting(ChamberRecipe::getId)
                .contains(CHAMBER_ID);

        RecipeCatalog.addScripted(recipe(LATE_ID));
        assertThat(RecipeCatalog.phase()).isEqualTo(RecipeCatalog.Phase.SCRIPTED_CHANGES);
        assertThat(RecipeCatalog.blackHoleRecipes())
                .extracting(BlackHoleRecipe::getId)
                .contains(LATE_ID);
        assertThat(RecipeCatalog.phase()).isEqualTo(RecipeCatalog.Phase.FINALIZED);
    }
}
