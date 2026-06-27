package com.github.aeddddd.ae2enhanced.util.memorycard.core;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.handler.ae2.AE2PartHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.handler.ae2.AE2TileHandler;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用内存卡 Handler 注册表.
 *
 * <p>硬依赖 handler(AE2)直接实例化；可选 mod handler 通过反射隔离加载,
 * 未安装的 mod 对应的 handler 类永远不会被触碰,避免 {@link NoClassDefFoundError}.</p>
 *
 * <p>按注册顺序遍历,第一个返回 {@code true} 的 handler 被使用.</p>
 */
public class MemoryCardHandlerRegistry {

    private static final List<IMemoryCardHandler> HANDLERS = new ArrayList<>();
    private static boolean initialized = false;

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

        // 1. 硬依赖 handler(AE2-UEL 是本 mod 的必需依赖)
        register(new AE2PartHandler());
        register(new AE2TileHandler());

        // 2. 可选 mod handler(反射隔离加载)
        tryLoad("mekanism", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.mekanism.MekanismMachineHandler");
        tryLoad("enderio", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.enderio.EnderIOMachineHandler");
        tryLoad("enderio", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.enderio.EnderIOConduitHandler");
        tryLoad("thermalexpansion", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.thermalexpansion.ThermalExpansionMachineHandler");
        tryLoad("nuclearcraft", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.nuclearcraft.NuclearCraftMachineHandler");
        tryLoad("techreborn", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.techreborn.TechRebornMachineHandler");
    }

    private static void tryLoad(String modId, String className) {
        if (!Loader.isModLoaded(modId)) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className);
            IMemoryCardHandler handler = (IMemoryCardHandler) clazz.newInstance();
            register(handler);
            AE2Enhanced.LOGGER.info("[AE2E] MemoryCardHandlerRegistry loaded handler for mod: {}", modId);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] MemoryCardHandlerRegistry failed to load handler for mod: {}", modId, e);
        }
    }
}
