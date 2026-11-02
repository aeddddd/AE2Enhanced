package com.github.aeddddd.ae2enhanced.craftingapi;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** CraftingGridCache 的中性只读能力。 */
public interface CraftingGridAccess {

    Set<IAEItemStack> ae2enhanced$craftableKeys();
    Map<IAEItemStack, List<ICraftingPatternDetails>> ae2enhanced$craftableSnapshot();
    boolean ae2enhanced$canEmit(IAEItemStack canonKey);
    Object ae2enhanced$patternIndex();
    boolean ae2enhanced$hasAssemblyHub();
    List<ICraftingMedium> ae2enhanced$getMediumsMemo(ICraftingPatternDetails details);
}
