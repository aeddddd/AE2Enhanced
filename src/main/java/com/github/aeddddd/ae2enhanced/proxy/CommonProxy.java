package com.github.aeddddd.ae2enhanced.proxy;

import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
    }

    public void init(FMLInitializationEvent event) {
        com.github.aeddddd.ae2enhanced.gui.GuiEndpointRegistry.register(
                net.minecraftforge.fml.relauncher.Side.SERVER,
                new com.github.aeddddd.ae2enhanced.gui.GuiServerEndpoint());
    }

    public void postInit(FMLPostInitializationEvent event) {
    }
}
