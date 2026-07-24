package com.github.aeddddd.ae2enhanced.client.render;

import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

/**
 * AE2Enhanced 自定义 RenderType 集合.
 * <p>继承 {@link RenderType} 以访问受保护的 {@link RenderType#create} 与
 * {@link RenderStateShard} 状态常量.</p>
 */
public class AE2ERenderTypes extends RenderType {

    private AE2ERenderTypes(String pName, VertexFormat pFormat, VertexFormat.Mode pMode, int pBufferSize,
            boolean pAffectsCrumbling, boolean pSortOnUpload, Runnable pSetupState, Runnable pClearState) {
        super(pName, pFormat, pMode, pBufferSize, pAffectsCrumbling, pSortOnUpload, pSetupState, pClearState);
    }

    /**
     * 粗线框 RenderType,用于结构线框、光环.
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
     * 线宽缓存,避免每帧创建 RenderType.
     */
    private static final Map<Double, RenderType> LINES_CACHE = new ConcurrentHashMap<>();

    /**
     * 获取指定线宽的线框 RenderType,结构同 {@link #TESR_LINES}.
     * <p>移植自 1.12 的 glLineWidth:不同几何元素使用不同线宽.</p>
     */
    public static RenderType lines(double width) {
        if (width == 2.0) {
            return TESR_LINES;
        }
        return LINES_CACHE.computeIfAbsent(width, w -> create(
                "ae2enhanced_tesr_lines_" + w,
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.LINES,
                256,
                false,
                false,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(w)))
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(LEQUAL_DEPTH_TEST)
                        .createCompositeState(false)));
    }

    /**
     * 半透明 RenderType,用于光晕、能量壳.
     * <p>使用 lightning shader:entity_translucent 会采样 Sampler0 并乘光照贴图颜色,
     * 而 POSITION_COLOR 格式缺少 UV0/UV2 属性,采样结果不可控导致颜色异常.</p>
     * <p>sortOnUpload 必须为 false:顶点排序按 QUADS 分组重排,会打乱 TRIANGLES
     * 顶点顺序产生错乱三角形;不写深度,避免半透明层互相遮挡.</p>
     */
    public static final RenderType TESR_TRANSLUCENT = create(
            "ae2enhanced_tesr_translucent",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /**
     * Additive 混合 RenderType,用于自发光层.
     * <p>使用 lightning shader：entity_translucent 会乘光照贴图颜色,
     * 而 POSITION_COLOR 格式缺少 UV2 属性时采样为黑色,additive 混合下完全不可见.</p>
     * <p>sortOnUpload 必须为 false,原因同 {@link #TESR_TRANSLUCENT}.</p>
     */
    public static final RenderType TESR_ADDITIVE = create(
            "ae2enhanced_tesr_additive",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /**
     * 自定义不透明 RenderType,用于黑色事件视界等实心体.
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
     * 装配枢纽黑洞核心 shader.
     */
    private static final ShaderStateShard ASSEMBLY_BLACK_HOLE_SHADER =
            new ShaderStateShard(AE2EnhancedShaders::getAssemblyBlackHole);

    /**
     * 装配枢纽黑洞主体 RenderType（事件视界 + 吸积盘）,使用自定义 shader.
     * <p>写入深度：事件视界作为黑洞本体需要被后处理光线步进与后续几何正确遮挡；
     * 吸积盘在同一缓冲内后绘制（LEQUAL）,与球体的前后关系由深度测试自然处理.</p>
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
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));

    /**
     * 装配枢纽黑洞发光 RenderType（相对论性喷流）,使用自定义 shader 与 Additive 混合.
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

    /**
     * 微型奇点黑洞 RenderType,绑定独立的 shader 实例（uniform 与装配枢纽隔离）.
     * <p>必须配合绘制后立即 endBatch 使用：同一帧多个微型奇点共用同一实例,
     * 立即结算可保证每个奇点以自己的 uCenter/uScale 绘制.</p>
     */
    private static final ShaderStateShard MICRO_SINGULARITY_SHADER =
            new ShaderStateShard(AE2EnhancedShaders::getMicroSingularity);

    public static final RenderType MICRO_SINGULARITY_BLACK_HOLE = create(
            "ae2enhanced_micro_singularity_black_hole",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.TRIANGLES,
            256,
            false,
            true,
            CompositeState.builder()
                    .setShaderState(MICRO_SINGULARITY_SHADER)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .createCompositeState(false));
}
