package com.github.aeddddd.ae2enhanced.crafting;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.oredict.ShapelessOreRecipe;

/**
 * 旧全能无线终端 → 全能无线终端 II 的转换配方（阶段 5）。
 *
 * <p>1:1 无序，<strong>只换物品类型、保留全部 NBT</strong>：{@code old.copy()} + {@code setItem(newItem)}
 * 会把 {@code storageId}（WorldSavedData 的键）、{@code encryptionKey}（绑定）、
 * {@code internalCurrentPower}（电量）以及可拼接终端的 {@code omni_layout} 一并带过去，
 * 因此内容与布局零迁移。</p>
 *
 * <p>写法照 {@link RecipeOmniToolUpgrade} 的先例：覆写 {@code matches}/{@code getCraftingResult}，
 * 自己数格而不是依赖父类的无序匹配（父类只按"输入物品类型"匹配，无法表达"必须恰好一件且是旧终端"）。</p>
 */
public class RecipeOmniTerminalV2Conversion extends ShapelessOreRecipe {

    private final Item source;
    private final Item result;

    public RecipeOmniTerminalV2Conversion(ResourceLocation group, Item result, Item source) {
        // 父类只用于 JEI 展示"无序 1:1"，实际匹配逻辑由下面两个覆写负责
        super(group, new ItemStack(result), new ItemStack(source));
        this.source = source;
        this.result = result;
    }

    @Override
    public boolean matches(InventoryCrafting inv, World world) {
        ItemStack found = ItemStack.EMPTY;
        int nonEmpty = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            nonEmpty++;
            if (stack.getItem() != this.source || !found.isEmpty()) {
                // 只有一件、且必须是旧终端；别的物品或第二件终端都不匹配
                return false;
            }
            found = stack;
        }
        return nonEmpty == 1;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting inv) {
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty() || stack.getItem() != this.source) {
                continue;
            }
            ItemStack converted = new ItemStack(this.result, 1, stack.getMetadata());
            // 1.12.2 的 ItemStack 没有 setItem：换类型只能新建，再把 NBT compound 原样挂上去
            // （storageId / encryptionKey / internalCurrentPower / omni_layout 都在这里）
            if (stack.getTagCompound() != null) {
                converted.setTagCompound(stack.getTagCompound().copy());
            }
            return converted;
        }
        return ItemStack.EMPTY;
    }
}
