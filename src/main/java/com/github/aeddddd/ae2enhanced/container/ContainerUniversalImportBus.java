package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.part.PartUniversalImportBus;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * E1a：通用输入总线的 Container.
 */
public class ContainerUniversalImportBus extends AbstractUniversalBusContainer {

    public ContainerUniversalImportBus(InventoryPlayer ip, PartUniversalImportBus te) {
        super(ip, te);
    }

    @Override
    public PartUniversalImportBus getPart() {
        return (PartUniversalImportBus) this.part;
    }
}
