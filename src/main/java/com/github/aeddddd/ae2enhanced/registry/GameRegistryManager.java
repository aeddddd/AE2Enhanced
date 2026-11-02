package com.github.aeddddd.ae2enhanced.registry;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.block.BlockRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.omnitool.crafting.RecipeOmniToolUpgrade;
import com.github.aeddddd.ae2enhanced.registry.content.ContentDefinition;
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

/** 游戏注册执行器；内容、条件与模型元数据由 {@link ContentDefinition} 声明。 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class GameRegistryManager {

    private GameRegistryManager() {
    }

    /** 在 preInit 阶段按声明顺序实例化全部 Item 与 Part。 */
    public static void initItems() {
        for (ContentDefinition.ItemDefinition definition : ContentDefinition.ITEMS) {
            if (!definition.condition.isEnabled()) {
                continue;
            }
            try {
                definition.binder.accept(definition.factory.get());
            } catch (Exception e) {
                AE2Enhanced.LOGGER.error("[AE2E] Failed to instantiate " + definition.registryName, e);
            }
        }
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        for (ContentDefinition.BlockDefinition definition : ContentDefinition.BLOCKS) {
            if (definition.condition.isEnabled()) {
                Block block = definition.factory.get();
                definition.binder.accept(block);
                event.getRegistry().register(block);
            }
        }
        for (ContentDefinition.TileDefinition definition : ContentDefinition.TILES) {
            if (definition.condition.isEnabled()
                    && (definition.block == null || definition.block.get() != null)) {
                GameRegistry.registerTileEntity(definition.tileClass,
                        AE2Enhanced.MOD_ID + ":" + definition.registryName);
            }
        }
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        for (ContentDefinition.BlockDefinition definition : ContentDefinition.BLOCKS) {
            Block block = definition.getter.get();
            if (block != null && definition.itemBlock) {
                event.getRegistry().register(new ItemBlock(block)
                        .setRegistryName(block.getRegistryName())
                        .setCreativeTab(AE2Enhanced.CREATIVE_TAB));
            }
        }
        for (ContentDefinition.ItemDefinition definition : ContentDefinition.ITEMS) {
            Item item = definition.getter.get();
            if (item != null) {
                event.getRegistry().register(item);
            }
        }
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<net.minecraft.item.crafting.IRecipe> event) {
        com.github.aeddddd.ae2enhanced.config.BootConfig boot =
                com.github.aeddddd.ae2enhanced.config.BootConfig.current();
        if (Loader.isModLoaded("draconicevolution") && boot.chaosCoreUpgradeRecipe) {
            Item chaoticCore = Item.REGISTRY.getObject(new ResourceLocation("draconicevolution", "chaotic_core"));
            if (chaoticCore != null) {
                event.getRegistry().register(new RecipeOmniToolUpgrade(
                        new ResourceLocation(AE2Enhanced.MOD_ID, "omni_tool_chaos_upgrade"),
                        new ItemStack(ItemRegistry.ME_OMNI_TOOL), "chaos",
                        new ItemStack(ItemRegistry.ME_OMNI_TOOL), new ItemStack(chaoticCore)
                ).setRegistryName(AE2Enhanced.MOD_ID, "omni_tool_chaos_upgrade"));
            }
        }

        event.getRegistry().register(new RecipeOmniToolUpgrade(
                new ResourceLocation(AE2Enhanced.MOD_ID, "omni_tool_enchanted_book_upgrade"),
                new ItemStack(ItemRegistry.ME_OMNI_TOOL), "enchanted_book",
                new ItemStack(ItemRegistry.ME_OMNI_TOOL), new ItemStack(Items.ENCHANTED_BOOK)
        ).setRegistryName(AE2Enhanced.MOD_ID, "omni_tool_enchanted_book_upgrade"));

        if (boot.bedrockBreakerUpgradeRecipe) {
            Item bedrockItem = Item.getItemFromBlock(Blocks.BEDROCK);
            if (bedrockItem != null && bedrockItem != Items.AIR) {
                event.getRegistry().register(new RecipeOmniToolUpgrade(
                        new ResourceLocation(AE2Enhanced.MOD_ID, "omni_tool_bedrock_upgrade"),
                        new ItemStack(ItemRegistry.ME_OMNI_TOOL), "bedrock",
                        new ItemStack(ItemRegistry.ME_OMNI_TOOL), new ItemStack(bedrockItem)
                ).setRegistryName(AE2Enhanced.MOD_ID, "omni_tool_bedrock_upgrade"));
            }
        }

        event.getRegistry().register(new com.github.aeddddd.ae2enhanced.item.crafting.RecipeSmartPatternMerge()
                .setRegistryName(AE2Enhanced.MOD_ID, "smart_pattern_merge"));

        event.getRegistry().register(new com.github.aeddddd.ae2enhanced.crafting.RecipeOmniTerminalV2Conversion(
                new ResourceLocation(AE2Enhanced.MOD_ID, "omni_wireless_terminal_v2_conversion"),
                ItemRegistry.OMNI_WIRELESS_TERMINAL_II, ItemRegistry.OMNI_WIRELESS_TERMINAL)
                .setRegistryName(AE2Enhanced.MOD_ID, "omni_wireless_terminal_v2_conversion"));
        event.getRegistry().register(new com.github.aeddddd.ae2enhanced.ring.crafting.RecipeRingModuleInstall(
                new ResourceLocation(AE2Enhanced.MOD_ID, "ring_module_install"),
                ItemRegistry.ME_OMNI_TOOL, ItemRegistry.NETWORK_LINK_CREDENTIAL)
                .setRegistryName(AE2Enhanced.MOD_ID, "ring_module_install"));

        if (boot.ringAscendRecipe) {
            event.getRegistry().register(new com.github.aeddddd.ae2enhanced.ring.crafting.RecipeRingAscend(
                    new ResourceLocation(AE2Enhanced.MOD_ID, "ring_ascend")));
        }

        if (boot.ringTierRecipes) {
            event.getRegistry().register(new com.github.aeddddd.ae2enhanced.ring.crafting.RecipeRingTierUpgrade(
                    new ResourceLocation(AE2Enhanced.MOD_ID, "ring_tier_2"), 0,
                    ItemRegistry.STABLE_SPACETIME_MANIFOLD, 4,
                    ItemRegistry.DIFFERENTIAL_FORM_STABILIZER, 2,
                    ItemRegistry.CONFORMAL_CHARGE, 2));
            event.getRegistry().register(new com.github.aeddddd.ae2enhanced.ring.crafting.RecipeRingTierUpgrade(
                    new ResourceLocation(AE2Enhanced.MOD_ID, "ring_tier_3"), 1,
                    ItemRegistry.DIFFERENTIAL_FORM_STABILIZER, 4,
                    ItemRegistry.CONFORMAL_CHARGE, 4));
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
