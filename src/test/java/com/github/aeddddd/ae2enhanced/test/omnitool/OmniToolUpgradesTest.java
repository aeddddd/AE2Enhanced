package com.github.aeddddd.ae2enhanced.test.omnitool;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolNBT;
import com.github.aeddddd.ae2enhanced.omnitool.OmniToolUpgrades;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link OmniToolUpgrades} 升级/模式/状态 NBT 读写测试.
 */
class OmniToolUpgradesTest {

    @BeforeAll
    static void bootstrap() {
        OmniToolTestSupport.bootstrap();
    }

    private static ItemStack newToolStack() {
        return OmniToolTestSupport.newToolStack();
    }

    // ==================== 模式 ====================

    @Test
    void testDefaultModeIsUniversal() {
        assertThat(OmniToolUpgrades.getMode(newToolStack()))
                .isEqualTo(AdvancedMEOmniToolItem.MODE_UNIVERSAL);
    }

    @Test
    void testSetModeRoundtrip() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setMode(stack, AdvancedMEOmniToolItem.MODE_ROTATE);
        assertThat(OmniToolUpgrades.getMode(stack)).isEqualTo(AdvancedMEOmniToolItem.MODE_ROTATE);
    }

    @Test
    void testSetModeWrapsByModulo() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setMode(stack, AdvancedMEOmniToolItem.MODE_COUNT);
        assertThat(OmniToolUpgrades.getMode(stack)).isEqualTo(AdvancedMEOmniToolItem.MODE_UNIVERSAL);
        OmniToolUpgrades.setMode(stack, AdvancedMEOmniToolItem.MODE_COUNT + 3);
        assertThat(OmniToolUpgrades.getMode(stack)).isEqualTo(AdvancedMEOmniToolItem.MODE_TRAVEL);
    }

    @Test
    void testCycleModeWrapsAround() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.cycleMode(stack);
        assertThat(OmniToolUpgrades.getMode(stack)).isEqualTo(AdvancedMEOmniToolItem.MODE_PLACEMENT);
        OmniToolUpgrades.setMode(stack, AdvancedMEOmniToolItem.MODE_TRAVEL);
        OmniToolUpgrades.cycleMode(stack);
        assertThat(OmniToolUpgrades.getMode(stack)).isEqualTo(AdvancedMEOmniToolItem.MODE_UNIVERSAL);
    }

    // ==================== 掉落模式 ====================

    @Test
    void testDefaultDropModeIsNormal() {
        assertThat(OmniToolUpgrades.getDropMode(newToolStack()))
                .isEqualTo(AdvancedMEOmniToolItem.DROP_NORMAL);
    }

    @Test
    void testSetDropModeWrapsByModulo() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setDropMode(stack, AdvancedMEOmniToolItem.DROP_AE);
        assertThat(OmniToolUpgrades.getDropMode(stack)).isEqualTo(AdvancedMEOmniToolItem.DROP_AE);
        OmniToolUpgrades.setDropMode(stack, 4); // 4 % 3 = 1
        assertThat(OmniToolUpgrades.getDropMode(stack)).isEqualTo(AdvancedMEOmniToolItem.DROP_INVENTORY);
    }

    @Test
    void testCycleDropModeWrapsAround() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.cycleDropMode(stack);
        assertThat(OmniToolUpgrades.getDropMode(stack)).isEqualTo(AdvancedMEOmniToolItem.DROP_INVENTORY);
        OmniToolUpgrades.cycleDropMode(stack);
        assertThat(OmniToolUpgrades.getDropMode(stack)).isEqualTo(AdvancedMEOmniToolItem.DROP_AE);
        OmniToolUpgrades.cycleDropMode(stack);
        assertThat(OmniToolUpgrades.getDropMode(stack)).isEqualTo(AdvancedMEOmniToolItem.DROP_NORMAL);
    }

    // ==================== 精准采集 ====================

    @Test
    void testSilkTouchDefaultFalseAndToggle() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolUpgrades.isSilkTouchEnabled(stack)).isFalse();
        OmniToolUpgrades.toggleSilkTouch(stack);
        assertThat(OmniToolUpgrades.isSilkTouchEnabled(stack)).isTrue();
        OmniToolUpgrades.toggleSilkTouch(stack);
        assertThat(OmniToolUpgrades.isSilkTouchEnabled(stack)).isFalse();
    }

    @Test
    void testSilkTouchAddsVisibleEnchantment() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setSilkTouchEnabled(stack, true);
        // 开启精准采集后,可见附魔中应出现精准采集
        assertThat(EnchantmentHelper.getEnchantments(stack))
                .containsEntry(Enchantments.SILK_TOUCH, 1);
        OmniToolUpgrades.setSilkTouchEnabled(stack, false);
        assertThat(EnchantmentHelper.getEnchantments(stack))
                .doesNotContainKey(Enchantments.SILK_TOUCH);
    }

    @Test
    void testAdvancedSilkTouchDefaultFalseAndRoundtrip() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolUpgrades.isAdvancedSilkTouchEnabled(stack)).isFalse();
        OmniToolUpgrades.setAdvancedSilkTouchEnabled(stack, true);
        assertThat(OmniToolUpgrades.isAdvancedSilkTouchEnabled(stack)).isTrue();
    }

    // ==================== 基岩破坏者 / 混沌核心 / 共形不变荷 ====================

    @Test
    void testBedrockBreakerDefaultFalseAndRoundtrip() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolUpgrades.hasBedrockBreaker(stack)).isFalse();
        OmniToolUpgrades.setBedrockBreaker(stack, true);
        assertThat(OmniToolUpgrades.hasBedrockBreaker(stack)).isTrue();
    }

    @Test
    void testChaosCoreDefaultFalseAndRoundtrip() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolUpgrades.hasChaosCore(stack)).isFalse();
        OmniToolUpgrades.setChaosCore(stack, true);
        assertThat(OmniToolUpgrades.hasChaosCore(stack)).isTrue();
    }

    @Test
    void testConformalChargeDefaultFalseAndRoundtrip() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolUpgrades.hasConformalCharge(stack)).isFalse();
        OmniToolUpgrades.setConformalCharge(stack, true);
        assertThat(OmniToolUpgrades.hasConformalCharge(stack)).isTrue();
    }

    @Test
    void testChaosForceKillDefaultsToTrue() {
        // 无标签时默认开启
        assertThat(OmniToolUpgrades.isChaosForceKillEnabled(newToolStack())).isTrue();
        // 有标签但无该键时同样默认开启
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setChaosCore(stack, true);
        assertThat(OmniToolUpgrades.isChaosForceKillEnabled(stack)).isTrue();
    }

    @Test
    void testChaosForceKillRoundtrip() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setChaosForceKillEnabled(stack, false);
        assertThat(OmniToolUpgrades.isChaosForceKillEnabled(stack)).isFalse();
        OmniToolUpgrades.setChaosForceKillEnabled(stack, true);
        assertThat(OmniToolUpgrades.isChaosForceKillEnabled(stack)).isTrue();
    }

    // ==================== 穿墙 ====================

    @Test
    void testWallPhaseDefaultsToConfigValue() {
        // 配置默认 enableWallPhase = true
        assertThat(OmniToolUpgrades.isWallPhaseEnabled(newToolStack())).isTrue();
        // 有标签但无该键时仍回落到配置默认值
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setMode(stack, AdvancedMEOmniToolItem.MODE_TRAVEL);
        assertThat(stack.getTag().contains(OmniToolNBT.WALL_PHASE)).isFalse();
        assertThat(OmniToolUpgrades.isWallPhaseEnabled(stack)).isTrue();
    }

    @Test
    void testWallPhaseRoundtrip() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setWallPhaseEnabled(stack, false);
        assertThat(OmniToolUpgrades.isWallPhaseEnabled(stack)).isFalse();
        OmniToolUpgrades.setWallPhaseEnabled(stack, true);
        assertThat(OmniToolUpgrades.isWallPhaseEnabled(stack)).isTrue();
    }

    // ==================== 参数开关位掩码 ====================

    @Test
    void testParamEnabledDefaultsToTrue() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolUpgrades.isParamEnabled(stack, 0)).isTrue();
        assertThat(OmniToolUpgrades.isParamEnabled(stack, 15)).isTrue();
        assertThat(OmniToolUpgrades.isParamEnabled(stack, 31)).isTrue();
    }

    @Test
    void testParamEnabledInvalidIndexAlwaysTrue() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolUpgrades.isParamEnabled(stack, -1)).isTrue();
        assertThat(OmniToolUpgrades.isParamEnabled(stack, 32)).isTrue();
    }

    @Test
    void testSetParamEnabledInvalidIndexIsNoOp() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setParamEnabled(stack, -1, false);
        OmniToolUpgrades.setParamEnabled(stack, 32, false);
        // 非法下标不应创建任何标签
        assertThat(stack.hasTag()).isFalse();
    }

    @Test
    void testSetParamEnabledRoundtrip() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setParamEnabled(stack, 5, false);
        assertThat(OmniToolUpgrades.isParamEnabled(stack, 5)).isFalse();
        OmniToolUpgrades.setParamEnabled(stack, 5, true);
        assertThat(OmniToolUpgrades.isParamEnabled(stack, 5)).isTrue();
    }

    @Test
    void testDisableSingleParamKeepsOthersEnabled() {
        // 期望行为:从默认状态(全开)关闭参数 1 时,其余参数应保持开启.
        // 注:当前实现以 0 为掩码起点做位与,关闭任意一个参数会把掩码写成 0,
        // 导致 isParamEnabled 对其它参数也返回 false(疑似源码 bug,见测试汇报).
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setParamEnabled(stack, 1, false);
        assertThat(OmniToolUpgrades.isParamEnabled(stack, 1)).isFalse();
        assertThat(OmniToolUpgrades.isParamEnabled(stack, 0)).isTrue();
        assertThat(OmniToolUpgrades.isParamEnabled(stack, 2)).isTrue();
    }

    // ==================== 挖掘冷却 ====================

    @Test
    void testBreakCooldownDefaultsToConfigMax() {
        // 配置默认 maxBreakCooldown = 20
        assertThat(OmniToolUpgrades.getBreakCooldown(newToolStack())).isEqualTo(20);
    }

    @Test
    void testBreakCooldownRoundtripAndClamp() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setBreakCooldown(stack, 5);
        assertThat(OmniToolUpgrades.getBreakCooldown(stack)).isEqualTo(5);
        // 超过配置上限时被钳制到上限
        OmniToolUpgrades.setBreakCooldown(stack, 100);
        assertThat(OmniToolUpgrades.getBreakCooldown(stack)).isEqualTo(20);
    }

    @Test
    void testLastBreakTickDefaultZeroAndRoundtrip() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolUpgrades.getLastBreakTick(stack)).isZero();
        OmniToolUpgrades.setLastBreakTick(stack, 123456789L);
        assertThat(OmniToolUpgrades.getLastBreakTick(stack)).isEqualTo(123456789L);
    }

    // ==================== 闪现距离 / 冷却 ====================

    @Test
    void testBlinkDistanceDefaultsToConfigMax() {
        // 配置默认 maxBlinkDistance = 256
        assertThat(OmniToolUpgrades.getBlinkDistance(newToolStack())).isEqualTo(256.0);
    }

    @Test
    void testBlinkDistanceWritesDefaultIntoExistingTag() {
        ItemStack stack = newToolStack();
        // 注意:ItemStack#hasTag() 对空标签返回 false,因此先写入一个无关键使标签非空
        stack.getOrCreateTag().putBoolean("dummy", true);
        double dist = OmniToolUpgrades.getBlinkDistance(stack);
        assertThat(dist).isEqualTo(256.0);
        // 读取时应把默认值写回标签
        assertThat(stack.getTag().contains(OmniToolNBT.BLINK_DIST)).isTrue();
    }

    @Test
    void testBlinkDistanceRoundtripAndClamp() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setBlinkDistance(stack, 10.5);
        assertThat(OmniToolUpgrades.getBlinkDistance(stack)).isEqualTo(10.5);
        // 超过配置上限时被钳制到上限
        OmniToolUpgrades.setBlinkDistance(stack, 99999.0);
        assertThat(OmniToolUpgrades.getBlinkDistance(stack)).isEqualTo(256.0);
    }

    @Test
    void testLastBlinkTickDefaultZeroAndRoundtrip() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolUpgrades.getLastBlinkTick(stack)).isZero();
        OmniToolUpgrades.setLastBlinkTick(stack, 987654321L);
        assertThat(OmniToolUpgrades.getLastBlinkTick(stack)).isEqualTo(987654321L);
    }

    // ==================== 时运 ====================

    @Test
    void testFortuneDefaultZero() {
        ItemStack stack = newToolStack();
        assertThat(OmniToolUpgrades.getFortuneLevel(stack)).isZero();
        assertThat(OmniToolUpgrades.hasFortuneUpgrade(stack)).isFalse();
    }

    @Test
    void testFortuneRoundtrip() {
        ItemStack stack = newToolStack();
        OmniToolUpgrades.setFortuneLevel(stack, 3);
        assertThat(OmniToolUpgrades.getFortuneLevel(stack)).isEqualTo(3);
        assertThat(OmniToolUpgrades.hasFortuneUpgrade(stack)).isTrue();
        // 存储时运同步到可见附魔
        assertThat(EnchantmentHelper.getEnchantments(stack))
                .containsEntry(Enchantments.BLOCK_FORTUNE, 3);
        OmniToolUpgrades.setFortuneLevel(stack, 0);
        assertThat(OmniToolUpgrades.getFortuneLevel(stack)).isZero();
        assertThat(OmniToolUpgrades.hasFortuneUpgrade(stack)).isFalse();
    }

    // ==================== 禁疗(委托 CombatModule) ====================

    @Test
    void testAntiHealDelegation() {
        LivingEntity entity = mock(LivingEntity.class);
        CompoundTag data = new CompoundTag();
        when(entity.getPersistentData()).thenReturn(data);

        assertThat(OmniToolUpgrades.hasAntiHeal(entity)).isFalse();
        OmniToolUpgrades.applyAntiHeal(entity);
        assertThat(OmniToolUpgrades.hasAntiHeal(entity)).isTrue();
        OmniToolUpgrades.clearAntiHeal(entity);
        assertThat(OmniToolUpgrades.hasAntiHeal(entity)).isFalse();
    }
}
