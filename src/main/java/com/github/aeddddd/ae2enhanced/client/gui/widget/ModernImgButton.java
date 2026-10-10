package com.github.aeddddd.ae2enhanced.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

import appeng.api.config.ActionItems;
import appeng.api.config.SearchBoxMode;
import appeng.api.config.Settings;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import appeng.client.gui.widgets.GuiImgButton;

import com.github.aeddddd.ae2enhanced.client.gui.dock.Ae2ModernArt;
import com.github.aeddddd.ae2enhanced.client.gui.dock.ChromeLayout;

/**
 * AE2 19.x（1.21.1）风格的图标按钮：按钮底用 {@code states.png} 的
 * {@code TOOLBAR_BUTTON_BACKGROUND} 三态精灵，图标用同一张表的 19.x 图标。
 *
 * <p>绘制规格逐字对齐 AE2 19.2.17 的 {@code IconButton.renderWidget}：</p>
 * <ul>
 *   <li>底图 18×20，画在 {@code (x-1, y + hoverOffset)}（比 16×16 的按钮左右各宽 1px、下垂 4px）；</li>
 *   <li>图标 16×16 画在 {@code (x, y + 1 + hoverOffset)}；</li>
 *   <li>禁用态底图用普通态 + 图标半透明（19.x 的 {@code opacity(0.5f)}）。</li>
 * </ul>
 *
 * <p>继承 {@link GuiImgButton} 是为了保留 1.12 侧的全部行为（{@code getSetting()}/
 * {@code getCurrentValue()} 驱动的 {@code actionPerformed} 轮转逻辑、可见性开关、右键反向），
 * 只替换"怎么画"。</p>
 *
 * <p>{@link #setLabel(String)} 用于 19.x 没有对应图标的动作按钮（编码区的倍率：×2/×3/÷2/÷3/+1/−1。
 * 19.x 把"数量"交给数量弹窗，因此没有倍率图标）——这些按钮改画 2 字符文字标签，
 * 底图仍是 19.x 官方按钮底。</p>
 */
public class ModernImgButton extends GuiImgButton {

    private final Settings setting;
    private final Enum<?> value;
    private String label;
    private String tooltip;

    public ModernImgButton(int x, int y, Settings setting, Enum<?> value) {
        super(x, y, setting, value);
        this.setting = setting;
        this.value = value;
    }

    /** 用文字标签代替图标（19.x 无对应图标时使用）。 */
    public ModernImgButton setLabel(String label) {
        this.label = label;
        return this;
    }

    /** 覆盖 tooltip 文本（默认取 AE2 的本地化 "设置名: 值"）。 */
    public ModernImgButton setTooltip(String tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /** 悬停时显示的提示文本（GUI 在绘制完按钮后调用）。 */
    public String tooltip() {
        if (tooltip != null) {
            return tooltip;
        }
        String message = getMessage();
        return message == null || message.isEmpty() ? null : message;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) {
            return;
        }
        boolean hovered = this.enabled && mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
        this.hovered = hovered;
        int hoverOffset = hovered ? 1 : 0;

        boolean pushed = com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.pushZLevel(1.0F);
        // 底图（18x20，与 19.x IconButton 相同的位置）
        Ae2ModernArt.drawToolbarButton(this.x - 1, this.y + hoverOffset, hovered, this.enabled);
        if (this.label != null) {
            drawLabel(mc, hoverOffset);
        } else {
            Ae2ModernArt.Icon icon = iconFor(this.setting, this.value);
            if (icon != null) {
                if (this.enabled) {
                    Ae2ModernArt.drawIcon(icon, this.x, this.y + 1 + hoverOffset, 0.0F);
                } else {
                    Ae2ModernArt.drawIconTinted(icon, this.x, this.y + 1 + hoverOffset,
                            1.0F, 1.0F, 1.0F, 0.5F, 0.0F);
                }
            }
        }
        com.github.aeddddd.ae2enhanced.client.gui.dock.NinePatch.popZLevel(pushed);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawLabel(Minecraft mc, int hoverOffset) {
        String text = this.label;
        int textWidth = mc.fontRenderer.getStringWidth(text);
        int color = this.enabled ? ChromeLayout.COLOR_TITLE_TEXT : ChromeLayout.COLOR_MUTED;
        // 2 字符标签在 16px 按钮里宽度吃紧：>12px 时用 0.5 缩放居中
        if (textWidth > this.width - 2) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(this.x + this.width / 2.0F, this.y + 1 + hoverOffset
                    + this.height / 2.0F, 0.0F);
            GlStateManager.scale(0.5F, 0.5F, 1.0F);
            mc.fontRenderer.drawString(text, -textWidth / 2, -4, color, false);
            GlStateManager.popMatrix();
        } else {
            mc.fontRenderer.drawString(text, this.x + (this.width - textWidth) / 2,
                    this.y + 1 + hoverOffset + (this.height - 8) / 2 + 1, color, false);
        }
    }

