/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;

/** Three named layout slots plus import/export/backup helpers. */
public final class LayoutPresetBank {
    public static final int SLOT_COUNT = 3;
    public static final int FILE_VERSION = 1;
    public static final String PRESETS_FILE = "presets.json";
    public static final String SHARE_LAYOUT_FILE = "layout.json";
    public static final String SHARE_NAME_FILE = "name.txt";

    public static final class SlotDocument {
        private final DockWorkspace workspace;
        private final String name;

        public SlotDocument(DockWorkspace workspace, String name) {
            this.workspace = workspace;
            this.name = name;
        }

        public DockWorkspace workspace() {
            return workspace;
        }

        public String name() {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            SlotDocument other = (SlotDocument) o;
            return Objects.equals(workspace, other.workspace) && Objects.equals(name, other.name);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + Objects.hashCode(workspace);
            result = 31 * result + Objects.hashCode(name);
            return result;
        }

        @Override
        public String toString() {
            return "SlotDocument[workspace=" + workspace + ", name=" + name + "]";
        }
    }

    public static String encodeSlot(DockWorkspace workspace, String name, DockLayoutCodec codec) {
        JsonObject object = parseJson(codec.encode(workspace)).getAsJsonObject();
        if (name != null && !isBlank(name)) {
            object.addProperty("name", name);
        }
        return GSON.toJson(object);
    }

    public static SlotDocument decodeSlot(String json, DockLayoutCodec codec) throws DockLayoutFormatException {
        DockWorkspace workspace = codec.decode(json).workspace();
        String name = "";
        try {
            JsonElement parsed = parseJson(json);
            if (parsed.isJsonObject() && parsed.getAsJsonObject().has("name")) {
                name = sanitizeName(parsed.getAsJsonObject().get("name").getAsString());
            }
        } catch (RuntimeException ignored) {
            // Workspace already decoded; missing name stays empty.
        }
        return new SlotDocument(workspace, name);
    }

    public static final String SHARE_PRESETS_FILE = "presets.json";
    public static final String SHARE_ZIP_FILE = "mest-layout.zip";

    public static String shareSlotFolder(int index) {
        return String.valueOf(Math.floorMod(index, SLOT_COUNT) + 1);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private LayoutPresetBank() {
    }

    public static final class Data {
        private final int active;
        private final String[] names;
        private final DockWorkspace[] workspaces;

        public Data(int active, String[] names, DockWorkspace[] workspaces) {
            if (names == null || names.length != SLOT_COUNT) {
                throw new IllegalArgumentException("names");
            }
            if (workspaces == null || workspaces.length != SLOT_COUNT) {
                throw new IllegalArgumentException("workspaces");
            }
            active = Math.floorMod(active, SLOT_COUNT);
            names = names.clone();
            workspaces = workspaces.clone();
            this.active = active;
            this.names = names;
            this.workspaces = workspaces;
        }

        public int active() {
            return active;
        }

        public String[] names() {
            return names;
        }

        public DockWorkspace[] workspaces() {
            return workspaces;
        }

        public Data copy() {
            return new Data(active, names, workspaces);
        }

        public Data withActive(int index) {
            return new Data(index, names, workspaces);
        }

        public Data withName(int index, String name) {
            String[] next = names.clone();
            next[Math.floorMod(index, SLOT_COUNT)] = name;
            return new Data(active, next, workspaces);
        }

        public Data withWorkspace(int index, DockWorkspace workspace) {
            DockWorkspace[] next = workspaces.clone();
            next[Math.floorMod(index, SLOT_COUNT)] = Objects.requireNonNull(workspace, "workspace");
            return new Data(active, names, next);
        }

        // record 语义：数组分量按 Objects.equals / Objects.hashCode 处理，即引用/身份语义
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Data other = (Data) o;
            return active == other.active
                    && Objects.equals(names, other.names)
                    && Objects.equals(workspaces, other.workspaces);
        }

        @Override
        public int hashCode() {
            // record 语义：hashCode 从 0 起累加（31 * result + componentHash）
            int result = 0;
            result = 31 * result + Integer.hashCode(active);
            result = 31 * result + Objects.hashCode(names);
            result = 31 * result + Objects.hashCode(workspaces);
            return result;
        }

        @Override
        public String toString() {
            return "Data[active=" + active + ", names=" + names + ", workspaces=" + workspaces + "]";
        }
    }

