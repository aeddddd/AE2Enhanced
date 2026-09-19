package com.github.aeddddd.ae2enhanced.guide;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 指南数据中枢 —— 页面表 + 导航树 + 物品索引.
 */
public final class GuideBook {

    public static final String START_PAGE = "index.md";

    private final Map<String, GuidePage> pages;
    private final List<NavNode> navRoots;
    private final Map<Item, PageAnchor> itemIndex;
    private GuideBook(Map<String, GuidePage> pages, List<NavNode> navRoots,
                      Map<Item, PageAnchor> itemIndex) {
        this.pages = pages;
        this.navRoots = navRoots;
        this.itemIndex = itemIndex;
    }

    public GuidePage getPage(String pageId) {
        return pages.get(pageId);
    }

    public boolean hasPage(String pageId) {
        return pages.containsKey(pageId);
    }

    public List<NavNode> getNavRoots() {
        return navRoots;
    }

    /**
     * 物品 → 关联页面（按住 G 打开用）.
     */
    public PageAnchor getPageForItem(Item item) {
        return itemIndex.get(item);
    }

    /**
     * 导航树节点.
     */
    public static final class NavNode {
        public final String pageId;
        public final String title;
        public final ItemStack icon;      // 可为空（EMPTY）
        public final int position;
        public final List<NavNode> children = new ArrayList<>();

        NavNode(String pageId, String title, ItemStack icon, int position) {
            this.pageId = pageId;
            this.title = title;
            this.icon = icon;
            this.position = position;
        }
    }

    /**
     * 从解析后的页面构建 GuideBook.
     */
    public static GuideBook build(Map<String, GuidePage> pages) {
        // 导航树
        Map<String, NavNode> nodes = new LinkedHashMap<>();
        for (GuidePage page : pages.values()) {
            if (!page.getFrontMatter().hasNavigation()) {
                continue;
            }
            ItemStack icon = resolveIcon(page.getFrontMatter().icon, page.getId());
            nodes.put(page.getId(), new NavNode(page.getId(), page.getTitle(), icon,
                    page.getFrontMatter().position));
        }
        List<NavNode> roots = new ArrayList<>();
        for (Map.Entry<String, NavNode> entry : nodes.entrySet()) {
            String parent = pages.get(entry.getKey()).getFrontMatter().parent;
            NavNode parentNode = parent != null ? nodes.get(parent) : null;
            if (parentNode != null && parentNode != entry.getValue()) {
                parentNode.children.add(entry.getValue());
            } else {
                roots.add(entry.getValue());
            }
        }
        Comparator<NavNode> order = Comparator.comparingInt((NavNode n) -> n.position)
                .thenComparing(n -> n.title);
        roots.sort(order);
        for (NavNode node : nodes.values()) {
            node.children.sort(order);
        }

        // 物品索引（item_ids）
        Map<Item, PageAnchor> itemIndex = new HashMap<>();
        for (GuidePage page : pages.values()) {
            for (String itemId : page.getFrontMatter().itemIds) {
                ResourceLocation rl = itemId.contains(":")
                        ? new ResourceLocation(itemId)
                        : new ResourceLocation(AE2Enhanced.MOD_ID, itemId);
                Item item = Item.REGISTRY.getObject(rl);
                if (item == null) {
                    AE2Enhanced.LOGGER.warn("[AE2E] Guide page {} references unknown item '{}' in item_ids",
                            page.getId(), itemId);
                    continue;
                }
                itemIndex.put(item, new PageAnchor(page.getId(), null));
            }
        }

        return new GuideBook(pages, roots, itemIndex);
    }

    /**
     * 标题文本 → 页内锚点（对齐 GuideME AnchorIndexer：小写、空白/符号转 -）.
     */
    public static String toAnchor(String headingText) {
        StringBuilder sb = new StringBuilder();
        boolean lastDash = false;
        for (char c : headingText.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                lastDash = false;
            } else if (!lastDash && sb.length() > 0) {
                sb.append('-');
                lastDash = true;
            }
        }
        // 去掉尾部 '-'
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == '-') {
            sb.setLength(len - 1);
        }
        return sb.toString();
    }

    private static ItemStack resolveIcon(String iconId, String pageId) {
        if (iconId == null || iconId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation rl = iconId.contains(":")
                ? new ResourceLocation(iconId)
                : new ResourceLocation(AE2Enhanced.MOD_ID, iconId);
        Item item = Item.REGISTRY.getObject(rl);
        if (item == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] Guide page {} references unknown icon item '{}'", pageId, iconId);
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }
}
