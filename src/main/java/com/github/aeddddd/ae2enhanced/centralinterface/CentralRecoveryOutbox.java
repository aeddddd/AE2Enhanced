package com.github.aeddddd.ae2enhanced.centralinterface;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import java.util.*;

/** 物理恢复余量的独立持久收件点；不把未知内容或恢复产物混入虚拟合成队列。 */
public final class CentralRecoveryOutbox {
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final List<NBTTagCompound> rawEntries = new ArrayList<>();
    private final Runnable changeListener;

    public CentralRecoveryOutbox(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public void put(UUID id, List<ItemStack> items, List<FluidStack> fluids) {
        Entry entry = entries.get(id);
        if (entry == null) {
            entry = new Entry(id);
            entries.put(id, entry);
        }
        entry.items = new ArrayList<>(items);
        entry.fluids = new ArrayList<>(fluids);
        changeListener.run();
    }

    public Entry get(UUID id) {
        return entries.get(id);
    }

    public List<Entry> entries() {
        return new ArrayList<>(entries.values());
    }

    public boolean remove(UUID id) {
        boolean changed = entries.remove(id) != null;
        if (changed) changeListener.run();
        return changed;
    }

    public boolean isEmpty() {
        return entries.isEmpty() && rawEntries.isEmpty();
    }

    public void writeToNBT(NBTTagCompound data) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound raw : rawEntries) list.appendTag(raw.copy());
        for (Entry entry : entries.values()) {
            list.appendTag(entry.writeToNBT());
        }
        if (list.tagCount() > 0) data.setTag("centralRecoveryOutbox", list);
        else data.removeTag("centralRecoveryOutbox");
    }

    public void readFromNBT(NBTTagCompound data) {
        entries.clear();
        rawEntries.clear();
        if (data == null || !data.hasKey("centralRecoveryOutbox", 9)) return;
        NBTTagList list = data.getTagList("centralRecoveryOutbox", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            boolean unknownRoot = false;
            for (String key : tag.getKeySet()) {
                if (!key.equals("idMost") && !key.equals("idLeast") && !key.equals("attempts")
                        && !key.equals("items") && !key.equals("fluids")) {
                    unknownRoot = true;
                    break;
                }
            }
            if (unknownRoot) {
                rawEntries.add(tag.copy());
                continue;
            }
            UUID id = tag.getUniqueId("id");
            Entry entry = new Entry(id);
            entry.attempts = tag.getInteger("attempts");
            NBTTagList items = tag.getTagList("items", 10);
            for (int j = 0; j < items.tagCount(); j++) {
                NBTTagCompound itemTag = items.getCompoundTagAt(j);
                net.minecraft.item.Item item = net.minecraft.item.Item.REGISTRY.getObject(
                        new net.minecraft.util.ResourceLocation(itemTag.getString("id")));
                int count = itemTag.getInteger("Count");
                if (item != null && count > 0) {
                    ItemStack stack = new ItemStack(itemTag);
                    stack.setCount(count);
                    entry.items.add(stack);
                } else {
                    entry.rawItems.add(itemTag.copy());
                }
            }
            NBTTagList fluids = tag.getTagList("fluids", 10);
            for (int j = 0; j < fluids.tagCount(); j++) {
                NBTTagCompound fluidTag = fluids.getCompoundTagAt(j);
                FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluidTag);
                if (fluid != null && fluid.amount > 0) entry.fluids.add(fluid);
                else entry.rawFluids.add(fluidTag.copy());
            }
            if (entry.items.isEmpty() && entry.fluids.isEmpty()
                    && (!entry.rawItems.isEmpty() || !entry.rawFluids.isEmpty())) {
                rawEntries.add(tag.copy());
            } else {
                entries.put(id, entry);
            }
        }
    }

    public static final class Entry {
        public final UUID id;
        private final List<NBTTagCompound> rawItems = new ArrayList<>();
        private final List<NBTTagCompound> rawFluids = new ArrayList<>();
        public List<ItemStack> items = new ArrayList<>();
        public List<FluidStack> fluids = new ArrayList<>();
        public int attempts;

        private Entry(UUID id) {
            this.id = id;
        }

        public boolean hasPreservedContent() {
            return !rawItems.isEmpty() || !rawFluids.isEmpty();
        }

        public void countAttempt(Runnable changeListener) {
            attempts++;
            changeListener.run();
        }

        private NBTTagCompound writeToNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setUniqueId("id", id);
            tag.setInteger("attempts", attempts);
            NBTTagList itemList = new NBTTagList();
            for (NBTTagCompound raw : rawItems) itemList.appendTag(raw.copy());
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    NBTTagCompound itemTag = stack.serializeNBT();
                    itemTag.setInteger("Count", stack.getCount());
                    itemList.appendTag(itemTag);
                }
            }
            tag.setTag("items", itemList);
            NBTTagList fluidList = new NBTTagList();
            for (NBTTagCompound raw : rawFluids) fluidList.appendTag(raw.copy());
            for (FluidStack fluid : fluids) {
                if (fluid != null && fluid.amount > 0) fluidList.appendTag(fluid.writeToNBT(new NBTTagCompound()));
            }
            tag.setTag("fluids", fluidList);
            return tag;
        }
    }
}
