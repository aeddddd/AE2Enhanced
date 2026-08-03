package com.github.aeddddd.ae2enhanced.test.omnitool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.module.PlacementModule;
import com.github.aeddddd.ae2enhanced.omnitool.network.OmniToolNetworkLink;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;
import com.github.aeddddd.ae2enhanced.util.placement.PlacementConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PlacementModule} 可在纯单测环境覆盖的部分:潜行右键/tooltip/属性修饰符.
 * 实际放置逻辑({@code onItemUse})依赖 AE 网络与真实世界,不在单测范围.
 */
class PlacementModuleTest {

    private final PlacementModule module = new PlacementModule();

    @BeforeAll
    static void bootstrap() {
        OmniToolTestSupport.bootstrap();
    }

    private static ItemStack newToolStack() {
        return OmniToolTestSupport.newToolStack();
    }

    private static Player mockPlayer(boolean sneaking, ItemStack stack) {
        Player player = mock(Player.class);
        when(player.isShiftKeyDown()).thenReturn(sneaking);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);
        return player;
    }

    @Test
    void testGetMode() {
        assertThat(module.getMode()).isEqualTo(AdvancedMEOmniToolItem.MODE_PLACEMENT);
    }

    @Test
    void testSneakRightClickClearsCableStart() {
        ItemStack stack = newToolStack();
        new PlacementConfig(stack).setCableStart(new BlockPos(1, 2, 3));

        InteractionResultHolder<ItemStack> result =
                module.onItemRightClick(null, mockPlayer(true, stack), InteractionHand.MAIN_HAND);

        assertThat(result.getResult()).isEqualTo(InteractionResult.SUCCESS);
        assertThat(result.getObject()).isSameAs(stack);
        // 线缆起点被清除
        assertThat(new PlacementConfig(stack).getCableStart()).isNull();
    }

    @Test
    void testSneakRightClickWithoutCableStartPasses() {
        ItemStack stack = newToolStack();
        InteractionResultHolder<ItemStack> result =
                module.onItemRightClick(null, mockPlayer(true, stack), InteractionHand.MAIN_HAND);
        assertThat(result.getResult()).isEqualTo(InteractionResult.PASS);
        assertThat(result.getObject()).isSameAs(stack);
    }

    @Test
    void testPlainRightClickPasses() {
        ItemStack stack = newToolStack();
        new PlacementConfig(stack).setCableStart(new BlockPos(1, 2, 3));

        InteractionResultHolder<ItemStack> result =
                module.onItemRightClick(null, mockPlayer(false, stack), InteractionHand.MAIN_HAND);
        // 非潜行右键不做任何动作,线缆起点保留
        assertThat(result.getResult()).isEqualTo(InteractionResult.PASS);
        assertThat(new PlacementConfig(stack).getCableStart()).isEqualTo(new BlockPos(1, 2, 3));
    }

    @Test
    void testTooltipNoSelectionAndUnlinked() {
        ItemStack stack = newToolStack();
        List<Component> tooltip = new ArrayList<>();
        module.addTooltip(stack, null, tooltip, TooltipFlag.NORMAL);

        assertThat(collectKeys(tooltip)).contains(
                "item.ae2enhanced.me_omni_tool.placement.no_selection",
                "item.ae2enhanced.me_omni_tool.placement.unlinked");
    }

    @Test
    void testTooltipShowsLinkedState() {
        ItemStack stack = newToolStack();
        OmniToolNetworkLink.link(stack, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO));

        List<Component> tooltip = new ArrayList<>();
        module.addTooltip(stack, null, tooltip, TooltipFlag.NORMAL);

        assertThat(collectKeys(tooltip)).contains(
                "item.ae2enhanced.me_omni_tool.placement.linked");
    }

    @Test
    void testAttributeModifiersEmptyOffMainhand() {
        // 仅主手提供触及距离修饰符;主手分支依赖 ForgeMod.BLOCK_REACH 注册,单测环境不可用
        ItemStack stack = newToolStack();
        assertThat(module.getAttributeModifiers(EquipmentSlot.OFFHAND, stack).isEmpty()).isTrue();
        assertThat(module.getAttributeModifiers(EquipmentSlot.CHEST, stack).isEmpty()).isTrue();
    }

    /**
     * 递归收集组件树中的所有本地化键(bullet 包装会把文本放进兄弟组件).
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
            }
            collectKeysInto(component.getSiblings(), keys);
        }
    }
}
