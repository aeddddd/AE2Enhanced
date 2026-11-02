package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HyperdimensionalUnknownPreservationTest {
    private final List<HyperdimensionalStorageFile> files = new ArrayList<>();
    @TempDir
    Path tempDir;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @AfterEach
    void cleanup() throws Exception {
        for (HyperdimensionalStorageFile file : files) file.close();
        com.github.aeddddd.ae2enhanced.test.util.TemporaryDirectoryCleaner.clearContents(tempDir);
    }

    @Test
    void unknownItemSurvivesCheckpointButStaysOutOfNetworkView() throws Exception {
        UUID id = UUID.randomUUID();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", "ae2etest:no_such_item");
        tag.setShort("Damage", (short) 7);
        tag.setTag("tag", new NBTTagCompound());
        writeSection(id, StorageSection.ITEM, "items", tag, 123L);

        ItemStorageAdapter adapter = openItem(id);
        assertThat(adapter.getStorageMap().keySet().iterator().next()).isInstanceOf(PreservedDescriptor.class);
        assertThat(adapter.getAvailableItems(AEApi.instance().storage()
                .getStorageChannel(IItemStorageChannel.class).createList())).isEmpty();
        files.get(files.size() - 1).close();

        ItemStorageAdapter restored = openItem(id);
        assertThat(restored.getStorageMap().values().iterator().next().toBigInteger().intValueExact())
                .isEqualTo(123);
        assertThat(restored.getStorageMap().keySet().iterator().next()).isInstanceOf(PreservedDescriptor.class);
    }

    @Test
    void unknownFluidSurvivesCheckpointButStaysOutOfNetworkView() throws Exception {
        UUID id = UUID.randomUUID();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("id", "ae2etest:no_such_fluid");
        writeSection(id, StorageSection.FLUID, "fluids", tag, 456L);

        FluidStorageAdapter adapter = openFluid(id);
        assertThat(adapter.getStorageMap().keySet().iterator().next()).isInstanceOf(PreservedDescriptor.class);
        assertThat(adapter.getAvailableItems(AEApi.instance().storage()
                .getStorageChannel(IFluidStorageChannel.class).createList())).isEmpty();
        files.get(files.size() - 1).close();

        FluidStorageAdapter restored = openFluid(id);
        assertThat(restored.getStorageMap().values().iterator().next().toBigInteger().intValueExact())
                .isEqualTo(456);
        assertThat(restored.getStorageMap().keySet().iterator().next()).isInstanceOf(PreservedDescriptor.class);
    }

    private ItemStorageAdapter openItem(UUID id) {
        HyperdimensionalStorageFile file = new HyperdimensionalStorageFile(tempDir.toFile(), id);
        files.add(file);
        ItemStorageAdapter adapter = new ItemStorageAdapter(file);
        file.setStorageRef(adapter.getStorageMap());
        file.beginLoad();
        file.awaitLoaded();
        return adapter;
    }

    private FluidStorageAdapter openFluid(UUID id) {
        HyperdimensionalStorageFile file = new HyperdimensionalStorageFile(tempDir.toFile(), id);
        files.add(file);
        FluidStorageAdapter adapter = new FluidStorageAdapter(file);
        file.setFluidStorageRef(adapter.getStorageMap());
        file.beginLoad();
        file.awaitLoaded();
        return adapter;
    }

    private void writeSection(UUID id, StorageSection section, String name,
            NBTTagCompound tag, long count) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream descriptor = new DataOutputStream(bytes);
        if (section == StorageSection.ITEM) {
            PreservedDescriptor.writeLegacyItem(tag, descriptor);
        } else {
            PreservedDescriptor.writeLegacyFluid(tag, descriptor);
        }
        descriptor.flush();
        Path directory = tempDir.resolve("ae2enhanced/storage").resolve(id.toString());
        Path file = directory.resolve(name + ".bin");
        Files.createDirectories(directory);
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            out.write("AE2E".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            out.writeInt(2);
            out.writeLong(0L);
            out.writeInt(1);
            out.writeInt(bytes.size());
            out.write(bytes.toByteArray());
            byte[] magnitude = HugeCount.of(count).toByteArray();
            out.writeByte(1);
            out.writeInt(magnitude.length);
            out.write(magnitude);
        }
    }
}
