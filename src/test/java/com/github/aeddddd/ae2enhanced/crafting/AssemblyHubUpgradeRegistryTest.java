package com.github.aeddddd.ae2enhanced.crafting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.github.aeddddd.ae2enhanced.test.util.AE2TestBootstrap;

/**
 * {@link AssemblyHubUpgradeRegistry} 测试。
 *
 * <p>覆盖 UpgradeDefinition 构造规范化（强制 count=1、数组克隆）、
 * register/findFor 精确匹配语义、getCustomMaxStack、getParallelValue /
 * getSpeedValue 的取值与回退逻辑。</p>
 *
 * <p>{@link AfterEach} 通过 removeById 删除本类注册的 key，避免污染其它测试。</p>
 */
public class AssemblyHubUpgradeRegistryTest {

    /** 本类注册占用的 key（item registryName#meta）。 */
    private static final String KEY_REDSTONE = "minecraft:redstone#0";
    private static final String KEY_GLOWSTONE = "minecraft:glowstone_dust#0";

    @BeforeAll
    public static void boot() {
        // 用例中构造 ItemStack，需无头引导
        AE2TestBootstrap.boot();
    }

    @AfterEach
    public void cleanup() {
        // 清理本类注册的条目
        AssemblyHubUpgradeRegistry.removeById(KEY_REDSTONE);
        AssemblyHubUpgradeRegistry.removeById(KEY_GLOWSTONE);
    }

    private static AssemblyHubUpgradeRegistry.UpgradeDefinition parallelDef(ItemStack stack,
            int maxStack, long... values) {
        return new AssemblyHubUpgradeRegistry.UpgradeDefinition(stack,
                AssemblyHubUpgradeRegistry.UpgradeType.PARALLEL, maxStack, values);
    }

    private static AssemblyHubUpgradeRegistry.UpgradeDefinition speedDef(ItemStack stack,
            int maxStack, long... values) {
        return new AssemblyHubUpgradeRegistry.UpgradeDefinition(stack,
                AssemblyHubUpgradeRegistry.UpgradeType.SPEED, maxStack, values);
    }

    // ------------------------------------------------------------------
    // UpgradeDefinition 构造规范化
    // ------------------------------------------------------------------

    /** 构造时拷贝物品并强制 count=1，传入栈不受影响。 */
    @Test
    public void testDefinitionForcesCountOne() {
        ItemStack stack = new ItemStack(Items.REDSTONE, 16);
        AssemblyHubUpgradeRegistry.UpgradeDefinition def = parallelDef(stack, 4, 1L);

        assertThat(def.item.getCount()).isEqualTo(1);
        assertThat(def.item).isNotSameAs(stack);
        assertThat(stack.getCount()).isEqualTo(16);
    }

    /** values 数组被克隆：修改传入数组不影响定义。 */
    @Test
    public void testDefinitionClonesValues() {
        long[] values = { 2L, 4L };
        AssemblyHubUpgradeRegistry.UpgradeDefinition def = parallelDef(
                new ItemStack(Items.REDSTONE), 4, values);

        values[0] = 999L;

        assertThat(def.values).containsExactly(2L, 4L);
    }

    /** null values 归一化为空数组。 */
    @Test
    public void testDefinitionNullValuesBecomesEmpty() {
        AssemblyHubUpgradeRegistry.UpgradeDefinition def = new AssemblyHubUpgradeRegistry.UpgradeDefinition(
                new ItemStack(Items.REDSTONE), AssemblyHubUpgradeRegistry.UpgradeType.PARALLEL, 4, null);

        assertThat(def.values).isNotNull().isEmpty();
    }

    // ------------------------------------------------------------------
    // register / findFor
    // ------------------------------------------------------------------

    /** 注册后 findFor 精确命中（同物品同 meta）；不同 meta / 不同物品 miss。 */
    @Test
    public void testRegisterAndFindFor() {
        AssemblyHubUpgradeRegistry.UpgradeDefinition def = parallelDef(
                new ItemStack(Items.REDSTONE), 4, 2L);
        AssemblyHubUpgradeRegistry.register(def);

        assertThat(AssemblyHubUpgradeRegistry.findFor(new ItemStack(Items.REDSTONE, 64, 0))).isSameAs(def);
        assertThat(AssemblyHubUpgradeRegistry.findFor(new ItemStack(Items.GLOWSTONE_DUST, 1))).isNull();
    }

