package com.github.aeddddd.ae2enhanced.integration;

import com.github.aeddddd.ae2enhanced.api.ChamberApi;
import com.github.aeddddd.ae2enhanced.api.ChamberJobView;
import com.github.aeddddd.ae2enhanced.api.ChamberSnapshot;
import com.github.aeddddd.ae2enhanced.api.CountedStackView;
import com.github.aeddddd.ae2enhanced.api.RecipeMatchView;
import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe;
import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex;
import com.github.aeddddd.ae2enhanced.chamber.LongItemStore;
import com.github.aeddddd.ae2enhanced.chamber.RecipeCatalog;
import com.github.aeddddd.ae2enhanced.chamber.packet.PacketChamberSync;
import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** 奇点处理仓只读 API 的服务端实现。 */
public final class ChamberApiImpl implements ChamberApi.Provider {
    private final Supplier<List<ChamberRecipe>> recipes;
    private final Supplier<Boolean> catalogReady;

    public ChamberApiImpl() {
        this(ChamberRecipeIndex::allRecipes, () -> RecipeCatalog.phase() == RecipeCatalog.Phase.FINALIZED);
    }

    public ChamberApiImpl(Supplier<List<ChamberRecipe>> recipes) {
        this(recipes, () -> true);
    }

    private ChamberApiImpl(Supplier<List<ChamberRecipe>> recipes, Supplier<Boolean> catalogReady) {
        this.recipes = recipes;
        this.catalogReady = catalogReady;
    }

    private static boolean accepts(ChamberRecipe recipe, String key) {
        for (ChamberRecipe.InputGroup group : recipe.getInputGroups()) {
            if (group.getKeys().contains(key)) {
                return true;
            }
        }
        return false;
    }

    private static RecipeMatchView view(ChamberRecipe recipe, long batches) {
        List<CountedStackView> inputs = new ArrayList<>();
        for (ChamberRecipe.InputGroup group : recipe.getInputGroups()) {
            for (ItemStack template : group.getTemplates()) {
                inputs.add(new CountedStackView(template, group.getCount()));
            }
        }
        return new RecipeMatchView(recipe.getId(), recipe.getOutput(), inputs, batches, recipe.getTimeTicks());
    }

    private static List<CountedStackView> storeView(LongItemStore store) {
        List<CountedStackView> result = new ArrayList<>();
        for (LongItemStore.Entry entry : store.getEntries()) {
            result.add(new CountedStackView(entry.getTemplate(), entry.getCount()));
        }
        return result;
    }

    @Override
    public Optional<ChamberSnapshot> snapshot(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null) {
            return Optional.empty();
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileSingularityChamber)) {
            return Optional.empty();
        }
        TileSingularityChamber chamber = (TileSingularityChamber) tile;
        List<PacketChamberSync.JobView> internalJobs = chamber.getJobViews();
        List<String> recipeIds = chamber.getJobRecipeIds();
        List<ChamberJobView> jobs = new ArrayList<>();
        for (int i = 0; i < internalJobs.size() && i < recipeIds.size(); i++) {
            PacketChamberSync.JobView job = internalJobs.get(i);
            jobs.add(new ChamberJobView(recipeIds.get(i), job.output, job.batches, job.progress, job.required));
        }
        return Optional.of(new ChamberSnapshot(world.provider.getDimension(), pos,
                chamber.getEnergy(), chamber.getMaxEnergy(), chamber.getParallelChannels(), chamber.getUsedChannels(),
                chamber.isPaused(), storeView(chamber.getInputStore()), storeView(chamber.getOutputStore()), jobs));
    }

    @Override
    public List<RecipeMatchView> matchStacks(List<CountedStackView> inputs) {
        if (!catalogReady.get()) {
            return Collections.emptyList();
        }
        Map<String, Long> available = new LinkedHashMap<>();
        if (inputs != null) {
            for (CountedStackView input : inputs) {
                if (input == null || input.getStack().isEmpty() || input.getCount() <= 0L) {
                    continue;
                }
                String key = LongItemStore.keyOf(input.getStack());
                long old = available.getOrDefault(key, 0L);
                long sum = old + input.getCount();
                available.put(key, sum < 0L ? Long.MAX_VALUE : sum);
            }
        }
        List<RecipeMatchView> result = new ArrayList<>();
        for (ChamberRecipe recipe : recipes.get()) {
            long batches = recipe.maxBatches(available);
            if (batches > 0L) {
                result.add(view(recipe, batches));
            }
        }
        return result;
    }

    @Override
    public List<RecipeMatchView> recipesForInput(ItemStack input) {
        if (!catalogReady.get() || input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        String key = LongItemStore.keyOf(input);
        List<RecipeMatchView> result = new ArrayList<>();
        for (ChamberRecipe recipe : recipes.get()) {
            if (accepts(recipe, key)) {
                result.add(view(recipe, 0L));
            }
        }
        return result;
    }

    @Override
    public List<RecipeMatchView> allRecipes() {
        if (!catalogReady.get()) {
            return Collections.emptyList();
        }
        List<RecipeMatchView> result = new ArrayList<>();
        for (ChamberRecipe recipe : recipes.get()) {
            result.add(view(recipe, 0L));
        }
        return result;
    }
}
