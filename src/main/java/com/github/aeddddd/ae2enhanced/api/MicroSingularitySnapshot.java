package com.github.aeddddd.ae2enhanced.api;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 服务端微型奇点某一时刻的只读状态。剩余时间为游戏 tick，未加载时冻结。 */
public final class MicroSingularitySnapshot {
    private final int dimension;
    private final BlockPos pos;
    private final boolean permanent;
    private final int remainingTicks;
    private final int nextAutoCraftTicks;
    private final int craftingRadius;
    private final int suctionRadius;
    private final List<CountedStackView> nearbyInputs;
    private final List<RecipeMatchView> executableRecipes;

    public MicroSingularitySnapshot(int dimension, BlockPos pos, boolean permanent, int remainingTicks,
            int nextAutoCraftTicks, int craftingRadius, int suctionRadius,
            List<CountedStackView> nearbyInputs, List<RecipeMatchView> executableRecipes) {
        this.dimension = dimension;
        this.pos = pos;
        this.permanent = permanent;
        this.remainingTicks = Math.max(0, remainingTicks);
        this.nextAutoCraftTicks = nextAutoCraftTicks;
        this.craftingRadius = craftingRadius;
        this.suctionRadius = suctionRadius;
        this.nearbyInputs = Collections.unmodifiableList(new ArrayList<>(nearbyInputs));
        this.executableRecipes = Collections.unmodifiableList(new ArrayList<>(executableRecipes));
    }

    public int getDimension() {
        return dimension;
    }

    public BlockPos getPos() {
        return pos;
    }

    public boolean isPermanent() {
        return permanent;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public double getRemainingSeconds() {
        return remainingTicks / 20.0D;
    }

    public int getNextAutoCraftTicks() {
        return nextAutoCraftTicks;
    }

    public int getCraftingRadius() {
        return craftingRadius;
    }

    public int getSuctionRadius() {
        return suctionRadius;
    }

    public List<CountedStackView> getNearbyInputs() {
        return nearbyInputs;
    }

    public List<RecipeMatchView> getExecutableRecipes() {
        return executableRecipes;
    }
}
