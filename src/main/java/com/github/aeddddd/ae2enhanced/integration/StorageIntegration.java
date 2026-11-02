package com.github.aeddddd.ae2enhanced.integration;

import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.api.NetworkResourcePort;
import com.github.aeddddd.ae2enhanced.config.BootConfig;
import com.github.aeddddd.ae2enhanced.integration.botaniaapplie.BotaniaApplieCompat;
import com.github.aeddddd.ae2enhanced.integration.fluxapplied.FluxAppliedCompat;
import com.github.aeddddd.ae2enhanced.integration.projecte.ProjectEHelper;
import com.github.aeddddd.ae2enhanced.storage.EmcStorage;
import com.github.aeddddd.ae2enhanced.storage.EmcStoragePort;
import com.github.aeddddd.ae2enhanced.storage.ExternalStorageChannels;
import net.minecraft.item.ItemStack;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

/** 可选 mod 到 storage 出站端口的适配器装配。 */
public final class StorageIntegration {

    private StorageIntegration() {
    }

    public static void register() {
        BootConfig boot = BootConfig.current();
        if (boot.channelEnabled("energy")) {
            NetworkResourcePort.register(NetworkResourcePort.Kind.ENERGY,
                    com.github.aeddddd.ae2enhanced.storage.NetworkResourceHandlers.ENERGY);
        }
        if (boot.channelEnabled("mana") && boot.integrationEnabled("botania")) {
            NetworkResourcePort.register(NetworkResourcePort.Kind.MANA,
                    com.github.aeddddd.ae2enhanced.storage.NetworkResourceHandlers.MANA);
        }
        if (boot.channelEnabled("starlight") && boot.integrationEnabled("astralsorcery")) {
            NetworkResourcePort.register(NetworkResourcePort.Kind.STARLIGHT,
                    com.github.aeddddd.ae2enhanced.storage.NetworkResourceHandlers.STARLIGHT);
        }
        if (boot.channelEnabled("energy") && boot.externalChannelAllowed("energy")) {
            ExternalStorageChannels.register(ExternalStorageChannels.Kind.ENERGY,
                    new ExternalStorageChannels.Provider() {
                        @Override
                        public boolean isAvailable() {
                            return FluxAppliedCompat.isFluxStorageChannelAvailable();
                        }

                        @Override
                        public IStorageChannel<?> channel() {
                            return FluxAppliedCompat.getFluxStorageChannelInstance();
                        }

                        @Override
                        public IAEStack createStack(long amount) {
                            return FluxAppliedCompat.createFluxStack(amount);
                        }
                    });
        }
        if (boot.channelEnabled("mana") && boot.externalChannelAllowed("mana")) {
            ExternalStorageChannels.register(ExternalStorageChannels.Kind.MANA,
                    new ExternalStorageChannels.Provider() {
                        @Override
                        public boolean isAvailable() {
                            return BotaniaApplieCompat.isManaStorageChannelAvailable();
                        }

                        @Override
                        public IStorageChannel<?> channel() {
                            return BotaniaApplieCompat.getManaStorageChannelInstance();
                        }

                        @Override
                        public IAEStack createStack(long amount) {
                            return BotaniaApplieCompat.createManaAE(amount);
                        }
                    });
        }
        if (boot.channelEnabled("emc") && boot.integrationEnabled("projecte")) {
            EmcStoragePort.register(new EmcStorage() {
                @Override
                public void subtractEmcBig(Object provider, BigInteger value) {
                    ProjectEHelper.subtractEmcBig(provider, value);
                }

                @Override
                public BigInteger getEmcBig(Object provider) {
                    return ProjectEHelper.getEmcBig(provider);
                }

                @Override
                public boolean subtractEmcOffline(UUID owner, BigInteger value) {
                    return ProjectEHelper.subtractEmcOffline(owner, value);
                }

                @Override
                public List<ItemStack> getKnowledge(Object provider) {
                    return ProjectEHelper.getKnowledge(provider);
                }

                @Override
                public long getEmcValue(ItemStack stack) {
                    return ProjectEHelper.getEmcValue(stack);
                }

                @Override
                public boolean isAvailable() {
                    return ProjectEHelper.isAvailable();
                }

                @Override
                public boolean shouldDupeWithNBT(ItemStack stack) {
                    return ProjectEHelper.shouldDupeWithNBT(stack);
                }

                @Override
                public Object getKnowledgeProvider(UUID owner) {
                    return ProjectEHelper.getKnowledgeProvider(owner);
                }

                @Override
                public void sync(Object provider, net.minecraft.entity.player.EntityPlayerMP player) {
                    ProjectEHelper.sync(provider, player);
                }
            });
        }
    }
}
