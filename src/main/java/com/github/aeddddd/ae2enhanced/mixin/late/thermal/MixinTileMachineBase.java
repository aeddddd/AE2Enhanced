package com.github.aeddddd.ae2enhanced.mixin.late.thermal;

import cofh.core.util.core.SlotConfig;
import cofh.thermalexpansion.block.machine.TileMachineBase;
import com.github.aeddddd.ae2enhanced.recycler.MachineOutputRedirector;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Thermal Expansion 机器产物直注 Mixin。
 *
 * <p>在机器调用 {@link TileMachineBase#transferOutput()} 之前，
 * 把所有“可提取槽”（即输出槽）中的物品尝试重定向到已绑定的 ME 网络回收节点。
 * 若超维度仓储中枢可用，产物将直接进入 AE2 网络而不会 push 到相邻容器。</p>
 */
@Mixin(value = TileMachineBase.class, remap = false)
public class MixinTileMachineBase {

    private static final Field FIELD_SLOT_CONFIG;
    private static final Field FIELD_INVENTORY;
    private static final Field FIELD_WORLD;
    private static final Field FIELD_POS;

    static {
        Field slotConfigField = null;
        Field inventoryField = null;
        Field worldField = null;
        Field posField = null;
        try {
            Class<?> clazz = Class.forName("cofh.thermalexpansion.block.machine.TileMachineBase");
            slotConfigField = findField(clazz, "slotConfig");
            inventoryField = findField(clazz, "inventory");
            worldField = findField(clazz, "field_145850_b");
            posField = findField(clazz, "field_174879_c");
        } catch (Exception ignored) {
        }
        FIELD_SLOT_CONFIG = slotConfigField;
        FIELD_INVENTORY = inventoryField;
        FIELD_WORLD = worldField;
        FIELD_POS = posField;
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    @Inject(method = "func_73660_a", at = @At(value = "INVOKE", target = "Lcofh/thermalexpansion/block/machine/TileMachineBase;transferOutput()V"))
    private void ae2enhanced$redirectOutputsBeforeTransfer(CallbackInfo ci) {
        if (FIELD_SLOT_CONFIG == null || FIELD_INVENTORY == null || FIELD_WORLD == null || FIELD_POS == null) {
            return;
        }
        try {
            World world = (World) FIELD_WORLD.get(this);
            if (world == null || world.isRemote) {
                return;
            }
            SlotConfig slotConfig = (SlotConfig) FIELD_SLOT_CONFIG.get(this);
            ItemStack[] inventory = (ItemStack[]) FIELD_INVENTORY.get(this);
            BlockPos pos = (BlockPos) FIELD_POS.get(this);
            if (slotConfig == null || inventory == null) {
                return;
            }

            int limit = Math.min(inventory.length, slotConfig.allowExtractionSlot.length);
            for (int i = 0; i < limit; i++) {
                if (!slotConfig.allowExtractionSlot[i]) {
                    continue;
                }
                ItemStack stack = inventory[i];
                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                ItemStack remainder = MachineOutputRedirector.tryRedirect(stack, world, pos);
                inventory[i] = remainder;
            }
        } catch (IllegalAccessException ignored) {
        }
    }
}
