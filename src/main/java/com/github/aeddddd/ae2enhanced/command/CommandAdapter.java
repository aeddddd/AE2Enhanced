package com.github.aeddddd.ae2enhanced.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

interface CommandAdapter {
    String name();

    default Permission permission() { return Permission.ADMIN; }

    void execute(MinecraftServer server, ICommandSender sender, String[] args);

    default List<String> tabComplete(MinecraftServer server, ICommandSender sender, String[] args,
            @Nullable BlockPos targetPos) {
        return Collections.emptyList();
    }

    enum Permission { ADMIN, ANALYSIS, PLAYER }
}
