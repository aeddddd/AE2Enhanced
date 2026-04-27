package com.github.aeddddd.ae2enhanced;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(AE2Enhanced.MODID)
public class AE2Enhanced {
    public static final String MODID = "ae2enhanced";

    public AE2Enhanced(IEventBus modEventBus) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
    }
}