    /** findFor 对空栈返回 null。 */
    @Test
    public void testFindForEmptyStack() {
        assertThat(AssemblyHubUpgradeRegistry.findFor(ItemStack.EMPTY)).isNull();
    }

    /** 同 key 重复注册时后者覆盖前者（Map.put 语义）。 */
    @Test
    public void testDuplicateRegisterOverwrites() {
        AssemblyHubUpgradeRegistry.UpgradeDefinition first = parallelDef(
                new ItemStack(Items.REDSTONE), 4, 2L);
        AssemblyHubUpgradeRegistry.UpgradeDefinition second = speedDef(
                new ItemStack(Items.REDSTONE), 2, 10L);
        AssemblyHubUpgradeRegistry.register(first);
        AssemblyHubUpgradeRegistry.register(second);

        assertThat(AssemblyHubUpgradeRegistry.findFor(new ItemStack(Items.REDSTONE))).isSameAs(second);
    }

    // ------------------------------------------------------------------
    // keyOf
    // ------------------------------------------------------------------

    /** 无注册名的不同 Item 实例生成不同 key，不再碰撞为 "unknown#meta"。 */
    @Test
    public void testKeyOfUnregisteredItemsDoNotCollide() {
        Item itemA = new Item();
        Item itemB = new Item();
        ItemStack stackA = new ItemStack(itemA, 1);
        ItemStack stackB = new ItemStack(itemB, 1);

        assertThat(stackA.getItem().getRegistryName()).isNull();
        assertThat(stackB.getItem().getRegistryName()).isNull();
        assertThat(AssemblyHubUpgradeRegistry.keyOf(stackA))
                .isNotEqualTo(AssemblyHubUpgradeRegistry.keyOf(stackB));
    }

    /** 无注册名的同一 Item 实例两次调用 keyOf 结果稳定一致。 */
    @Test
    public void testKeyOfUnregisteredItemStable() {
        Item item = new Item();

        assertThat(AssemblyHubUpgradeRegistry.keyOf(new ItemStack(item, 1)))
                .isEqualTo(AssemblyHubUpgradeRegistry.keyOf(new ItemStack(item, 64)));
    }

    /** 无注册名物品注册后 findFor 可命中，且不会被其它未注册物品误命中。 */
    @Test
    public void testRegisterUnregisteredItem() {
        Item itemA = new Item();
        Item itemB = new Item();
        AssemblyHubUpgradeRegistry.UpgradeDefinition def = parallelDef(
                new ItemStack(itemA, 1), 4, 2L);
        AssemblyHubUpgradeRegistry.register(def);
        try {
            assertThat(AssemblyHubUpgradeRegistry.findFor(new ItemStack(itemA, 1))).isSameAs(def);
            assertThat(AssemblyHubUpgradeRegistry.findFor(new ItemStack(itemB, 1))).isNull();
        } finally {
            AssemblyHubUpgradeRegistry.removeById(
                    AssemblyHubUpgradeRegistry.keyOf(new ItemStack(itemA, 1)));
        }
    }

    // ------------------------------------------------------------------
    // removeById
    // ------------------------------------------------------------------

    /** removeById 移除已注册条目，移除后 findFor 不再命中。 */
    @Test
    public void testRemoveByIdRemoves() {
        AssemblyHubUpgradeRegistry.register(parallelDef(new ItemStack(Items.REDSTONE), 4, 2L));

        assertThat(AssemblyHubUpgradeRegistry.removeById(KEY_REDSTONE)).isTrue();
        assertThat(AssemblyHubUpgradeRegistry.findFor(new ItemStack(Items.REDSTONE))).isNull();
    }

    /** 对不存在的 key / null / 空串调用 removeById 返回 false，幂等无副作用。 */
    @Test
    public void testRemoveByIdNonExistentIsIdempotent() {
        assertThat(AssemblyHubUpgradeRegistry.removeById("ahreg_test:nonexistent#0")).isFalse();
        assertThat(AssemblyHubUpgradeRegistry.removeById("ahreg_test:nonexistent#0")).isFalse();
        assertThat(AssemblyHubUpgradeRegistry.removeById(null)).isFalse();
        assertThat(AssemblyHubUpgradeRegistry.removeById("")).isFalse();
    }

    // ------------------------------------------------------------------
    // getCustomMaxStack
    // ------------------------------------------------------------------

