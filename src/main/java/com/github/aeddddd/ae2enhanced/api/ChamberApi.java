package com.github.aeddddd.ae2enhanced.api;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** 奇点处理仓只读 API；世界查询只允许在服务端主线程调用。 */
public final class ChamberApi {
    private static volatile Provider provider;

    private ChamberApi() {
    }

    public static void registerProvider(Provider newProvider) {
        provider = newProvider;
    }

    public static Optional<ChamberSnapshot> snapshot(World world, BlockPos pos) {
        return provider == null ? Optional.empty() : provider.snapshot(world, pos);
    }

    /** 返回材料可执行的配方及最大批次数，不承诺它们下一次一定被调度。 */
    public static List<RecipeMatchView> matchStacks(List<CountedStackView> inputs) {
        return provider == null ? Collections.emptyList() : provider.matchStacks(inputs);
    }

    /** 返回以该物品作为任一输入的配方，目录未定稿时为空。 */
    public static List<RecipeMatchView> recipesForInput(ItemStack input) {
        return provider == null ? Collections.emptyList() : provider.recipesForInput(input);
    }

    /** 返回已定稿的完整只读目录；输入列表按配方输入组展开，同组物品为替代关系。 */
    public static List<RecipeMatchView> allRecipes() {
        return provider == null ? Collections.emptyList() : provider.allRecipes();
    }

    public interface Provider {
        Optional<ChamberSnapshot> snapshot(World world, BlockPos pos);
        List<RecipeMatchView> matchStacks(List<CountedStackView> inputs);
        List<RecipeMatchView> recipesForInput(ItemStack input);
        List<RecipeMatchView> allRecipes();
    }
}
