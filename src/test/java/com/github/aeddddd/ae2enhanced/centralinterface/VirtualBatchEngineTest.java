package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link VirtualBatchEngine} 纯函数部分测试：mergeProducts / mergeItemCosts / getNetCosts。
 * 这些方法不触碰 owner 与网络，可直接用 mock 的 {@link DualityCentralInterface} 构造引擎。
 */
public class VirtualBatchEngineTest {

    private VirtualBatchEngine engine;
    private TargetBinding target;

    @BeforeAll
    public static void boot() {
        // ItemStack / AEItemStack 需要无头引导
        AE2TestBootstrap.boot();
    }

    @BeforeEach
    public void setUp() {
        this.engine = new VirtualBatchEngine(mock(DualityCentralInterface.class));
        this.target = new TargetBinding(new BlockPos(0, 64, 0), 0, "test:machine");
    }

    // ------------------------------------------------------------------
    // mergeProducts
    // ------------------------------------------------------------------

    /** null 与单元素列表原样返回（同一对象引用）。 */
    @Test
    public void testMergeProductsNullOrSingleReturnedAsIs() {
        assertThat(engine.mergeProducts(null)).isNull();

        List<ItemStack> single = new ArrayList<>();
        single.add(new ItemStack(Blocks.STONE, 5));
        assertThat(engine.mergeProducts(single)).isSameAs(single);

        List<ItemStack> empty = new ArrayList<>();
        assertThat(engine.mergeProducts(empty)).isSameAs(empty);
    }

    /** 同物品同 NBT 合并到 maxStackSize，超出部分作为 leftover 追加。 */
    @Test
    public void testMergeProductsOverflowProducesLeftover() {
        List<ItemStack> merged = engine.mergeProducts(Arrays.asList(
                new ItemStack(Blocks.STONE, 40),
                new ItemStack(Blocks.STONE, 40)));

        assertThat(merged).hasSize(2);
        assertThat(merged.get(0).getCount()).isEqualTo(64);
        assertThat(merged.get(1).getCount()).isEqualTo(16);
    }

