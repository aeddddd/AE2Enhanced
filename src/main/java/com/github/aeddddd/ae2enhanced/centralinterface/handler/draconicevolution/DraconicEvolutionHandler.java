package com.github.aeddddd.ae2enhanced.centralinterface.handler.draconicevolution;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import com.github.aeddddd.ae2enhanced.centralinterface.HandlerCapabilities;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Draconic Evolution 聚合核心 + 注入器处理器.
 *
 * <p>结构：中心 FusionCraftingCore,周围16格范围内放置 CraftingInjector.
 * slot 0 → Core 主材料,slot 1+ → Injectors 辅助材料(各1个).</p>
 */
public class DraconicEvolutionHandler implements IRemoteHandler {

    private static final String CORE_ID = "draconicevolution:fusion_crafting_core";

    // 反射缓存
    private static Class<?> CLASS_CORE;
    private static Class<?> CLASS_INJECTOR;
    private static Class<?> CLASS_ICRAFTING_INJECTOR;
    private static Class<?> CLASS_MANAGED_BOOL;
    private static Class<?> CLASS_MANAGED_SHORT;
    private static Method METHOD_ATTEMPT_START;
    private static Method METHOD_UPDATE_INJECTORS;
    private static Method METHOD_GET_INJECTORS;
    private static Method METHOD_GET_STACK_IN_PEDESTAL;
    private static Method METHOD_SET_STACK_IN_PEDESTAL;
    private static Field FIELD_IS_CRAFTING;
    private static Field FIELD_CRAFTING_STAGE;
    private static Field FIELD_VALUE_BOOL;
    private static Field FIELD_VALUE_SHORT;
    private static boolean reflectionReady = false;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_CORE = Class.forName("com.brandon3055.draconicevolution.blocks.tileentity.TileFusionCraftingCore");
            CLASS_INJECTOR = Class.forName("com.brandon3055.draconicevolution.blocks.tileentity.TileCraftingInjector");
            CLASS_ICRAFTING_INJECTOR = Class.forName("com.brandon3055.draconicevolution.api.fusioncrafting.ICraftingInjector");
            CLASS_MANAGED_BOOL = Class.forName("com.brandon3055.brandonscore.lib.datamanager.ManagedBool");
            CLASS_MANAGED_SHORT = Class.forName("com.brandon3055.brandonscore.lib.datamanager.ManagedShort");

            METHOD_ATTEMPT_START = CLASS_CORE.getMethod("attemptStartCrafting");
            METHOD_UPDATE_INJECTORS = CLASS_CORE.getMethod("updateInjectors");
            METHOD_GET_INJECTORS = CLASS_CORE.getMethod("getInjectors");
            METHOD_GET_STACK_IN_PEDESTAL = CLASS_ICRAFTING_INJECTOR.getMethod("getStackInPedestal");
            METHOD_SET_STACK_IN_PEDESTAL = CLASS_ICRAFTING_INJECTOR.getMethod("setStackInPedestal", ItemStack.class);

