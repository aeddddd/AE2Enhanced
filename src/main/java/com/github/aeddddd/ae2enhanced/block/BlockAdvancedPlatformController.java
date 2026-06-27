package com.github.aeddddd.ae2enhanced.block;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;

/**
 * 先进中枢平台控制器方块的占位实现.
 * 原平台功能已移除,现在仅作为装饰性方块保留.
 */
public class BlockAdvancedPlatformController extends Block {

    public BlockAdvancedPlatformController() {
        super(Material.IRON);
        setRegistryName(AE2Enhanced.MOD_ID, "advanced_platform_controller");
        setTranslationKey(AE2Enhanced.MOD_ID + ".advanced_platform_controller");
        setHardness(-1.0F);
        setResistance(6000000.0F);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
    }
}
