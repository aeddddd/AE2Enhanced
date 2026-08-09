package com.github.aeddddd.ae2enhanced.chamber;

import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Long 级物品缓存库：每种物品类型（item+meta+NBT 区分）占一格,
 * 每格可缓存 long 数量级. 用于奇点处理仓的输入/输出缓冲.
 *
 * key 格式与黑洞配方一致（{@link BlackHoleRecipe#keyOf}）,保证与配方索引互通.
 */
public class LongItemStore {

    public static class Entry {
        private final ItemStack template;
        private long count;

        Entry(ItemStack template, long count) {
            this.template = template;
            this.count = count;
        }

        public ItemStack getTemplate() {
            return template.copy();
        }

        public long getCount() {
            return count;
        }
    }

    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
    private final int maxTypes;

    public LongItemStore(int maxTypes) {
        this.maxTypes = maxTypes;
    }

    public static String keyOf(ItemStack stack) {
        return BlackHoleRecipe.keyOf(stack);
    }

    /**
     * 插入物品,返回未能插入的数量.
     */
    public long insert(ItemStack stack, long amount) {
        if (stack.isEmpty() || amount <= 0) {
            return amount;
        }
        String key = keyOf(stack);
        Entry entry = entries.get(key);
        if (entry != null) {
            entry.count = safeAdd(entry.count, amount);
            return 0;
        }
        if (entries.size() >= maxTypes) {
            return amount;
        }
        ItemStack template = stack.copy();
        template.setCount(1);
        entries.put(key, new Entry(template, amount));
        return 0;
    }

    /**
     * 抽取指定 key 的物品,返回实际抽取数量.
     */
    public long extract(String key, long amount) {
        Entry entry = entries.get(key);
        if (entry == null || amount <= 0) {
            return 0;
        }
        long taken = Math.min(entry.count, amount);
        entry.count -= taken;
        if (entry.count <= 0) {
            entries.remove(key);
        }
        return taken;
    }

    public long getCount(String key) {
        Entry entry = entries.get(key);
        return entry != null ? entry.count : 0;
    }

    public ItemStack getTemplate(String key) {
        Entry entry = entries.get(key);
        return entry != null ? entry.getTemplate() : ItemStack.EMPTY;
    }

    public Collection<Entry> getEntries() {
        return entries.values();
    }

    public int getTypeCount() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private static long safeAdd(long a, long b) {
        long r = a + b;
        return r < 0 ? Long.MAX_VALUE : r;
    }

    // ---- NBT ----

    public NBTTagList writeToNBT() {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            NBTTagCompound itemTag = new NBTTagCompound();
            e.getValue().template.writeToNBT(itemTag);
            tag.setTag("Item", itemTag);
            tag.setLong("Count", e.getValue().count);
            list.appendTag(tag);
        }
        return list;
    }

    public void readFromNBT(NBTTagList list) {
        entries.clear();
        for (int i = 0; i < list.tagCount() && entries.size() < maxTypes; i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            ItemStack template = new ItemStack(tag.getCompoundTag("Item"));
            if (template.isEmpty()) {
                continue;
            }
            template.setCount(1);
            long count = tag.getLong("Count");
            if (count > 0) {
                entries.put(keyOf(template), new Entry(template, count));
            }
        }
    }
}
