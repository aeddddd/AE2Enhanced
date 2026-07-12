package com.github.aeddddd.ae2enhanced.client.render;

import java.io.IOException;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * AE2Enhanced 自定义 Shader 管理器。
 * <p>在 {@link RegisterShadersEvent} 中注册模组 shader 资源，
 * 渲染器通过 {@link #getAssemblyBlackHole()} 获取实例。</p>
 */
public final class AE2EnhancedShaders {

    private AE2EnhancedShaders() {
    }

    public static ShaderInstance ASSEMBLY_BLACK_HOLE = null;

    public static ShaderInstance getAssemblyBlackHole() {
        return ASSEMBLY_BLACK_HOLE != null ? ASSEMBLY_BLACK_HOLE : GameRenderer.getPositionColorShader();
    }

    public static boolean isAssemblyBlackHoleLoaded() {
        return ASSEMBLY_BLACK_HOLE != null;
    }

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        new ResourceLocation(AE2Enhanced.MOD_ID, "assembly_black_hole"),
                        DefaultVertexFormat.POSITION_COLOR),
                shader -> ASSEMBLY_BLACK_HOLE = shader);
    }
}
