package com.github.aeddddd.ae2enhanced.test.crafting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import com.github.aeddddd.ae2enhanced.blockentity.MicroSingularityBlockEntity;
import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRitualRecipe;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;

/**
 * {@link SingularityRitualRecipe} 单元测试:构造默认值、区域匹配与仪式执行.
 * <p>Level/ItemEntity 均 mock,物品堆为真实实例以验证数量扣减.</p>
 */
class SingularityRitualRecipeTest {

    static {
        CraftingTestFixtures.init();
    }

    private static final ResourceLocation ID = new ResourceLocation("ae2enhanced", "ritual");
    private static final BlockPos CENTER = new BlockPos(0, 64, 0);

    /** 构造默认值:null 输入 → 空表,null 手持 → EMPTY,非正寿命 → 默认寿命. */
    @Test
    void testConstructorDefaults() {
        var recipe = new SingularityRitualRecipe(ID, null, null, null, 0);

        assertThat(recipe.getId()).isEqualTo(ID);
        assertThat(recipe.getInputs()).isEmpty();
        assertThat(recipe.getHeldItem()).isSameAs(ItemStack.EMPTY);
        assertThat(recipe.getTargetBlock()).isNull();
        assertThat(recipe.getLifetimeTicks()).isEqualTo(MicroSingularityBlockEntity.DEFAULT_LIFE_TICKS);
    }

    /** 正寿命按构造值保留. */
    @Test
    void testCustomLifetime() {
        var recipe = new SingularityRitualRecipe(ID, List.of(), ItemStack.EMPTY, null, 12000);
        assertThat(recipe.getLifetimeTicks()).isEqualTo(12000);
    }

    /** 无任何要求的配方永远匹配. */
    @Test
    void testMatchesWithNoRequirements() {
        var recipe = new SingularityRitualRecipe(ID, List.of(), ItemStack.EMPTY, null, 0);
        var level = mock(Level.class);

        assertThat(recipe.matches(level, CENTER, ItemStack.EMPTY)).isTrue();
    }

    /** 目标方块不匹配 → false. */
    @Test
    void testTargetBlockMismatch() {
        var recipe = new SingularityRitualRecipe(ID, List.of(), ItemStack.EMPTY, Blocks.BEACON, 0);
        var level = mock(Level.class);
        when(level.getBlockState(CENTER)).thenReturn(Blocks.STONE.defaultBlockState());

        assertThat(recipe.matches(level, CENTER, ItemStack.EMPTY)).isFalse();
    }

    /** 手持物品:缺失/错误 → false;类型匹配(忽略数量) → true. */
    @Test
    void testHeldItemMatching() {
        var recipe = new SingularityRitualRecipe(ID, List.of(), new ItemStack(Items.NETHER_STAR), null, 0);
        var level = mock(Level.class);

        assertThat(recipe.matches(level, CENTER, ItemStack.EMPTY)).isFalse();
        assertThat(recipe.matches(level, CENTER, new ItemStack(Items.STONE))).isFalse();
        assertThat(recipe.matches(level, CENTER, new ItemStack(Items.NETHER_STAR))).isTrue();
        assertThat(recipe.matches(level, CENTER, new ItemStack(Items.NETHER_STAR, 64))).isTrue();
    }

    /** 掉落物:区域内多实体数量累加;不足 → false,盈余 → true. */
    @Test
    void testDroppedInputsMatching() {
        var recipe = new SingularityRitualRecipe(ID,
                List.of(new ItemStack(Items.STONE, 5)), ItemStack.EMPTY, null, 0);
        var level = mock(Level.class);

        var e1 = itemEntity(new ItemStack(Items.STONE, 3));
        var e2 = itemEntity(new ItemStack(Items.STONE, 2));
        when(level.getEntitiesOfClass(eq(ItemEntity.class), any(AABB.class)))
                .thenAnswer(inv -> List.of(e1, e2));

        assertThat(recipe.matches(level, CENTER, ItemStack.EMPTY)).isTrue();

        // 移除一个实体后数量不足
        when(level.getEntitiesOfClass(eq(ItemEntity.class), any(AABB.class)))
                .thenAnswer(inv -> List.of(e1));
        assertThat(recipe.matches(level, CENTER, ItemStack.EMPTY)).isFalse();
    }

    /** 仪式执行:按需消耗区域物品(空堆实体丢弃),并在中心放置微型奇点方块. */
    @Test
    void testCraftConsumesAndPlaces() {
        var recipe = new SingularityRitualRecipe(ID,
                List.of(new ItemStack(Items.STONE, 5), new ItemStack(Items.DIRT, 2)),
                ItemStack.EMPTY, null, 6000);
        var level = mock(Level.class);

        var stoneStack1 = new ItemStack(Items.STONE, 3);
        var stoneStack2 = new ItemStack(Items.STONE, 4);
        var dirtStack = new ItemStack(Items.DIRT, 2);
        var e1 = itemEntity(stoneStack1);
        var e2 = itemEntity(stoneStack2);
        var e3 = itemEntity(dirtStack);
        when(level.getEntitiesOfClass(eq(ItemEntity.class), any(AABB.class)))
                .thenAnswer(inv -> List.of(e1, e2, e3));

        recipe.craft(level, CENTER);

        // 石头共需 5:第一个实体 3 个全消耗并丢弃,第二个实体剩 2
        assertThat(stoneStack1.isEmpty()).isTrue();
        verify(e1).discard();
        assertThat(stoneStack2.getCount()).isEqualTo(2);
        verify(e2, never()).discard();
        // 泥土 2 个恰好耗尽
        assertThat(dirtStack.isEmpty()).isTrue();
        verify(e3).discard();
        // 中心放置微型奇点方块
        verify(level).setBlockAndUpdate(CENTER, CraftingTestFixtures.MICRO_SINGULARITY_BLOCK.defaultBlockState());
    }

    /** 配方接口固定值:不用于工作台. */
    @Test
    void testRecipeInterfaceDefaults() {
        var recipe = new SingularityRitualRecipe(ID, List.of(), ItemStack.EMPTY, null, 0);
        assertThat(recipe.matches(null, null)).isFalse();
        assertThat(recipe.assemble(null, null)).isSameAs(ItemStack.EMPTY);
        assertThat(recipe.canCraftInDimensions(3, 3)).isFalse();
        assertThat(recipe.getResultItem(null)).isSameAs(ItemStack.EMPTY);
        assertThat(recipe.isSpecial()).isTrue();
        assertThat(recipe.getSerializer()).isSameAs(ModRecipes.SINGULARITY_RITUAL_SERIALIZER.get());
        assertThat(recipe.getType()).isSameAs(ModRecipes.SINGULARITY_RITUAL_TYPE.get());
    }

    private static ItemEntity itemEntity(ItemStack stack) {
        var entity = mock(ItemEntity.class);
        when(entity.getItem()).thenReturn(stack);
        return entity;
    }
}
