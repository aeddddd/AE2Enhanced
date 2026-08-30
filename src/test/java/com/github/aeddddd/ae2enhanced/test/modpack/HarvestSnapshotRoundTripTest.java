package com.github.aeddddd.ae2enhanced.test.modpack;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.github.aeddddd.ae2enhanced.diag.harvest.HarvestSnapshot;

/**
 * 快照序列化 round-trip:采集端写出的 JSON 必须能被 Fixture 侧无损读回
 * （字段遗漏/类型漂移的回归防护）.
 */
public class HarvestSnapshotRoundTripTest {

    @TempDir
    File dir;

    @Test
    public void testRoundTrip() throws Exception {
        HarvestSnapshot snapshot = new HarvestSnapshot();
        snapshot.timestamp = "20260829-230403";
        snapshot.mods.put("appliedenergistics2", "v0.56.7");
        snapshot.mods.put("thaumcraft", "6.1.BETA26");

        HarvestSnapshot.StackRef iron = new HarvestSnapshot.StackRef();
        iron.id = "minecraft:iron_ingot";
        iron.count = 1;
        HarvestSnapshot.StackRef ironOreA = new HarvestSnapshot.StackRef();
        ironOreA.id = "minecraft:iron_ore";
        HarvestSnapshot.StackRef ironOreB = new HarvestSnapshot.StackRef();
        ironOreB.id = "contenttweaker:iron_ore_fake";
        snapshot.oreDict.put("oreIron", java.util.Arrays.asList(ironOreA, ironOreB));

        HarvestSnapshot.CraftEntry craft = new HarvestSnapshot.CraftEntry();
        craft.name = "minecraft:iron_block";
        craft.type = "shaped_ore";
        craft.width = 3;
        craft.height = 3;
        HarvestSnapshot.StackRef block = new HarvestSnapshot.StackRef();
        block.id = "minecraft:iron_block";
        craft.output = block;
        craft.slots.add(java.util.Arrays.asList(iron, ironOreA)); // 矿词槽:两候选
        craft.slots.add(java.util.Collections.emptyList()); // 空槽
        craft.returnedSlots = java.util.Collections.singletonList(0); // 首槽容器物返还
        snapshot.crafting.add(craft);

        HarvestSnapshot.FurnaceEntry furnace = new HarvestSnapshot.FurnaceEntry();
        furnace.input = ironOreA;
        furnace.output = iron;
        snapshot.furnace.add(furnace);

        HarvestSnapshot.MachineEntry machine = new HarvestSnapshot.MachineEntry();
        machine.mod = "extendedcrafting";
        machine.machine = "combination";
        machine.type = "combination";
        machine.inputs.add(java.util.Collections.singletonList(iron));
        machine.outputs.add(block);
        machine.extras.put("cost", "1000000");
        snapshot.machines.add(machine);

        snapshot.stats.craftingTotal = 1;
        snapshot.stats.machineTotal = 1;
        snapshot.stats.adapters.put("extendedcrafting", "ok");

        File file = new File(this.dir, "harvest-test.json");
        snapshot.save(file);
        HarvestSnapshot loaded = HarvestSnapshot.load(file);

        assertThat(loaded.formatVersion).isEqualTo(2);
        assertThat(loaded.timestamp).isEqualTo("20260829-230403");
        assertThat(loaded.mods).containsEntry("thaumcraft", "6.1.BETA26");
        assertThat(loaded.oreDict.get("oreIron")).hasSize(2);
        assertThat(loaded.crafting).hasSize(1);
        HarvestSnapshot.CraftEntry loadedCraft = loaded.crafting.get(0);
        assertThat(loadedCraft.type).isEqualTo("shaped_ore");
        assertThat(loadedCraft.width).isEqualTo(3);
        assertThat(loadedCraft.output.id).isEqualTo("minecraft:iron_block");
        assertThat(loadedCraft.slots.get(0)).hasSize(2);
        assertThat(loadedCraft.slots.get(1)).isEmpty();
        assertThat(loadedCraft.returnedSlots).containsExactly(0);
        assertThat(loaded.furnace.get(0).output.id).isEqualTo("minecraft:iron_ingot");
        assertThat(loaded.machines.get(0).extras).containsEntry("cost", "1000000");
        assertThat(loaded.stats.adapters).containsEntry("extendedcrafting", "ok");
    }
}
