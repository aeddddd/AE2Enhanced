package com.github.aeddddd.ae2enhanced.network.packet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorRole;
import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorScheme;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.dimension.FloorPreset;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimPermission;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.dimension.PlayerDimEntry;
import com.github.aeddddd.ae2enhanced.dimension.PresetLoader;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * S→C：个人维度管理器的完整状态同步包（打开与变更时发送）.
 */
public class PersonalDimManagerStatePacket {

    /**
     * 单个受邀玩家的权限快照.
     */
    public record PlayerPerm(UUID uuid, String name, int mask) {
    }

    public final BlockPos pos;
    public final UUID owner;
    public final String ownerName;
    public final boolean created;
    public final PersonalDimensionRules rules;
    public final List<PlayerPerm> players;
    // 地板预设预览数据
    public final int floorY;
    public final int entryY;
    public final int presetWidth;
    public final int presetDepth;
    public final List<String> presetPalette;
    public final int[] presetStates;
    // 地板颜色方案(染料色 id)
    public final int roadColor;
    public final int lineColor;
    public final int platformColor;

    private PersonalDimManagerStatePacket(BlockPos pos, UUID owner, String ownerName, boolean created,
            PersonalDimensionRules rules, List<PlayerPerm> players, int floorY, int entryY,
            int presetWidth, int presetDepth, List<String> presetPalette, int[] presetStates,
            int roadColor, int lineColor, int platformColor) {
        this.pos = pos;
        this.owner = owner;
        this.ownerName = ownerName;
        this.created = created;
        this.rules = rules;
        this.players = players;
        this.floorY = floorY;
        this.entryY = entryY;
        this.presetWidth = presetWidth;
        this.presetDepth = presetDepth;
        this.presetPalette = presetPalette;
        this.presetStates = presetStates;
        this.roadColor = roadColor;
        this.lineColor = lineColor;
        this.platformColor = platformColor;
    }

    public static int permissionMask(Set<PersonalDimPermission> permissions) {
        int mask = 0;
        for (PersonalDimPermission permission : permissions) {
            mask |= 1 << permission.ordinal();
        }
        return mask;
    }

    public boolean hasPermission(PlayerPerm player, PersonalDimPermission permission) {
        return (player.mask() & (1 << permission.ordinal())) != 0;
    }

    public static PersonalDimManagerStatePacket create(MinecraftServer server, BlockPos pos, UUID owner) {
        PlayerDimEntry entry = PersonalDimensionManager.getEntry(server, owner);
        String ownerName = resolveName(server, owner);
        PersonalDimensionRules rules = entry != null ? entry.rules.copy() : new PersonalDimensionRules();
        boolean created = entry != null && entry.created;

        List<PlayerPerm> players = new ArrayList<>();
        if (entry != null) {
            for (Map.Entry<UUID, Set<PersonalDimPermission>> e : entry.permissions.entrySet()) {
                players.add(new PlayerPerm(e.getKey(), resolveName(server, e.getKey()),
                        permissionMask(e.getValue())));
            }
        }

        // API 注册的非调色板式预设采样为 FloorPreset,保证预览同步可用
        FloorPreset preset = FloorPreset.from(PresetLoader.getPreset());
        List<String> palette = new ArrayList<>(preset.palette.length);
        for (net.minecraft.world.level.block.state.BlockState state : preset.palette) {
            palette.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        }

        FloorColorScheme scheme = entry != null ? entry.colorScheme : FloorColorScheme.createDefault();
        return new PersonalDimManagerStatePacket(pos, owner, ownerName, created, rules, players,
                AE2EnhancedConfig.COMMON.personalDimensionFloorY.get(),
                AE2EnhancedConfig.COMMON.personalDimensionEntryY.get(),
                preset.width, preset.depth, palette, preset.stateList,
                scheme.getConcreteColor(FloorColorRole.ROAD_BASE).getId(),
                scheme.getConcreteColor(FloorColorRole.ROAD_LINE).getId(),
                scheme.getConcreteColor(FloorColorRole.PLATFORM_BASE).getId());
    }

