package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import net.minecraft.item.Item;

/**
 * 空白智能样板.
 * 可放入 ME 接口,但无实际配方,ME 接口会忽略它.
 * 需在智能样板接口 GUI 中编码后变为 {@link ItemSmartPattern}.
 */
public class ItemSmartBlankPattern extends Item {

    public ItemSmartBlankPattern() {
        setRegistryName(AE2Enhanced.MOD_ID, "smart_blank_pattern");
        setTranslationKey(AE2Enhanced.MOD_ID + ".smart_blank_pattern");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(64);
    }
}
