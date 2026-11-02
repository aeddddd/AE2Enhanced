package com.github.aeddddd.ae2enhanced.item.memorycard.api;

import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import net.minecraft.world.World;

import java.util.List;

/** 中枢接口接收 UMC 绑定选择的端口。 */
public interface CentralBindingTarget {

    int bindSelections(World world, List<ItemUniversalMemoryCard.SelectionEntry> selections);
    int clearBindingSelections();
}
