package com.github.aeddddd.ae2enhanced.blockentity;

import java.util.UUID;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.storage.MEStorage;

import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalMEStorage;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalStorage;
import com.github.aeddddd.ae2enhanced.hyperdimensional.storage.HyperdimensionalStorageFile;
import com.github.aeddddd.ae2enhanced.multiblock.IStorageHost;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;

/**
 * 超维度仓储中枢控制器方块实体。
 * <p>持有 Nexus UUID 与 BigInteger 外部存储，通过通用 ME 接口挂载到 AE2 网络。</p>
 */
public class HyperdimensionalControllerBlockEntity extends MultiblockControllerBlockEntity
        implements IStorageHost {

    @Nullable
    private UUID nexusId;
    @Nullable
    private HyperdimensionalStorage storage;
    @Nullable
    private HyperdimensionalMEStorage meStorage;

    private int validationCooldown = 0;
    private int saveCooldown = 0;

    public HyperdimensionalControllerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.HYPERDIMENSIONAL_CONTROLLER.get(), pos, blockState);
    }

    @Nullable
    public UUID getNexusId() {
        return nexusId;
    }

    public void setNexusId(UUID nexusId) {
        this.nexusId = nexusId;
        setChanged();
    }

    @Override
    public void setFormed(boolean formed) {
        boolean wasFormed = isFormed();
        super.setFormed(formed);
        if (formed && !wasFormed) {
            initStorage();
        } else if (!formed && wasFormed) {
            flushStorage();
        }
    }

    private void initStorage() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (nexusId == null) {
            nexusId = UUID.randomUUID();
            setChanged();
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        if (storage == null) {
            storage = HyperdimensionalStorageFile.loadOrCreate(server, nexusId, s -> refreshInterfaceServices());
            meStorage = new HyperdimensionalMEStorage(storage);
        }
        refreshInterfaceServices();
    }

    private void flushStorage() {
        if (storage == null || level == null || level.isClientSide()) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server != null) {
            HyperdimensionalStorageFile.save(server, storage);
        }
        refreshInterfaceServices();
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }

        if (validationCooldown-- <= 0) {
            validationCooldown = 20;
            if (isFormed() && !HyperdimensionalStructure.validate(level, worldPosition)) {
                HyperdimensionalStructure.disassemble(level, worldPosition);
            }
        }

        if (saveCooldown-- <= 0) {
            saveCooldown = 100;
            if (storage != null && storage.isDirty()) {
                MinecraftServer server = level.getServer();
                if (server != null) {
                    HyperdimensionalStorageFile.save(server, storage);
                }
            }
        }
    }

    // ---- IStorageHost ----

    @Nullable
    @Override
    public MEStorage getStorage() {
        if (!isFormed() || meStorage == null) {
            return null;
        }
        return meStorage;
    }

    // ---- NBT ----

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        if (data.hasUUID("nexusId")) {
            nexusId = data.getUUID("nexusId");
        } else {
            nexusId = null;
        }
        if (isFormed() && nexusId != null && level != null && !level.isClientSide()) {
            initStorage();
        }
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        if (nexusId != null) {
            data.putUUID("nexusId", nexusId);
        }
    }

    @Override
    public void setRemoved() {
        flushStorage();
        super.setRemoved();
    }
}
