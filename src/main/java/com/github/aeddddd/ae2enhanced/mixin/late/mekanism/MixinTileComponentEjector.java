package com.github.aeddddd.ae2enhanced.mixin.late.mekanism;

import com.github.aeddddd.ae2enhanced.recycler.MachineOutputRedirector;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.SideData;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.prefab.TileEntityContainerBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/**
 * Mekanism 机器产物直注 Mixin. 在 TileComponentEjector.tick() 调用 outputItems() 之前,
 * 把当前配置的物品输出槽中的产物重定向到已绑定的 ME 网络回收节点,
 * 流体阶段在 ejectFluid 前把流体产物重定向到网络.
 */
@Mixin(value = TileComponentEjector.class, remap = false)
public class MixinTileComponentEjector {

    private static final Field FIELD_TILE_ENTITY;
    private static final Field FIELD_SIDE_DATA;

    static {
        Field tileField = null;
        Field sideDataField = null;
        try {
            Class<?> clazz = TileComponentEjector.class;
            tileField = clazz.getDeclaredField("tileEntity");
            tileField.setAccessible(true);
            sideDataField = clazz.getDeclaredField("sideData");
            sideDataField.setAccessible(true);
        } catch (Exception ignored) {
        }
        FIELD_TILE_ENTITY = tileField;
        FIELD_SIDE_DATA = sideDataField;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lmekanism/common/tile/component/TileComponentEjector;outputItems()V"))
    private void ae2enhanced$redirectItemsBeforeOutput(CallbackInfo ci) {
        if (FIELD_TILE_ENTITY == null || FIELD_SIDE_DATA == null) {
            return;
        }
        try {
            TileEntityContainerBlock tile = (TileEntityContainerBlock) FIELD_TILE_ENTITY.get(this);
            if (tile == null) {
                return;
            }
            World world = tile.getWorld();
            if (world == null || world.isRemote) {
                return;
            }

            @SuppressWarnings("unchecked")
            Map<TransmissionType, SideData> sideData = (Map<TransmissionType, SideData>) FIELD_SIDE_DATA.get(this);
            if (sideData == null) {
                return;
            }

            SideData data = sideData.get(TransmissionType.ITEM);
            if (data == null || data.availableSlots == null) {
                return;
            }

            for (int slot : data.availableSlots) {
                ItemStack stack = tile.getStackInSlot(slot);
                if (stack == null || stack.isEmpty()) {
                    continue;
                }

                ItemStack remainder = MachineOutputRedirector.tryRedirect(stack, world, tile.getPos());
                if (remainder.getCount() != stack.getCount()) {
                    tile.setInventorySlotContents(slot, remainder);
                }
            }
        } catch (IllegalAccessException ignored) {
        }
    }

    @Inject(method = "ejectFluid(Ljava/util/Set;Lnet/minecraftforge/fluids/FluidTank;)V", at = @At("HEAD"), remap = false)
    private void ae2enhanced$redirectFluidBeforeEject(Set<EnumFacing> outputSides, FluidTank tank, CallbackInfo ci) {
        if (FIELD_TILE_ENTITY == null || tank == null) {
            return;
        }
        FluidStack fluid = tank.getFluid();
        if (fluid == null || fluid.amount <= 0) {
            return;
        }
        try {
            TileEntityContainerBlock tile = (TileEntityContainerBlock) FIELD_TILE_ENTITY.get(this);
            if (tile == null) {
                return;
            }
            World world = tile.getWorld();
            if (world == null || world.isRemote) {
                return;
            }

            FluidStack toRedirect = fluid.copy();
            toRedirect.amount = Math.min(256, tank.getFluidAmount());

            FluidStack remainder = MachineOutputRedirector.tryRedirectFluid(toRedirect, world, tile.getPos());
            if (remainder == null || remainder.amount == 0) {
                tank.drain(toRedirect.amount, true);
            } else if (remainder.amount < toRedirect.amount) {
                tank.drain(toRedirect.amount - remainder.amount, true);
            }
        } catch (IllegalAccessException ignored) {
        }
    }
}
