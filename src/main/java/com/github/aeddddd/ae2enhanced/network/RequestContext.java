package com.github.aeddddd.ae2enhanced.network;

import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.collector.TileAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.container.ContainerDisplayWall;
import com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm;
import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.container.ContainerUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.tile.TileDisplayPanel;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

/** M1b 服务端修改请求的统一交互上下文；M5 的描述符不在这里另建语义。 */
public final class RequestContext {
    public static final double MAX_INTERACTION_DISTANCE_SQ = 64.0D;

    private RequestContext() {
    }

    public static TileSmartPatternInterface smartPatternTile(EntityPlayer player, BlockPos pos) {
        if (!canReach(player, pos)) return null;
        TileEntity tile = player.world.getTileEntity(pos);
        if (!(tile instanceof TileSmartPatternInterface)) return null;
        if (!(player.openContainer instanceof ContainerSmartPatternInterface)) return null;
        ContainerSmartPatternInterface container = (ContainerSmartPatternInterface) player.openContainer;
        if (container.getTile() != tile || !container.canInteractWith(player)) return null;
        return hasPermission(player, pos) ? (TileSmartPatternInterface) tile : null;
    }

    public static TileDisplayPanel displayTile(EntityPlayer player, BlockPos pos) {
        if (!canReach(player, pos)) return null;
        TileEntity tile = player.world.getTileEntity(pos);
        if (!(tile instanceof TileDisplayPanel)) return null;
        if (!(player.openContainer instanceof ContainerDisplayWall)) return null;
        ContainerDisplayWall container = (ContainerDisplayWall) player.openContainer;
        if (container.getTile() != tile || !container.canInteractWith(player)) return null;
        return (TileDisplayPanel) tile;
    }

    public static TileAdvancedMECollector collectorTile(EntityPlayer player, BlockPos pos, int dimension) {
        if (player == null || player.world == null
                || player.dimension != dimension || !canReach(player, pos)) {
            return null;
        }
        TileEntity tile = player.world.getTileEntity(pos);
        if (!(tile instanceof TileAdvancedMECollector) || tile.getWorld() != player.world) return null;
        return hasPermission(player, pos) ? (TileAdvancedMECollector) tile : null;
    }

    public static ContainerOmniTerm omniTerminal(EntityPlayer player, long containerSessionId) {
        if (player == null || !(player.openContainer instanceof ContainerOmniTerm)) return null;
        ContainerOmniTerm container = (ContainerOmniTerm) player.openContainer;
        return containerSessionId != 0L && container.getContainerSessionId() == containerSessionId
                && container.canInteractWith(player) ? container : null;
    }

    public static boolean canUseHeldMemoryCardAt(EntityPlayer player, BlockPos pos) {
        return player != null && player.openContainer == player.inventoryContainer
                && player.getHeldItemMainhand().getItem() instanceof ItemUniversalMemoryCard
                && canReach(player, pos) && hasPermission(player, pos);
    }

    public static boolean canConfigureHeldMemoryCard(EntityPlayer player) {
        return player != null && player.openContainer instanceof ContainerUniversalMemoryCard
                && player.openContainer.canInteractWith(player)
                && player.getHeldItemMainhand().getItem() instanceof ItemUniversalMemoryCard;
    }

    public static boolean canReach(EntityPlayer player, BlockPos pos) {
        return player != null && player.world != null && pos != null
                && player.getDistanceSq(pos) <= MAX_INTERACTION_DISTANCE_SQ;
    }

    private static boolean hasPermission(EntityPlayer player, BlockPos pos) {
        if (!Platform.hasPermissions(player.world, pos, player)) return false;
        TileEntity tile = player.world.getTileEntity(pos);
        appeng.api.networking.IGridNode node = null;
        try {
            if (tile instanceof appeng.api.networking.security.IActionHost) {
                node = ((appeng.api.networking.security.IActionHost) tile).getActionableNode();
            } else if (tile instanceof appeng.me.helpers.IGridProxyable
                    && ((appeng.me.helpers.IGridProxyable) tile).getProxy() != null) {
                node = ((appeng.me.helpers.IGridProxyable) tile).getProxy().getNode();
            }
            if (node == null) return true;
            appeng.api.networking.security.ISecurityGrid security = node.getGrid().getCache(
                    appeng.api.networking.security.ISecurityGrid.class);
            return security == null || security.hasPermission(player,
                    appeng.api.config.SecurityPermissions.BUILD);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
