package com.github.aeddddd.ae2enhanced.container;

import appeng.api.config.SecurityPermissions;
import appeng.api.config.Upgrades;
import appeng.container.implementations.ContainerUpgradeable;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.OptionalSlotRestrictedInput;
import appeng.container.slot.SlotFake;
import appeng.container.slot.SlotOversized;
import appeng.container.guisync.GuiSync;
import appeng.container.slot.SlotRestrictedInput;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.centralinterface.DualityCentralInterface;
import com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.items.IItemHandler;

/**
 * 中枢 ME 接口的 Container.
 *
 * 复刻 AE2 ContainerInterface 的槽位布局.
 */
public class ContainerCentralInterface extends ContainerUpgradeable implements IOptionalSlotHost {

    private final TileCentralMEInterface tile;
    private final DualityCentralInterface duality;

    @GuiSync(value = 7)
    public int patternExpansions = 0;

    public ContainerCentralInterface(InventoryPlayer ip, TileCentralMEInterface te) {
        super(ip, te);
        this.tile = te;
        this.duality = te.getInterfaceDuality();

        IItemHandler config = this.duality.getConfig();
        IItemHandler patterns = this.duality.getPatterns();
        IItemHandler storage = this.duality.getStorage();

        // Pattern slots (optional, unlocked by upgrades) - y = 97 + 18*row
        // 必须放在 Storage/Config 之前，否则 Shift+点击样板会先落入 Storage 槽。
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = row * 9 + col;
                this.addSlotToContainer(
                    new OptionalSlotRestrictedInput(
                        SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                        patterns,
                        this,
                        slotIndex,
                        8 + 18 * col,
                        97 + 18 * row,
                        row,
                        this.getInventoryPlayer()
                    ).setStackLimit(1)
                );
            }
        }

        // Config slots (ghost/filter slots) - y = 35
        for (int i = 0; i < DualityCentralInterface.NUMBER_OF_CONFIG_SLOTS; i++) {
            this.addSlotToContainer(new SlotFake(config, i, 8 + 18 * i, 35));
        }

        // Storage slots (oversized) - y = 53
        for (int i = 0; i < DualityCentralInterface.NUMBER_OF_STORAGE_SLOTS; i++) {
            this.addSlotToContainer(new SlotOversized(storage, i, 8 + 18 * i, 53));
        }
    }

    @Override
    protected int getHeight() {
        return 256;
    }

    @Override
    protected void setupConfig() {
        this.setupUpgrades();
    }

    @Override
    protected boolean supportCapacity() {
        return true;
    }

    @Override
    public int availableUpgrades() {
        return DualityCentralInterface.NUMBER_OF_UPGRADE_SLOTS;
    }

    @Override
    public boolean isSlotEnabled(int idx) {
        // idx 是 groupNum,即 pattern 行号 (0-3)
        // 第 0 行始终可见,其余需要 Pattern Expansion 升级
        if (idx <= 0) return true;
        int expansions = this.tile.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION);
        return expansions >= idx;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return Platform.hasPermissions(this.tile.getWorld(), this.tile.getPos(), player);
    }

    public DualityCentralInterface getDuality() {
        return this.duality;
    }

    @Override
    public void func_75142_b() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);
        if (this.patternExpansions != this.getPatternUpgrades()) {
            this.patternExpansions = this.getPatternUpgrades();
            this.duality.dropExcessPatterns();
        }
        super.func_75142_b();
    }

    @Override
    protected void loadSettingsFromHost(appeng.api.util.IConfigManager cm) {
        // 中枢 ME 接口模式固定,不需要从 ConfigManager 同步设置到 GUI 按钮
    }

    @Override
    public void onUpdate(String field, Object oldValue, Object newValue) {
        super.onUpdate(field, oldValue, newValue);
        if (Platform.isClient() && field.equals("patternExpansions")) {
            this.duality.dropExcessPatterns();
        }
    }

    public int getPatternUpgrades() {
        return this.tile.getInstalledUpgrades(Upgrades.PATTERN_EXPANSION);
    }
}
