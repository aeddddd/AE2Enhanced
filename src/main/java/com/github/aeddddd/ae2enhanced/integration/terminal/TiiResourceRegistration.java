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
        // RF 能量提供者始终注册
        event.register(new EnergyResourceProvider());

        // Mana 提供者仅在 Botania 或 Botania_Applie 加载时注册
        if (Loader.isModLoaded("botania") || BotaniaApplieCompat.isLoaded()) {
            event.register(new ManaResourceProvider());
        }

        // Starlight 提供者仅在 Astral Sorcery 加载时注册
        if (Loader.isModLoaded("astralsorcery")) {
            event.register(new StarlightResourceProvider());
        }
    }
}
