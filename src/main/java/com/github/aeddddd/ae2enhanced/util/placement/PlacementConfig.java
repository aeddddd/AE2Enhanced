package com.github.aeddddd.ae2enhanced.util.placement;

import appeng.api.util.AEColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;

/**
 * 放置工具配置 —— 管理最多 9 个预设槽、当前选中槽、放置子模式、线缆颜色、触及距离、线缆起点。
 * 所有数据存储在 ItemStack NBT 的 AE2E_Placement 标签下。
 */
public class PlacementConfig {

    public static final String NBT_ROOT = "AE2E_Placement";
    public static final String NBT_PRESETS = "presets";
    public static final String NBT_SELECTED_SLOT = "selectedSlot";
    public static final String NBT_PLACEMENT_MODE = "placementMode";
    public static final String NBT_CABLE_COLOR = "cableColor";
    public static final String NBT_REACH_DISTANCE = "reachDistance";
    public static final String NBT_PLACEMENT_RESTRICTION = "placementRestriction";
    public static final String NBT_CABLE_START = "cableStart";

    // 旧版兼容字段
    public static final String LEGACY_NBT_ITEMS = "items";
    public static final String LEGACY_NBT_PLACEMENT_COUNT = "placementCount";

    public static final int MAX_PRESETS = 9;
    public static final int BULK_MAX_BLOCKS = 512;
    public static final float DEFAULT_REACH_DISTANCE = 12.0f;
    public static final float MIN_REACH_DISTANCE = 5.0f;
    public static final float MAX_REACH_DISTANCE = 32.0f;

    private final ItemStack stack;
    private final NBTTagCompound root;

