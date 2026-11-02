package com.github.aeddddd.ae2enhanced.verification;

import appeng.api.config.Actionable;
import appeng.api.storage.IMEInventoryHandler;
import com.github.aeddddd.ae2enhanced.config.BootConfig;
import com.github.aeddddd.ae2enhanced.storage.HugeCount;
import com.github.aeddddd.ae2enhanced.storage.HyperdimensionalStorageManager;
import com.github.aeddddd.ae2enhanced.storage.IStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.energy.AEEnergyStack;
import com.github.aeddddd.ae2enhanced.storage.energy.IAEEnergyStack;
import net.minecraft.world.WorldServer;
import org.apache.logging.log4j.LogManager;

import java.io.File;
import java.util.UUID;

/** A1 写入、B 禁用、A2 重启用的真实能源通道持久化验收。 */
final class StorageChannelVerifier {
    private static final UUID NEXUS_ID = UUID.fromString("12345678-1234-1234-1234-123456789abc");
    private static final long EXPECTED = 123456L;

    private StorageChannelVerifier() {
    }

    @SuppressWarnings("unchecked")
    static void verify(WorldServer world, String phase) {
        HyperdimensionalStorageManager.NexusSession session = HyperdimensionalStorageManager.acquire(world, NEXUS_ID);
        try {
            session.getFile().awaitLoaded();
            IStorageAdapter adapter = session.getEnergyAdapter();
            if ("B".equals(phase)) {
                require(!BootConfig.current().channelEnabled("energy"), "Energy channel was not frozen disabled");
                require(adapter == null, "Disabled energy channel created an adapter");
                File base = storageFile(world, "energy.bin");
                File wal = storageFile(world, "energy.wal");
                require(base.isFile() || wal.isFile(), "Disabled channel deleted both base and WAL");
                LogManager.getLogger("ae2enhancedsmoke").info(
                        "STORAGE_CHANNEL_PASS phase=B adapter=null base={} wal={}", base.isFile(), wal.isFile());
                return;
            }
            require(BootConfig.current().channelEnabled("energy"), "Energy channel was not enabled");
            require(adapter != null, "Enabled energy channel has no adapter");
            if ("A1".equals(phase)) {
                IAEEnergyStack remainder = ((IMEInventoryHandler<IAEEnergyStack>) adapter).injectItems(
                        AEEnergyStack.create(EXPECTED), Actionable.MODULATE, null);
                long remaining = remainder == null ? 0L : remainder.getStackSize();
                require(remaining == 0L, "Energy injection was not fully accepted");
                require(adapter.getTotalCount().equals(HugeCount.of(EXPECTED)), "Energy count after injection mismatch");
            } else if ("A2".equals(phase)) {
                require(adapter.getTotalCount().equals(HugeCount.of(EXPECTED)),
                        "Re-enabled energy channel did not restore persisted count: " + adapter.getTotalCount());
            } else {
                throw new IllegalArgumentException("Unknown storage verification phase " + phase);
            }
            LogManager.getLogger("ae2enhancedsmoke").info("STORAGE_CHANNEL_PASS phase={} count={}",
                    phase, adapter.getTotalCount());
        } finally {
            HyperdimensionalStorageManager.release(session);
        }
    }

    private static File storageFile(WorldServer world, String name) {
        return new File(world.getSaveHandler().getWorldDirectory(),
                "ae2enhanced/storage/" + NEXUS_ID + "/" + name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
