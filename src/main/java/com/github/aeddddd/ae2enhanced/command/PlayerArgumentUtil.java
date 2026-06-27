package com.github.aeddddd.ae2enhanced.command;

import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 命令参数解析工具：统一处理玩家名、UUID、@p、@s。
 */
public final class PlayerArgumentUtil {

    private PlayerArgumentUtil() {}

    /**
     * 解析参数为在线玩家实体。
     *
     * @param server 服务端实例
     * @param sender 命令发送者
     * @param arg    参数
     * @return 在线玩家，若不存在返回 null
     */
    @Nullable
    public static EntityPlayerMP parseOnlinePlayer(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender, @Nonnull String arg) {
        if ("@s".equals(arg)) {
            return sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
        }
        if ("@p".equals(arg)) {
            return sender instanceof EntityPlayerMP ? (EntityPlayerMP) sender : null;
        }
        // 先按玩家名解析
        EntityPlayerMP byName = server.getPlayerList().getPlayerByUsername(arg);
        if (byName != null) {
            return byName;
        }
        // 再按 UUID 解析
        try {
            UUID id = UUID.fromString(arg);
            return server.getPlayerList().getPlayerByUUID(id);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 解析参数为 UUID。支持玩家名（在线）和 UUID 字符串。
     *
     * @param server 服务端实例
     * @param arg    参数
     * @return UUID，若无法解析返回 null
     */
    @Nullable
    public static UUID parseUuid(@Nonnull MinecraftServer server, @Nonnull String arg) {
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUsername(arg);
            return player != null ? player.getUniqueID() : null;
        }
    }

    /**
     * 向发送者输出“找不到玩家”提示。
     */
    public static void sendPlayerNotFound(@Nonnull ICommandSender sender, @Nonnull String arg) {
        sender.sendMessage(new TextComponentString(TextFormatting.RED + "[AE2E] Player not found: " + arg));
    }
}
