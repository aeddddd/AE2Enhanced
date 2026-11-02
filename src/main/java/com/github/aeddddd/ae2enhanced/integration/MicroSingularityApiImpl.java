package com.github.aeddddd.ae2enhanced.integration;

import com.github.aeddddd.ae2enhanced.crafting.BlackHoleCraftingHelper;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;

import com.github.aeddddd.ae2enhanced.api.CountedStackView;
import com.github.aeddddd.ae2enhanced.api.MicroSingularityApi;
import com.github.aeddddd.ae2enhanced.api.MicroSingularitySnapshot;
import com.github.aeddddd.ae2enhanced.api.RecipeMatchView;
import com.github.aeddddd.ae2enhanced.chamber.RecipeCatalog;
import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** 微型奇点只读 API 的服务端实现。 */
public final class MicroSingularityApiImpl implements MicroSingularityApi.Provider {
    private static final int AUTO_INTERVAL = 10;
    private static final int BLACK_HOLE_TIME_TICKS = 20;
    private final Supplier<List<BlackHoleRecipe>> recipes;
    private final Supplier<Boolean> catalogReady;

    public MicroSingularityApiImpl() {
        this(com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry::getRecipes,
                () -> RecipeCatalog.phase() == RecipeCatalog.Phase.FINALIZED);
    }

    public MicroSingularityApiImpl(Supplier<List<BlackHoleRecipe>> recipes) {
        this(recipes, () -> true);
    }

    private MicroSingularityApiImpl(Supplier<List<BlackHoleRecipe>> recipes, Supplier<Boolean> catalogReady) {
        this.recipes = recipes;
        this.catalogReady = catalogReady;
    }

    private static com.github.aeddddd.ae2enhanced.api.RitualRecipeView ritualView(
            com.github.aeddddd.ae2enhanced.crafting.SingularityRecipe recipe) {
        List<CountedStackView> inputs = new ArrayList<>();
        for (ItemStack stack : recipe.getInputs()) {
            inputs.add(new CountedStackView(stack, stack.getCount()));
        }
        ResourceLocation target = recipe.getTargetBlock() == null ? null : recipe.getTargetBlock().getRegistryName();
        ItemStack output = new ItemStack(com.github.aeddddd.ae2enhanced.block.BlockRegistry.MICRO_SINGULARITY);
        return new com.github.aeddddd.ae2enhanced.api.RitualRecipeView(recipe.getId(), inputs,
                recipe.getHeldItem(), target, output, recipe.getLifetimeTicks());
    }

    private static com.github.aeddddd.ae2enhanced.api.SingularityFuelView fuelView(
            com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRecipe recipe) {
        return new com.github.aeddddd.ae2enhanced.api.SingularityFuelView(recipe.getId(),
                recipe.getFuelItem(), recipe.getTicks(), recipe.isPermanent());
    }

