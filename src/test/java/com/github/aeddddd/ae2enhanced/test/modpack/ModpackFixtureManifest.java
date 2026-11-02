package com.github.aeddddd.ae2enhanced.test.modpack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;

/** 可入版本库的整合包回放清单；所有缺失与格式错误均直接失败。 */
final class ModpackFixtureManifest {

    static final String FIXTURE_PROPERTY = "ae2e.modpack.fixture";
    static final String MANIFEST_FILE = "manifest.tsv";
    private static final String FORMAT_VERSION = "1";

    final File directory;
    final File snapshotFile;
    final String source;
    final String sourceVersion;
    final List<Request> requests;

    private ModpackFixtureManifest(File directory, File snapshotFile, String source,
            String sourceVersion, List<Request> requests) {
        this.directory = directory;
        this.snapshotFile = snapshotFile;
        this.source = source;
        this.sourceVersion = sourceVersion;
        this.requests = requests;
    }

    static Loaded loadFromProperty() throws IOException {
        String path = System.getProperty(FIXTURE_PROPERTY);
        if (path == null || path.trim().isEmpty()) {
            throw new IOException("缺少 -D" + FIXTURE_PROPERTY + "=<fixture目录>");
        }
        return load(new File(path));
    }

    static Loaded load(File fixtureDirectory) throws IOException {
        File directory = fixtureDirectory.getCanonicalFile();
        if (!directory.isDirectory()) {
            throw new IOException("fixture 目录不存在: " + directory);
        }
        File manifestFile = new File(directory, MANIFEST_FILE);
        if (!manifestFile.isFile()) {
            throw new IOException("fixture 缺少清单: " + manifestFile);
        }

        Map<String, String> properties = new LinkedHashMap<>();
        Map<String, String> mods = new LinkedHashMap<>();
        List<Request> requests = new ArrayList<>();
        int lineNumber = 0;
        for (String line : Files.readAllLines(manifestFile.toPath(), StandardCharsets.UTF_8)) {
            lineNumber++;
            if (line.trim().isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("\\t", -1);
            String kind = columns[0];
            if ("request".equals(kind)) {
                if (columns.length != 9) {
                    throw formatError(manifestFile, lineNumber, "request 必须有 8 个字段");
                }
                requests.add(parseRequest(manifestFile, lineNumber, columns));
            } else if ("mod".equals(kind)) {
                if (columns.length != 3 || columns[1].isEmpty() || columns[2].isEmpty()) {
                    throw formatError(manifestFile, lineNumber, "mod 必须为 modid 和版本");
                }
                if (mods.put(columns[1], columns[2]) != null) {
                    throw formatError(manifestFile, lineNumber, "重复 modid: " + columns[1]);
                }
            } else {
                if (columns.length != 2 || columns[1].isEmpty()) {
                    throw formatError(manifestFile, lineNumber, "属性必须为 key<TAB>value");
                }
                if (!"format".equals(kind) && !"snapshot".equals(kind) && !"sha256".equals(kind)
                        && !"snapshotFormat".equals(kind) && !"source".equals(kind)
                        && !"sourceVersion".equals(kind)) {
                    throw formatError(manifestFile, lineNumber, "未知记录: " + kind);
                }
                if (properties.put(kind, columns[1]) != null) {
                    throw formatError(manifestFile, lineNumber, "重复属性: " + kind);
                }
            }
        }

        requireProperty(properties, "format");
        if (!FORMAT_VERSION.equals(properties.get("format"))) {
            throw new IOException("fixture 格式不符: 期望 " + FORMAT_VERSION + ", 实际 " + properties.get("format"));
        }
        String snapshotName = requireProperty(properties, "snapshot");
        String expectedHash = requireProperty(properties, "sha256").toLowerCase(java.util.Locale.ROOT);
        if (!expectedHash.matches("[0-9a-f]{64}")) {
            throw new IOException("manifest sha256 必须是 64 位十六进制");
        }
        int snapshotFormat = parseInt(requireProperty(properties, "snapshotFormat"), "snapshotFormat");
        String source = requireProperty(properties, "source");
        String sourceVersion = requireProperty(properties, "sourceVersion");
        if (mods.isEmpty()) {
            throw new IOException("manifest 至少需要一个 mod 版本记录");
        }
        if (requests.isEmpty()) {
            throw new IOException("manifest 至少需要一个 request");
        }

        File relativeSnapshot = new File(snapshotName);
        if (relativeSnapshot.isAbsolute()) {
            throw new IOException("snapshot 必须是 fixture 内的相对路径: " + snapshotName);
        }
        File snapshotFile = new File(directory, snapshotName).getCanonicalFile();
        String directoryPrefix = directory.getPath() + File.separator;
        if (!snapshotFile.getPath().startsWith(directoryPrefix)) {
            throw new IOException("snapshot 不得越出 fixture 目录: " + snapshotName);
        }
        if (!snapshotFile.isFile()) {
            throw new IOException("fixture 缺少快照: " + snapshotFile);
        }
        String actualHash = sha256(snapshotFile);
        if (!expectedHash.equals(actualHash)) {
            throw new IOException("快照 SHA256 不符: 期望 " + expectedHash + ", 实际 " + actualHash);
        }

        ModpackFixture.Loaded loaded = ModpackFixture.load(snapshotFile);
        if (loaded.snapshot == null || loaded.snapshot.formatVersion != snapshotFormat) {
            throw new IOException("快照格式不符: 期望 " + snapshotFormat + ", 实际 "
                    + (loaded.snapshot == null ? "null" : loaded.snapshot.formatVersion));
        }
        if (!mods.equals(loaded.snapshot.mods)) {
            throw new IOException("快照 mod 版本不符: 期望 " + mods + ", 实际 " + loaded.snapshot.mods);
        }
        ModpackFixtureManifest manifest = new ModpackFixtureManifest(directory, snapshotFile, source,
                sourceVersion, Collections.unmodifiableList(requests));
        return new Loaded(manifest, loaded);
    }

    private static Request parseRequest(File manifest, int line, String[] c) throws IOException {
        if (!c[1].matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw formatError(manifest, line, "非法物品注册名: " + c[1]);
        }
        long count = parsePositiveLong(c[2], "request count");
        boolean simulation = parseBoolean(c[3], "simulation");
        int missing = parseNonNegativeInt(c[4], "missing");
        long fallback = parseNonNegativeLong(c[5], "fallback");
        long degraded = parseNonNegativeLong(c[6], "degraded");
        boolean timeout = parseBoolean(c[7], "timeout");
        long timeoutMs = parsePositiveLong(c[8], "timeoutMs");
        return new Request(c[1], count, simulation, missing, fallback, degraded, timeout, timeoutMs);
    }

    private static String requireProperty(Map<String, String> properties, String key) throws IOException {
        String value = properties.get(key);
        if (value == null) {
            throw new IOException("manifest 缺少属性: " + key);
        }
        return value;
    }

    private static IOException formatError(File manifest, int line, String message) {
        return new IOException(manifest.getName() + ":" + line + " " + message);
    }

    private static boolean parseBoolean(String value, String name) throws IOException {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IOException(name + " 必须为 true/false: " + value);
    }

    private static int parseInt(String value, String name) throws IOException {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IOException(name + " 不是整数: " + value, e);
        }
    }

