package com.github.aeddddd.ae2enhanced.guide.client;

/**
 * 指南配色主题, 全部采用知名软件的真实配色方案: VS Code Dark+, GitHub Light, Dracula, Nord.
 */
public enum GuideTheme {

    /**
     * VS Code Dark+：背景 #1E1E1E，面板 #252526，边框 #3E3E42，
     * 文字 #D4D4D4，标题 #4EC9B0，链接 #3794FF，选中 #094771，悬停 #2A2D2E。
     */
    VS_CODE_DARK(
            "vscode-dark", "VS Code Dark+",
            0xFF1E1E1E, 0xFF252526, 0xFF3E3E42,
            0xFFD4D4D4, 0xFF808080,
            0xFFFFFFFF, 0xFF4EC9B0,
            0xFF3794FF,
            0xFF094771, 0xFF2A2D2E,
            0xFF797979
    ),

    /**
     * GitHub Light：背景 #FFFFFF，面板 #F6F8FA，边框 #D0D7DE，
     * 文字 #24292F，链接 #0969DA，选中 #DDF4FF，悬停 #F3F4F6。
     */
    GITHUB_LIGHT(
            "github-light", "GitHub Light",
            0xFFFFFFFF, 0xFFF6F8FA, 0xFFD0D7DE,
            0xFF24292F, 0xFF57606A,
            0xFF1F2328, 0xFF1F2328,
            0xFF0969DA,
            0xFFDDF4FF, 0xFFF3F4F6,
            0xFF8C959F
    ),

    /**
     * Dracula：背景 #282A36，面板 #21222C，边框/选中 #44475A，
     * 文字 #F8F8F2，注释 #6272A4，标题绿 #50FA7B / 紫 #BD93F9，链接青 #8BE9FD。
     */
    DRACULA(
            "dracula", "Dracula",
            0xFF282A36, 0xFF21222C, 0xFF44475A,
            0xFFF8F8F2, 0xFF6272A4,
            0xFF50FA7B, 0xFFBD93F9,
            0xFF8BE9FD,
            0xFF44475A, 0xFF3B3E51,
            0xFF6272A4
    ),

    /**
     * Nord：背景 #2E3440(nord0)，面板 #3B4252(nord1)，选中 #434C5E(nord2)，
     * 边框 #4C566A(nord3)，文字 #D8DEE9(nord4)，标题 #ECEFF4/#81A1C1，链接 #88C0D0(nord8)。
     */
    NORD(
            "nord", "Nord",
            0xFF2E3440, 0xFF3B4252, 0xFF4C566A,
            0xFFD8DEE9, 0xFF616E88,
            0xFFECEFF4, 0xFF81A1C1,
            0xFF88C0D0,
            0xFF434C5E, 0xFF3B4252,
            0xFF4C566A
    );

    public final String id;
    public final String displayName;
    public final int windowBg;      // 页面区背景
    public final int panelBg;       // 导航面板/顶栏背景
    public final int border;        // 分隔线/边框
    public final int text;          // 正文
    public final int textMuted;     // 次要文字
    public final int heading1;      // 一级标题
    public final int heading;       // 二三级标题
    public final int link;          // 链接
    public final int selection;     // 导航选中行
    public final int hover;         // 导航悬停行
    public final int scrollThumb;   // 滚动条滑块

    GuideTheme(String id, String displayName,
               int windowBg, int panelBg, int border,
               int text, int textMuted,
               int heading1, int heading,
               int link,
               int selection, int hover,
               int scrollThumb) {
        this.id = id;
        this.displayName = displayName;
        this.windowBg = windowBg;
        this.panelBg = panelBg;
        this.border = border;
        this.text = text;
        this.textMuted = textMuted;
        this.heading1 = heading1;
        this.heading = heading;
        this.link = link;
        this.selection = selection;
        this.hover = hover;
        this.scrollThumb = scrollThumb;
    }

    /**
     * 循环到下一个主题.
     */
    public GuideTheme next() {
        GuideTheme[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }

    /**
     * 按 id 查找，无效 id 回退到 VS Code Dark+.
     */
    public static GuideTheme byId(String id) {
        if (id != null) {
            for (GuideTheme theme : values()) {
                if (theme.id.equals(id)) {
                    return theme;
                }
            }
        }
        return VS_CODE_DARK;
    }
}
