/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;

/** Atomic UTF-8 file persistence with an injected path for client config and tests. */
public final class AtomicFileDockLayoutStore implements DockLayoutPersistence {
    private final Path path;
    private final DockLayoutCodec codec;
    private Path lastQuarantinedPath;
    private boolean lastLoadCorrupt;

    public AtomicFileDockLayoutStore(Path path, DockLayoutCodec codec) {
        this.path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public Path path() {
        return path;
    }

    @Override
    public Optional<DockLayoutCodec.DecodedLayout> load() throws IOException {
        lastQuarantinedPath = null;
        lastLoadCorrupt = false;
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(codec.decode(readUtf8(path)));
        } catch (DockLayoutFormatException | MalformedInputException | RuntimeException e) {
            /*
             * A malformed document must never be silently replaced by defaults at the same path:
             * callers can recover with a fresh layout while the user's original remains available
             * for inspection or manual recovery.
             */
            lastQuarantinedPath = quarantineCorruptFile();
            lastLoadCorrupt = true;
            return Optional.empty();
        }
    }

    /**
     * Java 8 等价实现：{@code Files.readString(path, UTF_8)}。原 API 对非法 UTF-8 抛
     * {@link MalformedInputException}（load 依赖该分支做隔离），因此这里用 REPORT 策略严格解码，
     * 而不是用会替换为 U+FFFD 的宽松解码。
     */
    private static String readUtf8(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (MalformedInputException e) {
            throw e;
        } catch (CharacterCodingException e) {
            throw new MalformedInputException(bytes.length);
        }
    }

    /** Returns the most recent quarantine path produced by {@link #load()}, if any. */
    @Override
    public Optional<Path> lastQuarantinedPath() {
        return Optional.ofNullable(lastQuarantinedPath);
    }

    @Override
    public boolean lastLoadWasCorrupt() {
        return lastLoadCorrupt;
    }

    private Path quarantineCorruptFile() throws IOException {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IOException("layout path has no parent: " + path);
        }
        Files.createDirectories(parent);

        String fileName = path.getFileName().toString();
        Path quarantine;
        do {
            quarantine = parent.resolve(fileName
                    + ".corrupt."
                    + Instant.now().toEpochMilli()
                    + "."
                    + UUID.randomUUID());
        } while (Files.exists(quarantine));

        try {
            try {
                Files.move(path, quarantine, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(path, quarantine);
            }
        } catch (IOException e) {
            // The move is the point at which the original file is removed. If it fails, leave the
            // source untouched and propagate the error so callers do not overwrite it blindly.
            throw new IOException("failed to quarantine corrupt layout " + path, e);
        }
        return quarantine;
    }

    @Override
    public void save(DockWorkspace workspace) throws IOException {
        saveEncoded(codec.encode(workspace));
    }

    /**
     * 写入一段已编码的布局 JSON（复合存储用它避免重复编码）。
     *
     * <p>原子写：先写临时文件并 force，再原子替换目标。</p>
     */
    public void saveEncoded(String json) throws IOException {
        Objects.requireNonNull(json, "json");
        Path parent = path.getParent();
        if (parent == null) {
            throw new IOException("layout path has no parent: " + path);
        }
        Files.createDirectories(parent);

        String fileName = path.getFileName().toString();
        String prefix = fileName.length() >= 3 ? fileName : "layout-";
        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        boolean moved = false;
        try {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
