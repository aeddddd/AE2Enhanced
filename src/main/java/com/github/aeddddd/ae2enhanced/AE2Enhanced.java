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
    dependencies = "required-after:appliedenergistics2;after:terminal_interaction_integration"
)
public class AE2Enhanced {

    public static final String MOD_ID = "ae2enhanced";
    public static final String MOD_NAME = "AE2Enhanced";
    public static final String VERSION = "1.5.4-fix6";

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
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new com.github.aeddddd.ae2enhanced.command.CommandAE2Enhanced());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        PersonalDimensionManager.onServerStarted(event);
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
