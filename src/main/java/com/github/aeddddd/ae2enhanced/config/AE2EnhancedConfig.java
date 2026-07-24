package com.github.aeddddd.ae2enhanced.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * AE2Enhanced 配置中心.
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
        public final ForgeConfigSpec.IntValue personalDimensionFloorY;
        public final ForgeConfigSpec.IntValue personalDimensionEntryY;
        public final ForgeConfigSpec.ConfigValue<String> personalDimensionPresetPath;
        public final ForgeConfigSpec.BooleanValue omniToolEnableBedrockBreakerUpgrade;
        public final ForgeConfigSpec.IntValue omniToolMaxBlinkDistance;
        public final ForgeConfigSpec.IntValue omniToolMaxBreakCooldown;
        public final ForgeConfigSpec.DoubleValue omniToolBaseAttackDamage;
        public final ForgeConfigSpec.BooleanValue omniToolEnableWallPhase;
        public final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> omniToolBreakableBlacklist;
        public final ForgeConfigSpec.IntValue omniToolMaxEnchantmentLevel;

        CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("computation");
            computationMaxParallel = builder
                    .comment("超因果计算核心每个虚拟 CPU 的并行上限,同时作为 CPU 池最大数量上限")
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
                    .comment("微型奇点事件视界伤害模式：ALL 击杀所有实体,NON_CREATIVE 不击杀创造玩家,NONE 关闭伤害")
                    .defineEnum("damageMode", BlackHoleDamageMode.ALL);
            builder.pop();

            builder.push("personalDimension");
            personalDimensionFloorY = builder
                    .comment("个人维度地板高度")
                    .defineInRange("floorY", 64, 1, 250);
            personalDimensionEntryY = builder
                    .comment("个人维度默认进入高度")
                    .defineInRange("entryY", 65, 2, 255);
            personalDimensionPresetPath = builder
                    .comment("个人维度地板样式：API 注册的命名样式 id（默认 ae2enhanced:default 为马路+平台组合样式），或预设 JSON 路径（相对 config 目录，如 ae2enhanced/personal_dimension_floor.json）")
                    .define("presetPath", "ae2enhanced:default");
            builder.pop();

            builder.push("omniTool");
            omniToolEnableBedrockBreakerUpgrade = builder
                    .comment("是否启用基岩破坏者升级配方（工具 + 基岩）,允许工具破坏所有不可破坏方块（硬度 < 0）")
                    .define("enableBedrockBreakerUpgrade", true);
            omniToolMaxBlinkDistance = builder
                    .comment("旅行模式闪现（blink）最大距离（格）")
                    .defineInRange("maxBlinkDistance", 256, 1, 1000);
            omniToolMaxBreakCooldown = builder
                    .comment("挖掘冷却上限（tick）,0 表示无冷却")
                    .defineInRange("maxBreakCooldown", 20, 0, 200);
            omniToolBaseAttackDamage = builder
                    .comment("全能工具真实伤害基础攻击力")
                    .defineInRange("baseAttackDamage", 6.0, 0.0, 10000.0);
            omniToolEnableWallPhase = builder
                    .comment("闪现默认是否允许穿墙（可逐工具覆盖）")
                    .define("enableWallPhase", true);
            omniToolBreakableBlacklist = builder
                    .comment("不可破坏黑名单（注册名列表,如 minecraft:bedrock）,工具无法破坏列表中的方块")
                    .defineList("breakableBlacklist", java.util.List.of(), obj -> obj instanceof String);
            omniToolMaxEnchantmentLevel = builder
                    .comment("附魔书升级时附魔等级上限")
                    .defineInRange("maxEnchantmentLevel", 255, 1, 32767);
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
        public final ForgeConfigSpec.BooleanValue enableAssemblyPostProcessing;
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
                    .comment("是否启用装配枢纽对象空间 shader 渲染（作为后处理不可用时回退）")
                    .define("enableAssemblyShader", true);

            enableAssemblyPostProcessing = builder
                    .comment("是否启用装配枢纽全屏后处理 shader 渲染（参考 GTCEu 的 black_hole.fsh）")
                    .define("enableAssemblyPostProcessing", true);

            forceCompatibilityMode = builder
                    .comment("强制兼容模式：禁用后处理与 shader 渲染,避免与光影包/优化模组冲突")
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
