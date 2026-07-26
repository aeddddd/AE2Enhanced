package com.github.aeddddd.ae2enhanced.mixin.client;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.me.crafting.AbstractTableRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link AbstractTableRenderer#screen} 访问器(获取宿主界面以读取选中 CPU 信息).
 */
@Mixin(value = AbstractTableRenderer.class, remap = false)
public interface AbstractTableRendererAccessor {

    @Accessor("screen")
    AEBaseScreen<?> getScreen();
}