    /**
     * （设置, 取值）→ AE2 19.x 图标。坐标表来自 AE2 19.2.17 的 {@code appeng.client.gui.Icon}；
     * 对应关系取自同版本的 {@code SettingToggleButton} 注册表与 {@code ActionButton} 的 switch。
     */
    public static Ae2ModernArt.Icon iconFor(Settings setting, Enum<?> value) {
        if (setting == null || value == null) {
            return null;
        }
        if (setting == Settings.SORT_BY) {
            if (value == SortOrder.NAME) {
                return Ae2ModernArt.Icon.SORT_BY_NAME;
            }
            if (value == SortOrder.AMOUNT) {
                return Ae2ModernArt.Icon.SORT_BY_AMOUNT;
            }
            if (value == SortOrder.MOD) {
                return Ae2ModernArt.Icon.SORT_BY_MOD;
            }
            if (value == SortOrder.INVTWEAKS) {
                return Ae2ModernArt.Icon.SORT_BY_INVENTORY_TWEAKS;
            }
            return null;
        }
        if (setting == Settings.VIEW_MODE) {
            if (value == ViewItems.STORED) {
                return Ae2ModernArt.Icon.VIEW_MODE_STORED;
            }
            if (value == ViewItems.ALL) {
                return Ae2ModernArt.Icon.VIEW_MODE_ALL;
            }
            if (value == ViewItems.CRAFTABLE) {
                return Ae2ModernArt.Icon.VIEW_MODE_CRAFTING;
            }
            return null;
        }
        if (setting == Settings.SORT_DIRECTION) {
            if (value == SortDir.ASCENDING) {
                return Ae2ModernArt.Icon.ARROW_UP;
            }
            if (value == SortDir.DESCENDING) {
                return Ae2ModernArt.Icon.ARROW_DOWN;
            }
            return null;
        }
        if (setting == Settings.SEARCH_MODE && value instanceof SearchBoxMode) {
            // 19.x 的 8 个搜索图标与 1.12 的 8 个模式不是一一对应（19.x 有 REI 变体与 auto-clear 变体），
            // 按"自动聚焦 / 手动 / JEI / 记住上次 / 自动清空"的最近语义映射。
            switch ((SearchBoxMode) value) {
                case AUTOSEARCH:
                    return Ae2ModernArt.Icon.SEARCH_AUTO_FOCUS;
                case MANUAL_SEARCH:
                    return Ae2ModernArt.Icon.SEARCH_DEFAULT;
                case JEI_AUTOSEARCH:
                    return Ae2ModernArt.Icon.SEARCH_JEI;
                case JEI_MANUAL_SEARCH:
                    return Ae2ModernArt.Icon.SEARCH_REI;
                case AUTOSEARCH_KEEP:
                    return Ae2ModernArt.Icon.SEARCH_AUTO_FOCUS_REMEMBER;
                case MANUAL_SEARCH_KEEP:
                    return Ae2ModernArt.Icon.SEARCH_REMEMBER;
                case JEI_AUTOSEARCH_KEEP:
                    return Ae2ModernArt.Icon.SEARCH_JEI_AUTO_CLEAR;
                case JEI_MANUAL_SEARCH_KEEP:
                    return Ae2ModernArt.Icon.SEARCH_REI_AUTO_CLEAR;
                default:
                    return Ae2ModernArt.Icon.SEARCH_DEFAULT;
            }
        }
        if (setting == Settings.ACTIONS) {
            if (value == ActionItems.CLOSE) {
                return Ae2ModernArt.Icon.CLEAR;
            }
            if (value == ActionItems.ENCODE) {
                return Ae2ModernArt.Icon.WHITE_ARROW_DOWN;
            }
            // ItemSubstitution 是 1.12 的枚举（19.x 用 ToggleButton + S_SUBSTITUTION_*），
            // 这里按名字匹配避免编译期依赖具体枚举类型。
            String name = value.name();
            if ("ENABLED".equals(name)) {
                return Ae2ModernArt.Icon.SUBSTITUTION_ENABLED;
            }
            if ("DISABLED".equals(name)) {
                return Ae2ModernArt.Icon.SUBSTITUTION_DISABLED;
            }
            return null;
        }
        return null;
    }

    /** 供 GUI 做命中测试（与 {@link GuiButton} 的 x/y/width/height 一致）。 */
    public boolean isHoveredBy(int mouseX, int mouseY) {
        return this.visible && mouseX >= this.x && mouseY >= this.y
                && mouseX < this.x + this.width && mouseY < this.y + this.height;
    }
}
