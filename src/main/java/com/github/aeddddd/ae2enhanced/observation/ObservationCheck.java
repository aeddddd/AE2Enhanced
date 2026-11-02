package com.github.aeddddd.ae2enhanced.observation;

import net.minecraft.server.MinecraftServer;

import java.util.List;

/** 业务提供的只读健康检查端口。 */
public interface ObservationCheck {

    String name();
    String displayName();
    void run(MinecraftServer server, List<CheckObservation> out);
}
