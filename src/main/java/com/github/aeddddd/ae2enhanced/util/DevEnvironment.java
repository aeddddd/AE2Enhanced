package com.github.aeddddd.ae2enhanced.util;

import net.minecraftforge.fml.loading.FMLLoader;

/**
 * 开发环境判定。仅在 NeoGradle 开发运行（runClient/runServer）中返回 true，
 * 生产环境（整合包/正式服务器）中返回 false。
 * <p>用于"仅开发环境出现"的测试内容（如单方块测试 CPU）的条件注册。</p>
 */
public final class DevEnvironment {

    private DevEnvironment() {
    }

    public static boolean isDev() {
        return !FMLLoader.isProduction();
    }
}
