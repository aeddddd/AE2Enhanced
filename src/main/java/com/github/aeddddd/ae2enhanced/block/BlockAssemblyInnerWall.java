package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockAssemblyInnerWall extends Block {

    public BlockAssemblyInnerWall() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "assembly_inner_wall");
        setTranslationKey(AE2Enhanced.MOD_ID + ".assembly_inner_wall");
        setHardness(4.0F);
        setResistance(8.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }
}
