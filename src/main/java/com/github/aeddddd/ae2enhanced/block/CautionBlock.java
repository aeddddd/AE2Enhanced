package com.github.aeddddd.ae2enhanced.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/**
 * GTCEu yellow_stripes_block_b 的替代方块,用于个人维度地板预设(移植自 1.12 主分支).
 */
public class CautionBlock extends Block {

    public CautionBlock() {
        super(Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                // 对齐 1.12:硬度 4.0,爆炸抗性 8.0,需要镐挖掘
                .strength(4.0F, 8.0F)
                .requiresCorrectToolForDrops());
    }
}
