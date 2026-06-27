package com.github.aeddddd.ae2enhanced.centralinterface.handler.actuallyadditions;

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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Actually Additions Empowerer + Display Stand 处理器.
 *
 * <p>结构：中心 Empowerer,水平4方向距离3格各1个 Display Stand.
 * slot 0 → Empowerer 主材料,slot 1+ → 4个 Display Stands 辅助材料.</p>
 */
public class ActuallyAdditionsHandler implements IRemoteHandler {

    private static final String EMPOWERER_ID = "actuallyadditions:block_empowerer";

    // 反射缓存
    private static Class<?> CLASS_EMPOWERER;
    private static Class<?> CLASS_DISPLAY_STAND;
    private static Method METHOD_GET_NEARBY_STANDS;
    private static boolean reflectionReady = false;

    /**
     * 防止 pushMaterials 后立即 isIdle(processTime==0) 导致提前收回材料。
     * 推料 tick 记录在 {@link TargetSession} 中,isIdle 至少等待 GRACE_TICKS 后才认为可收集。
     */
    private static final int PUSH_IDLE_GRACE_TICKS = 2;

    private static void initReflection() {
        if (reflectionReady) return;
        try {
            CLASS_EMPOWERER = Class.forName("de.ellpeck.actuallyadditions.mod.tile.TileEntityEmpowerer");
            CLASS_DISPLAY_STAND = Class.forName("de.ellpeck.actuallyadditions.mod.tile.TileEntityDisplayStand");
            METHOD_GET_NEARBY_STANDS = CLASS_EMPOWERER.getDeclaredMethod("getNearbyStands");
            METHOD_GET_NEARBY_STANDS.setAccessible(true);
            reflectionReady = true;
        } catch (Exception e) {
            throw new RuntimeException("[AE2E] ActuallyAdditions reflection init failed", e);
        }
    }

    @Override
    public boolean canHandle(String blockId) {
        return EMPOWERER_ID.equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        return CLASS_EMPOWERER.isInstance(te);
    }

    @Override
    public EnumSet<HandlerCapabilities> getCapabilities() {
        return HandlerCapabilities.physicalOnly();
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) return false;

        // 检查 empowerer slot 0 是否为空或可容纳
        IItemHandler empowererInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (empowererInv == null) return false;
        if (!empowererInv.getStackInSlot(0).isEmpty()) return false;

        // 检查周围是否有足够的 display stands
        Object[] stands = getNearbyStands(te);
        if (stands == null) return false;

        int auxCount = 0;
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            if (!ingredients.getStackInSlot(i).isEmpty()) auxCount++;
        }
        for (int i = 0; i < Math.min(auxCount, stands.length); i++) {
            if (stands[i] == null) return false;
            TileEntity standTe = (TileEntity) stands[i];
            IItemHandler standInv = standTe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (standInv == null) return false;
            if (!standInv.getStackInSlot(0).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) return false;

        IItemHandler empowererInv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (empowererInv == null) return false;

        Object[] stands = getNearbyStands(te);
        if (stands == null) return false;

        ItemStack main = ingredients.getStackInSlot(0);
        List<Integer> usedStands = new ArrayList<>();

        // 预检：主材料能否完整放入
        if (!main.isEmpty()) {
            ItemStack simulated = empowererInv.insertItem(0, main.copy(), true);
            if (!simulated.isEmpty()) {
                return false;
            }
        }

        // 预检：每个辅助材料能否完整放入对应 Display Stand
        for (int i = 1; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            int standIdx = i - 1;
            if (standIdx >= stands.length || stands[standIdx] == null) {
                return false;
            }
            TileEntity standTe = (TileEntity) stands[standIdx];
            IItemHandler standInv = standTe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (standInv == null) return false;

            ItemStack single = stack.copy();
            single.setCount(1);
            ItemStack simulated = standInv.insertItem(0, single, true);
            if (!simulated.isEmpty()) {
                return false;
            }
            usedStands.add(standIdx);
        }

        // 全部预检通过，实际插入
        if (!main.isEmpty()) {
            empowererInv.insertItem(0, main.copy(), false);
        }
        for (int standIdx : usedStands) {
            TileEntity standTe = (TileEntity) stands[standIdx];
            IItemHandler standInv = standTe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
            if (standInv == null) continue;
            ItemStack single = ingredients.getStackInSlot(standIdx + 1).copy();
            single.setCount(1);
            standInv.insertItem(0, single, false);
        }

        // 推料 tick 由 dispatcher 在 commitPush 时写入 session，这里仅做防御性更新
        if (session != null) {
            session.setPushTick(world.getTotalWorldTime());
        }
        return true;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        // Empowerer 是自动检测配方的,不需要显式启动
        return true;
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        initReflection();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) return false;
        // 推料后至少等待数 tick,避免 processTime==0 时立即收回刚放入的材料
        if (session != null && !session.isPushGraceElapsed(world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) {
            return false;
        }
        try {
            int processTime = (int) CLASS_EMPOWERER.getField("processTime").get(te);
            return processTime == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) return result;

        IItemHandler inv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (inv == null) return result;

        // 收集 Empowerer slot 0 的产物
        ItemStack output = inv.extractItem(0, 64, false);
        if (!output.isEmpty()) {
            result.add(output);
        }

        // 清理 Display Stands 中未消耗的残余(理论上配方会消耗,但以防万一)
        Object[] stands = getNearbyStands(te);
        if (stands != null) {
            for (Object stand : stands) {
                if (stand == null) continue;
                TileEntity standTe = (TileEntity) stand;
                IItemHandler standInv = standTe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (standInv == null) continue;
                ItemStack residual = standInv.extractItem(0, 64, false);
                if (!residual.isEmpty()) {
                    result.add(residual);
                }
            }
        }
        return result;
    }

    @Override
    public List<ItemStack> clearOutputs(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return collectInternal(world, pos);
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        return collectInternal(world, pos);
    }

    private List<ItemStack> collectInternal(World world, BlockPos pos) {
        initReflection();
        List<ItemStack> result = new ArrayList<>();
        TileEntity te = world.getTileEntity(pos);
        if (!CLASS_EMPOWERER.isInstance(te)) return result;

        IItemHandler inv = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (inv != null) {
            for (int i = 0; i < inv.getSlots(); i++) {
                ItemStack stack = inv.extractItem(i, 64, false);
                if (!stack.isEmpty()) result.add(stack);
            }
        }

        Object[] stands = getNearbyStands(te);
        if (stands != null) {
            for (Object stand : stands) {
                if (stand == null) continue;
                TileEntity standTe = (TileEntity) stand;
                IItemHandler standInv = standTe.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
                if (standInv == null) continue;
                ItemStack stack = standInv.extractItem(0, 64, false);
                if (!stack.isEmpty()) result.add(stack);
            }
        }
        return result;
    }

    private Object[] getNearbyStands(TileEntity empowerer) {
        try {
            Object result = METHOD_GET_NEARBY_STANDS.invoke(empowerer);
            return (Object[]) result;
        } catch (Exception e) {
            return null;
        }
    }
}
