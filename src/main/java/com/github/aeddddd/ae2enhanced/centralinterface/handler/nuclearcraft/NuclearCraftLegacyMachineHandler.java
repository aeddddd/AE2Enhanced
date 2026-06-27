package com.github.aeddddd.ae2enhanced.centralinterface.handler.nuclearcraft;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * NuclearCraft 机器远程处理器.
 *
 * <p>同时支持 NuclearCraft 2.19a (非重制版) 与 NuclearCraft Overhauled (重制版).
 * 通过反射直接访问 {@code ITileInventory#getInventoryStacks()},绕过 NC 机器的
 * 侧面配置(I/O mode),避免因为配置面导致中枢 ME 接口无法输入输出.</p>
 *
 * <p>所有 NuclearCraft 类均通过 {@link Class#forName(String)} + 反射调用,不直接 import,
 * 保证 NuclearCraft 未安装时本类即使被加载也不会触发 {@link NoClassDefFoundError}.</p>
 */
public class NuclearCraftLegacyMachineHandler implements IRemoteHandler {

    private static final boolean AVAILABLE;
    private static final boolean IS_OVERHAULED;

    // ---------- 重制版 (Overhauled) ----------
    private static Class<?> OH_TILE_ENERGY_PROCESSOR_CLASS;
    private static Class<?> OH_PROCESSOR_CONTAINER_INFO_CLASS;
    private static Field OH_INFO_FIELD;
    private static Field OH_ITEM_INPUT_SIZE_FIELD;
    private static Field OH_ITEM_OUTPUT_SIZE_FIELD;
    private static Field OH_ITEM_INPUT_SLOTS_FIELD;
    private static Field OH_ITEM_OUTPUT_SLOTS_FIELD;

    // ---------- 非重制版 (2.19a) ----------
    private static Class<?> LEGACY_TILE_ITEM_PROCESSOR_CLASS;
    private static Class<?> LEGACY_TILE_FLUID_PROCESSOR_CLASS;
    private static Class<?> LEGACY_TILE_ITEM_FLUID_PROCESSOR_CLASS;

    // ---------- 共享 ----------
    private static Class<?> ITILE_INVENTORY_CLASS;
    private static Method GET_INVENTORY_STACKS_METHOD;
    private static Method IS_ITEM_VALID_FOR_SLOT_METHOD;

    static {
        boolean available = false;
        boolean overhauled = false;
        try {
            if (Loader.isModLoaded("nuclearcraft")) {
                // 先检测是否为重制版：重制版有 TileEnergyProcessor
                try {
                    OH_TILE_ENERGY_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileEnergyProcessor");
                    overhauled = true;
                } catch (ClassNotFoundException e) {
                    overhauled = false;
                }

                if (overhauled) {
                    available = initOverhauled();
                } else {
                    available = initLegacy();
                }
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize NuclearCraft handler", e);
        }
        AVAILABLE = available;
        IS_OVERHAULED = overhauled;
    }

    private static boolean initOverhauled() throws Exception {
        OH_PROCESSOR_CONTAINER_INFO_CLASS = Class.forName("nc.tile.processor.info.ProcessorContainerInfo");
        OH_INFO_FIELD = OH_TILE_ENERGY_PROCESSOR_CLASS.getDeclaredField("info");
        OH_INFO_FIELD.setAccessible(true);
        OH_ITEM_INPUT_SIZE_FIELD = OH_PROCESSOR_CONTAINER_INFO_CLASS.getField("itemInputSize");
        OH_ITEM_OUTPUT_SIZE_FIELD = OH_PROCESSOR_CONTAINER_INFO_CLASS.getField("itemOutputSize");
        OH_ITEM_INPUT_SLOTS_FIELD = OH_PROCESSOR_CONTAINER_INFO_CLASS.getField("itemInputSlots");
        OH_ITEM_OUTPUT_SLOTS_FIELD = OH_PROCESSOR_CONTAINER_INFO_CLASS.getField("itemOutputSlots");

        ITILE_INVENTORY_CLASS = Class.forName("nc.tile.inventory.ITileInventory");
        GET_INVENTORY_STACKS_METHOD = ITILE_INVENTORY_CLASS.getMethod("getInventoryStacks");
        IS_ITEM_VALID_FOR_SLOT_METHOD = ITILE_INVENTORY_CLASS.getMethod("isItemValidForSlot", int.class, ItemStack.class);
        return true;
    }

    private static boolean initLegacy() throws Exception {
        LEGACY_TILE_ITEM_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileItemProcessor");
        LEGACY_TILE_FLUID_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileFluidProcessor");
        LEGACY_TILE_ITEM_FLUID_PROCESSOR_CLASS = Class.forName("nc.tile.processor.TileItemFluidProcessor");

        ITILE_INVENTORY_CLASS = Class.forName("nc.tile.inventory.ITileInventory");
        GET_INVENTORY_STACKS_METHOD = ITILE_INVENTORY_CLASS.getMethod("getInventoryStacks");
        IS_ITEM_VALID_FOR_SLOT_METHOD = ITILE_INVENTORY_CLASS.getMethod("isItemValidForSlot", int.class, ItemStack.class);
        return true;
    }

    @Override
    public boolean canHandle(String blockId) {
        return blockId != null && blockId.startsWith("nuclearcraft:");
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return isProcessorTile(te);
    }

    @Override
    public EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.physicalOnly();
    }

    private boolean isProcessorTile(TileEntity te) {
        if (te == null || !AVAILABLE) return false;
        if (IS_OVERHAULED) {
            return OH_TILE_ENERGY_PROCESSOR_CLASS != null && OH_TILE_ENERGY_PROCESSOR_CLASS.isInstance(te);
        }
        return (LEGACY_TILE_ITEM_PROCESSOR_CLASS != null && LEGACY_TILE_ITEM_PROCESSOR_CLASS.isInstance(te))
                || (LEGACY_TILE_FLUID_PROCESSOR_CLASS != null && LEGACY_TILE_FLUID_PROCESSOR_CLASS.isInstance(te))
                || (LEGACY_TILE_ITEM_FLUID_PROCESSOR_CLASS != null && LEGACY_TILE_ITEM_FLUID_PROCESSOR_CLASS.isInstance(te));
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        return isValidTarget(world, pos);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return false;

        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            int[] inputSlots = getItemInputSlots(te);

            List<ItemStack> toPush = new ArrayList<>();
            for (int i = 0; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    toPush.add(stack.copy());
                }
            }

            for (ItemStack stack : toPush) {
                if (!pushItemToInventory(te, inventory, inputSlots, stack)) {
                    return false;
                }
            }
            markDirty(te);
            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft pushMaterials failed", e);
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return Collections.emptyList();

        List<ItemStack> reverted = new ArrayList<>();
        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            for (int slot : getItemInputSlots(te)) {
                if (slot < 0 || slot >= inventory.size()) continue;
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty()) {
                    reverted.add(stack.copy());
                    inventory.set(slot, ItemStack.EMPTY);
                }
            }
            markDirty(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft revertMaterials failed", e);
        }
        return reverted;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return Collections.emptyList();

        List<ItemStack> cleared = new ArrayList<>();
        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            for (int slot : getItemOutputSlots(te)) {
                if (slot < 0 || slot >= inventory.size()) continue;
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty()) {
                    cleared.add(stack.copy());
                    inventory.set(slot, ItemStack.EMPTY);
                }
            }
            markDirty(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft clearOutputs failed", e);
        }
        return cleared;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
            List<ItemStack> inputs, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return Collections.emptyList();

        List<ItemStack> collected = new ArrayList<>();
        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            int[] outputSlots = getItemOutputSlots(te);
            int[] inputSlots = getItemInputSlots(te);
            List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();

            // 阶段 1：优先收集匹配预期产物的物品
            if (expectedOutputs != null) {
                for (IAEItemStack expected : expectedOutputs) {
                    if (expected == null) continue;
                    ItemStack expectedStack = expected.createItemStack();
                    for (int slot : outputSlots) {
                        if (slot < 0 || slot >= inventory.size()) continue;
                        ItemStack inSlot = inventory.get(slot);
                        if (inSlot.isEmpty()) continue;
                        if (matchesLoosely(inSlot, expectedStack)) {
                            collected.add(inSlot.copy());
                            inventory.set(slot, ItemStack.EMPTY);
                        }
                    }
                }
            }

            // 阶段 2：收集输出槽中所有剩余物品(副产物/容器等)
            for (int slot : outputSlots) {
                if (slot < 0 || slot >= inventory.size()) continue;
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty()) {
                    collected.add(stack.copy());
                    inventory.set(slot, ItemStack.EMPTY);
                }
            }

            // 阶段 3：收集输入槽中未被识别为输入材料的剩余物品(防止机器把产物吐回输入槽)
            for (int slot : inputSlots) {
                if (slot < 0 || slot >= inventory.size()) continue;
                ItemStack stack = inventory.get(slot);
                if (stack.isEmpty()) continue;
                if (!isInputMaterial(stack, inputsSafe)) {
                    collected.add(stack.copy());
                    inventory.set(slot, ItemStack.EMPTY);
                }
            }
            markDirty(te);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft collectProducts failed", e);
        }
        return collected;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return true;

        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            int[] outputSlots = getItemOutputSlots(te);

            // 机器正在处理中 → 暂无可收集产物
            if (isProcessingNC(te)) {
                return false;
            }

            // 宽松语义：只要输出槽有产物,即可收集(支持流水线模式)
            for (int slot : outputSlots) {
                if (slot < 0 || slot >= inventory.size()) continue;
                if (!inventory.get(slot).isEmpty()) {
                    return true;
                }
            }

            return false;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft isIdle failed", e);
            return true;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isProcessorTile(te)) return true;

        try {
            NonNullList<ItemStack> inventory = (NonNullList<ItemStack>) GET_INVENTORY_STACKS_METHOD.invoke(te);
            int[] inputSlots = getItemInputSlots(te);
            int[] outputSlots = getItemOutputSlots(te);
            List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();

            // 仍在处理中 → 未完成
            if (isProcessingNC(te)) {
                return false;
            }

            // 输入槽还有输入材料 → 未处理完
            for (int slot : inputSlots) {
                if (slot < 0 || slot >= inventory.size()) continue;
                ItemStack stack = inventory.get(slot);
                if (!stack.isEmpty() && isInputMaterial(stack, inputsSafe)) {
                    return false;
                }
            }

            // 输入已耗尽,检查是否还有产物残留
            for (int slot : outputSlots) {
                if (slot < 0 || slot >= inventory.size()) continue;
                if (!inventory.get(slot).isEmpty()) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft hasFinished failed", e);
            return true;
        }
    }

    // ---- Internal helpers ----

    private boolean pushItemToInventory(TileEntity te, NonNullList<ItemStack> inventory, int[] inputSlots, ItemStack stack) {
        for (int slot : inputSlots) {
            if (slot < 0 || slot >= inventory.size()) continue;
            ItemStack existing = inventory.get(slot);
            try {
                if (!existing.isEmpty()) {
                    if (ItemStack.areItemsEqual(existing, stack)
                            && ItemStack.areItemStackTagsEqual(existing, stack)
                            && existing.getCount() + stack.getCount() <= existing.getMaxStackSize()) {
                        if ((Boolean) IS_ITEM_VALID_FOR_SLOT_METHOD.invoke(te, slot, stack)) {
                            existing.grow(stack.getCount());
                            return true;
                        }
                    }
                    continue;
                }
                if ((Boolean) IS_ITEM_VALID_FOR_SLOT_METHOD.invoke(te, slot, stack)) {
                    inventory.set(slot, stack.copy());
                    return true;
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] isItemValidForSlot failed for slot {}", slot, e);
            }
        }
        return false;
    }

    private int[] getItemInputSlots(TileEntity te) {
        if (IS_OVERHAULED) {
            int[] slots = getOverhauledIntArray(te, OH_ITEM_INPUT_SLOTS_FIELD);
            if (slots != null) return slots;
            int size = getOverhauledInt(te, OH_ITEM_INPUT_SIZE_FIELD, 0);
            return buildRange(0, size);
        }
        int size = getTileIntField(te, "itemInputSize", 0);
        return buildRange(0, size);
    }

    private int[] getItemOutputSlots(TileEntity te) {
        if (IS_OVERHAULED) {
            int[] slots = getOverhauledIntArray(te, OH_ITEM_OUTPUT_SLOTS_FIELD);
            if (slots != null) return slots;
            int inputSize = getOverhauledInt(te, OH_ITEM_INPUT_SIZE_FIELD, 0);
            int outputSize = getOverhauledInt(te, OH_ITEM_OUTPUT_SIZE_FIELD, 0);
            return buildRange(inputSize, outputSize);
        }
        int inputSize = getTileIntField(te, "itemInputSize", 0);
        int outputSize = getTileIntField(te, "itemOutputSize", 0);
        return buildRange(inputSize, outputSize);
    }

    private int[] buildRange(int start, int length) {
        if (length <= 0) return new int[0];
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = start + i;
        }
        return result;
    }

    private int getOverhauledInt(TileEntity te, Field field, int fallback) {
        try {
            Object info = OH_INFO_FIELD.get(te);
            if (info == null || field == null) return fallback;
            return field.getInt(info);
        } catch (Exception e) {
            return fallback;
        }
    }

    private int[] getOverhauledIntArray(TileEntity te, Field field) {
        try {
            Object info = OH_INFO_FIELD.get(te);
            if (info == null || field == null) return null;
            Object value = field.get(info);
            if (value instanceof int[]) {
                return (int[]) value;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get NC Overhauled int array", e);
        }
        return null;
    }

    private int getTileIntField(TileEntity te, String name, int fallback) {
        try {
            return te.getClass().getField(name).getInt(te);
        } catch (Exception e) {
            return fallback;
        }
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

    private boolean isProcessingNC(TileEntity te) {
        try {
            Method isProcessing = te.getClass().getMethod("isProcessing");
            return (Boolean) isProcessing.invoke(te);
        } catch (NoSuchMethodException e) {
            // Overhauled 使用 getIsProcessing
            try {
                Method getIsProcessing = te.getClass().getMethod("getIsProcessing");
                return (Boolean) getIsProcessing.invoke(te);
            } catch (NoSuchMethodException e2) {
                return false;
            } catch (Exception e2) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to get NC Overhauled processing state", e2);
                return false;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get NC processing state", e);
            return false;
        }
    }
}
