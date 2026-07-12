package com.github.aeddddd.ae2enhanced;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.data.DataGenerators;
import com.github.aeddddd.ae2enhanced.event.StructureEventHandler;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.registry.ModCreativeTab;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;

@Mod(AE2Enhanced.MOD_ID)
public class AE2Enhanced {
    public static final String MOD_ID = "ae2enhanced";
    public static final Logger LOGGER = LogManager.getLogger(AE2Enhanced.class);

    public AE2Enhanced() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // 注册配置
        AE2EnhancedConfig.register();

        // 注册 DeferredRegister
        ModBlocks.DR.register(modEventBus);
        ModItems.DR.register(modEventBus);
        ModBlockEntities.DR.register(modEventBus);
        ModMenus.DR.register(modEventBus);
        ModCreativeTab.DR.register(modEventBus);
        ModRecipes.DR.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);

        // 生命周期事件
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(DataGenerators::gatherData);

        // 注册网络包
        ModNetwork.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("AE2Enhanced common setup");

        // 在方块注册完成后初始化多方块结构定义
        AssemblyStructure.init();
        HyperdimensionalStructure.init();
        SupercausalStructure.init();

        ModRecipes.init();
        MinecraftForge.EVENT_BUS.register(StructureEventHandler.class);
    }
}
