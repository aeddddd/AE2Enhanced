package com.github.aeddddd.ae2enhanced.integration;

import com.github.aeddddd.ae2enhanced.api.ChamberJobView;
import com.github.aeddddd.ae2enhanced.api.ChamberSnapshot;
import com.github.aeddddd.ae2enhanced.api.CountedStackView;
import com.github.aeddddd.ae2enhanced.api.RecipeMatchView;
import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChamberApiImplTest {
    private static final BlockPos POS = new BlockPos(3, 70, -5);

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static ChamberApiImpl api(ChamberRecipe recipe) {
        return new ChamberApiImpl(() -> Collections.singletonList(recipe));
    }

    private static ChamberRecipe recipe() {
        return ChamberRecipe.builder("test:chamber")
                .inputAlternatives(Arrays.asList(new ItemStack(Blocks.COBBLESTONE), new ItemStack(Blocks.STONE)), 2)
                .input(new ItemStack(Items.REDSTONE), 1)
                .output(new ItemStack(Items.DIAMOND))
                .time(80)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static void addJob(TileSingularityChamber chamber, ChamberRecipe recipe,
            long batches, long progress, long required) throws Exception {
        Class<?> jobClass = Class.forName(TileSingularityChamber.class.getName() + "$Job");
        Constructor<?> constructor = jobClass.getDeclaredConstructor(ChamberRecipe.class, long.class, long.class, long.class);
        constructor.setAccessible(true);
        Object job = constructor.newInstance(recipe, batches, required, batches);
        Field progressField = jobClass.getDeclaredField("progress");
        progressField.setAccessible(true);
        progressField.setLong(job, progress);
        Field jobsField = TileSingularityChamber.class.getDeclaredField("jobs");
        jobsField.setAccessible(true);
        ((List<Object>) jobsField.get(chamber)).add(job);
    }

    @Test
    void matchesAlternativeInputGroupsAndReportsMaximumBatches() {
        ChamberApiImpl api = api(recipe());

        List<RecipeMatchView> matches = api.matchStacks(Arrays.asList(
                new CountedStackView(new ItemStack(Blocks.COBBLESTONE), 3),
                new CountedStackView(new ItemStack(Blocks.STONE), 1),
                new CountedStackView(new ItemStack(Items.REDSTONE), 2)));

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).getRecipeId()).isEqualTo("test:chamber");
        assertThat(matches.get(0).getMaxBatches()).isEqualTo(2);
        assertThat(matches.get(0).getTimeTicks()).isEqualTo(80);
        assertThat(matches.get(0).getInputs()).hasSize(3);
        assertThat(api.recipesForInput(new ItemStack(Blocks.STONE))).hasSize(1);
        assertThat(api.allRecipes()).hasSize(1);
        assertThat(api.matchStacks(Collections.singletonList(
                new CountedStackView(new ItemStack(Blocks.COBBLESTONE), 1)))).isEmpty();
    }

    @Test
    void snapshotMapsRecipeIdsProgressAndStoresWithoutExposingMutableState() throws Exception {
        TileSingularityChamber chamber = new TileSingularityChamber();
        chamber.getInputStore().insert(new ItemStack(Blocks.COBBLESTONE), 5);
        chamber.getOutputStore().insert(new ItemStack(Items.DIAMOND), 2);
        addJob(chamber, recipe(), 3, 25, 100);
        World world = mock(World.class);
        WorldProvider provider = mock(WorldProvider.class);
        Field providerField = World.class.getDeclaredField("provider");
        providerField.setAccessible(true);
        providerField.set(world, provider);
        when(world.provider.getDimension()).thenReturn(7);
        when(world.getTileEntity(POS)).thenReturn(chamber);

        ChamberSnapshot snapshot = new ChamberApiImpl().snapshot(world, POS).orElseThrow(AssertionError::new);

        assertThat(snapshot.getDimension()).isEqualTo(7);
        assertThat(snapshot.getPos()).isEqualTo(POS);
        assertThat(snapshot.getActiveJobCount()).isEqualTo(1);
        assertThat(snapshot.getUsedChannels()).isEqualTo(3);
        assertThat(snapshot.getInputs().get(0).getCount()).isEqualTo(5);
        assertThat(snapshot.getBufferedOutputs().get(0).getCount()).isEqualTo(2);
        ChamberJobView job = snapshot.getJobs().get(0);
        assertThat(job.getRecipeId()).isEqualTo("test:chamber");
        assertThat(job.getBatches()).isEqualTo(3);
        assertThat(job.getProgressTicks()).isEqualTo(25);
        assertThat(job.getRequiredTicks()).isEqualTo(100);
        assertThat(job.getRemainingTicks()).isEqualTo(75);
        job.getOutput().setCount(64);
        assertThat(job.getOutput().getCount()).isEqualTo(1);
        assertThatThrownBy(() -> snapshot.getJobs().add(job))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
