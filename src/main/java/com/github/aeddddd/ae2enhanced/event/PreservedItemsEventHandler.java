package com.github.aeddddd.ae2enhanced.event;

import com.github.aeddddd.ae2enhanced.item.ItemConformalCharge;
import com.github.aeddddd.ae2enhanced.omnitool.ItemAdvancedMEOmniTool;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.entity.player.PlayerDropsEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.Iterator;

public final class PreservedItemsEventHandler {
    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.crafting;
        if (!(result.getItem() instanceof ItemAdvancedMEOmniTool)) return;
        boolean hasTool = false;
        boolean hasConformal = false;
        ItemStack original = ItemStack.EMPTY;
        for (int i = 0; i < event.craftMatrix.getSizeInventory(); i++) {
            ItemStack input = event.craftMatrix.getStackInSlot(i);
            if (input.isEmpty()) continue;
            if (input.getItem() instanceof ItemAdvancedMEOmniTool) { hasTool = true; original = input; }
            else if (input.getItem() instanceof ItemConformalCharge) hasConformal = true;
        }
        if (hasTool && hasConformal) {
            if (!original.isEmpty() && original.hasTagCompound()) result.setTagCompound(original.getTagCompound().copy());
            OmniToolUpgrades.setConformalCharge(result, true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerDrops(PlayerDropsEvent event) {
        Iterator<EntityItem> iterator = event.getDrops().iterator();
        NBTTagList preserved = new NBTTagList();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next().getItem();
            if (stack.getItem() instanceof ItemAdvancedMEOmniTool && OmniToolUpgrades.hasConformalCharge(stack)) {
                iterator.remove();
                preserved.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
        }
        if (preserved.tagCount() > 0) event.getEntityPlayer().getEntityData().setTag("AE2E_ConformalPreserved", preserved);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        NBTTagCompound data = event.player.getEntityData();
        if (!data.hasKey("AE2E_ConformalPreserved", Constants.NBT.TAG_LIST)) return;
        NBTTagList preserved = data.getTagList("AE2E_ConformalPreserved", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < preserved.tagCount(); i++) {
            ItemStack stack = new ItemStack(preserved.getCompoundTagAt(i));
            if (!stack.isEmpty()) event.player.inventory.addItemStackToInventory(stack);
        }
        data.removeTag("AE2E_ConformalPreserved");
    }
}
