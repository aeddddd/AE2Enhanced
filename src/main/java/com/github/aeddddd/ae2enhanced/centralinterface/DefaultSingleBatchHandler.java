package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 默认单批次处理器(通用 Fallback).
 *
 * <p>当没有特定 {@link IRemoteHandler} 匹配目标方块类型时,使用此处理器.
 * 适用场景：熔炉、通用加工机器等具备 {@link IItemHandler} 能力、
 * 物品插入后自动开始处理、处理完成后产物留在输出槽的设备.</p>
 *
 * <p>多目标隔离：输入材料快照由 {@link DualityCentralInterface} 按 TargetBinding 维护,
 * 通过 {@code inputs} 参数传入,避免多个中枢接口共享单例 handler 时的状态覆盖.</p>
 */
public class DefaultSingleBatchHandler implements IRemoteHandler {

    @Override
    public boolean canHandle(String blockId) {
        return false; // 不主动匹配任何 blockId,由 HandlerRegistry 作为兜底 fallback
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return false;
        }
        for (EnumFacing face : EnumFacing.values()) {
            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face)) {
                return true;
            }
        }
        return false;
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
        if (te == null) {
            return false;
        }

        List<ItemStack> toPush = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                toPush.add(stack.copy());
            }
        }

        for (ItemStack stack : toPush) {
            ItemStack remaining = pushItemToTarget(te, stack);
            if (!remaining.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return true;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return Collections.emptyList();
        }
        List<ItemStack> inputsSafe = session != null ? session.getInputs() : Collections.emptyList();
        List<ItemStack> reverted = new ArrayList<>();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                // 只回退本次推送的输入材料，避免清空升级/电池槽
                if (!isInputMaterial(inSlot, inputsSafe)) continue;
                ItemStack extracted = handler.extractItem(slot, inSlot.getCount(), false);
                if (!extracted.isEmpty()) {
                    reverted.add(extracted);
                }
            }
        }
        return reverted;
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return Collections.emptyList();
        }
        List<ItemStack> inputsSafe = session != null ? session.getInputs() : Collections.emptyList();
        List<ItemStack> expectedSafe = getExpectedItemStacks(session);
        List<ItemStack> cleared = new ArrayList<>();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                // 优先收集预期产物；若无法匹配预期产物，则收集非输入物品。
                // 升级/电池等物品通常不在 expectedOutputs 也不在 inputs 中，借此避免被清空。
                if (!isInputMaterial(inSlot, inputsSafe)
                        && !matchesAnyExpected(inSlot, expectedSafe)) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, inSlot.getCount(), false);
                if (!extracted.isEmpty()) {
                    cleared.add(extracted);
                }
            }
        }
        return cleared;
    }

    private List<ItemStack> getExpectedItemStacks(TargetSession session) {
        if (session == null || session.getExpectedOutputs() == null) {
            return Collections.emptyList();
        }
        List<ItemStack> list = new ArrayList<>();
        for (IAEItemStack stack : session.getExpectedOutputs()) {
            if (stack != null) {
                list.add(stack.createItemStack());
            }
        }
        return list;
    }

    private boolean matchesAnyExpected(ItemStack stack, List<ItemStack> expectedList) {
        for (ItemStack expected : expectedList) {
            if (matchesLoosely(stack, expected)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs,
            List<ItemStack> inputs, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return Collections.emptyList();
        }

        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();
        List<ItemStack> collected = new ArrayList<>();
        Set<String> visitedSlots = new HashSet<>();

        // 阶段 1：优先收集匹配预期产物的物品(NBT 放宽)
        if (expectedOutputs != null && expectedOutputs.length > 0) {
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null) continue;
                ItemStack expectedStack = expected.createItemStack();
                ItemStack result = collectExpectedItem(te, expectedStack, inputsSafe, visitedSlots);
                if (!result.isEmpty()) {
                    collected.add(result);
                }
            }
        }

        // 阶段 2：收集所有其他非输入材料的可抽取物品(副产物、残余、容器物品等)
        List<ItemStack> extras = collectAllNonInputItems(te, inputsSafe, visitedSlots);
        collected.addAll(extras);

        return collected;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return true;
        }

        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();

        // 宽松语义：只要有可抽取的非输入物品(产物),即可收集(支持流水线模式)
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                ItemStack simulated = handler.extractItem(slot, 1, true);
                if (simulated.isEmpty()) continue;
                if (!isInputMaterial(inSlot, inputsSafe)) {
                    return true; // 发现产物,可以收集
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasFinished(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) {
            return true;
        }

        List<ItemStack> inputsSafe = inputs != null ? inputs : Collections.emptyList();

        // 检查是否还有输入材料未消耗完
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                ItemStack simulated = handler.extractItem(slot, 1, true);
                if (simulated.isEmpty()) continue;
                if (isInputMaterial(inSlot, inputsSafe)) {
                    return false; // 还有输入材料,未完成
                }
            }
        }

        // 输入已耗尽,再检查是否还有产物残留
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                ItemStack simulated = handler.extractItem(slot, 1, true);
                if (simulated.isEmpty()) continue;
                if (!isInputMaterial(inSlot, inputsSafe)) {
                    return false; // 还有产物,未真正完成
                }
            }
        }

        return true;
    }

    // ---- Internal helpers ----

    private ItemStack pushItemToTarget(TileEntity target, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
                remaining = handler.insertItem(slot, remaining, false);
            }
            if (remaining.isEmpty()) {
                break;
            }
        }
        return remaining;
    }

    private ItemStack collectExpectedItem(TileEntity target, ItemStack expected,
                                          List<ItemStack> inputs, Set<String> visitedSlots) {
        ItemStack collected = ItemStack.EMPTY;
        int remainingAmount = expected.getCount();

        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;

            for (int slot = 0; slot < handler.getSlots() && remainingAmount > 0; slot++) {
                String slotKey = face.ordinal() + ":" + slot;
                if (visitedSlots.contains(slotKey)) continue;

                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                if (isInputMaterial(inSlot, inputs)) continue;

                if (!matchesLoosely(inSlot, expected)) continue;

                int toExtract = Math.min(remainingAmount, inSlot.getCount());
                ItemStack extracted = handler.extractItem(slot, toExtract, false);
                if (!extracted.isEmpty()) {
                    if (collected.isEmpty()) {
                        collected = extracted.copy();
                    } else {
                        collected.grow(extracted.getCount());
                    }
                    remainingAmount -= extracted.getCount();

                    // 只有当槽位被完全清空时才标记为已访问,
                    // 否则剩余物品应由 collectAllNonInputItems 继续回收
                    ItemStack afterExtract = handler.getStackInSlot(slot);
                    if (afterExtract.isEmpty()) {
                        visitedSlots.add(slotKey);
                    }
                }
            }
            if (remainingAmount <= 0) {
                break;
            }
        }
        return collected;
    }

    private List<ItemStack> collectAllNonInputItems(TileEntity target,
                                                    List<ItemStack> inputs, Set<String> visitedSlots) {
        List<ItemStack> collected = new ArrayList<>();
        for (EnumFacing face : EnumFacing.values()) {
            IItemHandler handler = target.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                String slotKey = face.ordinal() + ":" + slot;
                if (visitedSlots.contains(slotKey)) continue;

                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty()) continue;
                if (isInputMaterial(inSlot, inputs)) continue;

                // 循环提取直到槽位真正清空，防止某些 IItemHandler 单次提取上限导致产物残留
                boolean anyExtracted = false;
                while (true) {
                    ItemStack current = handler.getStackInSlot(slot);
                    if (current.isEmpty()) break;
                    ItemStack extracted = handler.extractItem(slot, current.getCount(), false);
                    if (extracted.isEmpty()) break;
                    collected.add(extracted);
                    anyExtracted = true;
                }
                if (anyExtracted) {
                    visitedSlots.add(slotKey);
                }
            }
        }
        return collected;
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
        if (!ItemStack.areItemsEqual(actual, expected)) {
            return false;
        }
        if (!expected.hasTagCompound()) {
            return true;
        }
        return ItemStack.areItemStackTagsEqual(actual, expected);
    }
}
