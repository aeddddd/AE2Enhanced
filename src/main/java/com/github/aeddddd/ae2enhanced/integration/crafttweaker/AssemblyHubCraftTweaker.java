package com.github.aeddddd.ae2enhanced.integration.crafttweaker;

import com.github.aeddddd.ae2enhanced.crafting.AssemblyHubUpgradeRegistry;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import net.minecraft.item.ItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

/**
 * CraftTweaker 集成：允许通过 ZenScript 为 Assembly Hub 注册新的升级卡.
 * 类型仅限并行(Parallel)和速度(Speed).
 *
 * 用法示例：
 * <pre>
 *   // 注册自定义并行升级卡
 *   mods.ae2enhanced.AssemblyHub.registerParallelUpgrade(
 *       &lt;my_mod:quantum_parallel&gt;,
 *       5,
 *       [64, 2048, 65536, 2097152, 67108864]
 *   );
 *
 *   // 注册自定义速度升级卡
 *   mods.ae2enhanced.AssemblyHub.registerSpeedUpgrade(
 *       &lt;my_mod:temporal_accelerator&gt;,
 *       5,
 *       [20, 10, 5, 2, 1]
 *   );
 * </pre>
 */
@ZenRegister
@ZenClass("mods.ae2enhanced.AssemblyHub")
public class AssemblyHubCraftTweaker {

    @ZenMethod
    public static void registerParallelUpgrade(IItemStack card, int maxStack, long[] values) {
        CraftTweakerAPI.apply(new RegisterUpgradeAction(card, "parallel", maxStack, values));
    }

    @ZenMethod
    public static void registerSpeedUpgrade(IItemStack card, int maxStack, long[] values) {
        CraftTweakerAPI.apply(new RegisterUpgradeAction(card, "speed", maxStack, values));
    }

    public static class RegisterUpgradeAction implements IAction {
        private final IItemStack card;
        private final String type;
        private final int maxStack;
        private final long[] values;

        public RegisterUpgradeAction(IItemStack card, String type, int maxStack, long[] values) {
            this.card = card;
            this.type = type;
            this.maxStack = maxStack;
            this.values = values;
        }

        @Override
        public void apply() {
            ItemStack internal = (ItemStack) card.getInternal();
            ItemStack copy = internal.copy();
            copy.setCount(1);

            AssemblyHubUpgradeRegistry.UpgradeType upgradeType;
            if ("parallel".equalsIgnoreCase(type)) {
                upgradeType = AssemblyHubUpgradeRegistry.UpgradeType.PARALLEL;
            } else if ("speed".equalsIgnoreCase(type)) {
                upgradeType = AssemblyHubUpgradeRegistry.UpgradeType.SPEED;
            } else {
                CraftTweakerAPI.logError("[AE2Enhanced] Unknown AssemblyHub upgrade type: " + type);
                return;
            }

            AssemblyHubUpgradeRegistry.register(new AssemblyHubUpgradeRegistry.UpgradeDefinition(
                    copy, upgradeType, maxStack, values
            ));
        }

        @Override
        public String describe() {
            return "Registering AssemblyHub " + type + " upgrade: " + card.getName();
        }
    }
}
