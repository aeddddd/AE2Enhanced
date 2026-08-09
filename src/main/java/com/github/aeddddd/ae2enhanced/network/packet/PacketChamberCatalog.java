package com.github.aeddddd.ae2enhanced.network.packet;

import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe;
import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipeIndex;
import com.github.aeddddd.ae2enhanced.client.gui.GuiSingularityChamber;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * S→C 奇点处理仓配方目录：打开 GUI 时一次性下发,
 * 供配方过滤页展示（id/输入模板+单批量/输出/处理时间）.
 */
public class PacketChamberCatalog implements IMessage {

    public static class RecipeView {
        public String id;
        public List<ItemStack> inputTemplates = new ArrayList<>();
        public List<Long> inputCounts = new ArrayList<>();
        public ItemStack output = ItemStack.EMPTY;
        public int timeTicks;
    }

    private final List<RecipeView> recipes = new ArrayList<>();

    public PacketChamberCatalog() {
    }

    public static PacketChamberCatalog build() {
        PacketChamberCatalog packet = new PacketChamberCatalog();
        for (ChamberRecipe recipe : ChamberRecipeIndex.allRecipes()) {
            RecipeView view = new RecipeView();
            view.id = recipe.getId();
            for (Map.Entry<String, Long> input : recipe.getInputs().entrySet()) {
                ItemStack template = recipe.getInputTemplates().get(input.getKey());
                view.inputTemplates.add(template != null ? template : ItemStack.EMPTY);
                view.inputCounts.add(input.getValue());
            }
            view.output = recipe.getOutput();
            view.timeTicks = recipe.getTimeTicks();
            packet.recipes.add(view);
        }
        return packet;
    }

    public List<RecipeView> getRecipes() {
        return recipes;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            RecipeView view = new RecipeView();
            view.id = ByteBufUtils.readUTF8String(buf);
            int inputs = buf.readInt();
            for (int j = 0; j < inputs; j++) {
                view.inputTemplates.add(ByteBufUtils.readItemStack(buf));
                view.inputCounts.add(buf.readLong());
            }
            view.output = ByteBufUtils.readItemStack(buf);
            view.timeTicks = buf.readInt();
            recipes.add(view);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(recipes.size());
        for (RecipeView view : recipes) {
            ByteBufUtils.writeUTF8String(buf, view.id);
            buf.writeInt(view.inputTemplates.size());
            for (int j = 0; j < view.inputTemplates.size(); j++) {
                ByteBufUtils.writeItemStack(buf, view.inputTemplates.get(j));
                buf.writeLong(view.inputCounts.get(j));
            }
            ByteBufUtils.writeItemStack(buf, view.output);
            buf.writeInt(view.timeTicks);
        }
    }

    public static class Handler implements IMessageHandler<PacketChamberCatalog, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketChamberCatalog message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                GuiScreen screen = Minecraft.getMinecraft().currentScreen;
                if (screen instanceof GuiSingularityChamber) {
                    ((GuiSingularityChamber) screen).acceptCatalog(message.getRecipes());
                }
            });
            return null;
        }
    }
}
