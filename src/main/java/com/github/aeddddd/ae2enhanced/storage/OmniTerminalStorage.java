package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.Constants;

/**
 * 单个 Omni Terminal 的全部持久化数据.
 * 包含：合成栏 9 格、pattern 输入 81 格、pattern 输出 27 格、右侧扩展存储 36 格、
 * 以及跨会话保留的已完成合成物品列表.
 */
public class OmniTerminalStorage {

    private static final String KEY_CRAFTING = "crafting";
    private static final String KEY_PATTERN_INPUTS = "patternInputs";
    private static final String KEY_PATTERN_OUTPUTS = "patternOutputs";
    private static final String KEY_RIGHT_STORAGE = "rightStorage";
    private static final String KEY_PATTERN = "pattern";

    public static final int SIZE_CRAFTING = 9;
    public static final int SIZE_PATTERN_INPUTS = 81;
    public static final int SIZE_PATTERN_OUTPUTS = 27;
    public static final int SIZE_RIGHT_STORAGE = 36;
    public static final int SIZE_UPGRADE = 9;
    public static final int SIZE_PATTERN = 2;

    private final OmniTerminalInventory craftingInventory;
    private final OmniTerminalInventory patternInputInventory;
    private final OmniTerminalInventory patternOutputInventory;
    private final OmniTerminalInventory rightStorageInventory;
    private final OmniTerminalInventory upgradeInventory;
    private final OmniTerminalInventory patternInventory;


    private boolean dirty = false;

    public OmniTerminalStorage() {
        this.craftingInventory = new OmniTerminalInventory(SIZE_CRAFTING);
        this.patternInputInventory = new OmniTerminalInventory(SIZE_PATTERN_INPUTS);
        this.patternOutputInventory = new OmniTerminalInventory(SIZE_PATTERN_OUTPUTS);
        this.rightStorageInventory = new OmniTerminalInventory(SIZE_RIGHT_STORAGE);
        this.upgradeInventory = new OmniTerminalInventory(SIZE_UPGRADE);
        this.patternInventory = new OmniTerminalInventory(SIZE_PATTERN);
    }

    public OmniTerminalInventory getCraftingInventory() {
        return craftingInventory;
    }

    public OmniTerminalInventory getPatternInputInventory() {
        return patternInputInventory;
    }

    public OmniTerminalInventory getPatternOutputInventory() {
        return patternOutputInventory;
    }

    public OmniTerminalInventory getRightStorageInventory() {
        return rightStorageInventory;
    }

    public OmniTerminalInventory getUpgradeInventory() {
        return upgradeInventory;
    }

    public OmniTerminalInventory getPatternInventory() {
        return patternInventory;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void readFromNBT(NBTTagCompound compound) {
        if (compound.hasKey(KEY_CRAFTING, Constants.NBT.TAG_COMPOUND)) {
            craftingInventory.deserializeNBT(compound.getCompoundTag(KEY_CRAFTING));
        }
        if (compound.hasKey(KEY_PATTERN_INPUTS, Constants.NBT.TAG_COMPOUND)) {
            patternInputInventory.deserializeNBT(compound.getCompoundTag(KEY_PATTERN_INPUTS));
        }
        if (compound.hasKey(KEY_PATTERN_OUTPUTS, Constants.NBT.TAG_COMPOUND)) {
            patternOutputInventory.deserializeNBT(compound.getCompoundTag(KEY_PATTERN_OUTPUTS));
        }
        if (compound.hasKey(KEY_RIGHT_STORAGE, Constants.NBT.TAG_COMPOUND)) {
            rightStorageInventory.deserializeNBT(compound.getCompoundTag(KEY_RIGHT_STORAGE));
        }
        if (compound.hasKey("upgrade", Constants.NBT.TAG_COMPOUND)) {
            upgradeInventory.deserializeNBT(compound.getCompoundTag("upgrade"));
        }
        if (compound.hasKey(KEY_PATTERN, Constants.NBT.TAG_COMPOUND)) {
            patternInventory.deserializeNBT(compound.getCompoundTag(KEY_PATTERN));
        }
    }

    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setTag(KEY_CRAFTING, craftingInventory.serializeNBT());
        compound.setTag(KEY_PATTERN_INPUTS, patternInputInventory.serializeNBT());
        compound.setTag(KEY_PATTERN_OUTPUTS, patternOutputInventory.serializeNBT());
        compound.setTag(KEY_RIGHT_STORAGE, rightStorageInventory.serializeNBT());
        compound.setTag("upgrade", upgradeInventory.serializeNBT());
        compound.setTag(KEY_PATTERN, patternInventory.serializeNBT());
        return compound;
    }
}
