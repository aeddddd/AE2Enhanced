package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.test.util.CompatibilityFixtures;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 缺 version、旧 permissions 与真实 MapStorage 数据名迁移的静态 fixture 验收。 */
class PersonalDimensionLegacyCompatibilityTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GUEST = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void missingVersionLoadsWhitelistAndRulesWhileLegacyPermissionsRemainIgnored() {
        NBTTagCompound legacy = CompatibilityFixtures.readNbt("personal/legacy-v0.nbt");
        assertThat(legacy.hasKey("version")).isFalse();
        PersonalDimensionData data = new PersonalDimensionData();

        data.readFromNBT(legacy);

        PlayerDimEntry entry = data.getEntryByDimensionId(77);
        assertThat(entry).isNotNull();
        assertThat(entry.playerId).isEqualTo(OWNER);
        assertThat(entry.allowedPlayers).containsExactly(GUEST);
        assertThat(entry.rules.lockTime).isTrue();
        assertThat(entry.rules.timeValue).isEqualTo(12000L);
        NBTTagCompound written = data.writeToNBT(new NBTTagCompound());
        assertThat(written.getInteger("version")).isEqualTo(2);
        assertThat(written.hasKey("unknownLegacyRoot")).isFalse();
        assertThat(written.getTagList("entries", 10).getCompoundTagAt(0).hasKey("permissions")).isFalse();
    }

    @Test
    void realMapStorageMigratesLegacyDataNameWhenCurrentNameIsAbsent() {
        MapStorage storage = new MapStorage(null);
        PersonalDimensionData legacy = new PersonalDimensionData("ae2enhanced:personal_dimensions");
        legacy.readFromNBT(CompatibilityFixtures.readNbt("personal/legacy-v0.nbt"));
        storage.setData("ae2enhanced:personal_dimensions", legacy);
        World world = mock(World.class);
        when(world.getMapStorage()).thenReturn(storage);

        PersonalDimensionData migrated = PersonalDimensionData.get(world);

        assertThat(migrated).isNotSameAs(legacy);
        assertThat(migrated.getEntryByDimensionId(77).playerId).isEqualTo(OWNER);
        assertThat(migrated.isDirty()).isTrue();
        assertThat(storage.getOrLoadData(PersonalDimensionData.class, "ae2enhanced_personal_dimensions"))
                .isSameAs(migrated);
        assertThat(PersonalDimensionData.get(world)).as("新名已存在后不得重复覆盖").isSameAs(migrated);
    }
}
