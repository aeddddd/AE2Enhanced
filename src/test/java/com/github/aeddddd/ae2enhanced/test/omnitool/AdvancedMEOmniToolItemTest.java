package com.github.aeddddd.ae2enhanced.test.omnitool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.network.OmniToolNetworkLink;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AdvancedMEOmniToolItem} 可在纯单测环境覆盖的逻辑:
 * 常量/名称键/潜行旁路/模式分发/静态委托/tooltip.
 * 需要真实世界或服务端网络的交互(WAP 绑定、放置、传送)不在单测范围.
 */
class AdvancedMEOmniToolItemTest {

    private AdvancedMEOmniToolItem item;
    private ItemStack stack;

    @BeforeAll
    static void bootstrap() {
        OmniToolTestSupport.bootstrap();
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        item = OmniToolTestSupport.newToolItem();
        stack = new ItemStack(item);
    }

    // ==================== 常量与名称键 ====================

    @Test
    void testModeAndDropModeConstants() {
        assertThat(AdvancedMEOmniToolItem.MODE_COUNT).isEqualTo(4);
        assertThat(AdvancedMEOmniToolItem.MODE_UNIVERSAL).isZero();
        assertThat(AdvancedMEOmniToolItem.MODE_PLACEMENT).isEqualTo(1);
        assertThat(AdvancedMEOmniToolItem.MODE_ROTATE).isEqualTo(2);
        assertThat(AdvancedMEOmniToolItem.MODE_TRAVEL).isEqualTo(3);
        assertThat(AdvancedMEOmniToolItem.DROP_NORMAL).isZero();
        assertThat(AdvancedMEOmniToolItem.DROP_INVENTORY).isEqualTo(1);
        assertThat(AdvancedMEOmniToolItem.DROP_AE).isEqualTo(2);
    }

    @Test
    void testModeNameKeys() {
        assertThat(AdvancedMEOmniToolItem.getModeNameKey(AdvancedMEOmniToolItem.MODE_UNIVERSAL))
                .isEqualTo("item.ae2enhanced.me_omni_tool.mode.universal");
        assertThat(AdvancedMEOmniToolItem.getModeNameKey(AdvancedMEOmniToolItem.MODE_PLACEMENT))
                .isEqualTo("item.ae2enhanced.me_omni_tool.mode.placement");
        assertThat(AdvancedMEOmniToolItem.getModeNameKey(AdvancedMEOmniToolItem.MODE_ROTATE))
                .isEqualTo("item.ae2enhanced.me_omni_tool.mode.rotate");
        assertThat(AdvancedMEOmniToolItem.getModeNameKey(AdvancedMEOmniToolItem.MODE_TRAVEL))
                .isEqualTo("item.ae2enhanced.me_omni_tool.mode.travel");
        // 越界模式号按模式数取模
        assertThat(AdvancedMEOmniToolItem.getModeNameKey(AdvancedMEOmniToolItem.MODE_COUNT))
                .isEqualTo("item.ae2enhanced.me_omni_tool.mode.universal");
    }

    @Test
    void testDropModeNameKeys() {
        assertThat(AdvancedMEOmniToolItem.getDropModeNameKey(AdvancedMEOmniToolItem.DROP_NORMAL))
                .isEqualTo("item.ae2enhanced.me_omni_tool.drop_mode.normal");
        assertThat(AdvancedMEOmniToolItem.getDropModeNameKey(AdvancedMEOmniToolItem.DROP_INVENTORY))
                .isEqualTo("item.ae2enhanced.me_omni_tool.drop_mode.inventory");
        assertThat(AdvancedMEOmniToolItem.getDropModeNameKey(AdvancedMEOmniToolItem.DROP_AE))
                .isEqualTo("item.ae2enhanced.me_omni_tool.drop_mode.ae");
        assertThat(AdvancedMEOmniToolItem.getDropModeNameKey(3))
                .isEqualTo("item.ae2enhanced.me_omni_tool.drop_mode.normal");
    }

    // ==================== 静态委托 ====================

