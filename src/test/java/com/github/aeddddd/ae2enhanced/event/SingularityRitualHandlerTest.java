package com.github.aeddddd.ae2enhanced.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.github.aeddddd.ae2enhanced.crafting.singularity.SingularityRitualRecipe;

/**
 * {@link SingularityRitualHandler} 单元测试.
 * <p>Level/RecipeManager/配方均 mock,事件对象按真实构造器创建;
 * 服务端粒子分支要求 {@code ServerLevel},此处只验证非 ServerLevel 下的音效与核心流程.</p>
 */
class SingularityRitualHandlerTest {

    private static final BlockPos POS = new BlockPos(1, 64, 1);

    @BeforeAll
    static void setup() {
        EventTestFixtures.init();
    }

    /** 构造玩家手持 held 右键 POS 方块的真实事件对象. */
    private static PlayerInteractEvent.RightClickBlock newEvent(Level level, Player player, ItemStack held) {
        when(player.level()).thenReturn(level);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(held);
        return new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, POS,
                new BlockHitResult(Vec3.ZERO, Direction.UP, POS, false));
    }

    private static Level serverLevelWithRecipes(List<SingularityRitualRecipe> recipes) {
        Level level = mock(Level.class);
        RecipeManager recipeManager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(recipeManager);
        doReturn(recipes).when(recipeManager).getAllRecipesFor(any());
        return level;
    }

    /** 客户端直接返回,不查询配方也不取消事件. */
    @Test
    void testClientSideIgnored() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        Player player = mock(Player.class);
        var event = newEvent(level, player, new ItemStack(Items.NETHER_STAR));

        SingularityRitualHandler.onRightClick(event);

        assertFalse(event.isCanceled());
        verify(level, never()).getRecipeManager();
    }

    /** 配方列表为空时不取消事件. */
    @Test
    void testNoRecipesDoesNotCancel() {
        Level level = serverLevelWithRecipes(List.of());
        Player player = mock(Player.class);
        var event = newEvent(level, player, ItemStack.EMPTY);

        SingularityRitualHandler.onRightClick(event);

        assertFalse(event.isCanceled());
    }

    /** 配方不匹配时不取消事件,也不执行仪式. */
    @Test
    void testNoMatchingRecipeDoesNotCancel() {
        SingularityRitualRecipe recipe = mock(SingularityRitualRecipe.class);
        Level level = serverLevelWithRecipes(List.of(recipe));
        Player player = mock(Player.class);
        ItemStack held = new ItemStack(Items.STONE);
        var event = newEvent(level, player, held);
        when(recipe.matches(level, POS, held)).thenReturn(false);

        SingularityRitualHandler.onRightClick(event);

        assertFalse(event.isCanceled());
        verify(recipe, never()).craft(any(), any());
    }

    /** 匹配配方:取消事件,消耗一个手持物品并执行仪式. */
    @Test
    void testMatchConsumesHeldAndCrafts() {
        SingularityRitualRecipe recipe = mock(SingularityRitualRecipe.class);
        Level level = serverLevelWithRecipes(List.of(recipe));
        Player player = mock(Player.class);
        when(player.isCreative()).thenReturn(false);
        ItemStack held = new ItemStack(Items.NETHER_STAR, 2);
        var event = newEvent(level, player, held);
        when(recipe.matches(level, POS, held)).thenReturn(true);
        when(recipe.getHeldItem()).thenReturn(new ItemStack(Items.NETHER_STAR));

        SingularityRitualHandler.onRightClick(event);

        assertTrue(event.isCanceled());
        assertEquals(1, held.getCount());
        verify(recipe).craft(level, POS);
        // 非 ServerLevel 只播音效,不播粒子
        verify(level).playSound(isNull(), eq(POS), any(), any(), eq(1.0f), eq(0.5f));
    }

    /** 创造模式玩家不消耗手持物品,仪式仍执行. */
    @Test
    void testCreativePlayerDoesNotConsumeHeld() {
        SingularityRitualRecipe recipe = mock(SingularityRitualRecipe.class);
        Level level = serverLevelWithRecipes(List.of(recipe));
        Player player = mock(Player.class);
        when(player.isCreative()).thenReturn(true);
        ItemStack held = new ItemStack(Items.NETHER_STAR, 2);
        var event = newEvent(level, player, held);
        when(recipe.matches(level, POS, held)).thenReturn(true);
        when(recipe.getHeldItem()).thenReturn(new ItemStack(Items.NETHER_STAR));

        SingularityRitualHandler.onRightClick(event);

        assertTrue(event.isCanceled());
        assertEquals(2, held.getCount());
        verify(recipe).craft(level, POS);
    }

    /** 配方无手持要求时不消耗手持物品. */
    @Test
    void testEmptyHeldRequirementDoesNotConsume() {
        SingularityRitualRecipe recipe = mock(SingularityRitualRecipe.class);
        Level level = serverLevelWithRecipes(List.of(recipe));
        Player player = mock(Player.class);
        ItemStack held = new ItemStack(Items.STONE, 5);
        var event = newEvent(level, player, held);
        when(recipe.matches(level, POS, held)).thenReturn(true);
        when(recipe.getHeldItem()).thenReturn(ItemStack.EMPTY);

        SingularityRitualHandler.onRightClick(event);

        assertTrue(event.isCanceled());
        assertEquals(5, held.getCount());
        verify(recipe).craft(level, POS);
    }
}
