package com.github.aeddddd.ae2enhanced.tile;

import appeng.me.helpers.AENetworkProxy;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class StructureDirtyTest {
    @org.junit.jupiter.api.BeforeAll
    static void boot() {
        com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap.boot();
    }

    @Test
    void pendingOutputMutationMarksAssemblyDirty() {
        TileAssemblyController tile = spy(new TileAssemblyController());

        tile.addPendingOutput(new ItemStack(Items.COAL, 3));

        verify(tile).markDirty();
    }

    @Test
    void formedMutationMarksControllerDirty() {
        TileAssemblyController tile = spy(new TileAssemblyController());
        World world = mock(World.class);
        org.mockito.Mockito.when(world.getBlockState(org.mockito.ArgumentMatchers.any()))
                .thenReturn(net.minecraft.init.Blocks.STONE.getDefaultState());
        tile.setWorld(world);
        tile.proxy = mock(AENetworkProxy.class);

        tile.assemble();

        verify(tile).markDirty();
    }
}
