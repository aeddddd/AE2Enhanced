package com.github.aeddddd.ae2enhanced.crafting;

import appeng.api.networking.crafting.ICraftingMedium;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/** 每个 CPU 实例独享的执行状态；原生字段本身仍由 ISpecialCpuAccess 桥接。 */
public final class CraftingCpuExecutionContext
        implements com.github.aeddddd.ae2enhanced.craftingapi.CraftingCpuState {

    private final IdentityHashMap<ICraftingMedium, Long> batchCeilings = new IdentityHashMap<>();
    private final IdentityHashMap<ICraftingPatternDetails, Long> canCraftFailures = new IdentityHashMap<>();
    private final IdentityHashMap<ICraftingMedium, Long> pushFailureTicks = new IdentityHashMap<>();
    private IdentityHashMap<ICraftingPatternDetails, Long> quotaPushedThisTick;
    private Map<ICraftingPatternDetails, Long> remainingSnapshot;
    private Set<ICraftingPatternDetails> vetoedThisTick;
    private boolean specialMarked;
    private Map<ICraftingPatternDetails, Long> specialTotals;
    private Object specialQuota;
    private long executeStartNanos = -1L;
    private boolean creativeEnergy;
    private long creativeEnergyCheckTick = Long.MIN_VALUE;
    private ICraftingPatternDetails expectedDetails;
    private InventoryCrafting expectedInventory;
    private NonNullList<ItemStack> expectedRemaining;
    private int expectedSlot;
    private long lastStuckWarning;
    private TileEntity computationCore;
    private boolean dirtyMarkedThisTick;

    public TileEntity computationCore() {
        return computationCore;
    }

    public void computationCore(TileEntity value) {
        computationCore = value;
    }

    public boolean markDirty() {
        if (dirtyMarkedThisTick) {
            return false;
        }
        dirtyMarkedThisTick = true;
        if (computationCore != null) {
            computationCore.markDirty();
        }
        return true;
    }

    public void beginTick() {
        dirtyMarkedThisTick = false;
    }

    public boolean dirtyMarkedThisTick() {
        return dirtyMarkedThisTick;
    }

    public IdentityHashMap<ICraftingMedium, Long> batchCeilings() {
        return batchCeilings;
    }

    public IdentityHashMap<ICraftingPatternDetails, Long> canCraftFailures() {
        return canCraftFailures;
    }

    public IdentityHashMap<ICraftingMedium, Long> pushFailureTicks() {
        return pushFailureTicks;
    }

    public IdentityHashMap<ICraftingPatternDetails, Long> quotaPushedThisTick() {
        return quotaPushedThisTick;
    }

    public void clearQuotaPushedThisTick() {
        if (quotaPushedThisTick != null) {
            quotaPushedThisTick.clear();
        }
    }

    public void recordQuotaPushed(ICraftingPatternDetails details, long count) {
        if (quotaPushedThisTick == null) {
            quotaPushedThisTick = new IdentityHashMap<>();
        }
        quotaPushedThisTick.merge(details, count, Long::sum);
    }

    public Map<ICraftingPatternDetails, Long> remainingSnapshot() {
        return remainingSnapshot;
    }

    public void remainingSnapshot(Map<ICraftingPatternDetails, Long> value) {
        remainingSnapshot = value;
    }

    public Set<ICraftingPatternDetails> vetoedThisTick() {
        return vetoedThisTick;
    }

    public void vetoedThisTick(Set<ICraftingPatternDetails> value) {
        vetoedThisTick = value;
    }

    public void beginExecute() {
        remainingSnapshot = null;
        vetoedThisTick = null;
        clearQuotaPushedThisTick();
    }

    public void endExecute() {
        remainingSnapshot = null;
        vetoedThisTick = null;
    }

    public boolean specialMarked() {
        return specialMarked;
    }

    public void specialMarked(boolean value) {
        specialMarked = value;
        if (!value) {
            specialTotals = null;
            specialQuota = null;
            lastStuckWarning = 0L;
        }
    }

    public Map<ICraftingPatternDetails, Long> specialTotals() {
        return specialTotals;
    }

    public void specialTotals(Map<ICraftingPatternDetails, Long> value) {
        specialTotals = value;
        specialQuota = null;
    }

    public Object specialQuota() {
        return specialQuota;
    }

    public void specialQuota(Object value) {
        specialQuota = value;
    }

    public void clearSpecialPlan() {
        specialTotals = null;
        specialQuota = null;
    }

    public long executeStartNanos() {
        return executeStartNanos;
    }

    public void executeStartNanos(long value) {
        executeStartNanos = value;
    }

    public boolean creativeEnergy() {
        return creativeEnergy;
    }

    public void creativeEnergy(boolean value) {
        creativeEnergy = value;
    }

    public long creativeEnergyCheckTick() {
        return creativeEnergyCheckTick;
    }

    public void creativeEnergyCheckTick(long value) {
        creativeEnergyCheckTick = value;
    }

    public ICraftingPatternDetails expectedDetails() {
        return expectedDetails;
    }

    public InventoryCrafting expectedInventory() {
        return expectedInventory;
    }

    public NonNullList<ItemStack> expectedRemaining() {
        return expectedRemaining;
    }

    public int expectedSlot() {
        return expectedSlot;
    }

    public void beginPush(ICraftingPatternDetails details, InventoryCrafting inventory) {
        expectedDetails = details;
        expectedInventory = inventory;
        expectedRemaining = null;
        expectedSlot = 0;
    }

    public void expectedRemaining(NonNullList<ItemStack> value) {
        expectedRemaining = value;
        expectedSlot = 0;
    }

    public int nextExpectedSlot() {
        return expectedSlot++;
    }

    public void clearExpectedRemaining() {
        expectedRemaining = null;
    }

    public long lastStuckWarning() {
        return lastStuckWarning;
    }

    public void lastStuckWarning(long value) {
        lastStuckWarning = value;
    }
}
