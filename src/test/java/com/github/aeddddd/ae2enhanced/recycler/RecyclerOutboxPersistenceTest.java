package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.AENetworkProxy;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.ItemFluidDrop;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.tile.TileAENetworkBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RecyclerOutboxPersistenceTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        if (net.minecraft.tileentity.TileEntity.getKey(TileMENetworkRecycler.class) == null) {
            net.minecraft.tileentity.TileEntity.register(
                    "ae2enhanced:me_network_recycler", TileMENetworkRecycler.class);
        }
    }

    private static RecyclerNetworkHandler itemHandler(TileMENetworkRecycler tile) throws Exception {
        return get(tile, "networkHandler");
    }

    private static RecyclerFluidNetworkHandler fluidHandler(TileMENetworkRecycler tile) throws Exception {
        return get(tile, "fluidNetworkHandler");
    }

    private static BulkCollector items(TileMENetworkRecycler tile) throws Exception {
        return get(itemHandler(tile), "collector");
    }

    private static FluidBulkCollector fluids(TileMENetworkRecycler tile) throws Exception {
        return get(fluidHandler(tile), "collector");
    }

    private static void flush(Object handler) throws Exception {
        Method method = handler.getClass().getDeclaredMethod("flushCollector");
        method.setAccessible(true);
        method.invoke(handler);
    }

    private static IAEItemStack itemRemainder(IAEItemStack input, long amount) {
        IAEItemStack result = input.copy();
        result.setStackSize(amount);
        return result;
    }

    private static IAEFluidStack fluidRemainder(IAEFluidStack input, long amount) {
        IAEFluidStack result = input.copy();
        result.setStackSize(amount);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T get(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (T) field.get(target);
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
    void closeScope() {
        RuntimeScopes.closeServer();
        RuntimeScopes.releaseServer();
    }

    @Test
    void legacyTileWithoutOutboxLoadsAsEmpty() throws Exception {
        TileMENetworkRecycler tile = new TileMENetworkRecycler();
        NBTTagCompound legacy = new NBTTagCompound();
        legacy.setString("id", "ae2enhanced:me_network_recycler");

        tile.readFromNBT(legacy);

        NBTTagCompound saved = tile.writeToNBT(new NBTTagCompound());
        NBTTagCompound outbox = saved.getCompoundTag("RecyclerOutbox");
        assertThat(outbox.getInteger("Version")).isEqualTo(1);
        assertThat(outbox.getTagList("Items", Constants.NBT.TAG_COMPOUND).tagCount()).isZero();
        assertThat(outbox.getTagList("Fluids", Constants.NBT.TAG_COMPOUND).tagCount()).isZero();
    }

    @Test
    void reloadUnloadPartialInjectionAndBreakConserveExactItemAndFluidRemainder() throws Exception {
        boolean oldForce = AE2EnhancedConfig.recycler.forceHyperdimensionalStorage;
        AE2EnhancedConfig.recycler.forceHyperdimensionalStorage = false;
        ItemFluidDrop oldDrop = ItemRegistry.FLUID_DROP;
        ItemRegistry.FLUID_DROP = new ItemFluidDrop();
        try {
            TileMENetworkRecycler original = new TileMENetworkRecycler();
            ItemStack apples = new ItemStack(Items.APPLE, 10);
            apples.setTagInfo("quality", new net.minecraft.nbt.NBTTagString("persistent"));
            FluidStack water = new FluidStack(FluidRegistry.WATER, 1000);
            water.tag = new NBTTagCompound();
            water.tag.setString("origin", "persistent");
            items(original).add(apples);
            fluids(original).add(water);
            NBTTagCompound firstSave = original.writeToNBT(new NBTTagCompound());
            assertThat(firstSave.getCompoundTag("RecyclerOutbox").getInteger("Version")).isEqualTo(1);

            TileMENetworkRecycler restored = new TileMENetworkRecycler();
            NetworkFixture fixture = new NetworkFixture(restored);
            restored.readFromNBT(firstSave);
            restored.onChunkUnload();
            restored.onLoad();
            assertThat(items(restored).snapshot()).extracting(IAEItemStack::getStackSize).containsExactly(10L);
            assertThat(fluids(restored).snapshot()).extracting(IAEFluidStack::getStackSize).containsExactly(1000L);
            when(fixture.itemMonitor.injectItems(any(), eq(Actionable.MODULATE), any()))
                    .thenAnswer(invocation -> itemRemainder(invocation.getArgument(0), 6));
            when(fixture.fluidMonitor.injectItems(any(), eq(Actionable.MODULATE), any()))
                    .thenAnswer(invocation -> fluidRemainder(invocation.getArgument(0), 600));

            flush(itemHandler(restored));
            flush(fluidHandler(restored));

            NBTTagCompound secondSave = restored.writeToNBT(new NBTTagCompound());
            NBTTagCompound secondOutbox = secondSave.getCompoundTag("RecyclerOutbox");
            assertThat(secondOutbox.getTagList("Items", Constants.NBT.TAG_COMPOUND)
                    .getCompoundTagAt(0).getLong("Cnt")).isEqualTo(6);
            NBTTagCompound savedFluid = secondOutbox.getTagList("Fluids", Constants.NBT.TAG_COMPOUND)
                    .getCompoundTagAt(0);
            assertThat(savedFluid.getLong("Cnt")).isEqualTo(600);
            assertThat(savedFluid.getCompoundTag("Tag").getString("origin")).isEqualTo("persistent");

            TileMENetworkRecycler reloadedAfterPartial = new TileMENetworkRecycler();
            NetworkFixture finalFixture = new NetworkFixture(reloadedAfterPartial);
            reloadedAfterPartial.readFromNBT(secondSave);
            assertThat(items(reloadedAfterPartial).snapshot())
                    .extracting(IAEItemStack::getStackSize).containsExactly(6L);
            assertThat(fluids(reloadedAfterPartial).snapshot())
                    .extracting(IAEFluidStack::getStackSize).containsExactly(600L);
            reloadedAfterPartial.dropContents();

            ArgumentCaptor<Entity> spawned = ArgumentCaptor.forClass(Entity.class);
            verify(finalFixture.world, org.mockito.Mockito.times(2)).spawnEntity(spawned.capture());
            List<Entity> drops = spawned.getAllValues();
            ItemStack itemDrop = ((EntityItem) drops.get(0)).getItem();
            assertThat(itemDrop.getCount()).isEqualTo(6);
            assertThat(itemDrop.getTagCompound().getString("quality")).isEqualTo("persistent");
            FluidStack fluidDrop = ItemFluidDrop.getFluidStack(((EntityItem) drops.get(1)).getItem());
            assertThat(fluidDrop.amount).isEqualTo(600);
            assertThat(fluidDrop.tag.getString("origin")).isEqualTo("persistent");
            assertThat(items(reloadedAfterPartial).isEmpty()).isTrue();
            assertThat(fluids(reloadedAfterPartial).isEmpty()).isTrue();
        } finally {
            ItemRegistry.FLUID_DROP = oldDrop;
            AE2EnhancedConfig.recycler.forceHyperdimensionalStorage = oldForce;
        }
    }

    @Test
    void unknownEntriesSurviveReadWriteWithoutBlockingKnownEntries() {
        TileMENetworkRecycler tile = new TileMENetworkRecycler();
        NBTTagCompound outbox = new NBTTagCompound();
        outbox.setInteger("Version", 1);
        NBTTagCompound unknownItem = new NBTTagCompound();
        unknownItem.setString("id", "missing_mod:item");
        unknownItem.setLong("Cnt", 12);
        unknownItem.setString("Future", "keep");
        NBTTagCompound unknownFluid = new NBTTagCompound();
        unknownFluid.setString("FluidName", "missing_mod:fluid");
        unknownFluid.setLong("Cnt", 34);
        unknownFluid.setString("Future", "keep");
        net.minecraft.nbt.NBTTagList itemList = new net.minecraft.nbt.NBTTagList();
        itemList.appendTag(unknownItem);
        outbox.setTag("Items", itemList);
        net.minecraft.nbt.NBTTagList fluidList = new net.minecraft.nbt.NBTTagList();
        fluidList.appendTag(unknownFluid);
        outbox.setTag("Fluids", fluidList);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("RecyclerOutbox", outbox);

        tile.readFromNBT(root);
        NBTTagCompound rewritten = tile.writeToNBT(new NBTTagCompound())
                .getCompoundTag("RecyclerOutbox");

        assertThat(rewritten.getTagList("Items", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0))
                .isEqualTo(unknownItem);
        assertThat(rewritten.getTagList("Fluids", Constants.NBT.TAG_COMPOUND).getCompoundTagAt(0))
                .isEqualTo(unknownFluid);
    }

    @Test
    void futureOutboxVersionIsFrozenVerbatim() {
        TileMENetworkRecycler tile = new TileMENetworkRecycler();
        NBTTagCompound outbox = new NBTTagCompound();
        outbox.setInteger("Version", 99);
        outbox.setString("FuturePayload", "must-survive");
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("RecyclerOutbox", outbox);

        tile.readFromNBT(root);

        assertThat(tile.writeToNBT(new NBTTagCompound()).getCompoundTag("RecyclerOutbox"))
                .isEqualTo(outbox);
    }

    private static final class NetworkFixture {
        final World world = mock(World.class);
        final AENetworkProxy proxy = mock(AENetworkProxy.class);
        final IMEMonitor<IAEItemStack> itemMonitor = mock(IMEMonitor.class);
        final IMEMonitor<IAEFluidStack> fluidMonitor = mock(IMEMonitor.class);

        NetworkFixture(TileMENetworkRecycler tile) throws Exception {
            WorldProvider provider = mock(WorldProvider.class);
            when(provider.getDimension()).thenReturn(0);
            Field worldProvider = World.class.getDeclaredField("provider");
            worldProvider.setAccessible(true);
            worldProvider.set(world, provider);
            when(world.getBlockState(any(BlockPos.class)))
                    .thenReturn(net.minecraft.init.Blocks.STONE.getDefaultState());
            tile.setWorld(world);
            tile.setPos(BlockPos.ORIGIN);
            Field proxyField = TileAENetworkBase.class.getDeclaredField("proxy");
            proxyField.setAccessible(true);
            proxyField.set(tile, proxy);
            IGrid grid = mock(IGrid.class);
            IStorageGrid storage = mock(IStorageGrid.class);
            when(proxy.getGrid()).thenReturn(grid);
            when(grid.getCache(IStorageGrid.class)).thenReturn(storage);
            when(storage.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class)))
                    .thenReturn(itemMonitor);
            when(storage.getInventory(AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class)))
                    .thenReturn(fluidMonitor);
        }
    }
}
