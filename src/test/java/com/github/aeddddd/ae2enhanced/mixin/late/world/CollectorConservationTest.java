package com.github.aeddddd.ae2enhanced.mixin.late.world;

import appeng.me.helpers.AENetworkProxy;
import com.github.aeddddd.ae2enhanced.collector.CollectorRegistry;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.collector.TileAdvancedMECollector;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CollectorConservationTest {

    private World world;
    private TileAdvancedMECollector collector;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    private static World mockWorld() {
        World world = mock(World.class);
        WorldProvider provider = mock(WorldProvider.class);
        when(provider.getDimension()).thenReturn(0);
        when(world.getBlockState(any(BlockPos.class))).thenReturn(net.minecraft.init.Blocks.STONE.getDefaultState());
        try {
            set(world, World.class, "provider", provider);
            set(world, World.class, "isRemote", false);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return world;
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private static void set(Object target, Class<?> owner, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void startScope() {
        RuntimeScopes.currentServer().ifPresent(scope -> {
            scope.close();
            RuntimeScopes.releaseServer();
        });
        RuntimeScopes.startServer();
    }

    @AfterEach
    void cleanRegistry() {
        if (collector != null) {
            CollectorRegistry.unregister(collector);
        }
        RuntimeScopes.closeServer();
        RuntimeScopes.releaseServer();
    }

    @Test
    void worldSpawnKeepsOnlyRemainderOnOriginalEntity() throws Exception {
        setupCollector();
        EntityItem entity = new EntityItem(world, 0, 0, 0, new ItemStack(Items.APPLE, 10));
        CallbackInfoReturnable<Boolean> callback = new CallbackInfoReturnable<>("spawnEntity", true);

        invoke(new MixinWorldSpawnEntity(), "ae2e$onSpawnEntity",
                new Class<?>[]{net.minecraft.entity.Entity.class, CallbackInfoReturnable.class}, entity, callback);

        assertThat(entity.getItem().getCount()).isEqualTo(6);
        assertThat(callback.getReturnValue()).isNull();
    }

    @Test
    void playerDropShrinksOriginalStackToRemainder() throws Exception {
        ItemStack dropped = new ItemStack(Items.APPLE, 10);
        ItemStack remaining = new ItemStack(Items.APPLE, 6);
        Method apply = MixinEntityPlayerDropItem.class.getDeclaredMethod(
                "ae2e$applyRemainder", ItemStack.class, ItemStack.class);
        apply.setAccessible(true);
        apply.invoke(null, dropped, remaining);

        assertThat(dropped.getCount()).isEqualTo(6);
    }

    @Test
    void blockSpawnShrinksOriginalStackToRemainder() throws Exception {
        setupCollector();
        ItemStack dropped = new ItemStack(Items.APPLE, 10);
        CallbackInfo callback = new CallbackInfo("spawnAsEntity", true);

        Method method = MixinBlockSpawnAsEntity.class.getDeclaredMethod("ae2e$onSpawnAsEntity",
                World.class, BlockPos.class, ItemStack.class, CallbackInfo.class);
        method.setAccessible(true);
        method.invoke(null, world, BlockPos.ORIGIN, dropped, callback);

        assertThat(dropped.getCount()).isEqualTo(6);
        assertThat(callback.isCancelled()).isFalse();
    }

    @Test
    void breakDisablesCollectionAndDropsOnlyRealUpgradesAndBuffer() throws Exception {
        world = mockWorld();
        TileAdvancedMECollector tile = new TileAdvancedMECollector();
        tile.setWorld(world);
        tile.setPos(BlockPos.ORIGIN);
        AENetworkProxy proxy = mock(AENetworkProxy.class);
        when(proxy.getMachineRepresentation()).thenReturn(new ItemStack(Items.BUCKET));
        set(tile, TileAdvancedMECollector.class.getSuperclass(), "proxy", proxy);
        tile.getConfig().setStackInSlot(0, new ItemStack(Items.APPLE, 3));
        ((net.minecraftforge.items.IItemHandlerModifiable) tile.getInventoryByName("upgrades"))
                .setStackInSlot(0, new ItemStack(Items.BUCKET, 1));
        tile.getBuffer().setStackInSlot(0, new ItemStack(Items.DIAMOND, 9));
        collector = tile;
        CollectorRegistry.register(tile);

        tile.dropContents();

        ArgumentCaptor<net.minecraft.entity.Entity> spawned = ArgumentCaptor.forClass(net.minecraft.entity.Entity.class);
        verify(world, org.mockito.Mockito.times(2)).spawnEntity(spawned.capture());
        assertThat(tile.getConfig().getStackInSlot(0).getCount()).isEqualTo(3);
        assertThat(tile.getInventoryByName("upgrades").getStackInSlot(0).isEmpty()).isTrue();
        assertThat(tile.getBuffer().getStackInSlot(0).isEmpty()).isTrue();
        assertThat(tile.isActive()).isFalse();
        assertThat(CollectorRegistry.findBestCollector(world, BlockPos.ORIGIN)).isNull();
        assertThat(spawned.getAllValues()).allMatch(EntityItem.class::isInstance);
        assertThat(((EntityItem) spawned.getAllValues().get(0)).getItem().getItem()).isEqualTo(Items.BUCKET);
        assertThat(((EntityItem) spawned.getAllValues().get(1)).getItem().getCount()).isEqualTo(9);
    }

    private void setupCollector() {
        world = mockWorld();
        collector = mock(TileAdvancedMECollector.class);
        when(collector.getWorld()).thenReturn(world);
        when(collector.getPos()).thenReturn(BlockPos.ORIGIN);
        when(collector.isActive()).thenReturn(true);
        when(collector.getCollectionArea()).thenReturn(new AxisAlignedBB(-10, -10, -10, 10, 10, 10));
        when(collector.tryCollectStackForced(any())).thenAnswer(invocation -> {
            ItemStack input = invocation.getArgument(0);
            ItemStack result = input.copy();
            result.setCount(6);
            return result;
        });
        CollectorRegistry.register(collector);
    }
}