    @Test
    void testStaticModeDelegation() {
        assertThat(AdvancedMEOmniToolItem.getMode(stack))
                .isEqualTo(AdvancedMEOmniToolItem.MODE_UNIVERSAL);
        AdvancedMEOmniToolItem.cycleMode(stack);
        assertThat(AdvancedMEOmniToolItem.getMode(stack))
                .isEqualTo(AdvancedMEOmniToolItem.MODE_PLACEMENT);
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_TRAVEL);
        assertThat(AdvancedMEOmniToolItem.getMode(stack))
                .isEqualTo(AdvancedMEOmniToolItem.MODE_TRAVEL);
    }

    @Test
    void testStaticUpgradeDelegation() {
        AdvancedMEOmniToolItem.setChaosCore(stack, true);
        assertThat(AdvancedMEOmniToolItem.hasChaosCore(stack)).isTrue();
        AdvancedMEOmniToolItem.setBedrockBreaker(stack, true);
        assertThat(AdvancedMEOmniToolItem.hasBedrockBreaker(stack)).isTrue();
        AdvancedMEOmniToolItem.setConformalCharge(stack, true);
        assertThat(AdvancedMEOmniToolItem.hasConformalCharge(stack)).isTrue();
        AdvancedMEOmniToolItem.setFortuneLevel(stack, 2);
        assertThat(AdvancedMEOmniToolItem.getFortuneLevel(stack)).isEqualTo(2);
        assertThat(AdvancedMEOmniToolItem.hasFortuneUpgrade(stack)).isTrue();
        assertThat(AdvancedMEOmniToolItem.hasStoredEnchantments(stack)).isTrue();
        assertThat(AdvancedMEOmniToolItem.getStoredEnchantments(stack)).hasSize(1);

        AdvancedMEOmniToolItem.toggleSilkTouch(stack);
        assertThat(AdvancedMEOmniToolItem.isSilkTouchEnabled(stack)).isTrue();
        AdvancedMEOmniToolItem.setAdvancedSilkTouchEnabled(stack, true);
        assertThat(AdvancedMEOmniToolItem.isAdvancedSilkTouchEnabled(stack)).isTrue();

        AdvancedMEOmniToolItem.setChaosForceKillEnabled(stack, false);
        assertThat(AdvancedMEOmniToolItem.isChaosForceKillEnabled(stack)).isFalse();

        AdvancedMEOmniToolItem.setParamEnabled(stack, 3, true);
        assertThat(AdvancedMEOmniToolItem.isParamEnabled(stack, 3)).isTrue();

        AdvancedMEOmniToolItem.cycleDropMode(stack);
        assertThat(AdvancedMEOmniToolItem.getDropMode(stack))
                .isEqualTo(AdvancedMEOmniToolItem.DROP_INVENTORY);
        AdvancedMEOmniToolItem.setDropMode(stack, AdvancedMEOmniToolItem.DROP_AE);
        assertThat(AdvancedMEOmniToolItem.getDropMode(stack)).isEqualTo(AdvancedMEOmniToolItem.DROP_AE);
    }

    // ==================== 潜行旁路 ====================

    @Test
    void testSneakBypassByMode() {
        // 通用与放置模式潜行时旁路方块交互
        assertThat(item.doesSneakBypassUse(stack, null, BlockPos.ZERO, null)).isTrue();
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_PLACEMENT);
        assertThat(item.doesSneakBypassUse(stack, null, BlockPos.ZERO, null)).isTrue();
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_ROTATE);
        assertThat(item.doesSneakBypassUse(stack, null, BlockPos.ZERO, null)).isFalse();
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_TRAVEL);
        assertThat(item.doesSneakBypassUse(stack, null, BlockPos.ZERO, null)).isFalse();
    }

    // ==================== 挖掘分发(固定走 MiningModule) ====================

    @Test
    void testDestroySpeedDispatch() {
        // 通用模式下极速挖掘
        assertThat(item.getDestroySpeed(stack, Blocks.STONE.defaultBlockState()))
                .isEqualTo(1_000_000.0f);
        // 旋转模式下回落到普通速度
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_ROTATE);
        assertThat(item.getDestroySpeed(stack, Blocks.STONE.defaultBlockState())).isEqualTo(1.0f);
    }

    @Test
    void testCorrectToolForDropsDispatch() {
        assertThat(item.isCorrectToolForDrops(stack, Blocks.STONE.defaultBlockState())).isTrue();
        AdvancedMEOmniToolItem.setMode(stack, AdvancedMEOmniToolItem.MODE_ROTATE);
        assertThat(item.isCorrectToolForDrops(stack, Blocks.STONE.defaultBlockState())).isFalse();
    }

    // ==================== 交互早退分支 ====================

    @Test
    void testUseOnClientSideReturnsSuccess() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        UseOnContext context = mock(UseOnContext.class);
        when(context.getLevel()).thenReturn(level);
        when(context.getPlayer()).thenReturn(null);
        when(context.getItemInHand()).thenReturn(stack);

        assertThat(item.useOn(context)).isEqualTo(InteractionResult.SUCCESS);
    }

    @Test
    void testUseOnWithoutPlayerReturnsPass() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        UseOnContext context = mock(UseOnContext.class);
        when(context.getLevel()).thenReturn(level);
        when(context.getPlayer()).thenReturn(null);
        when(context.getItemInHand()).thenReturn(stack);

        assertThat(item.useOn(context)).isEqualTo(InteractionResult.PASS);
    }

    @Test
    void testUseClientSideReturnsSuccessHolder() {
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);
        Player player = mock(Player.class);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);

        var result = item.use(level, player, InteractionHand.MAIN_HAND);
        assertThat(result.getResult()).isEqualTo(InteractionResult.SUCCESS);
        assertThat(result.getObject()).isSameAs(stack);
    }

    @Test
    void testUseServerSideUniversalModePasses() {
        // 通用模式右键空气:MiningModule 默认实现返回 PASS
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(false);
        Player player = mock(Player.class);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);

        var result = item.use(level, player, InteractionHand.MAIN_HAND);
        assertThat(result.getResult()).isEqualTo(InteractionResult.PASS);
        assertThat(result.getObject()).isSameAs(stack);
    }

    // ==================== 属性修饰符 ====================

    @Test
    void testAttributeModifiersEmptyInUniversalMode() {
        // 通用模式的 MiningModule 不提供属性修饰符,物品本身也没有默认修饰符
        assertThat(item.getAttributeModifiers(EquipmentSlot.MAINHAND, stack).isEmpty()).isTrue();
        assertThat(item.getAttributeModifiers(EquipmentSlot.OFFHAND, stack).isEmpty()).isTrue();
    }

    // ==================== Tooltip ====================

    @Test
    void testHoverTextFreshTool() {
        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, null, tooltip, TooltipFlag.NORMAL);

        Set<String> keys = collectKeys(tooltip);
        assertThat(keys).contains(
                "item.ae2enhanced.me_omni_tool.mode",
                "item.ae2enhanced.me_omni_tool.mode.universal",
                "item.ae2enhanced.me_omni_tool.silk_touch.off",
                "item.ae2enhanced.me_omni_tool.break_cooldown",
                "item.ae2enhanced.me_omni_tool.drop_mode",
                "item.ae2enhanced.me_omni_tool.no_upgrades");
    }

    @Test
    void testHoverTextWithUpgrades() {
        AdvancedMEOmniToolItem.setChaosCore(stack, true);
        AdvancedMEOmniToolItem.setBedrockBreaker(stack, true);
        AdvancedMEOmniToolItem.setConformalCharge(stack, true);
        AdvancedMEOmniToolItem.setSilkTouchEnabled(stack, true);

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, null, tooltip, TooltipFlag.NORMAL);

        Set<String> keys = collectKeys(tooltip);
        assertThat(keys).contains(
                "item.ae2enhanced.me_omni_tool.silk_touch.on",
                "item.ae2enhanced.me_omni_tool.upgrade.chaos",
                "item.ae2enhanced.me_omni_tool.upgrade.bedrock",
                "item.ae2enhanced.me_omni_tool.upgrade.conformal");
        // 有升级时不显示"无升级"
        assertThat(keys).doesNotContain("item.ae2enhanced.me_omni_tool.no_upgrades");
    }

    @Test
    void testHoverTextShowsStoredEnchantmentName() {
        // 未注册的存储附魔走 unknown_enchant 分支
        AdvancedMEOmniToolItem.setFortuneLevel(stack, 3);

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, null, tooltip, TooltipFlag.NORMAL);
        // 时运为已注册附魔,显示附魔名(enchantment.minecraft.fortune)而非 unknown_enchant
        Set<String> keys = collectKeys(tooltip);
        assertThat(keys).contains("enchantment.minecraft.fortune");
        assertThat(keys).doesNotContain("item.ae2enhanced.me_omni_tool.unknown_enchant");
    }

    @Test
    void testHoverTextShowsUnknownEnchantForUnregisteredId() {
        net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        net.minecraft.nbt.CompoundTag entry = new net.minecraft.nbt.CompoundTag();
        entry.putString("id", "minecraft:nonexistent_enchant");
        entry.putShort("lvl", (short) 2);
        list.add(entry);
        com.github.aeddddd.ae2enhanced.omnitool.OmniToolEnchantments.setStoredEnchantments(stack, list);

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, null, tooltip, TooltipFlag.NORMAL);
        assertThat(collectKeys(tooltip)).contains("item.ae2enhanced.me_omni_tool.unknown_enchant");
    }

    @Test
    void testHoverTextShowsLinkedNetwork() {
        OmniToolNetworkLink.link(stack, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO));

        List<Component> tooltip = new ArrayList<>();
        item.appendHoverText(stack, null, tooltip, TooltipFlag.NORMAL);
        assertThat(collectKeys(tooltip)).contains("item.ae2enhanced.me_omni_tool.ae_bound");
    }

    /**
     * 递归收集组件树中的所有本地化键(tooltip 行会把文本包装在兄弟组件里).
     */
    private static Set<String> collectKeys(List<Component> components) {
        Set<String> keys = new HashSet<>();
        collectKeysInto(components, keys);
        return keys;
    }

    private static void collectKeysInto(List<Component> components, Set<String> keys) {
        for (Component component : components) {
            if (component.getContents() instanceof TranslatableContents t) {
                keys.add(t.getKey());
                // 模式名等文本作为参数嵌在翻译组件里,需要递归参数
                for (Object arg : t.getArgs()) {
                    if (arg instanceof Component c) {
                        collectKeysInto(List.of(c), keys);
                    }
                }
            }
            collectKeysInto(component.getSiblings(), keys);
        }
    }
}
