package com.github.aeddddd.ae2enhanced.gridcrafting;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;

import java.util.*;

/** 每个 CraftingGridCache 独享的索引、快照与计算核心状态。 */
public final class CraftingGridExecutionContext {

    private final Set<TileComputationCore> computationCores = new HashSet<>();
    private final IdentityHashMap<ICraftingPatternDetails, List<ICraftingMedium>> mediumsMemo =
            new IdentityHashMap<>();
    private int assemblyHubCount;
    private volatile Object patternIndex;
    private volatile Map<IAEItemStack, List<ICraftingPatternDetails>> craftableSnapshot;
    private volatile Set<IAEItemStack> emitterSnapshot;

    public Set<TileComputationCore> computationCores() {
        return computationCores;
    }

    public IdentityHashMap<ICraftingPatternDetails, List<ICraftingMedium>> mediumsMemo() {
        return mediumsMemo;
    }

    public int assemblyHubCount() {
        return assemblyHubCount;
    }

    public void assemblyHubAdded() {
        assemblyHubCount++;
    }

    public void assemblyHubRemoved() {
        if (assemblyHubCount > 0) {
            assemblyHubCount--;
        }
    }

    public boolean hasAssemblyHub() {
        return assemblyHubCount > 0;
    }

    public Object patternIndex() {
        return patternIndex;
    }

    public void patternIndex(Object value) {
        patternIndex = value;
    }

    public Map<IAEItemStack, List<ICraftingPatternDetails>> craftableSnapshot() {
        return craftableSnapshot;
    }

    public void craftableSnapshot(Map<IAEItemStack, List<ICraftingPatternDetails>> value) {
        craftableSnapshot = value;
    }

    public Set<IAEItemStack> emitterSnapshot() {
        return emitterSnapshot;
    }

    public void emitterSnapshot(Set<IAEItemStack> value) {
        emitterSnapshot = value;
    }
}
