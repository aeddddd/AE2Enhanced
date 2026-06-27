package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockHyperdimensionalCasing extends Block {

    public BlockHyperdimensionalCasing() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "hyperdimensional_casing");
        setTranslationKey(AE2Enhanced.MOD_ID + ".hyperdimensional_casing");
        setHardness(4.0F);
        setResistance(8.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }
}
