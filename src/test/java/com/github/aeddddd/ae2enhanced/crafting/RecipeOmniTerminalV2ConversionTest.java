package com.github.aeddddd.ae2enhanced.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 阶段 5：旧终端 → II 的转换配方。
 *
 * <p>关键是<strong>NBT 零迁移</strong>：{@code storageId}（WorldSavedData 的键）、
 * {@code encryptionKey}（绑定）、{@code internalCurrentPower}（电量）、
 * {@code omni_layout}（布局）都必须原样过去。</p>
 */
class RecipeOmniTerminalV2ConversionTest {

    @BeforeAll
    static void boot() {
        // 只需 MC 物品注册表（构造 ItemStack 用）；Bootstrap.register() 自带幂等 guard
        Bootstrap.register();
    }

    private static RecipeOmniTerminalV2Conversion recipe() {
        return new RecipeOmniTerminalV2Conversion(
                new ResourceLocation("ae2enhanced", "omni_wireless_terminal_v2_conversion"),
                Items.DIAMOND, Items.STICK);
    }

    private static InventoryCrafting grid(ItemStack... stacks) {
        InventoryCrafting inventory = new InventoryCrafting(new NoopContainer(), 3, 3);
        for (int i = 0; i < stacks.length; i++) {
            inventory.setInventorySlotContents(i, stacks[i]);
        }
        return inventory;
    }

    /** 平凡容器：只为满足 InventoryCrafting 的构造签名（不参与任何逻辑）。 */
    private static final class NoopContainer extends Container {
        @Override
        public void onCraftMatrixChanged(net.minecraft.inventory.IInventory inventory) {
        }

        @Override
        public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
            return true;
        }
    }

    @Test
    void onlyASingleOldTerminalMatches() {
        // 旧终端是"STICK"，新物品是"DIAMOND"
        assertTrue(recipe().matches(grid(new ItemStack(Items.STICK)), null));
        // 第二件会破坏 1:1
        assertFalse(recipe().matches(grid(new ItemStack(Items.STICK), new ItemStack(Items.STICK)), null));
        // 别的物品不匹配
        assertFalse(recipe().matches(grid(new ItemStack(Items.APPLE)), null));
        // 混搭不匹配
        assertFalse(recipe().matches(grid(new ItemStack(Items.STICK), new ItemStack(Items.APPLE)), null));
        // 空的合成格不匹配
        assertFalse(recipe().matches(grid(), null), "空网格不该匹配");
    }

    @Test
    void conversionSwapsTheItemAndKeepsEveryNbtPayload() {
        ItemStack oldTerminal = new ItemStack(Items.STICK, 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("storageId", "11111111-2222-3333-4444-555555555555");
        tag.setString("encryptionKey", "enc-key-42");
        tag.setDouble("internalCurrentPower", 4096.0D);
        tag.setString("omni_layout", "{\"version\":6,\"roots\":[]}");
        oldTerminal.setTagCompound(tag);

        ItemStack result = recipe().getCraftingResult(grid(oldTerminal));

        assertNotNull(result);
        assertEquals(Items.DIAMOND, result.getItem(), "物品类型换成新一代");
        assertEquals(1, result.getCount(), "结果数量恒为 1");
        NBTTagCompound out = result.getTagCompound();
        assertNotNull(out, "NBT 必须保留");
        assertEquals("11111111-2222-3333-4444-555555555555", out.getString("storageId"));
        assertEquals("enc-key-42", out.getString("encryptionKey"));
        assertEquals(4096.0D, out.getDouble("internalCurrentPower"), 0.0001D);
        assertEquals("{\"version\":6,\"roots\":[]}", out.getString("omni_layout"));
        // 原栈不受影响（配方不得就地改输入）
        assertEquals(Items.STICK, oldTerminal.getItem());
    }

    @Test
    void craftingResultIsEmptyForNonMatchingGrids() {
        assertTrue(recipe().getCraftingResult(grid(new ItemStack(Items.APPLE))).isEmpty());
        assertTrue(recipe().getCraftingResult(grid()).isEmpty());
    }
}
