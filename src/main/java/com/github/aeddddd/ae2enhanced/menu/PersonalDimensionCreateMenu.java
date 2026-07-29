package com.github.aeddddd.ae2enhanced.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.dimension.FloorPreset;
import com.github.aeddddd.ae2enhanced.dimension.PresetLoader;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;

/**
 * 个人维度创建向导菜单:纯配置界面,无任何槽位.
 * 地板样式预览数据在打开界面时随 buf 一次性下发.
 */
public class PersonalDimensionCreateMenu extends AbstractContainerMenu {

    public final UUID owner;
    public final int presetWidth;
    public final int presetDepth;
    public final List<String> presetPalette;
    public final int[] presetStates;

    public PersonalDimensionCreateMenu(int id, Inventory inv, UUID owner) {
        this(id, inv, owner, 0, 0, List.of(), new int[0]);
    }

    private PersonalDimensionCreateMenu(int id, Inventory inv, UUID owner, int presetWidth, int presetDepth,
            List<String> presetPalette, int[] presetStates) {
        super(ModMenus.PERSONAL_DIMENSION_CREATE.get(), id);
        this.owner = owner;
        this.presetWidth = presetWidth;
        this.presetDepth = presetDepth;
        this.presetPalette = presetPalette;
        this.presetStates = presetStates;
    }

    public static PersonalDimensionCreateMenu create(int id, Inventory inv, FriendlyByteBuf buf) {
        UUID owner = buf.readUUID();
        int width = buf.readVarInt();
        int depth = buf.readVarInt();
        int paletteSize = buf.readVarInt();
        List<String> palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            palette.add(buf.readUtf());
        }
        int stateCount = buf.readVarInt();
        int[] states = new int[stateCount];
        for (int i = 0; i < stateCount; i++) {
            states[i] = buf.readVarInt();
        }
        return new PersonalDimensionCreateMenu(id, inv, owner, width, depth, palette, states);
    }

    /**
     * 写入打开界面时的地板样式预览数据(服务端调用).
     */
    public static void writePreviewData(FriendlyByteBuf buf, UUID owner) {
        FloorPreset preset = FloorPreset.from(PresetLoader.getPreset());
        buf.writeUUID(owner);
        buf.writeVarInt(preset.width);
        buf.writeVarInt(preset.depth);
        buf.writeVarInt(preset.palette.length);
        for (BlockState state : preset.palette) {
            buf.writeUtf(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        }
        buf.writeVarInt(preset.stateList.length);
        for (int state : preset.stateList) {
            buf.writeVarInt(state);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
