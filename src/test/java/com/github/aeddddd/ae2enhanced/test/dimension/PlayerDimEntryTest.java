package com.github.aeddddd.ae2enhanced.test.dimension;

import java.util.EnumSet;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.DyeColor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorScheme;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimPermission;
import com.github.aeddddd.ae2enhanced.dimension.PlayerDimEntry;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PlayerDimEntry} 单元测试.
 */
class PlayerDimEntryTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GUEST = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID OTHER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @BeforeAll
    static void bootstrap() {
        // colorScheme 读写 NBT 依赖方块注册表
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void shouldHaveExpectedDefaults() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);

        assertThat(entry.playerId).isEqualTo(OWNER);
        assertThat(entry.created).isFalse();
        assertThat(entry.rules).isNotNull();
        assertThat(entry.entryPoint).isEqualTo(new BlockPos(0, 65, 0));
        assertThat(entry.returnDim).isEqualTo("minecraft:overworld");
        assertThat(entry.returnX).isZero();
        assertThat(entry.returnY).isZero();
        assertThat(entry.returnZ).isZero();
        assertThat(entry.returnYaw).isZero();
        assertThat(entry.returnPitch).isZero();
        assertThat(entry.hasReturnPoint).isFalse();
        assertThat(entry.colorScheme).isNotNull();
        assertThat(entry.allowedPlayers).isEmpty();
        assertThat(entry.permissions).isEmpty();
    }

    @Test
    void hasPermissionShouldReturnFalseForUnknownPlayer() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);

        assertThat(entry.hasPermission(GUEST, PersonalDimPermission.ENTER)).isFalse();
        assertThat(entry.getPermissions(GUEST)).isEmpty();
    }

    @Test
    void grantPermissionShouldAddToWhitelistAndPermissionSet() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);

        entry.grantPermission(GUEST, PersonalDimPermission.ENTER);
        entry.grantPermission(GUEST, PersonalDimPermission.BUILD);

        assertThat(entry.allowedPlayers).containsExactly(GUEST);
        assertThat(entry.hasPermission(GUEST, PersonalDimPermission.ENTER)).isTrue();
        assertThat(entry.hasPermission(GUEST, PersonalDimPermission.BUILD)).isTrue();
        assertThat(entry.hasPermission(GUEST, PersonalDimPermission.MANAGE_RULES)).isFalse();
        assertThat(entry.getPermissions(GUEST))
                .containsExactlyInAnyOrder(PersonalDimPermission.ENTER, PersonalDimPermission.BUILD);
    }

    @Test
    void grantPermissionShouldBeIdempotent() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);

        entry.grantPermission(GUEST, PersonalDimPermission.ENTER);
        entry.grantPermission(GUEST, PersonalDimPermission.ENTER);

        assertThat(entry.allowedPlayers).containsExactly(GUEST);
        assertThat(entry.getPermissions(GUEST)).containsExactly(PersonalDimPermission.ENTER);
    }

    @Test
    void revokeLastPermissionShouldRemovePlayerFromWhitelist() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.grantPermission(GUEST, PersonalDimPermission.ENTER);

        entry.revokePermission(GUEST, PersonalDimPermission.ENTER);

        assertThat(entry.hasPermission(GUEST, PersonalDimPermission.ENTER)).isFalse();
        assertThat(entry.permissions).isEmpty();
        assertThat(entry.allowedPlayers).isEmpty();
    }

    @Test
    void revokeNonLastPermissionShouldKeepWhitelistEntry() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.grantPermission(GUEST, PersonalDimPermission.ENTER);
        entry.grantPermission(GUEST, PersonalDimPermission.BUILD);

        entry.revokePermission(GUEST, PersonalDimPermission.BUILD);

        assertThat(entry.hasPermission(GUEST, PersonalDimPermission.BUILD)).isFalse();
        assertThat(entry.hasPermission(GUEST, PersonalDimPermission.ENTER)).isTrue();
        assertThat(entry.allowedPlayers).containsExactly(GUEST);
    }

    @Test
    void revokeUnknownPermissionShouldBeNoOp() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);

        // 未授权玩家撤销不抛异常
        entry.revokePermission(GUEST, PersonalDimPermission.ENTER);

        // 撤销玩家未持有的权限时白名单不受影响
        entry.grantPermission(OTHER, PersonalDimPermission.ENTER);
        entry.revokePermission(OTHER, PersonalDimPermission.BUILD);
        assertThat(entry.allowedPlayers).containsExactly(OTHER);
        assertThat(entry.hasPermission(OTHER, PersonalDimPermission.ENTER)).isTrue();
    }

    @Test
    void removePlayerShouldClearWhitelistAndPermissions() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.grantPermission(GUEST, PersonalDimPermission.ENTER);
        entry.grantPermission(GUEST, PersonalDimPermission.MANAGE_RULES);

        entry.removePlayer(GUEST);

        assertThat(entry.allowedPlayers).isEmpty();
        assertThat(entry.permissions).isEmpty();
        assertThat(entry.getPermissions(GUEST)).isEmpty();
    }

    @Test
    void getPermissionsShouldReturnDefensiveCopy() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.grantPermission(GUEST, PersonalDimPermission.ENTER);

        var view = entry.getPermissions(GUEST);

        // 返回副本且不可修改
        assertThatThrownBy(() -> view.add(PersonalDimPermission.BUILD))
                .isInstanceOf(UnsupportedOperationException.class);
        // 即使强转修改副本,也不影响内部状态
        assertThat(entry.hasPermission(GUEST, PersonalDimPermission.BUILD)).isFalse();
    }

    @Test
    void nbtRoundTripShouldPreserveAllFields() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.created = true;
        entry.rules.disableMobSpawning = true;
        entry.rules.movementSpeed = 0.5f;
        entry.entryPoint = new BlockPos(-10, 70, 25);
        entry.returnDim = "minecraft:the_nether";
        entry.returnX = 1.5;
        entry.returnY = 64.0;
        entry.returnZ = -2.25;
        entry.returnYaw = 90.5f;
        entry.returnPitch = -45.25f;
        entry.hasReturnPoint = true;
        entry.colorScheme = FloorColorScheme.ofConcrete(DyeColor.RED, DyeColor.YELLOW, DyeColor.BLUE);
        entry.grantPermission(GUEST, PersonalDimPermission.ENTER);
        entry.grantPermission(GUEST, PersonalDimPermission.INTERACT);
        entry.grantPermission(OTHER, PersonalDimPermission.BUILD);

        PlayerDimEntry restored = new PlayerDimEntry(OWNER);
        restored.readFromNBT(entry.writeToNBT());

        assertThat(restored.created).isTrue();
        assertThat(restored.rules.disableMobSpawning).isTrue();
        assertThat(restored.rules.movementSpeed).isEqualTo(0.5f);
        assertThat(restored.entryPoint).isEqualTo(new BlockPos(-10, 70, 25));
        assertThat(restored.returnDim).isEqualTo("minecraft:the_nether");
        assertThat(restored.returnX).isEqualTo(1.5);
        assertThat(restored.returnY).isEqualTo(64.0);
        assertThat(restored.returnZ).isEqualTo(-2.25);
        assertThat(restored.returnYaw).isEqualTo(90.5f);
        assertThat(restored.returnPitch).isEqualTo(-45.25f);
        assertThat(restored.hasReturnPoint).isTrue();
        assertThat(restored.allowedPlayers).containsExactlyInAnyOrder(GUEST, OTHER);
        assertThat(restored.getPermissions(GUEST))
                .containsExactlyInAnyOrder(PersonalDimPermission.ENTER, PersonalDimPermission.INTERACT);
        assertThat(restored.getPermissions(OTHER)).containsExactly(PersonalDimPermission.BUILD);
    }

    @Test
    void readFromNbtShouldFallBackToOverworldWhenReturnDimEmpty() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        CompoundTag tag = entry.writeToNBT();
        tag.putString("returnDim", "");

        entry.readFromNBT(tag);

        assertThat(entry.returnDim).isEqualTo("minecraft:overworld");
    }

    @Test
    void readFromNbtShouldSkipMalformedAllowedPlayers() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        CompoundTag tag = entry.writeToNBT();
        ListTag allowed = new ListTag();
        CompoundTag bad = new CompoundTag();
        bad.putString("uuid", "not-a-uuid");
        allowed.add(bad);
        CompoundTag good = new CompoundTag();
        good.putString("uuid", GUEST.toString());
        allowed.add(good);
        tag.put("allowedPlayers", allowed);

        entry.readFromNBT(tag);

        assertThat(entry.allowedPlayers).containsExactly(GUEST);
    }

    @Test
    void readFromNbtShouldSkipMalformedPermissions() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        CompoundTag tag = entry.writeToNBT();
        ListTag perms = new ListTag();
        // 非法 uuid 整条跳过
        CompoundTag badUuid = new CompoundTag();
        badUuid.putString("uuid", "bad");
        badUuid.putString("permissions", "ENTER");
        perms.add(badUuid);
        // 未知权限名被忽略,合法权限保留
        CompoundTag mixed = new CompoundTag();
        mixed.putString("uuid", GUEST.toString());
        mixed.putString("permissions", "ENTER,NO_SUCH_PERMISSION,BUILD");
        perms.add(mixed);
        tag.put("permissions", perms);

        entry.readFromNBT(tag);

        assertThat(entry.permissions).containsOnlyKeys(GUEST);
        assertThat(entry.getPermissions(GUEST))
                .containsExactlyInAnyOrder(PersonalDimPermission.ENTER, PersonalDimPermission.BUILD);
    }

    @Test
    void readFromNbtShouldKeepDefaultColorSchemeWhenTagMissing() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.colorScheme = FloorColorScheme.ofConcrete(DyeColor.GREEN, DyeColor.WHITE, DyeColor.BLACK);
        CompoundTag tag = entry.writeToNBT();
        tag.remove("colorScheme");

        PlayerDimEntry restored = new PlayerDimEntry(OWNER);
        restored.readFromNBT(tag);

        // 缺少 colorScheme 键时保留新建条目的默认(空)方案
        assertThat(restored.colorScheme.writeToNBT().getList("overrides", 10)).isEmpty();
    }

    @Test
    void readFromNbtShouldClearPreviouslyLoadedCollections() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.grantPermission(GUEST, PersonalDimPermission.ENTER);

        // 用空 tag 重新读取,旧的白名单与权限应被清空
        CompoundTag empty = new CompoundTag();
        empty.putString("playerUUID", OWNER.toString());
        entry.readFromNBT(empty);

        assertThat(entry.allowedPlayers).isEmpty();
        assertThat(entry.permissions).isEmpty();
    }

    @Test
    void permissionsShouldUseEnumSetSemantics() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.grantPermission(GUEST, PersonalDimPermission.ENTER);
        entry.grantPermission(GUEST, PersonalDimPermission.BUILD);

        assertThat(entry.permissions.get(GUEST)).isInstanceOf(EnumSet.class);
    }
}
