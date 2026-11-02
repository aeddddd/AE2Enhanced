package com.github.aeddddd.ae2enhanced.guide.script;

import com.github.aeddddd.ae2enhanced.guide.loader.FrontMatterParser;
import net.minecraftforge.fml.common.Loader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** 服务端受控 Markdown 加载入口；不允许 ZenScript 直接进行任意文件 IO。 */
public final class ScriptGuideService {
    private static final Pattern PAGE_ID = Pattern.compile("[a-z0-9_\\-./]+\\.md");
    private static final Pattern LANGUAGE = Pattern.compile("[a-z0-9_]{1,16}");
    private static final ScriptGuideRepository SERVER_REPOSITORY = new ScriptGuideRepository();

    private final Path root;
    private final ScriptGuideRepository repository;

    private ScriptGuideService(Path root, ScriptGuideRepository repository) {
        this.root = root;
        this.repository = repository;
    }

    public static ScriptGuideService server() {
        return new ScriptGuideService(Loader.instance().getConfigDir().toPath()
                .resolve("ae2enhanced").resolve("guides"), SERVER_REPOSITORY);
    }

    public static ScriptGuideService create(Path root, ScriptGuideRepository repository) {
        return new ScriptGuideService(root, repository);
    }

    public static ScriptGuideRepository serverRepository() {
        return SERVER_REPOSITORY;
    }

