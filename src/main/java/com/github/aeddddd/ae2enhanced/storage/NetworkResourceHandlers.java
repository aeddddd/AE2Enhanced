package com.github.aeddddd.ae2enhanced.storage;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.api.NetworkResourcePort;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;
import com.github.aeddddd.ae2enhanced.storage.external.ExternalStackFactory;
import com.github.aeddddd.ae2enhanced.storage.mana.ManaChannelResolver;
import com.github.aeddddd.ae2enhanced.storage.starlight.AEStarlightStack;
import com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel;
import net.minecraft.nbt.NBTTagCompound;

/** 公共资源 API 的存储通道实现。 */
public final class NetworkResourceHandlers {

    public static final NetworkResourcePort.Handler ENERGY = new NetworkResourcePort.Handler() {
        @Override
        public long extract(IActionHost host, long amount, boolean simulate) {
            return transfer(host, amount, simulate, true, true);
        }

        @Override
        public long insert(IActionHost host, long amount, boolean simulate) {
            return transfer(host, amount, simulate, false, true);
        }
    };

    public static final NetworkResourcePort.Handler MANA = new NetworkResourcePort.Handler() {
        @Override
        public long extract(IActionHost host, long amount, boolean simulate) {
            return transfer(host, amount, simulate, true, false);
        }

        @Override
        public long insert(IActionHost host, long amount, boolean simulate) {
            return 0L;
        }
    };

    public static final NetworkResourcePort.Handler STARLIGHT = new NetworkResourcePort.Handler() {
        @Override
        public long extract(IActionHost host, long amount, boolean simulate) {
            return transfer(host, amount, simulate, true, null);
        }

        @Override
        public long insert(IActionHost host, long amount, boolean simulate) {
            return 0L;
        }
    };

    private NetworkResourceHandlers() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static long transfer(IActionHost host, long amount, boolean simulate,
            boolean extract, Boolean energy) {
        if (host == null || amount <= 0) {
            return 0L;
        }
        try {
            IGridNode node = host.getActionableNode();
            if (node == null) {
                return 0L;
            }
            IGrid grid = node.getGrid();
            IStorageGrid storage = grid.getCache(IStorageGrid.class);
            if (storage == null) {
                return 0L;
            }
            IStorageChannel channel;
            IAEStack request;
            if (energy == null) {
                channel = AEApi.instance().storage().getStorageChannel(IStarlightStorageChannel.class);
                request = AEStarlightStack.create(amount);
            } else if (energy) {
                channel = EnergyChannelResolver.getChannel();
                request = EnergyChannelResolver.createStack(amount);
            } else {
                channel = ManaChannelResolver.getChannel();
                request = createManaStack(channel, amount);
            }
            if (channel == null || request == null) {
                return 0L;
            }
            IMEInventory inventory = storage.getInventory(channel);
            if (inventory == null) {
                return 0L;
            }
            IActionSource source = new MachineSource(host);
            Actionable mode = simulate ? Actionable.SIMULATE : Actionable.MODULATE;
            if (extract) {
                IAEStack result = (IAEStack) inventory.extractItems(request, mode, source);
                return result == null ? 0L : result.getStackSize();
            }
            IAEStack rejected = (IAEStack) inventory.injectItems(request, mode, source);
            return rejected == null ? amount : amount - rejected.getStackSize();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    @SuppressWarnings("rawtypes")
    private static IAEStack createManaStack(IStorageChannel channel, long amount) {
        if (channel == null) {
            return null;
        }
        String nbtKey = ExternalStorageChannels.isAvailable(ExternalStorageChannels.Kind.MANA) ? "mana" : "Count";
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong(nbtKey, amount);
        IAEStack stack = ExternalStackFactory.createFromNBT(channel, nbt);
        if (stack != null) {
            stack.setStackSize(amount);
        }
        return stack;
    }
}
