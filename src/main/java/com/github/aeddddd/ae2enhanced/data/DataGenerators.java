package com.github.aeddddd.ae2enhanced.data;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraftforge.common.data.BlockTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.data.client.AE2EBlockStateProvider;
import com.github.aeddddd.ae2enhanced.data.client.AE2EItemModelProvider;

/**
 * 数据生成入口。
 * <p>集中注册方块状态、物品模型、配方、战利品表等数据生成器。</p>
 */
public class DataGenerators {

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // 客户端数据
        generator.addProvider(event.includeClient(), new AE2EBlockStateProvider(output, existingFileHelper));
        generator.addProvider(event.includeClient(), new AE2EItemModelProvider(output, existingFileHelper));

        // 服务端数据
        BlockTagsProvider blockTags = new BlockTagsProvider(output, lookupProvider, AE2Enhanced.MOD_ID, existingFileHelper) {
            @Override
            protected void addTags(HolderLookup.Provider provider) {
                // 后续可在此添加方块标签
            }
        };
        generator.addProvider(event.includeServer(), blockTags);
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Collections.emptySet(),
                List.of(new LootTableProvider.SubProviderEntry(AE2EBlockLootProvider::new, LootContextParamSets.BLOCK))));
        generator.addProvider(event.includeServer(), new AE2ERecipeProvider(output));
    }
}
