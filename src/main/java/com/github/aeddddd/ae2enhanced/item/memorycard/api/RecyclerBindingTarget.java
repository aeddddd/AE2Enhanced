package com.github.aeddddd.ae2enhanced.item.memorycard.api;

import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import net.minecraft.world.World;

import java.util.List;

/** 回收器接收 UMC 绑定选择的端口。 */
public interface RecyclerBindingTarget {

    int bindSelections(World world, List<ItemUniversalMemoryCard.SelectionEntry> selections);
    int clearBindingSelections();
}
