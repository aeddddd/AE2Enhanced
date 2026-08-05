package com.github.aeddddd.ae2enhanced.crafting.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.github.aeddddd.ae2enhanced.registry.ModRecipes;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.RegistryObjectTestInjector;

/**
 * {@link BlackHoleCraftingHelper} 单元测试.
 * <p>Level/RecipeManager/ItemEntity 均 mock,物品堆为真实实例以验证数量扣减.
 * {@code killLivingEntities} 依赖 {@code DamageSources} 与 mixin accessor
 * (测试 JVM 未应用 mixin),不在单测覆盖范围,由 GameTest 验证.</p>
 */
class BlackHoleCraftingHelperTest {

    private static final BlockPos CENTER = new BlockPos(0, 64, 0);
    private static final BlockPos OUTPUT = new BlockPos(0, 66, 0);

    @BeforeAll
    static void setup() {
        MinecraftTestBootstrap.bootstrap();
        ConfigTestBootstrap.loadDefaults();
        // DeferredRegister 只在游戏注册事件中填充,测试环境注入测试配方类型实例
        RegistryObjectTestInjector.inject(ModRecipes.BLACK_HOLE_TYPE, new RecipeType<BlackHoleRecipe>() {
        });
        ensureFluidTypeRegistry();
    }

    /**
     * 创建 Forge 的 fluid_type 注册表并填充 {@code ForgeMod.EMPTY_TYPE}(游戏内由
     * NewRegistryEvent/RegisterEvent 完成).
     * <p>合成成功路径会在 mock Level 上真实构造 {@code ItemEntity},Forge 补丁后的
     * {@code Entity} 构造器会求值 {@code FluidType.SIZE} 并读取 {@code EMPTY_TYPE},
     * 注册表或注册值缺失时 NPE.</p>
     */
    private static void ensureFluidTypeRegistry() {
        if (net.minecraftforge.registries.ForgeRegistries.FLUID_TYPES.get() == null) {
            try {
                ResourceLocation name = new ResourceLocation("forge", "fluid_type");
                var builder = new net.minecraftforge.registries.RegistryBuilder<net.minecraftforge.fluids.FluidType>()
                        .setName(name);
                java.lang.reflect.Method create = net.minecraftforge.registries.RegistryManager.class
                        .getDeclaredMethod("createRegistry", ResourceLocation.class,
                                net.minecraftforge.registries.RegistryBuilder.class);
                create.setAccessible(true);
                create.invoke(net.minecraftforge.registries.RegistryManager.ACTIVE, name, builder);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("无法在测试环境中创建 Forge fluid_type 注册表", e);
            }
        }
        if (!net.minecraftforge.common.ForgeMod.EMPTY_TYPE.isPresent()) {
            RegistryObjectTestInjector.inject(net.minecraftforge.common.ForgeMod.EMPTY_TYPE,
                    new net.minecraftforge.fluids.FluidType(net.minecraftforge.fluids.FluidType.Properties.create()));
        }
    }

    private static BlackHoleRecipe recipe(Map<String, Integer> inputs, ItemStack output) {
        return new BlackHoleRecipe(new ResourceLocation("ae2enhanced", "test"), inputs, output);
    }

    private static ItemEntity itemEntityOf(ItemStack stack) {
        ItemEntity entity = mock(ItemEntity.class);
        when(entity.getItem()).thenReturn(stack);
        return entity;
    }

    private static Level levelWith(List<BlackHoleRecipe> recipes, List<ItemEntity> items) {
        Level level = mock(Level.class);
        RecipeManager recipeManager = mock(RecipeManager.class);
        when(level.getRecipeManager()).thenReturn(recipeManager);
        doReturn(recipes).when(recipeManager).getAllRecipesFor(any());
        doReturn(items).when(level).getEntitiesOfClass(eq(ItemEntity.class), any(AABB.class));
        fixRandomField(level);
        return level;
    }

    /**
     * 为 mock Level 填充 {@code random} 字段.
     * <p>合成成功路径会在 mock Level 上真实构造 {@code ItemEntity},
     * 其构造器直接读取 {@code level.random} 字段,mock 未初始化时为 null 会 NPE.</p>
     */
    private static void fixRandomField(Level level) {
        try {
            java.lang.reflect.Field field = Level.class.getDeclaredField("random");
            field.setAccessible(true);
            field.set(level, net.minecraft.util.RandomSource.create());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("无法为 mock Level 填充 random 字段", e);
        }
    }

    // ==================== tryCraft ====================

    /** 区域内没有物品实体时不合成. */
    @Test
    void testTryCraftNoItems() {
        Level level = levelWith(List.of(), List.of());

        assertFalse(BlackHoleCraftingHelper.tryCraft(level, CENTER, OUTPUT, true));
        verify(level, never()).addFreshEntity(any());
    }

