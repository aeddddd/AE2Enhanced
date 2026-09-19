package com.github.aeddddd.ae2enhanced.diag.check;

import appeng.api.AEApi;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.integration.botaniaapplie.BotaniaApplieCompat;
import com.github.aeddddd.ae2enhanced.storage.channel.ChannelRegistrationManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 存储通道注册完整性检查.
 *
 * <p>核对逻辑（与 {@link ChannelRegistrationManager#registerChannels()} 的注册条件一致）：</p>
 * <ul>
 *   <li>能量通道：必须存在（AE2E 自注册或 Flux Applied 外部通道）</li>
 *   <li>Mana 通道：Botania 或 Botania_Applie 存在时应注册</li>
 *   <li>Starlight 通道：Astral Sorcery 存在时应注册</li>
 *   <li>气体/源质通道：由外部 mod（mekeng/thaumicenergistics）提供，仅按类名报告存在性</li>
 * </ul>
 */
public final class ChannelCheck implements SystemCheck {

    private static final String KEY_PREFIX = "chat.ae2enhanced.check.channels.";

    @Override
    public String name() {
        return "channels";
    }

    @Override
    public String displayName() {
        return KEY_PREFIX + "name";
    }

    @Override
    public void run(MinecraftServer server, List<CheckResult> out) {
        try {
            Collection<IStorageChannel<? extends IAEStack<?>>> channels =
                    AEApi.instance().storage().storageChannels();
            List<String> names = new ArrayList<>();
            boolean hasEnergy = false;
            boolean hasMana = false;
            boolean hasStarlight = false;
            boolean hasGas = false;
            boolean hasEssentia = false;
            for (IStorageChannel<?> channel : channels) {
                String className = channel.getClass().getName();
                names.add(className);
                if (ChannelRegistrationManager.isEnergyChannel(channel)) hasEnergy = true;
                if (ChannelRegistrationManager.isManaChannel(channel)) hasMana = true;
                if (ChannelRegistrationManager.isStarlightChannel(channel)) hasStarlight = true;
                String lower = className.toLowerCase();
                if (lower.contains("gas")) hasGas = true;
                if (lower.contains("essentia")) hasEssentia = true;
            }
            out.add(CheckResult.ok(KEY_PREFIX + "registered", channels.size(), names));

            // 能量通道：无条件必须存在
            if (!hasEnergy) {
                out.add(CheckResult.error(KEY_PREFIX + "energy_missing"));
            }
            // Mana 通道：按注册条件核对
            boolean manaExpected = BotaniaApplieCompat.isManaStorageChannelAvailable()
                    || Loader.isModLoaded("botania");
            if (manaExpected && !hasMana) {
                out.add(CheckResult.error(KEY_PREFIX + "mana_missing"));
            } else if (!manaExpected && hasMana) {
                out.add(CheckResult.warn(KEY_PREFIX + "mana_unexpected"));
            }
            // Starlight 通道：按注册条件核对
            boolean starlightExpected = Loader.isModLoaded("astralsorcery");
            if (starlightExpected && !hasStarlight) {
                out.add(CheckResult.error(KEY_PREFIX + "starlight_missing"));
            } else if (!starlightExpected && hasStarlight) {
                out.add(CheckResult.warn(KEY_PREFIX + "starlight_unexpected"));
            }
            // 气体/源质通道：外部提供,仅报告
            if (Loader.isModLoaded("mekeng")) {
                out.add(hasGas
                        ? CheckResult.ok(KEY_PREFIX + "gas_ok")
                        : CheckResult.error(KEY_PREFIX + "gas_missing"));
            }
            if (Loader.isModLoaded("thaumicenergistics")) {
                out.add(hasEssentia
                        ? CheckResult.ok(KEY_PREFIX + "essentia_ok")
                        : CheckResult.error(KEY_PREFIX + "essentia_missing"));
            }
        } catch (Exception e) {
            out.add(CheckResult.error(KEY_PREFIX + "exception", String.valueOf(e)));
        }
    }
}
