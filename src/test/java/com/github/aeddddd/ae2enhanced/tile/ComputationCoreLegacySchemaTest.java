package com.github.aeddddd.ae2enhanced.tile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.items.misc.ItemEncodedPattern;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.CompatibilityFixtures;

/**
 * 原生 CPU/ComputationCore 静态 schema fixture 验收。
 * 普通 JUnit 不应用 Mixin，真实 CPU 恢复与特殊配额重建明确留给 L2/L3。
 */
class ComputationCoreLegacySchemaTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        ResourceLocation id = new ResourceLocation("appliedenergistics2:encoded_pattern");
        if (ForgeRegistries.ITEMS.getValue(id) == null) {
            Item item = new ItemEncodedPattern();
            item.setRegistryName(id);
            ForgeRegistries.ITEMS.register(item);
        }
    }

    @Test
    void staticFixtureContainsOuterCoreAndNativeCpuSchemas() {
        NBTTagCompound root = CompatibilityFixtures.readNbt("computation/legacy-v0.nbt");
        assertThat(root.getBoolean("formed")).isTrue();
        assertThat(root.getInteger("parallelLimit")).isEqualTo(8192);
        NBTTagList pool = root.getTagList("cpuPool", Constants.NBT.TAG_COMPOUND);
        assertThat(pool.tagCount()).isEqualTo(1);

        NBTTagCompound cpu = pool.getCompoundTagAt(0);
        assertThat(cpu.getKeySet()).contains("finalOutput", "inventory", "tasks", "waitingFor",
                "elapsedTime", "startItemCount", "remainingItemCount", "ae2eSpecial", "ae2eSpecialTotals");
        assertThat(cpu.getBoolean("waiting")).isTrue();
        assertThat(cpu.getBoolean("ae2eSpecial")).isTrue();
        assertThat(cpu.getLong("remainingItemCount")).isEqualTo(12L);
        assertThat(cpu.getTagList("tasks", 10).tagCount()).isEqualTo(1);
        assertThat(cpu.getTagList("waitingFor", 10).tagCount()).isEqualTo(1);
        assertThat(cpu.getTagList("tasks", 10).getCompoundTagAt(0).getLong("craftingProgress")).isEqualTo(7L);
        assertThat(cpu.getTagList("waitingFor", 10).getCompoundTagAt(0).getLong("Cnt")).isEqualTo(12L);
    }

    @Test
    void specialTotalsAndTasksUseRealAe2EncodedPatternPayload() {
        NBTTagCompound cpu = CompatibilityFixtures.readNbt("computation/legacy-v0.nbt")
                .getTagList("cpuPool", 10).getCompoundTagAt(0);
        NBTTagCompound task = cpu.getTagList("tasks", 10).getCompoundTagAt(0);
        NBTTagList totals = cpu.getTagList("ae2eSpecialTotals", 10);
        assertThat(totals.tagCount()).isEqualTo(1);
        assertThat(totals.getCompoundTagAt(0).getLong("total")).isEqualTo(21L);

        ItemStack taskPattern = new ItemStack(task);
        ItemStack totalPattern = new ItemStack(totals.getCompoundTagAt(0));
        assertThat(taskPattern.getItem()).isInstanceOf(ICraftingPatternItem.class);
        assertThat(totalPattern.getItem()).isInstanceOf(ItemEncodedPattern.class);
        assertThat(ItemStack.areItemStacksEqual(taskPattern, totalPattern)).isTrue();
        NBTTagCompound encoded = taskPattern.getTagCompound();
        assertThat(encoded.getBoolean("crafting")).isFalse();
        assertThat(encoded.getTagList("in", 10).tagCount()).isEqualTo(1);
        assertThat(encoded.getTagList("out", 10).tagCount()).isEqualTo(1);
        assertThat(encoded.getTagList("in", 10).getCompoundTagAt(0).getString("id"))
                .isEqualTo("minecraft:cobblestone");
        assertThat(encoded.getTagList("out", 10).getCompoundTagAt(0).getString("id"))
                .isEqualTo("minecraft:stick");
    }
}
