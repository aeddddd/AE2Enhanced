package com.github.aeddddd.ae2enhanced.centralinterface.recipe;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecipeBindingConservationTest {
    private IVirtualBatchCraftingHandler scaler;

    @BeforeAll
    static void boot() {
        AE2TestBootstrap.boot();
    }

    static Stream<Arguments> handlerFamilies() {
        return Stream.of(
                Arguments.of("BloodMagic", false),
                Arguments.of("Botania", true),
                Arguments.of("ExtendedCrafting-Compressor", false),
                Arguments.of("ExtendedCrafting-EnderCrafter", false),
                Arguments.of("ExtendedCrafting-Table", false),
                Arguments.of("ExtendedCrafting-Combination", false),
                Arguments.of("Avaritia", false),
                Arguments.of("AstralSorcery", false),
                Arguments.of("Thaumcraft-Infusion", false),
                Arguments.of("Thaumcraft-Crucible", false));
    }

    private static List<ItemStack> actualOutputs(boolean multiOutput) {
        return multiOutput
                ? Arrays.asList(new ItemStack(Items.APPLE, 3), new ItemStack(Items.STICK, 1))
                : Collections.singletonList(new ItemStack(Items.APPLE, 3));
    }

    private static List<ItemStack> copy(List<ItemStack> stacks) {
        List<ItemStack> result = new java.util.ArrayList<>();
        for (ItemStack stack : stacks) result.add(stack.copy());
        return result;
    }

    private static IAEItemStack[] ae(List<ItemStack> stacks) {
        IAEItemStack[] result = new IAEItemStack[stacks.size()];
        for (int i = 0; i < stacks.size(); i++) result[i] = AEItemStack.fromItemStack(stacks.get(i));
        return result;
    }

    private static long total(List<ItemStack> stacks, net.minecraft.item.Item item) {
        long total = 0;
        for (ItemStack stack : stacks) if (stack.getItem() == item) total += stack.getCount();
        return total;
    }

    private static IVirtualBatchCraftingHandler realScaler() {
        IVirtualBatchCraftingHandler handler = mock(IVirtualBatchCraftingHandler.class);
        when(handler.scaleOutputsByCount(any(), anyLong())).thenCallRealMethod();
        return handler;
    }

    @ParameterizedTest(name = "{0}: 合法配方按真实完整输出守恒")
    @MethodSource("handlerFamilies")
    void legalRecipeUsesActualOutputs(String family, boolean multiOutput) {
        scaler = realScaler();
        Object recipe = new Object();
        List<ItemStack> actual = actualOutputs(multiOutput);
        RecipeBinding binding = RecipeBinding.of(recipe, ae(actual), actual);

        assertThat(binding).isNotNull();
        assertThat(binding.getRecipe()).isSameAs(recipe);
        List<ItemStack> products = scaler.scaleOutputsByCount(binding.getOutputs(), 2);
        assertThat(total(products, Items.APPLE)).isEqualTo(6);
        assertThat(total(products, Items.STICK)).isEqualTo(multiOutput ? 2 : 0);
    }

    @ParameterizedTest(name = "{0}: 虚高数量、错误 NBT、额外输出均拒绝")
    @MethodSource("handlerFamilies")
    void forgedOutputsAreRejected(String family, boolean multiOutput) {
        List<ItemStack> actual = actualOutputs(multiOutput);
        List<ItemStack> inflated = copy(actual);
        inflated.get(0).setCount(inflated.get(0).getCount() + 1);
        List<ItemStack> wrongNbt = copy(actual);
        wrongNbt.get(0).setTagCompound(new NBTTagCompound());
        wrongNbt.get(0).getTagCompound().setBoolean("forged", true);
        List<ItemStack> extra = copy(actual);
        extra.add(new ItemStack(Items.DIAMOND, 1));

        assertThat(RecipeBinding.of(new Object(), ae(inflated), actual)).isNull();
        assertThat(RecipeBinding.of(new Object(), ae(wrongNbt), actual)).isNull();
        assertThat(RecipeBinding.of(new Object(), ae(extra), actual)).isNull();
    }

    @ParameterizedTest(name = "{0}: 无法计价与 long 溢出不会生成产物")
    @MethodSource("handlerFamilies")
    void unavailableCostAndOverflowAreRejected(String family, boolean multiOutput) {
        scaler = realScaler();
        RecipeBinding binding = RecipeBinding.of(new Object(), ae(actualOutputs(multiOutput)),
                actualOutputs(multiOutput));
        IAEItemStack output = AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 3));

        assertThat(RecipeCosts.fromIngredients(null, 1)).isNull();
        assertThat(RecipeCosts.fromIngredients(null, Long.MAX_VALUE)).isNull();
        assertThat(scaler.scaleOutputsByCount(new IAEItemStack[]{output}, Long.MAX_VALUE)).isEmpty();
        assertThat(IVirtualBatchCraftingHandler.isOutputScalingSafe(
                new IAEItemStack[]{output}, Long.MAX_VALUE)).isFalse();
        assertThat(binding).isNotNull();
    }
}
