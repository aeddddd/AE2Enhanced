package com.github.aeddddd.ae2enhanced.network.packet;

import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端包接口。
 */
public interface ServerboundPacket {
    void handleOnServer(ServerPlayer player);
}