    private static String resolveName(MinecraftServer server, UUID uuid) {
        var player = server.getPlayerList().getPlayer(uuid);
        if (player != null) {
            return player.getGameProfile().getName();
        }
        var cache = server.getProfileCache();
        if (cache != null) {
            var profile = cache.get(uuid);
            if (profile.isPresent()) {
                return profile.get().getName();
            }
        }
        return uuid.toString();
    }

    public static void encode(PersonalDimManagerStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeUUID(packet.owner);
        buffer.writeUtf(packet.ownerName);
        buffer.writeBoolean(packet.created);
        PersonalDimensionRules rules = packet.rules;
        buffer.writeBoolean(rules.disableMobSpawning);
        buffer.writeBoolean(rules.lockWeather);
        buffer.writeBoolean(rules.lockTime);
        buffer.writeBoolean(rules.daylightCycle);
        buffer.writeLong(rules.timeValue);
        buffer.writeBoolean(rules.flightEnabled);
        buffer.writeFloat(rules.movementSpeed);
        buffer.writeBoolean(rules.noFlightInertia);

        buffer.writeVarInt(packet.players.size());
        for (PlayerPerm player : packet.players) {
            buffer.writeUUID(player.uuid());
            buffer.writeUtf(player.name());
            buffer.writeByte(player.mask());
        }

        buffer.writeVarInt(packet.floorY);
        buffer.writeVarInt(packet.entryY);
        buffer.writeVarInt(packet.presetWidth);
        buffer.writeVarInt(packet.presetDepth);
        buffer.writeVarInt(packet.presetPalette.size());
        for (String name : packet.presetPalette) {
            buffer.writeUtf(name);
        }
        buffer.writeVarInt(packet.presetStates.length);
        for (int state : packet.presetStates) {
            buffer.writeVarInt(state);
        }
        buffer.writeVarInt(packet.roadColor);
        buffer.writeVarInt(packet.lineColor);
        buffer.writeVarInt(packet.platformColor);
    }

    public static PersonalDimManagerStatePacket decode(FriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        UUID owner = buffer.readUUID();
        String ownerName = buffer.readUtf();
        boolean created = buffer.readBoolean();
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.disableMobSpawning = buffer.readBoolean();
        rules.lockWeather = buffer.readBoolean();
        rules.lockTime = buffer.readBoolean();
        rules.daylightCycle = buffer.readBoolean();
        rules.timeValue = buffer.readLong();
        rules.flightEnabled = buffer.readBoolean();
        rules.movementSpeed = buffer.readFloat();
        rules.noFlightInertia = buffer.readBoolean();

        int playerCount = buffer.readVarInt();
        List<PlayerPerm> players = new ArrayList<>(playerCount);
        for (int i = 0; i < playerCount; i++) {
            players.add(new PlayerPerm(buffer.readUUID(), buffer.readUtf(), buffer.readByte()));
        }

        int floorY = buffer.readVarInt();
        int entryY = buffer.readVarInt();
        int width = buffer.readVarInt();
        int depth = buffer.readVarInt();
        int paletteSize = buffer.readVarInt();
        List<String> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            palette.add(buffer.readUtf());
        }
        int stateCount = buffer.readVarInt();
        int[] states = new int[stateCount];
        for (int i = 0; i < stateCount; i++) {
            states[i] = buffer.readVarInt();
        }
        int roadColor = buffer.readVarInt();
        int lineColor = buffer.readVarInt();
        int platformColor = buffer.readVarInt();

        return new PersonalDimManagerStatePacket(pos, owner, ownerName, created, rules, players,
                floorY, entryY, width, depth, palette, states, roadColor, lineColor, platformColor);
    }

    public static void handle(PersonalDimManagerStatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.github.aeddddd.ae2enhanced.client.ClientPersonalDimState.update(packet)));
        context.setPacketHandled(true);
    }
}
