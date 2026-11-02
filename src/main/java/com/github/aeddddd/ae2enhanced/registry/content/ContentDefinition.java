package com.github.aeddddd.ae2enhanced.registry.content;

import com.github.aeddddd.ae2enhanced.block.*;
import com.github.aeddddd.ae2enhanced.collector.BlockAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.collector.TileAdvancedMECollector;
import com.github.aeddddd.ae2enhanced.dimension.ItemPersonalDimension;
import com.github.aeddddd.ae2enhanced.item.*;
import com.github.aeddddd.ae2enhanced.omnitool.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.ItemMEPlacementTool;
import com.github.aeddddd.ae2enhanced.part.*;
import com.github.aeddddd.ae2enhanced.recycler.BlockMENetworkRecycler;
import com.github.aeddddd.ae2enhanced.recycler.TileMENetworkRecycler;
import com.github.aeddddd.ae2enhanced.ring.ItemConstrainedMicroSingularity;
import com.github.aeddddd.ae2enhanced.ring.ItemNetworkLinkCredential;
import com.github.aeddddd.ae2enhanced.tile.*;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 内容注册与模型元数据的唯一声明；注册事件和客户端代理只执行这些定义。 */
public final class ContentDefinition {

    public static final List<BlockDefinition> BLOCKS = Collections.unmodifiableList(Arrays.asList(
            block("assembly_controller", BlockAssemblyController::new, v -> BlockRegistry.ASSEMBLY_CONTROLLER = (BlockAssemblyController) v, () -> BlockRegistry.ASSEMBLY_CONTROLLER),
            block("assembly_me_interface", BlockAssemblyMeInterface::new, v -> BlockRegistry.ASSEMBLY_ME_INTERFACE = (BlockAssemblyMeInterface) v, () -> BlockRegistry.ASSEMBLY_ME_INTERFACE),
            block("assembly_casing", BlockAssemblyCasing::new, v -> BlockRegistry.ASSEMBLY_CASING = (BlockAssemblyCasing) v, () -> BlockRegistry.ASSEMBLY_CASING),
            block("assembly_inner_wall", BlockAssemblyInnerWall::new, v -> BlockRegistry.ASSEMBLY_INNER_WALL = (BlockAssemblyInnerWall) v, () -> BlockRegistry.ASSEMBLY_INNER_WALL),
            block("assembly_stabilizer", BlockAssemblyStabilizer::new, v -> BlockRegistry.ASSEMBLY_STABILIZER = (BlockAssemblyStabilizer) v, () -> BlockRegistry.ASSEMBLY_STABILIZER),
            block("micro_singularity", BlockMicroSingularity::new, v -> BlockRegistry.MICRO_SINGULARITY = (BlockMicroSingularity) v, () -> BlockRegistry.MICRO_SINGULARITY, Condition.ALWAYS, false),
            block("hyperdimensional_controller", BlockHyperdimensionalController::new, v -> BlockRegistry.HYPERDIMENSIONAL_CONTROLLER = (BlockHyperdimensionalController) v, () -> BlockRegistry.HYPERDIMENSIONAL_CONTROLLER),
            block("hyperdimensional_me_interface", BlockHyperdimensionalMeInterface::new, v -> BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE = (BlockHyperdimensionalMeInterface) v, () -> BlockRegistry.HYPERDIMENSIONAL_ME_INTERFACE),
            block("hyperdimensional_casing", BlockHyperdimensionalCasing::new, v -> BlockRegistry.HYPERDIMENSIONAL_CASING = (BlockHyperdimensionalCasing) v, () -> BlockRegistry.HYPERDIMENSIONAL_CASING),
            block("hyperdimensional_singularity_core", BlockHyperdimensionalSingularityCore::new, v -> BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE = (BlockHyperdimensionalSingularityCore) v, () -> BlockRegistry.HYPERDIMENSIONAL_SINGULARITY_CORE),
            block("computation_core", BlockComputationCore::new, v -> BlockRegistry.COMPUTATION_CORE = (BlockComputationCore) v, () -> BlockRegistry.COMPUTATION_CORE),
            block("constant_tensor_field_casing", BlockConstantTensorFieldCasing::new, v -> BlockRegistry.CONSTANT_TENSOR_FIELD_CASING = (BlockConstantTensorFieldCasing) v, () -> BlockRegistry.CONSTANT_TENSOR_FIELD_CASING),
            block("constant_spinor_field_casing", BlockConstantSpinorFieldCasing::new, v -> BlockRegistry.CONSTANT_SPINOR_FIELD_CASING = (BlockConstantSpinorFieldCasing) v, () -> BlockRegistry.CONSTANT_SPINOR_FIELD_CASING),
            block("causal_anchor_core", BlockCausalAnchorCore::new, v -> BlockRegistry.CAUSAL_ANCHOR_CORE = (BlockCausalAnchorCore) v, () -> BlockRegistry.CAUSAL_ANCHOR_CORE),
            block("super_crafting_interface", BlockSuperCraftingInterface::new, v -> BlockRegistry.SUPER_CRAFTING_INTERFACE = (BlockSuperCraftingInterface) v, () -> BlockRegistry.SUPER_CRAFTING_INTERFACE),
            block("wireless_channel_transmitter", BlockWirelessChannelTransmitter::new, v -> BlockRegistry.WIRELESS_CHANNEL_TRANSMITTER = (BlockWirelessChannelTransmitter) v, () -> BlockRegistry.WIRELESS_CHANNEL_TRANSMITTER),
            block("central_me_interface", BlockCentralMEInterface::new, v -> BlockRegistry.CENTRAL_ME_INTERFACE = (BlockCentralMEInterface) v, () -> BlockRegistry.CENTRAL_ME_INTERFACE),
            block("smart_pattern_interface", BlockSmartPatternInterface::new, v -> BlockRegistry.SMART_PATTERN_INTERFACE = (BlockSmartPatternInterface) v, () -> BlockRegistry.SMART_PATTERN_INTERFACE),
            block("network_access_node", BlockNetworkAccessNode::new, v -> BlockRegistry.NETWORK_ACCESS_NODE = (BlockNetworkAccessNode) v, () -> BlockRegistry.NETWORK_ACCESS_NODE),
            block("chunk_power_node", BlockChunkPowerNode::new, v -> BlockRegistry.CHUNK_POWER_NODE = (BlockChunkPowerNode) v, () -> BlockRegistry.CHUNK_POWER_NODE),
            block("compressed_chunk_power_node", BlockCompressedChunkPowerNode::new, v -> BlockRegistry.COMPRESSED_CHUNK_POWER_NODE = (BlockCompressedChunkPowerNode) v, () -> BlockRegistry.COMPRESSED_CHUNK_POWER_NODE),
            block("advanced_me_collector", BlockAdvancedMECollector::new, v -> BlockRegistry.ADVANCED_ME_COLLECTOR = (BlockAdvancedMECollector) v, () -> BlockRegistry.ADVANCED_ME_COLLECTOR),
            block("me_network_recycler", BlockMENetworkRecycler::new, v -> BlockRegistry.ME_NETWORK_RECYCLER = (BlockMENetworkRecycler) v, () -> BlockRegistry.ME_NETWORK_RECYCLER),
            block("yellow_stripes_block_b", BlockYellowStripesBlockB::new, v -> BlockRegistry.YELLOW_STRIPES_BLOCK_B = (BlockYellowStripesBlockB) v, () -> BlockRegistry.YELLOW_STRIPES_BLOCK_B),
            block("display_panel", BlockDisplayPanel::new, v -> BlockRegistry.DISPLAY_PANEL = (BlockDisplayPanel) v, () -> BlockRegistry.DISPLAY_PANEL),
            block("display_frame_dark", () -> new BlockDisplayFrame(false), v -> BlockRegistry.DISPLAY_FRAME_DARK = (BlockDisplayFrame) v, () -> BlockRegistry.DISPLAY_FRAME_DARK),
            block("display_frame_light", () -> new BlockDisplayFrame(true), v -> BlockRegistry.DISPLAY_FRAME_LIGHT = (BlockDisplayFrame) v, () -> BlockRegistry.DISPLAY_FRAME_LIGHT),
            block("singularity_chamber", BlockSingularityChamber::new, v -> BlockRegistry.SINGULARITY_CHAMBER = (BlockSingularityChamber) v, () -> BlockRegistry.SINGULARITY_CHAMBER),
            block("emc_interface", BlockEMCInterface::new, v -> BlockRegistry.EMC_INTERFACE = (BlockEMCInterface) v, () -> BlockRegistry.EMC_INTERFACE, Condition.EMC_INTERFACE, true),
            block("chunk_mana_node", BlockChunkManaNode::new, v -> BlockRegistry.CHUNK_MANA_NODE = (BlockChunkManaNode) v, () -> BlockRegistry.CHUNK_MANA_NODE, Condition.BOTANIA, true),
            block("compressed_chunk_mana_node", BlockCompressedChunkManaNode::new, v -> BlockRegistry.COMPRESSED_CHUNK_MANA_NODE = (BlockCompressedChunkManaNode) v, () -> BlockRegistry.COMPRESSED_CHUNK_MANA_NODE, Condition.BOTANIA, true)
    ));
    public static final List<ItemDefinition> ITEMS = Collections.unmodifiableList(Arrays.asList(
            item("upgrade_card", ItemUpgradeCard::new, v -> ItemRegistry.UPGRADE_CARD = (ItemUpgradeCard) v, () -> ItemRegistry.UPGRADE_CARD, Condition.ALWAYS, ModelType.UPGRADE_CARD,
                    "upgrade_card", "upgrade_card_parallel", "upgrade_card_speed", "upgrade_card_capacity", "upgrade_card_upload", "upgrade_card_efficiency", "upgrade_card_reserved"),
            simple("conformal_invariant_charge", ItemConformalCharge::new, v -> ItemRegistry.CONFORMAL_CHARGE = (ItemConformalCharge) v, () -> ItemRegistry.CONFORMAL_CHARGE),
            simple("differential_form_stabilizer", ItemDifferentialFormStabilizer::new, v -> ItemRegistry.DIFFERENTIAL_FORM_STABILIZER = (ItemDifferentialFormStabilizer) v, () -> ItemRegistry.DIFFERENTIAL_FORM_STABILIZER),
            simple("stable_spacetime_manifold", ItemStableSpacetimeManifold::new, v -> ItemRegistry.STABLE_SPACETIME_MANIFOLD = (ItemStableSpacetimeManifold) v, () -> ItemRegistry.STABLE_SPACETIME_MANIFOLD),
            item("essentia_drop", reflected("com.github.aeddddd.ae2enhanced.item.ItemEssentiaDrop"), v -> ItemRegistry.ESSENTIA_DROP = v, () -> ItemRegistry.ESSENTIA_DROP, Condition.THAUMCRAFT, ModelType.ESSENTIA_DROP),
            item("fluid_drop", ItemFluidDrop::new, v -> ItemRegistry.FLUID_DROP = (ItemFluidDrop) v, () -> ItemRegistry.FLUID_DROP, Condition.ALWAYS, ModelType.FLUID_DROP),
            item("gas_drop", reflected("com.github.aeddddd.ae2enhanced.item.ItemGasDrop"), v -> ItemRegistry.GAS_DROP = v, () -> ItemRegistry.GAS_DROP, Condition.GAS, ModelType.GAS_DROP),
            part("part_universal_import_bus", ItemPartUniversalImportBus::new, v -> PartRegistry.PART_UNIVERSAL_IMPORT_BUS = (ItemPartUniversalImportBus) v, () -> PartRegistry.PART_UNIVERSAL_IMPORT_BUS, () -> PartUniversalImportBus.MODELS),
            part("part_universal_export_bus", ItemPartUniversalExportBus::new, v -> PartRegistry.PART_UNIVERSAL_EXPORT_BUS = (ItemPartUniversalExportBus) v, () -> PartRegistry.PART_UNIVERSAL_EXPORT_BUS, () -> PartUniversalExportBus.MODELS),
            part("part_stocking_bus", ItemPartStockingBus::new, v -> PartRegistry.PART_STOCKING_BUS = (ItemPartStockingBus) v, () -> PartRegistry.PART_STOCKING_BUS, () -> PartStockingBus.MODELS),
            part("part_energy_storage_bus", ItemPartEnergyStorageBus::new, v -> PartRegistry.PART_ENERGY_STORAGE_BUS = (ItemPartEnergyStorageBus) v, () -> PartRegistry.PART_ENERGY_STORAGE_BUS, () -> PartEnergyStorageBus.MODELS),
            simple("channel_receiver_card", ItemChannelReceiverCard::new, v -> ItemRegistry.CHANNEL_RECEIVER_CARD = (ItemChannelReceiverCard) v, () -> ItemRegistry.CHANNEL_RECEIVER_CARD),
            simple("universal_memory_card", ItemUniversalMemoryCard::new, v -> ItemRegistry.UNIVERSAL_MEMORY_CARD = (ItemUniversalMemoryCard) v, () -> ItemRegistry.UNIVERSAL_MEMORY_CARD),
            simple("omni_wireless_terminal", ItemOmniWirelessTerminal::new, v -> ItemRegistry.OMNI_WIRELESS_TERMINAL = (ItemOmniWirelessTerminal) v, () -> ItemRegistry.OMNI_WIRELESS_TERMINAL),
            simple("omni_wireless_terminal_ii", ItemOmniWirelessTerminalV2::new, v -> ItemRegistry.OMNI_WIRELESS_TERMINAL_II = (ItemOmniWirelessTerminalV2) v, () -> ItemRegistry.OMNI_WIRELESS_TERMINAL_II),
            item("omni_upgrade_card", ItemOmniUpgradeCard::new, v -> ItemRegistry.OMNI_UPGRADE_CARD = (ItemOmniUpgradeCard) v, () -> ItemRegistry.OMNI_UPGRADE_CARD, Condition.ALWAYS, ModelType.OMNI_UPGRADE_CARD,
                    "omni_upgrade_card_magnet", "omni_upgrade_card_picker"),
            simple("smart_blank_pattern", ItemSmartBlankPattern::new, v -> ItemRegistry.SMART_BLANK_PATTERN = (ItemSmartBlankPattern) v, () -> ItemRegistry.SMART_BLANK_PATTERN),
            simple("smart_pattern", ItemSmartPattern::new, v -> ItemRegistry.SMART_PATTERN = (ItemSmartPattern) v, () -> ItemRegistry.SMART_PATTERN),
            simple("energy_drop", ItemEnergyDrop::new, v -> ItemRegistry.ENERGY_DROP = (ItemEnergyDrop) v, () -> ItemRegistry.ENERGY_DROP),
            simple("mana_drop", ItemManaDrop::new, v -> ItemRegistry.MANA_DROP = v, () -> ItemRegistry.MANA_DROP, Condition.BOTANIA),
            simple("starlight_drop", ItemStarlightDrop::new, v -> ItemRegistry.STARLIGHT_DROP = v, () -> ItemRegistry.STARLIGHT_DROP, Condition.ASTRAL_SORCERY, ModelType.STARLIGHT_DROP),
            item("me_omni_tool", ItemAdvancedMEOmniTool::new, v -> ItemRegistry.ME_OMNI_TOOL = (ItemAdvancedMEOmniTool) v, () -> ItemRegistry.ME_OMNI_TOOL, Condition.ALWAYS, ModelType.OMNI_TOOL,
                    "me_omni_tool_basic", "me_omni_tool_mode1", "me_omni_tool_mode2", "me_omni_tool_mode3"),
            simple("network_link_credential", ItemNetworkLinkCredential::new, v -> ItemRegistry.NETWORK_LINK_CREDENTIAL = (ItemNetworkLinkCredential) v, () -> ItemRegistry.NETWORK_LINK_CREDENTIAL),
            simple("me_placement_tool", ItemMEPlacementTool::new, v -> ItemRegistry.ME_PLACEMENT_TOOL = (ItemMEPlacementTool) v, () -> ItemRegistry.ME_PLACEMENT_TOOL),
            item("virtual_parallel_card", ItemVirtualParallelCard::new, v -> ItemRegistry.VIRTUAL_PARALLEL_CARD = (ItemVirtualParallelCard) v, () -> ItemRegistry.VIRTUAL_PARALLEL_CARD, Condition.ALWAYS, ModelType.VIRTUAL_PARALLEL_CARD,
                    "virtual_parallel_card_1", "virtual_parallel_card_2", "virtual_parallel_card_3", "virtual_parallel_card_4", "virtual_parallel_card_5", "virtual_parallel_card_6", "virtual_parallel_card_7", "virtual_parallel_card_8"),
            simple("singularity_constrictor", ItemSingularityConstrictor::new, v -> ItemRegistry.SINGULARITY_CONSTRICTOR = (ItemSingularityConstrictor) v, () -> ItemRegistry.SINGULARITY_CONSTRICTOR),
            item("constrained_micro_singularity", ItemConstrainedMicroSingularity::new, v -> ItemRegistry.CONSTRAINED_MICRO_SINGULARITY = (ItemConstrainedMicroSingularity) v, () -> ItemRegistry.CONSTRAINED_MICRO_SINGULARITY, Condition.ALWAYS, ModelType.CONSTRAINED_MICRO_SINGULARITY),
            simple("personal_dimension", ItemPersonalDimension::new, v -> ItemRegistry.PERSONAL_DIMENSION = (ItemPersonalDimension) v, () -> ItemRegistry.PERSONAL_DIMENSION),
            simple("guide_book", com.github.aeddddd.ae2enhanced.guide.item.ItemGuideBook::new, v -> ItemRegistry.GUIDE_BOOK = (com.github.aeddddd.ae2enhanced.guide.item.ItemGuideBook) v, () -> ItemRegistry.GUIDE_BOOK)
    ));
    public static final List<TileDefinition> TILES = Collections.unmodifiableList(Arrays.asList(
            tile("assembly_controller", TileAssemblyController.class),
            tile("assembly_me_interface", TileAssemblyMeInterface.class),
            tile("micro_singularity", TileMicroSingularity.class),
            tile("hyperdimensional_controller", TileHyperdimensionalController.class),
            tile("hyperdimensional_me_interface", TileHyperdimensionalMeInterface.class),
            tile("computation_core", TileComputationCore.class),
            tile("super_crafting_interface", TileSuperCraftingInterface.class),
            tile("wireless_channel_transmitter", TileWirelessChannelTransmitter.class),
            tile("central_me_interface", TileCentralMEInterface.class),
            tile("smart_pattern_interface", TileSmartPatternInterface.class),
            tile("network_access_node", TileNetworkAccessNode.class),
            tile("chunk_power_node", TileChunkPowerNode.class),
            tile("compressed_chunk_power_node", TileCompressedChunkPowerNode.class),
            tile("chunk_mana_node", TileChunkManaNode.class, Condition.BOTANIA, () -> BlockRegistry.CHUNK_MANA_NODE),
            tile("compressed_chunk_mana_node", TileCompressedChunkManaNode.class, Condition.BOTANIA, () -> BlockRegistry.COMPRESSED_CHUNK_MANA_NODE),
            tile("advanced_me_collector", TileAdvancedMECollector.class),
            tile("me_network_recycler", TileMENetworkRecycler.class),
            tile("display_panel", TileDisplayPanel.class),
            tile("singularity_chamber", TileSingularityChamber.class),
            tile("emc_interface", TileEMCInterface.class, Condition.EMC_INTERFACE, () -> BlockRegistry.EMC_INTERFACE)
    ));

