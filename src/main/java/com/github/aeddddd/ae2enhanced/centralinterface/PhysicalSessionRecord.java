package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 已提交物理批次的持久恢复记录；正常完成或显式回退后删除。 */
public final class PhysicalSessionRecord {
    public final UUID id;
    public final TargetBinding binding;
    public final IAEItemStack[] expectedOutputs;
    public final List<ItemStack> inputs;
    public final List<FluidStack> inputFluids;
    private final List<NBTTagCompound> rawOutputs = new ArrayList<>();
    private final List<NBTTagCompound> rawInputs = new ArrayList<>();
    private final List<NBTTagCompound> rawFluids = new ArrayList<>();
    private NBTTagCompound preservedRoot;

    private PhysicalSessionRecord(UUID id, TargetBinding binding, IAEItemStack[] expectedOutputs,
            List<ItemStack> inputs, List<FluidStack> inputFluids) {
        this.id = id;
        this.binding = binding;
        this.expectedOutputs = expectedOutputs;
        this.inputs = inputs;
        this.inputFluids = inputFluids;
    }

    public static PhysicalSessionRecord of(TargetSession session) {
        UUID id = session.getSessionId();
        if (id == null) throw new IllegalStateException("Physical session was not committed");
        return new PhysicalSessionRecord(id, session.getBinding(), session.getExpectedOutputs(),
                new ArrayList<>(session.getInputs()), new ArrayList<>(session.getInputFluids()));
    }

    public static PhysicalSessionRecord readFromNBT(NBTTagCompound tag) {
        UUID id = tag.getUniqueId("id");
        TargetBinding binding = TargetBinding.readFromNBT(tag.getCompoundTag("binding"));
        List<IAEItemStack> outputs = new ArrayList<>();
        List<NBTTagCompound> rawOutputs = new ArrayList<>();
        NBTTagList outputList = tag.getTagList("expectedOutputs", 10);
        for (int i = 0; i < outputList.tagCount(); i++) {
            NBTTagCompound entry = outputList.getCompoundTagAt(i);
            IAEItemStack stack = AEItemStack.fromNBT(entry);
            if (stack != null) outputs.add(stack); else rawOutputs.add(entry.copy());
        }
        List<ItemStack> inputs = new ArrayList<>();
        List<NBTTagCompound> rawInputs = new ArrayList<>();
        NBTTagList inputList = tag.getTagList("inputs", 10);
        for (int i = 0; i < inputList.tagCount(); i++) {
            NBTTagCompound entry = inputList.getCompoundTagAt(i);
            net.minecraft.item.Item item = net.minecraft.item.Item.REGISTRY.getObject(
                    new net.minecraft.util.ResourceLocation(entry.getString("id")));
            int count = entry.getInteger("Count");
            if (item != null && count > 0) {
                ItemStack stack = new ItemStack(entry);
                stack.setCount(count);
                inputs.add(stack);
            } else {
                rawInputs.add(entry.copy());
            }
        }
        List<FluidStack> fluids = new ArrayList<>();
        List<NBTTagCompound> rawFluids = new ArrayList<>();
        NBTTagList fluidList = tag.getTagList("inputFluids", 10);
        for (int i = 0; i < fluidList.tagCount(); i++) {
            NBTTagCompound entry = fluidList.getCompoundTagAt(i);
            FluidStack fluid = FluidStack.loadFluidStackFromNBT(entry);
            if (fluid != null && fluid.amount > 0) fluids.add(fluid); else rawFluids.add(entry.copy());
        }
        PhysicalSessionRecord record = new PhysicalSessionRecord(id, binding,
                outputs.toArray(new IAEItemStack[0]), inputs, fluids);
        record.rawOutputs.addAll(rawOutputs);
        record.rawInputs.addAll(rawInputs);
        record.rawFluids.addAll(rawFluids);
        record.preservedRoot = tag.copy();
        return record;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("id", id);
        tag.setTag("binding", binding.writeToNBT());
        NBTTagList outputs = new NBTTagList();
        for (NBTTagCompound raw : rawOutputs) outputs.appendTag(raw.copy());
        if (expectedOutputs != null) {
            for (IAEItemStack stack : expectedOutputs) {
                if (stack != null) {
                    NBTTagCompound entry = new NBTTagCompound();
                    stack.writeToNBT(entry);
                    outputs.appendTag(entry);
                }
            }
        }
        tag.setTag("expectedOutputs", outputs);
        NBTTagList inputsTag = new NBTTagList();
        for (NBTTagCompound raw : rawInputs) inputsTag.appendTag(raw.copy());
        for (ItemStack stack : inputs) {
            if (!stack.isEmpty()) {
                NBTTagCompound itemTag = stack.serializeNBT();
                itemTag.setInteger("Count", stack.getCount());
                inputsTag.appendTag(itemTag);
            }
        }
        tag.setTag("inputs", inputsTag);
        NBTTagList fluidsTag = new NBTTagList();
        for (NBTTagCompound raw : rawFluids) fluidsTag.appendTag(raw.copy());
        for (FluidStack fluid : inputFluids) {
            if (fluid != null && fluid.amount > 0) fluidsTag.appendTag(fluid.writeToNBT(new NBTTagCompound()));
        }
        tag.setTag("inputFluids", fluidsTag);
        return com.github.aeddddd.ae2enhanced.storage.PreservedNbt.mergeRoot(tag, preservedRoot,
                new java.util.HashSet<>(java.util.Arrays.asList(
                        "idMost", "idLeast", "binding", "expectedOutputs", "inputs", "inputFluids")));
    }

    public boolean hasPreservedContent() {
        if (!rawOutputs.isEmpty() || !rawInputs.isEmpty() || !rawFluids.isEmpty()) return true;
        if (preservedRoot == null) return false;
        for (String key : preservedRoot.getKeySet()) {
            if (!key.equals("idMost") && !key.equals("idLeast") && !key.equals("binding")
                    && !key.equals("expectedOutputs") && !key.equals("inputs") && !key.equals("inputFluids")) {
                return true;
            }
        }
        return false;
    }
}
