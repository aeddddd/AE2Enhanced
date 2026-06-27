package com.github.aeddddd.ae2enhanced.mixin.late.eio;

import com.github.aeddddd.ae2enhanced.recycler.MachineOutputRedirector;
import crazypants.enderio.base.machine.baselegacy.AbstractPoweredTaskEntity;
import crazypants.enderio.base.machine.baselegacy.SlotDefinition;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Ender IO 机器产物直注 Mixin。
 *
 * <p>在 {@link AbstractPoweredTaskEntity#taskComplete()} 把产物写入输出槽之后，
 * 立即把输出槽中的产物重定向到已绑定的 ME 网络回收节点。</p>
 */
@Mixin(value = AbstractPoweredTaskEntity.class, remap = false)
public class MixinAbstractPoweredTaskEntity {

    private static final Field FIELD_SLOT_DEFINITION;
    private static final Field FIELD_INVENTORY;
    private static final Field FIELD_WORLD;
    private static final Field FIELD_POS;

    static {
        Field slotDefField = null;
        Field inventoryField = null;
        Field worldField = null;
        Field posField = null;
        try {
            Class<?> clazz = Class.forName("crazypants.enderio.base.machine.baselegacy.AbstractPoweredTaskEntity");
            slotDefField = findField(clazz, "slotDefinition");
            inventoryField = findField(clazz, "inventory");
            worldField = findField(clazz, "field_145850_b");
            posField = findField(clazz, "field_174879_c");
        } catch (Exception ignored) {
        }
        FIELD_SLOT_DEFINITION = slotDefField;
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

    @Inject(method = "taskComplete", at = @At("TAIL"))
    private void ae2enhanced$redirectOutputsAfterTaskComplete(CallbackInfo ci) {
        if (FIELD_SLOT_DEFINITION == null || FIELD_INVENTORY == null || FIELD_WORLD == null || FIELD_POS == null) {
            return;
        }
        try {
            World world = (World) FIELD_WORLD.get(this);
            if (world == null || world.isRemote) {
                return;
            }
            SlotDefinition slotDefinition = (SlotDefinition) FIELD_SLOT_DEFINITION.get(this);
            ItemStack[] inventory = (ItemStack[]) FIELD_INVENTORY.get(this);
            BlockPos pos = (BlockPos) FIELD_POS.get(this);
            if (slotDefinition == null || inventory == null) {
                return;
            }

            for (int i = slotDefinition.minOutputSlot; i <= slotDefinition.maxOutputSlot; i++) {
                if (i < 0 || i >= inventory.length) {
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
