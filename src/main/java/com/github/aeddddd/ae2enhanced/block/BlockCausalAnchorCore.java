package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

public class BlockCausalAnchorCore extends Block {

    public BlockCausalAnchorCore() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "causal_anchor_core");
        setTranslationKey(AE2Enhanced.MOD_ID + ".causal_anchor_core");
        setHardness(5.0F);
        setResistance(10.0F);
        setHarvestLevel("pickaxe", 2);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }
}
