package com.github.aeddddd.ae2enhanced;

import com.github.aeddddd.ae2enhanced.config.ClientPreferences;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.event.ModEventHandler;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.proxy.CommonProxy;
import com.github.aeddddd.ae2enhanced.registry.GameRegistryManager;
import com.github.aeddddd.ae2enhanced.registry.ModContent;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;
import com.github.aeddddd.ae2enhanced.runtime.ModProcessScope;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.storage.channel.ChannelRegistrationManager;
import com.github.aeddddd.ae2enhanced.tile.network.WirelessChannelTickHandler;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.*;
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
    public static final String VERSION = "1.7.9.0";

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
        RuntimeScopes.startProcess(new ModProcessScope(MOD_ID, VERSION, event.getSide().isServer(),
                com.github.aeddddd.ae2enhanced.config.BootConfig.capture()));
        ClientPreferences.publish();
        com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.preInit();
        PersonalDimensionManager.registerDimensionType();
        com.github.aeddddd.ae2enhanced.dimension.PresetLoader.copyDefaultPresetToConfigIfMissing();
        GameRegistryManager.initItems();
        ModContent.preInit();
        ModNetwork.init();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        com.github.aeddddd.ae2enhanced.dimension.PrivateDimensionPermissions.register();
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new GuiHandler());
        proxy.init(event);

        checkMixinEnvironment();
        com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.init();

        // 注册存储通道：根据外部通道存在性决定是否注册 AE2E 自有通道
        ChannelRegistrationManager.registerChannels();

        // 注册 TII 资源提供者(仅在 TII 存在时加载注册器类,避免无条件引用 TII 类)
        if (com.github.aeddddd.ae2enhanced.integration.terminal.tii.TiiCompat.isLoaded()
                && com.github.aeddddd.ae2enhanced.config.BootConfig.current().integrationEnabled("tii")) {
            com.github.aeddddd.ae2enhanced.integration.terminal.TiiResourceRegistration tiiRegistration =
                    new com.github.aeddddd.ae2enhanced.integration.terminal.TiiResourceRegistration();
            MinecraftForge.EVENT_BUS.register(tiiRegistration);
            tiiRegistration.registerDirect();
        }

        ModContent.init();
        ModRecipes.init();
        ModEventHandler.register();
        MinecraftForge.EVENT_BUS.register(new WirelessChannelTickHandler());
        // 服务器 TPS/tick 耗时采集（/ae2e perf tps,总开关 /ae2e debug perf off）
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.diag.perf.TpsTracker());
        // 超维度仓储会话注册表的闲置扫描（会话常驻 + 闲置落盘关闭）
        com.github.aeddddd.ae2enhanced.storage.HyperdimensionalStorageManager.startIdleScanner();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.postInit();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        RuntimeScopes.startServer();
        event.registerServerCommand(new com.github.aeddddd.ae2enhanced.command.CommandAE2Enhanced());
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        RuntimeScopes.bindServerSaveRoot(net.minecraftforge.fml.common.FMLCommonHandler.instance()
                .getMinecraftServerInstance().getWorld(0).getSaveHandler().getWorldDirectory().toPath());
        PersonalDimensionManager.onServerStarted(event);
        // 世界加载完成后使处理仓配方索引失效,首次使用时以完整注册表重建
        //（cells/CraftTweaker 等在 init 之后才完成注册,过早构建会漏配方）
        com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex.markDirty();
    }

    @Mod.EventHandler
    public void serverStopping(net.minecraftforge.fml.common.event.FMLServerStoppingEvent event) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            for (net.minecraft.entity.player.EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                com.github.aeddddd.ae2enhanced.ring.RingManager.discard(player.getUniqueID());
            }
        }
        com.github.aeddddd.ae2enhanced.dimension.rules.PlayerAbilityApplier.clearAll();
        com.github.aeddddd.ae2enhanced.ring.TeleportWhitelist.clear();
        com.github.aeddddd.ae2enhanced.ring.CredentialRecoveryTracker.clear();
        com.github.aeddddd.ae2enhanced.util.ServerTickCounter.clear();
        com.github.aeddddd.ae2enhanced.tile.TileEMCInterface.flushAllPendingOfflineEmc();
        com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.serverStopping();
        RuntimeScopes.stopServer();
        try {
            // 全部超维度仓储会话落盘关闭（WAL 冲刷 + checkpoint），保证关服耐久
            com.github.aeddddd.ae2enhanced.storage.HyperdimensionalStorageManager.closeAll();
        } finally {
            RuntimeScopes.closeServer();
        }
    }

    @Mod.EventHandler
    public void serverStopped(net.minecraftforge.fml.common.event.FMLServerStoppedEvent event) {
        RuntimeScopes.releaseServer();
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
