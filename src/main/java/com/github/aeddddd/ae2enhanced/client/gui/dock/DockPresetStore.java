package com.github.aeddddd.ae2enhanced.client.gui.dock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.LayoutPresetBank;
import com.github.aeddddd.ae2enhanced.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.dock.workspace.DockLayoutCodec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class DockPresetStore {
    private static final Logger LOGGER = LogManager.getLogger(DockPresetStore.class);

    private LayoutPresetBank.Data data;
    private Path configDir;
    private DockLayoutCodec codec;

    LayoutPresetBank.Data data() {
        return data;
    }

    void replace(LayoutPresetBank.Data next) {
        data = next;
    }

    void attach(Path configDir, DockLayoutCodec codec, LayoutPresetBank.Data data) {
        this.configDir = configDir;
        this.codec = codec;
        this.data = data;
    }

    void load(DockWorkspace current, DockWorkspace filler) {
        if (configDir == null || codec == null) {
            return;
        }
        LayoutPresetBank.Data fallback = LayoutPresetBank.create(current, filler, null);
        Path file = configDir.resolve(LayoutPresetBank.PRESETS_FILE);
        data = LayoutPresetBank.load(file, codec, fallback);
        if (!Files.isRegularFile(file)) {
            saveQuietly();
        }
    }

    void capture(DockWorkspace current) {
        if (data != null && current != null) {
            data = data.withWorkspace(data.active(), current);
        }
    }

    void save() throws IOException {
        if (data == null || codec == null || configDir == null) {
            return;
        }
        LayoutPresetBank.save(configDir.resolve(LayoutPresetBank.PRESETS_FILE), data, codec);
    }

    void saveQuietly() {
        try {
            save();
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Failed to write spliced terminal layout presets", e);
        }
    }

    Path shareFolder() {
        return configDir == null ? null : configDir.resolve("share");
    }

    Path exportShare() throws IOException {
        Path share = shareFolder();
        Files.createDirectories(share);
        for (int index = 0; index < LayoutPresetBank.SLOT_COUNT; index++) {
            Path slotDir = share.resolve(LayoutPresetBank.shareSlotFolder(index));
            Files.createDirectories(slotDir);
            LayoutPresetBank.writeAtomic(slotDir.resolve(LayoutPresetBank.SHARE_LAYOUT_FILE),
                    LayoutPresetBank.encodeSlot(data.workspaces()[index], data.names()[index], codec));
        }
        LayoutPresetBank.zipShareSlots(share, share.resolve(LayoutPresetBank.SHARE_ZIP_FILE));
        return share;
    }
}
