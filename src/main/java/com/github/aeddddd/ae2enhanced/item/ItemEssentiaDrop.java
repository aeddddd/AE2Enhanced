package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.client.render.EssentiaItemRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import thaumcraft.api.aspects.Aspect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 源质假物品(Essentia Drop).
 * 用于在标准 AE2 物品终端中显示源质存储.
 *
 * 使用 NBT 中的 "AspectTag" 字段编码源质类型,符合 AGENTS.md 规范.
 * AE2-UEL 的 AEItemStack.isSameType 会完整比较 item、meta 和 NBT tag,
 * 因此 NBT 在 NetworkMonitor 的序列化/反序列化及网络同步中完全保留.
 */
public class ItemEssentiaDrop extends Item {

    private static final String NBT_ASPECT_TAG = "AspectTag";

    public ItemEssentiaDrop() {
        setRegistryName(AE2Enhanced.MOD_ID, "essentia_drop");
        setTranslationKey(AE2Enhanced.MOD_ID + ".essentia_drop");
        setCreativeTab(null);
        setHasSubtypes(false); // 不再使用 metadata,全部通过 NBT 区分
    }

    /**
     * 创建指定源质类型的假物品堆叠.
     */
    public static ItemStack createStack(String aspectTag, int amount) {
        ItemStack stack = new ItemStack(ItemRegistry.ESSENTIA_DROP, amount);
        if (aspectTag != null && !aspectTag.isEmpty()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(NBT_ASPECT_TAG, aspectTag);
            stack.setTagCompound(tag);
        }
        return stack;
    }

    /**
     * 从 ItemStack 中提取源质类型标签.
     */
    public static String getAspectTag(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemEssentiaDrop)) return null;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(NBT_ASPECT_TAG, 8)) {
            return tag.getString(NBT_ASPECT_TAG);
        }
        return null;
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        String aspectTag = getAspectTag(stack);
        if (aspectTag != null) {
            Aspect aspect = Aspect.getAspect(aspectTag);
            return aspect != null ? aspect.getName() : aspectTag;
        }
        return super.getItemStackDisplayName(stack);
    }

    /**
     * 返回所有已注册源质类型的 ItemStack 列表(每个堆叠数量为 1).
     * 供 JEI 黑名单等外部代码调用,避免直接依赖 Aspect.aspects.
     */
    public static List<ItemStack> getAllAspectStacks() {
        List<ItemStack> result = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        if (Aspect.aspects != null) {
            for (Aspect aspect : Aspect.aspects.values()) {
                if (aspect != null && aspect.getTag() != null) {
                    tags.add(aspect.getTag());
                }
            }
        }
        Collections.sort(tags);
        for (String tag : tags) {
            result.add(createStack(tag, 1));
        }
        return result;
    }

    /**
     * 为 JEI / 创造模式标签页提供所有子类型,使 JEI 能枚举所有 aspect 变体.
     */
    @Override
    public void getSubItems(net.minecraft.creativetab.CreativeTabs tab, net.minecraft.util.NonNullList<ItemStack> items) {
        if (this.isInCreativeTab(tab)) {
            items.addAll(getAllAspectStacks());
        }
    }

    /**
     * 判断 ItemStack 是否是源质假物品.
     */
    public static boolean isEssentiaDrop(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemEssentiaDrop;
    }

    /**
     * 客户端初始化：注册自定义 TileEntityItemStackRenderer.
     * RenderItem 在 isBuiltInRenderer=true 时会优先使用 Item 自己的 renderer.
     */
    @SideOnly(Side.CLIENT)
    public void initModel() {
        this.setTileEntityItemStackRenderer(EssentiaItemRenderer.INSTANCE);
    }
}