    private static List<CountedStackView> aggregateEntities(World world, BlockPos pos) {
        AxisAlignedBB area = new AxisAlignedBB(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, ItemStack> templates = new LinkedHashMap<>();
        for (EntityItem entity : world.getEntitiesWithinAABB(EntityItem.class, area) ) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            String key = BlackHoleRecipe.keyOf(stack);
            counts.put(key, counts.getOrDefault(key, 0L) + stack.getCount());
            templates.putIfAbsent(key, stack);
        }
        List<CountedStackView> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            result.add(new CountedStackView(templates.get(entry.getKey()), entry.getValue()));
        }
        return result;
    }

    private static RecipeMatchView view(BlackHoleRecipe recipe, long batches) {
        List<CountedStackView> inputs = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : new java.util.TreeMap<>(recipe.getInputs()).entrySet()) {
            inputs.add(new CountedStackView(keyTemplate(entry.getKey()), entry.getValue()));
        }
        return new RecipeMatchView(recipe.getId(), recipe.getOutput(), inputs, batches, BLACK_HOLE_TIME_TICKS);
    }

    private static ItemStack keyTemplate(String key) {
        int nbtStart = key.indexOf('{');
        String base = nbtStart >= 0 ? key.substring(0, nbtStart) : key;
        int lastColon = base.lastIndexOf(':');
        if (lastColon < 0) {
            return ItemStack.EMPTY;
        }
        try {
            ResourceLocation name = new ResourceLocation(base.substring(0, lastColon));
            int meta = Integer.parseInt(base.substring(lastColon + 1));
            Item item = Item.REGISTRY.getObject(name);
            if (item == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item, 1, meta);
            if (nbtStart >= 0) {
                try {
                    NBTTagCompound tag = JsonToNBT.getTagFromJson(key.substring(nbtStart));
                    stack.setTagCompound(tag);
                } catch (Exception ignored) {
                }
            }
            return stack;
        } catch (Exception exception) {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public Optional<MicroSingularitySnapshot> snapshot(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null) {
            return Optional.empty();
        }
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileMicroSingularity)) {
            return Optional.empty();
        }
        TileMicroSingularity singularity = (TileMicroSingularity) tile;
        List<CountedStackView> nearby = aggregateEntities(world, pos);
        List<RecipeMatchView> matches = matchStacks(nearby);
        int next = (int) ((AUTO_INTERVAL - world.getTotalWorldTime() % AUTO_INTERVAL) % AUTO_INTERVAL);
        return Optional.of(new MicroSingularitySnapshot(world.provider.getDimension(), pos,
                singularity.isPermanent(), singularity.getLifetimeTicks(), next, 1, 2, nearby, matches));
    }

    @Override
    public List<RecipeMatchView> matchNearby(World world, BlockPos pos) {
        if (world == null || world.isRemote || pos == null) {
            return java.util.Collections.emptyList();
        }
        return matchStacks(aggregateEntities(world, pos));
    }

    @Override
    public List<RecipeMatchView> matchStacks(List<CountedStackView> inputs) {
        if (!catalogReady.get()) {
            return java.util.Collections.emptyList();
        }
        Map<String, Integer> found = new LinkedHashMap<>();
        if (inputs != null) {
            for (CountedStackView input : inputs) {
                if (input == null || input.getCount() <= 0L) {
                    continue;
                }
                String key = BlackHoleRecipe.keyOf(input.getStack());
                if (key.isEmpty()) {
                    continue;
                }
                long sum = found.getOrDefault(key, 0) + input.getCount();
                found.put(key, sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum);
            }
        }
        List<RecipeMatchView> result = new ArrayList<>();
        for (BlackHoleRecipe recipe : recipes.get()) {
            int batches = recipe.maxBatches(found);
            if (batches > 0) {
                result.add(view(recipe, batches));
            }
        }
        return result;
    }

    @Override
    public List<RecipeMatchView> blackHoleRecipes() {
        if (!catalogReady.get()) {
            return java.util.Collections.emptyList();
        }
        List<RecipeMatchView> result = new ArrayList<>();
        for (BlackHoleRecipe recipe : recipes.get()) {
            result.add(view(recipe, 0));
        }
        return result;
    }

    @Override
    public List<com.github.aeddddd.ae2enhanced.api.RitualRecipeView> ritualRecipes() {
        if (!catalogReady.get()) {
            return java.util.Collections.emptyList();
        }
        List<com.github.aeddddd.ae2enhanced.api.RitualRecipeView> result = new ArrayList<>();
        for (com.github.aeddddd.ae2enhanced.crafting.SingularityRecipe recipe
                : com.github.aeddddd.ae2enhanced.crafting.SingularityRecipeRegistry.getRecipes()) {
            result.add(ritualView(recipe));
        }
        return result;
    }

    @Override
    public Optional<com.github.aeddddd.ae2enhanced.api.RitualRecipeView> matchRitual(
            World world, BlockPos center, ItemStack heldItem) {
        if (!catalogReady.get() || world == null || world.isRemote || center == null) {
            return Optional.empty();
        }
        com.github.aeddddd.ae2enhanced.crafting.SingularityRecipe recipe =
                com.github.aeddddd.ae2enhanced.crafting.SingularityRecipeRegistry.findMatching(world, center, heldItem);
        return recipe == null ? Optional.empty() : Optional.of(ritualView(recipe));
    }

    @Override
    public List<com.github.aeddddd.ae2enhanced.api.SingularityFuelView> fuelRecipes() {
        if (!catalogReady.get()) {
            return java.util.Collections.emptyList();
        }
        List<com.github.aeddddd.ae2enhanced.api.SingularityFuelView> result = new ArrayList<>();
        for (com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRecipe recipe
                : com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRegistry.getRecipes()) {
            result.add(fuelView(recipe));
        }
        return result;
    }

    @Override
    public Optional<com.github.aeddddd.ae2enhanced.api.SingularityFuelView> matchFuel(ItemStack stack) {
        if (!catalogReady.get() || stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRecipe recipe =
                com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRegistry.findFor(stack);
        return recipe == null ? Optional.empty() : Optional.of(fuelView(recipe));
    }

    @Override
    public Optional<com.github.aeddddd.ae2enhanced.api.ConstrainedSingularitySnapshot> constrainedSnapshot(ItemStack stack) {
        if (stack == null || stack.isEmpty()
                || !(stack.getItem() instanceof com.github.aeddddd.ae2enhanced.ring.ItemConstrainedMicroSingularity)) {
            return Optional.empty();
        }
        return Optional.of(new com.github.aeddddd.ae2enhanced.api.ConstrainedSingularitySnapshot(
                com.github.aeddddd.ae2enhanced.ring.ItemConstrainedMicroSingularity.getLifeTicks(stack),
                com.github.aeddddd.ae2enhanced.ring.ItemConstrainedMicroSingularity.isPermanent(stack), 30));
    }
}