    /** 多次合并可填满一个堆叠。 */
    @Test
    public void testMergeProductsMultipleIntoOneStack() {
        List<ItemStack> merged = engine.mergeProducts(Arrays.asList(
                new ItemStack(Blocks.STONE, 10),
                new ItemStack(Blocks.STONE, 20),
                new ItemStack(Blocks.STONE, 30)));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getCount()).isEqualTo(60);
    }

    /** NBT 不同（含一方无 NBT）不合并。 */
    @Test
    public void testMergeProductsDifferentNbtNotMerged() {
        ItemStack withTag = new ItemStack(Blocks.STONE, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("k", 1);
        withTag.setTagCompound(tag);

        List<ItemStack> merged = engine.mergeProducts(Arrays.asList(
                new ItemStack(Blocks.STONE, 3), withTag));

        assertThat(merged).hasSize(2);
    }

    /** 不同物品不合并；空堆叠被跳过。 */
    @Test
    public void testMergeProductsDifferentItemsAndEmptyStacks() {
        List<ItemStack> merged = engine.mergeProducts(Arrays.asList(
                ItemStack.EMPTY,
                new ItemStack(Blocks.STONE, 5),
                new ItemStack(Blocks.DIRT, 7)));

        assertThat(merged).hasSize(2);
    }

    /** 合并结果不修改入参原堆叠（防御性拷贝）。 */
    @Test
    public void testMergeProductsDoesNotMutateInput() {
        ItemStack a = new ItemStack(Blocks.STONE, 10);
        ItemStack b = new ItemStack(Blocks.STONE, 20);
        engine.mergeProducts(Arrays.asList(a, b));

        assertThat(a.getCount()).isEqualTo(10);
        assertThat(b.getCount()).isEqualTo(20);
    }

    // ------------------------------------------------------------------
    // mergeItemCosts
    // ------------------------------------------------------------------

    /** 同种物品（可能分散在多个 crafting slot）聚合为单个条目。 */
    @Test
    public void testMergeItemCostsAggregatesSameItem() {
        List<IAEStack> merged = VirtualBatchEngine.mergeItemCosts(Arrays.asList(
                ae(Blocks.STONE, 3),
                ae(Blocks.STONE, 5)));

        assertThat(merged).hasSize(1);
        assertThat(merged.get(0).getStackSize()).isEqualTo(8);
        assertThat(((IAEItemStack) merged.get(0)).getItem())
                .isSameAs(Item.getItemFromBlock(Blocks.STONE));
    }

    /** 不同物品分别成条；零尺寸成本被跳过；null/空列表返回空。 */
    @Test
    public void testMergeItemCostsDistinctItemsAndZeroSkipped() {
        List<IAEStack> merged = VirtualBatchEngine.mergeItemCosts(Arrays.asList(
                ae(Blocks.STONE, 3),
                ae(Blocks.DIRT, 2),
                ae(Blocks.STONE, 0)));

        assertThat(merged).hasSize(2);
        assertThat(VirtualBatchEngine.mergeItemCosts(null)).isEmpty();
        assertThat(VirtualBatchEngine.mergeItemCosts(Collections.emptyList())).isEmpty();
    }

    /** 非物品栈（流体/能量等）原样保留，不参与物品聚合。 */
    @Test
    public void testMergeItemCostsPreservesNonItemStacks() {
        IAEStack fluidLike = mock(IAEStack.class);
        when(fluidLike.getStackSize()).thenReturn(100L);

        List<IAEStack> merged = VirtualBatchEngine.mergeItemCosts(Arrays.asList(
                ae(Blocks.STONE, 3), fluidLike));

        assertThat(merged).hasSize(2);
        assertThat(merged).contains(fluidLike);
    }

    // ------------------------------------------------------------------
    // getNetCosts
    // ------------------------------------------------------------------

    /** parallel <= 0 返回空列表。 */
    @Test
    public void testGetNetCostsNonPositiveParallel() {
        IVirtualBatchCraftingHandler handler = mock(IVirtualBatchCraftingHandler.class);
        List<IAEStack> net = engine.getNetCosts(handler, null, target, null, null, 0, null);
        assertThat(net).isEmpty();
    }

    /** 物品成本 = parallel 份全量 − CPU 已预提的 1 份；parallel = 1 时净成本为空。 */
    @Test
    public void testGetNetCostsSubtractsCpuPrefetchedCopy() {
        IVirtualBatchCraftingHandler handler = mock(IVirtualBatchCraftingHandler.class);
        stubCost(handler, 3, list(ae(Blocks.STONE, 9)));
        stubCost(handler, 1, list(ae(Blocks.STONE, 3)));

        List<IAEStack> net = engine.getNetCosts(handler, null, target, null, null, 3, null);

        assertThat(net).hasSize(1);
        assertThat(sizeOf(net, Item.getItemFromBlock(Blocks.STONE))).isEqualTo(6);

        // parallel = 1：全量与单份相同，净成本为空
        List<IAEStack> netOne = engine.getNetCosts(handler, null, target, null, null, 1, null);
        assertThat(netOne).isEmpty();
    }

    /** 物品按身份对齐而非 List 下标：两次调用返回顺序相反也能正确扣减。 */
    @Test
    public void testGetNetCostsAlignsByItemIdentityNotIndex() {
        IVirtualBatchCraftingHandler handler = mock(IVirtualBatchCraftingHandler.class);
        stubCost(handler, 3, list(ae(Blocks.STONE, 9), ae(Blocks.DIRT, 6)));
        // 单份成本以相反顺序返回
        stubCost(handler, 1, list(ae(Blocks.DIRT, 2), ae(Blocks.STONE, 3)));

        List<IAEStack> net = engine.getNetCosts(handler, null, target, null, null, 3, null);

        assertThat(sizeOf(net, Item.getItemFromBlock(Blocks.STONE))).isEqualTo(6);
        assertThat(sizeOf(net, Item.getItemFromBlock(Blocks.DIRT))).isEqualTo(4);
    }

    /** 单份成本缺失某物品时，按 全量 / parallel 回退推算首份。 */
    @Test
    public void testGetNetCostsFallbackDivisionWhenPerCopyMissing() {
        IVirtualBatchCraftingHandler handler = mock(IVirtualBatchCraftingHandler.class);
        stubCost(handler, 3, list(ae(Blocks.STONE, 9)));
        stubCost(handler, 1, Collections.emptyList());

        List<IAEStack> net = engine.getNetCosts(handler, null, target, null, null, 3, null);

        assertThat(sizeOf(net, Item.getItemFromBlock(Blocks.STONE))).isEqualTo(6);
    }

    /** 非物品成本（CPU 不预提取）全额保留。 */
    @Test
    public void testGetNetCostsKeepsNonItemCostsInFull() {
        IAEStack fluidLike = mock(IAEStack.class);
        when(fluidLike.getStackSize()).thenReturn(30L);
        IAEStack fluidCopy = mock(IAEStack.class);
        when(fluidLike.copy()).thenReturn(fluidCopy);

        IVirtualBatchCraftingHandler handler = mock(IVirtualBatchCraftingHandler.class);
        stubCost(handler, 3, list(fluidLike, ae(Blocks.STONE, 9)));
        stubCost(handler, 1, list(ae(Blocks.STONE, 3)));

        List<IAEStack> net = engine.getNetCosts(handler, null, target, null, null, 3, null);

        assertThat(net).hasSize(2);
        assertThat(net).contains(fluidCopy);
        verify(fluidCopy).setStackSize(30L);
        assertThat(sizeOf(net, Item.getItemFromBlock(Blocks.STONE))).isEqualTo(6);
    }

    /** 批量成本 null 表示无法计价；只有明确的空列表才表示零成本。 */
    @Test
    public void testGetNetCostsRejectsNullButKeepsExplicitZeroCost() {
        IVirtualBatchCraftingHandler handler = mock(IVirtualBatchCraftingHandler.class);
        stubCost(handler, 3, null);

        assertThat(engine.getNetCosts(handler, null, target, null, null, 3, null)).isNull();

        stubCost(handler, 3, Collections.emptyList());
        assertThat(engine.getNetCosts(handler, null, target, null, null, 3, null)).isEmpty();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static IAEItemStack ae(net.minecraft.block.Block block, int count) {
        return AEItemStack.fromItemStack(new ItemStack(block, count));
    }

    private static List<IAEStack> list(IAEStack... stacks) {
        return new ArrayList<>(Arrays.asList(stacks));
    }

    /** 按 count 桩 handler 的 6 参 getVirtualCost。 */
    private static void stubCost(IVirtualBatchCraftingHandler handler, long count, List<IAEStack> result) {
        when(handler.getVirtualCost(any(), any(), any(), any(), eq(count), any())).thenReturn(result);
    }

    /** 在净成本列表中按物品身份查找条目尺寸；不存在返回 -1。 */
    private static long sizeOf(List<IAEStack> net, Item item) {
        for (IAEStack stack : net) {
            if (stack instanceof IAEItemStack && ((IAEItemStack) stack).getItem() == item) {
                return stack.getStackSize();
            }
        }
        return -1;
    }
}
