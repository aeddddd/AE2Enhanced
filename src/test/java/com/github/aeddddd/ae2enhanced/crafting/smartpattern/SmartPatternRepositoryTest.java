package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemRegistry;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import com.github.aeddddd.ae2enhanced.item.crafting.SmartPatternMergeHelper;
import com.github.aeddddd.ae2enhanced.runtime.RuntimeScopes;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import com.github.aeddddd.ae2enhanced.test.util.CompatibilityFixtures;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmartPatternRepositoryTest {
    private static final UUID ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final String FIXTURE = "smartpattern/smartpattern_" + ID + ".dat";

    @TempDir
    Path tempDir;
    private World world;
    private SmartPatternRepository repository;

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
        ResourceLocation blankId = new ResourceLocation("ae2enhanced:smart_blank_pattern");
        Item blank = ForgeRegistries.ITEMS.getValue(blankId);
        if (blank == null) {
            blank = new com.github.aeddddd.ae2enhanced.item.ItemSmartBlankPattern();
            ForgeRegistries.ITEMS.register(blank);
        }
        ItemRegistry.SMART_BLANK_PATTERN = (com.github.aeddddd.ae2enhanced.item.ItemSmartBlankPattern) blank;
        if (net.minecraft.tileentity.TileEntity.getKey(TileSmartPatternInterface.class) == null) {
            net.minecraftforge.fml.common.registry.GameRegistry.registerTileEntity(
                    TileSmartPatternInterface.class, "ae2enhanced:smart_pattern_interface");
        }
    }

    private static SmartPatternData data(UUID id, int recipes) {
        List<SmartRecipe> list = new ArrayList<>();
        for (int i = 0; i < recipes; i++) {
            IAEItemStack output = AEItemStack.fromItemStack(new ItemStack(Items.STICK, 1, i));
            list.add(new SmartRecipe(new IAEItemStack[0], new IAEItemStack[]{output}, false));
        }
        return new SmartPatternData(id, "minecraft:furnace", list);
    }

    @BeforeEach
    void setUp() {
        world = mock(World.class);
        ISaveHandler saveHandler = mock(ISaveHandler.class);
        when(saveHandler.getWorldDirectory()).thenReturn(tempDir.toFile());
        when(world.getSaveHandler()).thenReturn(saveHandler);
        when(world.getBlockState(BlockPos.ORIGIN)).thenReturn(net.minecraft.init.Blocks.AIR.getDefaultState());
        startScope();
        repository = SmartPatternRepository.get(world);
    }

    @AfterEach
    void tearDown() throws Exception {
        RuntimeScopes.closeServer();
        RuntimeScopes.releaseServer();
        com.github.aeddddd.ae2enhanced.test.util.TemporaryDirectoryCleaner.clearContents(tempDir);
    }

    @Test
    void realV1FileMigratesOnceAndStaleEmbeddedDataCannotOverwrite() {
        CompatibilityFixtures.copy(FIXTURE,
                tempDir.resolve("ae2enhanced/storage/smartpattern_" + ID + ".dat"));
        SmartPatternData legacyFile = repository.get(ID).data();
        assertThat(repository.get(ID).revision()).isZero();

        SmartPatternData embedded = new SmartPatternData(ID, "minecraft:crafting_table",
                legacyFile.getRecipes());
        embedded.setDisabledMask(legacyFile.getDisabledMask());
        SmartPatternRepository.UpdateResult migrated = repository.migrate(embedded, 0L);
        SmartPatternRepository.UpdateResult stale = repository.migrate(legacyFile, 0L);

        assertThat(migrated.committed()).isTrue();
        assertThat(migrated.snapshot().revision()).isEqualTo(1L);
        assertThat(stale.status()).isEqualTo(SmartPatternRepository.Status.STALE);
        assertThat(repository.get(ID).data().getTargetBlockId()).isEqualTo("minecraft:crafting_table");
        restartScope();
        assertThat(repository.get(ID).revision()).isEqualTo(1L);
        assertThat(repository.get(ID).data().getTargetBlockId()).isEqualTo("minecraft:crafting_table");
    }

    @Test
    void doubleEditorCasRejectsSecondStaleWriter() {
        SmartPatternData original = data(ID, 1);
        assertThat(repository.migrate(original, 0L).committed()).isTrue();
        long firstRead = repository.get(ID).revision();

        SmartPatternRepository.UpdateResult first = repository.edit(ID, firstRead, data -> {
            data.addEmptyRecipe();
            return data;
        });
        SmartPatternRepository.UpdateResult second = repository.edit(ID, firstRead, data -> {
            data.addEmptyRecipe();
            return data;
        });

        assertThat(first.committed()).isTrue();
        assertThat(second.status()).isEqualTo(SmartPatternRepository.Status.STALE);
        assertThat(repository.get(ID).revision()).isEqualTo(firstRead + 1);
        assertThat(repository.get(ID).data().getRecipeCount()).isEqualTo(2);
    }

    @Test
    void copiedItemsReloadSharedContentButKeepIndependentDisabledMasks() {
        repository.migrate(data(ID, 2), 0L);
        long revision = repository.get(ID).revision();
        BitSet firstMask = new BitSet();
        BitSet secondMask = new BitSet();
        secondMask.set(0);
        ItemStack firstCopy = ItemSmartPattern.createPattern(ID, firstMask, 2, "minecraft:furnace", 0L);
        ItemStack secondCopy = ItemSmartPattern.createPattern(ID, secondMask, 2, "minecraft:furnace", revision);

        assertThat(ItemSmartPattern.expandPatterns(firstCopy, world)).hasSize(2);
        assertThat(ItemSmartPattern.expandPatterns(secondCopy, world)).hasSize(1);
        repository.edit(ID, revision, data -> {
            data.addEmptyRecipe();
            return data;
        });

        assertThat(ItemSmartPattern.expandPatterns(firstCopy, world)).hasSize(3);
        assertThat(ItemSmartPattern.getRevision(firstCopy)).isZero();
        assertThat(ItemSmartPattern.getDisabledMask(firstCopy).isEmpty()).isTrue();
        assertThat(ItemSmartPattern.getDisabledMask(secondCopy).get(0)).isTrue();
        assertThat(repository.get(ID).revision()).isEqualTo(revision + 1);
    }

    @Test
    void recyclingOneCopyDoesNotDeleteSharedFile() {
        repository.migrate(data(ID, 1), 0L);
        ItemStack copy = ItemSmartPattern.createPattern(ID, new BitSet(), 1,
                "minecraft:furnace", repository.get(ID).revision());
        net.minecraft.inventory.InventoryCrafting matrix = new net.minecraft.inventory.InventoryCrafting(
                mock(net.minecraft.inventory.Container.class), 1, 1);
        matrix.setInventorySlotContents(0, copy);
        net.minecraft.entity.player.EntityPlayer player = mock(net.minecraft.entity.player.EntityPlayer.class);
        player.world = world;

        new SmartPatternMergeHelper().onItemCrafted(new net.minecraftforge.fml.common.gameevent.PlayerEvent
                .ItemCraftedEvent(player, new ItemStack(ItemRegistry.SMART_BLANK_PATTERN), matrix));

        assertThat(repository.get(ID)).isNotNull();
        assertThat(ItemSmartPattern.expandPatterns(copy, world)).hasSize(1);
    }

    @Test
    void multipleTilesShareAuthorityAndStaleTileReloadsInsteadOfOverwriting() {
        TileSmartPatternInterface first = tile();
        TileSmartPatternInterface second = tile();
        SmartPatternData initial = data(ID, 1);
        assertThat(first.bindWithData(BlockPos.ORIGIN, 0, initial.getTargetBlockId(), initial)).isTrue();
        assertThat(second.bindWithData(BlockPos.ORIGIN, 0, initial.getTargetBlockId(), initial)).isFalse();
        assertThat(second.getPatternRevision()).isEqualTo(first.getPatternRevision());

        long stale = second.getPatternRevision();
        assertThat(first.addNewRecipe(first.getPatternRevision())).isTrue();
        assertThat(second.addNewRecipe(stale)).isFalse();

        assertThat(second.getPatternRevision()).isEqualTo(first.getPatternRevision());
        assertThat(second.getPatternData().getRecipeCount()).isEqualTo(2);
        long sharedRevision = repository.get(ID).revision();
        assertThat(second.toggleRecipe(0, sharedRevision)).isTrue();
        assertThat(repository.get(ID).revision()).isEqualTo(sharedRevision);
        assertThat(first.getPatternData().getDisabledMask().isEmpty()).isTrue();
        assertThat(second.getPatternData().getDisabledMask().isEmpty()).isFalse();
        NBTTagCompound saved = second.writeToNBT(new NBTTagCompound());
        assertThat(saved.hasKey("patternData")).isFalse();
        assertThat(saved.hasKey("recipeDisplay")).isFalse();
        assertThat(saved.hasKey("miniGuiInventory")).isFalse();
        assertThat(saved.getLong("revision")).isEqualTo(first.getPatternRevision());
        assertThat(second.getUpdateTag().hasKey("patternData")).isTrue();
    }

    @Test
    void restartUsesPersistedRevisionAndLastCommittedContent() {
        repository.migrate(data(ID, 1), 0L);
        long revision = repository.get(ID).revision();
        repository.edit(ID, revision, data -> {
            data.addEmptyRecipe();
            return data;
        });
        long committed = repository.get(ID).revision();

        restartScope();
        assertThat(repository.get(ID).revision()).isEqualTo(committed);
        assertThat(repository.get(ID).data().getRecipeCount()).isEqualTo(2);
        assertThat(repository.edit(ID, revision, data -> data).status())
                .isEqualTo(SmartPatternRepository.Status.STALE);
    }

    @Test
    void interruptedSaveKeepsOldFileAndDoesNotAdvanceMemoryRevision() {
        repository.migrate(data(ID, 1), 0L);
        long revision = repository.get(ID).revision();
        SmartPatternStorageFile.Adapter files = SmartPatternStorageFile.adapter(world);
        SmartPatternRepository failing = new SmartPatternRepository(new SmartPatternStorageFile.Adapter() {
            @Override
            public SmartPatternStorageFile.Stored load(UUID id) {
                return files.load(id);
            }

            @Override
            public boolean save(UUID id, SmartPatternStorageFile.Stored stored) {
                return false;
            }
        });

        SmartPatternRepository.UpdateResult failed = failing.edit(ID, revision, data -> {
            data.addEmptyRecipe();
            return data;
        });
        assertThat(failed.status()).isEqualTo(SmartPatternRepository.Status.SAVE_FAILED);
        assertThat(failing.get(ID).revision()).isEqualTo(revision);
        restartScope();
        assertThat(repository.get(ID).revision()).isEqualTo(revision);
        assertThat(repository.get(ID).data().getRecipeCount()).isEqualTo(1);
    }

    @Test
    void crashAfterFileCommitLeavesItemStaleAndReadOnly() {
        repository.migrate(data(ID, 1), 0L);
        ItemStack itemBeforeCrash = ItemSmartPattern.createPattern(ID, new BitSet(), 1,
                "minecraft:furnace", repository.get(ID).revision());
        repository.edit(ID, ItemSmartPattern.getRevision(itemBeforeCrash), data -> {
            data.addEmptyRecipe();
            return data;
        });
        long fileRevision = repository.get(ID).revision();

        restartScope();
        assertThat(ItemSmartPattern.getRevision(itemBeforeCrash)).isLessThan(fileRevision);
        assertThat(repository.edit(ID, ItemSmartPattern.getRevision(itemBeforeCrash), data -> {
            data.addEmptyRecipe();
            return data;
        }).status()).isEqualTo(SmartPatternRepository.Status.STALE);
        assertThat(ItemSmartPattern.expandPatterns(itemBeforeCrash, world)).hasSize(2);
        assertThat(repository.get(ID).revision()).isEqualTo(fileRevision);
    }

    @Test
    void deployedListenerRefreshesAndCanBeUnregistered() {
        repository.migrate(data(ID, 1), 0L);
        AtomicInteger invalidations = new AtomicInteger();
        SmartPatternRepository.ListenerRegistration registration = repository.listen(ID,
                (id, revision) -> invalidations.incrementAndGet());
        repository.edit(ID, repository.get(ID).revision(), data -> {
            data.addEmptyRecipe();
            return data;
        });
        assertThat(invalidations).hasValue(1);

        registration.close();
        repository.edit(ID, repository.get(ID).revision(), data -> {
            data.addEmptyRecipe();
            return data;
        });
        assertThat(invalidations).hasValue(1);
    }

    private TileSmartPatternInterface tile() {
        TileSmartPatternInterface tile = new TileSmartPatternInterface();
        tile.setWorld(world);
        tile.setPos(BlockPos.ORIGIN);
        return tile;
    }

    private void restartScope() {
        RuntimeScopes.closeServer();
        RuntimeScopes.releaseServer();
        startScope();
        repository = SmartPatternRepository.get(world);
    }

    private void startScope() {
        if (RuntimeScopes.currentServer().isPresent()) {
            RuntimeScopes.closeServer();
            RuntimeScopes.releaseServer();
        }
        RuntimeScopes.startServer();
        RuntimeScopes.bindServerSaveRoot(tempDir);
    }
}
