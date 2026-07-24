package com.github.aeddddd.ae2enhanced.blockentity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;

/**
 * 个人维度管理器方块实体：仅记录放置者（所有者）UUID.
 */
public class PersonalDimensionManagerBlockEntity extends BlockEntity {

    @Nullable
    private UUID owner;

    public PersonalDimensionManagerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PERSONAL_DIMENSION_MANAGER.get(), pos, state);
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (owner != null) {
            tag.putUUID("owner", owner);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }
}
