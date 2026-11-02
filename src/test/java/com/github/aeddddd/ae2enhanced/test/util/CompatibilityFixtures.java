package com.github.aeddddd.ae2enhanced.test.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

/** 小型、确定性的旧格式 classpath fixture 读取工具；资源缺失一律失败。 */
public final class CompatibilityFixtures {

    private CompatibilityFixtures() {
    }

    public static InputStream open(String resource) {
        String path = resource.startsWith("/") ? resource : "/compat/" + resource;
        InputStream stream = CompatibilityFixtures.class.getResourceAsStream(path);
        if (stream == null) {
            throw new UncheckedIOException(new FileNotFoundException("缺少兼容 fixture: " + path));
        }
        return stream;
    }

    public static NBTTagCompound readNbt(String resource) {
        try (InputStream stream = open(resource)) {
            java.io.PushbackInputStream input = new java.io.PushbackInputStream(stream, 2);
            byte[] magic = new byte[2];
            int read = input.read(magic);
            input.unread(magic, 0, Math.max(0, read));
            boolean gzip = read == 2 && (magic[0] & 0xff) == 0x1f && (magic[1] & 0xff) == 0x8b;
            NBTTagCompound tag = gzip
                    ? CompressedStreamTools.readCompressed(input)
                    : CompressedStreamTools.read(new java.io.DataInputStream(input));
            if (tag == null) {
                throw new IOException("空 NBT fixture: " + resource);
            }
            return tag;
        } catch (IOException e) {
            throw new UncheckedIOException("无法读取兼容 fixture: " + resource, e);
        }
    }

    public static Path copy(String resource, Path destination) {
        try (InputStream stream = open(resource)) {
            Path parent = destination.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(stream, destination, StandardCopyOption.REPLACE_EXISTING);
            return destination;
        } catch (IOException e) {
            throw new UncheckedIOException("无法复制兼容 fixture: " + resource + " -> " + destination, e);
        }
    }
}
