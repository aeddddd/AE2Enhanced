package com.github.aeddddd.ae2enhanced.storage.codec;

import com.github.aeddddd.ae2enhanced.storage.FluidDescriptor;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * FluidDescriptor 自定义二进制编解码器.
 */
public class FluidDescriptorCodec implements DescriptorCodec<FluidDescriptor> {

    public static final FluidDescriptorCodec INSTANCE = new FluidDescriptorCodec();

    private FluidDescriptorCodec() {}

    @Override
    public void write(DataOutput out, FluidDescriptor descriptor) throws IOException {
        String id = descriptor.getFluid() != null ? descriptor.getFluid().getName() : "water";
        byte[] idBytes = id.getBytes("UTF-8");
        out.writeInt(idBytes.length);
        out.write(idBytes);

        NBTTagCompound nbt = descriptor.getNbt();
        if (nbt != null) {
            out.writeByte(1);
            CompressedStreamTools.write(nbt, out);
        } else {
            out.writeByte(0);
        }
    }

    @Override
    public FluidDescriptor read(DataInput in) throws IOException {
        int idLen = in.readInt();
        byte[] idBytes = new byte[idLen];
        in.readFully(idBytes);
        String id = new String(idBytes, "UTF-8");

        boolean hasNbt = in.readByte() != 0;
        NBTTagCompound nbt = null;
        if (hasNbt) {
            nbt = CompressedStreamTools.read(in, new NBTSizeTracker(2097152L));
        }

        Fluid fluid = FluidRegistry.getFluid(id);
        if (fluid == null) {
            return null;
        }
        return FluidDescriptor.fromRaw(fluid, nbt);
    }
}
