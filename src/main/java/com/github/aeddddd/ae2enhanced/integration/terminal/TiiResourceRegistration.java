package com.github.aeddddd.ae2enhanced.integration.terminal;

import com.github.aeddddd.ae2enhanced.integration.botaniaapplie.BotaniaApplieCompat;
import com.github.aeddddd.ae2enhanced.integration.terminal.tii.energy.EnergyResourceProvider;
import com.github.aeddddd.ae2enhanced.integration.terminal.tii.mana.ManaResourceProvider;
import com.github.aeddddd.ae2enhanced.integration.terminal.tii.starlight.StarlightResourceProvider;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import nyonio.terminal_interaction_integration.api.ResourceRegistrationEvent;

/**
 * TII 资源提供者注册器.
 * <p>
 * 仅在 TII 已加载时实例化并注册到 Forge 事件总线.
 * 根据外部 mod 是否存在,条件注册 Mana 与 Starlight 提供者.
 * </p>
 */
public class TiiResourceRegistration {

    @SubscribeEvent
    public void onResourceRegistration(ResourceRegistrationEvent event) {
        registerProviders(event::register);
    }

    /** TII 的一次性事件可能早于 AE2E 通道注册；通道就绪后直接注册，事件重复注册由 TII 优先级去重。 */
    public void registerDirect() {
        registerProviders(nyonio.terminal_interaction_integration.api.TerminalInteractionRegistry::registerResourceProvider);
    }

    private void registerProviders(java.util.function.Consumer<nyonio.terminal_interaction_integration.api.IResourceProvider> registry) {
        registry.accept(new EnergyResourceProvider());
        if (Loader.isModLoaded("botania") || BotaniaApplieCompat.isLoaded()) {
            registry.accept(new ManaResourceProvider());
        }
        if (Loader.isModLoaded("astralsorcery")) {
            registry.accept(new StarlightResourceProvider());
        }
    }
}
