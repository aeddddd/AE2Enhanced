package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * GTCEu yellow_stripes_block_b 的替代方块，用于个人维度地板预设。
 */
public class BlockYellowStripesBlockB extends Block {

    public BlockYellowStripesBlockB() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "yellow_stripes_block_b");
        setTranslationKey(AE2Enhanced.MOD_ID + ".yellow_stripes_block_b");
        setHardness(4.0F);
        setResistance(8.0F);
        setHarvestLevel("pickaxe", 1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }
}