            FIELD_IS_CRAFTING = CLASS_CORE.getField("isCrafting");
            FIELD_CRAFTING_STAGE = CLASS_CORE.getField("craftingStage");
            FIELD_VALUE_BOOL = CLASS_MANAGED_BOOL.getField("value");
            FIELD_VALUE_SHORT = CLASS_MANAGED_SHORT.getField("value");

            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] DraconicEvolution reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return CORE_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_CORE.isInstance(te);
    }

    @Override
    public EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.physicalOnly();
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        if (isCrafting(te)) return false;

        // 重新加载后 injectors 列表可能为空,先刷新
        try {
            METHOD_UPDATE_INJECTORS.invoke(te);
        } catch (Exception e) {
            return false;
        }

        // Core slot 0 必须为空
        IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (coreInv == null) {
            // fallback: ISidedInventory
            try {
                ItemStack slot0 = (ItemStack) te.getClass().getMethod("func_70301_a", int.class).invoke(te, 0);
                if (!slot0.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        } else {
            if (!coreInv.getStackInSlot(0).isEmpty()) return false;
        }

        // 检查有足够空 injectors
        List<Object> injectors = getConnectedInjectors(te);
        int needed = 0;
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) needed++;
        }
        int empty = 0;
        for (Object injector : injectors) {
            try {
                ItemStack stack = (ItemStack) METHOD_GET_STACK_IN_PEDESTAL.invoke(injector);
                if (stack.isEmpty()) empty++;
            } catch (Exception ignored) {}
        }
        return empty >= needed;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        if (isCrafting(te)) return false;

        // 刷新 injectors 列表
        try {
            METHOD_UPDATE_INJECTORS.invoke(te);
        } catch (Exception e) {
            return false;
        }

        List<Object> injectors = getConnectedInjectors(te);
        ItemStack main = ingredients.getStackInSlot(0);

        // 预检：收集需要使用的 injector，确保全部材料都有位置
        List<Object> chosenInjectors = new ArrayList<>();
        int injectorIdx = 0;
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            Object chosen = null;
            while (injectorIdx < injectors.size()) {
                Object injector = injectors.get(injectorIdx);
                injectorIdx++;
                try {
                    ItemStack existing = (ItemStack) METHOD_GET_STACK_IN_PEDESTAL.invoke(injector);
                    if (existing.isEmpty()) {
                        chosen = injector;
                        break;
                    }
                } catch (Exception ignored) {}
            }
            if (chosen == null) {
                return false; // 辅助材料无处可放
            }
            chosenInjectors.add(chosen);
        }

        // 实际推送，失败时回滚
        boolean mainPushed = false;
        try {
            if (!main.isEmpty()) {
                IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (coreInv != null) {
                    ItemStack inserted = coreInv.insertItem(0, main.copy(), false);
                    if (!inserted.isEmpty()) {
                        return false;
                    }
                } else {
                    try {
                        te.getClass().getMethod("setStackInCore", int.class, ItemStack.class).invoke(te, 0, main.copy());
                    } catch (Exception ex) {
                        return false;
                    }
                }
                mainPushed = true;
            }

            int auxIdx = 0;
            for (int i = 1; i < ingredients.getSizeInventory(); i++) {
                ItemStack stack = ingredients.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                Object injector = chosenInjectors.get(auxIdx++);
                ItemStack single = stack.copy();
                single.setCount(1);
                METHOD_SET_STACK_IN_PEDESTAL.invoke(injector, single);
            }
            return true;
        } catch (Exception e) {
            if (mainPushed) {
                // 尽量回滚主材料
                try {
                    IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                    if (coreInv != null) {
                        coreInv.extractItem(0, 64, false);
                    } else {
                        te.getClass().getMethod("setStackInCore", int.class, ItemStack.class).invoke(te, 0, ItemStack.EMPTY);
                    }
                } catch (Exception ignored) {}
            }
            return false;
        }
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        try {
            METHOD_ATTEMPT_START.invoke(te);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return false;
        // 重新加载后 injectors 列表可能为空,先刷新
        try {
            METHOD_UPDATE_INJECTORS.invoke(te);
        } catch (Exception ignored) {}
        return !isCrafting(te) && getCraftingStage(te) == 0;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return result;
        if (isCrafting(te)) return result;
        // 重新加载后 injectors 列表可能为空,先刷新
        try {
            METHOD_UPDATE_INJECTORS.invoke(te);
        } catch (Exception ignored) {}

        // 从 Core 提取产物(slot 0 或 slot 1)
        IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (coreInv != null) {
            // 先尝试 slot 1(输出槽)
            ItemStack out1 = coreInv.extractItem(1, 64, false);
            if (!out1.isEmpty()) result.add(out1);
            // 再检查 slot 0(某些配方可能替换主材料)
            ItemStack out0 = coreInv.extractItem(0, 64, false);
            if (!out0.isEmpty()) result.add(out0);
        } else {
            // fallback
            try {
                for (int slot : new int[]{1, 0}) {
                    ItemStack stack = (ItemStack) te.getClass().getMethod("func_70301_a", int.class).invoke(te, slot);
                    if (!stack.isEmpty()) {
                        result.add(stack.copy());
                        te.getClass().getMethod("func_70299_a", int.class, ItemStack.class).invoke(te, slot, ItemStack.EMPTY);
                    }
                }
            } catch (Exception ignored) {}
        }

        // 清理 injectors 中的残余
        List<Object> injectors = getConnectedInjectors(te);
        for (Object injector : injectors) {
            try {
                ItemStack residual = (ItemStack) METHOD_GET_STACK_IN_PEDESTAL.invoke(injector);
                if (!residual.isEmpty()) {
                    METHOD_SET_STACK_IN_PEDESTAL.invoke(injector, ItemStack.EMPTY);
                    result.add(residual);
                }
            } catch (Exception ignored) {}
        }

        // 龙研聚合产物 NBT 可能与样板预期不一致，按 item+meta 匹配后回写预期 NBT，
        // 使 AE2 CPU 的 waitingFor 精确匹配能够完成合成。
        normalizeToExpectedOutputs(result, expectedOutputs);
        return result;
    }

    private static void normalizeToExpectedOutputs(List<ItemStack> products, IAEItemStack[] expectedOutputs) {
        if (expectedOutputs == null || products == null) {
            return;
        }
        for (ItemStack product : products) {
            if (product.isEmpty()) continue;
            for (IAEItemStack expected : expectedOutputs) {
                if (expected == null || expected.getStackSize() <= 0) continue;
                ItemStack expectedStack = expected.createItemStack();
                if (expectedStack.isEmpty()) continue;
                if (ItemStack.areItemsEqual(product, expectedStack) && expectedStack.hasTagCompound()) {
                    product.setTagCompound(expectedStack.getTagCompound().copy());
                    break;
                }
            }
        }
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return collectAllFromTarget(world, pos);
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return collectAllFromTarget(world, pos);
    }

    private List<ItemStack> collectAllFromTarget(World world, BlockPos pos) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_CORE.isInstance(te)) return result;

        IItemHandler coreInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (coreInv != null) {
            for (int i = 0; i < coreInv.getSlots(); i++) {
                ItemStack stack = coreInv.extractItem(i, 64, false);
                if (!stack.isEmpty()) result.add(stack);
            }
        } else {
            try {
                for (int slot : new int[]{0, 1}) {
                    ItemStack stack = (ItemStack) te.getClass().getMethod("func_70301_a", int.class).invoke(te, slot);
                    if (!stack.isEmpty()) {
                        result.add(stack.copy());
                        te.getClass().getMethod("func_70299_a", int.class, ItemStack.class).invoke(te, slot, ItemStack.EMPTY);
                    }
                }
            } catch (Exception ignored) {}
        }

        try {
            METHOD_UPDATE_INJECTORS.invoke(te);
        } catch (Exception ignored) {}
        List<Object> injectors = getConnectedInjectors(te);
        for (Object injector : injectors) {
            try {
                ItemStack stack = (ItemStack) METHOD_GET_STACK_IN_PEDESTAL.invoke(injector);
                if (!stack.isEmpty()) {
                    METHOD_SET_STACK_IN_PEDESTAL.invoke(injector, ItemStack.EMPTY);
                    result.add(stack);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private static boolean isCrafting(TileEntity core) {
        try {
            Object managedBool = FIELD_IS_CRAFTING.get(core);
            return (boolean) FIELD_VALUE_BOOL.get(managedBool);
        } catch (Exception e) {
            return false;
        }
    }

    private static short getCraftingStage(TileEntity core) {
        try {
            Object managedShort = FIELD_CRAFTING_STAGE.get(core);
            return (short) FIELD_VALUE_SHORT.get(managedShort);
        } catch (Exception e) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> getConnectedInjectors(TileEntity core) {
        try {
            return (List<Object>) METHOD_GET_INJECTORS.invoke(core);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
