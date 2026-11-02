package com.github.aeddddd.ae2enhanced.network;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;

/** 按 {@link PacketCatalog} 注册线上协议；编解码与 handler 行为不属于注册层。 */
public final class ModNetwork {

    private ModNetwork() {
    }

    public static void init() {
        AE2Enhanced.network = new SimpleNetworkWrapper(AE2Enhanced.MOD_ID);
        for (PacketDescriptor descriptor : PacketCatalog.descriptors()) {
            descriptor.register(AE2Enhanced.network);
        }
    }
}
