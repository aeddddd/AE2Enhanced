package com.github.aeddddd.ae2enhanced.test.omnitool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolEnchantments;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolNBT;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OmniToolEnchantments} 存储附魔读写与同步测试.
 */
class OmniToolEnchantmentsTest {

    private static final ResourceLocation SHARPNESS_ID = new ResourceLocation("minecraft", "sharpness");
    private static final ResourceLocation SILK_TOUCH_ID = new ResourceLocation("minecraft", "silk_touch");

    @BeforeAll
    static void bootstrap() {
        OmniToolTestSupport.bootstrap();
    }

    private static ItemStack newToolStack() {
        return OmniToolTestSupport.newToolStack();
    }

    // ==================== 基础读写 ====================

    @Test
    void testEmptyByDefault() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolEnchantments.hasStoredEnchantments(stack)).isFalse();
        assertThat(OmniToolEnchantments.getStoredEnchantments(stack)).isEmpty();
        assertThat(OmniToolEnchantments.getStoredEnchantmentLevel(stack, SHARPNESS_ID)).isZero();
        assertThat(OmniToolEnchantments.getEnchantmentSourceLevel(stack, SHARPNESS_ID)).isZero();
    }

    @Test
    void testSetNewEnchantmentStoresLevelAndMax() {
        ItemStack stack = newToolStack();
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SHARPNESS_ID, 5);
        assertThat(OmniToolEnchantments.getStoredEnchantmentLevel(stack, SHARPNESS_ID)).isEqualTo(5);
        // 新增条目时 max(来源等级) 与 lvl 一致
        assertThat(OmniToolEnchantments.getEnchantmentSourceLevel(stack, SHARPNESS_ID)).isEqualTo(5);
        assertThat(OmniToolEnchantments.hasStoredEnchantments(stack)).isTrue();
    }

    @Test
    void testLevelIncreaseIsClampedBySourceMax() {
        ItemStack stack = newToolStack();
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SHARPNESS_ID, 5);
        // 调低再调高:不能超过来源等级 max
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SHARPNESS_ID, 3);
        assertThat(OmniToolEnchantments.getStoredEnchantmentLevel(stack, SHARPNESS_ID)).isEqualTo(3);
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SHARPNESS_ID, 10);
        assertThat(OmniToolEnchantments.getStoredEnchantmentLevel(stack, SHARPNESS_ID)).isEqualTo(5);
        // max 保持不变
        assertThat(OmniToolEnchantments.getEnchantmentSourceLevel(stack, SHARPNESS_ID)).isEqualTo(5);
    }

    @Test
    void testSetZeroRemovesEntry() {
        ItemStack stack = newToolStack();
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SHARPNESS_ID, 5);
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SHARPNESS_ID, 0);
        assertThat(OmniToolEnchantments.getStoredEnchantmentLevel(stack, SHARPNESS_ID)).isZero();
        assertThat(OmniToolEnchantments.hasStoredEnchantments(stack)).isFalse();
        // 列表清空后整个存储键被移除(标签内最后一个键移除后标签本身被置空)
        assertThat(hasEnchantmentStorage(stack)).isFalse();
    }

    @Test
    void testSetNegativeLevelOnMissingEntryIsNoOp() {
        ItemStack stack = newToolStack();
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SHARPNESS_ID, -1);
        assertThat(OmniToolEnchantments.hasStoredEnchantments(stack)).isFalse();
    }

    @Test
    void testSourceLevelFallsBackToLvlWhenMaxAbsent() {
        // 手工构造没有 max 字段的存储条目(旧格式兼容)
        ItemStack stack = newToolStack();
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putString("id", SHARPNESS_ID.toString());
        entry.putShort("lvl", (short) 4);
        list.add(entry);
        stack.getOrCreateTag().put(OmniToolNBT.ENCHANTMENTS, list);

        assertThat(OmniToolEnchantments.getEnchantmentSourceLevel(stack, SHARPNESS_ID)).isEqualTo(4);
    }

    @Test
    void testSetStoredEnchantmentsNullOrEmptyRemovesKey() {
        ItemStack stack = newToolStack();
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SHARPNESS_ID, 5);
        OmniToolEnchantments.setStoredEnchantments(stack, null);
        assertThat(hasEnchantmentStorage(stack)).isFalse();

        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SHARPNESS_ID, 5);
        OmniToolEnchantments.setStoredEnchantments(stack, new ListTag());
        assertThat(hasEnchantmentStorage(stack)).isFalse();
    }

    /**
     * 判断物品是否带有存储附魔键(标签可能因最后一个键被移除而整体置空).
     */
    private static boolean hasEnchantmentStorage(ItemStack stack) {
        return stack.hasTag() && stack.getTag().contains(OmniToolNBT.ENCHANTMENTS);
    }

    // ==================== 附魔书复制 ====================

    @Test
    void testCopyEnchantmentsFromBook() {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(Enchantments.SHARPNESS, 10));

        ListTag copied = OmniToolEnchantments.copyEnchantmentsFromBook(book);
        assertThat(copied).hasSize(1);
        CompoundTag entry = copied.getCompound(0);
        assertThat(entry.getString("id")).isEqualTo(SHARPNESS_ID.toString());
        assertThat(entry.getShort("lvl")).isEqualTo((short) 10);
        assertThat(entry.getShort("max")).isEqualTo((short) 10);
    }

    @Test
    void testCopyEnchantmentsFromBookClampsToConfigMax() {
        // 配置默认 maxEnchantmentLevel = 255
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(Enchantments.SHARPNESS, 300));

        ListTag copied = OmniToolEnchantments.copyEnchantmentsFromBook(book);
        CompoundTag entry = copied.getCompound(0);
        assertThat(entry.getShort("lvl")).isEqualTo((short) 255);
        assertThat(entry.getShort("max")).isEqualTo((short) 255);
    }

    @Test
    void testCopiedListIsIndependentOfBook() {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(book, new EnchantmentInstance(Enchantments.SHARPNESS, 5));

        ListTag copied = OmniToolEnchantments.copyEnchantmentsFromBook(book);
        copied.clear();
        // 修改返回的列表不影响附魔书本身
        assertThat(EnchantedBookItem.getEnchantments(book)).hasSize(1);
    }

    // ==================== 可见附魔同步 ====================

    @Test
    void testUpdateEnchantmentsMirrorsStoredToVisible() {
        ItemStack stack = newToolStack();
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SHARPNESS_ID, 5);
        assertThat(EnchantmentHelper.getEnchantments(stack))
                .containsEntry(Enchantments.SHARPNESS, 5);
    }

    @Test
    void testUpdateEnchantmentsSkipsInvalidAndUnknownIds() {
        ItemStack stack = newToolStack();
        ListTag list = new ListTag();
        // 非法 id(无法解析)
        CompoundTag bad = new CompoundTag();
        bad.putString("id", "not:a:valid:id");
        bad.putShort("lvl", (short) 3);
        list.add(bad);
        // 合法但未注册的 id
        CompoundTag unknown = new CompoundTag();
        unknown.putString("id", "minecraft:nonexistent_enchant");
        unknown.putShort("lvl", (short) 3);
        list.add(unknown);
        // 正常条目
        CompoundTag good = new CompoundTag();
        good.putString("id", SHARPNESS_ID.toString());
        good.putShort("lvl", (short) 2);
        list.add(good);

        OmniToolEnchantments.setStoredEnchantments(stack, list);
        assertThat(EnchantmentHelper.getEnchantments(stack))
                .hasSize(1)
                .containsEntry(Enchantments.SHARPNESS, 2);
    }

    @Test
    void testUpdateEnchantmentsSkipsNonPositiveLevel() {
        ItemStack stack = newToolStack();
        ListTag list = new ListTag();
        CompoundTag zero = new CompoundTag();
        zero.putString("id", SHARPNESS_ID.toString());
        zero.putShort("lvl", (short) 0);
        list.add(zero);
        OmniToolEnchantments.setStoredEnchantments(stack, list);
        assertThat(EnchantmentHelper.getEnchantments(stack)).isEmpty();
    }

    @Test
    void testBookSilkTouchWinsOverToggle() {
        ItemStack stack = newToolStack();
        // 存储区已有精准采集
        OmniToolEnchantments.setStoredEnchantmentLevel(stack, SILK_TOUCH_ID, 1);
        // 再打开工具自带精准采集开关,不应产生重复条目
        OmniToolUpgrades.setSilkTouchEnabled(stack, true);
        assertThat(EnchantmentHelper.getEnchantments(stack))
                .hasSize(1)
                .containsEntry(Enchantments.SILK_TOUCH, 1);
    }

    @Test
    void testRegistryLookupMatchesVanillaIds() {
        // 防御性检查:测试所用的附魔 id 与原版注册表一致
        assertThat(BuiltInRegistries.ENCHANTMENT.getKey(Enchantments.SHARPNESS)).isEqualTo(SHARPNESS_ID);
        assertThat(BuiltInRegistries.ENCHANTMENT.getKey(Enchantments.SILK_TOUCH)).isEqualTo(SILK_TOUCH_ID);
    }
}
