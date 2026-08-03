package com.github.aeddddd.ae2enhanced.test.omnitool;

import java.util.ArrayList;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.item.AdvancedMEOmniToolItem;
import com.github.aeddddd.ae2enhanced.omnitool.module.CombatModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.IOmniToolModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.MiningModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.OmniToolModules;
import com.github.aeddddd.ae2enhanced.omnitool.module.PlacementModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.RotationModule;
import com.github.aeddddd.ae2enhanced.omnitool.module.TravelModule;
import com.github.aeddddd.ae2enhanced.testutil.MinecraftTestBootstrap;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link OmniToolModules} 模块注册表与 {@link IOmniToolModule} 默认方法测试.
 */
class OmniToolModulesTest {

    @BeforeAll
    static void bootstrap() {
        MinecraftTestBootstrap.bootstrap();
    }

    @Test
    void testModeToModuleMapping() {
        assertThat(OmniToolModules.getForMode(AdvancedMEOmniToolItem.MODE_UNIVERSAL))
                .isInstanceOf(MiningModule.class);
        assertThat(OmniToolModules.getForMode(AdvancedMEOmniToolItem.MODE_PLACEMENT))
                .isInstanceOf(PlacementModule.class);
        assertThat(OmniToolModules.getForMode(AdvancedMEOmniToolItem.MODE_ROTATE))
                .isInstanceOf(RotationModule.class);
        assertThat(OmniToolModules.getForMode(AdvancedMEOmniToolItem.MODE_TRAVEL))
                .isInstanceOf(TravelModule.class);
    }

    @Test
    void testGetForModeWrapsAround() {
        // 超出范围的模式号按模块数取模
        assertThat(OmniToolModules.getForMode(AdvancedMEOmniToolItem.MODE_COUNT))
                .isSameAs(OmniToolModules.getForMode(AdvancedMEOmniToolItem.MODE_UNIVERSAL));
    }

    @Test
    void testModulesAreCachedSingletons() {
        assertThat(OmniToolModules.getForMode(AdvancedMEOmniToolItem.MODE_UNIVERSAL))
                .isSameAs(OmniToolModules.getForMode(AdvancedMEOmniToolItem.MODE_UNIVERSAL));
    }

    @Test
    void testModuleGetModeMatchesRegistrationSlot() {
        for (int mode = 0; mode < AdvancedMEOmniToolItem.MODE_COUNT; mode++) {
            assertThat(OmniToolModules.getForMode(mode).getMode()).isEqualTo(mode);
        }
    }

    @Test
    void testCombatModuleReportsUniversalMode() {
        // 战斗模块不参与注册表,但其模式号为通用模式(战斗逻辑独立于模式)
        assertThat(new CombatModule().getMode()).isEqualTo(AdvancedMEOmniToolItem.MODE_UNIVERSAL);
    }

    @Test
    void testInterfaceDefaultMethods() {
        IOmniToolModule module = new IOmniToolModule() {
            @Override
            public int getMode() {
                return 0;
            }
        };
        ItemStack stack = OmniToolTestSupport.newToolStack();
        Player player = mock(Player.class);
        when(player.getItemInHand(InteractionHand.MAIN_HAND)).thenReturn(stack);

        assertThat(module.onItemUse(null)).isEqualTo(InteractionResult.PASS);
        assertThat(module.onItemRightClick(null, player, InteractionHand.MAIN_HAND).getResult())
                .isEqualTo(InteractionResult.PASS);
        assertThat(module.onItemRightClick(null, player, InteractionHand.MAIN_HAND).getObject())
                .isSameAs(stack);
        assertThat(module.onBlockStartBreak(stack, null, player)).isFalse();
        assertThat(module.getDestroySpeed(stack, null)).isEqualTo(1.0f);
        assertThat(module.canHarvestBlock(null, stack)).isFalse();
        assertThat(module.onLeftClickEntity(stack, player, null)).isFalse();
        assertThat(module.getAttributeModifiers(null, stack).isEmpty()).isTrue();

        // addTooltip 默认无操作
        var tooltip = new ArrayList<Component>();
        module.addTooltip(stack, null, tooltip, TooltipFlag.NORMAL);
        assertThat(tooltip).isEmpty();
    }
}
