package com.github.aeddddd.ae2enhanced.guide.client;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.GuiButtonTech;
import com.github.aeddddd.ae2enhanced.config.ClientPreferences;
import com.github.aeddddd.ae2enhanced.guide.GuideBook;
import com.github.aeddddd.ae2enhanced.guide.GuideManager;
import com.github.aeddddd.ae2enhanced.guide.GuidePage;
import com.github.aeddddd.ae2enhanced.guide.PageAnchor;
import com.github.aeddddd.ae2enhanced.guide.md.element.InlineElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiConfirmOpenLink;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

/**
 * 指南主界面 —— 全屏：左侧导航树（支持二级目录）+ 顶栏（标题 + 主题切换）+ 右侧滚动页面.
 */
@SideOnly(Side.CLIENT)
public class GuiGuide extends GuiScreen {

    private static final int NAV_WIDTH = 150;
    private static final int TOP_HEIGHT = 22;
    private static final int BTN_THEME = 0;

    private final GuidePageView pageView = new GuidePageView(this);
    private final GuideNavigationPanel navPanel = new GuideNavigationPanel(this);

    private GuiButton themeButton;
    private GuideTheme theme;

    private String currentPageId;
    private GuidePage currentPage;
    private GuideBook lastBook;

    /**
     * 打开指南（指定页面/锚点；null 或无效则从首页开始）.
     */
    public static void open(PageAnchor anchor) {
        GuiGuide gui = new GuiGuide();
        GuideBook book = GuideManager.getInstance().getBook();
        PageAnchor target = anchor;
        if (target == null || book == null || !book.hasPage(target.getPageId())) {
            target = new PageAnchor(GuideBook.START_PAGE, null);
        }
        gui.navigateTo(target.getPageId(), target.getAnchor());
        Minecraft.getMinecraft().displayGuiScreen(gui);
    }

    public GuiGuide() {
        this.theme = GuideTheme.byId(ClientPreferences.current().guide.theme);
    }

    public GuideTheme getTheme() {
        return theme;
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);

        // 全屏布局：无外边距
        this.buttonList.clear();
        String themeLabel = I18n.format("gui.ae2enhanced.guide.theme", theme.displayName);
        this.themeButton = new GuiButtonTech(BTN_THEME, this.width - themeButtonWidth(themeLabel) - 6, 3,
                themeButtonWidth(themeLabel), 16, themeLabel);
        this.buttonList.add(this.themeButton);

        int contentTop = TOP_HEIGHT;
        this.navPanel.setBounds(0, contentTop, NAV_WIDTH, this.height - contentTop);
        this.navPanel.rebuildRows();
        this.pageView.setBounds(NAV_WIDTH, contentTop, this.width - NAV_WIDTH, this.height - contentTop);
        if (this.currentPage != null) {
            this.pageView.relayout();
        }
    }

    private int themeButtonWidth(String label) {
        return Math.max(90, this.fontRenderer.getStringWidth(label) + 12);
    }

    /**
     * 跳转到页面（核心导航方法）.
     */
    public void navigateTo(String pageId, String anchor) {
        GuideBook book = GuideManager.getInstance().getBook();
        if (book == null) {
            return;
        }
        GuidePage page = book.getPage(pageId);
        if (page == null) {
            AE2Enhanced.LOGGER.warn("[AE2E] Guide page '{}' not found (broken link)", pageId);
            return;
        }
        boolean samePage = pageId.equals(this.currentPageId);
        this.currentPageId = pageId;
        this.currentPage = page;
        if (samePage && anchor != null) {
            // 同页锚点：仅滚动
            this.pageView.scrollToAnchor(anchor);
        } else {
            this.pageView.setPage(page, anchor);
        }
    }

    public String getCurrentPageId() {
        return currentPageId;
    }

    /**
     * 供子组件调用（renderToolTip 在 GuiScreen 中为 protected）.
     */
    public void renderItemTooltip(net.minecraft.item.ItemStack stack, int x, int y) {
        this.renderToolTip(stack, x, y);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // 检测资源重载（F3+T / 语言切换）：book 对象变更后按 pageId 重新取页
        GuideBook book = GuideManager.getInstance().getBook();
        if (book != lastBook) {
            lastBook = book;
            if (currentPageId != null && book != null) {
                GuidePage page = book.getPage(currentPageId);
                if (page != null) {
                    this.currentPage = page;
                    this.pageView.setPage(page, null);
                }
            }
            this.navPanel.rebuildRows();
        }

        GuiScreen.drawRect(0, 0, this.width, this.height, theme.windowBg);

        // 顶栏
        GuiScreen.drawRect(0, 0, this.width, TOP_HEIGHT, theme.panelBg);
        GuiScreen.drawRect(0, TOP_HEIGHT - 1, this.width, TOP_HEIGHT, theme.border);
        if (this.currentPage != null) {
            this.fontRenderer.drawString(this.currentPage.getTitle(), 8, 7, theme.heading1);
        }

        this.navPanel.render(mouseX, mouseY);
        this.pageView.render(mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_THEME) {
            // 循环切换主题并持久化到配置
            this.theme = this.theme.next();
            ClientPreferences.setGuideTheme(this.theme.id);
            ConfigManager.sync(AE2Enhanced.MOD_ID, Config.Type.INSTANCE);
            String label = I18n.format("gui.ae2enhanced.guide.theme", theme.displayName);
            this.themeButton.displayString = label;
            this.themeButton.width = themeButtonWidth(label);
            this.themeButton.x = this.width - this.themeButton.width - 6;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (this.navPanel.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }

        // 页面视图点击（链接 / ItemLink）
        InlineElement clicked = this.pageView.mouseClicked(mouseX, mouseY, mouseButton);
        if (clicked != null) {
            handleElementClick(clicked);
        }
    }

    private void handleElementClick(InlineElement element) {
        if (element.getKind() == InlineElement.Kind.LINK) {
            String target = element.getLinkTarget();
            if (element.isExternalLink()) {
                this.mc.displayGuiScreen(new GuiConfirmOpenLink(this, target, 31102009, false));
                return;
            }
            int hash = target.indexOf('#');
            String pageId = hash >= 0 ? target.substring(0, hash) : target;
            String anchor = hash >= 0 ? target.substring(hash + 1) : null;
            navigateTo(pageId, anchor);
        } else if (element.getKind() == InlineElement.Kind.ITEM_LINK) {
            ItemStack stack = element.getItemStack();
            GuideBook book = GuideManager.getInstance().getBook();
            if (stack != null && !stack.isEmpty() && book != null) {
                PageAnchor anchor = book.getPageForItem(stack.getItem());
                if (anchor != null) {
                    navigateTo(anchor.getPageId(), anchor.getAnchor());
                }
            }
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        this.pageView.mouseReleased();
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        this.pageView.mouseClickMove(mouseY);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dWheel = Mouse.getEventDWheel();
        if (dWheel != 0) {
            int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
            int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
            if (this.pageView.isMouseOver(mouseX, mouseY)) {
                this.pageView.scrollWheel(dWheel);
            } else if (this.navPanel.isMouseOver(mouseX, mouseY)) {
                this.navPanel.scrollWheel(dWheel);
            }
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