    private static int parseNonNegativeInt(String value, String name) throws IOException {
        int parsed = parseInt(value, name);
        if (parsed < 0) {
            throw new IOException(name + " 不得为负数: " + value);
        }
        return parsed;
    }

    private static long parseNonNegativeLong(String value, String name) throws IOException {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0) {
                throw new IOException(name + " 不得为负数: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IOException(name + " 不是整数: " + value, e);
        }
    }

    private static long parsePositiveLong(String value, String name) throws IOException {
        long parsed = parseNonNegativeLong(value, name);
        if (parsed == 0) {
            throw new IOException(name + " 必须大于 0");
        }
        return parsed;
    }

    private static String sha256(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buffer = new byte[8192];
        try (InputStream input = Files.newInputStream(file.toPath())) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder result = new StringBuilder(64);
        for (byte b : digest.digest()) {
            result.append(String.format(java.util.Locale.ROOT, "%02x", b & 0xff));
        }
        return result.toString();
    }

    static final class Loaded {
        final ModpackFixtureManifest manifest;
        final ModpackFixture.Loaded fixture;

        Loaded(ModpackFixtureManifest manifest, ModpackFixture.Loaded fixture) {
            this.manifest = manifest;
            this.fixture = fixture;
        }
    }

    static final class Request {
        final String item;
        final long count;
        final boolean simulation;
        final int missing;
        final long fallback;
        final long degraded;
        final boolean timeout;
        final long timeoutMs;

        Request(String item, long count, boolean simulation, int missing, long fallback,
                long degraded, boolean timeout, long timeoutMs) {
            this.item = item;
            this.count = count;
            this.simulation = simulation;
            this.missing = missing;
            this.fallback = fallback;
            this.degraded = degraded;
            this.timeout = timeout;
            this.timeoutMs = timeoutMs;
        }
    }
}
