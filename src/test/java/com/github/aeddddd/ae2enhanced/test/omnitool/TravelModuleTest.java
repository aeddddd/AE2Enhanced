package com.github.aeddddd.ae2enhanced.test.omnitool;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.module.TravelModule;
import com.github.aeddddd.ae2enhanced.testutil.ConfigTestBootstrap;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TravelModule} 可在纯单测环境覆盖的部分:状态委托/tooltip/客户端早退分支.
 * 实际闪现传送依赖 {@code Level#clip} 与真实碰撞世界,不在单测范围.
 */
class TravelModuleTest {

    private final TravelModule module = new TravelModule();

    @BeforeAll
    static void bootstrap() {
        OmniToolTestSupport.bootstrap();
    }

    private static ItemStack newToolStack() {
        return OmniToolTestSupport.newToolStack();
    }

    @Test
    void testGetMode() {
        assertThat(module.getMode()).isEqualTo(AdvancedMEOmniToolItem.MODE_TRAVEL);
    }

    @Test
    void testBlinkDistanceDelegation() {
        ItemStack stack = newToolStack();
        assertThat(TravelModule.getBlinkDistance(stack)).isEqualTo(256.0); // 配置默认值
        TravelModule.setBlinkDistance(stack, 32.0);
        assertThat(TravelModule.getBlinkDistance(stack)).isEqualTo(32.0);
    }

    @Test
    void testWallPhaseDelegation() {
        ItemStack stack = newToolStack();
        assertThat(TravelModule.isWallPhaseEnabled(stack)).isTrue(); // 配置默认开启
        TravelModule.setWallPhaseEnabled(stack, false);
        assertThat(TravelModule.isWallPhaseEnabled(stack)).isFalse();
    }

    @Test
    void testAddTooltipShowsWallPhaseState() {
        ItemStack stack = newToolStack();
        List<Component> tooltip = new ArrayList<>();
        module.addTooltip(stack, null, tooltip, TooltipFlag.NORMAL);

        assertThat(tooltip.stream().map(TravelModuleTest::keyOf))
                .containsExactly(
                        "item.ae2enhanced.me_omni_tool.blink_dist",
                        "item.ae2enhanced.me_omni_tool.wall_phase.on");

        TravelModule.setWallPhaseEnabled(stack, false);
        tooltip.clear();
        module.addTooltip(stack, null, tooltip, TooltipFlag.NORMAL);
        assertThat(tooltip.stream().map(TravelModuleTest::keyOf))
                .containsExactly(
                        "item.ae2enhanced.me_omni_tool.blink_dist",
                        "item.ae2enhanced.me_omni_tool.wall_phase.off");
    }

    @Test
    void testOnItemUseWithoutPlayerReturnsPass() {
        UseOnContext context = mock(UseOnContext.class);
        when(context.getPlayer()).thenReturn(null);
        assertThat(module.onItemUse(context)).isEqualTo(InteractionResult.PASS);
    }

    @Test
    void testOnItemUseClientSideReturnsSuccess() {
        // 客户端早退:不执行传送,直接 SUCCESS
        ItemStack stack = newToolStack();
        Player player = mock(Player.class);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);

        UseOnContext context = mock(UseOnContext.class);
        when(context.getPlayer()).thenReturn(player);
        when(context.getLevel()).thenReturn(level);
        when(context.getHand()).thenReturn(InteractionHand.MAIN_HAND);

        assertThat(module.onItemUse(context)).isEqualTo(InteractionResult.SUCCESS);
    }

    @Test
    void testRightClickClientSideReturnsSuccessHolder() {
        ItemStack stack = newToolStack();
        Player player = mock(Player.class);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
        Level level = mock(Level.class);
        when(level.isClientSide()).thenReturn(true);

        InteractionResultHolder<ItemStack> result =
                module.onItemRightClick(level, player, InteractionHand.MAIN_HAND);
        assertThat(result.getResult()).isEqualTo(InteractionResult.SUCCESS);
        assertThat(result.getObject()).isSameAs(stack);
    }

    private static String keyOf(Component component) {
        return component.getContents() instanceof TranslatableContents t ? t.getKey() : null;
    }
}
