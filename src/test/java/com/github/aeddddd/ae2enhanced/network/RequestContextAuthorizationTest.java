package com.github.aeddddd.ae2enhanced.network;

import com.github.aeddddd.ae2enhanced.collector.TileAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.container.ContainerDisplayWall;
import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.tile.TileDisplayPanel;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestContextAuthorizationTest {
    private static final BlockPos POS = new BlockPos(4, 64, -3);

    private EntityPlayer player;
    private World world;
    private TileSmartPatternInterface smartTile;
    private ContainerSmartPatternInterface smartContainer;

    @BeforeEach
    void setUp() {
        player = mock(EntityPlayer.class);
        world = mock(World.class);
        smartTile = mock(TileSmartPatternInterface.class);
        smartContainer = mock(ContainerSmartPatternInterface.class);
        player.world = world;
        player.openContainer = smartContainer;
        player.inventoryContainer = new Container() {
            @Override
            public boolean canInteractWith(EntityPlayer ignored) {
                return true;
            }
        };
        when(player.getDistanceSq(POS)).thenReturn(16.0D);
        when(world.getTileEntity(POS)).thenReturn(smartTile);
        when(world.canMineBlockBody(player, POS)).thenReturn(true);
        when(smartContainer.getTile()).thenReturn(smartTile);
        when(smartContainer.canInteractWith(player)).thenReturn(true);
    }

    @Test
    void smartPatternAcceptsOnlyTheAuthorizedOpenTile() {
        assertThat(RequestContext.smartPatternTile(player, POS)).isSameAs(smartTile);

        TileSmartPatternInterface otherTile = mock(TileSmartPatternInterface.class);
        when(smartContainer.getTile()).thenReturn(otherTile);
        assertThat(RequestContext.smartPatternTile(player, POS)).isNull();

        when(smartContainer.getTile()).thenReturn(smartTile);
        player.openContainer = player.inventoryContainer;
        assertThat(RequestContext.smartPatternTile(player, POS)).isNull();
    }

    @Test
    void smartPatternRejectsDistanceWrongTileAndPermissions() {
        when(player.getDistanceSq(POS)).thenReturn(RequestContext.MAX_INTERACTION_DISTANCE_SQ + 1.0D);
        assertThat(RequestContext.smartPatternTile(player, POS)).isNull();

        when(player.getDistanceSq(POS)).thenReturn(0.0D);
        when(world.getTileEntity(POS)).thenReturn(mock(TileEntity.class));
        assertThat(RequestContext.smartPatternTile(player, POS)).isNull();

        when(world.getTileEntity(POS)).thenReturn(smartTile);
        when(world.canMineBlockBody(player, POS)).thenReturn(false);
        assertThat(RequestContext.smartPatternTile(player, POS)).isNull();
    }

    @Test
    void collectorRequiresMatchingDimensionWorldDistanceAndPermission() {
        TileAdvancedMECollector collector = mock(TileAdvancedMECollector.class);
        player.dimension = 7;
        when(world.getTileEntity(POS)).thenReturn(collector);
        when(collector.getWorld()).thenReturn(world);
        when(collector.getActionableNode()).thenReturn(null);

        assertThat(RequestContext.collectorTile(player, POS, 7)).isSameAs(collector);
        assertThat(RequestContext.collectorTile(player, POS, 8)).isNull();

        when(player.getDistanceSq(POS)).thenReturn(65.0D);
        assertThat(RequestContext.collectorTile(player, POS, 7)).isNull();
        when(player.getDistanceSq(POS)).thenReturn(1.0D);
        when(world.canMineBlockBody(player, POS)).thenReturn(false);
        assertThat(RequestContext.collectorTile(player, POS, 7)).isNull();
    }

    @Test
    void collectorChecksAeBuildPermissionWhenNetworkIsOnline() throws Exception {
        TileAdvancedMECollector collector = mock(TileAdvancedMECollector.class);
        appeng.api.networking.IGridNode node = mock(appeng.api.networking.IGridNode.class);
        appeng.api.networking.IGrid grid = mock(appeng.api.networking.IGrid.class);
        appeng.api.networking.security.ISecurityGrid security =
                mock(appeng.api.networking.security.ISecurityGrid.class);
        player.dimension = 0;
        when(world.getTileEntity(POS)).thenReturn(collector);
        when(collector.getWorld()).thenReturn(world);
        when(collector.getActionableNode()).thenReturn(node);
        when(node.getGrid()).thenReturn(grid);
        when(grid.getCache(appeng.api.networking.security.ISecurityGrid.class)).thenReturn(security);
        when(security.hasPermission(player, appeng.api.config.SecurityPermissions.BUILD)).thenReturn(false);
        assertThat(RequestContext.collectorTile(player, POS, 0)).isNull();

        when(security.hasPermission(player, appeng.api.config.SecurityPermissions.BUILD)).thenReturn(true);
        assertThat(RequestContext.collectorTile(player, POS, 0)).isSameAs(collector);
    }

    @Test
    void omniQueryRequiresCurrentInteractiveContainerAndExactSession() {
        com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm terminal =
                mock(com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm.class);
        player.openContainer = terminal;
        when(terminal.getContainerSessionId()).thenReturn(91L);
        when(terminal.canInteractWith(player)).thenReturn(true);
        assertThat(RequestContext.omniTerminal(player, 91L)).isSameAs(terminal);
        assertThat(RequestContext.omniTerminal(player, 92L)).isNull();
        assertThat(RequestContext.omniTerminal(player, 0L)).isNull();
        when(terminal.canInteractWith(player)).thenReturn(false);
        assertThat(RequestContext.omniTerminal(player, 91L)).isNull();
    }

    @Test
    void displayContainerMustBelongToTargetTile() {
        TileDisplayPanel display = mock(TileDisplayPanel.class);
        ContainerDisplayWall container = mock(ContainerDisplayWall.class);
        when(world.getTileEntity(POS)).thenReturn(display);
        when(container.getTile()).thenReturn(display);
        when(container.canInteractWith(player)).thenReturn(true);
        player.openContainer = container;
        assertThat(RequestContext.displayTile(player, POS)).isSameAs(display);

        when(container.getTile()).thenReturn(mock(TileDisplayPanel.class));
        assertThat(RequestContext.displayTile(player, POS)).isNull();
    }
}
