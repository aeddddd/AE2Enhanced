package com.github.aeddddd.ae2enhanced.guide.loader;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.guide.GuidePage;
import com.github.aeddddd.ae2enhanced.guide.md.MarkdownParser;
import com.github.aeddddd.ae2enhanced.guide.md.element.BlockElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 指南页面加载器 —— 从资源目录读取 Markdown 页面.
 * <p>
 * 内容根目录：{@code assets/ae2enhanced/guides/ae2enhanced/guide/}。
 * 1.12.2 资源系统无法枚举目录，故用 {@code pages.txt} 清单列出所有基础页（每行一个页面 id）。
 * 翻译页按 GuideME 的 {@code _<lang>/} 约定存放，与基础页同路径同名，当前语言存在时替换基础页。
 */
@SideOnly(Side.CLIENT)
public final class GuideLoader {

    private static final String ROOT = "guides/ae2enhanced/guide/";
    private static final String MANIFEST = ROOT + "pages.txt";

    private GuideLoader() {}

    /**
     * 加载全部页面（含翻译替换），返回 pageId → GuidePage.
     */
    public static Map<String, GuidePage> loadPages() {
        Map<String, GuidePage> pages = new LinkedHashMap<>();
        String currentLang = getCurrentLanguage();

        for (String pageId : readManifest()) {
            String content = readPageContent(pageId, currentLang);
            if (content == null) {
                AE2Enhanced.LOGGER.warn("[AE2E] Guide page '{}' listed in pages.txt but not found", pageId);
                continue;
            }
            try {
                pages.put(pageId, parsePage(pageId, content));
            } catch (Exception e) {
                // 解析失败：降级为错误占位页，不影响其他页面（对齐 GuideME buildErrorPage）
                AE2Enhanced.LOGGER.error("[AE2E] Failed to parse guide page '{}'", pageId, e);
                pages.put(pageId, buildErrorPage(pageId, e));
            }
        }
        return pages;
    }

    public static GuidePage parsePage(String pageId, String content) {
        FrontMatterParser.ParseResult result = FrontMatterParser.parse(content);
        List<BlockElement> blocks = MarkdownParser.parse(result.body);
        return new GuidePage(pageId, result.frontMatter, blocks);
    }

    /**
     * 读取页面内容：当前语言非 en_us 时优先 {@code _<lang>/} 下的翻译页.
     */
    private static String readPageContent(String pageId, String currentLang) {
        if (!"en_us".equals(currentLang)) {
            String translated = readResource(ROOT + "_" + currentLang + "/" + pageId);
            if (translated != null) {
                return translated;
            }
        }
        return readResource(ROOT + pageId);
    }

    private static List<String> readManifest() {
        List<String> pageIds = new ArrayList<>();
        String manifest = readResource(MANIFEST);
        if (manifest == null) {
            AE2Enhanced.LOGGER.error("[AE2E] Guide manifest '{}' not found, guide will be empty", MANIFEST);
            return pageIds;
        }
        for (String line : manifest.split("\n")) {
            String id = line.trim();
            if (!id.isEmpty() && !id.startsWith("#")) {
                pageIds.add(id);
            }
        }
        return pageIds;
    }

    private static String readResource(String path) {
        ResourceLocation rl = new ResourceLocation(AE2Enhanced.MOD_ID, path);
        try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(rl);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 当前语言代码（小写，对齐 GuideME LangUtil）.
     */
    public static String getCurrentLanguage() {
        try {
            String code = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
            return code.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "en_us";
        }
    }

    private static GuidePage buildErrorPage(String pageId, Exception e) {
        List<BlockElement> blocks = new ArrayList<>();
        BlockElement heading = new BlockElement(BlockElement.Type.HEADING, 1);
        heading.getChildren().add(com.github.aeddddd.ae2enhanced.guide.md.element.InlineElement.text("PARSING ERROR", false));
        blocks.add(heading);
        BlockElement paragraph = new BlockElement(BlockElement.Type.PARAGRAPH, 0);
        paragraph.getChildren().add(com.github.aeddddd.ae2enhanced.guide.md.element.InlineElement.text(
                pageId + ": " + e, false));
        blocks.add(paragraph);
        return new GuidePage(pageId, new FrontMatterParser.FrontMatter(), blocks);
    }
}