    public static Data create(
            DockWorkspace current,
            DockWorkspace filler,
            String[] defaultNames) {
        String[] names = new String[SLOT_COUNT];
        DockWorkspace[] workspaces = new DockWorkspace[SLOT_COUNT];
        for (int index = 0; index < SLOT_COUNT; index++) {
            names[index] = defaultNames != null && index < defaultNames.length
                    ? sanitizeName(defaultNames[index])
                    : "";
            workspaces[index] = index == 0 ? current : filler;
        }
        return new Data(0, names, workspaces);
    }

    public static Data load(Path file, DockLayoutCodec codec, Data fallback) {
        if (file == null || !Files.isRegularFile(file)) {
            return fallback;
        }
        try {
            Data loaded = read(readUtf8(file), codec, fallback);
            return loaded == null ? fallback : loaded;
        } catch (IOException | RuntimeException e) {
            return fallback;
        }
    }

    public static Data read(String json, DockLayoutCodec codec, Data fallback) throws DockLayoutFormatException {
        if (json == null || isBlank(json)) {
            throw new DockLayoutFormatException("preset document is empty");
        }
        JsonElement parsed;
        try {
            parsed = parseJson(json);
        } catch (RuntimeException e) {
            throw new DockLayoutFormatException("invalid layout JSON", e);
        }
        if (!parsed.isJsonObject()) {
            throw new DockLayoutFormatException("preset document must be a JSON object");
        }
        JsonObject object = parsed.getAsJsonObject();
        if (object.has("presets")) {
            return readBank(object, codec, fallback);
        }
        if (object.has("roots")) {
            DockWorkspace workspace = codec.decode(json).workspace();
            Data data = fallback.copy();
            return data.withWorkspace(data.active(), workspace);
        }
        throw new DockLayoutFormatException("unrecognized layout document");
    }

    public static void save(Path file, Data data, DockLayoutCodec codec) throws IOException {
        writeAtomic(file, GSON.toJson(writeBank(data, codec)));
    }

    public static void zipShareSlots(Path shareDir, Path zipFile) throws IOException {
        Path parent = zipFile.getParent();
        if (parent == null) {
            throw new IOException("zip path has no parent: " + zipFile);
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "mest-layout-", ".tmp.zip");
        boolean moved = false;
        try {
            try (ZipOutputStream zip = new ZipOutputStream(
                    Files.newOutputStream(temporary), StandardCharsets.UTF_8)) {
                for (int index = 0; index < SLOT_COUNT; index++) {
                    Path slotDir = shareDir.resolve(shareSlotFolder(index));
                    addZipFile(zip, slotDir.resolve(SHARE_LAYOUT_FILE), shareSlotFolder(index) + "/" + SHARE_LAYOUT_FILE);
                }
            }
            try {
                Files.move(temporary, zipFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, zipFile, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static Data readShareZip(Path zipFile, DockLayoutCodec codec, Data fallback)
            throws IOException, DockLayoutFormatException {
        Data data = fallback.copy();
        boolean any = false;
        try (ZipInputStream zip = new ZipInputStream(
                Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = normalizeZipEntry(entry.getName());
                if (name == null) {
                    continue;
                }
                int slash = name.indexOf('/');
                if (slash <= 0) {
                    continue;
                }
                int slot;
                try {
                    slot = Integer.parseInt(name.substring(0, slash)) - 1;
                } catch (NumberFormatException e) {
                    continue;
                }
                if (slot < 0 || slot >= SLOT_COUNT) {
                    continue;
                }
                String file = name.substring(slash + 1);
                byte[] bytes = readAllBytes(zip);
                if (SHARE_LAYOUT_FILE.equals(file)) {
                    SlotDocument document = decodeSlot(new String(bytes, StandardCharsets.UTF_8), codec);
                    data = data.withWorkspace(slot, document.workspace());
                    if (!isBlank(document.name())) {
                        data = data.withName(slot, document.name());
                    }
                    any = true;
                } else if (SHARE_NAME_FILE.equals(file)) {
                    data = data.withName(slot, sanitizeName(new String(bytes, StandardCharsets.UTF_8)));
                }
            }
        }
        if (!any) {
            throw new DockLayoutFormatException("zip does not contain preset layouts");
        }
        return data;
    }

    private static void addZipFile(ZipOutputStream zip, Path file, String entryName) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        zip.putNextEntry(new ZipEntry(entryName));
        Files.copy(file, zip);
        zip.closeEntry();
    }

    private static String normalizeZipEntry(String raw) {
        if (raw == null || isBlank(raw)) {
            return null;
        }
        String name = raw.replace('\\', '/');
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (name.startsWith("share/")) {
            name = name.substring("share/".length());
        }
        if (name.contains("..")) {
            return null;
        }
        return name;
    }

    public static void writeAtomic(Path file, String json) throws IOException {
        Path parent = file.getParent();
        if (parent == null) {
            throw new IOException("path has no parent: " + file);
        }
        Files.createDirectories(parent);
        String fileName = file.getFileName().toString();
        String prefix = fileName.length() >= 3 ? fileName : "layout-";
        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, json.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    public static void clearDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path child : stream) {
                if (Files.isRegularFile(child)) {
                    Files.deleteIfExists(child);
                }
            }
        }
    }

    public static String defaultName(String[] defaults, int index) {
        if (defaults != null && index >= 0 && index < defaults.length && defaults[index] != null
                && !isBlank(defaults[index])) {
            return defaults[index];
        }
        return String.valueOf(index + 1);
    }

    public static String sanitizeName(String name, String fallback) {
        String trimmed = sanitizeName(name);
        return trimmed.isEmpty() ? (fallback == null ? "" : fallback) : trimmed;
    }

    public static String sanitizeName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = strip(name.replaceAll("\\p{Cntrl}", ""));
        if (trimmed.length() > 24) {
            trimmed = strip(trimmed.substring(0, 24));
        }
        return trimmed;
    }

