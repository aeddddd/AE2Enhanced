package com.github.aeddddd.ae2enhanced.test.crafting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.electronwill.nightconfig.toml.TomlParser;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.crafting.omnitool.OmniToolUpgradeRecipe;
import com.github.aeddddd.ae2enhanced.item.MicroSingularityItem;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolEnchantments;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;

/**
 * {@link OmniToolUpgradeRecipe} 单元测试:四类升级的匹配门控与合成结果.
 */
class OmniToolUpgradeRecipeTest {

    static {
        CraftingTestFixtures.init();
    }

    private static final ResourceLocation ID = new ResourceLocation("ae2enhanced", "upgrade");

    private static OmniToolUpgradeRecipe recipe(OmniToolUpgradeRecipe.Type type) {
        return new OmniToolUpgradeRecipe(ID, type);
    }

    private static ItemStack omniTool() {
        return new ItemStack(CraftingTestFixtures.OMNI_TOOL);
    }

    private static CraftingContainer container(ItemStack... stacks) {
        var menu = new AbstractContainerMenu(null, 0) {
            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return false;
            }
        };
        var container = new TransientCraftingContainer(menu, 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            container.setItem(i, stacks[i]);
        }
        return container;
    }

    private static ItemStack enchantedBook(net.minecraft.world.item.enchantment.Enchantment ench, int lvl) {
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(ench, lvl));
        return book;
    }

    // ===== 通用匹配规则 =====

    /** 类型访问器与配方 id. */
    @Test
    void testAccessors() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.BEDROCK);
        assertThat(recipe.getUpgradeType()).isEqualTo(OmniToolUpgradeRecipe.Type.BEDROCK);
        assertThat(recipe.getId()).isEqualTo(ID);
        assertThat(recipe.getSerializer()).isSameAs(ModRecipes.OMNI_TOOL_UPGRADE_SERIALIZER.get());
    }

    /** 必须恰好"工具 + 升级物品"两件:空容器/单件/三件/双工具/双升级物均不匹配. */
    @Test
    void testMatchesRequiresExactlyToolPlusUpgrade() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.BEDROCK);

        assertThat(recipe.matches(container(), null)).isFalse();
        assertThat(recipe.matches(container(omniTool()), null)).isFalse();
        assertThat(recipe.matches(container(new ItemStack(Items.BEDROCK)), null)).isFalse();
        assertThat(recipe.matches(container(omniTool(), omniTool()), null)).isFalse();
        assertThat(recipe.matches(container(omniTool(), new ItemStack(Items.BEDROCK),
                new ItemStack(Items.BEDROCK)), null)).isFalse();
        assertThat(recipe.matches(container(omniTool(), new ItemStack(Items.STONE)), null)).isFalse();
    }

    /** 升级物品类型必须与配方类型对应:基岩配方不吃共形不变荷. */
    @Test
    void testUpgradeItemMustMatchType() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.BEDROCK);
        assertThat(recipe.matches(
                container(omniTool(), new ItemStack(CraftingTestFixtures.CONFORMAL_CHARGE)), null)).isFalse();
    }

    // ===== 基岩破坏者 =====

    /** 工具 + 基岩 → 匹配;合成结果带基岩破坏者且原工具不变. */
    @Test
    void testBedrockUpgrade() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.BEDROCK);
        var tool = omniTool();
        var inv = container(tool, new ItemStack(Items.BEDROCK));

        assertThat(recipe.matches(inv, null)).isTrue();

        var result = recipe.assemble(inv, null);
        assertThat(result.getItem()).isSameAs(CraftingTestFixtures.OMNI_TOOL);
        assertThat(result.getCount()).isEqualTo(1);
        assertThat(OmniToolUpgrades.hasBedrockBreaker(result)).isTrue();
        assertThat(OmniToolUpgrades.hasBedrockBreaker(tool)).as("原工具不被修改").isFalse();
    }

    /** 已有基岩破坏者的工具不能重复升级. */
    @Test
    void testBedrockUpgradeNotRepeatable() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.BEDROCK);
        var tool = omniTool();
        OmniToolUpgrades.setBedrockBreaker(tool, true);

        assertThat(recipe.matches(container(tool, new ItemStack(Items.BEDROCK)), null)).isFalse();
    }

    /** 配置关闭基岩破坏者升级时不匹配. */
    @Test
    void testBedrockUpgradeDisabledByConfig() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.BEDROCK);
        try {
            AE2EnhancedConfig.COMMON_SPEC.setConfig(
                    new TomlParser().parse("[omniTool]\nenableBedrockBreakerUpgrade = false"));

            assertThat(recipe.matches(container(omniTool(), new ItemStack(Items.BEDROCK)), null)).isFalse();
        } finally {
            // 恢复默认配置,避免污染其他测试
            AE2EnhancedConfig.COMMON_SPEC.setConfig(new TomlParser().parse(""));
        }
    }

    // ===== 共形不变荷 =====

    /** 工具 + 共形不变荷 → 匹配;合成结果带共形不变荷;不可重复. */
    @Test
    void testConformalChargeUpgrade() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.CONFORMAL_CHARGE);
        var tool = omniTool();
        var inv = container(tool, new ItemStack(CraftingTestFixtures.CONFORMAL_CHARGE));

        assertThat(recipe.matches(inv, null)).isTrue();

        var result = recipe.assemble(inv, null);
        assertThat(OmniToolUpgrades.hasConformalCharge(result)).isTrue();

        assertThat(recipe.matches(container(result, new ItemStack(CraftingTestFixtures.CONFORMAL_CHARGE)), null))
                .as("不可重复升级").isFalse();
    }

    // ===== 混沌核心 =====

    /** 仅永久存在的被约束微型奇点可用作混沌核心升级. */
    @Test
    void testChaosUpgradeRequiresPermanentSingularity() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.CHAOS);
        var permanent = MicroSingularityItem.createStack(100, true);
        var temporary = MicroSingularityItem.createStack(100, false);

        assertThat(recipe.matches(container(omniTool(), permanent), null)).isTrue();
        assertThat(recipe.matches(container(omniTool(), temporary), null)).isFalse();

        var result = recipe.assemble(container(omniTool(), permanent), null);
        assertThat(OmniToolUpgrades.hasChaosCore(result)).isTrue();

        assertThat(recipe.matches(container(result, permanent), null)).as("不可重复升级").isFalse();
    }

    // ===== 附魔书 =====

    /** 附魔书:书内附魔合并进工具存储区;全部达到上限后不再匹配. */
    @Test
    void testEnchantedBookUpgrade() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.ENCHANTED_BOOK);
        var sharpnessId = new ResourceLocation("minecraft", "sharpness");
        var book = enchantedBook(Enchantments.SHARPNESS, 5);

        assertThat(recipe.matches(container(omniTool(), book), null)).isTrue();

        var result = recipe.assemble(container(omniTool(), book), null);
        assertThat(OmniToolEnchantments.getStoredEnchantmentLevel(result, sharpnessId)).isEqualTo(5);
        assertThat(OmniToolEnchantments.getEnchantmentSourceLevel(result, sharpnessId)).isEqualTo(5);

        // 同样的书再升一次不会有任何变化 → 不匹配
        assertThat(recipe.matches(container(result, book), null)).isFalse();

        // 更高等级的书仍可升级,等级取 max
        var betterBook = enchantedBook(Enchantments.SHARPNESS, 10);
        assertThat(recipe.matches(container(result, betterBook), null)).isTrue();
        var upgraded = recipe.assemble(container(result, betterBook), null);
        assertThat(OmniToolEnchantments.getStoredEnchantmentLevel(upgraded, sharpnessId)).isEqualTo(10);
    }

    /** 无附魔的书不匹配. */
    @Test
    void testEmptyEnchantedBookRejected() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.ENCHANTED_BOOK);
        assertThat(recipe.matches(container(omniTool(), new ItemStack(Items.ENCHANTED_BOOK)), null)).isFalse();
    }

    /** 不同附魔的书可以叠加到同一把工具上. */
    @Test
    void testEnchantedBookMergesDifferentEnchantments() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.ENCHANTED_BOOK);
        var tool = omniTool();
        var fortuneBook = enchantedBook(Enchantments.BLOCK_FORTUNE, 3);

        var result = recipe.assemble(container(tool, fortuneBook), null);
        var sharpnessBook = enchantedBook(Enchantments.SHARPNESS, 5);
        var merged = recipe.assemble(container(result, sharpnessBook), null);

        assertThat(OmniToolEnchantments.getStoredEnchantmentLevel(merged,
                new ResourceLocation("minecraft", "fortune"))).isEqualTo(3);
        assertThat(OmniToolEnchantments.getStoredEnchantmentLevel(merged,
                new ResourceLocation("minecraft", "sharpness"))).isEqualTo(5);
    }

    // ===== 配方接口 =====

    /** 无工具时 assemble 返回空堆. */
    @Test
    void testAssembleWithoutToolReturnsEmpty() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.BEDROCK);
        assertThat(recipe.assemble(container(new ItemStack(Items.BEDROCK)), null))
                .isSameAs(ItemStack.EMPTY);
    }

    /** 合成格至少 2 格;结果预览为全能工具. */
    @Test
    void testRecipeInterfaceDefaults() {
        var recipe = recipe(OmniToolUpgradeRecipe.Type.BEDROCK);
        assertThat(recipe.canCraftInDimensions(1, 1)).isFalse();
        assertThat(recipe.canCraftInDimensions(1, 2)).isTrue();
        assertThat(recipe.canCraftInDimensions(3, 3)).isTrue();
        assertThat(recipe.getResultItem(null).getItem()).isSameAs(CraftingTestFixtures.OMNI_TOOL);
    }
}
