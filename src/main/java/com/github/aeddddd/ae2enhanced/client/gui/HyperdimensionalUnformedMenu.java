package com.github.aeddddd.ae2enhanced.client.gui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 超维度仓储未成形状态的菜单容器。
 */
public class HyperdimensionalUnformedMenu extends AbstractContainerMenu {

    private final Inventory playerInventory;
    private final BlockPos controllerPos;
    private final Map<Block, Integer> missing;

    public HyperdimensionalUnformedMenu(int id, Inventory inv, BlockPos controllerPos, Map<Block, Integer> missing) {
        super(ModMenus.HYPERDIMENSIONAL_UNFORMED.get(), id);
        this.playerInventory = inv;
        this.controllerPos = controllerPos;
        this.missing = missing;
    }

    public static HyperdimensionalUnformedMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int count = buf.readVarInt();
        Map<Block, Integer> missing = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Block block = buf.readById(net.minecraft.core.registries.BuiltInRegistries.BLOCK);
            int amount = buf.readVarInt();
            if (block != null) {
                missing.put(block, amount);
            }
        }
        return new HyperdimensionalUnformedMenu(id, inv, pos, missing);
    }

    public static void encodeMissing(FriendlyByteBuf buf, Map<Block, Integer> missing) {
        buf.writeVarInt(missing.size());
        for (Map.Entry<Block, Integer> entry : missing.entrySet()) {
            buf.writeId(net.minecraft.core.registries.BuiltInRegistries.BLOCK, entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public Map<Block, Integer> getMissing() {
        return Collections.unmodifiableMap(missing);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5, controllerPos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
