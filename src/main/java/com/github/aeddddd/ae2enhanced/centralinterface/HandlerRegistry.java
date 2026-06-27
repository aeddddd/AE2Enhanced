package com.github.aeddddd.ae2enhanced.centralinterface;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.List;

/**
 * 远程处理器反射隔离注册表.
 *
 * 无条件加载,内部通过 {@link Class#forName(String)} 懒加载具体 handler 类.
 * 未安装的 mod 对应的 handler 类永远不会被触碰,避免 {@link NoClassDefFoundError}.
 *
 * <p>注册顺序：通用 fallback 最先注册,mod-specific handlers 随后通过反射加载.
 * {@link #findHandler(String)} 按注册顺序匹配 {@link IRemoteHandler#canHandle(String)},
 * 未匹配到时返回默认 fallback({@link DefaultSingleBatchHandler}).</p>
 */
public class HandlerRegistry {

    private static final List<IRemoteHandler> HANDLERS = new ArrayList<>();
    private static boolean initialized = false;

    /**
     * 初始化注册表.线程安全,重复调用无效果.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // 1. 通用 fallback 必须最先注册(作为默认匹配)
        HANDLERS.add(new DefaultSingleBatchHandler());

        // 2. Mod-specific handlers(反射隔离加载)
        // P4 - Botania
        tryLoad("botania", "com.github.aeddddd.ae2enhanced.centralinterface.handler.botania.BotaniaHandler");
        // P5 - Blood Magic
        tryLoad("bloodmagic", "com.github.aeddddd.ae2enhanced.centralinterface.handler.bloodmagic.BloodMagicHandler");
        // P6 - Astral Sorcery / Actually Additions / Extended Crafting / Draconic Evolution / Bewitchment
        tryLoad("bewitchment", "com.github.aeddddd.ae2enhanced.centralinterface.handler.bewitchment.BewitchmentHandler");
        tryLoad("astralsorcery", "com.github.aeddddd.ae2enhanced.centralinterface.handler.astralsorcery.AstralSorceryHandler");
        tryLoad("actuallyadditions", "com.github.aeddddd.ae2enhanced.centralinterface.handler.actuallyadditions.ActuallyAdditionsHandler");
        tryLoad("extendedcrafting", "com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting.ExtendedCraftingHandler");
        tryLoad("extendedcrafting", "com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting.ExtendedCraftingTableHandler");
        tryLoad("extendedcrafting", "com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting.CompressorHandler");
        tryLoad("extendedcrafting", "com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting.EnderCrafterHandler");
        tryLoad("draconicevolution", "com.github.aeddddd.ae2enhanced.centralinterface.handler.draconicevolution.DraconicEvolutionHandler");
        // P8 - Thaumcraft (Infusion & Crucible)
        tryLoad("thaumcraft", "com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft.ThaumcraftHandler");
        tryLoad("thaumcraft", "com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft.ThaumcraftCrucibleHandler");

        // P9 - Ender IO / Thermal Expansion / NuclearCraft 2.19a
        // 这些 handler 通过反射直接访问机器内部库存,绕过侧面配置限制
        tryLoad("enderio", "com.github.aeddddd.ae2enhanced.centralinterface.handler.enderio.EnderIOMachineHandler");
        tryLoad("thermalexpansion", "com.github.aeddddd.ae2enhanced.centralinterface.handler.thermalexpansion.ThermalExpansionMachineHandler");
        tryLoad("nuclearcraft", "com.github.aeddddd.ae2enhanced.centralinterface.handler.nuclearcraft.NuclearCraftLegacyMachineHandler");
    }

    private static void tryLoad(String modId, String className) {
        if (!Loader.isModLoaded(modId)) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className);
            IRemoteHandler handler = (IRemoteHandler) clazz.newInstance();
            HANDLERS.add(handler);
            AE2Enhanced.LOGGER.info("[AE2E] HandlerRegistry loaded handler for mod: {}", modId);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] HandlerRegistry failed to load handler for mod: {}", modId, e);
        }
    }

    /**
     * 根据方块 ID 查找匹配的处理器.
     *
     * @param blockId 方块注册 ID(如 "minecraft:furnace")
     * @return 匹配的 handler；若无可匹配者,返回默认 fallback
     */
    public static IRemoteHandler findHandler(String blockId) {
        init();
        for (IRemoteHandler handler : HANDLERS) {
            if (handler.canHandle(blockId)) {
                return handler;
            }
        }
        return HANDLERS.get(0);
    }

    /**
     * 获取所有已注册的处理器副本(用于调试或列表展示).
     */
    public static List<IRemoteHandler> getHandlers() {
        init();
        return new ArrayList<>(HANDLERS);
    }
}
