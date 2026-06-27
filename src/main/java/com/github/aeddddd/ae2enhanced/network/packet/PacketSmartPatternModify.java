package com.github.aeddddd.ae2enhanced.network.packet;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * 智能样板接口：修改锁定配方或锁定/解锁操作.
 *
 * <p>支持的操作：</p>
 * <ul>
 *   <li>"lock" — 锁定指定排序索引的配方</li>
 *   <li>"unlock" — 解除锁定</li>
 *   <li>"keepPrimary" — 只保留主产物</li>
 *   <li>"doubleAmounts" — 所有数量翻倍</li>
 * </ul>
 */
public class PacketSmartPatternModify implements IMessage {

    private long pos;           // BlockPos.toLong()
    private String action;      // 操作类型
    private int recipeIndex;    // 用于 lock 操作

    public PacketSmartPatternModify() {
    }

    public PacketSmartPatternModify(BlockPos pos, String action) {
        this.pos = pos.toLong();
        this.action = action;
        this.recipeIndex = -1;
    }

    public PacketSmartPatternModify(BlockPos pos, String action, int recipeIndex) {
        this.pos = pos.toLong();
        this.action = action;
        this.recipeIndex = recipeIndex;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.pos = buf.readLong();
        this.action = ByteBufUtils.readUTF8String(buf);
        this.recipeIndex = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(pos);
        ByteBufUtils.writeUTF8String(buf, action);
        buf.writeInt(recipeIndex);
    }

    public BlockPos getPos() {
        return BlockPos.fromLong(pos);
    }

    public String getAction() {
        return action;
    }

    public int getRecipeIndex() {
        return recipeIndex;
    }

    public static class Handler implements IMessageHandler<PacketSmartPatternModify, IMessage> {

        @Override
        public IMessage onMessage(PacketSmartPatternModify message, MessageContext ctx) {
            ctx.getServerHandler().player.getServerWorld().addScheduledTask(() -> {
                World world = ctx.getServerHandler().player.world;
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(message.getPos());
                if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) {
                    com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface tile =
                            (com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface) te;
                    switch (message.getAction()) {
                        case "lock":
                            tile.lockRecipe(message.getRecipeIndex());
                            break;
                        case "unlock":
                            tile.unlockRecipe();
                            break;
                        case "keepPrimary":
                            tile.modifyLockedRecipe("keepPrimary");
                            break;
                        case "doubleAmounts":
                            tile.modifyLockedRecipe("doubleAmounts");
                            break;
                        case "deleteDisabled":
                            tile.deleteDisabledRecipes();
                            break;
                        default:
                            // 其他小按钮操作(multiply2, divide2, clearInputs 等)
                            tile.modifyLockedRecipe(message.getAction());
                            break;
                    }
                }
            });
            return null;
        }
    }
}
