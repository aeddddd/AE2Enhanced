package com.github.aeddddd.ae2enhanced.api.dimension;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 地板样式中可换色的颜色角色;每个角色对应样式内的一种默认占位方块,
 * 玩家的颜色方案即以角色为单位替换占位方块.
 */
public enum FloorColorRole {

    /** 马路基色(默认灰色混凝土). */
    ROAD_BASE(Blocks.GRAY_CONCRETE, DyeColor.GRAY),

    /** 马路标线(默认白色混凝土). */
    ROAD_LINE(Blocks.WHITE_CONCRETE, DyeColor.WHITE),

    /** 平台基色(默认黑色混凝土). */
    PLATFORM_BASE(Blocks.BLACK_CONCRETE, DyeColor.BLACK);

    private final Block placeholder;
    private final DyeColor defaultColor;

    FloorColorRole(Block placeholder, DyeColor defaultColor) {
        this.placeholder = placeholder;
        this.defaultColor = defaultColor;
    }

    /** 样式中代表该角色的占位方块. */
    public Block getPlaceholder() {
        return placeholder;
    }

    /** 该角色的默认染料色. */
    public DyeColor getDefaultColor() {
        return defaultColor;
    }
}
