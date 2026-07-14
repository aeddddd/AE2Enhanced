package com.github.aeddddd.ae2enhanced.client.render;

import java.util.OptionalDouble;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * AE2Enhanced 自定义 RenderType 集合。
 * <p>继承 {@link RenderType} 以访问受保护的 {@link RenderType#create} 与
 * {@link RenderStateShard} 状态常量。</p>
 */
public class AE2ERenderTypes extends RenderType {

    private AE2ERenderTypes(String pName, VertexFormat pFormat, VertexFormat.Mode pMode, int pBufferSize,
            boolean pAffectsCrumbling, boolean pSortOnUpload, Runnable pSetupState, Runnable pClearState) {
        super(pName, pFormat, pMode, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState);
    }

    /**
     * 粗线框 RenderType，用于结构线框、光环。
     */
    public static final RenderType TESR_LINES = create(
            "ae2enhanced_tesr_lines",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.LINES,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /**
     * 半透明 RenderType，用于光晕、能量壳。
     */
    public static final RenderType TESR_TRANSLUCENT = create(
            "ae2enhanced_tesr_translucent",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /**
     * Additive 混合 RenderType，用于自发光层。
     */
    public static final RenderType TESR_ADDITIVE = create(
            "ae2enhanced_tesr_additive",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /**
     * 自定义不透明 RenderType，用于黑色事件视界等实心体。
     */
    public static final RenderType TESR_SOLID = create(
            "ae2enhanced_tesr_solid",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /**
     * 装配枢纽黑洞核心 shader。
     */
    private static final ShaderStateShard ASSEMBLY_BLACK_HOLE_SHADER =
            new ShaderStateShard(AE2EnhancedShaders::getAssemblyBlackHole);

    /**
     * 装配枢纽黑洞主体 RenderType（事件视界 + 吸积盘），使用自定义 shader。
     * <p>关闭深度写入，使吸积盘能叠在不透明的事件视界球体上；仍保留深度测试，
     * 确保被方块等环境几何正确遮挡。</p>
     */
    public static final RenderType ASSEMBLY_BLACK_HOLE = create(
            "ae2enhanced_assembly_black_hole",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(ASSEMBLY_BLACK_HOLE_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /**
     * 装配枢纽黑洞发光 RenderType（相对论性喷流），使用自定义 shader 与 Additive 混合。
     */
    public static final RenderType ASSEMBLY_BLACK_HOLE_GLOW = create(
            "ae2enhanced_assembly_black_hole_glow",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(ASSEMBLY_BLACK_HOLE_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));
}
