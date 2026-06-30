package com.github.aeddddd.ae2enhanced;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.registry.ModCreativeTab;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

@Mod(AE2Enhanced.MOD_ID)
public class AE2Enhanced {
    public static final String MOD_ID = "ae2enhanced";
    public static final Logger LOGGER = LogManager.getLogger(AE2Enhanced.class);

    public AE2Enhanced(IEventBus modEventBus, ModContainer container) {
        // 注册 DeferredRegister
        ModBlocks.DR.register(modEventBus);
        ModItems.DR.register(modEventBus);
        ModBlockEntities.DR.register(modEventBus);
        ModMenus.DR.register(modEventBus);
        ModCreativeTab.DR.register(modEventBus);

        // 生命周期事件
        modEventBus.addListener(this::commonSetup);

        // 注册网络包
        ModNetwork.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("AE2Enhanced common setup");
    }
}
