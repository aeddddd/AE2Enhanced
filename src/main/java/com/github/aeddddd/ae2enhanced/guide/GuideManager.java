package com.github.aeddddd.ae2enhanced.guide;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.guide.loader.GuideLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Map;

/**
 * 指南管理器 —— 客户端单例，持有当前 GuideBook，监听资源重载（F3+T / 语言切换）.
 */
@SideOnly(Side.CLIENT)
public final class GuideManager implements net.minecraft.client.resources.IResourceManagerReloadListener {

    private static final GuideManager INSTANCE = new GuideManager();

    private volatile GuideBook book;

    private GuideManager() {}

    public static GuideManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册资源重载监听（ClientProxy.init 调用）.
     */
    public static void init() {
        IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
        if (rm instanceof IReloadableResourceManager) {
            ((IReloadableResourceManager) rm).registerReloadListener(INSTANCE);
        }
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {
        reload();
    }

    public void reload() {
        try {
            Map<String, GuidePage> pages = GuideLoader.loadPages();
            com.github.aeddddd.ae2enhanced.guide.client.ScriptGuideClient.merge(
                    pages, GuideLoader.getCurrentLanguage());
            this.book = GuideBook.build(pages);
            AE2Enhanced.LOGGER.info("[AE2E] Guide loaded: {} pages", pages.size());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to load guide", e);
        }
    }

    /**
     * 获取当前 GuideBook；首次访问时懒加载.
     */
    public GuideBook getBook() {
        GuideBook b = book;
        if (b == null) {
            reload();
            b = book;
        }
        return b;
    }
}
