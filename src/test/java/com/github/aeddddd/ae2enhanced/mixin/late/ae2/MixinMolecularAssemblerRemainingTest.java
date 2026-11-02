package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.container.ContainerNull;
import com.github.aeddddd.ae2enhanced.crafting.AssemblerRemainingService;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuExecutionContext;
import com.github.aeddddd.ae2enhanced.crafting.RemainingItemsService;
import com.github.aeddddd.ae2enhanced.mixin.bridge.IPatternHelperAccess;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.NonNullList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MixinMolecularAssemblerRemainingTest {

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    @Test
    void multipleRemaindersIncludingNinthSlotReturnWithoutOverwritingOutput() {
        NonNullList<ItemStack> remaining = NonNullList.withSize(9, ItemStack.EMPTY);
        remaining.set(0, new ItemStack(Items.BUCKET));
        remaining.set(8, new ItemStack(Items.APPLE, 2));
        IRecipe recipe = mock(IRecipe.class);
        when(recipe.getRemainingItems(any())).thenReturn(remaining);
        Object plan = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ICraftingPatternDetails.class, IPatternHelperAccess.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("isCraftable")) return true;
                    if (method.getName().equals("ae2enhanced$standardRecipe")) return recipe;
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    if (method.getReturnType() == long.class) return 0L;
                    return null;
                });

        AssemblerRemainingService.State state = new AssemblerRemainingService.State();
        InventoryCrafting inventory = new InventoryCrafting(new ContainerNull(), 3, 3);
        List<ItemStack> returned = new ArrayList<>();
        for (int slot = 0; slot < 9; slot++) {
            returned.add(AssemblerRemainingService.remaining(state, (ICraftingPatternDetails) plan, inventory,
                    new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE))).copy());
        }

        assertThat(returned.get(0).getItem()).isEqualTo(Items.BUCKET);
        assertThat(returned.subList(1, 8)).allMatch(ItemStack::isEmpty);
        assertThat(returned.get(8).getItem()).isEqualTo(Items.APPLE);
        assertThat(returned.get(8).getCount()).isEqualTo(2);
    }

    @Test
    void cpuWaitingForUsesTheSameCompleteNinthSlotRemainder() {
        NonNullList<ItemStack> remaining = NonNullList.withSize(9, ItemStack.EMPTY);
        remaining.set(8, new ItemStack(Items.APPLE, 2));
        IRecipe recipe = mock(IRecipe.class);
        when(recipe.getRemainingItems(any())).thenReturn(remaining);
        Object plan = Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ICraftingPatternDetails.class, IPatternHelperAccess.class},
                (proxy, method, args) -> method.getName().equals("ae2enhanced$standardRecipe") ? recipe : null);
        CraftingCpuExecutionContext context = new CraftingCpuExecutionContext();
        InventoryCrafting inventory = new InventoryCrafting(new ContainerNull(), 3, 3);
        RemainingItemsService.beginPush(context, (ICraftingPatternDetails) plan, inventory);
        ItemStack ninth = ItemStack.EMPTY;
        for (int slot = 0; slot < 9; slot++) {
            ninth = RemainingItemsService.expected(context, new ItemStack(
                    net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.STONE)));
        }

        assertThat(ninth.getItem()).isEqualTo(Items.APPLE);
        assertThat(ninth.getCount()).isEqualTo(2);
    }
}
