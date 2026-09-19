package com.github.aeddddd.ae2enhanced.container;

import appeng.api.config.*;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.slot.SlotRestrictedInput;
import appeng.util.Platform;
import appeng.util.helpers.ItemHandlerUtil;
import com.github.aeddddd.ae2enhanced.container.slot.OptionalSlotEnergyTypeOnly;
import com.github.aeddddd.ae2enhanced.container.slot.SlotEnergyTypeOnly;
import com.github.aeddddd.ae2enhanced.item.ItemEnergyDrop;
import com.github.aeddddd.ae2enhanced.part.PartEnergyStorageBus;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.items.IItemHandler;

/**
 * 能源存储总线 Container,布局完全对标原版存储总线(ContainerStorageBus):
 * 63 过滤槽(前 18 常驻,后 45 由容量卡解锁)+ 5 升级卡槽.
 */
public class ContainerEnergyStorageBus extends ContainerUpgradeable {

    @GuiSync(value = 3)
    public AccessRestriction rwMode = AccessRestriction.READ_WRITE;

    @GuiSync(value = 4)
    public StorageFilter storageFilter = StorageFilter.EXTRACTABLE_ONLY;

    public ContainerEnergyStorageBus(InventoryPlayer ip, PartEnergyStorageBus te) {
        super(ip, te);
    }

    @Override
    protected int getHeight() {
        return 251;
    }

    @Override
    protected void setupConfig() {
        IItemHandler config = this.getUpgradeable().getInventoryByName("config");
        for (int y = 0; y < 7; ++y) {
            for (int x = 0; x < 9; ++x) {
                if (y < 2) {
                    this.addSlotToContainer(new SlotEnergyTypeOnly(config, y * 9 + x, 8 + x * 18, 29 + y * 18));
                    continue;
                }
                this.addSlotToContainer(new OptionalSlotEnergyTypeOnly(config, this, y * 9 + x, 8, 29, x, y, y - 2));
            }
        }
        IItemHandler upgrades = this.getUpgradeable().getInventoryByName("upgrades");
        this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 0, 187, 8, this.getInventoryPlayer()).setNotDraggable());
        this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 1, 187, 26, this.getInventoryPlayer()).setNotDraggable());
        this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 2, 187, 44, this.getInventoryPlayer()).setNotDraggable());
        this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 3, 187, 62, this.getInventoryPlayer()).setNotDraggable());
        this.addSlotToContainer(new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES, upgrades, 4, 187, 80, this.getInventoryPlayer()).setNotDraggable());
    }

    @Override
    protected boolean supportCapacity() {
        return true;
    }

    @Override
    public int availableUpgrades() {
        return 5;
    }

    @Override
    public void func_75142_b() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);
        if (Platform.isServer()) {
            this.setReadWriteMode((AccessRestriction) this.getUpgradeable().getConfigManager().getSetting(Settings.ACCESS));
            this.setStorageFilter((StorageFilter) this.getUpgradeable().getConfigManager().getSetting(Settings.STORAGE_FILTER));
        }
        this.standardDetectAndSendChanges();
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        int upgrades = this.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY);
        return upgrades > idx;
    }

    public void clear() {
        ItemHandlerUtil.clear(this.getUpgradeable().getInventoryByName("config"));
        this.func_75142_b();
    }

    /**
     * 分区:能量容器的内容只有 RF 一种,直接在第一个过滤槽放入 RF 模板.
     */
    public void partition() {
        IItemHandler inv = this.getUpgradeable().getInventoryByName("config");
        ItemHandlerUtil.clear(inv);
        ItemHandlerUtil.setStackInSlot(inv, 0, ItemEnergyDrop.createStack());
        this.func_75142_b();
    }

    public AccessRestriction getReadWriteMode() {
        return this.rwMode;
    }

    private void setReadWriteMode(AccessRestriction rwMode) {
        this.rwMode = rwMode;
    }

    public StorageFilter getStorageFilter() {
        return this.storageFilter;
    }

    private void setStorageFilter(StorageFilter storageFilter) {
        this.storageFilter = storageFilter;
    }
}