    private static Data readBank(JsonObject object, DockLayoutCodec codec, Data fallback)
            throws DockLayoutFormatException {
        int active = object.has("active") ? object.get("active").getAsInt() : 0;
        JsonArray presets = object.getAsJsonArray("presets");
        if (presets == null) {
            throw new DockLayoutFormatException("presets must be an array");
        }
        String[] names = Arrays.copyOf(fallback.names(), SLOT_COUNT);
        DockWorkspace[] workspaces = Arrays.copyOf(fallback.workspaces(), SLOT_COUNT);
        int count = Math.min(SLOT_COUNT, presets.size());
        for (int index = 0; index < count; index++) {
            JsonElement element = presets.get(index);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject preset = element.getAsJsonObject();
            if (preset.has("name")) {
                names[index] = sanitizeName(preset.get("name").getAsString(), names[index]);
            }
            if (preset.has("layout") && preset.get("layout").isJsonObject()) {
                workspaces[index] = codec.decode(preset.get("layout").toString()).workspace();
            }
        }
        return new Data(active, names, workspaces);
    }

    private static JsonObject writeBank(Data data, DockLayoutCodec codec) {
        JsonObject object = new JsonObject();
        object.addProperty("version", FILE_VERSION);
        object.addProperty("active", data.active());
        JsonArray presets = new JsonArray();
        for (int index = 0; index < SLOT_COUNT; index++) {
            JsonObject preset = new JsonObject();
            preset.addProperty("name", data.names()[index]);
            preset.add("layout", parseJson(codec.encode(data.workspaces()[index])).getAsJsonObject());
            presets.add(preset);
        }
        object.add("presets", presets);
        return object;
    }

    /**
     * Java 8 / Gson 2.8 兼容：{@code JsonParser.parseString(json)} 自 Gson 2.8.6 才提供；
     * 1.12.2 运行时自带 Gson 2.8.0，因此改用等价的实例 API（对全部输入行为一致）。
     */
    private static JsonElement parseJson(String json) {
        return new JsonParser().parse(json);
    }

    /** Java 8 等价实现：{@code String.isBlank()}，按 Unicode 码点判定空白。 */
    private static boolean isBlank(String value) {
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            if (!Character.isWhitespace(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }

    /** Java 8 等价实现：{@code String.strip()}，按 Unicode 码点去除首尾空白。 */
    private static String strip(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    /** Java 8 等价实现：{@code InputStream.readAllBytes()}。 */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(32, in.available()));
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    /**
     * Java 8 等价实现：{@code Files.readString(path, UTF_8)}（严格 UTF-8 解码，
     * 与 {@link AtomicFileDockLayoutStore} 中的实现一致）。
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
}
