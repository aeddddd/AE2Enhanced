package com.github.aeddddd.ae2enhanced.specialcrafting;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.observation.PlanSnapshotObservation;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.text.SimpleDateFormat;
import java.util.*;

/** 把规划器输入投影为与实现无关的诊断快照。 */
public final class PlanSnapshotFactory {

    private PlanSnapshotFactory() {
    }

    public static PlanSnapshotObservation capture(ICraftingGrid cc, NetworkPatternIndex index, IAEItemStack root,
            Map<IAEItemStack, Long> stock) {
        PlanSnapshotObservation snapshot = new PlanSnapshotObservation();
        snapshot.timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        snapshot.root = toRef(root);
        for (Map.Entry<IAEItemStack, Long> entry : stock.entrySet()) {
            PlanSnapshotObservation.StackRef ref = toRef(entry.getKey());
            if (ref != null && entry.getValue() > 0) {
                ref.count = entry.getValue();
                snapshot.stock.add(ref);
            }
        }
        Set<ICraftingPatternDetails> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IAEItemStack key : index.keyGraphKeys()) {
            for (ICraftingPatternDetails pattern : index.patternsFor(key)) {
                if (seen.add(pattern)) {
                    snapshot.patterns.add(toEntry(pattern));
                }
            }
            for (ICraftingPatternDetails pattern : index.byproductMap().getOrDefault(key, Collections.emptyList())) {
                if (seen.add(pattern)) {
                    snapshot.patterns.add(toEntry(pattern));
                }
            }
        }
        return snapshot;
    }

    private static PlanSnapshotObservation.StackRef toRef(IAEItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemStack definition = stack.getDefinition();
        if (definition.isEmpty() || definition.getItem().getRegistryName() == null) {
            return null;
        }
        PlanSnapshotObservation.StackRef ref = new PlanSnapshotObservation.StackRef();
        ref.id = definition.getItem().getRegistryName().toString();
        ref.meta = definition.getMetadata();
        ref.count = stack.getStackSize();
        NBTTagCompound tag = definition.getTagCompound();
        if (tag != null && !tag.isEmpty()) {
            ref.nbt = tag.toString();
        }
        return ref;
    }

    private static PlanSnapshotObservation.PatternEntry toEntry(ICraftingPatternDetails pattern) {
        PlanSnapshotObservation.PatternEntry entry = new PlanSnapshotObservation.PatternEntry();
        for (IAEItemStack input : pattern.getCondensedInputs()) {
            PlanSnapshotObservation.StackRef ref = toRef(input);
            if (ref != null) {
                entry.inputs.add(ref);
            }
        }
        for (IAEItemStack output : pattern.getCondensedOutputs()) {
            PlanSnapshotObservation.StackRef ref = toRef(output);
            if (ref != null) {
                entry.outputs.add(ref);
            }
        }
        entry.craftable = pattern.isCraftable();
        entry.canSubstitute = pattern.canSubstitute();
        entry.priority = pattern.getPriority();
        if (entry.craftable && entry.canSubstitute) {
            entry.rawSlots = new ArrayList<>();
            IAEItemStack[] slots = pattern.getInputs();
            for (IAEItemStack slot : slots) {
                entry.rawSlots.add(toRef(slot));
            }
            entry.substitutes = new LinkedHashMap<>();
            for (int slot = 0; slot < slots.length; slot++) {
                List<IAEItemStack> substitutes = pattern.getSubstituteInputs(slot);
                if (substitutes == null || substitutes.isEmpty()) {
                    continue;
                }
                List<PlanSnapshotObservation.StackRef> candidates = new ArrayList<>();
                for (IAEItemStack substitute : substitutes) {
                    PlanSnapshotObservation.StackRef ref = toRef(substitute);
                    if (ref != null) {
                        candidates.add(ref);
                    }
                }
                if (!candidates.isEmpty()) {
                    entry.substitutes.put(slot, candidates);
                }
            }
        }
        Map<IAEItemStack, Long> returns = FlowReconciler.returnsPerCraft(pattern);
        if (!returns.isEmpty()) {
            entry.returns = new ArrayList<>();
            for (Map.Entry<IAEItemStack, Long> entryReturn : returns.entrySet()) {
                PlanSnapshotObservation.StackRef ref = toRef(entryReturn.getKey());
                if (ref != null) {
                    ref.count = entryReturn.getValue();
                    entry.returns.add(ref);
                }
            }
        }
        return entry;
    }
}
