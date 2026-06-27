package com.github.aeddddd.ae2enhanced.storage;

import net.minecraft.nbt.NBTTagCompound;

import java.util.Objects;

/**
 * 源质描述符,作为 Map 的 Key.基于 Thaumcraft Aspect tag.
 */
public class EssentiaDescriptor implements Descriptor {
    private final String aspectTag;
    private final int hash;
    private transient volatile thaumicenergistics.api.storage.IAEEssentiaStack aeTemplate = null;

    public EssentiaDescriptor(thaumicenergistics.api.storage.IAEEssentiaStack stack) {
        this.aspectTag = stack.getAspect() != null ? stack.getAspect().getTag() : "unknown";
        this.hash = aspectTag.hashCode();
    }

    public EssentiaDescriptor(String aspectTag) {
        this.aspectTag = sanitizeAspectTag(aspectTag);
        this.hash = this.aspectTag.hashCode();
    }

    /**
     * 修复历史损坏数据：早期版本的 loadSectionReflective 未正确解包 length+bytes 包装层，
     * 导致 aspectTag 前导出现 \0 和控制字符（来自内层 tagBytes.length 的 4 字节整数）。
     */
    private static String sanitizeAspectTag(String tag) {
        if (tag == null || tag.isEmpty()) {
            return tag;
        }
        int start = 0;
        while (start < tag.length()) {
            char c = tag.charAt(start);
            // 跳过 \0 和 C0 控制字符 (\u0001-\u001F)
            if (c == '\0' || (c >= '\u0001' && c <= '\u001F')) {
                start++;
            } else {
                break;
            }
        }
        return start > 0 ? tag.substring(start) : tag;
    }

    /**
     * 根据存储的 aspectTag 重建 IAEEssentiaStack 模板(stackSize=1).
     */
    public thaumicenergistics.api.storage.IAEEssentiaStack getAETemplate() {
        thaumicenergistics.api.storage.IAEEssentiaStack result = aeTemplate;
        if (result == null) {
            synchronized (this) {
                result = aeTemplate;
                if (result == null) {
                    thaumicenergistics.api.EssentiaStack stack = new thaumicenergistics.api.EssentiaStack(aspectTag, 1);
                    // 防御性检查：aspectTag 对应的 Aspect 可能已被移除或不存在
                    if (stack.getAspect() == null) {
                        return null;
                    }
                    result = aeTemplate = thaumicenergistics.integration.appeng.AEEssentiaStack.fromEssentiaStack(stack);
                }
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EssentiaDescriptor)) return false;
        return Objects.equals(aspectTag, ((EssentiaDescriptor) obj).aspectTag);
    }

    public static EssentiaDescriptor fromNBT(NBTTagCompound tag) {
        String aspectTag = tag.getString("aspectTag");
        if (aspectTag.isEmpty()) return null;
        return new EssentiaDescriptor(aspectTag);
    }

    public NBTTagCompound toNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("aspectTag", aspectTag);
        return tag;
    }

    public String getAspectTag() {
        return aspectTag;
    }
}
