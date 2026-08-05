package com.github.aeddddd.ae2enhanced.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.LogicalSide;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolNBT;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.omnitool.module.CombatModule;

/**
 * {@link OmniToolEventHandler} 单元测试.
 * <p>不可覆盖部分:放置模式 Ctrl+右键撤销依赖客户端 {@code Screen.hasControlDown()}(GLFW),
 * 以及 {@code MiningModule.forceBreakBlock} 的 ServerLevel 掉落表路径,均不在单测环境验证.</p>
 */
class OmniToolEventHandlerTest {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @BeforeAll
    static void setup() {
        EventTestFixtures.init();
    }

    @AfterEach
    void cleanupTracked() {
        // 禁疗追踪集合为全局静态状态,避免跨用例/跨测试类污染
        CombatModule.getAntiHealTracked().clear();
    }

    // ==================== 左键基岩破坏 ====================

    private static Player playerWith(Level level, ItemStack mainHand) {
        Player player = mock(Player.class);
        when(player.level()).thenReturn(level);
        when(player.getMainHandItem()).thenReturn(mainHand);
        return player;
    }

    private static PlayerInteractEvent.LeftClickBlock leftClick(Player player) {
        return new PlayerInteractEvent.LeftClickBlock(player, POS, Direction.UP,
                PlayerInteractEvent.LeftClickBlock.Action.START);
    }

    /** 客户端不处理. */
    @Test
    void testLeftClickClientSideIgnored() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        var event = leftClick(playerWith(level, EventTestFixtures.newToolStack()));

        OmniToolEventHandler.onLeftClickBlock(event);

