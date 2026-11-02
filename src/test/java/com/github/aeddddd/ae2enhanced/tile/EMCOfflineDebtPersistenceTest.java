package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.networking.IGrid;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EMCOfflineDebtPersistenceTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static Object handler(TileEMCInterface tile) throws Exception {
        Field field = TileEMCInterface.class.getDeclaredField("handler");
        field.setAccessible(true);
        return field.get(tile);
    }

    private static BigInteger pending(TileEMCInterface tile) throws Exception {
        return (BigInteger) handler(tile).getClass().getMethod("getPendingOfflineEmc").invoke(handler(tile));
    }

    private static void seed(TileEMCInterface tile, UUID owner, String debt) throws Exception {
        Field uuid = TileEMCInterface.class.getDeclaredField("ownerUUID");
        uuid.setAccessible(true);
        uuid.set(tile, owner);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("PendingOfflineEmc", debt);
        handler(tile).getClass().getMethod("readPendingOfflineEmc", NBTTagCompound.class)
                .invoke(handler(tile), nbt);
    }

    @Test
    void debtRoundTripAndMissingLegacyKeyAreUnambiguous() throws Exception {
        TileEMCInterface tile = new TileEMCInterface();
        Object handler = handler(tile);
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("PendingOfflineEmc", "123456789012345678901234567890");
        handler.getClass().getMethod("readPendingOfflineEmc", NBTTagCompound.class).invoke(handler, nbt);

        NBTTagCompound written = new NBTTagCompound();
        handler.getClass().getMethod("writePendingOfflineEmc", NBTTagCompound.class).invoke(handler, written);
        assertThat(written.getString("PendingOfflineEmc")).isEqualTo("123456789012345678901234567890");

        TileEMCInterface restored = new TileEMCInterface();
        Object restoredHandler = handler(restored);
        restoredHandler.getClass().getMethod("readPendingOfflineEmc", NBTTagCompound.class)
                .invoke(restoredHandler, written);
        assertThat(restoredHandler.getClass().getMethod("getPendingOfflineEmc").invoke(restoredHandler))
                .isEqualTo(new BigInteger("123456789012345678901234567890"));

        restoredHandler.getClass().getMethod("readPendingOfflineEmc", NBTTagCompound.class)
                .invoke(restoredHandler, new NBTTagCompound());
        assertThat(restoredHandler.getClass().getMethod("hasPendingOfflineEmc").invoke(restoredHandler))
                .isEqualTo(false);
    }

    @Test
    void failedChunkUnloadFlushRetainsDebtAndRejectsRebinding() throws Exception {
        TileEMCInterface tile = new TestTile();
        World world = mock(World.class);
        tile.setWorld(world);
        UUID original = UUID.randomUUID();
        seed(tile, original, "900");

        tile.onChunkUnload();
        assertThat(tile.getOwnerUUID()).isEqualTo(original);
        assertThat(pending(tile)).isEqualTo(new BigInteger("900"));

        EntityPlayer other = mock(EntityPlayer.class);
        when(other.getUniqueID()).thenReturn(UUID.randomUUID());
        tile.setOwner(other);
        assertThat(tile.getOwnerUUID()).isEqualTo(original);
        assertThat(pending(tile)).isEqualTo(new BigInteger("900"));
    }

    @Test
    void serverStoppingFlushAttemptsLoadedOfflineDebtWithoutClearingFailure() throws Exception {
        TileEMCInterface tile = new TestTile();
        World world = mock(World.class);
        tile.setWorld(world);
        UUID owner = UUID.randomUUID();
        seed(tile, owner, "700");
        tile.validate();
        try {
            TileEMCInterface.flushAllPendingOfflineEmc();
            assertThat(tile.getOwnerUUID()).isEqualTo(owner);
            assertThat(pending(tile)).isEqualTo(new BigInteger("700"));
        } finally {
            tile.invalidate();
        }
    }

    @Test
    void balanceChangesRefreshWholeCellViewOnThrottle() {
        TestTile tile = new TestTile();
        World world = mock(World.class);
        when(world.getTotalWorldTime()).thenReturn(100L);
        tile.setWorld(world);
        tile.markEmcBalanceDirty();
        tile.update();
        tile.markEmcBalanceDirty();
        tile.update();
        verify(tile.grid, times(1)).postEvent(org.mockito.ArgumentMatchers.any(MENetworkCellArrayUpdate.class));

        when(world.getTotalWorldTime())
                .thenReturn(100L + AE2EnhancedConfig.emcInterface.syncIntervalTicks);
        tile.update();
        verify(tile.grid, times(2)).postEvent(org.mockito.ArgumentMatchers.any(MENetworkCellArrayUpdate.class));
    }

    private static final class TestTile extends TileEMCInterface {
        private final AENetworkProxy proxy = mock(AENetworkProxy.class);
        private final IGrid grid = mock(IGrid.class);

        private TestTile() {
            try {
                when(proxy.getGrid()).thenReturn(grid);
            } catch (GridAccessException e) {
                throw new AssertionError(e);
            }
        }

        @Override
        public AENetworkProxy getProxy() {
            return proxy;
        }
    }
}
