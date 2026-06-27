package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.ItemStackHandler;

/**
 * 支持单槽位 Integer.MAX_VALUE 堆叠的 ItemStackHandler.
 * 用于 Omni Terminal 的 pattern 存储区与右侧扩展存储,
 * 彻底绕过 ItemStack.getMaxStackSize() 的 64 限制.
 *
 * <p>序列化时使用自定义 NBT 格式(Count 存为 Integer),
 * 避免 Minecraft 1.12.2 中 ItemStack.writeToNBT 使用 byte 导致 >127 溢出的问题.</p>
 */
public class OmniTerminalInventory extends ItemStackHandler {

    private Runnable onContentsChangedCallback;

    public OmniTerminalInventory(int size) {
        super(size);
    }

    public void setOnContentsChangedCallback(Runnable callback) {
        this.onContentsChangedCallback = callback;
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        if (this.onContentsChangedCallback != null) {
            this.onContentsChangedCallback.run();
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    /**
     * 重写 insertItem：合并时忽略 getMaxStackSize(),只受 int 上限限制.
     */
    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        validateSlotIndex(slot);
        ItemStack existing = this.stacks.get(slot);

        int limit = getSlotLimit(slot);

        if (!existing.isEmpty()) {
            if (!ItemStack.areItemsEqual(existing, stack) || !ItemStack.areItemStackTagsEqual(existing, stack)) {
                return stack;
            }
            limit -= existing.getCount();
        }

        if (limit <= 0) {
            return stack;
        }

        boolean reachedLimit = stack.getCount() > limit;

        if (!simulate) {
            if (existing.isEmpty()) {
                this.stacks.set(slot, reachedLimit ? copyStackWithSize(stack, limit) : stack.copy());
            } else {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
            onContentsChanged(slot);
        }

        return reachedLimit ? copyStackWithSize(stack, stack.getCount() - limit) : ItemStack.EMPTY;
    }

    /**
     * 自定义序列化：使用 Integer 存储 Count,绕过 byte 限制.
     */
    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            if (!stack.isEmpty()) {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setInteger("Slot", i);
                itemTag.setString("id", stack.getItem().getRegistryName().toString());
                itemTag.setInteger("Count", stack.getCount());
                itemTag.setShort("Damage", (short) stack.getItemDamage());
                if (stack.hasTagCompound()) {
                    itemTag.setTag("tag", stack.getTagCompound().copy());
                }
                list.appendTag(itemTag);
            }
        }
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("Items", list);
        nbt.setInteger("Size", stacks.size());
        return nbt;
    }

    /**
     * 自定义反序列化：从 Integer Count 读取,支持任意数量.
     */
    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        if (nbt.hasKey("Size", Constants.NBT.TAG_INT)) {
            setSize(nbt.getInteger("Size"));
        } else if (nbt.hasKey("Items", Constants.NBT.TAG_LIST)) {
            NBTTagList tagList = nbt.getTagList("Items", Constants.NBT.TAG_COMPOUND);
            setSize(tagList.tagCount());
        }
        NBTTagList tagList = nbt.getTagList("Items", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tagList.tagCount(); i++) {
            NBTTagCompound itemTag = tagList.getCompoundTagAt(i);
            int slot = itemTag.getInteger("Slot");
            if (slot >= 0 && slot < stacks.size()) {
                Item item = Item.REGISTRY.getObject(new ResourceLocation(itemTag.getString("id")));
                if (item != null) {
                    int count = itemTag.getInteger("Count");
                    int damage = itemTag.getShort("Damage");
                    ItemStack stack = new ItemStack(item, count, damage);
                    if (itemTag.hasKey("tag", Constants.NBT.TAG_COMPOUND)) {
                        stack.setTagCompound(itemTag.getCompoundTag("tag"));
                    }
                    this.stacks.set(slot, stack);
                }
            }
        }
        onLoad();
    }

    private static ItemStack copyStackWithSize(ItemStack stack, int size) {
        if (size <= 0) return ItemStack.EMPTY;
        ItemStack copy = stack.copy();
        copy.setCount(size);
        return copy;
    }
}
