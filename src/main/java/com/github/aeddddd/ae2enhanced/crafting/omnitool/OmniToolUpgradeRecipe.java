package com.github.aeddddd.ae2enhanced.crafting.omnitool;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.item.MicroSingularityItem;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolEnchantments;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;

/**
 * 先进 ME 全能工具升级配方：工具 + 升级物品（附魔书/基岩/共形不变荷/永久微型奇点）→ 升级后的工具副本。
 * <p>附魔书：把 StoredEnchantments 合并进工具的 {@code AE2E_Enchantments} 存储区，
 * 同 id 取 max(lvl)、max(max)，max 受配置上限钳制；书中附魔已全部达到 source 上限时不匹配。</p>
 * <p>混沌核心：消耗一个永久存在的被约束微型奇点（对应 1.12 的 DE 混沌核心,已解除龙之研究绑定）。</p>
 */
public class OmniToolUpgradeRecipe extends CustomRecipe {

    public enum Type {
        ENCHANTED_BOOK,
        BEDROCK,
        CONFORMAL_CHARGE,
        CHAOS
    }

    private final Type type;

    public OmniToolUpgradeRecipe(ResourceLocation id, Type type) {
        super(id, CraftingBookCategory.MISC);
        this.type = type;
    }

    public Type getUpgradeType() {
        return type;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        // 基岩破坏者升级受配置开关门控（原 RecipesUpdatedEvent 注入时的开关迁移至此）
        if (type == Type.BEDROCK && !AE2EnhancedConfig.COMMON.omniToolEnableBedrockBreakerUpgrade.get()) {
            return false;
        }

        ItemStack omniTool = ItemStack.EMPTY;
        ItemStack upgradeItem = ItemStack.EMPTY;
        int nonEmptyCount = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            nonEmptyCount++;

            if (stack.getItem() instanceof AdvancedMEOmniToolItem) {
                if (!omniTool.isEmpty()) return false;
                omniTool = stack;
            } else if (matchesUpgradeItem(stack)) {
                if (!upgradeItem.isEmpty()) return false;
                upgradeItem = stack;
            } else {
                return false;
            }
        }

        if (nonEmptyCount != 2 || omniTool.isEmpty() || upgradeItem.isEmpty()) {
            return false;
        }

        // 防止重复升级
        if (type == Type.BEDROCK && OmniToolUpgrades.hasBedrockBreaker(omniTool)) {
            return false;
        }
        if (type == Type.CONFORMAL_CHARGE && OmniToolUpgrades.hasConformalCharge(omniTool)) {
            return false;
        }
        if (type == Type.CHAOS && OmniToolUpgrades.hasChaosCore(omniTool)) {
            return false;
        }
        if (type == Type.ENCHANTED_BOOK && !bookWouldChangeAnything(omniTool, upgradeItem)) {
            return false;
        }

        return true;
    }

    /**
     * 书中附魔已全部包含于工具且工具 source 上限不低于书本等级时，合成不会再带来任何变化，不匹配。
     */
    private boolean bookWouldChangeAnything(ItemStack omniTool, ItemStack book) {
        ListTag fromBook = OmniToolEnchantments.copyEnchantmentsFromBook(book);
        if (fromBook.size() == 0) return false;
        ListTag current = OmniToolEnchantments.getStoredEnchantments(omniTool);
        for (int i = 0; i < fromBook.size(); i++) {
            CompoundTag src = fromBook.getCompound(i);
            String id = src.getString("id");
            int bookMax = src.contains("max", Tag.TAG_SHORT) ? src.getShort("max") : src.getShort("lvl");
            int bookLvl = src.getShort("lvl");
            int oldMax = 0;
            int oldLvl = 0;
            for (int j = 0; j < current.size(); j++) {
                CompoundTag entry = current.getCompound(j);
                if (id.equals(entry.getString("id"))) {
                    oldMax = entry.contains("max", Tag.TAG_SHORT) ? entry.getShort("max") : entry.getShort("lvl");
                    oldLvl = entry.getShort("lvl");
                    break;
                }
            }
            if (Math.max(oldMax, bookMax) > oldMax || Math.max(oldLvl, bookLvl) > oldLvl) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesUpgradeItem(ItemStack stack) {
        return switch (type) {
            case ENCHANTED_BOOK -> stack.is(Items.ENCHANTED_BOOK)
                    && OmniToolEnchantments.copyEnchantmentsFromBook(stack).size() > 0;
            case BEDROCK -> stack.is(Items.BEDROCK);
            case CONFORMAL_CHARGE -> stack.is(ModItems.CONFORMAL_INVARIANT_CHARGE.get());
            case CHAOS -> stack.is(ModItems.MICRO_SINGULARITY.get()) && MicroSingularityItem.isPermanent(stack);
        };
    }

    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registryAccess) {
        ItemStack omniTool = ItemStack.EMPTY;
        ItemStack upgradeItem = ItemStack.EMPTY;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof AdvancedMEOmniToolItem) {
                omniTool = stack;
            } else if (matchesUpgradeItem(stack)) {
                upgradeItem = stack;
            }
        }
        if (omniTool.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = omniTool.copy();
        result.setCount(1);
        switch (type) {
            case BEDROCK -> OmniToolUpgrades.setBedrockBreaker(result, true);
            case CONFORMAL_CHARGE -> OmniToolUpgrades.setConformalCharge(result, true);
            case CHAOS -> OmniToolUpgrades.setChaosCore(result, true);
            case ENCHANTED_BOOK -> mergeBookEnchantments(result, upgradeItem);
        }
        return result;
    }

    /**
     * 将书上的附魔合并到工具的存储附魔区，同 id 取 max(lvl)、max(max)。
     */
    private void mergeBookEnchantments(ItemStack result, ItemStack book) {
        ListTag fromBook = OmniToolEnchantments.copyEnchantmentsFromBook(book);
        ListTag current = OmniToolEnchantments.getStoredEnchantments(result);

        for (int i = 0; i < fromBook.size(); i++) {
            CompoundTag src = fromBook.getCompound(i);
            String id = src.getString("id");
            short bookLvl = src.getShort("lvl");
            short bookMax = src.contains("max", Tag.TAG_SHORT) ? src.getShort("max") : bookLvl;

            boolean found = false;
            for (int j = 0; j < current.size(); j++) {
                CompoundTag entry = current.getCompound(j);
                if (id.equals(entry.getString("id"))) {
                    short oldMax = entry.contains("max", Tag.TAG_SHORT)
                            ? entry.getShort("max")
                            : entry.getShort("lvl");
                    short newMax = (short) Math.max(oldMax, bookMax);
                    short newLvl = (short) Math.max(entry.getShort("lvl"), bookLvl);
                    if (newLvl > newMax) newLvl = newMax;
                    entry.putShort("lvl", newLvl);
                    entry.putShort("max", newMax);
                    found = true;
                    break;
                }
            }
            if (!found) {
                CompoundTag entry = new CompoundTag();
                entry.putString("id", id);
                entry.putShort("lvl", bookLvl);
                entry.putShort("max", bookMax);
                current.add(entry);
            }
        }
        OmniToolEnchantments.setStoredEnchantments(result, current);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return new ItemStack(ModItems.ME_OMNI_TOOL.get());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.OMNI_TOOL_UPGRADE_SERIALIZER.get();
    }
}
