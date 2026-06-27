package com.github.aeddddd.ae2enhanced.crafting.smartpattern;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个配方的数据结构,可被序列化为 NBT.
 * 用于智能样板接口的配方聚合与虚拟展开.
 */
public class SmartRecipe {

    private IAEItemStack[] inputs;
    private IAEItemStack[] outputs;
    private final boolean isCrafting;

    public SmartRecipe(@Nonnull IAEItemStack[] inputs, @Nonnull IAEItemStack[] outputs, boolean isCrafting) {
        this.inputs = inputs != null ? inputs.clone() : new IAEItemStack[0];
        this.outputs = outputs != null ? outputs.clone() : new IAEItemStack[0];
        this.isCrafting = isCrafting;
    }

    @Nonnull
    public IAEItemStack[] getInputs() {
        return inputs;
    }

    @Nonnull
    public IAEItemStack[] getOutputs() {
        return outputs;
    }

    public boolean isCrafting() {
        return isCrafting;
    }

    /**
     * 获取主要输出(第一个非空输出).
     */
    @Nullable
    public IAEItemStack getPrimaryOutput() {
        for (IAEItemStack output : outputs) {
            if (output != null && output.getStackSize() > 0) {
                return output;
            }
        }
        return null;
    }

    public void setInput(int index, @Nullable IAEItemStack stack) {
        if (index < 0) return;
        if (index >= inputs.length) {
            inputs = java.util.Arrays.copyOf(inputs, index + 1);
        }
        inputs[index] = stack;
    }

    public void setOutput(int index, @Nullable IAEItemStack stack) {
        if (index < 0) return;
        if (index >= outputs.length) {
            outputs = java.util.Arrays.copyOf(outputs, index + 1);
        }
        outputs[index] = stack;
    }

    /**
     * 只保留主输出,清空其他输出.
     */
    public void keepPrimary() {
        IAEItemStack primary = getPrimaryOutput();
        for (int i = 0; i < outputs.length; i++) {
            outputs[i] = null;
        }
        if (primary != null && outputs.length > 0) {
            outputs[0] = primary.copy();
            outputs[0].setStackSize(primary.getStackSize());
        }
    }

