package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import com.github.aeddddd.ae2enhanced.storage.FluidStorageAdapter;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;

import javax.annotation.Nullable;

/**
 * 缓存并查找当前网络中超维度中枢的 FluidStorageAdapter.
 */
public class HyperStorageLinkFluid {

    private FluidStorageAdapter cachedAdapter;
    private long lastSearchTick;
    private static final long SEARCH_COOLDOWN = 20;

    @Nullable
    public FluidStorageAdapter find(AENetworkProxy proxy, long currentTick) {
        if (cachedAdapter != null) {
            return cachedAdapter;
        }
        if (currentTick - lastSearchTick < SEARCH_COOLDOWN) {
            return null;
        }
        lastSearchTick = currentTick;

        try {
            IGrid grid = proxy.getGrid();
            if (grid == null) return null;

            for (IGridNode node : grid.getNodes()) {
                if (node == null) continue;
                Object host = node.getMachine();
                if (host instanceof TileHyperdimensionalController) {
                    TileHyperdimensionalController controller = (TileHyperdimensionalController) host;
                    cachedAdapter = controller.getFluidAdapter();
                    if (cachedAdapter != null) return cachedAdapter;
                }
            }

        } catch (GridAccessException e) {
            // network not ready
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to find hyperdimensional fluid storage adapter", e);
        }

        return cachedAdapter;
    }

    public void invalidate() {
        cachedAdapter = null;
        lastSearchTick = 0;
    }
}
