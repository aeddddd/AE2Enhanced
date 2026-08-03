package com.github.aeddddd.ae2enhanced.test.dimension;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.DyeColor;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.api.dimension.FloorColorScheme;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionData;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.dimension.PlayerDimEntry;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PersonalDimensionData} 单元测试.
 */
class PersonalDimensionDataTest {

    private static final UUID PLAYER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @BeforeAll
    static void bootstrap() {
        // setColorScheme / 条目反序列化依赖方块注册表
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void getEntryShouldCreateAndReuseEntry() {
        PersonalDimensionData data = new PersonalDimensionData();

        PlayerDimEntry first = data.getEntry(PLAYER_A);
        PlayerDimEntry second = data.getEntry(PLAYER_A);

        assertThat(first).isSameAs(second);
        assertThat(first.playerId).isEqualTo(PLAYER_A);
        assertThat(data.getAllEntries()).containsExactly(first);
    }

    @Test
    void setRulesShouldStoreDefensiveCopyAndMarkDirty() {
        PersonalDimensionData data = new PersonalDimensionData();
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.disableMobSpawning = true;
        rules.timeValue = 1000L;

        data.setRules(PLAYER_A, rules);
        // 修改原对象不应影响已存储的副本
        rules.timeValue = -1L;

        assertThat(data.getEntry(PLAYER_A).rules.disableMobSpawning).isTrue();
        assertThat(data.getEntry(PLAYER_A).rules.timeValue).isEqualTo(1000L);
        assertThat(data.isDirty()).isTrue();
    }

    @Test
    void setColorSchemeShouldStoreDefensiveCopyAndMarkDirty() {
        PersonalDimensionData data = new PersonalDimensionData();
        FloorColorScheme scheme = FloorColorScheme.ofConcrete(DyeColor.RED, DyeColor.WHITE, DyeColor.BLACK);

        data.setColorScheme(PLAYER_A, scheme);
        scheme.setConcrete(com.github.aeddddd.ae2enhanced.api.dimension.FloorColorRole.ROAD_BASE, DyeColor.LIME);

        assertThat(data.getEntry(PLAYER_A).colorScheme)
                .isNotSameAs(scheme);
        assertThat(data.isDirty()).isTrue();
    }

    @Test
    void removeEntryShouldDropEntryAndMarkDirty() {
        PersonalDimensionData data = new PersonalDimensionData();
        data.getEntry(PLAYER_A);

        data.removeEntry(PLAYER_A);

        assertThat(data.getAllEntries()).isEmpty();
        assertThat(data.isDirty()).isTrue();
    }

    @Test
    void saveLoadRoundTripShouldPreserveEntries() {
        PersonalDimensionData data = new PersonalDimensionData();
        data.getEntry(PLAYER_A).created = true;
        PersonalDimensionRules rules = new PersonalDimensionRules();
        rules.lockTime = true;
        rules.timeValue = 7777L;
        data.setRules(PLAYER_A, rules);
        data.getEntry(PLAYER_B).returnDim = "minecraft:the_end";

        PersonalDimensionData restored = PersonalDimensionData.load(data.save(new CompoundTag()));

        assertThat(restored.getAllEntries()).hasSize(2);
        PlayerDimEntry entryA = restored.getEntry(PLAYER_A);
        assertThat(entryA.created).isTrue();
        assertThat(entryA.rules.lockTime).isTrue();
        assertThat(entryA.rules.timeValue).isEqualTo(7777L);
        assertThat(restored.getEntry(PLAYER_B).returnDim).isEqualTo("minecraft:the_end");
    }

    @Test
    void saveShouldWriteVersionTag() {
        PersonalDimensionData data = new PersonalDimensionData();

        CompoundTag tag = data.save(new CompoundTag());

        assertThat(tag.getInt("version")).isEqualTo(1);
    }

    @Test
    void loadShouldSkipEntriesWithMalformedUuid() {
        PersonalDimensionData data = new PersonalDimensionData();
        data.getEntry(PLAYER_A);
        CompoundTag tag = data.save(new CompoundTag());

        ListTag list = tag.getList("entries", 10);
        CompoundTag bad = new CompoundTag();
        bad.putString("playerUUID", "not-a-uuid");
        list.add(bad);

        PersonalDimensionData restored = PersonalDimensionData.load(tag);

        assertThat(restored.getAllEntries()).hasSize(1);
        assertThat(restored.getAllEntries().iterator().next().playerId).isEqualTo(PLAYER_A);
    }

    @Test
    void loadEmptyTagShouldYieldEmptyData() {
        PersonalDimensionData restored = PersonalDimensionData.load(new CompoundTag());

        assertThat(restored.getAllEntries()).isEmpty();
    }
}
