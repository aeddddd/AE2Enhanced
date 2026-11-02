package com.github.aeddddd.ae2enhanced.centralinterface;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.item.AEItemStack;
import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link IVirtualBatchCraftingHandler} 接口默认方法契约测试。
 *
 * <p>覆盖带 details 参数的默认委托链（委托到无 details 版本）、
 * 各默认值（capabilities=all、defaultParallel=1、skipCooldownOnSingleBatch=false、
 * 粒子=PORTAL），以及 {@code scaleOutputsByCount} 的边界行为：
 * null/空数组/count<=0 → 空列表；按 maxStackSize 切片；stackSize<=0 与空模板跳过。</p>
 */
public class IVirtualBatchCraftingHandlerContractTest {

    @BeforeAll
    public static void boot() {
        // scaleOutputsByCount 用例需构造 ItemStack / AEItemStack
        AE2TestBootstrap.boot();
    }

    // ------------------------------------------------------------------
    // 默认值
    // ------------------------------------------------------------------

    /** getCapabilities 默认返回 all()（物理 + 虚拟批量）。 */
    @Test
    public void testGetCapabilitiesDefaultAll() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();

        assertThat(handler.getCapabilities())
                .containsExactly(HandlerCapabilities.PHYSICAL, HandlerCapabilities.VIRTUAL_BATCH);
        assertThat(handler.hasCapability(HandlerCapabilities.PHYSICAL)).isTrue();
        assertThat(handler.hasCapability(HandlerCapabilities.VIRTUAL_BATCH)).isTrue();
    }

    /** getDefaultParallel 默认 1：必须由虚拟并行卡触发。 */
    @Test
    public void testGetDefaultParallelDefaultOne() {
        assertThat(new MinimalVirtualHandler().getDefaultParallel()).isEqualTo(1L);
    }

    /** skipCooldownOnSingleBatch 默认 false。 */
    @Test
    public void testSkipCooldownOnSingleBatchDefaultFalse() {
        assertThat(new MinimalVirtualHandler().skipCooldownOnSingleBatch()).isFalse();
    }

    /** getVirtualCraftingParticles 默认只含 PORTAL。 */
    @Test
    public void testGetVirtualCraftingParticlesDefaultPortal() {
        assertThat(new MinimalVirtualHandler().getVirtualCraftingParticles(null, null))
                .containsExactly(EnumParticleTypes.PORTAL);
    }

    // ------------------------------------------------------------------
    // 带 details 参数的委托链
    // ------------------------------------------------------------------

    /** canCraftVirtually(带 details) 委托到 4 参版本，并透传返回值（true 分支）。 */
    @Test
    public void testCanCraftVirtuallyWithDetailsDelegatesTrue() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();
        handler.canCraftResult = true;

        assertThat(handler.canCraftVirtually(null, null, null, null, null)).isTrue();
        assertThat(handler.canCraft4ArgCalls).isEqualTo(1);
    }

    /** canCraftVirtually(带 details) 委托到 4 参版本，并透传返回值（false 分支）。 */
    @Test
    public void testCanCraftVirtuallyWithDetailsDelegatesFalse() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();
        handler.canCraftResult = false;

        assertThat(handler.canCraftVirtually(null, null, null, null, null)).isFalse();
        assertThat(handler.canCraft4ArgCalls).isEqualTo(1);
    }

    /** getVirtualCost(带 details) 委托到 5 参版本，返回同一列表实例。 */
    @Test
    public void testGetVirtualCostWithDetailsDelegates() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();
        List<IAEStack> sentinel = new ArrayList<>();
        handler.costResult = sentinel;

        List<IAEStack> result = handler.getVirtualCost(null, null, null, null, 7L, null);

        assertThat(result).isSameAs(sentinel);
        assertThat(handler.cost5ArgCalls).isEqualTo(1);
        assertThat(handler.lastCostCount).isEqualTo(7L);
    }

    /** virtualCraftBatch(带 details) 委托到 6 参版本，返回同一列表实例。 */
    @Test
    public void testVirtualCraftBatchWithDetailsDelegates() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();
        List<ItemStack> sentinel = new ArrayList<>();
        handler.craftResult = sentinel;

        List<ItemStack> result = handler.virtualCraftBatch(null, null, null, null, 3L, null, null);

        assertThat(result).isSameAs(sentinel);
        assertThat(handler.craft6ArgCalls).isEqualTo(1);
        assertThat(handler.lastCraftCount).isEqualTo(3L);
    }

    // ------------------------------------------------------------------
    // scaleOutputsByCount 边界
    // ------------------------------------------------------------------

    /** outputs 为 null 或空数组时返回空列表（非 null）。 */
    @Test
    public void testScaleOutputsNullOrEmptyArray() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();

        assertThat(handler.scaleOutputsByCount(null, 5)).isNotNull().isEmpty();
        assertThat(handler.scaleOutputsByCount(new IAEItemStack[0], 5)).isNotNull().isEmpty();
    }

    /** count <= 0 时返回空列表。 */
    @Test
    public void testScaleOutputsNonPositiveCount() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();
        IAEItemStack output = AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 1));

        assertThat(handler.scaleOutputsByCount(new IAEItemStack[] { output }, 0)).isEmpty();
        assertThat(handler.scaleOutputsByCount(new IAEItemStack[] { output }, -3)).isEmpty();
    }

    /** 单产物按 count 缩放，并按 maxStackSize 切片：苹果(64) count=130 → 64 + 64 + 2。 */
    @Test
    public void testScaleOutputsSlicedByMaxStackSize() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();
        IAEItemStack output = AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 1));

        List<ItemStack> products = handler.scaleOutputsByCount(new IAEItemStack[] { output }, 130);

        assertThat(products).hasSize(3);
        assertThat(products).extracting(ItemStack::getCount).containsExactly(64, 64, 2);
        assertThat(products).allMatch(s -> s.getItem() == Items.APPLE);
    }

    /** 单份 stackSize 与 count 相乘：stackSize=3, count=2 → 总量 6，未超 maxStack 不切片。 */
    @Test
    public void testScaleOutputsMultipliesStackSize() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();
        IAEItemStack output = AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 3));

        List<ItemStack> products = handler.scaleOutputsByCount(new IAEItemStack[] { output }, 2);

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getCount()).isEqualTo(6);
        assertThat(products.get(0).getItem()).isEqualTo(Items.APPLE);
    }

    /** stackSize <= 0 的产物模板被跳过。 */
    @Test
    public void testScaleOutputsSkipsNonPositiveStackSize() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();
        IAEItemStack zero = mock(IAEItemStack.class);
        when(zero.getStackSize()).thenReturn(0L);
        IAEItemStack negative = mock(IAEItemStack.class);
        when(negative.getStackSize()).thenReturn(-5L);
        IAEItemStack valid = AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 1));

        List<ItemStack> products = handler.scaleOutputsByCount(
                new IAEItemStack[] { zero, negative, valid }, 2);

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getCount()).isEqualTo(2);
    }

    /** 任一有效数量模板无法物化时整批拒绝，不能静默丢失其余产物。 */
    @Test
    public void testScaleOutputsRejectsEmptyTemplate() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();
        IAEItemStack emptyTemplate = mock(IAEItemStack.class);
        when(emptyTemplate.getStackSize()).thenReturn(5L);
        when(emptyTemplate.createItemStack()).thenReturn(ItemStack.EMPTY);
        IAEItemStack valid = AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 1));

        List<ItemStack> products = handler.scaleOutputsByCount(
                new IAEItemStack[] { emptyTemplate, valid }, 1);

        assertThat(products).isEmpty();
    }

    /** outputs 数组中的 null 元素被跳过。 */
    @Test
    public void testScaleOutputsSkipsNullElements() {
        MinimalVirtualHandler handler = new MinimalVirtualHandler();
        IAEItemStack valid = AEItemStack.fromItemStack(new ItemStack(Items.APPLE, 2));

        List<ItemStack> products = handler.scaleOutputsByCount(
                new IAEItemStack[] { null, valid }, 1);

        assertThat(products).hasSize(1);
        assertThat(products.get(0).getCount()).isEqualTo(2);
    }

    /**
     * 仅实现抽象方法的最小 fake：三个无 details 抽象方法记录调用次数并返回可控结果，
     * 用于验证带 details 默认方法的委托链。
     */
    private static final class MinimalVirtualHandler implements IVirtualBatchCraftingHandler {
        private boolean canCraftResult;
        private int canCraft4ArgCalls;
        private List<IAEStack> costResult = Collections.emptyList();
        private int cost5ArgCalls;
        private long lastCostCount;
        private List<ItemStack> craftResult = Collections.emptyList();
        private int craft6ArgCalls;
        private long lastCraftCount;

        @Override
        public boolean canHandle(String blockId) {
            return false;
        }

        @Override
        public boolean isValidTarget(World world, BlockPos pos) {
            return false;
        }

        @Override
        public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
            return false;
        }

        @Override
        public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients,
                IActionSource source, TargetSession session) {
            return false;
        }

        @Override
        public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
            return true;
        }

        @Override
        public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
                List<ItemStack> inputs, IActionSource source, TargetSession session) {
            return Collections.emptyList();
        }

        @Override
        public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
            return true;
        }

        @Override
        public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients,
                IAEItemStack[] outputs) {
            canCraft4ArgCalls++;
            return canCraftResult;
        }

        @Override
        public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients,
                IAEItemStack[] outputs, long count) {
            cost5ArgCalls++;
            lastCostCount = count;
            return costResult;
        }

        @Override
        public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients,
                IAEItemStack[] outputs, long count, IActionSource source) {
            craft6ArgCalls++;
            lastCraftCount = count;
            return craftResult;
        }
    }
}
