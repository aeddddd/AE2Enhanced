package com.github.aeddddd.ae2enhanced.memorycard.core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraftforge.fml.ModList;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.memorycard.handler.ae2.AE2PartHandler;
import com.github.aeddddd.ae2enhanced.memorycard.handler.ae2.AE2TileHandler;

/**
 * 通用内存卡 Handler 注册表.
 *
 * <p>硬依赖 handler(AE2)直接实例化;可选 mod handler 通过反射隔离加载,
 * 未安装的 mod 对应的 handler 类永远不会被触碰,避免 {@link NoClassDefFoundError}.
 * 第三方 Mod Handler 禁止在主源码集产生编译期依赖,必须以独立类 + 此处按 modid
 * 条件反射注册的方式接入(已接入: mekanism, enderio).</p>
 *
 * <p>按注册顺序遍历,第一个返回 {@code true} 的 handler 被使用.</p>
 */
public class MemoryCardHandlerRegistry {

    private static final List<IMemoryCardHandler> HANDLERS = new CopyOnWriteArrayList<>();
    private static volatile boolean initialized = false;

    public static void register(IMemoryCardHandler handler) {
        HANDLERS.add(handler);
    }

    public static IMemoryCardHandler findHandler(Object target) {
        init();
        for (IMemoryCardHandler handler : HANDLERS) {
            if (handler.canHandle(target)) {
                return handler;
            }
        }
        return null;
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // 1. 硬依赖 handler(AE2 是本 mod 的必需依赖)
        register(new AE2PartHandler());
        register(new AE2TileHandler());

        // 2. 可选 mod handler(反射隔离加载,不要在主源码集 import 第三方 Mod 类)
        // Mekanism 1.20.1 (10.4.x): 原生配置卡机制 + 升级槽
        tryLoad("mekanism", "com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismMachineHandler");
        // Ender IO 1.20.1 (6.x 重写版): 六面 IO / 红石控制 / 工作范围 / 合金炉模式
        tryLoad("enderio", "com.github.aeddddd.ae2enhanced.memorycard.handler.enderio.EnderIOMachineHandler");
    }

    private static void tryLoad(String modId, String className) {
        if (!ModList.get().isLoaded(modId)) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className);
            IMemoryCardHandler handler = (IMemoryCardHandler) clazz.getDeclaredConstructor().newInstance();
            register(handler);
            AE2Enhanced.LOGGER.info("[AE2E] MemoryCardHandlerRegistry loaded handler for mod: {}", modId);
        } catch (Throwable t) {
            // 可选 mod 的 handler 初始化失败(含 Error,如类初始化异常)不应导致游戏崩溃
            AE2Enhanced.LOGGER.warn("[AE2E] MemoryCardHandlerRegistry failed to load handler for mod: {}", modId, t);
        }
    }
}