    /** 未注册的物品返回 -1（调用方回退默认逻辑）；已注册返回定义的 maxStack。 */
    @Test
    public void testGetCustomMaxStack() {
        assertThat(AssemblyHubUpgradeRegistry.getCustomMaxStack(new ItemStack(Items.REDSTONE))).isEqualTo(-1);

        AssemblyHubUpgradeRegistry.register(parallelDef(new ItemStack(Items.REDSTONE), 7, 2L));
        assertThat(AssemblyHubUpgradeRegistry.getCustomMaxStack(new ItemStack(Items.REDSTONE))).isEqualTo(7);
    }

    // ------------------------------------------------------------------
    // getParallelValue / getSpeedValue
    // ------------------------------------------------------------------

    /** getParallelValue：未注册或非 PARALLEL 类型返回 -1。 */
    @Test
    public void testGetParallelValueUnregisteredOrWrongType() {
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        assertThat(AssemblyHubUpgradeRegistry.getParallelValue(redstone, 1)).isEqualTo(-1);

        // 注册为 SPEED 类型时，查询并行值仍返回 -1
        AssemblyHubUpgradeRegistry.register(speedDef(redstone, 4, 10L));
        assertThat(AssemblyHubUpgradeRegistry.getParallelValue(redstone, 1)).isEqualTo(-1);
    }

    /** getParallelValue：count<=0 返回默认 64；values 为空数组时同样回落 64。 */
    @Test
    public void testGetParallelValueDefaultFallback() {
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        AssemblyHubUpgradeRegistry.register(parallelDef(redstone, 4, 2L, 4L));

        assertThat(AssemblyHubUpgradeRegistry.getParallelValue(redstone, 0)).isEqualTo(64L);
        assertThat(AssemblyHubUpgradeRegistry.getParallelValue(redstone, -3)).isEqualTo(64L);
    }

    /** getParallelValue：索引 0 对应 1 张卡；count 超出 values 长度时钳制到最后一个值。 */
    @Test
    public void testGetParallelValueIndexing() {
        ItemStack redstone = new ItemStack(Items.REDSTONE);
        AssemblyHubUpgradeRegistry.register(parallelDef(redstone, 4, 2L, 8L, 32L));

        assertThat(AssemblyHubUpgradeRegistry.getParallelValue(redstone, 1)).isEqualTo(2L);
        assertThat(AssemblyHubUpgradeRegistry.getParallelValue(redstone, 3)).isEqualTo(32L);
        // 超出 values 长度：钳制到最后一个元素
        assertThat(AssemblyHubUpgradeRegistry.getParallelValue(redstone, 99)).isEqualTo(32L);
    }

    /** getSpeedValue：未注册或非 SPEED 类型返回 -1。 */
    @Test
    public void testGetSpeedValueUnregisteredOrWrongType() {
        ItemStack glowstone = new ItemStack(Items.GLOWSTONE_DUST);
        assertThat(AssemblyHubUpgradeRegistry.getSpeedValue(glowstone, 1)).isEqualTo(-1);

        AssemblyHubUpgradeRegistry.register(parallelDef(glowstone, 4, 2L));
        assertThat(AssemblyHubUpgradeRegistry.getSpeedValue(glowstone, 1)).isEqualTo(-1);
    }

    /** getSpeedValue：count<=0 返回默认 20。 */
    @Test
    public void testGetSpeedValueDefaultFallback() {
        ItemStack glowstone = new ItemStack(Items.GLOWSTONE_DUST);
        AssemblyHubUpgradeRegistry.register(speedDef(glowstone, 4, 10L));

        assertThat(AssemblyHubUpgradeRegistry.getSpeedValue(glowstone, 0)).isEqualTo(20);
        assertThat(AssemblyHubUpgradeRegistry.getSpeedValue(glowstone, -1)).isEqualTo(20);
    }

    /** getSpeedValue：按 count 索引取值，且结果下限为 1（max(val,1)）。 */
    @Test
    public void testGetSpeedValueIndexingAndClamp() {
        ItemStack glowstone = new ItemStack(Items.GLOWSTONE_DUST);
        AssemblyHubUpgradeRegistry.register(speedDef(glowstone, 4, 10L, 0L));

        assertThat(AssemblyHubUpgradeRegistry.getSpeedValue(glowstone, 1)).isEqualTo(10);
        // values[1] = 0，被钳制为 1
        assertThat(AssemblyHubUpgradeRegistry.getSpeedValue(glowstone, 2)).isEqualTo(1);
        // 超出 values 长度：钳制索引到最后一个元素（同样被钳为 1）
        assertThat(AssemblyHubUpgradeRegistry.getSpeedValue(glowstone, 99)).isEqualTo(1);
    }
}