    /** 匹配配方:按需消耗多个物品实体,空堆实体被丢弃,产物从输出位置生成. */
    @Test
    void testTryCraftMatchConsumesAndSpawns() {
        BlackHoleRecipe recipe = recipe(Map.of("minecraft:stone", 3), new ItemStack(Items.DIAMOND));
        ItemStack stack1 = new ItemStack(Items.STONE, 2);
        ItemStack stack2 = new ItemStack(Items.STONE, 2);
        ItemEntity e1 = itemEntityOf(stack1);
        ItemEntity e2 = itemEntityOf(stack2);
        Level level = levelWith(List.of(recipe), List.of(e1, e2));

        assertTrue(BlackHoleCraftingHelper.tryCraft(level, CENTER, OUTPUT, false));

        // 共需 3:第一个实体 2 个全消耗并丢弃,第二个实体剩 1
        assertTrue(stack1.isEmpty());
        verify(e1).discard();
        assertEquals(1, stack2.getCount());
        verify(e2, never()).discard();
        // 产物实体从输出位置中心生成
        ArgumentCaptor<ItemEntity> captor = ArgumentCaptor.forClass(ItemEntity.class);
        verify(level).addFreshEntity(captor.capture());
        ItemEntity spawned = captor.getValue();
        assertEquals(Items.DIAMOND, spawned.getItem().getItem());
        assertEquals(1, spawned.getItem().getCount());
        assertEquals(Vec3.atCenterOf(OUTPUT), spawned.position());
    }

    /** 不匹配且 destroyOnMismatch=true:销毁区域内所有物品. */
    @Test
    void testTryCraftMismatchDestroysItems() {
        BlackHoleRecipe recipe = recipe(Map.of("minecraft:stone", 3), new ItemStack(Items.DIAMOND));
        ItemEntity e1 = itemEntityOf(new ItemStack(Items.DIRT, 5));
        ItemEntity e2 = itemEntityOf(new ItemStack(Items.STONE, 1));
        Level level = levelWith(List.of(recipe), List.of(e1, e2));

        assertFalse(BlackHoleCraftingHelper.tryCraft(level, CENTER, OUTPUT, true));

        verify(e1).discard();
        verify(e2).discard();
        verify(level, never()).addFreshEntity(any());
    }

    /** 不匹配且 destroyOnMismatch=false:保留所有物品(微型奇点玩家触发路径). */
    @Test
    void testTryCraftMismatchKeepsItems() {
        BlackHoleRecipe recipe = recipe(Map.of("minecraft:stone", 3), new ItemStack(Items.DIAMOND));
        ItemEntity e1 = itemEntityOf(new ItemStack(Items.DIRT, 5));
        Level level = levelWith(List.of(recipe), List.of(e1));

        assertFalse(BlackHoleCraftingHelper.tryCraft(level, CENTER, OUTPUT, false));

        verify(e1, never()).discard();
        verify(level, never()).addFreshEntity(any());
    }

    // ==================== craftAllAvailable ====================

    /** 批量合成:按最大批次数一次消耗并产出,循环至无配方可匹配. */
    @Test
    void testCraftAllAvailableMaxBatches() {
        BlackHoleRecipe recipe = recipe(Map.of("minecraft:stone", 3), new ItemStack(Items.DIAMOND));
        ItemStack stack = new ItemStack(Items.STONE, 7);
        ItemEntity entity = itemEntityOf(stack);
        Level level = levelWith(List.of(recipe), List.of(entity));

        int batches = BlackHoleCraftingHelper.craftAllAvailable(level, CENTER, OUTPUT);

        // 7 / 3 = 2 批,消耗 6,剩 1
        assertEquals(2, batches);
        assertEquals(1, stack.getCount());
        // 产出 2 个钻石,合并为单个实体
        ArgumentCaptor<ItemEntity> captor = ArgumentCaptor.forClass(ItemEntity.class);
        verify(level).addFreshEntity(captor.capture());
        assertEquals(2, captor.getValue().getItem().getCount());
        assertEquals(Items.DIAMOND, captor.getValue().getItem().getItem());
    }

    /** 产出按最大堆叠拆分为多个物品实体. */
    @Test
    void testCraftAllAvailableSplitsOutputByMaxStack() {
        BlackHoleRecipe recipe = recipe(Map.of("minecraft:stone", 1), new ItemStack(Items.DIAMOND, 60));
        ItemStack stack = new ItemStack(Items.STONE, 2);
        ItemEntity entity = itemEntityOf(stack);
        Level level = levelWith(List.of(recipe), List.of(entity));

        int batches = BlackHoleCraftingHelper.craftAllAvailable(level, CENTER, OUTPUT);

        assertEquals(2, batches);
        // 120 个产出拆为 64 + 56 两个实体
        ArgumentCaptor<ItemEntity> captor = ArgumentCaptor.forClass(ItemEntity.class);
        verify(level, times(2)).addFreshEntity(captor.capture());
        List<ItemEntity> spawned = captor.getAllValues();
        assertEquals(64, spawned.get(0).getItem().getCount());
        assertEquals(56, spawned.get(1).getItem().getCount());
    }

