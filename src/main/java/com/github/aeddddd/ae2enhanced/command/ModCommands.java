package com.github.aeddddd.ae2enhanced.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimPermission;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionData;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PlayerDimEntry;

/**
 * {@code /ae2e pd} 个人维度管理命令.
 *
 * <p>list / info / delete 为管理命令（权限等级 ≥ 2）;tp / invite / kick / setperm
 * 面向普通玩家,操作自己的个人维度.</p>
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class ModCommands {

    private ModCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("ae2e")
                .then(Commands.literal("pd")
                        .then(Commands.literal("list")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("info")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> info(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("delete")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> delete(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("tp")
                                .executes(ctx -> tpSelf(ctx.getSource()))
                                .then(Commands.argument("owner", EntityArgument.player())
                                        .executes(ctx -> tpTo(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "owner")))))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> invite(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target")))))
                        .then(Commands.literal("kick")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(ctx -> kick(ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target")))))
                        .then(Commands.literal("setperm")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("permission", StringArgumentType.word())
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(ctx -> setPerm(ctx.getSource(),
                                                                EntityArgument.getPlayer(ctx, "target"),
                                                                StringArgumentType.getString(ctx, "permission"),
                                                                BoolArgumentType.getBool(ctx, "value")))))))));
    }

    private static int list(CommandSourceStack source) {
        PersonalDimensionData data = PersonalDimensionData.get(source.getServer());
        int count = 0;
        for (PlayerDimEntry entry : data.getAllEntries()) {
            source.sendSuccess(() -> Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.list_entry",
                    entry.playerId.toString(), entry.created), false);
            count++;
        }
        final int total = count;
        source.sendSuccess(() -> Component.translatable(
                "chat.ae2enhanced.personal_dimension.cmd.list_total", total), false);
        return count;
    }

    private static int info(CommandSourceStack source, ServerPlayer target) {
        PlayerDimEntry entry = PersonalDimensionManager.getEntry(source.getServer(), target.getUUID());
        if (entry == null || !entry.created) {
            source.sendFailure(Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.no_dimension", target.getName().getString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "chat.ae2enhanced.personal_dimension.cmd.info_header", target.getName().getString()), false);
        source.sendSuccess(() -> Component.translatable(
                "chat.ae2enhanced.personal_dimension.cmd.info_entry",
                entry.entryPoint.getX(), entry.entryPoint.getY(), entry.entryPoint.getZ()), false);
        source.sendSuccess(() -> Component.translatable(
                "chat.ae2enhanced.personal_dimension.cmd.info_allowed", entry.allowedPlayers.size()), false);
        return 1;
    }

    private static int delete(CommandSourceStack source, ServerPlayer target) {
        if (PersonalDimensionManager.deleteDimension(source.getServer(), target.getUUID())) {
            source.sendSuccess(() -> Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.deleted", target.getName().getString()), true);
            return 1;
        }
        source.sendFailure(Component.translatable(
                "chat.ae2enhanced.personal_dimension.cmd.no_dimension", target.getName().getString()));
        return 0;
    }

    private static int tpSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.player_only"));
            return 0;
        }
        if (PersonalDimensionManager.teleportPlayerToDimension(player, player.getUUID())) {
            return 1;
        }
        source.sendFailure(Component.translatable("chat.ae2enhanced.personal_dimension.create_failed"));
        return 0;
    }

    private static int tpTo(CommandSourceStack source, ServerPlayer owner) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.player_only"));
            return 0;
        }
        if (PersonalDimensionManager.teleportPlayerToDimension(player, owner.getUUID())) {
            return 1;
        }
        source.sendFailure(Component.translatable(
                "chat.ae2enhanced.personal_dimension.cmd.no_permission", owner.getName().getString()));
        return 0;
    }

    private static int invite(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.player_only"));
            return 0;
        }
        if (PersonalDimensionManager.invitePlayer(source.getServer(), player.getUUID(), target.getUUID())) {
            source.sendSuccess(() -> Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.invited", target.getName().getString()), false);
            return 1;
        }
        return 0;
    }

    private static int kick(CommandSourceStack source, ServerPlayer target) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.player_only"));
            return 0;
        }
        if (PersonalDimensionManager.kickPlayer(source.getServer(), player.getUUID(), target.getUUID())) {
            source.sendSuccess(() -> Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.kicked", target.getName().getString()), false);
            return 1;
        }
        return 0;
    }

    private static int setPerm(CommandSourceStack source, ServerPlayer target, String permissionName,
            boolean value) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.player_only"));
            return 0;
        }
        PersonalDimPermission permission;
        try {
            permission = PersonalDimPermission.valueOf(permissionName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.bad_permission", permissionName));
            return 0;
        }
        if (PersonalDimensionManager.setPermission(source.getServer(), player.getUUID(), target.getUUID(),
                permission, value)) {
            source.sendSuccess(() -> Component.translatable(
                    "chat.ae2enhanced.personal_dimension.cmd.perm_set",
                    target.getName().getString(), permission.name(), value), false);
            return 1;
        }
        return 0;
    }
}
