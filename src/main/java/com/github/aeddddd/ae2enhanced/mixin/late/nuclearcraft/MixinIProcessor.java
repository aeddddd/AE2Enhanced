package com.github.aeddddd.ae2enhanced.mixin.late.nuclearcraft;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.recycler.MachineOutputRedirector;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * NuclearCraft Overhauled 机器产物直注 Mixin（Tier 2）。
 *
 * <p>在 {@code IProcessor#produceProducts()} 方法末尾注入，
 * 将产物从输出槽重定向到已绑定的 ME 网络回收节点。由于产物刚刚写入输出槽，
 * 此时拦截可以把全部或剩余产物直接注入 AE2 网络，避免产物在槽位中堆积。</p>
 *
 * <p>所有 NuclearCraft 类均通过字符串 + 反射访问，保证 NC 未安装时不会触发类加载错误。</p>
 */
@Mixin(targets = "nc.tile.processor.IProcessor", remap = false)
public class MixinIProcessor {

    private static final Method METHOD_GET_TILE_WORLD;
    private static final Method METHOD_GET_TILE_POS;
    private static final Method METHOD_GET_CONTAINER_INFO;
    private static final Method METHOD_GET_INVENTORY_STACKS;
    private static final Method METHOD_MARK_DIRTY;
    private static final Field FIELD_ITEM_OUTPUT_SLOTS;

    static {
        Method getTileWorld = null;
        Method getTilePos = null;
        Method getContainerInfo = null;
        Method getInventoryStacks = null;
        Method markDirty = null;
        Field itemOutputSlots = null;
        try {
            Class<?> processorClass = Class.forName("nc.tile.processor.IProcessor");
            Class<?> tileClass = Class.forName("nc.tile.ITile");
            Class<?> containerInfoClass = Class.forName("nc.tile.processor.info.ProcessorContainerInfo");

            getTileWorld = tileClass.getMethod("getTileWorld");
            getTilePos = tileClass.getMethod("getTilePos");
            getContainerInfo = processorClass.getMethod("getContainerInfo");
            getInventoryStacks = Class.forName("nc.tile.inventory.ITileInventory").getMethod("getInventoryStacks");

            itemOutputSlots = containerInfoClass.getField("itemOutputSlots");

            markDirty = Class.forName("net.minecraft.tileentity.TileEntity").getMethod("markDirty");
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to initialize NuclearCraft IProcessor mixin reflection", e);
        }
        METHOD_GET_TILE_WORLD = getTileWorld;
        METHOD_GET_TILE_POS = getTilePos;
        METHOD_GET_CONTAINER_INFO = getContainerInfo;
        METHOD_GET_INVENTORY_STACKS = getInventoryStacks;
        METHOD_MARK_DIRTY = markDirty;
        FIELD_ITEM_OUTPUT_SLOTS = itemOutputSlots;
    }

    // 注：编译期 Mixin AP 可能报 "Cannot find target method"，因为 NC 以 compileOnly 提供且
    // 接口 default 方法在 AP 阶段解析受限；运行时 Mixin 会在 IProcessor 加载后正常注入。
    @Inject(method = "produceProducts()V", at = @At("TAIL"), remap = false)
    private void ae2enhanced$redirectOutputs(CallbackInfo ci) {
        if (METHOD_GET_TILE_WORLD == null || METHOD_GET_TILE_POS == null
                || METHOD_GET_CONTAINER_INFO == null || METHOD_GET_INVENTORY_STACKS == null
                || FIELD_ITEM_OUTPUT_SLOTS == null) {
            return;
        }

        Object processor = (Object) this;
        try {
            World world = (World) METHOD_GET_TILE_WORLD.invoke(processor);
            if (world == null || world.isRemote) {
                return;
            }
            BlockPos pos = (BlockPos) METHOD_GET_TILE_POS.invoke(processor);

            Object info = METHOD_GET_CONTAINER_INFO.invoke(processor);
            int[] outputSlots = (int[]) FIELD_ITEM_OUTPUT_SLOTS.get(info);
            // 受 Mixin 目标接口签名限制，泛型类型擦除为 Object
            @SuppressWarnings("unchecked")
            NonNullList<ItemStack> stacks = (NonNullList<ItemStack>) METHOD_GET_INVENTORY_STACKS.invoke(processor);
            if (outputSlots == null || stacks == null) {
                return;
            }

            boolean changed = false;
            for (int slot : outputSlots) {
                if (slot < 0 || slot >= stacks.size()) {
                    continue;
                }
                ItemStack stack = stacks.get(slot);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }
                ItemStack remainder = MachineOutputRedirector.tryRedirect(stack, world, pos);
                if (remainder.getCount() != stack.getCount()) {
                    stacks.set(slot, remainder);
                    changed = true;
                }
            }

            if (changed && METHOD_MARK_DIRTY != null) {
                METHOD_MARK_DIRTY.invoke(processor);
            }
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] NuclearCraft output redirect failed", e);
        }
    }
}
