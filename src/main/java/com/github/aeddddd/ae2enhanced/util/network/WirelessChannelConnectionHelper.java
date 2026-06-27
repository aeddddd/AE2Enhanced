package com.github.aeddddd.ae2enhanced.util.network;

import appeng.api.AEApi;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.parts.automation.PartUpgradeable;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemChannelReceiverCard;
import com.github.aeddddd.ae2enhanced.tile.TileWirelessChannelTransmitter;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.items.IItemHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * 无线频道接收卡的远程网格连接管理器.
 * 此类不是 Mixin,可被任意 Mixin 安全引用,避免类加载器隔离问题.
 */
public class WirelessChannelConnectionHelper {

    private static final Map<IGridNode, IGridConnection> AE2E_REMOTE_CONNECTIONS = new HashMap<>();

    public static void destroyConnection(IAEAppEngInventory parent) {
        IGridNode node = getNodeFromParent(parent);
        if (node == null) return;
        IGridConnection old = AE2E_REMOTE_CONNECTIONS.remove(node);
        if (old != null) {
            try {
                old.destroy();
                AE2Enhanced.LOGGER.warn("[AE2E] Destroyed wireless grid connection for {}",
                        parent.getClass().getSimpleName());
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to destroy wireless grid connection", e);
            }
        }
    }

