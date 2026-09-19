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

    /**
     * 按稳定 ID 反查 handler(供粘贴过滤器查询来源 handler 声明的键分类).
     * 兼容旧版内存卡中的粗粒度 ID(ae2_part / ae2_tile / ae2e_custom).
     * 找不到时返回 null,调用方回退到全局键清单.
     */
    public static IMemoryCardHandler findById(String id) {
        if (id == null || id.isEmpty()) return null;
        init();
        String effective = id;
        switch (id) {
            case "ae2_part":
                effective = "AE2PartHandler";
                break;
            case "ae2_tile":
                effective = "AE2TileHandler";
                break;
            default:
                break;
        }
        for (IMemoryCardHandler handler : HANDLERS) {
            if (handler.getId().equals(effective)) {
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
        tryLoad("industrialforegoing", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.industrialforegoing.IndustrialForegoingMachineHandler");
        tryLoad("extrautils2", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.extrautils2.ExU2MachineHandler");
        // Quantum Things(Random Things 非官方续作)沿用 randomthings 作为 modid
        tryLoad("randomthings", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.quantumthings.QuantumThingsMachineHandler");
        tryLoad("ae2stuff", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.ae2stuff.AE2StuffMachineHandler");
        // Lazy AE2 的 modid 为 threng
        tryLoad("threng", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.lazyae2.LazyAE2MachineHandler");
        tryLoad("rftools", "com.github.aeddddd.ae2enhanced.util.memorycard.handler.rftools.RFToolsCrafterHandler");

        // 3. 原版容器兜底(必须最后注册,模组设备优先由各自 handler 处理;
        //    运行时由配置 memoryCard.vanillaContainerCopy 控制)
        register(new com.github.aeddddd.ae2enhanced.util.memorycard.handler.vanilla.VanillaContainerHandler());
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
        } catch (Throwable t) {
            // 可选 mod 的 handler 初始化失败（含 Error，如类初始化异常）不应导致游戏崩溃
            AE2Enhanced.LOGGER.warn("[AE2E] MemoryCardHandlerRegistry failed to load handler for mod: {}", modId, t);
        }
    }
}