        assertFalse(event.isCanceled());
    }

    /** 主手不是全能工具不处理. */
    @Test
    void testLeftClickNonToolIgnored() {
        Level level = mock(Level.class);
        var event = leftClick(playerWith(level, new ItemStack(Items.STONE)));

        OmniToolEventHandler.onLeftClickBlock(event);

        assertFalse(event.isCanceled());
    }

    /** 非通用/旅行模式(放置模式)不处理. */
    @Test
    void testLeftClickPlacementModeIgnored() {
        Level level = mock(Level.class);
        ItemStack tool = EventTestFixtures.newToolStack();
        OmniToolUpgrades.setMode(tool, AdvancedMEOmniToolItem.MODE_PLACEMENT);
        var event = leftClick(playerWith(level, tool));

        OmniToolEventHandler.onLeftClickBlock(event);

        assertFalse(event.isCanceled());
    }

    /** 可破坏方块(硬度 >= 0)不接管. */
    @Test
    void testLeftClickBreakableBlockIgnored() {
        Level level = mock(Level.class);
        when(level.getBlockState(POS)).thenReturn(Blocks.STONE.defaultBlockState());
        var event = leftClick(playerWith(level, EventTestFixtures.newToolStack()));

        OmniToolEventHandler.onLeftClickBlock(event);

        assertFalse(event.isCanceled());
    }

    /** 通用模式下左键不可破坏方块(基岩):取消事件并强制破坏. */
    @Test
    void testLeftClickBedrockForceBreaks() {
        Level level = mock(Level.class);
        when(level.getBlockState(POS)).thenReturn(Blocks.BEDROCK.defaultBlockState());
        ItemStack tool = EventTestFixtures.newToolStack();
        // 默认挖掘冷却 20 tick 会在 mock 世界(gameTime=0)拦截破坏,置 0 以走到破坏分支
        OmniToolUpgrades.setBreakCooldown(tool, 0);
        Player player = playerWith(level, tool);
        var event = leftClick(player);

        OmniToolEventHandler.onLeftClickBlock(event);

        assertTrue(event.isCanceled());
        // 非 ServerLevel 走普通破坏路径:非创造玩家带掉落破坏
        verify(level).destroyBlock(POS, true, player);
        verify(level).levelEvent(eq(2001), eq(POS), anyInt());
    }

    /** 旅行模式下左键不可破坏方块同样接管. */
    @Test
    void testLeftClickBedrockTravelModeForceBreaks() {
        Level level = mock(Level.class);
        when(level.getBlockState(POS)).thenReturn(Blocks.BEDROCK.defaultBlockState());
        ItemStack tool = EventTestFixtures.newToolStack();
        OmniToolUpgrades.setMode(tool, AdvancedMEOmniToolItem.MODE_TRAVEL);
        OmniToolUpgrades.setBreakCooldown(tool, 0);
        Player player = playerWith(level, tool);
        var event = leftClick(player);

        OmniToolEventHandler.onLeftClickBlock(event);

        assertTrue(event.isCanceled());
        verify(level).destroyBlock(POS, true, player);
    }

    // ==================== 禁疗 ====================

    private static LivingEntity livingWithAntiHeal(boolean antiHeal) {
        LivingEntity entity = mock(LivingEntity.class);
        CompoundTag data = new CompoundTag();
        if (antiHeal) {
            data.putBoolean(OmniToolNBT.ANTI_HEAL, true);
        }
        when(entity.getPersistentData()).thenReturn(data);
        return entity;
    }

    /** 带禁疗标记的实体治疗事件被取消. */
    @Test
    void testHealCanceledForAntiHealEntity() {
        var event = new LivingHealEvent(livingWithAntiHeal(true), 4.0f);

        OmniToolEventHandler.onLivingHeal(event);

        assertTrue(event.isCanceled());
    }

    /** 无禁疗标记的实体治疗不受影响. */
    @Test
    void testHealAllowedWithoutAntiHeal() {
        var event = new LivingHealEvent(livingWithAntiHeal(false), 4.0f);

        OmniToolEventHandler.onLivingHeal(event);

        assertFalse(event.isCanceled());
    }

    /** 带持久禁疗标记的实体进入世界时重新登记到追踪集合. */
    @Test
    void testEntityJoinLevelReTracksAntiHeal() {
        Level level = mock(Level.class);
        LivingEntity entity = livingWithAntiHeal(true);

        OmniToolEventHandler.onEntityJoinLevel(new EntityJoinLevelEvent(entity, level));

        assertTrue(CombatModule.getAntiHealTracked().contains(entity));
    }

    /** 无禁疗标记或客户端进入世界时不登记. */
    @Test
    void testEntityJoinLevelSkipsUnmarkedAndClientSide() {
        LivingEntity unmarked = livingWithAntiHeal(false);
        Level serverLevel = mock(Level.class);
        OmniToolEventHandler.onEntityJoinLevel(new EntityJoinLevelEvent(unmarked, serverLevel));
        assertFalse(CombatModule.getAntiHealTracked().contains(unmarked));

        LivingEntity marked = livingWithAntiHeal(true);
        Level clientLevel = mock(Level.class);
        when(clientLevel.isClientSide()).thenReturn(true);
        OmniToolEventHandler.onEntityJoinLevel(new EntityJoinLevelEvent(marked, clientLevel));
        assertFalse(CombatModule.getAntiHealTracked().contains(marked));
    }

    /** tick 兜底:已移除实体从追踪集合清理. */
    @Test
    void testLevelTickUntracksRemovedEntity() {
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(level);
        LivingEntity removed = livingWithAntiHeal(true);
        when(removed.isRemoved()).thenReturn(true);
        CombatModule.trackAntiHeal(removed);

        OmniToolEventHandler.onLevelTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,
                TickEvent.Phase.END, level, () -> true));

        assertFalse(CombatModule.getAntiHealTracked().contains(removed));
        verify(removed, never()).remove(any());
    }

    /** tick 兜底:未移除实体被强制标记移除. */
    @Test
    void testLevelTickForceRemovesSurvivingEntity() {
        ServerLevel level = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(level.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(level);
        LivingEntity living = livingWithAntiHeal(true);
        when(living.isRemoved()).thenReturn(false);
        CombatModule.trackAntiHeal(living);

        OmniToolEventHandler.onLevelTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,
                TickEvent.Phase.END, level, () -> true));

        verify(living).remove(Entity.RemovalReason.KILLED);
        // remove 后仍未移除时回退 public final 的 setRemoved
        verify(living).setRemoved(Entity.RemovalReason.KILLED);
    }

    /** 非 END 阶段 / 非主世界 / 非 ServerLevel 均不处理. */
    @Test
    void testLevelTickGuardBranches() {
        ServerLevel overworld = mock(ServerLevel.class);
        MinecraftServer server = mock(MinecraftServer.class);
        when(overworld.getServer()).thenReturn(server);
        when(server.overworld()).thenReturn(overworld);
        ServerLevel otherDim = mock(ServerLevel.class);
        when(otherDim.getServer()).thenReturn(server);
        LivingEntity living = livingWithAntiHeal(true);
        CombatModule.trackAntiHeal(living);

        // START 阶段
        OmniToolEventHandler.onLevelTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,
                TickEvent.Phase.START, overworld, () -> true));
        // 非主世界维度
        OmniToolEventHandler.onLevelTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,
                TickEvent.Phase.END, otherDim, () -> true));
        // 非 ServerLevel
        OmniToolEventHandler.onLevelTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER,
                TickEvent.Phase.END, mock(Level.class), () -> true));

        verify(living, never()).remove(any());
        assertTrue(CombatModule.getAntiHealTracked().contains(living));
    }

    // ==================== 共形不变荷:掉落物不消失 ====================

    private static ItemEntity itemEntityOf(ItemStack stack) {
        ItemEntity entity = mock(ItemEntity.class);
        when(entity.getItem()).thenReturn(stack);
        return entity;
    }

    private static ItemStack conformalToolStack() {
        ItemStack stack = EventTestFixtures.newToolStack();
        OmniToolUpgrades.setConformalCharge(stack, true);
        return stack;
    }

    /** 带共形不变荷的工具掉落物永不消失. */
    @Test
    void testItemExpireCanceledForConformalTool() {
        var event = new ItemExpireEvent(itemEntityOf(conformalToolStack()), 6000);

        OmniToolEventHandler.onItemExpire(event);

        assertTrue(event.isCanceled());
    }

    /** 无共形不变荷的工具与普通物品正常消失. */
    @Test
    void testItemExpireAllowedOtherwise() {
        var plainTool = new ItemExpireEvent(itemEntityOf(EventTestFixtures.newToolStack()), 6000);
        OmniToolEventHandler.onItemExpire(plainTool);
        assertFalse(plainTool.isCanceled());

        var stone = new ItemExpireEvent(itemEntityOf(new ItemStack(Items.STONE)), 6000);
        OmniToolEventHandler.onItemExpire(stone);
        assertFalse(stone.isCanceled());
    }

    // ==================== 共形不变荷:死亡保留 / 重生返还 ====================

    private static Player deadPlayer(List<ItemEntity> drops) {
        Player player = mock(Player.class);
        Level level = mock(Level.class);
        when(player.level()).thenReturn(level);
        when(player.getPersistentData()).thenReturn(new CompoundTag());
        return player;
    }

    /** 死亡掉落中的共形工具被拦截并写入 persistentData,其余掉落保留. */
    @Test
    void testLivingDropsPreservesConformalTool() {
        ItemStack tool = conformalToolStack();
        ItemEntity toolDrop = itemEntityOf(tool);
        ItemEntity stoneDrop = itemEntityOf(new ItemStack(Items.STONE, 3));
        List<ItemEntity> drops = new ArrayList<>(List.of(toolDrop, stoneDrop));
        Player player = deadPlayer(drops);
        var event = new LivingDropsEvent(player, null, drops, 0, false);

        OmniToolEventHandler.onLivingDrops(event);

        assertEquals(List.of(stoneDrop), drops);
        CompoundTag data = player.getPersistentData();
        assertTrue(data.contains(OmniToolNBT.CONFORMAL_PRESERVED, Tag.TAG_LIST));
        assertEquals(1, data.getList(OmniToolNBT.CONFORMAL_PRESERVED, Tag.TAG_COMPOUND).size());
    }

    /** 掉落中没有共形工具时不写 persistentData,掉落列表不变. */
    @Test
    void testLivingDropsWithoutConformalToolKeepsEverything() {
        ItemEntity stoneDrop = itemEntityOf(new ItemStack(Items.STONE, 3));
        ItemEntity plainToolDrop = itemEntityOf(EventTestFixtures.newToolStack());
        List<ItemEntity> drops = new ArrayList<>(List.of(stoneDrop, plainToolDrop));
        Player player = deadPlayer(drops);
        var event = new LivingDropsEvent(player, null, drops, 0, false);

        OmniToolEventHandler.onLivingDrops(event);

        assertEquals(2, drops.size());
        assertFalse(player.getPersistentData().contains(OmniToolNBT.CONFORMAL_PRESERVED));
    }

    /** 空掉落列表直接返回(非玩家实体同样直接返回). */
    @Test
    void testLivingDropsEmptyListAndNonPlayer() {
        Player player = deadPlayer(List.of());
        OmniToolEventHandler.onLivingDrops(new LivingDropsEvent(player, null, List.of(), 0, false));
        assertFalse(player.getPersistentData().contains(OmniToolNBT.CONFORMAL_PRESERVED));

        LivingEntity mob = mock(LivingEntity.class);
        List<ItemEntity> drops = new ArrayList<>(List.of(itemEntityOf(conformalToolStack())));
        OmniToolEventHandler.onLivingDrops(new LivingDropsEvent(mob, null, drops, 0, false));
        // 非玩家不拦截,掉落列表不变
        assertEquals(1, drops.size());
    }

    /** 重生时保留的工具返还背包并清除记录. */
    @Test
    void testRespawnReturnsPreservedToolToInventory() {
        Player player = mock(Player.class);
        CompoundTag data = new CompoundTag();
        ListTag preserved = new ListTag();
        preserved.add(conformalToolStack().save(new CompoundTag()));
        data.put(OmniToolNBT.CONFORMAL_PRESERVED, preserved);
        when(player.getPersistentData()).thenReturn(data);
        Inventory inventory = mock(Inventory.class);
        when(inventory.add(any(ItemStack.class))).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);

        OmniToolEventHandler.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));

        verify(inventory).add(argThat(stack -> stack.getItem() == EventTestFixtures.toolItem()));
        assertFalse(data.contains(OmniToolNBT.CONFORMAL_PRESERVED));
        verify(player, never()).drop(any(ItemStack.class), eq(false));
    }

    /** 背包满时保留的工具改为掉落返还. */
    @Test
    void testRespawnDropsPreservedToolWhenInventoryFull() {
        Player player = mock(Player.class);
        CompoundTag data = new CompoundTag();
        ListTag preserved = new ListTag();
        preserved.add(conformalToolStack().save(new CompoundTag()));
        data.put(OmniToolNBT.CONFORMAL_PRESERVED, preserved);
        when(player.getPersistentData()).thenReturn(data);
        Inventory inventory = mock(Inventory.class);
        when(inventory.add(any(ItemStack.class))).thenReturn(false);
        when(player.getInventory()).thenReturn(inventory);

        OmniToolEventHandler.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));

        verify(player).drop(argThat(stack -> stack.getItem() == EventTestFixtures.toolItem()), eq(false));
        assertFalse(data.contains(OmniToolNBT.CONFORMAL_PRESERVED));
    }

    /** 无保留记录时重生不做任何返还. */
    @Test
    void testRespawnWithoutPreservedDataIsNoOp() {
        Player player = mock(Player.class);
        when(player.getPersistentData()).thenReturn(new CompoundTag());

        OmniToolEventHandler.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(player, false));

        verify(player, never()).getInventory();
    }
}
