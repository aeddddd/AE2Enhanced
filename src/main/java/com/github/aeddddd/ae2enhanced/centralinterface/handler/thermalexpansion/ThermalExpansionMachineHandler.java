package com.github.aeddddd.ae2enhanced.centralinterface.handler.thermalexpansion;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Thermal Expansion 机器远程处理器.
 *
 * <p>通过反射直接访问 CoFH {@code TileInventory#inventory} 字段,
 * 绕过 Thermal Expansion 机器的 SideCache 配置限制,避免因为配置面导致中枢 ME 接口无法输入输出.</p>
 *
 * <p>同时读取 {@code TileReconfigurable#slotConfig} 中的
 * {@code allowInsertionSlot}/{@code allowExtractionSlot}, 精确识别输入/输出槽,
 * 避免在输入槽解锁时将材料错误写入输出槽或升级槽.</p>
 */
public class ThermalExpansionMachineHandler implements IRemoteHandler {

    private static final boolean AVAILABLE;
    private static Class<?> TILE_INVENTORY_CLASS;
    private static Class<?> TILE_MACHINE_BASE_CLASS;
    private static Class<?> SLOT_CONFIG_CLASS;
    private static Field INVENTORY_FIELD;
    private static Field PROCESS_REM_FIELD;
    private static Field SLOT_CONFIG_FIELD;
    private static Field ALLOW_INSERTION_SLOT_FIELD;
    private static Field ALLOW_EXTRACTION_SLOT_FIELD;

    static {
        boolean available = false;
        try {
            if (Loader.isModLoaded("thermalexpansion")) {
                TILE_INVENTORY_CLASS = Class.forName("cofh.core.block.TileInventory");
                INVENTORY_FIELD = TILE_INVENTORY_CLASS.getField("inventory");
                try {
                    TILE_MACHINE_BASE_CLASS = Class.forName("cofh.thermalexpansion.block.machine.TileMachineBase");
                    PROCESS_REM_FIELD = TILE_MACHINE_BASE_CLASS.getDeclaredField("processRem");
                    PROCESS_REM_FIELD.setAccessible(true);
                } catch (ClassNotFoundException | NoSuchFieldException e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] ThermalExpansion processRem support not available");
                }
                try {
                    SLOT_CONFIG_CLASS = Class.forName("cofh.core.util.core.SlotConfig");
                    Class<?> tileReconfigurableClass = Class.forName("cofh.core.block.TileReconfigurable");
                    SLOT_CONFIG_FIELD = tileReconfigurableClass.getDeclaredField("slotConfig");
                    SLOT_CONFIG_FIELD.setAccessible(true);
                    ALLOW_INSERTION_SLOT_FIELD = SLOT_CONFIG_CLASS.getField("allowInsertionSlot");
                    ALLOW_EXTRACTION_SLOT_FIELD = SLOT_CONFIG_CLASS.getField("allowExtractionSlot");
                } catch (ClassNotFoundException | NoSuchFieldException e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] ThermalExpansion SlotConfig support not available");
                }
                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Thermal Expansion handler", e);
        }
        AVAILABLE = available;
    }

    @Override
    public boolean canHandle(String blockId) {
        return blockId != null && blockId.startsWith("thermalexpansion:");
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return te instanceof IInventory && TILE_INVENTORY_CLASS.isInstance(te);
    }

    @Override
    public EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.physicalOnly();
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        return isValidTarget(world, pos);
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return false;

        IInventory inv = (IInventory) te;
        ItemStack[] inventory = getInventoryArray(te);
        if (inventory == null) return false;

        boolean[] insertionSlots = getAllowInsertionSlots(te);

        List<ItemStack> toPush = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                toPush.add(stack.copy());
            }
        }

        for (ItemStack stack : toPush) {
            if (!pushItemToInventory(inv, inventory, insertionSlots, stack)) {
                return false;
            }
        }
        markDirty(te);
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return Collections.emptyList();

        IInventory inv = (IInventory) te;
        ItemStack[] inventory = getInventoryArray(te);
        if (inventory == null) return Collections.emptyList();

        boolean[] insertionSlots = getAllowInsertionSlots(te);
        List<ItemStack> reverted = new ArrayList<>();
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack stack = inventory[slot];
            if (!stack.isEmpty() && isInputSlot(inv, insertionSlots, slot)) {
                reverted.add(stack.copy());
                inventory[slot] = ItemStack.EMPTY;
            }
        }
        markDirty(te);
        return reverted;
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return Collections.emptyList();

        IInventory inv = (IInventory) te;
        ItemStack[] inventory = getInventoryArray(te);
        if (inventory == null) return Collections.emptyList();

        boolean[] extractionSlots = getAllowExtractionSlots(te);
        List<ItemStack> cleared = new ArrayList<>();
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack stack = inventory[slot];
            if (!stack.isEmpty() && isOutputSlot(inv, extractionSlots, slot)) {
                cleared.add(stack.copy());
                inventory[slot] = ItemStack.EMPTY;
            }
        }
        markDirty(te);
        return cleared;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
            List<ItemStack> inputs, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return Collections.emptyList();

        IInventory inv = (IInventory) te;
        ItemStack[] inventory = getInventoryArray(te);
        if (inventory == null) return Collections.emptyList();
        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();
        boolean[] extractionSlots = getAllowExtractionSlots(te);

        List<ItemStack> collected = new ArrayList<>();

        // 阶段 1：优先收集匹配预期产物的物品
        if (expectedOutputs != null) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null) continue;
                ItemStack expectedStack = expected.createItemStack();
                for (int slot = 0; slot < inventory.length; slot++) {
                    ItemStack inSlot = inventory[slot];
                    if (inSlot.isEmpty()) continue;
                    if (isInputMaterial(inSlot, inputsSafe)) continue;
                    if (!isOutputSlot(inv, extractionSlots, slot) && !isProbablyOutputSlotFallback(inv, slot)) continue;
                    if (!matchesLoosely(inSlot, expectedStack)) continue;
                    collected.add(inSlot.copy());
                    inventory[slot] = ItemStack.EMPTY;
                }
            }
        }

        // 阶段 2：收集所有非输入材料物品(主要是输出槽)
        for (int slot = 0; slot < inventory.length; slot++) {
            ItemStack stack = inventory[slot];
            if (stack.isEmpty()) continue;
            if (isInputMaterial(stack, inputsSafe)) continue;
            if (!isOutputSlot(inv, extractionSlots, slot) && !isProbablyOutputSlotFallback(inv, slot)) continue;
            collected.add(stack.copy());
            inventory[slot] = ItemStack.EMPTY;
        }

        markDirty(te);
        return collected;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return true;

        // 如果机器仍在处理中,直接返回不空闲
        if (isProcessing(te)) {
            return false;
        }

        IInventory inv = (IInventory) te;
        ItemStack[] inventory = getInventoryArray(te);
        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();
        boolean[] extractionSlots = getAllowExtractionSlots(te);

        // 宽松语义：只要有可收集的产物(非输入材料)或流体,即可收集(支持流水线模式)
        boolean hasProducts = false;
        if (inventory != null) {
            for (int slot = 0; slot < inventory.length; slot++) {
                ItemStack stack = inventory[slot];
                if (stack.isEmpty()) continue;
                if (isInputMaterial(stack, inputsSafe)) continue;
                if (!isOutputSlot(inv, extractionSlots, slot) && !isProbablyOutputSlotFallback(inv, slot)) continue;
                hasProducts = true;
                break;
            }
        }

        return hasProducts || getTankFluid(te) != null;
    }

    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return true;

        // 仍在处理中 → 未完成
        if (isProcessing(te)) {
            return false;
        }

        ItemStack[] inventory = getInventoryArray(te);
        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();
        boolean[] insertionSlots = getAllowInsertionSlots(te);
        boolean[] extractionSlots = getAllowExtractionSlots(te);
        IInventory inv = (IInventory) te;

        if (inventory != null) {
            for (int slot = 0; slot < inventory.length; slot++) {
                ItemStack stack = inventory[slot];
                if (stack.isEmpty()) continue;

                // 还有输入材料 → 未完成
                if (isInputSlot(inv, insertionSlots, slot) && isInputMaterial(stack, inputsSafe)) {
                    return false;
                }

                // 输出槽仍有产物 → 未完成（等待下一次 tick 收集）
                if (isOutputSlot(inv, extractionSlots, slot) && !isInputMaterial(stack, inputsSafe)) {
                    return false;
                }
            }
        }

        // 输入已耗尽、输出槽已空且无产物流体 → 完成
        return getTankFluid(te) == null;
    }

    // ---- Internal helpers ----

    private ItemStack[] getInventoryArray(TileEntity te) {
        try {
            return (ItemStack[]) INVENTORY_FIELD.get(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get ThermalExpansion inventory field", e);
            return null;
        }
    }

    private boolean[] getAllowInsertionSlots(TileEntity te) {
        return getSlotConfigArray(te, ALLOW_INSERTION_SLOT_FIELD);
    }

    private boolean[] getAllowExtractionSlots(TileEntity te) {
        return getSlotConfigArray(te, ALLOW_EXTRACTION_SLOT_FIELD);
    }

    private boolean[] getSlotConfigArray(TileEntity te, Field arrayField) {
        if (SLOT_CONFIG_FIELD == null || arrayField == null) return null;
        try {
            Object slotConfig = SLOT_CONFIG_FIELD.get(te);
            if (slotConfig == null) return null;
            return (boolean[]) arrayField.get(slotConfig);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get ThermalExpansion slot config", e);
            return null;
        }
    }

    private boolean isInputSlot(IInventory inv, boolean[] insertionSlots, int slot) {
        if (insertionSlots != null && slot >= 0 && slot < insertionSlots.length) {
            return insertionSlots[slot];
        }
        return isProbablyInputSlotFallback(inv, slot);
    }

    private boolean isOutputSlot(IInventory inv, boolean[] extractionSlots, int slot) {
        if (extractionSlots != null && slot >= 0 && slot < extractionSlots.length) {
            return extractionSlots[slot];
        }
        return isProbablyOutputSlotFallback(inv, slot);
    }

    private boolean pushItemToInventory(IInventory inv, ItemStack[] inventory, boolean[] insertionSlots, ItemStack stack) {
        for (int slot = 0; slot < inventory.length; slot++) {
            if (!isInputSlot(inv, insertionSlots, slot)) {
                continue;
            }
            ItemStack existing = inventory[slot];
            if (!existing.isEmpty()) {
                if (ItemStack.areItemsEqual(existing, stack)
                        && ItemStack.areItemStackTagsEqual(existing, stack)
                        && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()
                        && inv.isItemValidForSlot(slot, stack)) {
                    existing.grow(stack.getCount());
                    return true;
                }
                continue;
            }
            if (inv.isItemValidForSlot(slot, stack)) {
                inventory[slot] = stack.copy();
                return true;
            }
        }
        return false;
    }

    private boolean isProbablyInputSlotFallback(IInventory inv, int slot) {
        // 简单启发式：前 2/3 槽位视为输入(大多数 TE 机器输入槽在前,输出槽在后)
        return slot < inv.getSizeInventory() * 2 / 3;
    }

    private boolean isProbablyOutputSlotFallback(IInventory inv, int slot) {
        return slot >= inv.getSizeInventory() * 2 / 3;
    }

    private void markDirty(TileEntity te) {
        try {
            te.markDirty();
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] markDirty failed", e);
        }
    }

    private boolean isInputMaterial(ItemStack stack, List<ItemStack> inputs) {
        for (ItemStack input : inputs) {
            if (ItemStack.areItemsEqual(stack, input) && ItemStack.areItemStackTagsEqual(stack, input)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesLoosely(ItemStack actual, ItemStack expected) {
        if (!ItemStack.areItemsEqual(actual, expected)) return false;
        if (!expected.hasTagCompound()) return true;
        return ItemStack.areItemStackTagsEqual(actual, expected);
    }

    private boolean isProcessing(TileEntity te) {
        if (PROCESS_REM_FIELD == null) return false;
        try {
            int processRem = PROCESS_REM_FIELD.getInt(te);
            return processRem > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private FluidStack getTankFluid(TileEntity te) {
        try {
            Method getTankFluid = te.getClass().getMethod("getTankFluid");
            Object result = getTankFluid.invoke(te);
            if (result instanceof FluidStack) {
                return (FluidStack) result;
            }
        } catch (NoSuchMethodException e) {
            // 该机器没有 tank
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get TE tank fluid", e);
        }
        return null;
    }
}
