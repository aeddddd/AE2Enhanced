package com.github.aeddddd.ae2enhanced.test.omnitool;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.module.MiningModule;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link MiningModule} 可在纯单测环境覆盖的部分:挖掘速度/采集判定/黑名单/冷却委托/掉落分发.
 */
class MiningModuleTest {

    private static final float DESTROY_SPEED = 1_000_000.0f;

    private final MiningModule module = new MiningModule();

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
        ConfigTestBootstrap.loadDefaults();
    }

    @org.junit.jupiter.api.BeforeEach
    @AfterEach
    void resetBlacklist() throws Exception {
        // 恢复黑名单配置并清空模块缓存(5 秒 TTL,可能被其它测试类污染),避免相互影响
        AE2EnhancedConfig.COMMON.omniToolBreakableBlacklist.set(List.of());
        clearBlacklistCache();
    }

    private static ItemStack newToolStack() {
        return OmniToolTestSupport.newToolStack();
    }

    private static void clearBlacklistCache() throws Exception {
        Field cache = MiningModule.class.getDeclaredField("blacklistCache");
        cache.setAccessible(true);
        cache.set(null, null);
    }

    // ==================== 挖掘速度 / 采集判定 ====================

    @Test
    void testDestroySpeedInUniversalMode() {
        ItemStack stack = newToolStack();
        assertThat(module.getDestroySpeed(stack, Blocks.STONE.defaultBlockState()))
                .isEqualTo(DESTROY_SPEED);
    }

    @Test
    void testDestroySpeedInTravelMode() {
        ItemStack stack = newToolStack();
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_TRAVEL);
        assertThat(module.getDestroySpeed(stack, Blocks.STONE.defaultBlockState()))
                .isEqualTo(DESTROY_SPEED);
    }

    @Test
    void testDestroySpeedInNonMiningModes() {
        ItemStack stack = newToolStack();
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_PLACEMENT);
        assertThat(module.getDestroySpeed(stack, Blocks.STONE.defaultBlockState())).isEqualTo(1.0f);
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_ROTATE);
        assertThat(module.getDestroySpeed(stack, Blocks.STONE.defaultBlockState())).isEqualTo(1.0f);
    }

    @Test
    void testCanHarvestBlock() {
        ItemStack stack = newToolStack();
        assertThat(module.canHarvestBlock(Blocks.STONE.defaultBlockState(), stack)).isTrue();
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_ROTATE);
        assertThat(module.canHarvestBlock(Blocks.STONE.defaultBlockState(), stack)).isFalse();
    }

    // ==================== 黑名单 ====================

    @Test
    void testBlacklistBlocksMining() {
        AE2EnhancedConfig.COMMON.omniToolBreakableBlacklist.set(List.of("minecraft:bedrock"));

        assertThat(MiningModule.isBlacklisted(Blocks.BEDROCK)).isTrue();
        assertThat(MiningModule.isBlacklisted(Blocks.STONE)).isFalse();

        ItemStack stack = newToolStack();
        // 黑名单方块:速度为 0 且不可采集
        assertThat(module.getDestroySpeed(stack, Blocks.BEDROCK.defaultBlockState())).isEqualTo(0.0f);
        assertThat(module.canHarvestBlock(Blocks.BEDROCK.defaultBlockState(), stack)).isFalse();
    }

    @Test
    void testInvalidBlacklistEntryIsIgnored() {
        // 非法注册名只记录警告,不影响其余条目
        AE2EnhancedConfig.COMMON.omniToolBreakableBlacklist.set(List.of("", "not a location", "minecraft:bedrock"));
        assertThat(MiningModule.isBlacklisted(Blocks.BEDROCK)).isTrue();
        assertThat(MiningModule.isBlacklisted(Blocks.STONE)).isFalse();
    }

    // ==================== onBlockStartBreak 模式门控 ====================

    @Test
    void testBlockStartBreakIgnoredInNonMiningModes() {
        // 非挖掘模式直接返回 false,不与世界交互
        ItemStack stack = newToolStack();
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_PLACEMENT);
        Player player = mock(Player.class);
        assertThat(module.onBlockStartBreak(stack, BlockPos.ZERO, player)).isFalse();
        verifyNoInteractions(player);
    }

    // ==================== 冷却委托 ====================

    @Test
    void testBreakCooldownDelegation() {
        ItemStack stack = newToolStack();
        assertThat(MiningModule.getBreakCooldown(stack)).isEqualTo(20); // 配置默认值
        MiningModule.setBreakCooldown(stack, 7);
        assertThat(MiningModule.getBreakCooldown(stack)).isEqualTo(7);
    }

    // ==================== Tooltip ====================

    @Test
    void testAddTooltip() {
        ItemStack stack = newToolStack();
        List<Component> tooltip = new ArrayList<>();
        module.addTooltip(stack, null, tooltip, TooltipFlag.NORMAL);

        assertThat(tooltip).hasSize(2);
        assertThat(tooltip.stream().map(MiningModuleTest::keyOf))
                .containsExactly(
                        "item.ae2enhanced.me_omni_tool.break_cooldown",
                        "item.ae2enhanced.me_omni_tool.drop_mode");
    }

    private static String keyOf(Component component) {
        return component.getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }

    // ==================== 掉落分发 ====================

    @Test
    void testHandleDropsDoesNothingOnClient() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        Player player = mock(Player.class);
        ItemStack tool = newToolStack();

        MiningModule.handleDrops(level, player, BlockPos.ZERO,
                List.of(new ItemStack(Items.DIAMOND)), tool);
        verify(level, never()).addFreshEntity(any());
        verifyNoInteractions(player);
    }

    @Test
    void testHandleDropsDoesNothingWithEmptyDrops() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        Player player = mock(Player.class);
        ItemStack tool = newToolStack();

        MiningModule.handleDrops(level, player, BlockPos.ZERO, List.of(), tool);
        verify(level, never()).addFreshEntity(any());
        verifyNoInteractions(player);
    }

    @Test
    void testHandleDropsToInventory() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        Player player = mock(Player.class);
        Inventory inventory = mock(Inventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.add(any(ItemStack.class))).thenReturn(true);

        ItemStack tool = newToolStack();
        AdvancedMEOmniToolItem.setDropMode(tool, AdvancedMEOmniToolItem.DROP_INVENTORY);
        ItemStack drop = new ItemStack(Items.DIAMOND, 3);

        MiningModule.handleDrops(level, player, BlockPos.ZERO, List.of(drop), tool);

        // 成功放入背包,不生成掉落物实体
        verify(inventory).add(drop);
        verify(level, never()).addFreshEntity(any());
    }
}
