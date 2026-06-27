package com.github.aeddddd.ae2enhanced.proxy;

import appeng.api.AEApi;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.client.handler.KeyHandlerOmniTool;
import com.github.aeddddd.ae2enhanced.client.model.FluidDropModel;
import com.github.aeddddd.ae2enhanced.client.model.StarlightDropModel;
import com.github.aeddddd.ae2enhanced.client.render.EssentiaPacketModel;
import com.github.aeddddd.ae2enhanced.client.render.RenderBlackHole;
import com.github.aeddddd.ae2enhanced.client.render.RenderComputationCore;
import com.github.aeddddd.ae2enhanced.client.render.SelectionBoxRenderer;
import com.github.aeddddd.ae2enhanced.client.render.ChunkPowerHighlightRenderer;
import com.github.aeddddd.ae2enhanced.client.render.RenderHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.client.render.RenderMicroSingularity;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import com.github.aeddddd.ae2enhanced.item.ItemVirtualParallelCard;
import com.github.aeddddd.ae2enhanced.part.PartStockingBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalExportBus;
import com.github.aeddddd.ae2enhanced.part.PartUniversalImportBus;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import com.github.aeddddd.ae2enhanced.tile.TileMicroSingularity;
import com.github.aeddddd.ae2enhanced.util.fakeitem.FakeItemRegister;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class ClientProxy extends CommonProxy {

    public static final KeyBinding JEI_SEARCH_KEY = new KeyBinding(
            "key.ae2enhanced.jeiSearchTerminal",
            Keyboard.KEY_F,
            "key.categories.ae2enhanced"
    );

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


    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        ClientRegistry.registerKeyBinding(JEI_SEARCH_KEY);
        ClientRegistry.registerKeyBinding(OPEN_OMNI_TERMINAL_KEY);
        ClientRegistry.registerKeyBinding(TOGGLE_MAGNET_KEY);
        KeyHandlerOmniTool.init();
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(new KeyHandlerOmniTool());
        com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard.registerClientEvents();
        MinecraftForge.EVENT_BUS.register(new SelectionBoxRenderer());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.client.hud.PlacementToolHudRenderer());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.client.render.PlacementPreviewRenderer());
        MinecraftForge.EVENT_BUS.register(new ChunkPowerHighlightRenderer());
        MinecraftForge.EVENT_BUS.register(new com.github.aeddddd.ae2enhanced.client.render.BindingLineRenderer());

        ClientRegistry.bindTileEntitySpecialRenderer(TileAssemblyController.class, new RenderBlackHole());
        ClientRegistry.bindTileEntitySpecialRenderer(TileMicroSingularity.class, new RenderMicroSingularity());
        ClientRegistry.bindTileEntitySpecialRenderer(TileHyperdimensionalController.class, new RenderHyperdimensionalController());
        ClientRegistry.bindTileEntitySpecialRenderer(TileComputationCore.class, new RenderComputationCore());
        // E2a：注册 EssentiaDrop 的内置物品渲染器(流体/气体使用标准模型系统)
        if (ItemRegistry.ESSENTIA_DROP != null) {
            try {
                java.lang.reflect.Method initModel = ItemRegistry.ESSENTIA_DROP.getClass().getMethod("initModel");
                initModel.invoke(ItemRegistry.ESSENTIA_DROP);
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to init essentia drop model", e);
            }
        }
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
                        Object gasType = gas.getClass().getMethod("getGas").invoke(gas);
                        if (gasType != null) {
                            int tint = (int) gasType.getClass().getMethod("getTint").invoke(gasType);
                            return tint | 0xFF000000;
                        }
                    }
                } catch (Exception ignored) {
                }
                return -1;
            }, ItemRegistry.GAS_DROP);
        }

    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
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
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        registerBlockItemModel(BlockRegistry.ASSEMBLY_CONTROLLER);
        registerBlockItemModel(BlockRegistry.ASSEMBLY_ME_INTERFACE);
        registerBlockItemModel(BlockRegistry.ASSEMBLY_CASING);
        registerBlockItemModel(BlockRegistry.ASSEMBLY_INNER_WALL);
        registerBlockItemModel(BlockRegistry.ASSEMBLY_STABILIZER);
        registerBlockItemModel(BlockRegistry.MICRO_SINGULARITY);

        registerBlockItemModel(BlockRegistry.HYPERDIMENSIONAL_CONTROLLER);
        registerBlockItemModel(BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE);
        registerBlockItemModel(BlockRegistry.HYPERDIMENSIONAL_CASING);
        registerBlockItemModel(BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE);

        // 第三阶段：超因果计算核心
        registerBlockItemModel(BlockRegistry.COMPUTATION_CORE);
        registerBlockItemModel(BlockRegistry.CONSTANT_TENSOR_FIELD_CASING);
        registerBlockItemModel(BlockRegistry.CONSTANT_SPINOR_FIELD_CASING);
        registerBlockItemModel(BlockRegistry.CAUSAL_ANCHOR_CORE);
        registerBlockItemModel(BlockRegistry.SUPER_CRAFTING_INTERFACE);
        registerBlockItemModel(BlockRegistry.WIRELESS_CHANNEL_TRANSMITTER);
        registerBlockItemModel(BlockRegistry.CHUNK_POWER_NODE);
        registerBlockItemModel(BlockRegistry.COMPRESSED_CHUNK_POWER_NODE);
        if (BlockRegistry.CHUNK_MANA_NODE != null) {
            registerBlockItemModel(BlockRegistry.CHUNK_MANA_NODE);
            registerBlockItemModel(BlockRegistry.COMPRESSED_CHUNK_MANA_NODE);
        }
        registerBlockItemModel(BlockRegistry.CENTRAL_ME_INTERFACE);
        registerBlockItemModel(BlockRegistry.SMART_PATTERN_INTERFACE);
        registerBlockItemModel(BlockRegistry.NETWORK_ACCESS_NODE);
        registerBlockItemModel(BlockRegistry.ADVANCED_ME_COLLECTOR);
        registerBlockItemModel(BlockRegistry.ME_NETWORK_RECYCLER);
        registerBlockItemModel(BlockRegistry.YELLOW_STRIPES_BLOCK_B);
        if (BlockRegistry.EMC_INTERFACE != null) {
            registerBlockItemModel(BlockRegistry.EMC_INTERFACE);
        }

        // 注册升级卡的所有模型 variant
        ModelLoader.registerItemVariants(ItemRegistry.UPGRADE_CARD,
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card", "inventory"),
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_parallel", "inventory"),
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_speed", "inventory"),
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_capacity", "inventory"),
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_upload", "inventory"),
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_efficiency", "inventory"),
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_reserved", "inventory")
        );

        // 新材料物品模型
        registerItemModel(ItemRegistry.CONFORMAL_CHARGE);
        registerItemModel(ItemRegistry.DIFFERENTIAL_FORM_STABILIZER);
        registerItemModel(ItemRegistry.STABLE_SPACETIME_MANIFOLD);
        // EssentiaDrop：使用 CustomMeshDefinition 让所有 damage 值都映射到同一个模型路径
        if (ItemRegistry.ESSENTIA_DROP != null) {
            ModelLoader.setCustomMeshDefinition(ItemRegistry.ESSENTIA_DROP, stack ->
                    new ModelResourceLocation(AE2Enhanced.MOD_ID + ":essentia_drop", "inventory"));
        }

        // 注册 FluidDrop / GasDrop / StarlightDrop 的自定义模型加载器
        ModelLoaderRegistry.registerLoader(new FluidDropModel.Loader());
        ModelLoader.setCustomModelResourceLocation(ItemRegistry.FLUID_DROP, 0,
                new ModelResourceLocation(FluidDropModel.MODEL_LOCATION, "inventory"));

        if (ItemRegistry.STARLIGHT_DROP != null) {
            ModelLoaderRegistry.registerLoader(new StarlightDropModel.Loader());
            ModelLoader.setCustomModelResourceLocation(ItemRegistry.STARLIGHT_DROP, 0,
                    new ModelResourceLocation(StarlightDropModel.MODEL_LOCATION, "inventory"));
        }

        if (ItemRegistry.GAS_DROP != null) {
            try {
                Class<?> loaderClass = Class.forName("com.github.aeddddd.ae2enhanced.client.model.GasDropModel$Loader");
                ModelLoaderRegistry.registerLoader((net.minecraftforge.client.model.ICustomModelLoader) loaderClass.newInstance());
                ModelLoader.setCustomModelResourceLocation(ItemRegistry.GAS_DROP, 0,
                        new ModelResourceLocation(AE2Enhanced.MOD_ID + ":gas_drop", "inventory"));
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to register gas drop model", e);
            }
        }

        // E1a：注册通用输入总线的 Part 模型和物品模型
        if (PartRegistry.PART_UNIVERSAL_IMPORT_BUS != null) {
            AEApi.instance().registries().partModels().registerModels(PartUniversalImportBus.MODELS);
            registerItemModel(PartRegistry.PART_UNIVERSAL_IMPORT_BUS);
        }
        // E1b：注册通用输出总线的 Part 模型和物品模型
        if (PartRegistry.PART_UNIVERSAL_EXPORT_BUS != null) {
            AEApi.instance().registries().partModels().registerModels(PartUniversalExportBus.MODELS);
            registerItemModel(PartRegistry.PART_UNIVERSAL_EXPORT_BUS);
        }

        // E1c：注册 Stocking 总线的 Part 模型和物品模型
        if (PartRegistry.PART_STOCKING_BUS != null) {
            AEApi.instance().registries().partModels().registerModels(PartStockingBus.MODELS);
            registerItemModel(PartRegistry.PART_STOCKING_BUS);
            registerItemModel(ItemRegistry.CHANNEL_RECEIVER_CARD);
        registerItemModel(ItemRegistry.UNIVERSAL_MEMORY_CARD);
        }

        // Omni 无线终端物品模型
        registerItemModel(ItemRegistry.OMNI_WIRELESS_TERMINAL);
        registerItemModel(ItemRegistry.SMART_BLANK_PATTERN);
        registerItemModel(ItemRegistry.SMART_PATTERN);
        registerItemModel(ItemRegistry.ENERGY_DROP);
        if (ItemRegistry.MANA_DROP != null) {
            registerItemModel(ItemRegistry.MANA_DROP);
        }

        // ME 放置工具
        registerItemModel(ItemRegistry.ME_PLACEMENT_TOOL);

        // 虚拟并行卡：单模型，名称颜色按 tier 变化
        if (ItemRegistry.VIRTUAL_PARALLEL_CARD != null) {
            registerItemModel(ItemRegistry.VIRTUAL_PARALLEL_CARD);
        }
        if (ItemRegistry.PERSONAL_DIMENSION != null) {
            registerItemModel(ItemRegistry.PERSONAL_DIMENSION);
        }

        // 先进ME工具：4模式使用 ItemMeshDefinition 动态切换模型
        if (ItemRegistry.ME_OMNI_TOOL != null) {
            ModelLoader.registerItemVariants(ItemRegistry.ME_OMNI_TOOL,
                new ModelResourceLocation(AE2Enhanced.MOD_ID + ":me_omni_tool_basic", "inventory"),
                new ModelResourceLocation(AE2Enhanced.MOD_ID + ":me_omni_tool_mode1", "inventory"),
                new ModelResourceLocation(AE2Enhanced.MOD_ID + ":me_omni_tool_mode2", "inventory"),
                new ModelResourceLocation(AE2Enhanced.MOD_ID + ":me_omni_tool_mode3", "inventory")
            );
            ModelLoader.setCustomMeshDefinition(ItemRegistry.ME_OMNI_TOOL, stack -> {
                int mode = com.github.aeddddd.ae2enhanced.item.ItemAdvancedMEOmniTool.getMode(stack);
                switch (mode) {
                    case 0: return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":me_omni_tool_basic", "inventory");
                    case 1: return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":me_omni_tool_mode1", "inventory");
                    case 2: return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":me_omni_tool_mode2", "inventory");
                    case 3: return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":me_omni_tool_mode3", "inventory");
                    default: return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":me_omni_tool_basic", "inventory");
                }
            });
        }

        // Omni 专用升级卡模型(根据 metadata 动态选择)
        ModelLoader.registerItemVariants(ItemRegistry.OMNI_UPGRADE_CARD,
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":omni_upgrade_card_magnet", "inventory"),
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":omni_upgrade_card_picker", "inventory")
        );
        ModelLoader.setCustomMeshDefinition(ItemRegistry.OMNI_UPGRADE_CARD, stack -> {
            int meta = stack.getMetadata();
            String name = (meta == 0) ? "magnet" : (meta == 1) ? "picker" : "unknown";
            return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":omni_upgrade_card_" + name, "inventory");
        });

        // 使用 ItemMeshDefinition 根据 metadata 动态选择模型
        ModelLoader.setCustomMeshDefinition(ItemRegistry.UPGRADE_CARD, stack -> {
            int meta = stack.getMetadata();
            switch (meta) {
                case ItemUpgradeCard.META_PARALLEL:
                    return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_parallel", "inventory");
                case ItemUpgradeCard.META_SPEED:
                    return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_speed", "inventory");
                case ItemUpgradeCard.META_CAPACITY:
                    return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_capacity", "inventory");
                case ItemUpgradeCard.META_EFFICIENCY:
                    return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_efficiency", "inventory");
                case ItemUpgradeCard.META_RESERVED1:
                    return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_upload", "inventory");
                case ItemUpgradeCard.META_RESERVED2:
                    return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_reserved", "inventory");
                default:
                    return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card", "inventory");
            }
        });
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