    private static String frontMatter(String title, String parent, int position,
            String icon, String[] itemIds) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Guide navigation title is empty");
        }
        requireSingleLine(title, "title");
        requireSingleLine(icon, "icon");
        if (parent != null && !parent.isEmpty()) {
            validatePageId(parent);
        }
        if (itemIds != null && itemIds.length > 64) {
            throw new IllegalArgumentException("Guide page has more than 64 item_ids");
        }
        StringBuilder result = new StringBuilder("---\nnavigation:\n  title: ").append(title.trim())
                .append("\n  position: ").append(position).append('\n');
        if (parent != null && !parent.isEmpty()) {
            result.append("  parent: ").append(parent).append('\n');
        }
        if (icon != null && !icon.trim().isEmpty()) {
            result.append("  icon: ").append(icon.trim()).append('\n');
        }
        if (itemIds != null && itemIds.length > 0) {
            result.append("item_ids: [");
            for (int i = 0; i < itemIds.length; i++) {
                if (itemIds[i] == null || itemIds[i].trim().isEmpty()) {
                    throw new IllegalArgumentException("Guide item_ids contains an empty item");
                }
                requireSingleLine(itemIds[i], "item_ids");
                if (i > 0) {
                    result.append(", ");
                }
                result.append(itemIds[i].trim());
            }
            result.append("]\n");
        }
        return result.append("---\n").toString();
    }

    public static String normalizeRelative(String path, boolean markdown) {
        if (path == null) {
            throw new IllegalArgumentException("Guide path is null");
        }
        String normalized = path.trim().replace('\\', '/');
        if (normalized.isEmpty() || normalized.startsWith("/") || normalized.contains("..")
                || normalized.contains("//") || normalized.startsWith("./")) {
            throw new IllegalArgumentException("Unsafe guide path: " + path);
        }
        if (markdown) {
            validatePageId(normalized);
        } else if (!normalized.matches("[a-z0-9_\\-./]+")) {
            throw new IllegalArgumentException("Invalid guide directory: " + path);
        }
        return normalized;
    }

    public static void validatePageId(String pageId) {
        if (pageId == null || !PAGE_ID.matcher(pageId).matches() || pageId.startsWith("/")
                || pageId.contains("..") || pageId.contains("//")) {
            throw new IllegalArgumentException("Invalid guide page id: " + pageId);
        }
    }

    public static void validateLanguage(String language, boolean allowDefault) {
        if (allowDefault && language != null && language.isEmpty()) {
            return;
        }
        if (language == null || !LANGUAGE.matcher(language).matches()) {
            throw new IllegalArgumentException("Invalid guide language: " + language);
        }
    }

    public static String decodeUtf8(byte[] bytes) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new IOException("Guide Markdown is not valid UTF-8", exception);
        }
    }

    private static String normalizeLanguage(String language) {
        return language.toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void requireSingleLine(String value, String field) {
        if (value != null && (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)) {
            throw new IllegalArgumentException("Guide " + field + " must be a single line");
        }
    }

    public void load(String path) throws IOException {
        repository.put(readEntry(path));
    }

    public void validateLoad(String path) throws IOException {
        ScriptGuidePage page = readEntry(path);
        repository.validateAdditions(java.util.Collections.singletonList(page));
    }

    public void loadDirectory(String path) throws IOException {
        repository.putAll(readDirectory(path));
    }

    public void validateDirectory(String path) throws IOException {
        repository.validateAdditions(readDirectory(path));
    }

    public void addPage(String pageId, String title, String body) {
        addPage(pageId, title, null, 0, null, new String[0], body);
    }

    public void addPage(String pageId, String title, String parent, int position,
            String icon, String[] itemIds, String body) {
        repository.put(new ScriptGuidePage(pageId, "", frontMatter(title, parent, position, icon, itemIds)
                + nullToEmpty(body)));
    }

    public void validateAddPage(String pageId, String title, String parent, int position,
            String icon, String[] itemIds, String body) {
        ScriptGuidePage page = new ScriptGuidePage(pageId, "", frontMatter(title, parent, position, icon, itemIds)
                + nullToEmpty(body));
        repository.validateAdditions(java.util.Collections.singletonList(page));
    }

    public void addTranslation(String pageId, String language, String content) {
        validatePageId(pageId);
        validateLanguage(language, false);
        if (!repository.contains(pageId)) {
            throw new IllegalArgumentException("Cannot translate missing guide page " + pageId);
        }
        repository.put(new ScriptGuidePage(pageId, normalizeLanguage(language), nullToEmpty(content)));
    }

    public void validateTranslation(String pageId, String language, String content) {
        validatePageId(pageId);
        validateLanguage(language, false);
        if (!repository.contains(pageId)) {
            throw new IllegalArgumentException("Cannot translate missing guide page " + pageId);
        }
        repository.validateAdditions(java.util.Collections.singletonList(
                new ScriptGuidePage(pageId, normalizeLanguage(language), nullToEmpty(content))));
    }

    public void removePage(String pageId) {
        validatePageId(pageId);
        repository.remove(pageId);
    }

    private List<ScriptGuidePage> readDirectory(String directory) throws IOException {
        Path rootReal = prepareRoot();
        Path resolved = resolveExistingDirectory(directory, rootReal);
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(resolved)) {
            stream.filter(Files::isRegularFile).forEach(files::add);
        }
        files.sort(Comparator.comparing(path -> rootReal.relativize(path).toString().replace('\\', '/')));
        List<ScriptGuidePage> pages = new ArrayList<>();
        for (Path file : files) {
            String relative = rootReal.relativize(file).toString().replace('\\', '/');
            if (relative.endsWith(".md")) {
                pages.add(readEntry(relative));
            }
        }
        return pages;
    }

    private ScriptGuidePage readEntry(String path) throws IOException {
        String normalized = normalizeRelative(path, true);
        Path rootReal = prepareRoot();
        Path file = rootReal.resolve(normalized).normalize();
        if (!file.startsWith(rootReal) || !Files.isRegularFile(file)) {
            throw new IOException("Guide Markdown does not exist: " + normalized);
        }
        Path real = file.toRealPath();
        if (!real.startsWith(rootReal)) {
            throw new IOException("Guide Markdown symbolic link escapes configured root");
        }
        if (Files.size(real) > ScriptGuideRepository.MAX_PAGE_BYTES) {
            throw new IOException("Guide Markdown exceeds 64 KiB: " + normalized);
        }
        String content = decodeUtf8(Files.readAllBytes(real));
        String pageId = normalized;
        String language = "";
        int slash = normalized.indexOf('/');
        String first = slash < 0 ? normalized : normalized.substring(0, slash);
        if (slash > 0 && first.startsWith("_") && first.length() > 1) {
            language = first.substring(1).toLowerCase(Locale.ROOT);
            validateLanguage(language, false);
            pageId = normalized.substring(slash + 1);
            validatePageId(pageId);
        }
        FrontMatterParser.ParseResult parsed = FrontMatterParser.parse(content);
        if (parsed.frontMatter.itemIds.size() > 64) {
            throw new IOException("Guide page has more than 64 item_ids: " + pageId);
        }
        return new ScriptGuidePage(pageId, language, content);
    }

    private Path prepareRoot() throws IOException {
        Files.createDirectories(root);
        return root.toRealPath();
    }

    private Path resolveExistingDirectory(String directory, Path rootReal) throws IOException {
        String normalized = directory == null || directory.trim().isEmpty() ? "" : normalizeRelative(directory, false);
        Path resolved = rootReal.resolve(normalized).normalize();
        if (!resolved.startsWith(rootReal) || !Files.isDirectory(resolved)) {
            throw new IOException("Guide directory does not exist: " + directory);
        }
        Path real = resolved.toRealPath();
        if (!real.startsWith(rootReal)) {
            throw new IOException("Guide directory symbolic link escapes configured root");
        }
        return real;
    }
}