    private ContentDefinition() {
    }

    private static BlockDefinition block(String name, Supplier<Block> factory, Consumer<Block> binder, Supplier<Block> getter) {
        return block(name, factory, binder, getter, Condition.ALWAYS, true);
    }

    private static BlockDefinition block(String name, Supplier<Block> factory, Consumer<Block> binder,
                                         Supplier<Block> getter, Condition condition, boolean itemBlock) {
        return new BlockDefinition(name, factory, binder, getter, condition, itemBlock);
    }

    private static ItemDefinition simple(String name, Supplier<Item> factory, Consumer<Item> binder, Supplier<Item> getter) {
        return simple(name, factory, binder, getter, Condition.ALWAYS);
    }

    private static ItemDefinition simple(String name, Supplier<Item> factory, Consumer<Item> binder,
                                         Supplier<Item> getter, Condition condition) {
        return simple(name, factory, binder, getter, condition, ModelType.SIMPLE);
    }

    private static ItemDefinition simple(String name, Supplier<Item> factory, Consumer<Item> binder,
                                         Supplier<Item> getter, Condition condition, ModelType modelType) {
        return item(name, factory, binder, getter, condition, modelType);
    }

    private static ItemDefinition item(String name, Supplier<Item> factory, Consumer<Item> binder,
                                       Supplier<Item> getter, Condition condition, ModelType modelType,
                                       String... variants) {
        return new ItemDefinition(name, factory, binder, getter, condition, modelType,
                Collections.unmodifiableList(Arrays.asList(variants)), null);
    }