    /**
     * 验证所有缓存的无线连接,销毁已失效的连接并从缓存中移除.
     * 由定时 tick handler 调用.
     */
    public static void validateAllConnections() {
        if (AE2E_REMOTE_CONNECTIONS.isEmpty()) return;
        java.util.Iterator<Map.Entry<IGridNode, IGridConnection>> it = AE2E_REMOTE_CONNECTIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<IGridNode, IGridConnection> entry = it.next();
            IGridConnection conn = entry.getValue();
            if (!isConnectionValid(conn)) {
                try {
                    conn.destroy();
                } catch (Exception e) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Failed to destroy invalid wireless connection during validation", e);
                }
                it.remove();
                AE2Enhanced.LOGGER.warn("[AE2E] Removed invalid wireless connection from cache");
            }
        }
    }

    private static boolean isConnectionValid(IGridConnection conn) {
        if (conn == null) return false;
        try {
            IGridNode a = conn.a();
            IGridNode b = conn.b();
            if (a == null || b == null) return false;
            IGrid gridA = a.getGrid();
            IGrid gridB = b.getGrid();
            return gridA != null && gridB != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static void tryConnect(IAEAppEngInventory parent) {
        if (!Platform.isServer()) return;

        IGridNode node = getNodeFromParent(parent);
        if (node == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] tryConnect: node is null for {}",
                    parent.getClass().getSimpleName());
            return;
        }

        // If already connected, validate it; destroy stale connections and retry
        IGridConnection existing = AE2E_REMOTE_CONNECTIONS.get(node);
        if (existing != null) {
            if (isConnectionValid(existing)) {
                AE2Enhanced.LOGGER.debug("[AE2E] tryConnect: already connected and valid for {}",
                        parent.getClass().getSimpleName());
                return;
            }
            // Stale connection: destroy and remove so we can reconnect
            AE2Enhanced.LOGGER.warn("[AE2E] tryConnect: stale connection detected for {}, destroying and reconnecting",
                    parent.getClass().getSimpleName());
            try {
                existing.destroy();
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to destroy stale wireless connection", e);
            }
            AE2E_REMOTE_CONNECTIONS.remove(node);
        }

        IItemHandler upgrades = getUpgradesFromParent(parent);
        if (upgrades == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] tryConnect: upgrades inventory is null for {}",
                    parent.getClass().getSimpleName());
            return;
        }

        TileEntity tile = getTileFromParent(parent);
        if (tile == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] tryConnect: tile is null for {}",
                    parent.getClass().getSimpleName());
            return;
        }

        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof ItemChannelReceiverCard)) continue;
            if (!ItemChannelReceiverCard.isBound(stack)) continue;

            BlockPos pos = ItemChannelReceiverCard.getTransmitterPos(stack);
            int dim = ItemChannelReceiverCard.getTransmitterDim(stack);
            if (pos == null) continue;

            if (!AE2EnhancedConfig.wirelessChannel.crossDimension) {
                try {
                    int localDim = tile.getWorld().provider.getDimension();
                    if (dim != localDim) continue;
                } catch (Exception ignored) {
                    continue;
                }
            }

            // Range check
            if (AE2EnhancedConfig.wirelessChannel.maxRange > 0) {
                try {
                    BlockPos localPos = tile.getPos();
                    if (localPos.getDistance(pos.getX(), pos.getY(), pos.getZ()) > AE2EnhancedConfig.wirelessChannel.maxRange) {
                        continue;
                    }
                } catch (Exception ignored) {
                }
            }

            IGridNode transmitterNode = findTransmitterNode(pos, dim);
            if (transmitterNode == null) continue;

            // Prevent self-connection
            if (transmitterNode == node) continue;

            try {
                IGridConnection conn = AEApi.instance().grid().createGridConnection(node, transmitterNode);
                AE2E_REMOTE_CONNECTIONS.put(node, conn);
                AE2Enhanced.LOGGER.warn("[AE2E] Created wireless grid connection for {} -> transmitter at {}",
                        parent.getClass().getSimpleName(), pos);

                // AE2 的 createGridConnection 在 addConnection 之前调用 repath(),
                // 导致路径系统看不到这条新连接,从而无法分配频道.
                // 手动再触发一次 repath() 以修正路径计算.
                IGrid grid = node.getGrid();
                if (grid != null) {
                    IPathingGrid pathing = grid.getCache(IPathingGrid.class);
                    if (pathing != null) {
                        pathing.repath();
                        AE2Enhanced.LOGGER.warn("[AE2E] Triggered manual repath() for wireless connection");
                    }
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.warn("[AE2E] Failed to create wireless channel connection", e);
            }
            break;
        }
    }

    private static IGridNode getNodeFromParent(IAEAppEngInventory parent) {
        // 1. PartUpgradeable
        if (parent instanceof PartUpgradeable) {
            return ((PartUpgradeable) parent).getProxy().getNode();
        }
        // 2. IActionHost
        if (parent instanceof appeng.api.networking.security.IActionHost) {
            return ((appeng.api.networking.security.IActionHost) parent).getActionableNode();
        }
        // 3. IGridProxyable
        if (parent instanceof appeng.me.helpers.IGridProxyable) {
            appeng.me.helpers.AENetworkProxy proxy = ((appeng.me.helpers.IGridProxyable) parent).getProxy();
            return proxy != null ? proxy.getNode() : null;
        }
        // 4. getProxy() method
        try {
            java.lang.reflect.Method m = parent.getClass().getMethod("getProxy");
            appeng.me.helpers.AENetworkProxy proxy = (appeng.me.helpers.AENetworkProxy) m.invoke(parent);
            return proxy != null ? proxy.getNode() : null;
        } catch (Exception ignored) {
        }
        // 5. gridProxy field (DualityInterface etc.)
        try {
            java.lang.reflect.Field f = parent.getClass().getDeclaredField("gridProxy");
            f.setAccessible(true);
            appeng.me.helpers.AENetworkProxy proxy = (appeng.me.helpers.AENetworkProxy) f.get(parent);
            return proxy != null ? proxy.getNode() : null;
        } catch (Exception ignored) {
        }
        // 6. proxy field (TileAENetworkBase etc.)
        try {
            java.lang.reflect.Field f = parent.getClass().getDeclaredField("proxy");
            f.setAccessible(true);
            appeng.me.helpers.AENetworkProxy proxy = (appeng.me.helpers.AENetworkProxy) f.get(parent);
            return proxy != null ? proxy.getNode() : null;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static IItemHandler getUpgradesFromParent(IAEAppEngInventory parent) {
        // 1. ISegmentedInventory
        if (parent instanceof appeng.api.implementations.tiles.ISegmentedInventory) {
            return ((appeng.api.implementations.tiles.ISegmentedInventory) parent).getInventoryByName("upgrades");
        }
        // 2. getInventoryByName method
        try {
            java.lang.reflect.Method m = parent.getClass().getMethod("getInventoryByName", String.class);
            IItemHandler inv = (IItemHandler) m.invoke(parent, "upgrades");
            if (inv != null) return inv;
        } catch (Exception ignored) {
        }
        // 3. getUpgrades / getUpgradeInventory method
        try {
            java.lang.reflect.Method m = parent.getClass().getMethod("getUpgrades");
            Object result = m.invoke(parent);
            if (result instanceof IItemHandler) return (IItemHandler) result;
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method m = parent.getClass().getMethod("getUpgradeInventory");
            Object result = m.invoke(parent);
            if (result instanceof IItemHandler) return (IItemHandler) result;
        } catch (Exception ignored) {
        }
        return null;
    }

    private static TileEntity getTileFromParent(IAEAppEngInventory parent) {
        if (parent instanceof TileEntity) {
            return (TileEntity) parent;
        }
        if (parent instanceof PartUpgradeable) {
            return ((PartUpgradeable) parent).getTile();
        }
        if (parent instanceof appeng.helpers.DualityInterface) {
            return ((appeng.helpers.DualityInterface) parent).getTile();
        }
        // try getTile() / getTileEntity()
        try {
            java.lang.reflect.Method m = parent.getClass().getMethod("getTile");
            Object result = m.invoke(parent);
            if (result instanceof TileEntity) return (TileEntity) result;
        } catch (Exception ignored) {
        }
        try {
            java.lang.reflect.Method m = parent.getClass().getMethod("getTileEntity");
            Object result = m.invoke(parent);
            if (result instanceof TileEntity) return (TileEntity) result;
        } catch (Exception ignored) {
        }
        // try host.getTileEntity() (DualityCentralInterface etc.)
        try {
            java.lang.reflect.Field f = parent.getClass().getDeclaredField("host");
            f.setAccessible(true);
            Object host = f.get(parent);
            if (host instanceof TileEntity) return (TileEntity) host;
            if (host != null) {
                java.lang.reflect.Method m = host.getClass().getMethod("getTileEntity");
                Object result = m.invoke(host);
                if (result instanceof TileEntity) return (TileEntity) result;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static IGridNode findTransmitterNode(BlockPos pos, int dim) {
        World world = DimensionManager.getWorld(dim);
        if (world == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] findTransmitterNode: world is null for dim={}", dim);
            return null;
        }
        if (!world.isBlockLoaded(pos)) {
            AE2Enhanced.LOGGER.debug("[AE2E] findTransmitterNode: block at {} not loaded", pos);
            return null;
        }

        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            AE2Enhanced.LOGGER.debug("[AE2E] findTransmitterNode: no TileEntity at {}", pos);
            return null;
        }
        if (!(te instanceof TileWirelessChannelTransmitter)) {
            AE2Enhanced.LOGGER.debug("[AE2E] findTransmitterNode: TileEntity at {} is {}, expected TileWirelessChannelTransmitter",
                    pos, te.getClass().getName());
            // 向后兼容：旧版卡片可能绑定到 IPartHost(线缆)上的 Part
            if (te instanceof IPartHost) {
                IPartHost host = (IPartHost) te;
                for (appeng.api.util.AEPartLocation side : appeng.api.util.AEPartLocation.SIDE_LOCATIONS) {
                    IPart part = host.getPart(side);
                    if (part != null) {
                        try {
                            return part.getExternalFacingNode();
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
            return null;
        }

        TileWirelessChannelTransmitter tile = (TileWirelessChannelTransmitter) te;
        try {
            IGridNode node = tile.getGridNode(appeng.api.util.AEPartLocation.INTERNAL);
            if (node == null) {
                AE2Enhanced.LOGGER.debug("[AE2E] findTransmitterNode: tile.getGridNode() returned null at {}", pos);
            }
            return node;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] findTransmitterNode: getGridNode threw exception at {}", pos, e);
            return null;
        }
    }
}
