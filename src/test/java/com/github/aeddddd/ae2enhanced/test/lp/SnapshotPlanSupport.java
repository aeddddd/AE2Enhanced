package com.github.aeddddd.ae2enhanced.test.lp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;

import com.github.aeddddd.ae2enhanced.diag.plansnapshot.PlanSnapshot;
import com.github.aeddddd.ae2enhanced.specialcrafting.RecursiveCraftingHelper;
import com.github.aeddddd.ae2enhanced.test.support.SimulationEnv;

/**
 * 快照驱动的计划测试共享支持(基准测试/回放测试共用):
 * StackRef → AE 物品重建、库存映射、快照样板 mock.
 */
final class SnapshotPlanSupport {

    private SnapshotPlanSupport() {
    }

    /** 重建库存映射(canon 键 → 数量;与 LpCraftingJob 同口径). */
    static Map<IAEItemStack, Long> stockOf(SimulationEnv env) {
        Map<IAEItemStack, Long> stock = new HashMap<>();
        for (IAEItemStack stack : env.networkStorage()) {
            if (stack.getStackSize() > 0) {
                stock.merge(RecursiveCraftingHelper.canon(stack), stack.getStackSize(), Long::sum);
            }
        }
        return stock;
    }

    /** StackRef → AE 物品(dummy Item + meta + NBT(JsonToNBT 解析 SNBT)). */
    static IAEItemStack toAe(PlanSnapshot.StackRef ref, Map<String, Item> items) {
        if (ref == null || ref.id == null) {
            return null;
        }
        Item item = items.computeIfAbsent(ref.id, id -> {
            Item it = new Item();
            it.setRegistryName(new ResourceLocation(id));
            return it;
        });
        NBTTagCompound nbt = null;
        if (ref.nbt != null && !ref.nbt.isEmpty()) {
            try {
                nbt = JsonToNBT.getTagFromJson(ref.nbt);
            } catch (Exception e) {
                System.out.println("[SNAP] NBT 解析失败(" + ref.id + "): " + ref.nbt + " — 按无 NBT 处理");
                nbt = null;
            }
        }
        int count = ref.count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1, ref.count);
        IAEItemStack ae = AEItemStack.fromItemStack(new ItemStack(item, count, ref.meta, nbt));
        if (ae == null) {
            return null;
        }
        IAEItemStack canon = ae.copy();
        canon.setStackSize(Math.max(1, ref.count > Integer.MAX_VALUE ? Integer.MAX_VALUE : ref.count));
        return canon;
    }

    /** 由快照构建等价模拟环境(库存 + 全部样板). */
    static SimulationEnv envOf(PlanSnapshot snap, Map<String, Item> items) {
        SimulationEnv env = new SimulationEnv();
        for (PlanSnapshot.StackRef ref : snap.stock) {
            IAEItemStack stack = toAe(ref, items);
            if (stack != null && ref.count > 0) {
                IAEItemStack s = stack.copy();
                s.setStackSize(ref.count);
                env.addStoredItem(s);
            }
        }
        for (PlanSnapshot.PatternEntry entry : snap.patterns) {
            env.addPattern(new SnapshotPattern(entry, items));
        }
        return env;
    }

    /** 快照样板 → 等价 mock ICraftingPatternDetails(按采集面逐字段还原). */
    static final class SnapshotPattern implements ICraftingPatternDetails {
        private final IAEItemStack[] inputs;
        private final IAEItemStack[] outputs;
        private final boolean craftable;
        private final boolean canSubstitute;
        private final int priority;
        private final IAEItemStack[] rawSlots;
        private final Map<Integer, List<IAEItemStack>> substitutes;

        SnapshotPattern(PlanSnapshot.PatternEntry e, Map<String, Item> items) {
            this.inputs = toArray(e.inputs, items);
            this.outputs = toArray(e.outputs, items);
            this.craftable = e.craftable;
            this.canSubstitute = e.canSubstitute;
            this.priority = e.priority;
            this.rawSlots = e.rawSlots == null ? null : toArray(e.rawSlots, items);
            this.substitutes = new HashMap<>();
            if (e.substitutes != null) {
                for (Map.Entry<Integer, List<PlanSnapshot.StackRef>> en : e.substitutes.entrySet()) {
                    List<IAEItemStack> cands = new ArrayList<>();
                    for (PlanSnapshot.StackRef ref : en.getValue()) {
                        IAEItemStack ae = toAe(ref, items);
                        if (ae != null) {
                            cands.add(ae);
                        }
                    }
                    if (!cands.isEmpty()) {
                        this.substitutes.put(en.getKey(), cands);
                    }
                }
            }
        }

        private static IAEItemStack[] toArray(List<PlanSnapshot.StackRef> refs, Map<String, Item> items) {
            List<IAEItemStack> list = new ArrayList<>();
            for (PlanSnapshot.StackRef ref : refs) {
                IAEItemStack ae = toAe(ref, items);
                if (ae != null) {
                    list.add(ae);
                }
            }
            return list.toArray(new IAEItemStack[0]);
        }

        @Override
        public ItemStack getPattern() {
            return new ItemStack(net.minecraft.init.Items.PAPER);
        }

        @Override
        public boolean isValidItemForSlot(int slot, ItemStack stack, World world) {
            return true;
        }

        @Override
        public boolean isCraftable() {
            return this.craftable;
        }

        @Override
        public IAEItemStack[] getInputs() {
            return (this.rawSlots != null ? this.rawSlots : this.inputs).clone();
        }

        @Override
        public IAEItemStack[] getCondensedInputs() {
            return this.inputs.clone();
        }

        @Override
        public IAEItemStack[] getCondensedOutputs() {
            return this.outputs.clone();
        }

        @Override
        public IAEItemStack[] getOutputs() {
            return this.outputs.clone();
        }

        @Override
        public boolean canSubstitute() {
            return this.canSubstitute;
        }

        @Override
        public List<IAEItemStack> getSubstituteInputs(int slot) {
            return this.substitutes.getOrDefault(slot, java.util.Collections.emptyList());
        }

        @Override
        public ItemStack getOutput(net.minecraft.inventory.InventoryCrafting ic, World w) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getPriority() {
            return this.priority;
        }

        @Override
        public void setPriority(int p) {
        }
    }
}
