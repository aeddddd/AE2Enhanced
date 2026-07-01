package com.github.aeddddd.ae2enhanced.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

/**
 * AE2Enhanced 配置中心。
 */
public final class AE2EnhancedConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final CommonConfig COMMON;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        COMMON = new CommonConfig(builder);
        COMMON_SPEC = builder.build();
    }

    private AE2EnhancedConfig() {
    }

    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, COMMON_SPEC);
    }

    public static class CommonConfig {
        public final ForgeConfigSpec.IntValue computationMaxParallel;
        public final ForgeConfigSpec.IntValue hyperdimensionalFlushIntervalSeconds;
        public final ForgeConfigSpec.IntValue assemblyMaxPendingOutputs;
        public final ForgeConfigSpec.BooleanValue enableBlackHole;
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
                    .comment("是否启用装配枢纽黑洞事件视界")
                    .define("enableBlackHole", true);
            builder.pop();

            builder.push("debug");
            debugMode = builder
                    .comment("调试模式：输出更多日志")
                    .define("debugMode", false);
            builder.pop();
        }
    }
}