    private static ItemDefinition part(String name, Supplier<Item> factory, Consumer<Item> binder,
                                       Supplier<Item> getter, Supplier<ResourceLocation[]> models) {
        return new ItemDefinition(name, factory, binder, getter, Condition.ALWAYS, ModelType.PART,
                Collections.emptyList(), models);
    }

    private static Supplier<Item> reflected(String className) {
        return () -> {
            try {
                return (Item) Class.forName(className).newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(className, e);
            }
        };
    }

    private static TileDefinition tile(String name, Class<? extends TileEntity> tileClass) {
        return new TileDefinition(name, tileClass, Condition.ALWAYS, null);
    }

    private static TileDefinition tile(String name, Class<? extends TileEntity> tileClass,
                                       Condition condition, Supplier<Block> block) {
        return new TileDefinition(name, tileClass, condition, block);
    }

    public enum Condition {
        ALWAYS,
        THAUMCRAFT,
        GAS,
        BOTANIA,
        ASTRAL_SORCERY,
        EMC_INTERFACE;

        public boolean isEnabled() {
            switch (this) {
                case THAUMCRAFT:
                    return com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.isModLoaded("thaumcraft");
                case GAS:
                    return com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.isModLoaded("mekanism")
                            && com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.isModLoaded("mekeng");
                case BOTANIA:
                    return com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.isModLoaded("botania");
                case ASTRAL_SORCERY:
                    return com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.isModLoaded("astralsorcery");
                case EMC_INTERFACE:
                    return com.github.aeddddd.ae2enhanced.config.BootConfig.current().emcInterfaceEnabled
                            && com.github.aeddddd.ae2enhanced.integration.RuntimeIntegrationRegistry.isModLoaded("projecte");
                default:
                    return true;
            }
        }
    }

