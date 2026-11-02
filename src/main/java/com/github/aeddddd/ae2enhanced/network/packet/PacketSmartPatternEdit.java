package com.github.aeddddd.ae2enhanced.network.packet;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** 带 expectedRevision 的 SmartPattern 编辑请求；旧包 wire format 保持不变。 */
public class PacketSmartPatternEdit implements IMessage {
    private long pos;
    private long expectedRevision;
    private String action = "";
    private NBTTagCompound payload = new NBTTagCompound();

    public PacketSmartPatternEdit() {
    }

    public PacketSmartPatternEdit(BlockPos pos, long expectedRevision, String action) {
        this(pos, expectedRevision, action, new NBTTagCompound());
    }

    public PacketSmartPatternEdit(BlockPos pos, long expectedRevision, String action, NBTTagCompound payload) {
        this.pos = pos.toLong();
        this.expectedRevision = expectedRevision;
        this.action = action;
        this.payload = payload;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        pos = buf.readLong();
        expectedRevision = buf.readLong();
        action = ByteBufUtils.readUTF8String(buf);
        payload = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        buf.writeLong(expectedRevision);
        ByteBufUtils.writeUTF8String(buf, action);
        ByteBufUtils.writeTag(buf, payload);
    }

    public long getExpectedRevision() {
        return expectedRevision;
    }

    public String getAction() {
        return action;
    }

    public NBTTagCompound getPayload() {
        return payload;
    }

    public static class Handler implements IMessageHandler<PacketSmartPatternEdit, IMessage> {
        private static void apply(TileSmartPatternInterface tile, PacketSmartPatternEdit message) {
            long revision = message.expectedRevision;
            NBTTagCompound payload = message.payload;
            switch (message.action) {
                case "encode":
                    tile.encodePattern(revision);
                    return;
                case "addRecipe":
                    tile.addNewRecipe(revision);
                    return;
                case "deleteDisabled":
                    tile.deleteDisabledRecipes(revision);
                    return;
                case "toggle":
                    tile.toggleRecipe(payload.getInteger("recipeIndex"), revision);
                    return;
                case "replace":
                    if (payload.hasKey("from", 10) && payload.hasKey("to", 10)) {
                        tile.replaceInAllRecipes(new ItemStack(payload.getCompoundTag("from")),
                                new ItemStack(payload.getCompoundTag("to")), revision);
                    }
                    return;
                case "fill":
                    NBTTagList inputs = payload.getTagList("inputs", 10);
                    NBTTagList outputs = payload.getTagList("outputs", 10);
                    if (inputs.tagCount() <= 81 && outputs.tagCount() <= 9) {
                        tile.fillLockedRecipe(readList(inputs), readList(outputs), revision);
                    }
                    return;
                case "bind":
                    SmartPatternData data = SmartPatternData.fromNBT(payload.getCompoundTag("data"));
                    if (data != null && payload.hasKey("boundPos", 4)) {
                        tile.bindWithData(BlockPos.fromLong(payload.getLong("boundPos")),
                                payload.getInteger("boundDim"), data.getTargetBlockId(), data, revision);
                        tile.updateRecipeDisplay();
                    }
                    return;
                default:
                    tile.modifyLockedRecipe(message.action, revision);
            }
        }

        private static IAEItemStack[] readList(NBTTagList list) {
            IAEItemStack[] stacks = new IAEItemStack[list.tagCount()];
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                stacks[i] = tag.getKeySet().isEmpty() ? null : AEItemStack.fromNBT(tag);
            }
            return stacks;
        }

        @Override
        public IMessage onMessage(PacketSmartPatternEdit message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                net.minecraft.entity.player.EntityPlayerMP player = ctx.getServerHandler().player;
                TileSmartPatternInterface tile = com.github.aeddddd.ae2enhanced.network.RequestContext
                        .smartPatternTile(player, BlockPos.fromLong(message.pos));
                if (tile == null || message.payload == null || message.action == null) return;
                apply(tile, message);
                if (player.openContainer instanceof ContainerSmartPatternInterface) {
                    ((ContainerSmartPatternInterface) player.openContainer)
                            .setExpectedRevision(tile.getPatternRevision());
                }
            });
            return null;
        }
    }
}
