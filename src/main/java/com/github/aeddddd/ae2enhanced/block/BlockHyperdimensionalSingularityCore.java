package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockHyperdimensionalSingularityCore extends Block {

    public BlockHyperdimensionalSingularityCore() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "hyperdimensional_singularity_core");
        setTranslationKey(AE2Enhanced.MOD_ID + ".hyperdimensional_singularity_core");
        setHardness(4.0F);
        setResistance(8.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }
}
