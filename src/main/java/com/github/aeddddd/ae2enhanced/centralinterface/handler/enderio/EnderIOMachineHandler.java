package com.github.aeddddd.ae2enhanced.centralinterface.handler.enderio;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.fml.common.Loader;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * Ender IO 机器远程处理器.
 *
 * <p>基于 Ender IO 5.3.72 + EnderCore 0.5.78 的实际类结构实现,通过反射访问：</p>
 * <ul>
 *   <li>legacy 机器 {@code AbstractInventoryMachineEntity} 的 {@code inventory[]} 与 {@code SlotDefinition}</li>
 *   <li>capability 机器 {@code AbstractCapabilityMachineEntity} 的 {@code EnderInventory} 及其 {@code Type.INPUT/OUTPUT} 视图</li>
 *   <li>{@code AbstractMachineEntity} 的 {@code isActive()} 与 {@code getOutputQueue()}</li>
 * </ul>
 *
 * <p>所有第三方类均通过 {@link Class#forName(String)} + 反射调用,不直接 import,
 * 保证 Ender IO 未安装时本类即使被加载也不会触发 {@link NoClassDefFoundError}.</p>
 */
public class EnderIOMachineHandler implements IRemoteHandler {

    private static final boolean AVAILABLE;

    // ---- Ender IO classes ----
    private static Class<?> ABSTRACT_MACHINE_ENTITY_CLASS;
    private static Class<?> ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS;
    private static Class<?> ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS;
    private static Class<?> SLOT_DEFINITION_CLASS;

    // ---- EnderCore classes ----
    private static Class<?> ENDER_INVENTORY_CLASS;
    private static Class<?> ENDER_INVENTORY_TYPE_CLASS;

    // ---- Methods ----
    private static Method METHOD_IS_ACTIVE;
    private static Method METHOD_GET_OUTPUT_QUEUE;
    private static Method METHOD_GET_SLOT_DEFINITION;
    private static Method METHOD_IS_MACHINE_ITEM_VALID;
    private static Method METHOD_IS_INPUT_SLOT;
    private static Method METHOD_IS_OUTPUT_SLOT;
    private static Method METHOD_GET_INVENTORY_STACK_LIMIT;
    private static Method METHOD_GET_INVENTORY;
    private static Method METHOD_GET_VIEW;
    private static Method METHOD_IS_VALID_INPUT;
    private static Method METHOD_IS_VALID_OUTPUT;

    // ---- Fields ----
    private static Field FIELD_LEGACY_INVENTORY;
    private static Field FIELD_MIN_INPUT_SLOT;
    private static Field FIELD_MAX_INPUT_SLOT;
    private static Field FIELD_MIN_OUTPUT_SLOT;
    private static Field FIELD_MAX_OUTPUT_SLOT;

    // ---- Enum constants ----
    private static Object ENDER_INVENTORY_TYPE_INPUT;
    private static Object ENDER_INVENTORY_TYPE_OUTPUT;

    static {
        boolean available = false;
        try {
            if (Loader.isModLoaded("enderio")) {
                // 核心基类
                ABSTRACT_MACHINE_ENTITY_CLASS = Class.forName("crazypants.enderio.base.machine.base.te.AbstractMachineEntity");
                METHOD_IS_ACTIVE = ABSTRACT_MACHINE_ENTITY_CLASS.getMethod("isActive");
                METHOD_GET_OUTPUT_QUEUE = ABSTRACT_MACHINE_ENTITY_CLASS.getDeclaredMethod("getOutputQueue");
                METHOD_GET_OUTPUT_QUEUE.setAccessible(true);

                // legacy 机器
                try {
                    ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS = Class.forName("crazypants.enderio.base.machine.baselegacy.AbstractInventoryMachineEntity");
                    SLOT_DEFINITION_CLASS = Class.forName("crazypants.enderio.base.machine.baselegacy.SlotDefinition");
                    FIELD_LEGACY_INVENTORY = ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.getDeclaredField("inventory");
                    FIELD_LEGACY_INVENTORY.setAccessible(true);
                    METHOD_GET_SLOT_DEFINITION = ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.getMethod("getSlotDefinition");
                    METHOD_IS_MACHINE_ITEM_VALID = ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.getMethod("isMachineItemValidForSlot", int.class, ItemStack.class);
                    METHOD_IS_INPUT_SLOT = SLOT_DEFINITION_CLASS.getMethod("isInputSlot", int.class);
                    METHOD_IS_OUTPUT_SLOT = SLOT_DEFINITION_CLASS.getMethod("isOutputSlot", int.class);
                    METHOD_GET_INVENTORY_STACK_LIMIT = ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.getMethod("getInventoryStackLimit", int.class);
                    FIELD_MIN_INPUT_SLOT = SLOT_DEFINITION_CLASS.getField("minInputSlot");
                    FIELD_MAX_INPUT_SLOT = SLOT_DEFINITION_CLASS.getField("maxInputSlot");
                    FIELD_MIN_OUTPUT_SLOT = SLOT_DEFINITION_CLASS.getField("minOutputSlot");
                    FIELD_MAX_OUTPUT_SLOT = SLOT_DEFINITION_CLASS.getField("maxOutputSlot");
                } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] EIO legacy machine support not available: {}", e.toString());
                }

                // capability 机器
                try {
                    ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS = Class.forName("crazypants.enderio.base.machine.base.te.AbstractCapabilityMachineEntity");
                    METHOD_GET_INVENTORY = ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.getMethod("getInventory");
                    METHOD_IS_VALID_INPUT = ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.getMethod("isValidInput", ItemStack.class);
                    METHOD_IS_VALID_OUTPUT = ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.getMethod("isValidOutput", ItemStack.class);

                    ENDER_INVENTORY_CLASS = Class.forName("com.enderio.core.common.inventory.EnderInventory");
                    ENDER_INVENTORY_TYPE_CLASS = Class.forName("com.enderio.core.common.inventory.EnderInventory$Type");
                    METHOD_GET_VIEW = ENDER_INVENTORY_CLASS.getMethod("getView", ENDER_INVENTORY_TYPE_CLASS);
                    ENDER_INVENTORY_TYPE_INPUT = Enum.valueOf((Class<Enum>) ENDER_INVENTORY_TYPE_CLASS, "INPUT");
                    ENDER_INVENTORY_TYPE_OUTPUT = Enum.valueOf((Class<Enum>) ENDER_INVENTORY_TYPE_CLASS, "OUTPUT");
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    AE2Enhanced.LOGGER.debug("[AE2E] EIO capability machine support not available: {}", e.toString());
                }

                available = true;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize Ender IO handler", e);
        }
        AVAILABLE = available;
    }

    @Override
    public boolean canHandle(String blockId) {
        return blockId != null && blockId.startsWith("enderio:");
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        return AVAILABLE && te != null && ABSTRACT_MACHINE_ENTITY_CLASS.isInstance(te);
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

        List<ItemStack> toPush = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                toPush.add(stack.copy());
            }
        }
        if (toPush.isEmpty()) return true;

        // 优先走内部库存（绕过 IoMode），失败再回退到六面 capability
        if (pushToInternal(te, toPush)) {
            te.markDirty();
            return true;
        }
        if (pushToAnyFace(te, toPush)) {
            return true;
        }
        return false;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return Collections.emptyList();

        List<ItemStack> reverted = new ArrayList<>();
        reverted.addAll(extractFromInternal(te, SlotRole.INPUT, Integer.MAX_VALUE));
        reverted.addAll(drainOutputQueue(te));
        if (!reverted.isEmpty()) {
            te.markDirty();
        }
        return reverted;
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return Collections.emptyList();

        List<ItemStack> cleared = new ArrayList<>();
        cleared.addAll(extractFromInternal(te, SlotRole.OUTPUT, Integer.MAX_VALUE));
        cleared.addAll(drainOutputQueue(te));
        if (!cleared.isEmpty()) {
            te.markDirty();
        }
        return cleared;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
            List<ItemStack> inputs, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return Collections.emptyList();

        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();
        List<ItemStack> collected = new ArrayList<>();

        // 阶段 1：优先收集预期产物（从输出槽）
        if (expectedOutputs != null) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null) continue;
                ItemStack expectedStack = expected.createItemStack();
                collected.addAll(extractMatchingFromInternal(te, SlotRole.OUTPUT, expectedStack, inputsSafe));
            }
        }

        // 阶段 2：收集输出槽中剩余的非输入物品
        List<ItemStack> outputs = extractFromInternal(te, SlotRole.OUTPUT, Integer.MAX_VALUE);
        for (ItemStack stack : outputs) {
            if (!isInputMaterial(stack, inputsSafe)) {
                collected.add(stack);
            }
        }

        // 阶段 3：收集输出队列
        collected.addAll(drainOutputQueue(te));

        if (!collected.isEmpty()) {
            te.markDirty();
        }
        return collected;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return true;

        // 机器正在活跃处理 → 暂无可收集产物
        if (isActive(te)) {
            return false;
        }

        // 宽松语义：只要输出槽/队列中有产物,即可收集(支持流水线模式)
        return hasAnyOutput(te);
    }

    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (!isValidTarget(world, pos)) return true;

        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();

        // 仍在处理中 → 未完成
        if (isActive(te)) {
            return false;
        }

        // 输入槽还有材料 → 未处理完
        if (hasAnyInput(te, inputsSafe)) {
            return false;
        }

        // 输入已耗尽且无产物 → 完成
        return !hasAnyOutput(te);
    }

    // ---- Internal helpers ----

    private enum SlotRole {
        INPUT, OUTPUT
    }

    private boolean pushToInternal(TileEntity te, List<ItemStack> toPush) {
        if (ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.isInstance(te)) {
            IItemHandler inputView = getCapabilityView(te, ENDER_INVENTORY_TYPE_INPUT);
            if (inputView != null) {
                return pushToHandler(inputView, toPush);
            }
        }
        if (ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.isInstance(te)) {
            return pushToLegacyInputSlots(te, toPush);
        }
        return false;
    }

    private boolean pushToAnyFace(TileEntity te, List<ItemStack> toPush) {
        List<ItemStack> remaining = new ArrayList<>(toPush);
        for (EnumFacing face : EnumFacing.values()) {
            if (remaining.isEmpty()) break;
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            pushToHandler(handler, remaining);
        }
        return remaining.isEmpty();
    }

    private boolean pushToHandler(IItemHandler handler, List<ItemStack> toPush) {
        for (int i = 0; i < toPush.size(); ) {
            ItemStack stack = toPush.get(i);
            ItemStack remaining = stack.copy();
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            if (remaining.isEmpty()) {
                toPush.remove(i);
            } else {
                toPush.set(i, remaining);
                i++;
            }
        }
        return toPush.isEmpty();
    }

    private boolean pushToLegacyInputSlots(TileEntity te, List<ItemStack> toPush) {
        try {
            ItemStack[] inventory = (ItemStack[]) FIELD_LEGACY_INVENTORY.get(te);
            if (inventory == null) return false;

            SlotRange range = getLegacySlotRange(te, SlotRole.INPUT);
            for (int i = 0; i < toPush.size(); ) {
                ItemStack remaining = toPush.get(i).copy();
                for (int slot = range.min; slot < range.max && !remaining.isEmpty(); slot++) {
                    if (!isLegacyInputSlot(te, slot)) continue;
                    if (!isMachineItemValid(te, slot, remaining)) continue;
                    remaining = insertIntoLegacySlot(te, inventory, slot, remaining);
                }
                if (remaining.isEmpty()) {
                    toPush.remove(i);
                } else {
                    toPush.set(i, remaining);
                    i++;
                }
            }
            return toPush.isEmpty();
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to push to EIO legacy machine", e);
            return false;
        }
    }

    private ItemStack insertIntoLegacySlot(TileEntity te, ItemStack[] inventory, int slot, ItemStack stack) {
        if (slot < 0 || slot >= inventory.length || stack.isEmpty()) return stack;
        ItemStack existing = inventory[slot];
        int slotLimit = getLegacySlotLimit(te, slot);
        if (existing.isEmpty()) {
            ItemStack insert = stack.copy();
            insert.setCount(Math.min(insert.getCount(), slotLimit));
            inventory[slot] = insert;
            if (insert.getCount() == stack.getCount()) {
                return ItemStack.EMPTY;
            }
            ItemStack remaining = stack.copy();
            remaining.shrink(insert.getCount());
            return remaining;
        }
        if (ItemStack.areItemsEqual(existing, stack) && ItemStack.areItemStackTagsEqual(existing, stack)) {
            int max = Math.min(existing.getMaxStackSize(), slotLimit);
            int canAdd = max - existing.getCount();
            if (canAdd > 0) {
                int add = Math.min(canAdd, stack.getCount());
                existing.grow(add);
                if (add == stack.getCount()) {
                    return ItemStack.EMPTY;
                }
                ItemStack remaining = stack.copy();
                remaining.shrink(add);
                return remaining;
            }
        }
        return stack;
    }

    private List<ItemStack> extractFromInternal(TileEntity te, SlotRole role, int maxAmount) {
        if (ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.isInstance(te)) {
            Object type = role == SlotRole.INPUT ? ENDER_INVENTORY_TYPE_INPUT : ENDER_INVENTORY_TYPE_OUTPUT;
            IItemHandler view = getCapabilityView(te, type);
            if (view != null) {
                return extractAllFromHandler(view, maxAmount);
            }
        }
        if (ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.isInstance(te)) {
            return extractFromLegacySlots(te, role, maxAmount);
        }
        return Collections.emptyList();
    }

    private List<ItemStack> extractMatchingFromInternal(TileEntity te, SlotRole role, ItemStack expected,
                                                        List<ItemStack> inputsSafe) {
        List<ItemStack> result = new ArrayList<>();
        if (ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.isInstance(te)) {
            Object type = role == SlotRole.INPUT ? ENDER_INVENTORY_TYPE_INPUT : ENDER_INVENTORY_TYPE_OUTPUT;
            IItemHandler view = getCapabilityView(te, type);
            if (view != null) {
                for (int slot = 0; slot < view.getSlots(); slot++) {
                    ItemStack inSlot = view.getStackInSlot(slot);
                    if (inSlot.isEmpty()) continue;
                    if (isInputMaterial(inSlot, inputsSafe)) continue;
                    if (!matchesLoosely(inSlot, expected)) continue;
                    ItemStack extracted = view.extractItem(slot, inSlot.getCount(), false);
                    if (!extracted.isEmpty()) {
                        result.add(extracted);
                    }
                }
            }
            return result;
        }
        if (ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.isInstance(te)) {
            try {
                ItemStack[] inventory = (ItemStack[]) FIELD_LEGACY_INVENTORY.get(te);
                if (inventory == null) return result;
                SlotRange range = getLegacySlotRange(te, role);
                for (int slot = range.min; slot < range.max; slot++) {
                    ItemStack inSlot = inventory[slot];
                    if (inSlot == null || inSlot.isEmpty()) continue;
                    if (isInputMaterial(inSlot, inputsSafe)) continue;
                    if (!matchesLoosely(inSlot, expected)) continue;
                    result.add(inSlot.copy());
                    inventory[slot] = ItemStack.EMPTY;
                }
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to extract matching from EIO legacy machine", e);
            }
        }
        return result;
    }

    private List<ItemStack> extractFromLegacySlots(TileEntity te, SlotRole role, int maxAmount) {
        List<ItemStack> result = new ArrayList<>();
        try {
            ItemStack[] inventory = (ItemStack[]) FIELD_LEGACY_INVENTORY.get(te);
            if (inventory == null) return result;
            SlotRange range = getLegacySlotRange(te, role);
            for (int slot = range.min; slot < range.max && maxAmount > 0; slot++) {
                ItemStack inSlot = inventory[slot];
                if (inSlot == null || inSlot.isEmpty()) continue;
                int take = Math.min(inSlot.getCount(), maxAmount);
                ItemStack copy = inSlot.copy();
                copy.setCount(take);
                result.add(copy);
                inSlot.shrink(take);
                if (inSlot.isEmpty()) {
                    inventory[slot] = ItemStack.EMPTY;
                }
                maxAmount -= take;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to extract from EIO legacy machine", e);
        }
        return result;
    }

    private IItemHandler getCapabilityView(TileEntity te, Object typeConstant) {
        if (METHOD_GET_INVENTORY == null || METHOD_GET_VIEW == null) return null;
        try {
            Object inventory = METHOD_GET_INVENTORY.invoke(te);
            if (inventory == null) return null;
            Object view = METHOD_GET_VIEW.invoke(inventory, typeConstant);
            if (view instanceof IItemHandler) {
                return (IItemHandler) view;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get EIO inventory view", e);
        }
        return null;
    }

    private SlotRange getLegacySlotRange(TileEntity te, SlotRole role) {
        try {
            Object slotDef = METHOD_GET_SLOT_DEFINITION.invoke(te);
            if (slotDef == null) {
                return new SlotRange(0, Integer.MAX_VALUE);
            }
            if (role == SlotRole.INPUT) {
                int min = FIELD_MIN_INPUT_SLOT.getInt(slotDef);
                int max = FIELD_MAX_INPUT_SLOT.getInt(slotDef) + 1;
                return new SlotRange(min, max);
            } else {
                int min = FIELD_MIN_OUTPUT_SLOT.getInt(slotDef);
                int max = FIELD_MAX_OUTPUT_SLOT.getInt(slotDef) + 1;
                return new SlotRange(min, max);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get EIO legacy slot range", e);
            return new SlotRange(0, Integer.MAX_VALUE);
        }
    }

    private boolean isLegacyInputSlot(TileEntity te, int slot) {
        if (METHOD_IS_INPUT_SLOT == null) return true;
        try {
            Object slotDef = METHOD_GET_SLOT_DEFINITION.invoke(te);
            if (slotDef == null) return true;
            return (boolean) METHOD_IS_INPUT_SLOT.invoke(slotDef, slot);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean isMachineItemValid(TileEntity te, int slot, ItemStack stack) {
        if (METHOD_IS_MACHINE_ITEM_VALID == null) return true;
        try {
            return (boolean) METHOD_IS_MACHINE_ITEM_VALID.invoke(te, slot, stack);
        } catch (Exception e) {
            return true;
        }
    }

    private int getLegacySlotLimit(TileEntity te, int slot) {
        if (METHOD_GET_INVENTORY_STACK_LIMIT == null) return 64;
        try {
            return (int) METHOD_GET_INVENTORY_STACK_LIMIT.invoke(te, slot);
        } catch (Exception e) {
            return 64;
        }
    }

    private boolean hasAnyOutput(TileEntity te) {
        if (!getOutputQueue(te).isEmpty()) {
            return true;
        }
        return hasAnyInSlotRole(te, SlotRole.OUTPUT);
    }

    private boolean hasAnyInput(TileEntity te, List<ItemStack> inputsSafe) {
        return hasAnyInSlotRole(te, SlotRole.INPUT, inputsSafe);
    }

    private boolean hasAnyInSlotRole(TileEntity te, SlotRole role) {
        return hasAnyInSlotRole(te, role, Collections.emptyList());
    }

    private boolean hasAnyInSlotRole(TileEntity te, SlotRole role, List<ItemStack> inputsSafe) {
        if (ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_CAPABILITY_MACHINE_ENTITY_CLASS.isInstance(te)) {
            Object type = role == SlotRole.INPUT ? ENDER_INVENTORY_TYPE_INPUT : ENDER_INVENTORY_TYPE_OUTPUT;
            IItemHandler view = getCapabilityView(te, type);
            if (view != null) {
                for (int slot = 0; slot < view.getSlots(); slot++) {
                    ItemStack inSlot = view.getStackInSlot(slot);
                    if (inSlot.isEmpty()) continue;
                    if (role == SlotRole.INPUT && isInputMaterial(inSlot, inputsSafe)) continue;
                    return true;
                }
                return false;
            }
        }
        if (ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS != null
                && ABSTRACT_INVENTORY_MACHINE_ENTITY_CLASS.isInstance(te)) {
            try {
                ItemStack[] inventory = (ItemStack[]) FIELD_LEGACY_INVENTORY.get(te);
                if (inventory == null) return false;
                SlotRange range = getLegacySlotRange(te, role);
                for (int slot = range.min; slot < range.max; slot++) {
                    ItemStack inSlot = inventory[slot];
                    if (inSlot == null || inSlot.isEmpty()) continue;
                    if (role == SlotRole.INPUT && isInputMaterial(inSlot, inputsSafe)) continue;
                    return true;
                }
                return false;
            } catch (Exception e) {
                AE2Enhanced.LOGGER.debug("[AE2E] Failed to check EIO legacy slot role", e);
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> getOutputQueue(TileEntity te) {
        if (METHOD_GET_OUTPUT_QUEUE == null) return Collections.emptyList();
        try {
            Object queue = METHOD_GET_OUTPUT_QUEUE.invoke(te);
            if (queue instanceof List) {
                List<ItemStack> result = new ArrayList<>();
                for (Object o : (List<?>) queue) {
                    if (o instanceof ItemStack) {
                        result.add(((ItemStack) o).copy());
                    }
                }
                return result;
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to get EIO output queue", e);
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> drainOutputQueue(TileEntity te) {
        if (METHOD_GET_OUTPUT_QUEUE == null) return Collections.emptyList();
        try {
            Object queue = METHOD_GET_OUTPUT_QUEUE.invoke(te);
            if (!(queue instanceof List)) return Collections.emptyList();
            List<ItemStack> result = new ArrayList<>();
            for (Object o : (List<?>) queue) {
                if (o instanceof ItemStack) {
                    result.add(((ItemStack) o).copy());
                }
            }
            ((List<?>) queue).clear();
            return result;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to drain EIO output queue", e);
        }
        return Collections.emptyList();
    }

    private boolean isActive(TileEntity te) {
        if (METHOD_IS_ACTIVE == null) return false;
        try {
            Object result = METHOD_IS_ACTIVE.invoke(te);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.debug("[AE2E] Failed to check EIO machine active state", e);
            return false;
        }
    }

    private List<ItemStack> extractAllFromHandler(IItemHandler handler, int maxAmount) {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < handler.getSlots() && maxAmount > 0; slot++) {
            ItemStack inSlot = handler.getStackInSlot(slot);
            if (inSlot.isEmpty()) continue;
            int take = Math.min(inSlot.getCount(), maxAmount);
            ItemStack extracted = handler.extractItem(slot, take, false);
            if (!extracted.isEmpty()) {
                result.add(extracted);
                maxAmount -= extracted.getCount();
            }
        }
        return result;
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

    private static final class SlotRange {
        final int min;
        final int max;
        SlotRange(int min, int max) {
            this.min = min;
            this.max = max;
        }
    }
}
