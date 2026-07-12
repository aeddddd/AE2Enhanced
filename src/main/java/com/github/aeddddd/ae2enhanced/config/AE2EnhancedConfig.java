package com.github.aeddddd.ae2enhanced.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * AE2Enhanced 配置中心。
 */
public final class AE2EnhancedConfig {

    public enum BlackHoleDamageMode {
        ALL, NON_CREATIVE, NONE
    }

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final CommonConfig COMMON;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final ClientConfig CLIENT;

    static {
        ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        COMMON = new CommonConfig(commonBuilder);
        COMMON_SPEC = commonBuilder.build();

        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        CLIENT = new ClientConfig(clientBuilder);
        CLIENT_SPEC = clientBuilder.build();
    }

    private AE2EnhancedConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CLIENT_SPEC);
    }

    public static class CommonConfig {
        public final ForgeConfigSpec.IntValue computationMaxParallel;
        public final ForgeConfigSpec.IntValue hyperdimensionalFlushIntervalSeconds;
        public final ForgeConfigSpec.IntValue assemblyMaxPendingOutputs;
        public final ForgeConfigSpec.BooleanValue enableBlackHole;
        public final ForgeConfigSpec.EnumValue<BlackHoleDamageMode> blackHoleDamageMode;
        public final ForgeConfigSpec.BooleanValue debugMode;

        CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("computation");
            computationMaxParallel = builder
                    .comment("超因果计算核心每个虚拟 CPU 的并行上限，同时作为 CPU 池最大数量上限")
                    .defineInRange("maxParallel", 8, 1, 16);
            builder.pop();

            builder.push("hyperdimensional");
            hyperdimensionalFlushIntervalSeconds = builder
                    .comment("超维度仓储文件刷新间隔（秒）")
                    .defineInRange("flushIntervalSeconds", 30, 1, 3600);
            builder.pop();

            builder.push("assembly");
            assemblyMaxPendingOutputs = builder
                    .comment("装配枢纽产物缓冲上限")
                    .defineInRange("maxPendingOutputs", 4096, 1, 100000);
            enableBlackHole = builder
                    .comment("是否启用装配枢纽黑洞事件视界（服务端逻辑开关）")
                    .define("enableBlackHole", true);
            builder.pop();

            builder.push("blackHole");
            blackHoleDamageMode = builder
                    .comment("微型奇点事件视界伤害模式：ALL 击杀所有实体，NON_CREATIVE 不击杀创造玩家，NONE 关闭伤害")
                    .defineEnum("damageMode", BlackHoleDamageMode.ALL);
            builder.pop();

            builder.push("debug");
            debugMode = builder
                    .comment("调试模式：输出更多日志")
                    .define("debugMode", false);
            builder.pop();
        }
    }

    public static class ClientConfig {
        public final ForgeConfigSpec.BooleanValue enableAssemblyRenderer;
        public final ForgeConfigSpec.BooleanValue enableAssemblyShader;
        public final ForgeConfigSpec.BooleanValue forceCompatibilityMode;
        public final ForgeConfigSpec.BooleanValue enableHyperdimensionalRenderer;
        public final ForgeConfigSpec.IntValue renderDistance;
        public final ForgeConfigSpec.DoubleValue dynamicRenderIntensity;
        public final ForgeConfigSpec.IntValue maxDynamicElements;
        public final ForgeConfigSpec.DoubleValue particleDensity;
        public final ForgeConfigSpec.BooleanValue useLOD;

        ClientConfig(ForgeConfigSpec.Builder builder) {
            builder.push("render");

            enableAssemblyRenderer = builder
                    .comment("是否启用装配枢纽中心渲染")
                    .define("enableAssemblyRenderer", true);

            enableAssemblyShader = builder
                    .comment("是否启用装配枢纽自定义 shader 渲染（禁用则回退到 VertexConsumer）")
                    .define("enableAssemblyShader", true);

            forceCompatibilityMode = builder
                    .comment("强制兼容模式：禁用 shader 渲染，避免与光影包/优化模组冲突")
                    .define("forceCompatibilityMode", false);

            enableHyperdimensionalRenderer = builder
                    .comment("是否启用超维度仓储全息渲染")
                    .define("enableHyperdimensionalRenderer", true);

            renderDistance = builder
                    .comment("多方块特效最大渲染距离（方块数）")
                    .defineInRange("renderDistance", 96, 16, 512);

            dynamicRenderIntensity = builder
                    .comment("动态渲染强度缩放（0.0 ~ 2.0）")
                    .defineInRange("dynamicRenderIntensity", 1.0, 0.0, 2.0);

            maxDynamicElements = builder
                    .comment("动态元素数量上限（环、壳等）")
                    .defineInRange("maxDynamicElements", 8, 1, 16);

            particleDensity = builder
                    .comment("粒子密度缩放（0.0 ~ 2.0）")
                    .defineInRange("particleDensity", 1.0, 0.0, 2.0);

            useLOD = builder
                    .comment("是否启用远距离 LOD 简化")
                    .define("useLOD", true);

            builder.pop();
        }
    }
}