    /**
     * 所有输入输出数量翻倍,上限 Integer.MAX_VALUE.
     */
    public void doubleAmounts() {
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != null) {
                long newSize = inputs[i].getStackSize() * 2L;
                inputs[i].setStackSize(newSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : newSize);
            }
        }
        for (int i = 0; i < outputs.length; i++) {
            if (outputs[i] != null) {
                long newSize = outputs[i].getStackSize() * 2L;
                outputs[i].setStackSize(newSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : newSize);
            }
        }
    }

    // ---- 数量操作 ----

    public void multiplyAmounts(int multiplier) {
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != null) {
                long newSize = inputs[i].getStackSize() * (long) multiplier;
                inputs[i].setStackSize(Math.min(newSize, Integer.MAX_VALUE));
            }
        }
        for (int i = 0; i < outputs.length; i++) {
            if (outputs[i] != null) {
                long newSize = outputs[i].getStackSize() * (long) multiplier;
                outputs[i].setStackSize(Math.min(newSize, Integer.MAX_VALUE));
            }
        }
    }

    public void divideAmounts(int divisor) {
        if (divisor <= 0) return;
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != null) {
                long newSize = Math.max(1, inputs[i].getStackSize() / divisor);
                inputs[i].setStackSize(newSize);
            }
        }
        for (int i = 0; i < outputs.length; i++) {
            if (outputs[i] != null) {
                long newSize = Math.max(1, outputs[i].getStackSize() / divisor);
                outputs[i].setStackSize(newSize);
            }
        }
    }

    public void addToAmounts(int delta) {
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != null) {
                long newSize = inputs[i].getStackSize() + delta;
                inputs[i].setStackSize(Math.max(1, Math.min(newSize, Integer.MAX_VALUE)));
            }
        }
        for (int i = 0; i < outputs.length; i++) {
            if (outputs[i] != null) {
                long newSize = outputs[i].getStackSize() + delta;
                outputs[i].setStackSize(Math.max(1, Math.min(newSize, Integer.MAX_VALUE)));
            }
        }
    }

    // ---- 轮换 ----

    public void rotateInputs() {
        if (inputs.length <= 1) return;
        IAEItemStack first = inputs[0];
        System.arraycopy(inputs, 1, inputs, 0, inputs.length - 1);
        inputs[inputs.length - 1] = first;
    }

    public void rotateOutputs() {
        if (outputs.length <= 1) return;
        IAEItemStack first = outputs[0];
        System.arraycopy(outputs, 1, outputs, 0, outputs.length - 1);
        outputs[outputs.length - 1] = first;
    }

    // ---- 清除 ----

    public void clearInputs() {
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = null;
        }
    }

    public void clearOutputs() {
        for (int i = 0; i < outputs.length; i++) {
            outputs[i] = null;
        }
    }

    // ---- 堆叠 / 展开 ----

    /**
     * 将多个槽位中的同类输入合并到前面的槽位.
     */
    public void stackInputs() {
        Map<IAEItemStack, Long> merged = new HashMap<>();
        for (IAEItemStack input : inputs) {
            if (input != null) {
                IAEItemStack key = input.copy();
                key.setStackSize(0);
                merged.merge(key, input.getStackSize(), Long::sum);
            }
        }
        int slot = 0;
        for (Map.Entry<IAEItemStack, Long> entry : merged.entrySet()) {
            if (slot < inputs.length) {
                IAEItemStack stack = entry.getKey().copy();
                stack.setStackSize(Math.min(entry.getValue(), Integer.MAX_VALUE));
                inputs[slot++] = stack;
            }
        }
        for (int i = slot; i < inputs.length; i++) {
            inputs[i] = null;
        }
    }

    /**
     * 将大堆叠的输入拆分到空槽位中(尽可能平均分配).
     */
    public void unstackInputs() {
        List<Integer> nonEmpty = new ArrayList<>();
        List<Integer> empty = new ArrayList<>();
        for (int i = 0; i < inputs.length; i++) {
            if (inputs[i] != null) nonEmpty.add(i);
            else empty.add(i);
        }
        if (nonEmpty.isEmpty() || empty.isEmpty()) return;

        // 对每个非空槽位,如果有空槽位,将数量平均分配
        for (int srcIdx : nonEmpty) {
            if (empty.isEmpty()) break;
            IAEItemStack src = inputs[srcIdx];
            if (src == null || src.getStackSize() <= 1) continue;

            long perSlot = src.getStackSize() / (empty.size() + 1);
            if (perSlot < 1) continue;

            long remainder = src.getStackSize() % (empty.size() + 1);
            src.setStackSize(perSlot + (remainder > 0 ? 1 : 0));
            if (remainder > 0) remainder--;

            for (int j = 0; j < empty.size() && j < empty.size(); ) {
                int destIdx = empty.get(j);
                IAEItemStack copy = src.copy();
                copy.setStackSize(perSlot + (remainder > 0 ? 1 : 0));
                inputs[destIdx] = copy;
                if (remainder > 0) remainder--;
                empty.remove(j);
            }
        }
    }

    // ---- NBT ----

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setTag("inputs", writeStackArray(inputs));
        tag.setTag("outputs", writeStackArray(outputs));
        tag.setBoolean("crafting", isCrafting);
        return tag;
    }

    public static SmartRecipe fromNBT(NBTTagCompound tag) {
        IAEItemStack[] inputs = readStackArray(tag.getTagList("inputs", 10));
        IAEItemStack[] outputs = readStackArray(tag.getTagList("outputs", 10));
        // 智能样板统一作为 processing 配方，忽略旧 NBT 中的 crafting 标记
        return new SmartRecipe(inputs, outputs, false);
    }

    private static NBTTagList writeStackArray(IAEItemStack[] stacks) {
        NBTTagList list = new NBTTagList();
        for (IAEItemStack stack : stacks) {
            if (stack != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                ((AEItemStack) stack).writeToNBT(itemTag);
                list.appendTag(itemTag);
            } else {
                list.appendTag(new NBTTagCompound());
            }
        }
        return list;
    }

    private static IAEItemStack[] readStackArray(NBTTagList list) {
        IAEItemStack[] stacks = new IAEItemStack[list.tagCount()];
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(i);
            if (itemTag.getKeySet().isEmpty()) {
                stacks[i] = null;
            } else {
                stacks[i] = AEItemStack.fromNBT(itemTag);
            }
        }
        return stacks;
    }
}
