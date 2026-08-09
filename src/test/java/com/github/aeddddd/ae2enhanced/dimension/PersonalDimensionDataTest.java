package com.github.aeddddd.ae2enhanced.dimension;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

/**
 * {@link PersonalDimensionData} 的 NBT 序列化、双向索引与迁移逻辑测试。
 * 静态 get(World) 依赖服务端运行环境，不在本测试范围内。
 */
public class PersonalDimensionDataTest {

    private static final UUID PLAYER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_C = UUID.fromString("33333333-3333-3333-3333-333333333333");

    /** getEntry 按需创建条目（维度 ID 为 MIN_VALUE 表示未分配），重复调用返回同一实例。 */
    @Test
    public void testGetEntryCreatesOnceAndReuses() {
        PersonalDimensionData data = new PersonalDimensionData();

        PlayerDimEntry first = data.getEntry(PLAYER_A);
        assertThat(first.playerId).isEqualTo(PLAYER_A);
        assertThat(first.dimensionId).isEqualTo(Integer.MIN_VALUE);
        assertThat(data.getEntry(PLAYER_A)).isSameAs(first);
        assertThat(data.getAllEntries()).hasSize(1);
    }

    /** NBT 往返：多条目全部读回，且 dimToPlayer 双向索引与 entries 一致。 */
    @Test
    public void testNbtRoundTripMultipleEntries() {
        PersonalDimensionData original = new PersonalDimensionData();
        original.updateDimensionMapping(PLAYER_A, 10);
        original.setEntryPoint(PLAYER_A, new BlockPos(-50, 70, 120));
        original.setReturnPoint(PLAYER_A, 0, 1.5, 64.0, -2.5, 90.0f, -30.0f);
        original.updateDimensionMapping(PLAYER_B, -20);
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.lockTime = true;
        rules.timeValue = 18000L;
        original.setRules(PLAYER_B, rules);
        // 未分配维度的空条目（dimensionId == MIN_VALUE）不进入反向索引，也不会被持久化
        original.getEntry(PLAYER_C);

        PersonalDimensionData restored = new PersonalDimensionData();
        restored.readFromNBT(original.writeToNBT(new NBTTagCompound()));

        assertThat(restored.getAllEntries()).hasSize(2);
        // 空条目不持久化，恢复后不存在 C 的条目
        assertThat(restored.peekEntry(PLAYER_C)).isNull();

        PlayerDimEntry a = restored.getEntry(PLAYER_A);
        assertThat(a.dimensionId).isEqualTo(10);
        assertThat(a.entryPoint).isEqualTo(new BlockPos(-50, 70, 120));
        assertThat(a.hasReturnPoint).isTrue();
        assertThat(a.returnX).isEqualTo(1.5);
        assertThat(a.returnYaw).isEqualTo(90.0f);

        PlayerDimEntry b = restored.getEntry(PLAYER_B);
        assertThat(b.dimensionId).isEqualTo(-20);
        assertThat(b.rules.lockTime).isTrue();
        assertThat(b.rules.timeValue).isEqualTo(18000L);

        // 双向索引一致性
        assertThat(restored.getPlayerForDimension(10)).isEqualTo(PLAYER_A);
        assertThat(restored.getPlayerForDimension(-20)).isEqualTo(PLAYER_B);
        assertThat(restored.getEntryByDimensionId(10)).isSameAs(a);
        assertThat(restored.getEntryByDimensionId(-20)).isSameAs(b);
        // 未分配维度的条目不进反向索引
        assertThat(restored.getPlayerForDimension(Integer.MIN_VALUE)).isNull();
        assertThat(restored.getEntryByDimensionId(999)).isNull();
    }

    /** writeToNBT 总是写入当前版本号。 */
    @Test
    public void testWriteToNbtWritesCurrentVersion() {
        PersonalDimensionData data = new PersonalDimensionData();
        NBTTagCompound tag = data.writeToNBT(new NBTTagCompound());

        // CURRENT_VERSION = 1
        assertThat(tag.getInteger("version")).isEqualTo(1);
    }

    /** 读取缺失 version 字段的旧存档时回退为 0，数据仍能正常加载。 */
    @Test
    public void testReadFromNbtMissingVersionFallsBack() {
        PersonalDimensionData source = new PersonalDimensionData();
        source.updateDimensionMapping(PLAYER_A, 7);
        NBTTagCompound tag = source.writeToNBT(new NBTTagCompound());
        tag.removeTag("version");

        PersonalDimensionData restored = new PersonalDimensionData();
        restored.readFromNBT(tag);

        assertThat(restored.getPlayerForDimension(7)).isEqualTo(PLAYER_A);
        assertThat(restored.getAllEntries()).hasSize(1);
    }

    /** 换绑维度 ID 时，旧的维度映射被移除，新映射生效。 */
    @Test
    public void testUpdateDimensionMappingRemovesOldMapping() {
        PersonalDimensionData data = new PersonalDimensionData();
        data.updateDimensionMapping(PLAYER_A, 10);
        assertThat(data.getPlayerForDimension(10)).isEqualTo(PLAYER_A);

        data.updateDimensionMapping(PLAYER_A, 20);

        assertThat(data.getPlayerForDimension(10)).isNull();
        assertThat(data.getEntryByDimensionId(10)).isNull();
        assertThat(data.getPlayerForDimension(20)).isEqualTo(PLAYER_A);
        assertThat(data.getEntry(PLAYER_A).dimensionId).isEqualTo(20);
    }

