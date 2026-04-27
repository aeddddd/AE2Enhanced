package com.github.aeddddd.ae2enhanced;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AE2Enhanced.MODID);

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
