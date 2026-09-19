package com.github.aeddddd.ae2enhanced.dimension;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

/**
 * {@link PlayerDimEntry} 的 NBT 序列化容错与组队白名单契约测试。
 */
public class PlayerDimEntryTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GUEST_A = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID GUEST_B = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** 新条目默认值：维度 ID 为 MIN_VALUE（未分配）、进入点 (0,65,0)、组队白名单为空。 */
    @Test
    public void testDefaultValues() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);

        assertThat(entry.playerId).isEqualTo(OWNER);
        assertThat(entry.dimensionId).isEqualTo(Integer.MIN_VALUE);
        assertThat(entry.entryPoint).isEqualTo(new BlockPos(0, 65, 0));
        assertThat(entry.returnDim).isEqualTo(0);
        assertThat(entry.hasReturnPoint).isFalse();
        assertThat(entry.allowedPlayers).isEmpty();
    }

    /** 全字段 NBT 往返：维度 ID、进入点、返回点、组队白名单逐一相同。 */
    @Test
    public void testNbtRoundTrip() {
        PlayerDimEntry original = new PlayerDimEntry(OWNER);
        original.dimensionId = 42;
        original.rules.lockTime = true;
        original.rules.timeValue = 12345L;
        original.entryPoint = new BlockPos(-100, 70, 250);
        original.returnDim = -1;
        original.returnX = 1.5;
        original.returnY = 64.0;
        original.returnZ = -2.5;
        original.returnYaw = 90.0f;
        original.returnPitch = -30.0f;
        original.hasReturnPoint = true;
        original.allowedPlayers.add(GUEST_A);
        original.allowedPlayers.add(GUEST_B);

        PlayerDimEntry restored = new PlayerDimEntry(OWNER);
        restored.readFromNBT(original.writeToNBT());

        assertThat(restored.dimensionId).isEqualTo(42);
        assertThat(restored.rules.lockTime).isTrue();
        assertThat(restored.rules.timeValue).isEqualTo(12345L);
        assertThat(restored.entryPoint).isEqualTo(new BlockPos(-100, 70, 250));
        assertThat(restored.returnDim).isEqualTo(-1);
        assertThat(restored.returnX).isEqualTo(1.5);
        assertThat(restored.returnY).isEqualTo(64.0);
        assertThat(restored.returnZ).isEqualTo(-2.5);
        assertThat(restored.returnYaw).isEqualTo(90.0f);
        assertThat(restored.returnPitch).isEqualTo(-30.0f);
        assertThat(restored.hasReturnPoint).isTrue();
        assertThat(restored.allowedPlayers).containsExactlyInAnyOrder(GUEST_A, GUEST_B);
    }

    /** entryPoint 的 BlockPos 通过 long 编解码，负坐标也能正确往返。 */
    @Test
    public void testEntryPointBlockPosRoundTripNegative() {
        PlayerDimEntry original = new PlayerDimEntry(OWNER);
        original.entryPoint = new BlockPos(-29999999, 5, 29999999);

        PlayerDimEntry restored = new PlayerDimEntry(OWNER);
        restored.readFromNBT(original.writeToNBT());

        assertThat(restored.entryPoint).isEqualTo(new BlockPos(-29999999, 5, 29999999));
    }

    /** 白名单反序列化容错：非法 UUID 字符串与空字符串被跳过，合法项保留。 */
    @Test
    public void testReadSkipsInvalidUuidInAllowedPlayers() {
        NBTTagCompound tag = baseTag();
        NBTTagList allowed = new NBTTagList();
        allowed.appendTag(uuidTag(GUEST_A.toString()));
        allowed.appendTag(uuidTag("not-a-uuid"));
        allowed.appendTag(uuidTag(""));
        tag.setTag("allowedPlayers", allowed);

        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.readFromNBT(tag);

        assertThat(entry.allowedPlayers).containsExactly(GUEST_A);
    }

    /** 旧存档中的 "permissions" 逐玩家权限表已废弃，读取时直接忽略、不影响白名单。 */
    @Test
    public void testReadIgnoresLegacyPermissionsTag() {
        NBTTagCompound tag = baseTag();
        NBTTagList allowed = new NBTTagList();
        allowed.appendTag(uuidTag(GUEST_A.toString()));
        tag.setTag("allowedPlayers", allowed);
        NBTTagList perms = new NBTTagList();
        NBTTagCompound t = uuidTag(GUEST_B.toString());
        t.setString("permissions", "ENTER,BUILD");
        perms.appendTag(t);
        tag.setTag("permissions", perms);

        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.readFromNBT(tag);

        assertThat(entry.allowedPlayers).containsExactly(GUEST_A);
    }

    /** removePlayer 将玩家从组队白名单中移除，对不在白名单的玩家是空操作。 */
    @Test
    public void testRemovePlayer() {
        PlayerDimEntry entry = new PlayerDimEntry(OWNER);
        entry.allowedPlayers.add(GUEST_A);
        entry.allowedPlayers.add(GUEST_B);

        entry.removePlayer(GUEST_A);

        assertThat(entry.allowedPlayers).containsExactly(GUEST_B);

        entry.removePlayer(GUEST_A);
        assertThat(entry.allowedPlayers).containsExactly(GUEST_B);
    }

    /**
     * 版本兼容：readFromNBT(tag) 委托 readFromNBT(tag, 0)。
     * 当前实现中 version 参数未被使用（预留给未来字段扩展的向后兼容），
     * 因此 version=0 与 version=1 读出的结果完全相同。
     */
    @Test
    public void testVersionParameterCurrentlyIgnored() {
        PlayerDimEntry source = new PlayerDimEntry(OWNER);
        source.dimensionId = 7;
        source.entryPoint = new BlockPos(3, 64, -9);
        source.allowedPlayers.add(GUEST_A);
        NBTTagCompound tag = source.writeToNBT();

        PlayerDimEntry v0 = new PlayerDimEntry(OWNER);
        v0.readFromNBT(tag, 0);
        PlayerDimEntry v1 = new PlayerDimEntry(OWNER);
        v1.readFromNBT(tag, 1);
        PlayerDimEntry vDefault = new PlayerDimEntry(OWNER);
        vDefault.readFromNBT(tag);

        for (PlayerDimEntry e : new PlayerDimEntry[]{v1, vDefault}) {
            assertThat(e.dimensionId).isEqualTo(v0.dimensionId);
            assertThat(e.entryPoint).isEqualTo(v0.entryPoint);
            assertThat(e.allowedPlayers).isEqualTo(v0.allowedPlayers);
        }
    }

    /** 构造一个只含必填标量字段的基础 tag，便于手工追加白名单。 */
    private static NBTTagCompound baseTag() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("playerUUID", OWNER.toString());
        tag.setInteger("dimensionId", 5);
        tag.setTag("rules", new PersonalDimensionRules().writeToNBT());
        tag.setLong("entryPoint", new BlockPos(0, 65, 0).toLong());
        return tag;
    }

    private static NBTTagCompound uuidTag(String uuid) {
        NBTTagCompound t = new NBTTagCompound();
        t.setString("uuid", uuid);
        return t;
    }
}
