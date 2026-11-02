package com.github.aeddddd.ae2enhanced.guide.script;

import com.github.aeddddd.ae2enhanced.guide.GuideBook;
import com.github.aeddddd.ae2enhanced.guide.GuidePage;
import com.github.aeddddd.ae2enhanced.guide.loader.FrontMatterParser;
import com.github.aeddddd.ae2enhanced.guide.md.MarkdownParser;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScriptGuideServiceTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @Test
    void inlinePageTranslationsAndSnapshotRoundTripAreImmutable() throws Exception {
        ScriptGuideRepository repository = new ScriptGuideRepository();
        ScriptGuideService service = ScriptGuideService.create(temporaryDirectory, repository);
        service.addPage("mymod/index.md", "Quick Start", null, 10,
                "minecraft:diamond", new String[]{"minecraft:diamond"}, "# Quick Start\n\n**Body**");
        service.addTranslation("mymod/index.md", "zh_cn", "---\nnavigation:\n  title: 快速开始\n---\n# 快速开始");

        ScriptGuideSnapshot snapshot = repository.snapshot();
        ScriptGuideSnapshot decoded = ScriptGuideSnapshot.decode(snapshot.getRevision(), snapshot.encode());

        assertThat(decoded.getRevision()).isEqualTo(1L + 1L);
        assertThat(decoded.getPages()).hasSize(2);
        assertThat(decoded.getPages().get(0).getLanguage()).isEmpty();
        assertThat(decoded.getPages().get(1).getLanguage()).isEqualTo("zh_cn");
        assertThat(decoded.getPages().get(1).getContent()).contains("快速开始");
        assertThatThrownBy(() -> decoded.getPages().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> service.addTranslation("mymod/missing.md", "zh_cn", "missing"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void externalDirectoryLoadsMarkdownAndLanguageWithoutManifest() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("mymod"));
        Files.createDirectories(temporaryDirectory.resolve("_zh_cn/mymod"));
        Files.write(temporaryDirectory.resolve("mymod/press.md"), Arrays.asList(
                "---", "navigation:", "  title: Press", "  position: 20", "item_ids: [minecraft:diamond]", "---",
                "# Press", "", "Uses **FE**."), StandardCharsets.UTF_8);
        Files.write(temporaryDirectory.resolve("_zh_cn/mymod/press.md"),
                Collections.singletonList("# 压机"), StandardCharsets.UTF_8);
        ScriptGuideRepository repository = new ScriptGuideRepository();
        ScriptGuideService service = ScriptGuideService.create(temporaryDirectory, repository);

        service.loadDirectory("");
        ScriptGuideSnapshot snapshot = repository.snapshot();

        assertThat(snapshot.getPages()).hasSize(2);
        assertThat(snapshot.getPages().get(0).getPageId()).isEqualTo("mymod/press.md");
        assertThat(snapshot.getPages().get(1).getLanguage()).isEqualTo("zh_cn");
        assertThatThrownBy(() -> snapshot.getPages().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unsafePathsAndInvalidUtf8AreRejectedWithoutPartialDirectoryWrite() throws Exception {
        ScriptGuideRepository repository = new ScriptGuideRepository();
        ScriptGuideService service = ScriptGuideService.create(temporaryDirectory, repository);
        Files.createDirectories(temporaryDirectory.resolve("mymod"));
        Files.write(temporaryDirectory.resolve("mymod/good.md"),
                Collections.singletonList("# Good"), StandardCharsets.UTF_8);
        Files.write(temporaryDirectory.resolve("mymod/bad.md"), new byte[]{(byte) 0xc3, (byte) 0x28});

        assertThatThrownBy(() -> service.loadDirectory("mymod")).isInstanceOf(java.io.IOException.class);
        assertThat(repository.snapshot().getPages()).isEmpty();
        assertThatThrownBy(() -> service.load("../secret.md")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.load("/absolute.md")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.addPage("UPPER.md", "Bad", "body"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsedExternalPageBuildsNavigationAnchorAndItemIndex() {
        FrontMatterParser.ParseResult parsed = FrontMatterParser.parse(
                "---\nnavigation:\n  title: Press\n  position: 20\nitem_ids: [minecraft:diamond]\n---\n# Press\n\nBody");
        GuidePage page = new GuidePage("mymod/press.md", parsed.frontMatter, MarkdownParser.parse(parsed.body));
        Map<String, GuidePage> pages = new LinkedHashMap<>();
        pages.put(page.getId(), page);

        GuideBook book = GuideBook.build(pages);

        assertThat(book.getNavRoots()).hasSize(1);
        assertThat(book.getNavRoots().get(0).pageId).isEqualTo("mymod/press.md");
        assertThat(book.getPageForItem(Items.DIAMOND).getPageId()).isEqualTo("mymod/press.md");
        assertThat(GuideBook.toAnchor("Hello World!")).isEqualTo("hello-world");
    }
}
