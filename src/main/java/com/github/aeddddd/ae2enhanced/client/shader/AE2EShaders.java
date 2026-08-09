package com.github.aeddddd.ae2enhanced.client.shader;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

import java.nio.charset.StandardCharsets;

/**
 * AE2Enhanced 自定义 Shader 管理器（GL20 实现）.
 *
 * <p>1.12.2 没有 1.20 的 ShaderInstance/RegisterShadersEvent 管线,
 * 这里直接在首次渲染时从资源包加载 GLSL 源码并用 GL20 编译.</p>
 *
 * <p>同一 program 在每次绘制前重设 uniform 即可,
 * 无需像 1.20 那样为微型奇点维护独立 ShaderInstance.</p>
 *
 * <p>shader 不可用（编译失败/配置关闭/兼容模式/检测到 OptiFine）时返回 null,
 * 渲染器回退到固定管线绘制.</p>
 */
public final class AE2EShaders {

    private static ShaderProgram blackHole = null;
    private static boolean initAttempted = false;

    private static boolean optifineChecked = false;
    private static boolean optifinePresent = false;

    private AE2EShaders() {
    }

    /**
     * 获取黑洞 shader 程序；不可用时返回 null（调用方应走固定管线回退）.
     */
    public static ShaderProgram getBlackHole() {
        if (!shouldUseShader()) {
            return null;
        }
        if (!initAttempted) {
            initAttempted = true;
            tryLoad();
        }
        return blackHole;
    }

    /**
     * 是否满足使用自定义 shader 的配置与环境条件.
     */
    public static boolean shouldUseShader() {
        return AE2EnhancedConfig.render.enableBlackHoleShader
                && !AE2EnhancedConfig.render.forceCompatibilityMode
                && !isOptifinePresent();
    }

    private static void tryLoad() {
        try {
            String vert = readShaderSource("shaders/assembly_black_hole.vert");
            String frag = readShaderSource("shaders/assembly_black_hole.frag");
            blackHole = ShaderProgram.create(vert, frag);
            AE2Enhanced.LOGGER.info("[AE2E] Registered assembly black hole shader (GL20)");
        } catch (Exception e) {
            blackHole = null;
            AE2Enhanced.LOGGER.error("[AE2E] Failed to compile black hole shader, falling back to fixed pipeline", e);
        }
    }

    private static String readShaderSource(String path) throws Exception {
        IResource res = Minecraft.getMinecraft().getResourceManager()
                .getResource(new ResourceLocation(AE2Enhanced.MOD_ID, path));
        return IOUtils.toString(res.getInputStream(), StandardCharsets.UTF_8);
    }

    /**
     * OptiFine 检测：OptiFine 接管 FBO 与 shader 状态,自定义 GL20 shader 与其冲突,
     * 等价于 1.20 侧的 oculus 检测.
     */
    private static boolean isOptifinePresent() {
        if (optifineChecked) {
            return optifinePresent;
        }
        optifineChecked = true;
        try {
            Class.forName("optifine.OptiFineClassTransformer");
            optifinePresent = true;
        } catch (Throwable ignored) {
            try {
                Class.forName("net.optifine.Config");
                optifinePresent = true;
            } catch (Throwable ignored2) {
                // 未安装 OptiFine
            }
        }
        return optifinePresent;
    }
}
