package com.github.aeddddd.ae2enhanced.item;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;
import com.github.aeddddd.ae2enhanced.registry.content.PartRegistry;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternStorageFile;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternSubDetails;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 编码后的智能样板.
 * 实现 {@link ICraftingPatternItem},但 {@link #getPatternForItem} 返回 null,
 * 实际展开逻辑由 Mixin 注入 {@code DualityInterface.addToCraftingList} 处理.
 *
 * <p>NBT 结构(最小化)：</p>
 * <pre>
 * {
 *   patternDataId: UUID
 *   disabledMask: String (Base64 BitSet)
 *   recipeCount: int
 *   targetBlockId: String (诊断信息,可选)
 * }
 * </pre>
 */
public class ItemSmartPattern extends Item implements ICraftingPatternItem {

    private static final String NBT_PATTERN_DATA_ID = "patternDataId";
    private static final String NBT_DISABLED_MASK = "disabledMask";
    private static final String NBT_RECIPE_COUNT = "recipeCount";
    private static final String NBT_TARGET_BLOCK_ID = "targetBlockId";

    public ItemSmartPattern() {
        setRegistryName(AE2Enhanced.MOD_ID, "smart_pattern");
        setTranslationKey(AE2Enhanced.MOD_ID + ".smart_pattern");
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setMaxStackSize(1);
    }

    /**
     * 创建编码后的智能样板 ItemStack.
     */
    @Nonnull
    public static ItemStack createPattern(@Nonnull UUID patternDataId,
                                           @Nonnull BitSet disabledMask,
                                           int recipeCount,
                                           @Nonnull String targetBlockId) {
        ItemStack stack = new ItemStack(ItemRegistry.SMART_PATTERN);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId(NBT_PATTERN_DATA_ID, patternDataId);
        tag.setString(NBT_DISABLED_MASK, SmartPatternData.bitSetToBase64(disabledMask));
        tag.setInteger(NBT_RECIPE_COUNT, recipeCount);
        tag.setString(NBT_TARGET_BLOCK_ID, targetBlockId);
        stack.setTagCompound(tag);
        return stack;
    }

    /**
     * 从 ItemStack 中提取 patternDataId.
     */
    @Nullable
    public static UUID getPatternDataId(@Nonnull ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSmartPattern)) {
            return null;
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey(NBT_PATTERN_DATA_ID + "Most")) {
            return null;
        }
        return tag.getUniqueId(NBT_PATTERN_DATA_ID);
    }

    /**
     * 从 ItemStack 中提取禁用掩码.
     */
    @Nonnull
    public static BitSet getDisabledMask(@Nonnull ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSmartPattern)) {
            return new BitSet();
        }
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return new BitSet();
        }
        return SmartPatternData.bitSetFromBase64(tag.getString(NBT_DISABLED_MASK));
    }

    /**
     * 从 ItemStack 中提取配方数量(诊断信息).
     */
    public static int getRecipeCount(@Nonnull ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSmartPattern)) {
            return 0;
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.getInteger(NBT_RECIPE_COUNT) : 0;
    }

    /**
     * 从 ItemStack 中提取目标方块 ID(诊断信息).
     */
    @Nonnull
    public static String getTargetBlockId(@Nonnull ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemSmartPattern)) {
            return "";
        }
        NBTTagCompound tag = stack.getTagCompound();
        return tag != null ? tag.getString(NBT_TARGET_BLOCK_ID) : "";
    }

    /**
     * 将智能样板展开为多个 {@link ICraftingPatternDetails}.
     * 由 Mixin 调用.
     */
    @Nonnull
    public static List<SmartPatternSubDetails> expandPatterns(@Nonnull ItemStack stack, @Nullable World world) {
        if (world == null) {
            // TileEntity.readFromNBT 时 world 字段尚未设置,延迟到 initialize() 再展开
            return Collections.emptyList();
        }
        UUID dataId = getPatternDataId(stack);
        if (dataId == null) {
            return Collections.emptyList();
        }
        SmartPatternData data = SmartPatternStorageFile.load(world, dataId);
        if (data == null) {
            return Collections.emptyList();
        }
        BitSet disabledMask = getDisabledMask(stack);
        List<SmartPatternSubDetails> result = new java.util.ArrayList<>();
        for (int i = 0; i < data.getRecipes().size(); i++) {
            if (disabledMask.get(i)) {
                continue;
            }
            result.add(new SmartPatternSubDetails(stack, data.getRecipes().get(i)));
        }
        return result;
    }

    @Override
    public ICraftingPatternDetails getPatternForItem(@Nonnull ItemStack stack, @Nonnull World world) {
        // 返回 null,由 Mixin 在 addToCraftingList 中拦截并展开
        return null;
    }

    @Override
    public void addInformation(@Nonnull ItemStack stack, @Nullable World worldIn,
                               @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flagIn) {
        String target = getTargetBlockId(stack);
        int count = getRecipeCount(stack);
        if (!target.isEmpty()) {
            tooltip.add(I18n.format("tooltip.ae2enhanced.smart_pattern.bound_to", target));
        }
        if (count > 0) {
            tooltip.add(I18n.format("tooltip.ae2enhanced.smart_pattern.recipe_count", count));
        }
    }
}
