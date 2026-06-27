package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockAssemblyStabilizer extends Block {

    public BlockAssemblyStabilizer() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "assembly_stabilizer");
        setTranslationKey(AE2Enhanced.MOD_ID + ".assembly_stabilizer");
        setHardness(4.0F);
        setResistance(8.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }
}
