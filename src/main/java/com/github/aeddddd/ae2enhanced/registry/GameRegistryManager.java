package com.github.aeddddd.ae2enhanced.registry;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.block.*;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.tile.TileAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.item.*;
import com.github.aeddddd.ae2enhanced.registry.content.BlockRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.tile.*;
import com.github.aeddddd.ae2enhanced.crafting.RecipeOmniToolUpgrade;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

/**
 * 游戏注册管理器 —— 统一处理 Block、Item、TileEntity 的注册.
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class GameRegistryManager {

    private GameRegistryManager() {}

    /**
     * 在 preInit 阶段实例化所有 Item(必须在 FakeFluids.init() 等之前调用).
     */
    public static void initItems() {
        ItemRegistry.UPGRADE_CARD = new ItemUpgradeCard();
        ItemRegistry.CONFORMAL_CHARGE = new ItemConformalCharge();
        ItemRegistry.DIFFERENTIAL_FORM_STABILIZER = new ItemDifferentialFormStabilizer();
        ItemRegistry.STABLE_SPACETIME_MANIFOLD = new ItemStableSpacetimeManifold();
        try {
            if (net.minecraftforge.fml.common.Loader.isModLoaded("thaumcraft")) {
                ItemRegistry.ESSENTIA_DROP = (Item) Class.forName("com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop").newInstance();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to instantiate ItemEssentiaDrop", e);
        }
        ItemRegistry.FLUID_DROP = new ItemFluidDrop();
        try {
            if (net.minecraftforge.fml.common.Loader.isModLoaded("mekanism") && net.minecraftforge.fml.common.Loader.isModLoaded("mekeng")) {
                ItemRegistry.GAS_DROP = (Item) Class.forName("com.github.aeddddd.ae2enhanced.item.ItemGasDrop").newInstance();
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to instantiate ItemGasDrop", e);
        }
        PartRegistry.PART_UNIVERSAL_IMPORT_BUS = new ItemPartUniversalImportBus();
        PartRegistry.PART_UNIVERSAL_EXPORT_BUS = new ItemPartUniversalExportBus();
        PartRegistry.PART_STOCKING_BUS = new ItemPartStockingBus();
        ItemRegistry.CHANNEL_RECEIVER_CARD = new ItemChannelReceiverCard();
        ItemRegistry.UNIVERSAL_MEMORY_CARD = new ItemUniversalMemoryCard();
        ItemRegistry.OMNI_WIRELESS_TERMINAL = new ItemOmniWirelessTerminal();
        ItemRegistry.OMNI_UPGRADE_CARD = new ItemOmniUpgradeCard();
        ItemRegistry.SMART_BLANK_PATTERN = new ItemSmartBlankPattern();
        ItemRegistry.SMART_PATTERN = new ItemSmartPattern();
        ItemRegistry.ENERGY_DROP = new ItemEnergyDrop();
        if (net.minecraftforge.fml.common.Loader.isModLoaded("botania")) {
            ItemRegistry.MANA_DROP = new com.github.aeddddd.ae2enhanced.item.ItemManaDrop();
        }
        if (net.minecraftforge.fml.common.Loader.isModLoaded("astralsorcery")) {
            ItemRegistry.STARLIGHT_DROP = new com.github.aeddddd.ae2enhanced.item.ItemStarlightDrop();
        }
        ItemRegistry.ME_OMNI_TOOL = new ItemAdvancedMEOmniTool();
        ItemRegistry.ME_PLACEMENT_TOOL = new ItemMEPlacementTool();
        ItemRegistry.VIRTUAL_PARALLEL_CARD = new ItemVirtualParallelCard();
        ItemRegistry.PERSONAL_DIMENSION = new ItemPersonalDimension();
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(
            BlockRegistry.ASSEMBLY_CONTROLLER = new BlockAssemblyController(),
            BlockRegistry.ASSEMBLY_ME_INTERFACE = new BlockAssemblyMeInterface(),
            BlockRegistry.ASSEMBLY_CASING = new BlockAssemblyCasing(),
            BlockRegistry.ASSEMBLY_INNER_WALL = new BlockAssemblyInnerWall(),
            BlockRegistry.ASSEMBLY_STABILIZER = new BlockAssemblyStabilizer(),
            BlockRegistry.MICRO_SINGULARITY = new BlockMicroSingularity(),
            BlockRegistry.HYPERDIMENSIONAL_CONTROLLER = new BlockHyperdimensionalController(),
            BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE = new BlockHyperdimensionalMeInterface(),
            BlockRegistry.HYPERDIMENSIONAL_CASING = new BlockHyperdimensionalCasing(),
            BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE = new BlockHyperdimensionalSingularityCore(),
            BlockRegistry.COMPUTATION_CORE = new BlockComputationCore(),
            BlockRegistry.CONSTANT_TENSOR_FIELD_CASING = new BlockConstantTensorFieldCasing(),
            BlockRegistry.CONSTANT_SPINOR_FIELD_CASING = new BlockConstantSpinorFieldCasing(),
            BlockRegistry.CAUSAL_ANCHOR_CORE = new BlockCausalAnchorCore(),
            BlockRegistry.SUPER_CRAFTING_INTERFACE = new BlockSuperCraftingInterface(),
            BlockRegistry.WIRELESS_CHANNEL_TRANSMITTER = new BlockWirelessChannelTransmitter(),
            BlockRegistry.CENTRAL_ME_INTERFACE = new BlockCentralMEInterface(),
            BlockRegistry.SMART_PATTERN_INTERFACE = new BlockSmartPatternInterface(),
            BlockRegistry.NETWORK_ACCESS_NODE = new BlockNetworkAccessNode(),
            BlockRegistry.CHUNK_POWER_NODE = new BlockChunkPowerNode(),
            BlockRegistry.COMPRESSED_CHUNK_POWER_NODE = new BlockCompressedChunkPowerNode(),
            BlockRegistry.ADVANCED_PLATFORM_CONTROLLER = new BlockAdvancedPlatformController(),
            BlockRegistry.ADVANCED_ME_COLLECTOR = new BlockAdvancedMECollector(),
            BlockRegistry.ME_NETWORK_RECYCLER = new BlockMENetworkRecycler(),
            BlockRegistry.YELLOW_STRIPES_BLOCK_B = new BlockYellowStripesBlockB()
        );
        if (AE2EnhancedConfig.emcInterface.enabled) {
            BlockRegistry.EMC_INTERFACE = new BlockEMCInterface();
            event.getRegistry().register(BlockRegistry.EMC_INTERFACE);
        }

        GameRegistry.registerTileEntity(TileAssemblyController.class, AE2Enhanced.MOD_ID + ":assembly_controller");
        // 占位：旧存档中的 advanced_platform_controller TileEntity 会被替换为空实现
        GameRegistry.registerTileEntity(com.github.aeddddd.ae2enhanced.tile.TileAdvancedPlatformController.class, AE2Enhanced.MOD_ID + ":advanced_platform_controller");
        GameRegistry.registerTileEntity(TileAssemblyMeInterface.class, AE2Enhanced.MOD_ID + ":assembly_me_interface");
        GameRegistry.registerTileEntity(TileMicroSingularity.class, AE2Enhanced.MOD_ID + ":micro_singularity");
        GameRegistry.registerTileEntity(TileHyperdimensionalController.class, AE2Enhanced.MOD_ID + ":hyperdimensional_controller");
        GameRegistry.registerTileEntity(TileHyperdimensionalMeInterface.class, AE2Enhanced.MOD_ID + ":hyperdimensional_me_interface");
        GameRegistry.registerTileEntity(TileComputationCore.class, AE2Enhanced.MOD_ID + ":computation_core");
        GameRegistry.registerTileEntity(TileSuperCraftingInterface.class, AE2Enhanced.MOD_ID + ":super_crafting_interface");
        GameRegistry.registerTileEntity(TileWirelessChannelTransmitter.class, AE2Enhanced.MOD_ID + ":wireless_channel_transmitter");
        GameRegistry.registerTileEntity(TileCentralMEInterface.class, AE2Enhanced.MOD_ID + ":central_me_interface");
        GameRegistry.registerTileEntity(TileSmartPatternInterface.class, AE2Enhanced.MOD_ID + ":smart_pattern_interface");
        GameRegistry.registerTileEntity(TileNetworkAccessNode.class, AE2Enhanced.MOD_ID + ":network_access_node");
        GameRegistry.registerTileEntity(TileChunkPowerNode.class, AE2Enhanced.MOD_ID + ":chunk_power_node");
        GameRegistry.registerTileEntity(TileCompressedChunkPowerNode.class, AE2Enhanced.MOD_ID + ":compressed_chunk_power_node");
        if (Loader.isModLoaded("botania")) {
            BlockRegistry.CHUNK_MANA_NODE = new BlockChunkManaNode();
            BlockRegistry.COMPRESSED_CHUNK_MANA_NODE = new BlockCompressedChunkManaNode();
            event.getRegistry().registerAll(
                BlockRegistry.CHUNK_MANA_NODE,
                BlockRegistry.COMPRESSED_CHUNK_MANA_NODE
            );
            GameRegistry.registerTileEntity(TileChunkManaNode.class, AE2Enhanced.MOD_ID + ":chunk_mana_node");
            GameRegistry.registerTileEntity(TileCompressedChunkManaNode.class, AE2Enhanced.MOD_ID + ":compressed_chunk_mana_node");
        }
        GameRegistry.registerTileEntity(TileAdvancedMECollector.class, AE2Enhanced.MOD_ID + ":advanced_me_collector");
        GameRegistry.registerTileEntity(TileMENetworkRecycler.class, AE2Enhanced.MOD_ID + ":me_network_recycler");
        if (BlockRegistry.EMC_INTERFACE != null) {
            GameRegistry.registerTileEntity(TileEMCInterface.class, AE2Enhanced.MOD_ID + ":emc_interface");
        }
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        // Block items
        event.getRegistry().registerAll(
            new ItemBlock(BlockRegistry.ASSEMBLY_CONTROLLER).setRegistryName(BlockRegistry.ASSEMBLY_CONTROLLER.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.ASSEMBLY_ME_INTERFACE).setRegistryName(BlockRegistry.ASSEMBLY_ME_INTERFACE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.ASSEMBLY_CASING).setRegistryName(BlockRegistry.ASSEMBLY_CASING.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.ASSEMBLY_INNER_WALL).setRegistryName(BlockRegistry.ASSEMBLY_INNER_WALL.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.ASSEMBLY_STABILIZER).setRegistryName(BlockRegistry.ASSEMBLY_STABILIZER.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlockMicroSingularity(BlockRegistry.MICRO_SINGULARITY).setRegistryName(BlockRegistry.MICRO_SINGULARITY.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.HYPERDIMENSIONAL_CONTROLLER).setRegistryName(BlockRegistry.HYPERDIMENSIONAL_CONTROLLER.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE).setRegistryName(BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.HYPERDIMENSIONAL_CASING).setRegistryName(BlockRegistry.HYPERDIMENSIONAL_CASING.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE).setRegistryName(BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.COMPUTATION_CORE).setRegistryName(BlockRegistry.COMPUTATION_CORE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.CONSTANT_TENSOR_FIELD_CASING).setRegistryName(BlockRegistry.CONSTANT_TENSOR_FIELD_CASING.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.CONSTANT_SPINOR_FIELD_CASING).setRegistryName(BlockRegistry.CONSTANT_SPINOR_FIELD_CASING.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.CAUSAL_ANCHOR_CORE).setRegistryName(BlockRegistry.CAUSAL_ANCHOR_CORE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.SUPER_CRAFTING_INTERFACE).setRegistryName(BlockRegistry.SUPER_CRAFTING_INTERFACE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.WIRELESS_CHANNEL_TRANSMITTER).setRegistryName(BlockRegistry.WIRELESS_CHANNEL_TRANSMITTER.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.CENTRAL_ME_INTERFACE).setRegistryName(BlockRegistry.CENTRAL_ME_INTERFACE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.SMART_PATTERN_INTERFACE).setRegistryName(BlockRegistry.SMART_PATTERN_INTERFACE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.NETWORK_ACCESS_NODE).setRegistryName(BlockRegistry.NETWORK_ACCESS_NODE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.CHUNK_POWER_NODE).setRegistryName(BlockRegistry.CHUNK_POWER_NODE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.COMPRESSED_CHUNK_POWER_NODE).setRegistryName(BlockRegistry.COMPRESSED_CHUNK_POWER_NODE.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.ADVANCED_PLATFORM_CONTROLLER).setRegistryName(BlockRegistry.ADVANCED_PLATFORM_CONTROLLER.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.ADVANCED_ME_COLLECTOR).setRegistryName(BlockRegistry.ADVANCED_ME_COLLECTOR.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.ME_NETWORK_RECYCLER).setRegistryName(BlockRegistry.ME_NETWORK_RECYCLER.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB),
            new ItemBlock(BlockRegistry.YELLOW_STRIPES_BLOCK_B).setRegistryName(BlockRegistry.YELLOW_STRIPES_BLOCK_B.getRegistryName()).setCreativeTab(AE2Enhanced.CREATIVE_TAB)
        );
        if (Loader.isModLoaded("botania") && BlockRegistry.CHUNK_MANA_NODE != null) {
            event.getRegistry().register(new ItemBlock(BlockRegistry.CHUNK_MANA_NODE)
                    .setRegistryName(BlockRegistry.CHUNK_MANA_NODE.getRegistryName())
                    .setCreativeTab(AE2Enhanced.CREATIVE_TAB));
            event.getRegistry().register(new ItemBlock(BlockRegistry.COMPRESSED_CHUNK_MANA_NODE)
                    .setRegistryName(BlockRegistry.COMPRESSED_CHUNK_MANA_NODE.getRegistryName())
                    .setCreativeTab(AE2Enhanced.CREATIVE_TAB));
        }
        if (BlockRegistry.EMC_INTERFACE != null) {
            event.getRegistry().register(new ItemBlock(BlockRegistry.EMC_INTERFACE)
                    .setRegistryName(BlockRegistry.EMC_INTERFACE.getRegistryName())
                    .setCreativeTab(AE2Enhanced.CREATIVE_TAB));
        }

        // Items
        event.getRegistry().register(ItemRegistry.UPGRADE_CARD);
        event.getRegistry().register(ItemRegistry.CONFORMAL_CHARGE);
        event.getRegistry().register(ItemRegistry.DIFFERENTIAL_FORM_STABILIZER);
        event.getRegistry().register(ItemRegistry.STABLE_SPACETIME_MANIFOLD);
        if (ItemRegistry.ESSENTIA_DROP != null) {
            event.getRegistry().register(ItemRegistry.ESSENTIA_DROP);
        }
        event.getRegistry().register(ItemRegistry.FLUID_DROP);
        if (ItemRegistry.GAS_DROP != null) {
            event.getRegistry().register(ItemRegistry.GAS_DROP);
        }
        event.getRegistry().register(PartRegistry.PART_UNIVERSAL_IMPORT_BUS);
        event.getRegistry().register(PartRegistry.PART_UNIVERSAL_EXPORT_BUS);
        event.getRegistry().register(PartRegistry.PART_STOCKING_BUS);
        event.getRegistry().register(ItemRegistry.CHANNEL_RECEIVER_CARD);
        event.getRegistry().register(ItemRegistry.UNIVERSAL_MEMORY_CARD);
        event.getRegistry().register(ItemRegistry.OMNI_WIRELESS_TERMINAL);
        event.getRegistry().register(ItemRegistry.OMNI_UPGRADE_CARD);
        event.getRegistry().register(ItemRegistry.SMART_BLANK_PATTERN);
        event.getRegistry().register(ItemRegistry.SMART_PATTERN);
        event.getRegistry().register(ItemRegistry.ENERGY_DROP);
        if (ItemRegistry.MANA_DROP != null) {
            event.getRegistry().register(ItemRegistry.MANA_DROP);
        }
        if (ItemRegistry.STARLIGHT_DROP != null) {
            event.getRegistry().register(ItemRegistry.STARLIGHT_DROP);
        }
        event.getRegistry().register(ItemRegistry.ME_OMNI_TOOL);
        event.getRegistry().register(ItemRegistry.ME_PLACEMENT_TOOL);
        event.getRegistry().register(ItemRegistry.VIRTUAL_PARALLEL_CARD);
        event.getRegistry().register(ItemRegistry.PERSONAL_DIMENSION);
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<net.minecraft.item.crafting.IRecipe> event) {
        // ME Omni Tool - Chaos Core upgrade
        if (Loader.isModLoaded("draconicevolution") && com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.omniTool.enableChaosCoreUpgrade) {
            Item chaoticCore = Item.REGISTRY.getObject(new ResourceLocation("draconicevolution", "chaotic_core"));
            if (chaoticCore != null) {
                event.getRegistry().register(new RecipeOmniToolUpgrade(
                        new ResourceLocation(AE2Enhanced.MOD_ID, "omni_tool_chaos_upgrade"),
                        new ItemStack(ItemRegistry.ME_OMNI_TOOL),
                        "chaos",
                        new ItemStack(ItemRegistry.ME_OMNI_TOOL),
                        new ItemStack(chaoticCore)
                ).setRegistryName(AE2Enhanced.MOD_ID, "omni_tool_chaos_upgrade"));
            }
        }

        // ME Omni Tool - Enchanted Book upgrade（任意附魔书）
        event.getRegistry().register(new RecipeOmniToolUpgrade(
                new ResourceLocation(AE2Enhanced.MOD_ID, "omni_tool_enchanted_book_upgrade"),
                new ItemStack(ItemRegistry.ME_OMNI_TOOL),
                "enchanted_book",
                new ItemStack(ItemRegistry.ME_OMNI_TOOL),
                new ItemStack(Items.ENCHANTED_BOOK)
        ).setRegistryName(AE2Enhanced.MOD_ID, "omni_tool_enchanted_book_upgrade"));

        // ME Omni Tool - Bedrock Breaker upgrade
        if (AE2EnhancedConfig.omniTool.enableBedrockBreakerUpgrade) {
            Item bedrockItem = Item.getItemFromBlock(Blocks.BEDROCK);
            if (bedrockItem != null && bedrockItem != Items.AIR) {
                event.getRegistry().register(new RecipeOmniToolUpgrade(
                        new ResourceLocation(AE2Enhanced.MOD_ID, "omni_tool_bedrock_upgrade"),
                        new ItemStack(ItemRegistry.ME_OMNI_TOOL),
                        "bedrock",
                        new ItemStack(ItemRegistry.ME_OMNI_TOOL),
                        new ItemStack(bedrockItem)
                ).setRegistryName(AE2Enhanced.MOD_ID, "omni_tool_bedrock_upgrade"));
            }
        }
    }

    @SubscribeEvent
    public static void onMissingBlockMappings(RegistryEvent.MissingMappings<Block> event) {
        Block target = BlockRegistry.NETWORK_ACCESS_NODE;
        if (target == null) return;
        for (RegistryEvent.MissingMappings.Mapping<Block> mapping : event.getAllMappings()) {
            ResourceLocation key = mapping.key;
            if (!AE2Enhanced.MOD_ID.equals(key.getNamespace())) continue;
            String path = key.getPath();
            if ("rf_access_node".equals(path) || "mana_access_node".equals(path) || "starlight_access_node".equals(path)) {
                mapping.remap(target);
            }
        }
    }

    @SubscribeEvent
    public static void onMissingItemMappings(RegistryEvent.MissingMappings<Item> event) {
        Item target = Item.getItemFromBlock(BlockRegistry.NETWORK_ACCESS_NODE);
        if (target == null || target == Items.AIR) return;
        for (RegistryEvent.MissingMappings.Mapping<Item> mapping : event.getAllMappings()) {
            ResourceLocation key = mapping.key;
            if (!AE2Enhanced.MOD_ID.equals(key.getNamespace())) continue;
            String path = key.getPath();
            if ("rf_access_node".equals(path) || "mana_access_node".equals(path) || "starlight_access_node".equals(path)) {
                mapping.remap(target);
            }
        }
    }
}