    /** 区域内无物品或无匹配配方时返回 0. */
    @Test
    void testCraftAllAvailableNoWork() {
        Level empty = levelWith(List.of(), List.of());
        assertEquals(0, BlackHoleCraftingHelper.craftAllAvailable(empty, CENTER, OUTPUT));

        BlackHoleRecipe recipe = recipe(Map.of("minecraft:stone", 3), new ItemStack(Items.DIAMOND));
        Level mismatch = levelWith(List.of(recipe), List.of(itemEntityOf(new ItemStack(Items.DIRT, 9))));
        assertEquals(0, BlackHoleCraftingHelper.craftAllAvailable(mismatch, CENTER, OUTPUT));
    }

    // ==================== 吸入 ====================

    /** isCraftingInput:按注册名匹配,空堆不参与. */
    @Test
    void testIsCraftingInput() {
        BlackHoleRecipe recipe = recipe(Map.of("minecraft:stone", 3), new ItemStack(Items.DIAMOND));
        Level level = levelWith(List.of(recipe), List.of());

        assertTrue(BlackHoleCraftingHelper.isCraftingInput(level, new ItemStack(Items.STONE)));
        assertFalse(BlackHoleCraftingHelper.isCraftingInput(level, new ItemStack(Items.DIRT)));
        assertFalse(BlackHoleCraftingHelper.isCraftingInput(level, ItemStack.EMPTY));
    }

    /** isCraftingInput:配方键带 NBT 后缀时按注册名前缀匹配. */
    @Test
    void testIsCraftingInputNbtKeyPrefix() {
        BlackHoleRecipe recipe = recipe(Map.of("minecraft:stone#{custom:1b}", 1), new ItemStack(Items.DIAMOND));
        Level level = levelWith(List.of(recipe), List.of());

        assertTrue(BlackHoleCraftingHelper.isCraftingInput(level, new ItemStack(Items.STONE)));
        assertFalse(BlackHoleCraftingHelper.isCraftingInput(level, new ItemStack(Items.DIRT)));
    }

    /** suckMatchingItems:只有可参与配方的物品被吸入,方向指向中心. */
    @Test
    void testSuckMatchingItemsOnlyPullsRecipeInputs() {
        BlackHoleRecipe recipe = recipe(Map.of("minecraft:stone", 3), new ItemStack(Items.DIAMOND));
        ItemEntity matching = itemEntityOf(new ItemStack(Items.STONE));
        when(matching.position()).thenReturn(new Vec3(2.5, 64.5, 0.5));
        ItemEntity other = itemEntityOf(new ItemStack(Items.DIRT));
        when(other.position()).thenReturn(new Vec3(2.5, 64.5, 0.5));
        Level level = levelWith(List.of(recipe), List.of(matching, other));

        BlackHoleCraftingHelper.suckMatchingItems(level, CENTER);

        // 中心 (0.5,64.5,0.5),偏移 (-2,0,0) 归一化后 × 0.25
        verify(matching).setDeltaMovement(argThat(v -> Math.abs(v.x + 0.25) < 1e-6
                && Math.abs(v.y) < 1e-6 && Math.abs(v.z) < 1e-6));
        verify(other, never()).setDeltaMovement(any(Vec3.class));
    }

    /** suckItems:区域内所有物品实体都被吸入. */
    @Test
    void testSuckItemsPullsEverything() {
        ItemEntity e1 = itemEntityOf(new ItemStack(Items.STONE));
        when(e1.position()).thenReturn(new Vec3(2.5, 64.5, 0.5));
        ItemEntity e2 = itemEntityOf(new ItemStack(Items.DIRT));
        when(e2.position()).thenReturn(new Vec3(-1.5, 64.5, 0.5));
        Level level = levelWith(List.of(), List.of(e1, e2));

        BlackHoleCraftingHelper.suckItems(level, CENTER);

        verify(e1).setDeltaMovement(any(Vec3.class));
        verify(e2).setDeltaMovement(any(Vec3.class));
    }

    // ==================== 爆炸特效 ====================

    /** 服务端爆炸:粒子 + 音效. */
    @Test
    void testExplodeOnServer() {
        ServerLevel level = mock(ServerLevel.class);

        BlackHoleCraftingHelper.explode(level, CENTER);

        verify(level).sendParticles(eq(ParticleTypes.EXPLOSION), anyDouble(), anyDouble(), anyDouble(),
                eq(4), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        verify(level).playSound(any(), eq(CENTER), any(), eq(SoundSource.BLOCKS), eq(2.0f), eq(0.5f));
    }

    /** 客户端不播放爆炸效果. */
    @Test
    void testExplodeClientSideIgnored() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);

        BlackHoleCraftingHelper.explode(level, CENTER);

        verify(level, never()).playSound(any(), any(BlockPos.class), any(), any(), anyFloat(), anyFloat());
    }
}
