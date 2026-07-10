package com.github.aeddddd.ae2enhanced.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.AssemblyPatternScreen;
import com.github.aeddddd.ae2enhanced.client.gui.AssemblyScreen;
import com.github.aeddddd.ae2enhanced.client.gui.AssemblyUnformedScreen;
import com.github.aeddddd.ae2enhanced.client.gui.ComputationCoreScreen;
import com.github.aeddddd.ae2enhanced.client.gui.ComputationUnformedScreen;
import com.github.aeddddd.ae2enhanced.client.gui.HyperdimensionalNexusScreen;
import com.github.aeddddd.ae2enhanced.client.gui.HyperdimensionalUnformedScreen;
import com.github.aeddddd.ae2enhanced.client.render.AssemblyHubRenderer;
import com.github.aeddddd.ae2enhanced.client.render.HyperdimensionalControllerRenderer;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 客户端专属初始化：注册菜单屏幕等。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.ASSEMBLY.get(), AssemblyScreen::new);
            MenuScreens.register(ModMenus.ASSEMBLY_PATTERN.get(), AssemblyPatternScreen::new);
            MenuScreens.register(ModMenus.ASSEMBLY_UNFORMED.get(), AssemblyUnformedScreen::new);
            MenuScreens.register(ModMenus.HYPERDIMENSIONAL_NEXUS.get(), HyperdimensionalNexusScreen::new);
            MenuScreens.register(ModMenus.HYPERDIMENSIONAL_UNFORMED.get(), HyperdimensionalUnformedScreen::new);
            MenuScreens.register(ModMenus.COMPUTATION_CORE.get(), ComputationCoreScreen::new);
            MenuScreens.register(ModMenus.COMPUTATION_UNFORMED.get(), ComputationUnformedScreen::new);

            BlockEntityRenderers.register(ModBlockEntities.ASSEMBLY_CONTROLLER.get(), AssemblyHubRenderer::new);
            BlockEntityRenderers.register(ModBlockEntities.HYPERDIMENSIONAL_CONTROLLER.get(),
                    HyperdimensionalControllerRenderer::new);
        });
    }

    private ClientSetup() {
    }
}