    public PlacementConfig(ItemStack stack) {
        this.stack = stack;
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_ROOT)) {
            tag.setTag(NBT_ROOT, new NBTTagCompound());
        }
        this.root = tag.getCompoundTag(NBT_ROOT);
        migrateLegacyData();
    }

    // ========== 预设槽 ==========

    public int getPresetCount() {
        NBTTagList list = root.getTagList(NBT_PRESETS, 10);
        int count = 0;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getByte("Slot") & 0xFF;
            if (slot >= 0 && slot < MAX_PRESETS) {
                ItemStack s = new ItemStack(tag);
                if (!s.isEmpty()) {
                    count++;
                }
            }
        }
        return count;
    }

    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= MAX_PRESETS) return ItemStack.EMPTY;
        NBTTagList list = root.getTagList(NBT_PRESETS, 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if ((tag.getByte("Slot") & 0xFF) == slot) {
                return new ItemStack(tag);
            }
        }
        return ItemStack.EMPTY;
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= MAX_PRESETS) return;
        NBTTagList list = root.getTagList(NBT_PRESETS, 10);
        // 查找并移除旧槽位
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            if ((tag.getByte("Slot") & 0xFF) == slot) {
                list.removeTag(i);
                break;
            }
        }
        // 写入新槽位
        if (!stack.isEmpty()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setByte("Slot", (byte) slot);
            stack.writeToNBT(tag);
            list.appendTag(tag);
        }
        root.setTag(NBT_PRESETS, list);
    }

    public void clearSlot(int slot) {
        setStackInSlot(slot, ItemStack.EMPTY);
    }

    public int getFirstEmptySlot() {
        for (int i = 0; i < MAX_PRESETS; i++) {
            if (getStackInSlot(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    // ========== 当前选中槽 ==========

    public int getSelectedSlot() {
        int slot = root.getInteger(NBT_SELECTED_SLOT);
        if (slot < -1 || slot >= MAX_PRESETS) slot = -1;
        return slot;
    }

    public void setSelectedSlot(int slot) {
        if (slot < -1) slot = -1;
        if (slot >= MAX_PRESETS) slot = MAX_PRESETS - 1;
        root.setInteger(NBT_SELECTED_SLOT, slot);
    }

    public ItemStack getSelectedStack() {
        int slot = getSelectedSlot();
        if (slot < 0) return ItemStack.EMPTY;
        return getStackInSlot(slot);
    }

    // ========== 放置子模式 ==========

    public PlacementMode getPlacementMode() {
        return PlacementMode.fromOrdinal(root.getByte(NBT_PLACEMENT_MODE));
    }

    public void setPlacementMode(PlacementMode mode) {
        root.setByte(NBT_PLACEMENT_MODE, (byte) mode.ordinal());
    }

    // ========== 线缆颜色 ==========

    public AEColor getCableColor() {
        int ordinal = root.getByte(NBT_CABLE_COLOR) & 0xFF;
        if (ordinal < 0 || ordinal >= AEColor.values().length) {
            return AEColor.TRANSPARENT;
        }
        return AEColor.values()[ordinal];
    }

    public void setCableColor(AEColor color) {
        root.setByte(NBT_CABLE_COLOR, (byte) color.ordinal());
    }

    // ========== 触及距离（仅 Omni Tool） ==========

    public float getReachDistance() {
        if (!root.hasKey(NBT_REACH_DISTANCE)) {
            return DEFAULT_REACH_DISTANCE;
        }
        float reach = root.getFloat(NBT_REACH_DISTANCE);
        if (reach < MIN_REACH_DISTANCE) reach = MIN_REACH_DISTANCE;
        if (reach > MAX_REACH_DISTANCE) reach = MAX_REACH_DISTANCE;
        return reach;
    }

    public void setReachDistance(float reach) {
        if (reach < MIN_REACH_DISTANCE) reach = MIN_REACH_DISTANCE;
        if (reach > MAX_REACH_DISTANCE) reach = MAX_REACH_DISTANCE;
        root.setFloat(NBT_REACH_DISTANCE, reach);
    }

    // ========== 批量放置方向锁 ==========

    public PlacementRestriction getPlacementRestriction() {
        return PlacementRestriction.fromOrdinal(root.getByte(NBT_PLACEMENT_RESTRICTION));
    }

    public void setPlacementRestriction(PlacementRestriction restriction) {
        root.setByte(NBT_PLACEMENT_RESTRICTION, (byte) restriction.ordinal());
    }

    // ========== 线缆起点 ==========

    public BlockPos getCableStart() {
        if (!root.hasKey(NBT_CABLE_START)) {
            return null;
        }
        long val = root.getLong(NBT_CABLE_START);
        if (val == -1L) return null;
        return BlockPos.fromLong(val);
    }

    public void setCableStart(BlockPos pos) {
        if (pos == null) {
            root.setLong(NBT_CABLE_START, -1L);
        } else {
            root.setLong(NBT_CABLE_START, pos.toLong());
        }
    }

    // ========== 旧版数据迁移 ==========

    private void migrateLegacyData() {
        if (root.hasKey(NBT_PRESETS)) {
            return; // 已经是新格式
        }
        if (!root.hasKey(LEGACY_NBT_ITEMS)) {
            return; // 没有旧数据
        }
        NBTTagList oldList = root.getTagList(LEGACY_NBT_ITEMS, 10);
        NBTTagList newList = new NBTTagList();
        int migrated = 0;
        for (int i = 0; i < oldList.tagCount() && migrated < MAX_PRESETS; i++) {
            NBTTagCompound tag = oldList.getCompoundTagAt(i);
            int oldSlot = tag.getByte("Slot") & 0xFF;
            if (oldSlot < 0 || oldSlot >= 18) continue;
            ItemStack s = new ItemStack(tag);
            if (s.isEmpty()) continue;
            // 按顺序写入新槽位 0~8
            NBTTagCompound newTag = new NBTTagCompound();
            newTag.setByte("Slot", (byte) migrated);
            s.writeToNBT(newTag);
            newList.appendTag(newTag);
            migrated++;
        }
        root.setTag(NBT_PRESETS, newList);
        // 迁移选中槽
        if (root.hasKey(NBT_SELECTED_SLOT)) {
            int oldSelected = root.getInteger(NBT_SELECTED_SLOT);
            if (oldSelected >= 0 && oldSelected < migrated) {
                root.setInteger(NBT_SELECTED_SLOT, oldSelected);
            } else {
                root.setInteger(NBT_SELECTED_SLOT, migrated > 0 ? 0 : -1);
            }
        }
        // 删除旧字段
        root.removeTag(LEGACY_NBT_ITEMS);
        root.removeTag(LEGACY_NBT_PLACEMENT_COUNT);
        // 默认模式
        if (!root.hasKey(NBT_PLACEMENT_MODE)) {
            root.setByte(NBT_PLACEMENT_MODE, (byte) PlacementMode.SINGLE.ordinal());
        }
    }

    public static boolean hasConfig(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(NBT_ROOT);
    }
}
