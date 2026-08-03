package com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism;

import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.AVAILABLE;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.CONFIGURATION_DATA_SET;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.CONFIG_CARD_CAPABILITY;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.GET_COMPONENT;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.GET_CONFIGURATION_DATA;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.GET_CONFIGURATION_DATA_TYPE;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.IS_CONFIGURATION_DATA_COMPATIBLE;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.SET_CONFIGURATION_DATA;
import static com.github.aeddddd.ae2enhanced.memorycard.handler.mekanism.MekanismReflectionHelper.UPGRADE_TILE_CLASS;

import java.util.List;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.registries.ForgeRegistries;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.memorycard.api.PasteResult;
import com.github.aeddddd.ae2enhanced.memorycard.core.MemoryCardUpgradeHelper;
import com.github.aeddddd.ae2enhanced.memorycard.upgrade.MekanismUpgradeProvider;

/**
 * Mekanism 机器的配置复制粘贴 Handler(目标版本: Mekanism 1.20.1, 10.4.x).
 *
 * <p>配置段完全复用 Mekanism 原生配置卡(Configuration Card)机制: 通过
 * {@code Capabilities.CONFIG_CARD} 能力拿到 IConfigCardAccess,
 * 复制 = getConfigurationData(红石控制/侧向配置/自动弹出/ISustainedData/频率等),
 * 粘贴 = isConfigurationDataCompatible 校验后 setConfigurationData + configurationDataSet.
 * 工厂跨 tier 粘贴(如基础工厂 → 精英工厂)由原生 isConfigurationDataCompatible 放行,
 * 1.12 中手动逐级使用 Tier Installer 的逻辑在 1.20.1 不再需要(原生不改 tier,仅迁移配置).</p>
 *
 * <p>升级卡原生配置卡不处理,由本类按槽位(Upgrade 枚举)序列化为 ae2e:upgrades,
 * 粘贴时经 MekanismUpgradeProvider 走统一升级流程(校验/网络回退/消耗/安装).</p>
 *
 * <p>反射失效时行为: canHandle 返回 false,复制/粘贴按"不支持该设备"处理,不影响游戏运行.</p>
 */
public class MekanismMachineHandler implements IMemoryCardHandler {

    @Override
    public boolean canHandle(Object target) {
        return AVAILABLE && target instanceof BlockEntity blockEntity && getAccess(blockEntity) != null;
    }

    @Override
    public CompoundTag copy(Object target) {
        BlockEntity blockEntity = (BlockEntity) target;
        try {
            Object access = getAccess(blockEntity);
            if (access == null) {
                return null;
            }

            // 1. 原生配置卡数据(1.20.1 全部实现均忽略 player 参数,复制路径传 null)
            CompoundTag output = (CompoundTag) GET_CONFIGURATION_DATA.invoke(access, new Object[] { null });
            if (output == null) {
                output = new CompoundTag();
            }

            // 2. dataType: 方块实体类型注册名(与原生配置卡格式一致,粘贴时据此校验机型)
            Object type = GET_CONFIGURATION_DATA_TYPE.invoke(access);
            ResourceLocation typeKey = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey((BlockEntityType<?>) type);
            if (typeKey == null) {
                return null;
            }
            output.putString("dataType", typeKey.toString());

            // 3. 升级卡(按 Upgrade 枚举序列化,含 count;同时供 MISSING_UPGRADES 提示显示)
            if (UPGRADE_TILE_CLASS.isInstance(blockEntity)) {
                Object component = GET_COMPONENT.invoke(blockEntity);
                var upgradeList = MemoryCardUpgradeHelper.serializeUpgrades(
                        new MekanismUpgradeProvider(blockEntity, component));
                if (!upgradeList.isEmpty()) {
                    output.put("ae2e:upgrades", upgradeList);
                }
            }

            return output;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy Mekanism machine config", e);
            return null;
        }
    }

    @Override
    public PasteResult paste(Object target, CompoundTag data, Player player) {
        BlockEntity blockEntity = (BlockEntity) target;
        try {
            Object access = getAccess(blockEntity);
            if (access == null) {
                return PasteResult.INVALID_MACHINE;
            }

            // 1. 机型校验(原生兼容规则,含工厂跨 tier)
            ResourceLocation typeKey = ResourceLocation.tryParse(data.getString("dataType"));
            BlockEntityType<?> storedType = typeKey == null ? null : ForgeRegistries.BLOCK_ENTITY_TYPES.getValue(typeKey);
            if (storedType == null
                    || !(Boolean) IS_CONFIGURATION_DATA_COMPATIBLE.invoke(access, storedType)) {
                return PasteResult.INVALID_MACHINE;
            }

            // 2. 先处理升级(配置粘贴不应覆盖升级槽;缺少时向绑定网络发起合成请求)
            if (data.contains("ae2e:upgrades") && UPGRADE_TILE_CLASS.isInstance(blockEntity)) {
                Object component = GET_COMPONENT.invoke(blockEntity);
                List<ItemStack> needed = MemoryCardUpgradeHelper.deserializeUpgrades(
                        data.getList("ae2e:upgrades", Tag.TAG_COMPOUND));
                PasteResult result = MemoryCardUpgradeHelper.applyUpgrades(
                        new MekanismUpgradeProvider(blockEntity, component), needed, player);
                if (result != PasteResult.SUCCESS) {
                    return result;
                }
            }

            // 3. 写回配置(剔除内存卡自有键,dataType 原生不读取,一并移除)
            CompoundTag settings = data.copy();
            settings.remove("ae2e:upgrades");
            settings.remove("dataType");
            SET_CONFIGURATION_DATA.invoke(access, player, settings);
            CONFIGURATION_DATA_SET.invoke(access);

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste Mekanism machine config", e);
            return PasteResult.FAILED;
        }
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof BlockEntity blockEntity) {
            return blockEntity.getBlockState().getBlock().getName().getString();
        }
        return target.getClass().getSimpleName();
    }

    /**
     * 按面探测 CONFIG_CARD 能力(含 null 面),返回 IConfigCardAccess 实例.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static Object getAccess(BlockEntity blockEntity) {
        Capability cap = (Capability) CONFIG_CARD_CAPABILITY;
        if (cap == null) {
            return null;
        }
        Object access = blockEntity.getCapability(cap, null).resolve().orElse(null);
        if (access != null) {
            return access;
        }
        for (Direction side : Direction.values()) {
            access = blockEntity.getCapability(cap, side).resolve().orElse(null);
            if (access != null) {
                return access;
            }
        }
        return null;
    }
}
