package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.CompatibilityFixtures;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 固定 UUID 的 SmartPattern v1 文件、Tile 与物品旧 NBT 兼容测试。 */
class SmartPatternLegacyCompatibilityTest {

    private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String FILE = "smartpattern/smartpattern_" + ID + ".dat";

    @TempDir
    Path tempDir;
    private World world;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
        ResourceLocation id = new ResourceLocation("ae2enhanced:smart_pattern");
        Item registered = ForgeRegistries.ITEMS.getValue(id);
        if (registered == null) {
            registered = new ItemSmartPattern();
            ForgeRegistries.ITEMS.register(registered);
        }
        ItemRegistry.SMART_PATTERN = (ItemSmartPattern) registered;
    }

    static void startScope() {
        if (com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.currentServer().isPresent()) {
            com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.closeServer();
            com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.releaseServer();
        }
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.startServer();
    }

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        ISaveHandler saveHandler = mock(ISaveHandler.class);
        when(saveHandler.getWorldDirectory()).thenReturn(tempDir.toFile());
        when(world.getSaveHandler()).thenReturn(saveHandler);
        startScope();
        CompatibilityFixtures.copy(FILE,
                tempDir.resolve("ae2enhanced/storage/smartpattern_" + ID + ".dat"));
    }

    @AfterEach
    void tearDown() throws Exception {
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.closeServer();
        com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes.releaseServer();
        com.github.aeddddd.ae2enhanced.test.util.TemporaryDirectoryCleaner.clearContents(tempDir);
    }

    @Test
    void staticV1FileLoadsRecipeIdentityAndDisabledMask() {
        SmartPatternData data = SmartPatternStorageFile.load(world, ID);

        assertThat(data).isNotNull();
        assertThat(data.getPatternDataId()).isEqualTo(ID);
        assertThat(data.getTargetBlockId()).isEqualTo("minecraft:furnace");
        assertThat(data.getRecipeCount()).isEqualTo(1);
        assertThat(data.getDisabledMask().get(0)).isTrue();
        assertThat(data.getRecipes().get(0).getInputs()[0].getStackSize()).isEqualTo(2L);
        assertThat(data.getRecipes().get(0).getPrimaryOutput().getItem()).isEqualTo(Items.STICK);
        assertThat(data.getRecipes().get(0).getPrimaryOutput().getStackSize()).isEqualTo(4L);
    }

    @Test
    void tileWithoutEmbeddedDataFallsBackToStaticFile() {
        TileSmartPatternInterface tile = new TileSmartPatternInterface();
        tile.setWorld(world);
        tile.readFromNBT(CompatibilityFixtures.readNbt("smartpattern/tile-v0.nbt"));

        assertThat(tile.getPatternData()).isNotNull();
        assertThat(tile.getPatternData().getPatternDataId()).isEqualTo(ID);
        assertThat(tile.getBoundBlockId()).isEqualTo("minecraft:furnace");
        assertThat(tile.getBoundPos().getX()).isEqualTo(10);
        assertThat(tile.getBoundPos().getY()).isEqualTo(64);
        assertThat(tile.getBoundPos().getZ()).isEqualTo(-7);
        assertThat(tile.getPatternData().getDisabledMask().get(0)).isTrue();
    }

    @Test
    void embeddedTileDataHasPriorityOverFileWithSameUuid() {
        NBTTagCompound tileTag = CompatibilityFixtures.readNbt("smartpattern/tile-v0.nbt");
        NBTTagCompound embedded = CompatibilityFixtures.readNbt(FILE);
        embedded.setString("targetBlockId", "minecraft:crafting_table");
        embedded.setString("disabledMask", "");
        tileTag.setTag("patternData", embedded);
        TileSmartPatternInterface tile = new TileSmartPatternInterface();
        tile.setWorld(world);

        tile.readFromNBT(tileTag);

        assertThat(tile.getPatternData().getPatternDataId()).isEqualTo(ID);
        assertThat(tile.getPatternData().getTargetBlockId()).isEqualTo("minecraft:crafting_table");
        assertThat(tile.getPatternData().getDisabledMask().get(0)).isFalse();
    }

    @Test
    void legacyItemNbtKeepsMinimalSmartPatternIdentity() {
        ItemStack stack = new ItemStack(CompatibilityFixtures.readNbt("smartpattern/item-v0.nbt"));

        assertThat(stack.getItem()).isInstanceOf(ItemSmartPattern.class);
        assertThat(ItemSmartPattern.getPatternDataId(stack)).isEqualTo(ID);
        assertThat(ItemSmartPattern.getDisabledMask(stack).get(0)).isTrue();
        assertThat(ItemSmartPattern.getRecipeCount(stack)).isEqualTo(1);
        assertThat(ItemSmartPattern.getTargetBlockId(stack)).isEqualTo("minecraft:furnace");
    }
}
