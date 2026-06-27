package com.github.aeddddd.ae2enhanced.client.gui.util;

import net.minecraft.util.ResourceLocation;

/**
 * GUI 纹理资源缓存.
 * <p>
 * 避免在 drawBG 等每帧调用中重复创建 {@link ResourceLocation} 对象.
 * 所有常用纹理在此以 static final 常量形式常驻.
 */
public final class GuiResourceCache {

    private GuiResourceCache() {}

    // AE2 标准纹理
    public static final ResourceLocation AE2_TERMINAL = new ResourceLocation("appliedenergistics2", "textures/guis/terminal.png");
    public static final ResourceLocation AE2_STATES = new ResourceLocation("appliedenergistics2", "textures/guis/states.png");

    // Omni Terminal 专属纹理
    public static final ResourceLocation OMNI_BG = new ResourceLocation("ae2enhanced", "textures/gui/omnigui.png");
    public static final ResourceLocation PATTERN_MODES = new ResourceLocation("ae2enhanced", "textures/gui/pattern_modes.png");
    public static final ResourceLocation CRAFTING_HIGHLIGHT = new ResourceLocation("ae2enhanced", "textures/gui/crafting.png");
}
