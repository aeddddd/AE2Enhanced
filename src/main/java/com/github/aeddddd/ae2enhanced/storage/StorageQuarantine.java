package com.github.aeddddd.ae2enhanced.storage;

import java.io.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;

/** 关闭失败的持久隔离记录；不改动同目录的 WAL/base。 */
final class StorageQuarantine {
    static final String FILE_NAME = "close-quarantine.properties";

    private StorageQuarantine() {}

    static Path path(File worldDir, UUID nexusId) {
        return new File(new File(worldDir, "ae2enhanced/storage"), nexusId.toString())
            .toPath().resolve(FILE_NAME);
    }

    static boolean exists(File worldDir, UUID nexusId) {
        return Files.isRegularFile(path(worldDir, nexusId));
    }

    static Entry read(File worldDir, UUID nexusId) throws IOException {
        Properties properties = new Properties();
        try (InputStream in = new BufferedInputStream(new FileInputStream(path(worldDir, nexusId).toFile()))) {
            properties.load(in);
        }
        return new Entry(properties.getProperty("key"), CloseResult.fromProperties(properties),
            properties.getProperty("reason", ""));
    }

    static void write(File worldDir, UUID nexusId, String key, CloseResult result, String reason,
                      HyperdimensionalStorageTestHooks hooks) throws IOException {
        Path target = path(worldDir, nexusId);
        Files.createDirectories(target.getParent());
        Path temporary = target.resolveSibling(FILE_NAME + ".tmp");
        Properties properties = result.toProperties();
        properties.setProperty("key", key);
        properties.setProperty("reason", reason);
        properties.setProperty("recordedAtMs", Long.toString(System.currentTimeMillis()));
        boolean moved = false;
        try {
            hooks.beforeQuarantineWrite();
            try (FileOutputStream fileOut = new FileOutputStream(temporary.toFile());
                 OutputStream out = new BufferedOutputStream(fileOut)) {
                properties.store(out, "AE2Enhanced storage close quarantine");
                out.flush();
                fileOut.getChannel().force(true);
            }
            move(temporary, target);
            moved = true;
        } finally {
            if (!moved) Files.deleteIfExists(temporary);
        }
    }

    static void clear(File worldDir, UUID nexusId) throws IOException {
        Files.deleteIfExists(path(worldDir, nexusId));
    }

    private static void move(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static final class Entry {
        final String key;
        final CloseResult result;
        final String reason;

        Entry(String key, CloseResult result, String reason) {
            this.key = key;
            this.result = result;
            this.reason = reason;
        }
    }
}
