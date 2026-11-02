package com.github.aeddddd.ae2enhanced.dimension;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrivateDimensionDataTest {
    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @Test
    void legacyPersonalEntrySurvivesVersionTwoRoundTrip() {
        UUID player = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        PlayerDimEntry legacy = new PlayerDimEntry(player);
        legacy.dimensionId = 123;
        legacy.hasReturnPoint = true;
        legacy.returnDim = 0;
        legacy.returnX = 10.5;
        legacy.returnY = 70;
        legacy.returnZ = -4.5;
        legacy.allowedPlayers.add(guest);
        legacy.rules.flightEnabled = true;
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("version", 1);
        net.minecraft.nbt.NBTTagList entries = new net.minecraft.nbt.NBTTagList();
        entries.appendTag(legacy.writeToNBT());
        root.setTag("entries", entries);

        PersonalDimensionData data = new PersonalDimensionData();
        data.readFromNBT(root);
        PlayerDimEntry loaded = data.peekEntry(player);
        assertThat(loaded).isNotNull();
        assertThat(loaded.dimensionId).isEqualTo(123);
        assertThat(loaded.allowedPlayers).contains(guest);
        assertThat(loaded.hasReturnPoint).isTrue();
        assertThat(loaded.rules.flightEnabled).isTrue();
        assertThat(loaded.floor.getFloorStyleId()).isEqualTo(FloorDefinition.DEFAULT_STYLE);
        assertThat(data.getPreference(player)).isEqualTo(DimensionPreference.AUTO);
        assertThat(data.writeToNBT(new NBTTagCompound()).getInteger("version")).isEqualTo(2);
    }

    @Test
    void teamBindingPreferenceAndQuarantineRoundTrip() {
        PersonalDimensionData data = new PersonalDimensionData();
        UUID player = UUID.randomUUID();
        data.setPreference(player, DimensionPreference.TEAM);
        TeamDimEntry entry = data.getTeamEntry("team", "binding-a");
        data.updateTeamDimensionMapping("team", 456);
        entry.entryPoint = new net.minecraft.util.math.BlockPos(8, 65, -8);
        entry.floor.setStyle("grid", new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15});

        PersonalDimensionData loaded = new PersonalDimensionData();
        loaded.readFromNBT(data.writeToNBT(new NBTTagCompound()));
        assertThat(loaded.getPreference(player)).isEqualTo(DimensionPreference.TEAM);
        assertThat(loaded.peekTeamEntry("team").bindingId).isEqualTo("binding-a");
        assertThat(loaded.getTeamEntryByDimensionId(456)).isNotNull();
        assertThat(loaded.getOwnerByDimensionId(456).scope).isEqualTo(DimensionOwner.Scope.TEAM);

        loaded.quarantineTeamEntry("team");
        assertThat(loaded.peekTeamEntry("team")).isNull();
        assertThat(loaded.getOrphanedTeamEntries()).hasSize(1);
        TeamDimEntry orphan = loaded.getOrphanedTeamEntries().iterator().next();
        assertThat(orphan.frozen).isTrue();
        assertThat(orphan.dimensionId).isEqualTo(456);
        assertThat(orphan.bindingId).isEqualTo("binding-a");
        assertThat(loaded.getTeamEntry("team", "binding-b").dimensionId).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void floorDefinitionAndConcretePalettePreserveRevisionAndDyeMetadata() {
        FloorDefinition definition = new FloorDefinition();
        int[] colors = definition.getColorScheme();
        colors[1] = EnumDyeColor.BLUE.getMetadata();
        definition.setStyle("test_grid", colors);
        definition.setStyle("test_grid", colors);
        assertThat(definition.getRevision()).isEqualTo(1);
        FloorStyleRegistry.register("test_grid", new FloorPreset(1, 1,
                new net.minecraft.block.state.IBlockState[]{Blocks.CONCRETE.getStateFromMeta(1)}, new int[]{0}));

        FloorPreset resolved = FloorStyleRegistry.resolve(definition);
        assertThat(resolved.getState(0, 0).getBlock()).isEqualTo(Blocks.CONCRETE);
        assertThat(resolved.getState(0, 0).getBlock().getMetaFromState(resolved.getState(0, 0)))
                .isEqualTo(EnumDyeColor.BLUE.getMetadata());

        FloorDefinition loaded = new FloorDefinition();
        loaded.readFromNBT(definition.writeToNBT());
        assertThat(loaded.getFloorStyleId()).isEqualTo("test_grid");
        assertThat(loaded.getColorScheme()[1]).isEqualTo(EnumDyeColor.BLUE.getMetadata());
        assertThat(loaded.getRevision()).isEqualTo(1);
    }

    @Test
    void repaintTaskAdvancesPastTheFinalRegionBeforeCompleting() {
        FloorRepaintTask task = FloorRepaintTask.create(3, 0, 0, 0);
        task.advanceRegion();

        assertThat(task.getNextRegionZ()).isGreaterThan(0);
        assertThat(task.getStatus()).isEqualTo(FloorRepaintTask.Status.COMPLETE);
        assertThat(task.getPendingChunks()).isEmpty();
    }

    @Test
    void repaintTaskPersistsCursorAndRejectsStaleCancellation() {
        FloorRepaintTask task = FloorRepaintTask.create(7, 40, -40, 2);
        long token = task.nextScanToken();
        task.addScannedChunks(java.util.Collections.singletonList(FloorRepaintTask.chunkKey(40, -40)));
        task.advanceRegion();
        FloorRepaintTask loaded = FloorRepaintTask.read(task.writeToNBT());
        assertThat(loaded.getTaskId()).isEqualTo(task.getTaskId());
        assertThat(loaded.getRevision()).isEqualTo(7);
        assertThat(loaded.getScanToken()).isEqualTo(token);
        assertThat(loaded.containsChunk(40, -40)).isTrue();
        assertThat(loaded.removeChunk(40, -40)).isTrue();
        loaded.cancel();
        assertThat(loaded.getStatus()).isEqualTo(FloorRepaintTask.Status.CANCELLED);
        assertThat(loaded.getPendingChunks()).isEmpty();
        assertThat(loaded.getScanToken()).isGreaterThan(token);
    }
}
