package com.github.aeddddd.ae2enhanced.client.gui.slot;

import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.SlotPatternOutputs;
import net.minecraftforge.items.IItemHandler;

/**
 * 支持 visible 切换的样板输出槽位(81槽位编码终端用)
 */
public class RCSlotPatternOutputs extends SlotPatternOutputs {

    public boolean visible = true;
    private final int defX;
    private final int defY;

    public RCSlotPatternOutputs(IItemHandler inv, IOptionalSlotHost host, int idx, int x, int y, int offX, int offY, int page) {
        super(inv, host, idx, x, y, offX, offY, page);
        this.defX = x;
        this.defY = y;
    }

    @Override
    public boolean shouldDisplay() {
        return this.visible;
    }

    public int getDefX() {
        return this.defX;
    }

    public int getDefY() {
        return this.defY;
    }
}
