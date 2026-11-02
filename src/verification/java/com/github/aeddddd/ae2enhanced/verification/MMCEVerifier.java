package com.github.aeddddd.ae2enhanced.verification;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternRepository;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartRecipe;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MMCEVerifier {
    private static final Logger LOGGER = LogManager.getLogger("AE2E-MMCE-VERIFY");
    private static final String PROVIDER_CLASS = "github.kasuminova.mmce.common.tile.MEPatternProvider";

    private MMCEVerifier() {
    }

    static void verify(WorldServer world) throws Exception {
        Class<?> providerClass = Class.forName(PROVIDER_CLASS);
        Object provider = providerClass.newInstance();
        BlockPos pos = world.getSpawnPoint().add(0, 12, 0);
        world.setTileEntity(pos, (TileEntity) provider);
        try {
            verifySmartPatterns(world, providerClass, provider);
        } finally {
            world.removeTileEntity(pos);
        }
    }

    private static void verifySmartPatterns(WorldServer world, Class<?> providerClass, Object provider) throws Exception {
        UUID id = UUID.randomUUID();
        SmartPatternRepository repository = SmartPatternRepository.get(world);
        SmartPatternData initial = new SmartPatternData(id, "minecraft:furnace", Arrays.asList(
                recipe(Items.APPLE, Items.BREAD),
                recipe(Items.BONE, Items.DIAMOND)));
        require(repository.migrate(initial, 0L).committed(), "MMCE initial SmartPattern commit failed");

        ItemStack pattern = ItemSmartPattern.createPattern(id, new BitSet(), 2, "minecraft:furnace", 1L);
        Field patternsField = providerClass.getDeclaredField("patterns");
        patternsField.setAccessible(true);
        AppEngInternalInventory patterns = (AppEngInternalInventory) patternsField.get(provider);
        patterns.setStackInSlot(0, pattern);

        Method refreshPatterns = providerClass.getDeclaredMethod("refreshPatterns");
        refreshPatterns.setAccessible(true);
        refreshPatterns.invoke(provider);
        require(detailCount(providerClass, provider) == 2, "MMCE initial expanded detail count is not 2");

        require(repository.edit(id, 1L, data -> {
            data.appendRecipe(recipe(Items.CARROT, Items.EMERALD));
            return data;
        }).committed(), "MMCE SmartPattern edit failed");
        require(detailCount(providerClass, provider) == 3,
                "MMCE deployed listener did not refresh expanded details");

        ICraftingPatternDetails extra = firstExtra(providerClass, provider);
        Method setCurrentPattern = providerClass.getDeclaredMethod("setCurrentPattern", ICraftingPatternDetails.class);
        setCurrentPattern.setAccessible(true);
        setCurrentPattern.invoke(provider, extra);
        Field currentPatternIdx = providerClass.getDeclaredField("currentPatternIdx");
        currentPatternIdx.setAccessible(true);
        require(currentPatternIdx.getInt(provider) == 0,
                "MMCE enhanced blocking mode did not resolve the smart-pattern slot");

        String version = Loader.instance().getIndexedModList().get("modularmachinery").getVersion();
        LOGGER.info("MMCE_PASS version={} expandedAfterEdit=3 blockingSlot=0", version);
    }

    private static int detailCount(Class<?> providerClass, Object provider) throws Exception {
        Field detailsField = providerClass.getDeclaredField("details");
        detailsField.setAccessible(true);
        Object details = detailsField.get(provider);
        int count = 0;
        if (details instanceof List) {
            for (Object detail : (List<?>) details) {
                if (detail != null) {
                    count++;
                }
            }
        } else {
            for (Object detail : (Object[]) details) {
                if (detail != null) {
                    count++;
                }
            }
        }
        Field extrasField = providerClass.getDeclaredField("ae2e$extraDetails");
        extrasField.setAccessible(true);
        Map<?, ?> extras = (Map<?, ?>) extrasField.get(provider);
        for (Object value : extras.values()) {
            count += ((List<?>) value).size();
        }
        return count;
    }

    private static ICraftingPatternDetails firstExtra(Class<?> providerClass, Object provider) throws Exception {
        Field extrasField = providerClass.getDeclaredField("ae2e$extraDetails");
        extrasField.setAccessible(true);
        Map<?, ?> extras = (Map<?, ?>) extrasField.get(provider);
        for (Object value : extras.values()) {
            List<?> details = (List<?>) value;
            if (!details.isEmpty()) {
                return (ICraftingPatternDetails) details.get(0);
            }
        }
        throw new IllegalStateException("MMCE extra details are empty");
    }

    private static SmartRecipe recipe(Item input, Item output) {
        IAEItemStack inputStack = AEItemStack.fromItemStack(new ItemStack(input));
        IAEItemStack outputStack = AEItemStack.fromItemStack(new ItemStack(output));
        return new SmartRecipe(new IAEItemStack[]{inputStack}, new IAEItemStack[]{outputStack}, false);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
