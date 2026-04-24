package com.github.aeddddd.ae2enhanced.proxy;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.ModBlocks;
import com.github.aeddddd.ae2enhanced.ModItems;
import com.github.aeddddd.ae2enhanced.client.render.RenderBlackHole;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import com.github.aeddddd.ae2enhanced.tile.TileAssemblyController;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, value = Side.CLIENT)
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        ClientRegistry.bindTileEntitySpecialRenderer(TileAssemblyController.class, new RenderBlackHole());
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void registerModels(ModelRegistryEvent event) {
        registerBlockItemModel(ModBlocks.ASSEMBLY_CONTROLLER);
        registerBlockItemModel(ModBlocks.ASSEMBLY_ME_INTERFACE);
        registerBlockItemModel(ModBlocks.ASSEMBLY_CASING);
        registerBlockItemModel(ModBlocks.ASSEMBLY_INNER_WALL);
        registerBlockItemModel(ModBlocks.ASSEMBLY_STABILIZER);

        // 注册升级卡的所有模型 variant
        ModelLoader.registerItemVariants(ModItems.UPGRADE_CARD,
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card", "inventory"),
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_parallel", "inventory"),
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_speed", "inventory"),
            new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_capacity", "inventory")
        );

        // 使用 ItemMeshDefinition 根据 metadata 动态选择模型
        ModelLoader.setCustomMeshDefinition(ModItems.UPGRADE_CARD, stack -> {
            int meta = stack.getMetadata();
            switch (meta) {
                case ItemUpgradeCard.META_PARALLEL:
                    return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_parallel", "inventory");
                case ItemUpgradeCard.META_SPEED:
                    return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_speed", "inventory");
                case ItemUpgradeCard.META_CAPACITY:
                    return new ModelResourceLocation(AE2Enhanced.MOD_ID + ":upgrade_card_capacity", "inventory");
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
}
