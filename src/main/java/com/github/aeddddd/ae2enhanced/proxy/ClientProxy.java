package com.github.aeddddd.ae2enhanced.proxy;

import appeng.api.AEApi;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.handler.KeyHandlerOmniTool;
import com.github.aeddddd.ae2enhanced.client.model.FluidDropModel;
import com.github.aeddddd.ae2enhanced.client.model.StarlightDropModel;
import com.github.aeddddd.ae2enhanced.client.render.*;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import com.github.aeddddd.ae2enhanced.item.ItemVirtualParallelCard;
import com.github.aeddddd.ae2enhanced.item.fakeitem.FakeItemRegister;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class ClientProxy extends CommonProxy {

    public static final KeyBinding OPEN_OMNI_TERMINAL_KEY = new KeyBinding(
            "key.ae2enhanced.openOmniTerminal",
            KeyConflictContext.IN_GAME,
            KeyModifier.SHIFT,
            Keyboard.KEY_E,
            "key.categories.ae2enhanced"
    );

    public static final KeyBinding TOGGLE_MAGNET_KEY = new KeyBinding(
            "key.ae2enhanced.toggleMagnet",
            KeyConflictContext.IN_GAME,
            Keyboard.KEY_H,
            "key.categories.ae2enhanced"
    );


    @SideOnly(Side.CLIENT)
    private static void initializeContentModels() {
        for (com.github.aeddddd.ae2enhanced.registry.content.ContentDefinition.ItemDefinition definition
                : com.github.aeddddd.ae2enhanced.registry.content.ContentDefinition.ITEMS) {
            Item item = definition.getter.get();
            if (item == null) {
                continue;
            }
            try {
                switch (definition.modelType) {
                    case CONSTRAINED_MICRO_SINGULARITY:
                        item.setTileEntityItemStackRenderer(
                                com.github.aeddddd.ae2enhanced.client.render.ConstrainedSingularityItemRenderer.INSTANCE);
                        break;
                    case ESSENTIA_DROP:
                        item.setTileEntityItemStackRenderer(
                                com.github.aeddddd.ae2enhanced.client.render.EssentiaItemRenderer.INSTANCE);
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to init " + definition.registryName + " model", e);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onClientConnected(net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent event) {
        com.github.aeddddd.ae2enhanced.guide.client.ScriptGuideClient.clear();
        RuntimeScopes.connectClient();
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onClientDisconnected(net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        com.github.aeddddd.ae2enhanced.guide.client.ScriptGuideClient.clear();
        RuntimeScopes.disconnectClient();
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    @Optional.Method(modid = "thaumcraft")
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        // 将 Thaumcraft 的所有 aspect 纹理注册到 texture atlas
        try {
            for (thaumcraft.api.aspects.Aspect aspect : thaumcraft.api.aspects.Aspect.aspects.values()) {
                if (aspect != null && aspect.getImage() != null) {
                    event.getMap().registerSprite(aspect.getImage());
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to register Thaumcraft aspect textures: {}", e.getMessage());
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        if (OPEN_OMNI_TERMINAL_KEY.isPressed()) {
            AE2Enhanced.network.sendToServer(new com.github.aeddddd.ae2enhanced.network.packet.PacketOpenOmniTerminal());
        }
        if (TOGGLE_MAGNET_KEY.isPressed()) {
            AE2Enhanced.network.sendToServer(new com.github.aeddddd.ae2enhanced.network.packet.PacketToggleMagnet());
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        for (com.github.aeddddd.ae2enhanced.registry.content.ContentDefinition.BlockDefinition definition
                : com.github.aeddddd.ae2enhanced.registry.content.ContentDefinition.BLOCKS) {
            Block block = definition.getter.get();
            if (block != null && definition.modelType
                    == com.github.aeddddd.ae2enhanced.registry.content.ContentDefinition.ModelType.BLOCK_ITEM) {
                registerBlockItemModel(block);
            }
        }
        for (com.github.aeddddd.ae2enhanced.registry.content.ContentDefinition.ItemDefinition definition
                : com.github.aeddddd.ae2enhanced.registry.content.ContentDefinition.ITEMS) {
            Item item = definition.getter.get();
            if (item != null) {
                registerItemModel(definition, item);
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private static void registerItemModel(
            com.github.aeddddd.ae2enhanced.registry.content.ContentDefinition.ItemDefinition definition, Item item) {
        ModelResourceLocation[] variants = modelLocations(definition.modelVariants);
        switch (definition.modelType) {
            case SIMPLE:
            case CONSTRAINED_MICRO_SINGULARITY:
                registerItemModel(item);
                break;
            case PART:
                AEApi.instance().registries().partModels().registerModels(definition.partModels.get());
                registerItemModel(item);
                break;
            case UPGRADE_CARD:
                ModelLoader.registerItemVariants(item, variants);
                ModelLoader.setCustomMeshDefinition(item, stack -> upgradeCardModel(stack, variants));
                break;
            case ESSENTIA_DROP:
                ModelLoader.setCustomMeshDefinition(item, stack -> modelLocation(definition.registryName));
                break;
            case FLUID_DROP:
                ModelLoaderRegistry.registerLoader(new FluidDropModel.Loader());
                ModelLoader.setCustomModelResourceLocation(item, 0,
                        new ModelResourceLocation(FluidDropModel.MODEL_LOCATION, "inventory"));
                break;
            case STARLIGHT_DROP:
                ModelLoaderRegistry.registerLoader(new StarlightDropModel.Loader());
                ModelLoader.setCustomModelResourceLocation(item, 0,
                        new ModelResourceLocation(StarlightDropModel.MODEL_LOCATION, "inventory"));
                break;
            case GAS_DROP:
                registerGasDropModel(item);
                break;
            case VIRTUAL_PARALLEL_CARD:
                ModelLoader.registerItemVariants(item, variants);
                ModelLoader.setCustomMeshDefinition(item, stack -> {
                    int tier = ItemVirtualParallelCard.getTier(stack);
                    return variants[tier >= 0 && tier < variants.length ? tier : 0];
                });
                break;
            case OMNI_TOOL:
                ModelLoader.registerItemVariants(item, variants);
                ModelLoader.setCustomMeshDefinition(item, stack -> {
                    int mode = com.github.aeddddd.ae2enhanced.omnitool.ItemAdvancedMEOmniTool.getMode(stack);
                    return variants[mode >= 0 && mode < variants.length ? mode : 0];
                });
                break;
            case OMNI_UPGRADE_CARD:
                ModelLoader.registerItemVariants(item, variants);
                ModelLoader.setCustomMeshDefinition(item, stack -> {
                    int meta = stack.getMetadata();
                    return meta >= 0 && meta < variants.length
                            ? variants[meta] : modelLocation("omni_upgrade_card_unknown");
                });
                break;
            default:
                break;
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onModelBake(ModelBakeEvent event) {
        // 将 essentia_drop 的模型替换为 BakedEssentiaPacketModel,使 isBuiltInRenderer()=true
        // 从而触发 RenderItem 调用 Item 自己的 TileEntityItemStackRenderer
        try {
            net.minecraft.util.registry.IRegistry<ModelResourceLocation, IBakedModel> registry = event.getModelRegistry();
            ModelResourceLocation locationEssentia = new ModelResourceLocation(AE2Enhanced.MOD_ID + ":essentia_drop", "inventory");
            registry.putObject(locationEssentia, new EssentiaPacketModel.BakedEssentiaPacketModel());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to replace essentia_drop model", e);
        }
    }

    @SideOnly(Side.CLIENT)
    private static ModelResourceLocation upgradeCardModel(ItemStack stack, ModelResourceLocation[] variants) {
        switch (stack.getMetadata()) {
            case ItemUpgradeCard.META_PARALLEL:
                return variants[1];
            case ItemUpgradeCard.META_SPEED:
                return variants[2];
            case ItemUpgradeCard.META_CAPACITY:
                return variants[3];
            case ItemUpgradeCard.META_EFFICIENCY:
                return variants[5];
            case ItemUpgradeCard.META_RESERVED1:
                return variants[4];
            case ItemUpgradeCard.META_RESERVED2:
                return variants[6];
            default:
                return variants[0];
        }
    }

    @SideOnly(Side.CLIENT)
    private static void registerGasDropModel(Item item) {
        try {
            Class<?> loaderClass = Class.forName("com.github.aeddddd.ae2enhanced.client.model.GasDropModel$Loader");
            ModelLoaderRegistry.registerLoader((net.minecraftforge.client.model.ICustomModelLoader) loaderClass.newInstance());
            ModelLoader.setCustomModelResourceLocation(item, 0, modelLocation("gas_drop"));
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to register gas drop model", e);
        }
    }

    @SideOnly(Side.CLIENT)
    private static ModelResourceLocation[] modelLocations(java.util.List<String> names) {
        ModelResourceLocation[] result = new ModelResourceLocation[names.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = modelLocation(names.get(i));
        }
        return result;
    }

    @SideOnly(Side.CLIENT)
    private static ModelResourceLocation modelLocation(String name) {
        return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":" + name, "inventory");
    }

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        com.github.aeddddd.ae2enhanced.client.network.ClientPacketHandlerRegistration.register();
        com.github.aeddddd.ae2enhanced.client.dev.PrivateDimensionAcceptanceDriver.register();
        com.github.aeddddd.ae2enhanced.guide.client.GuideAcceptanceDriver.register();
        ClientRegistry.registerKeyBinding(OPEN_OMNI_TERMINAL_KEY);
        ClientRegistry.registerKeyBinding(TOGGLE_MAGNET_KEY);
        ClientRegistry.registerKeyBinding(com.github.aeddddd.ae2enhanced.guide.GuideHotkeyHandler.OPEN_GUIDE_KEY);
        com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.register();
        KeyHandlerOmniTool.init();
        com.github.aeddddd.ae2enhanced.client.handler.KeyHandlerRing.init();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        com.github.aeddddd.ae2enhanced.gui.GuiEndpointRegistry.register(
                Side.CLIENT, new com.github.aeddddd.ae2enhanced.client.gui.GuiClientEndpoint());
        MinecraftForge.EVENT_BUS.register(new KeyHandlerOmniTool());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.client.handler.KeyHandlerRing());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.client.event.PlacementInputHandler());
        com.github.aeddddd.ae2enhanced.client.event.UMCClientEvents.register();
        MinecraftForge.EVENT_BUS.register(new SelectionBoxRenderer());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.client.render.CollectorBoundsRenderer());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.client.hud.PlacementToolHudRenderer());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.client.render.PlacementPreviewRenderer());
        MinecraftForge.EVENT_BUS.register(new ChunkPowerHighlightRenderer());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.client.render.BindingLineRenderer());

        // 停靠引擎开发演示屏：/ae2edockdemo 打开 GuiDockDemo（阶段 1 手工验证入口）
        // TODO(AE2E): 正式发布前移除或加配置开关
        net.minecraftforge.client.ClientCommandHandler.instance
                .registerCommand(new com.github.aeddddd.ae2enhanced.client.gui.dock.DockDemoCommand());

        // 指南系统：资源重载加载页面 + 按住 G 热键
        if (com.github.aeddddd.ae2enhanced.config.ClientPreferences.current().guide.enabled) {
            com.github.aeddddd.ae2enhanced.guide.GuideManager.init();
            MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.guide.GuideHotkeyHandler());
        }

        ClientRegistry.bindTileEntitySpecialRenderer(TileAssemblyController.class, new RenderBlackHole());
        ClientRegistry.bindTileEntitySpecialRenderer(TileMicroSingularity.class, new RenderMicroSingularity());
        ClientRegistry.bindTileEntitySpecialRenderer(
                com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber.class,
                new com.github.aeddddd.ae2enhanced.client.render.RenderTileSingularityChamber());
        ClientRegistry.bindTileEntitySpecialRenderer(TileHyperdimensionalController.class, new RenderHyperdimensionalController());
        ClientRegistry.bindTileEntitySpecialRenderer(TileComputationCore.class, new RenderComputationCore());
        ClientRegistry.bindTileEntitySpecialRenderer(
                com.github.aeddddd.ae2enhanced.tile.TileDisplayPanel.class,
                new com.github.aeddddd.ae2enhanced.client.render.RenderDisplayWall());
        initializeContentModels();
        // E2a：注册流体假物品的 ItemColors,根据流体颜色染色
        if (ItemRegistry.FLUID_DROP != null) {
            Minecraft.getMinecraft().getItemColors().registerItemColorHandler((stack, tintIndex) -> {
                FluidStack fluid = com.github.aeddddd.ae2enhanced.item.ItemFluidDrop.getFluidStack(stack);
                if (fluid != null) {
                    return fluid.getFluid().getColor(fluid);
                }
                return -1;
            }, ItemRegistry.FLUID_DROP);
        }
        // E2a：注册气体假物品的 ItemColors,根据气体 tint 染色(复刻 ae2fc ClientProxy.init)
        if (ItemRegistry.GAS_DROP != null) {
            Minecraft.getMinecraft().getItemColors().registerItemColorHandler((stack, tintIndex) -> {
                try {
                    Object gas = FakeItemRegister.getStack(stack);
                    if (gas != null) {
                        Object gasType = com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry
                                .method(gas.getClass(), "getGas").invoke(gas);
                        if (gasType != null) {
                            int tint = (int) com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry
                                    .method(gasType.getClass(), "getTint").invoke(gasType);
                            return tint | 0xFF000000;
                        }
                    }
                } catch (Exception ignored) {
                }
                return -1;
            }, ItemRegistry.GAS_DROP);
        }

    }

    @SideOnly(Side.CLIENT)
    private static void registerBlockItemModel(Block block) {
        Item item = Item.getItemFromBlock(block);
        if (item != null) {
            ModelLoader.setCustomModelResourceLocation(item, 0,
                new ModelResourceLocation(block.getRegistryName(), "inventory"));
        }
    }

    @SideOnly(Side.CLIENT)
    private static void registerItemModel(Item item) {
        ModelLoader.setCustomModelResourceLocation(item, 0,
            new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }
}
