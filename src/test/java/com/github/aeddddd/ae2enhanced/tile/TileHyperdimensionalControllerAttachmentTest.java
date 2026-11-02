package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.storage.AttachmentToken;
import com.github.aeddddd.ae2enhanced.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.storage.HyperdimensionalStorageManager;
import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyStorageChannel;
import com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TileHyperdimensionalControllerAttachmentTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void boot() throws Exception {
        AE2TestBootstrap.boot();
        Field namedMods = net.minecraftforge.fml.common.Loader.class.getDeclaredField("namedMods");
        namedMods.setAccessible(true);
        if (namedMods.get(net.minecraftforge.fml.common.Loader.instance()) == null) {
            namedMods.set(net.minecraftforge.fml.common.Loader.instance(), new java.util.HashMap<>());
        }
        try {
            AEApi.instance().storage().getStorageChannel(IEnergyStorageChannel.class);
        } catch (RuntimeException absent) {
            AEApi.instance().storage().registerStorageChannel(
                    IEnergyStorageChannel.class, new EnergyStorageChannel());
        }
    }

    private static TileHyperdimensionalController attach(
            HyperdimensionalStorageManager.NexusSession session) throws Exception {
        TileHyperdimensionalController tile = new TileHyperdimensionalController();
        setField(tile, "session", session);
        invoke(tile, "attachSession");
        return tile;
    }

    private static void closeStorage(TileHyperdimensionalController tile) {
        if (tile == null) return;
        try {
            invoke(tile, "closeStorage");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void injectItemAndFluid(HyperdimensionalStorageManager.NexusSession session) {
        injectItem(session.getItemAdapter());
        IFluidStorageChannel channel = AEApi.instance().storage()
                .getStorageChannel(IFluidStorageChannel.class);
        IAEFluidStack fluid = channel.createStack(new FluidStack(FluidRegistry.WATER, 1));
        fluid.setStackSize(1);
        session.getFluidAdapter().injectItems(fluid, Actionable.MODULATE, null);
    }

    private static void injectItem(ItemStorageAdapter adapter) {
        IItemStorageChannel channel = AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class);
        IAEItemStack item = channel.createStack(new ItemStack(Items.DYE, 1, 4));
        item.setStackSize(1);
        adapter.injectItems(item, Actionable.MODULATE, null);
    }

    private static int pendingAlterations(TileHyperdimensionalController tile) throws Exception {
        Field field = TileHyperdimensionalController.class.getDeclaredField("pendingAlterations");
        field.setAccessible(true);
        Map<?, ?> pending = (Map<?, ?>) field.get(tile);
        int count = 0;
        for (Object value : pending.values()) {
            count += ((List<?>) value).size();
        }
        return count;
    }

    private static AttachmentToken reflectAttach(Object adapter, Runnable onChange,
                                                  BiConsumer<Object, IActionSource> postChange) throws Exception {
        Method method = TileHyperdimensionalController.class.getDeclaredMethod("attachSubscriber",
                Object.class, Runnable.class, BiConsumer.class);
        method.setAccessible(true);
        return (AttachmentToken) method.invoke(null, adapter, onChange, postChange);
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = TileHyperdimensionalController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invoke(Object target, String name) throws Exception {
        Method method = TileHyperdimensionalController.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(target);
    }

    @AfterEach
    void closeSessions() {
        HyperdimensionalStorageManager.closeAll();
    }

    @Test
    void sharedSessionNotifiesBothTilesAndDetachReleasesOnlyThatTile() throws Exception {
        World world = mock(World.class);
        ISaveHandler saves = mock(ISaveHandler.class);
        when(world.getSaveHandler()).thenReturn(saves);
        when(saves.getWorldDirectory()).thenReturn(tempDir.toFile());
        UUID nexusId = UUID.randomUUID();

        HyperdimensionalStorageManager.NexusSession firstSession =
                HyperdimensionalStorageManager.acquire(world, nexusId);
        HyperdimensionalStorageManager.NexusSession secondSession =
                HyperdimensionalStorageManager.acquire(world, nexusId);
        assertThat(secondSession).isSameAs(firstSession);
        assertThat(intField(firstSession, "attachCount")).isEqualTo(2);
        firstSession.getFile().awaitLoaded();

        TileHyperdimensionalController first = attach(firstSession);
        TileHyperdimensionalController second = attach(secondSession);
        try {
            injectItemAndFluid(firstSession);

            assertThat(pendingAlterations(first)).isEqualTo(2);
            assertThat(pendingAlterations(second)).isEqualTo(2);

            closeStorage(first);
            assertThat(intField(firstSession, "attachCount")).isEqualTo(1);
            injectItemAndFluid(firstSession);

            assertThat(pendingAlterations(first)).isEqualTo(2);
            assertThat(pendingAlterations(second)).isEqualTo(4);

            closeStorage(second);
            assertThat(intField(firstSession, "attachCount")).isZero();
            injectItemAndFluid(firstSession);
            assertThat(pendingAlterations(second)).isEqualTo(4);
        } finally {
            closeStorage(first);
            closeStorage(second);
        }
    }

    @Test
    void modernOptionalAdapterSubscriptionsAreIndependent() throws Exception {
        ModernOptionalAdapter adapter = new ModernOptionalAdapter();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();

        AttachmentToken firstToken = reflectAttach(adapter, null,
                (change, source) -> first.incrementAndGet());
        AttachmentToken secondToken = reflectAttach(adapter, null,
                (change, source) -> second.incrementAndGet());
        adapter.emit();
        assertThat(first).hasValue(1);
        assertThat(second).hasValue(1);

        firstToken.close();
        adapter.emit();
        assertThat(first).hasValue(1);
        assertThat(second).hasValue(2);

        secondToken.close();
        adapter.emit();
        assertThat(second).hasValue(2);
    }

    @Test
    void legacyOptionalDetachCannotClearNewerAttachment() throws Exception {
        LegacyOptionalAdapter adapter = new LegacyOptionalAdapter();
        AtomicInteger firstChanges = new AtomicInteger();
        AtomicInteger firstPosts = new AtomicInteger();
        AtomicInteger secondChanges = new AtomicInteger();
        AtomicInteger secondPosts = new AtomicInteger();

        AttachmentToken first = reflectAttach(adapter, firstChanges::incrementAndGet,
                (change, source) -> firstPosts.incrementAndGet());
        AttachmentToken second = reflectAttach(adapter, secondChanges::incrementAndGet,
                (change, source) -> secondPosts.incrementAndGet());
        adapter.emit();

        assertThat(firstChanges).hasValue(0);
        assertThat(firstPosts).hasValue(0);
        assertThat(secondChanges).hasValue(1);
        assertThat(secondPosts).hasValue(1);

        first.close();
        adapter.emit();
        assertThat(secondChanges).hasValue(2);
        assertThat(secondPosts).hasValue(2);

        second.close();
        adapter.emit();
        assertThat(secondChanges).hasValue(2);
        assertThat(secondPosts).hasValue(2);
        assertThat(adapter.onChange).isNull();
        assertThat(adapter.postChange).isNull();
    }

    @Test
    void legacyAdapterSettersOnlyReplaceTheirOwnSubscriptions() {
        ItemStorageAdapter adapter = new ItemStorageAdapter(mock(HyperdimensionalStorageFile.class));
        AtomicInteger legacyChanges = new AtomicInteger();
        AtomicInteger legacyPosts = new AtomicInteger();
        AtomicInteger independentChanges = new AtomicInteger();
        AtomicInteger independentPosts = new AtomicInteger();
        adapter.setOnChangeCallback(legacyChanges::incrementAndGet);
        adapter.setPostChangeCallback((change, source) -> legacyPosts.incrementAndGet());
        AttachmentToken independent = adapter.attachSubscriber(independentChanges::incrementAndGet,
                (change, source) -> independentPosts.incrementAndGet());

        injectItem(adapter);
        assertThat(legacyChanges).hasValue(1);
        assertThat(legacyPosts).hasValue(1);
        assertThat(independentChanges).hasValue(1);
        assertThat(independentPosts).hasValue(1);

        adapter.setOnChangeCallback(null);
        adapter.setPostChangeCallback(null);
        injectItem(adapter);
        assertThat(legacyChanges).hasValue(1);
        assertThat(legacyPosts).hasValue(1);
        assertThat(independentChanges).hasValue(2);
        assertThat(independentPosts).hasValue(2);

        independent.close();
        injectItem(adapter);
        assertThat(independentChanges).hasValue(2);
        assertThat(independentPosts).hasValue(2);
    }

    private static final class ModernOptionalAdapter {
        private final List<BiConsumer<Object, IActionSource>> subscribers = new CopyOnWriteArrayList<>();

        @SuppressWarnings({"unchecked", "rawtypes"})
        public AttachmentToken attachSubscriber(Runnable onChange,
                                                 BiConsumer<?, IActionSource> postChange) {
            BiConsumer<Object, IActionSource> subscriber = (BiConsumer) postChange;
            subscribers.add(subscriber);
            return new AttachmentToken(() -> subscribers.remove(subscriber));
        }

        void emit() {
            for (BiConsumer<Object, IActionSource> subscriber : subscribers) {
                subscriber.accept(new Object(), null);
            }
        }
    }

    private static final class LegacyOptionalAdapter {
        private Runnable onChange;
        private BiConsumer<Object, IActionSource> postChange;

        public void setOnChangeCallback(Runnable callback) {
            this.onChange = callback;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        public void setPostChangeCallback(BiConsumer callback) {
            this.postChange = callback;
        }

        void emit() {
            if (postChange != null) postChange.accept(new Object(), null);
            if (onChange != null) onChange.run();
        }
    }
}
