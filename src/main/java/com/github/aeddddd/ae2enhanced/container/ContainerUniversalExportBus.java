package com.github.aeddddd.ae2enhanced.container;

import com.github.aeddddd.ae2enhanced.part.PartUniversalExportBus;
import net.minecraft.entity.player.InventoryPlayer;

/**
 * E1b：通用输出总线的 Container.
 */
public class ContainerUniversalExportBus extends AbstractUniversalBusContainer {

    public ContainerUniversalExportBus(InventoryPlayer ip, PartUniversalExportBus te) {
        super(ip, te);
    }

    @Override
    public PartUniversalExportBus getPart() {
        return (PartUniversalExportBus) this.part;
    }
}
