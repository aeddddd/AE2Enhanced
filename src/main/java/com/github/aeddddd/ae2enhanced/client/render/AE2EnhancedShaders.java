package com.github.aeddddd.ae2enhanced.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * AE2Enhanced 自定义 Shader 管理器.
 * <p>在 {@link RegisterShadersEvent} 中注册模组 shader 资源,
 * 渲染器通过 {@link #getAssemblyBlackHole()} 获取实例.</p>
 */
public final class AE2EnhancedShaders {

    private AE2EnhancedShaders() {
    }

    public static ShaderInstance ASSEMBLY_BLACK_HOLE = null;
    public static ShaderInstance ASSEMBLY_BLACK_HOLE_POST = null;
    /**
     * 微型奇点专用实例：与装配枢纽共用同一 vsh/fsh,但 uniform 独立,
     * 避免同一帧内多个渲染目标互相覆盖 uniform（后写者获胜）.
     */
    public static ShaderInstance MICRO_SINGULARITY = null;

    public static ShaderInstance getAssemblyBlackHole() {
        return ASSEMBLY_BLACK_HOLE != null ? ASSEMBLY_BLACK_HOLE : GameRenderer.getPositionColorShader();
    }

    public static ShaderInstance getMicroSingularity() {
        return MICRO_SINGULARITY != null ? MICRO_SINGULARITY : GameRenderer.getPositionColorShader();
    }

    public static boolean isMicroSingularityLoaded() {
        return MICRO_SINGULARITY != null;
    }

    public static ShaderInstance getAssemblyBlackHolePost() {
        return ASSEMBLY_BLACK_HOLE_POST;
    }

    public static boolean isAssemblyBlackHoleLoaded() {
        return ASSEMBLY_BLACK_HOLE != null;
    }

    public static boolean isAssemblyBlackHolePostLoaded() {
        return ASSEMBLY_BLACK_HOLE_POST != null;
    }

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            new ResourceLocation(AE2Enhanced.MOD_ID, "assembly_black_hole"),
                            DefaultVertexFormat.POSITION_COLOR),
                    shader -> {
                        ASSEMBLY_BLACK_HOLE = shader;
                        AE2Enhanced.LOGGER.info("Registered assembly black hole shader");
                    });
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("Failed to register assembly black hole shader, falling back to position_color", e);
        }

        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            new ResourceLocation(AE2Enhanced.MOD_ID, "assembly_black_hole_post"),
                            DefaultVertexFormat.POSITION),
                    shader -> {
                        ASSEMBLY_BLACK_HOLE_POST = shader;
                        AE2Enhanced.LOGGER.info("Registered assembly black hole post-processing shader");
                    });
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("Failed to register assembly black hole post-processing shader", e);
        }

        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            new ResourceLocation(AE2Enhanced.MOD_ID, "micro_singularity"),
                            DefaultVertexFormat.POSITION_COLOR),
                    shader -> {
                        MICRO_SINGULARITY = shader;
                        AE2Enhanced.LOGGER.info("Registered micro singularity shader");
                    });
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("Failed to register micro singularity shader, falling back to position_color", e);
        }
    }
}
