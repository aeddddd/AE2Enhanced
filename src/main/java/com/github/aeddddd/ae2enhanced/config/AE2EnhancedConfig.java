package com.github.aeddddd.ae2enhanced.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * AE2Enhanced 配置中心.
 * <p>
 * 所有配置项均通过 {@link ForgeConfigSpec.Builder#translation(String)} 绑定
 * {@code ae2enhanced.configuration.<key>} 形式的本地化键,配合 lang 文件在配置界面中显示.
 */
public final class AE2EnhancedConfig {

    private static final String TRANSLATION_PREFIX = "ae2enhanced.configuration.";

    public enum BlackHoleDamageMode {
        ALL, NON_CREATIVE, NONE
    }

    /** DAG 计划引擎模式(阶段 4,kill-switch). */
    public enum DagPlannerMode {
        /** 关闭:原生递归 + 特殊求解器(现状). */
        OFF,
        /** 兜底:原生先算,得出缺料模拟计划时 DAG 重算,更优则采用. */
        FALLBACK,
        /** 默认:一切非特殊根请求直接走 DAG 引擎,其内部按需回落原生. */
        DEFAULT
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

    private static ForgeConfigSpec.Builder translation(ForgeConfigSpec.Builder builder, String key) {
        return builder.translation(TRANSLATION_PREFIX + key);
    }

    public static class CommonConfig {
        public final ForgeConfigSpec.IntValue computationMaxParallel;
        public final ForgeConfigSpec.IntValue computationMaxCpus;
        public final ForgeConfigSpec.IntValue hyperdimensionalFlushIntervalSeconds;
        public final ForgeConfigSpec.IntValue assemblyMaxPendingOutputs;
        public final ForgeConfigSpec.BooleanValue enableBlackHole;
        public final ForgeConfigSpec.EnumValue<BlackHoleDamageMode> blackHoleDamageMode;
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
        public final ForgeConfigSpec.EnumValue<DagPlannerMode> dagPlannerMode;
        public final ForgeConfigSpec.BooleanValue debugMode;

        CommonConfig(ForgeConfigSpec.Builder builder) {
            builder.push("computation");
            computationMaxParallel = translation(builder, "maxParallel")
                    .comment("超因果计算核心每个子 CPU 的并行数")
                    .defineInRange("maxParallel", 16384, 1, Integer.MAX_VALUE);
            computationMaxCpus = translation(builder, "maxCpus")
                    .comment("超因果计算核心子 CPU 池最大数量,提交任务时无空闲 CPU 将自动分裂新子 CPU,直至达到该上限")
                    .defineInRange("maxCpus", 256, 1, 4096);
            builder.pop();

            builder.push("hyperdimensional");
            hyperdimensionalFlushIntervalSeconds = translation(builder, "flushIntervalSeconds")
                    .comment("超维度仓储文件刷新间隔（秒）")
                    .defineInRange("flushIntervalSeconds", 30, 1, 3600);
            builder.pop();

            builder.push("assembly");
            assemblyMaxPendingOutputs = translation(builder, "maxPendingOutputs")
                    .comment("装配枢纽产物缓冲上限")
                    .defineInRange("maxPendingOutputs", 4096, 1, 100000);
            builder.pop();

            builder.push("blackHole");
            enableBlackHole = translation(builder, "enableBlackHole")
                    .comment("是否启用装配枢纽黑洞事件视界")
                    .define("enableBlackHole", true);
            blackHoleDamageMode = translation(builder, "damageMode")
                    .comment("微型奇点事件视界伤害模式：ALL 击杀所有实体,NON_CREATIVE 不击杀创造玩家,NONE 关闭伤害")
                    .defineEnum("damageMode", BlackHoleDamageMode.ALL);
            builder.pop();

            builder.push("personalDimension");
            personalDimensionFloorY = translation(builder, "floorY")
                    .comment("个人维度地板高度")
                    .defineInRange("floorY", 64, 1, 250);
            personalDimensionEntryY = translation(builder, "entryY")
                    .comment("个人维度默认进入高度")
                    .defineInRange("entryY", 65, 2, 255);
            personalDimensionPresetPath = translation(builder, "presetPath")
                    .comment("个人维度地板样式：API 注册的命名样式 id")
                    .define("presetPath", "ae2enhanced:default");
            builder.pop();

            builder.push("omniTool");
            omniToolEnableBedrockBreakerUpgrade = translation(builder, "enableBedrockBreakerUpgrade")
                    .comment("是否启用基岩破坏者升级配方（工具 + 基岩）,允许工具破坏所有不可破坏方块（硬度 < 0）")
                    .define("enableBedrockBreakerUpgrade", true);
            omniToolMaxBlinkDistance = translation(builder, "maxBlinkDistance")
                    .comment("旅行模式闪现（blink）最大距离（格）")
                    .defineInRange("maxBlinkDistance", 256, 1, 1000);
            omniToolMaxBreakCooldown = translation(builder, "maxBreakCooldown")
                    .comment("挖掘冷却上限（tick）,0 表示无冷却")
                    .defineInRange("maxBreakCooldown", 20, 0, 200);
            omniToolBaseAttackDamage = translation(builder, "baseAttackDamage")
                    .comment("先进ME工具攻击力")
                    .defineInRange("baseAttackDamage", 6.0, 0.0, 10000.0);
            omniToolEnableWallPhase = translation(builder, "enableWallPhase")
                    .comment("闪现默认是否允许穿墙（可逐工具覆盖）")
                    .define("enableWallPhase", true);
            omniToolBreakableBlacklist = translation(builder, "breakableBlacklist")
                    .comment("不可破坏黑名单,工具无法破坏列表中的方块")
                    .defineList("breakableBlacklist", java.util.List.of(), obj -> obj instanceof String);
            omniToolMaxEnchantmentLevel = translation(builder, "maxEnchantmentLevel")
                    .comment("附魔书升级时附魔等级上限")
                    .defineInRange("maxEnchantmentLevel", 255, 1, 32767);
            builder.pop();

            builder.push("craftingPlan");
            dagPlannerMode = translation(builder, "dagPlannerMode")
                    .comment("合成计划引擎优化:OFF 关闭;FALLBACK 原生算不出时启用优化;"
                            + "DEFAULT 所有非特殊请求默认走优化,原版递归树作回退")
                    .defineEnum("dagPlannerMode", DagPlannerMode.DEFAULT);
            builder.pop();

            builder.push("debug");
            debugMode = translation(builder, "debugMode")
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

            enableAssemblyRenderer = translation(builder, "enableAssemblyRenderer")
                    .comment("是否启用装配枢纽中心渲染")
                    .define("enableAssemblyRenderer", true);

            enableAssemblyShader = translation(builder, "enableAssemblyShader")
                    .comment("是否启用装配枢纽对象空间 shader 渲染")
                    .define("enableAssemblyShader", true);

            enableAssemblyPostProcessing = translation(builder, "enableAssemblyPostProcessing")
                    .comment("是否启用装配枢纽全屏后处理 shader 渲染")
                    .define("enableAssemblyPostProcessing", true);

            forceCompatibilityMode = translation(builder, "forceCompatibilityMode")
                    .comment("强制兼容模式：禁用后处理与 shader 渲染")
                    .define("forceCompatibilityMode", false);

            enableHyperdimensionalRenderer = translation(builder, "enableHyperdimensionalRenderer")
                    .comment("是否启用超维度仓储全息渲染")
                    .define("enableHyperdimensionalRenderer", true);

            renderDistance = translation(builder, "renderDistance")
                    .comment("多方块特效最大渲染距离（方块数）")
                    .defineInRange("renderDistance", 96, 16, 512);

            dynamicRenderIntensity = translation(builder, "dynamicRenderIntensity")
                    .comment("动态渲染强度缩放（0.0 ~ 2.0）")
                    .defineInRange("dynamicRenderIntensity", 1.0, 0.0, 2.0);

            maxDynamicElements = translation(builder, "maxDynamicElements")
                    .comment("动态元素数量上限（环、壳等）")
                    .defineInRange("maxDynamicElements", 8, 1, 16);

            particleDensity = translation(builder, "particleDensity")
                    .comment("粒子密度缩放（0.0 ~ 2.0）")
                    .defineInRange("particleDensity", 1.0, 0.0, 2.0);

            useLOD = translation(builder, "useLOD")
                    .comment("是否启用远距离 LOD 简化")
                    .define("useLOD", true);

            builder.pop();
        }
    }
}
