package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import java.util.Objects;

/**
 * 物品描述符,用于在内存中作为存储 Map 的 Key.
 * 基于 Item registryName + meta + NBT 内容做 equals/hashCode,不依赖 NBTTagCompound 的引用相等.
 */
public class ItemDescriptor implements Descriptor {

    private final Item item;
    private final int meta;
    private final NBTTagCompound nbt;
    private final int hash;
    // 缓存 AE2 的 IAEItemStack 模板,避免终端刷新时重复创建
    private transient volatile IAEItemStack aeTemplate;
    // 缓存显示名称和 modId，避免搜索时重复计算
    private transient volatile String displayName;
    private transient volatile String modId;

    public ItemDescriptor(ItemStack stack) {
        this.item = stack.getItem();
        this.meta = stack.getMetadata();
        this.nbt = stack.hasTagCompound() ? stack.getTagCompound().copy() : null;
        this.hash = computeHash();
    }

    private ItemDescriptor(Item item, int meta, NBTTagCompound nbt) {
        this.item = item;
        this.meta = meta;
        this.nbt = nbt != null ? nbt.copy() : null;
        this.hash = computeHash();
    }

    private int computeHash() {
        int result = item != null ? item.hashCode() : 0;
        result = 31 * result + meta;
        return result;
    }

    /**
     * 供自定义二进制 Codec 使用的工厂方法.
     */
    public static ItemDescriptor fromRaw(Item item, int meta, NBTTagCompound nbt) {
        return new ItemDescriptor(item, meta, nbt);
    }

    public static ItemDescriptor fromNBT(NBTTagCompound tag) {
        String id = tag.getString("id");
        if (id.isEmpty()) return null;
        Item item = Item.REGISTRY.getObject(new ResourceLocation(id));
        if (item == null) return null;
        int meta = tag.getShort("Damage");
        NBTTagCompound nbt = tag.hasKey("tag", 10) ? tag.getCompoundTag("tag") : null;
        return new ItemDescriptor(item, meta, nbt);
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        ResourceLocation regName = item.getRegistryName();
        tag.setString("id", regName != null ? regName.toString() : "minecraft:air");
        tag.setShort("Damage", (short) meta);
        if (nbt != null) {
            tag.setTag("tag", nbt.copy());
        }
        return tag;
    }

    public ItemStack toItemStack() {
        ItemStack stack = new ItemStack(item, 1, meta);
        if (nbt != null) {
            stack.setTagCompound(nbt.copy());
        }
        return stack;
    }

    /**
     * 获取缓存的 IAEItemStack 模板(stackSize=1).
     * 首次调用时通过 channel 创建,后续直接复用.
     */
    public IAEItemStack getAETemplate(IItemStorageChannel channel) {
        IAEItemStack result = aeTemplate;
        if (result == null) {
            synchronized (this) {
                result = aeTemplate;
                if (result == null) {
                    ItemStack stack = toItemStack();
                    result = aeTemplate = channel.createStack(stack);
                }
            }
        }
        return result;
    }

    public Item getItem() {
        return item;
    }

    public int getMeta() {
        return meta;
    }

    public NBTTagCompound getNbt() {
        return nbt;
    }

    /**
     * 获取缓存的显示名称（首次调用时计算并缓存）.
     * 直接使用 ItemStack.getDisplayName()，避免在服务端调用 AE2 客户端-only 方法.
     */
    public String getDisplayName() {
        String result = displayName;
        if (result == null) {
            result = toItemStack().getDisplayName();
            displayName = result;
        }
        return result;
    }

    /**
     * 获取缓存的 modId（首次调用时计算并缓存）.
     * 直接从 Item registryName 提取，避免在服务端调用 AE2 客户端-only 方法.
     */
    public String getModId() {
        String result = modId;
        if (result == null) {
            net.minecraft.util.ResourceLocation reg = item.getRegistryName();
            result = reg != null ? reg.getNamespace() : "unknown";
            modId = result;
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemDescriptor)) return false;
        ItemDescriptor other = (ItemDescriptor) o;
        if (meta != other.meta || item != other.item) return false;
        if (nbt == null && other.nbt == null) return true;
        if (nbt == null || other.nbt == null) return false;
        return nbt.equals(other.nbt);
    }

    @Override
    public int hashCode() {
        return hash;
    }
}
