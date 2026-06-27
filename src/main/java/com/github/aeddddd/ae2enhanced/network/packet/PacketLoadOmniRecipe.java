package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Omni Terminal JEI 配方转移网络包
 *
 * 使用 Map<Integer, ItemStack> 保留 JEI slot index,确保配方空位正确对应.
 */
public class PacketLoadOmniRecipe implements IMessage {

    private NBTTagCompound data;

    public PacketLoadOmniRecipe() {
    }

    public PacketLoadOmniRecipe(byte mode, boolean isCrafting, int gridSize, Map<Integer, ItemStack> inputs, Map<Integer, ItemStack> outputs) {
        this.data = new NBTTagCompound();
        this.data.setByte("mode", mode);
        this.data.setBoolean("isCrafting", isCrafting);
        this.data.setInteger("gridSize", gridSize);

        NBTTagCompound inputsTag = new NBTTagCompound();
        for (Map.Entry<Integer, ItemStack> entry : inputs.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                inputsTag.setTag(String.valueOf(entry.getKey()), entry.getValue().writeToNBT(new NBTTagCompound()));
            }
        }
        this.data.setTag("inputs", inputsTag);

        NBTTagCompound outputsTag = new NBTTagCompound();
        for (Map.Entry<Integer, ItemStack> entry : outputs.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                outputsTag.setTag(String.valueOf(entry.getKey()), entry.getValue().writeToNBT(new NBTTagCompound()));
            }
        }
        this.data.setTag("outputs", outputsTag);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, this.data);
    }

    public NBTTagCompound getData() {
        return data;
    }

    public static class Handler implements IMessageHandler<PacketLoadOmniRecipe, IMessage> {

        @Override
        public IMessage onMessage(PacketLoadOmniRecipe message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                if (!(ctx.getServerHandler().player.openContainer instanceof com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm)) {
                    return;
                }
                com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm c =
                        (com.github.aeddddd.ae2enhanced.container.ContainerOmniTerm) ctx.getServerHandler().player.openContainer;
                net.minecraft.nbt.NBTTagCompound data = message.getData();
                if (data == null) return;

                byte mode = data.getByte("mode");
                boolean isCrafting = data.getBoolean("isCrafting");
                int gridSize = data.hasKey("gridSize") ? data.getInteger("gridSize") : 3;

                Map<Integer, net.minecraft.item.ItemStack> inputs = new HashMap<>();
                Map<Integer, net.minecraft.item.ItemStack> outputs = new HashMap<>();

                net.minecraft.nbt.NBTTagCompound inTag = data.getCompoundTag("inputs");
                for (String key : inTag.getKeySet()) {
                    int slot = Integer.parseInt(key);
                    inputs.put(slot, new net.minecraft.item.ItemStack(inTag.getCompoundTag(key)));
                }

                net.minecraft.nbt.NBTTagCompound outTag = data.getCompoundTag("outputs");
                for (String key : outTag.getKeySet()) {
                    int slot = Integer.parseInt(key);
                    outputs.put(slot, new net.minecraft.item.ItemStack(outTag.getCompoundTag(key)));
                }

                c.loadPattern(mode, isCrafting, gridSize, inputs, outputs);
            });
            return null;
        }
    }
}