    /** 从未分配状态（MIN_VALUE）首次绑定维度时不会误删任何映射。 */
    @Test
    public void testUpdateDimensionMappingFromUnassigned() {
        PersonalDimensionData data = new PersonalDimensionData();
        data.updateDimensionMapping(PLAYER_A, 10);
        data.updateDimensionMapping(PLAYER_B, 20);

        // B 从未分配状态绑定时，不应移除 A 的映射
        assertThat(data.getPlayerForDimension(10)).isEqualTo(PLAYER_A);
        assertThat(data.getPlayerForDimension(20)).isEqualTo(PLAYER_B);
    }

    /** removeEntry 同步清理反向索引，条目与维度映射都不复存在。 */
    @Test
    public void testRemoveEntryCleansReverseIndex() {
        PersonalDimensionData data = new PersonalDimensionData();
        data.updateDimensionMapping(PLAYER_A, 10);

        data.removeEntry(PLAYER_A);

        assertThat(data.getAllEntries()).isEmpty();
        assertThat(data.getPlayerForDimension(10)).isNull();
        assertThat(data.getEntryByDimensionId(10)).isNull();
    }

    /** 移除未分配维度的条目时不触碰反向索引。 */
    @Test
    public void testRemoveEntryWithoutDimensionKeepsIndex() {
        PersonalDimensionData data = new PersonalDimensionData();
        data.updateDimensionMapping(PLAYER_A, 10);
        data.getEntry(PLAYER_B);

        data.removeEntry(PLAYER_B);

        assertThat(data.getAllEntries()).hasSize(1);
        assertThat(data.getPlayerForDimension(10)).isEqualTo(PLAYER_A);
    }

    /** copyFrom 迁移全部条目与反向索引。 */
    @Test
    public void testCopyFromMigratesEntriesAndIndex() {
        PersonalDimensionData legacy = new PersonalDimensionData("legacy");
        legacy.updateDimensionMapping(PLAYER_A, 10);
        legacy.updateDimensionMapping(PLAYER_B, -5);

        PersonalDimensionData target = new PersonalDimensionData();
        target.copyFrom(legacy);

        assertThat(target.getAllEntries()).hasSize(2);
        assertThat(target.getPlayerForDimension(10)).isEqualTo(PLAYER_A);
        assertThat(target.getPlayerForDimension(-5)).isEqualTo(PLAYER_B);
        assertThat(target.getEntryByDimensionId(10).playerId).isEqualTo(PLAYER_A);
    }

    /** copyFrom 会清空目标实例已有的数据，而不是合并。 */
    @Test
    public void testCopyFromReplacesExistingData() {
        PersonalDimensionData legacy = new PersonalDimensionData("legacy");
        legacy.updateDimensionMapping(PLAYER_A, 10);

        PersonalDimensionData target = new PersonalDimensionData();
        target.updateDimensionMapping(PLAYER_B, 99);
        target.copyFrom(legacy);

        assertThat(target.getAllEntries()).hasSize(1);
        assertThat(target.getPlayerForDimension(99)).isNull();
        assertThat(target.getPlayerForDimension(10)).isEqualTo(PLAYER_A);
    }

    /** readFromNBT 跳过 playerUUID 非法的条目，合法条目不受影响。 */
    @Test
    public void testReadFromNbtSkipsInvalidUuidEntries() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("version", 1);
        NBTTagList list = new NBTTagList();

        NBTTagCompound bad = new NBTTagCompound();
        bad.setString("playerUUID", "not-a-uuid");
        bad.setInteger("dimensionId", 11);
        list.appendTag(bad);

        PlayerDimEntry good = new PlayerDimEntry(PLAYER_A);
        good.dimensionId = 22;
        list.appendTag(good.writeToNBT());

        tag.setTag("entries", list);

        PersonalDimensionData data = new PersonalDimensionData();
        data.readFromNBT(tag);

        assertThat(data.getAllEntries()).hasSize(1);
        assertThat(data.getPlayerForDimension(22)).isEqualTo(PLAYER_A);
        assertThat(data.getPlayerForDimension(11)).isNull();
    }

    /** readFromNBT 清空旧数据后再加载，不会残留之前的条目与索引。 */
    @Test
    public void testReadFromNbtClearsPreviousState() {
        PersonalDimensionData data = new PersonalDimensionData();
        data.updateDimensionMapping(PLAYER_A, 10);

        PersonalDimensionData source = new PersonalDimensionData();
        source.updateDimensionMapping(PLAYER_B, 20);
        data.readFromNBT(source.writeToNBT(new NBTTagCompound()));

        assertThat(data.getAllEntries()).hasSize(1);
        assertThat(data.getPlayerForDimension(10)).isNull();
        assertThat(data.getPlayerForDimension(20)).isEqualTo(PLAYER_B);
    }

    /** 修改操作（映射更新、规则、进入点、返回点、删除、copyFrom）均置脏标记。 */
    @Test
    public void testMutationsMarkDirty() {
        PersonalDimensionData data = new PersonalDimensionData();
        assertThat(data.isDirty()).isFalse();

        data.updateDimensionMapping(PLAYER_A, 10);
        assertThat(data.isDirty()).isTrue();

        data.setDirty(false);
        data.setRules(PLAYER_A, new PersonalDimensionRules());
        assertThat(data.isDirty()).isTrue();

        data.setDirty(false);
        data.setEntryPoint(PLAYER_A, new BlockPos(1, 2, 3));
        assertThat(data.isDirty()).isTrue();

        data.setDirty(false);
        data.setReturnPoint(PLAYER_A, 0, 0, 0, 0, 0, 0);
        assertThat(data.isDirty()).isTrue();

        data.setDirty(false);
        data.removeEntry(PLAYER_A);
        assertThat(data.isDirty()).isTrue();

        data.setDirty(false);
        data.copyFrom(new PersonalDimensionData());
        assertThat(data.isDirty()).isTrue();
    }
}
