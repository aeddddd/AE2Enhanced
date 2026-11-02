package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.dimension.team.TeamDimensionAccess;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

import java.util.UUID;

/** 私域统一权限入口；FTB ranks 经 Forge PermissionAPI 生效，不直接依赖 FTB 类。 */
public final class PrivateDimensionPermissions {
    public static final String ENTER = "ae2enhanced.personal_dimension.enter";
    public static final String BUILD = "ae2enhanced.personal_dimension.build";
    public static final String INTERACT = "ae2enhanced.personal_dimension.interact";
    public static final String MANAGE = "ae2enhanced.personal_dimension.manage";
    public static final String BYPASS = "ae2enhanced.personal_dimension.bypass";
    public static final String COMMAND = "ae2e";
    public static final String COMMAND_ROOT = "ae2enhanced";
    public static final String COMMAND_OVERRIDE = "ae2enhanced.ae2e";
    public static final String ADMIN = "ae2enhanced.admin";

    private static boolean registered;

    private PrivateDimensionPermissions() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        PermissionAPI.registerNode(COMMAND, DefaultPermissionLevel.ALL, "Use AE2Enhanced player commands");
        PermissionAPI.registerNode(COMMAND_ROOT, DefaultPermissionLevel.ALL, "Use AE2Enhanced player commands");
        PermissionAPI.registerNode(COMMAND_OVERRIDE, DefaultPermissionLevel.ALL, "Use AE2Enhanced player commands through FTB command overrides");
        PermissionAPI.registerNode(ADMIN, DefaultPermissionLevel.OP, "Use AE2Enhanced administrative commands");
        PermissionAPI.registerNode(ENTER, DefaultPermissionLevel.ALL, "Enter a private dimension as a member or legacy guest");
        PermissionAPI.registerNode(BUILD, DefaultPermissionLevel.ALL, "Place and break blocks in an accessible private dimension");
        PermissionAPI.registerNode(INTERACT, DefaultPermissionLevel.ALL, "Interact with blocks, items and entities in a private dimension");
        PermissionAPI.registerNode(MANAGE, DefaultPermissionLevel.OP, "Manage or delete another player's private dimension");
        PermissionAPI.registerNode(BYPASS, DefaultPermissionLevel.OP, "Bypass private dimension membership checks");
    }

    public static boolean canEnter(EntityPlayer player, PersonalDimensionData data, DimensionOwner owner) {
        if (bypass(player)) {
            return true;
        }
        if (!hasMembership(player, data, owner)) {
            return false;
        }
        return !isPersonalOwner(player, owner) || PermissionAPI.hasPermission(player, ENTER);
    }

    public static boolean canBuild(EntityPlayer player, PersonalDimensionData data, DimensionOwner owner) {
        return bypass(player) || hasMembership(player, data, owner) && PermissionAPI.hasPermission(player, BUILD);
    }

    public static boolean canInteract(EntityPlayer player, PersonalDimensionData data, DimensionOwner owner) {
        return bypass(player) || hasMembership(player, data, owner) && PermissionAPI.hasPermission(player, INTERACT);
    }

    public static boolean canModifyRules(EntityPlayer player, PersonalDimensionData data, DimensionOwner owner) {
        if (bypass(player)) {
            return true;
        }
        if (!hasMembership(player, data, owner)) {
            return false;
        }
        return owner.isTeam() ? TeamDimensionAccess.canManage(owner.ownerId, player.getUniqueID())
                : isPersonalOwner(player, owner);
    }

    public static boolean canManageOther(EntityPlayer player) {
        return PermissionAPI.hasPermission(player, MANAGE) || bypass(player);
    }

    private static boolean hasMembership(EntityPlayer player, PersonalDimensionData data, DimensionOwner owner) {
        if (owner == null) {
            return false;
        }
        if (!owner.isTeam()) {
            if (isPersonalOwner(player, owner)) {
                return true;
            }
            try {
                PlayerDimEntry entry = data.peekEntry(UUID.fromString(owner.ownerId));
                return entry != null && entry.allowedPlayers.contains(player.getUniqueID());
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return TeamDimensionAccess.hasValidBinding(owner.ownerId, owner.bindingId)
                && TeamDimensionAccess.isMember(owner.ownerId, player.getUniqueID());
    }

    private static boolean isPersonalOwner(EntityPlayer player, DimensionOwner owner) {
        return !owner.isTeam() && player.getUniqueID().toString().equals(owner.ownerId);
    }

    private static boolean bypass(EntityPlayer player) {
        return PermissionAPI.hasPermission(player, BYPASS);
    }
}
