package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import net.minecraft.inventory.InventoryCrafting;

public class TileAssemblyMeInterface extends TileDelegatedProxyBase<TileAssemblyController> implements ICraftingProvider {



    @Override
    protected Class<TileAssemblyController> getControllerClass() {
        return TileAssemblyController.class;
    }

    @Override
    protected boolean isControllerFormed(TileAssemblyController controller) {
        return controller.isFormed();
    }

    // ICraftingMedium
    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        TileAssemblyController controller = getController();
        if (controller != null) {
            return controller.pushPattern(patternDetails, table);
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        TileAssemblyController controller = getController();
        if (controller != null) {
            return controller.isBusy();
        }
        return false;
    }

    // ICraftingProvider
    @Override
    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        TileAssemblyController controller = getController();
        if (controller != null && controller.isMeInterfaceActive(pos)) {
            controller.provideCrafting(craftingTracker);
        }
    }


}
