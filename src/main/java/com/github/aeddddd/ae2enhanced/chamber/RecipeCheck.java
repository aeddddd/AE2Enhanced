package com.github.aeddddd.ae2enhanced.chamber;

import com.github.aeddddd.ae2enhanced.observation.CheckObservation;
import com.github.aeddddd.ae2enhanced.observation.ObservationCheck;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 合成配方注册表健康检查：黑洞配方 / 奇点仪式配方 / 奇点处理仓索引.
 */
public final class RecipeCheck implements ObservationCheck {

    private static final String KEY_PREFIX = "chat.ae2enhanced.check.recipes.";

    @Override
    public String name() {
        return "recipes";
    }

    @Override
    public String displayName() {
        return KEY_PREFIX + "name";
    }

    @Override
    public void run(MinecraftServer server, List<CheckObservation> out) {
        try {
            int blackHole = com.github.aeddddd.ae2enhanced.chamber.RecipeCatalog.blackHoleRecipes().size();
            out.add(blackHole > 0
                    ? CheckObservation.ok(KEY_PREFIX + "blackhole_ok", blackHole)
                    : CheckObservation.warn(KEY_PREFIX + "blackhole_empty"));

            int singularity = com.github.aeddddd.ae2enhanced.chamber.RecipeCatalog.singularityRecipes().size();
            out.add(singularity > 0
                    ? CheckObservation.ok(KEY_PREFIX + "ritual_ok", singularity)
                    : CheckObservation.warn(KEY_PREFIX + "ritual_empty"));

            List<ChamberRecipe> chamber = ChamberRecipeIndex.allRecipes();
            Map<String, Integer> byType = new TreeMap<>();
            for (ChamberRecipe r : chamber) {
                String prefix = r.getId().contains(":")
                        ? r.getId().substring(0, r.getId().indexOf(':')) : r.getId();
                byType.merge(prefix, 1, Integer::sum);
            }
            out.add(chamber.isEmpty()
                    ? CheckObservation.error(KEY_PREFIX + "chamber_empty")
                    : CheckObservation.ok(KEY_PREFIX + "chamber_ok", chamber.size(), byType));
        } catch (Exception e) {
            out.add(CheckObservation.error(KEY_PREFIX + "exception", String.valueOf(e)));
        }
    }
}
