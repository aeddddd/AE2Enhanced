package com.github.aeddddd.ae2enhanced.data;

import java.util.Set;

import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.registry.ModBlocks;

/**
 * 方块战利品表数据生成器.
 */
public class AE2EBlockLootProvider extends BlockLootSubProvider {

    public AE2EBlockLootProvider() {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags());
    }

    @Override
    protected void generate() {
        for (RegistryObject<Block> block : ModBlocks.DR.getEntries()) {
            // 微型奇点是黑洞仪式方块,破坏后不掉落
            if (block.get() == ModBlocks.MICRO_SINGULARITY.get()) {
                add(block.get(), noDrop());
                continue;
            }
            dropSelf(block.get());
        }
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.DR.getEntries().stream().map(RegistryObject::get)::iterator;
    }
}
