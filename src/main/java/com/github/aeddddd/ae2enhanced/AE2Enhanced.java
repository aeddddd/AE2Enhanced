package com.github.aeddddd.ae2enhanced;

import com.github.aeddddd.ae2enhanced.event.ModEventHandler;
import com.github.aeddddd.ae2enhanced.util.network.WirelessChannelTickHandler;
import net.minecraftforge.common.MinecraftForge;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.proxy.CommonProxy;
import com.github.aeddddd.ae2enhanced.registry.GameRegistryManager;
import com.github.aeddddd.ae2enhanced.registry.ModContent;
import com.github.aeddddd.ae2enhanced.registry.ModNetwork;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.storage.channel.ChannelRegistrationManager;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartedEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = AE2Enhanced.MOD_ID,
    name = AE2Enhanced.MOD_NAME,
    version = AE2Enhanced.VERSION,
    dependencies = "required-after:mixinbooter@[10.6,);required-after:appliedenergistics2;after:terminal_interaction_integration;after:projecte"
)
public class AE2Enhanced {

    public static final String MOD_ID = "ae2enhanced";
    public static final String MOD_NAME = "AE2Enhanced";
    public static final String VERSION = "1.7.5";

    public static final String CLIENT_PROXY = "com.github.aeddddd.ae2enhanced.proxy.ClientProxy";
    public static final String SERVER_PROXY = "com.github.aeddddd.ae2enhanced.proxy.CommonProxy";

    @Mod.Instance(MOD_ID)
    public static AE2Enhanced instance;

    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = SERVER_PROXY)
    public static CommonProxy proxy;

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static SimpleNetworkWrapper network;

    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs(MOD_ID) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(ItemRegistry.CONFORMAL_CHARGE);
        }
    };

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigManager.sync(MOD_ID, net.minecraftforge.common.config.Config.Type.INSTANCE);
        PersonalDimensionManager.registerDimensionType();
        com.github.aeddddd.ae2enhanced.dimension.PresetLoader.copyDefaultPresetToConfigIfMissing();
        GameRegistryManager.initItems();
        ModContent.preInit();
        ModNetwork.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new GuiHandler());
        proxy.init(event);

        checkMixinEnvironment();

        // 注册存储通道：根据外部通道存在性决定是否注册 AE2E 自有通道
        ChannelRegistrationManager.registerChannels();

        // 注册 TII 资源提供者(仅在 TII 存在时加载注册器类,避免无条件引用 TII 类)
        if (com.github.aeddddd.ae2enhanced.integration.terminal.tii.TiiCompat.isLoaded()) {
            MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.integration.terminal.TiiResourceRegistration());
        }

        ModContent.init();
        ModRecipes.init();
        ModEventHandler.register();
        MinecraftForge.EVENT_BUS.register(new WirelessChannelTickHandler());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);

        // 元件终端(Cell Terminal)集成: 注册 EMC 接口的存储总线扫描器.
        // 经 Class.forName 隔离,避免无条件加载类引用 cellterminal 类
        if (net.minecraftforge.fml.common.Loader.isModLoaded("cellterminal")) {
            try {
                Class<?> clazz = Class.forName("com.github.aeddddd.ae2enhanced.integration.cellterminal.CellTerminalIntegration");
                clazz.getMethod("init").invoke(null);
            } catch (Exception e) {
                LOGGER.warn("[AE2E] Failed to register Cell Terminal integration", e);
            }
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.github.aeddddd.ae2enhanced.command.CommandAE2Enhanced());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        PersonalDimensionManager.onServerStarted(event);
        // 世界加载完成后使处理仓配方索引失效,首次使用时以完整注册表重建
        //（cells/CraftTweaker 等在 init 之后才完成注册,过早构建会漏配方）
        com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex.markDirty();
    }

    @Mod.EventHandler
    public void serverStopping(net.minecraftforge.fml.common.event.FMLServerStoppingEvent event) {
        // 清理中枢接口目标所有权静态表，避免单机跨存档重载后旧实例残留锁死机器
        com.github.aeddddd.ae2enhanced.centralinterface.TargetOwnershipTracker.instance().clearAll();
    }

    private void checkMixinEnvironment() {
        boolean hasMixinBooter = net.minecraftforge.fml.common.Loader.isModLoaded("mixinbooter");
        boolean hasCleanroom = false;
        try {
            Class.forName("com.cleanroommc.common.launch.ActualClassLoader");
            hasCleanroom = true;
        } catch (ClassNotFoundException ignored) {
            try {
                Class.forName("com.cleanroommc.loader.ActualClassLoader");
                hasCleanroom = true;
            } catch (ClassNotFoundException ignored2) {}
        }
        if (!hasMixinBooter && !hasCleanroom) {
            LOGGER.error("[AE2E] ============================================================");
            LOGGER.error("[AE2E] CRITICAL: MixinBooter not detected!");
            LOGGER.error("[AE2E] AE2Enhanced requires MixinBooter on standard Forge environments.");
            LOGGER.error("[AE2E] Without it, all Mixin-based features will be silently disabled");
            LOGGER.error("[AE2E] Please install MixinBooter:");
            LOGGER.error("[AE2E]   https://www.curseforge.com/minecraft/mc-mods/mixinbooter");
            LOGGER.error("[AE2E] ============================================================");
        }
    }
}
