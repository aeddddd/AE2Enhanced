package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 缓存并查找当前网络中超维度中枢的 ItemStorageAdapter.
 */
public class HyperStorageLink {

    private ItemStorageAdapter cachedAdapter;
    private long lastSearchTick;
    private static final long SEARCH_COOLDOWN = 20;

    @Nullable
    public ItemStorageAdapter find(AENetworkProxy proxy, long currentTick) {
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

            // 1. 尝试从网格节点直接查找 TileHyperdimensionalController
            for (IGridNode node : grid.getNodes()) {
                if (node == null) continue;
                Object host = node.getMachine();
                if (host instanceof TileHyperdimensionalController) {
                    TileHyperdimensionalController controller = (TileHyperdimensionalController) host;
                    cachedAdapter = controller.getItemAdapter();
                    if (cachedAdapter != null) return cachedAdapter;
                }
            }

        } catch (GridAccessException e) {
            // network not ready
        } catch (Exception e) {
            com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.warn("[AE2E] Failed to find hyperdimensional storage adapter", e);
        }

        return cachedAdapter;
    }

    public void invalidate() {
        cachedAdapter = null;
        lastSearchTick = 0;
    }
}