    public enum ModelType {
        NONE,
        BLOCK_ITEM,
        SIMPLE,
        PART,
        UPGRADE_CARD,
        ESSENTIA_DROP,
        FLUID_DROP,
        GAS_DROP,
        STARLIGHT_DROP,
        VIRTUAL_PARALLEL_CARD,
        OMNI_TOOL,
        OMNI_UPGRADE_CARD,
        CONSTRAINED_MICRO_SINGULARITY
    }

    public static final class BlockDefinition {
        public final String registryName;
        public final Supplier<Block> factory;
        public final Consumer<Block> binder;
        public final Supplier<Block> getter;
        public final Condition condition;
        public final boolean itemBlock;
        public final ModelType modelType;

        private BlockDefinition(String registryName, Supplier<Block> factory, Consumer<Block> binder,
                                Supplier<Block> getter, Condition condition, boolean itemBlock) {
            this.registryName = registryName;
            this.factory = factory;
            this.binder = binder;
            this.getter = getter;
            this.condition = condition;
            this.itemBlock = itemBlock;
            this.modelType = itemBlock ? ModelType.BLOCK_ITEM : ModelType.NONE;
        }
    }

    public static final class ItemDefinition {
        public final String registryName;
        public final Supplier<Item> factory;
        public final Consumer<Item> binder;
        public final Supplier<Item> getter;
        public final Condition condition;
        public final ModelType modelType;
        public final List<String> modelVariants;
        public final Supplier<ResourceLocation[]> partModels;

        private ItemDefinition(String registryName, Supplier<Item> factory, Consumer<Item> binder,
                               Supplier<Item> getter, Condition condition, ModelType modelType,
                               List<String> modelVariants, Supplier<ResourceLocation[]> partModels) {
            this.registryName = registryName;
            this.factory = factory;
            this.binder = binder;
            this.getter = getter;
            this.condition = condition;
            this.modelType = modelType;
            this.modelVariants = modelVariants;
            this.partModels = partModels;
        }
    }

    public static final class TileDefinition {
        public final String registryName;
        public final Class<? extends TileEntity> tileClass;
        public final Condition condition;
        public final Supplier<Block> block;

        private TileDefinition(String registryName, Class<? extends TileEntity> tileClass,
                               Condition condition, Supplier<Block> block) {
            this.registryName = registryName;
            this.tileClass = tileClass;
            this.condition = condition;
            this.block = block;
        }
    }
}
