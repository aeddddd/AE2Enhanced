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
    private TileHyperdimensionalController cachedController;
    private IGrid cachedGrid;
    private long lastSearchTick;
    private static final long SEARCH_COOLDOWN = 20;

    @Nullable
    public FluidStorageAdapter find(AENetworkProxy proxy, long currentTick) {
        try {
            IGrid grid = proxy.getGrid();
            if (grid == null) {
                invalidate();
                return null;
            }
            if (cachedAdapter != null && cachedGrid == grid && cachedController != null
                    && !cachedController.isInvalid() && cachedController.isFormed()
                    && cachedController.getFluidAdapter() == cachedAdapter) {
                return cachedAdapter;
            }

            cachedAdapter = null;
            cachedController = null;
            cachedGrid = grid;
            if (currentTick - lastSearchTick < SEARCH_COOLDOWN) {
                return null;
            }
            lastSearchTick = currentTick;

            for (IGridNode node : grid.getNodes()) {
                if (node == null) continue;
                Object host = node.getMachine();
                if (host instanceof TileHyperdimensionalController) {
                    TileHyperdimensionalController controller = (TileHyperdimensionalController) host;
                    if (controller.isInvalid() || !controller.isFormed()) continue;
                    FluidStorageAdapter adapter = controller.getFluidAdapter();
                    if (adapter != null) {
                        cachedAdapter = adapter;
                        cachedController = controller;
                        return adapter;
                    }
                }
            }
        } catch (GridAccessException e) {
            invalidate();
        } catch (Exception e) {
            invalidate();
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to find hyperdimensional fluid storage adapter", e);
        }

        return null;
    }

    public void invalidate() {
        cachedAdapter = null;
        cachedController = null;
        cachedGrid = null;
        lastSearchTick = 0;
    }
}
