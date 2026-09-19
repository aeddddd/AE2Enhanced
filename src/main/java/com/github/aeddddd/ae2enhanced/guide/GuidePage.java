package com.github.aeddddd.ae2enhanced.guide;

import com.github.aeddddd.ae2enhanced.guide.loader.FrontMatterParser;
import com.github.aeddddd.ae2enhanced.guide.md.element.BlockElement;

import java.util.List;

/**
 * 指南页面 —— 解析后的数据模型.
 */
public final class GuidePage {

    private final String id;
    private final FrontMatterParser.FrontMatter frontMatter;
    private final List<BlockElement> blocks;
    private final String title;           // 导航标题（无 front-matter 时用首个一级标题，再没有则用 id）

    public GuidePage(String id, FrontMatterParser.FrontMatter frontMatter, List<BlockElement> blocks) {
        this.id = id;
        this.frontMatter = frontMatter;
        this.blocks = blocks;
        this.title = resolveTitle(id, frontMatter, blocks);
    }

    private static String resolveTitle(String id, FrontMatterParser.FrontMatter fm, List<BlockElement> blocks) {
        if (fm.hasNavigation()) {
            return fm.title;
        }
        for (BlockElement block : blocks) {
            if (block.getType() == BlockElement.Type.HEADING && block.getHeadingDepth() == 1) {
                String text = block.getPlainText().trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return id;
    }

    public String getId() {
        return id;
    }

    public FrontMatterParser.FrontMatter getFrontMatter() {
        return frontMatter;
    }

    public List<BlockElement> getBlocks() {
        return blocks;
    }

    public String getTitle() {
        return title;
    }
}
