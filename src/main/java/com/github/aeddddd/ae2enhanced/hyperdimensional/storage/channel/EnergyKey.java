package com.github.aeddddd.ae2enhanced.hyperdimensional.storage.channel;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 内部能量 key。AE2 没有原生能量类型，因此本 key 仅用于模组内部存储，
 * 不直接暴露给 AE2 网络（避免未知 key type 导致兼容性问题）。
 */
public class EnergyKey extends AEKey {

    public static final ResourceLocation ID = new ResourceLocation("ae2enhanced", "energy");

    /**
     * 能量 key type。由于 AE2 不识别自定义 key type，该类型仅用于内部通道分发。
     */
    public static final AEKeyType ENERGY_KEY_TYPE = new AEKeyType(
            ID,
            EnergyKey.class,
            Component.translatable("gui.ae2enhanced.hyperdimensional.channel.energy")
    ) {
        @Override
        public AEKey readFromPacket(FriendlyByteBuf buf) {
            return EnergyKey.INSTANCE;
        }

        @Override
        public AEKey loadKeyFromTag(net.minecraft.nbt.CompoundTag tag) {
            return EnergyKey.INSTANCE;
        }
    };

    public static final EnergyKey INSTANCE = new EnergyKey();

    private EnergyKey() {
        super();
    }

    @Override
    public AEKeyType getType() {
        return ENERGY_KEY_TYPE;
    }

    @Override
    public EnergyKey dropSecondary() {
        return this;
    }

    @Override
    public net.minecraft.nbt.CompoundTag toTag() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", ID.toString());
        return tag;
    }

    @Override
    public Object getPrimaryKey() {
        return ID;
    }

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void writeToPacket(FriendlyByteBuf buf) {
        buf.writeResourceLocation(ID);
    }

    @Override
    public ItemStack wrapForDisplayOrFilter() {
        return ItemStack.EMPTY;
    }

    @Override
    protected Component computeDisplayName() {
        return Component.translatable("gui.ae2enhanced.hyperdimensional.channel.energy");
    }

    @Override
    public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        // 能量不掉落物品
    }
}
