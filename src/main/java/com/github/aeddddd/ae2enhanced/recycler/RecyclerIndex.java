package com.github.aeddddd.ae2enhanced.recycler;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 物品类型 → 目标集合 的倒排索引.
 */
public class RecyclerIndex {

    private final Object2ObjectOpenHashMap<ItemDescriptor, ObjectOpenHashSet<TargetManager.TargetRef>> index =
            new Object2ObjectOpenHashMap<>();

    public void add(@Nonnull ItemDescriptor desc, @Nonnull TargetManager.TargetRef target) {
        ObjectOpenHashSet<TargetManager.TargetRef> set = index.get(desc);
        if (set == null) {
            set = new ObjectOpenHashSet<>();
            index.put(desc, set);
        }
        set.add(target);
    }

    public void remove(@Nonnull ItemDescriptor desc, @Nonnull TargetManager.TargetRef target) {
        ObjectOpenHashSet<TargetManager.TargetRef> set = index.get(desc);
        if (set != null) {
            set.remove(target);
            if (set.isEmpty()) {
                index.remove(desc);
            }
        }
    }

    public void clear() {
        index.clear();
    }

    @Nonnull
    public Set<TargetManager.TargetRef> getTargets(@Nonnull ItemDescriptor desc) {
        ObjectOpenHashSet<TargetManager.TargetRef> set = index.get(desc);
        return set == null ? Collections.emptySet() : Collections.unmodifiableSet(set);
    }

    @Nonnull
    public Set<ItemDescriptor> getAllTypes() {
        return Collections.unmodifiableSet(index.keySet());
    }

    public boolean contains(@Nonnull ItemDescriptor desc) {
        return index.containsKey(desc);
    }

    public void rebuild(@Nonnull Map<TargetManager.TargetRef, TargetAdapterSnapshot> snapshots) {
        clear();
        for (Map.Entry<TargetManager.TargetRef, TargetAdapterSnapshot> entry : snapshots.entrySet()) {
            TargetManager.TargetRef target = entry.getKey();
            for (ItemStack stack : entry.getValue().contents) {
                if (stack.isEmpty()) continue;
                add(new ItemDescriptor(stack), target);
            }
        }
    }

    /**
     * 目标快照.
     */
    public static final class TargetAdapterSnapshot {
        public final long lastChangeTick;
        public final java.util.List<ItemStack> contents;

        public TargetAdapterSnapshot(long lastChangeTick, java.util.List<ItemStack> contents) {
            this.lastChangeTick = lastChangeTick;
            this.contents = contents;
        }
    }
}
