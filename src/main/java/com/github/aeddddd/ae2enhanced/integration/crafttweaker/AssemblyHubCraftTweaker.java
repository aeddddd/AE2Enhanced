package com.github.aeddddd.ae2enhanced.integration.crafttweaker;

import com.github.aeddddd.ae2enhanced.crafting.AssemblyHubUpgradeRegistry;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import net.minecraft.item.ItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

/** CraftTweaker Assembly Hub 自定义升级卡入口。 */
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

    @ZenMethod
    public static void removeUpgrade(IItemStack card) {
        CraftTweakerAPI.apply(new RemoveUpgradeAction(card));
    }

    @ZenMethod
    public static void removeUpgrade(String id) {
        CraftTweakerAPI.apply(new RemoveUpgradeByIdAction(id));
    }

    @ZenMethod
    public static String[] getUpgradeIds() {
        return AssemblyHubUpgradeRegistry.recipeIds();
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
            this.values = values == null ? null : values.clone();
        }

        @Override
        public boolean validate() {
            if (card == null || !(card.getInternal() instanceof ItemStack)
                    || ((ItemStack) card.getInternal()).isEmpty() || maxStack <= 0
                    || values == null || values.length != maxStack) {
                return false;
            }
            for (long value : values) {
                if (value <= 0L) {
                    return false;
                }
            }
            return "parallel".equalsIgnoreCase(type) || "speed".equalsIgnoreCase(type);
        }

        @Override
        public String describeInvalid() {
            return "AssemblyHub upgrade requires a card, positive maxStack and exactly one positive value per card";
        }

        @Override
        public void apply() {
            ItemStack copy = ((ItemStack) card.getInternal()).copy();
            copy.setCount(1);
            AssemblyHubUpgradeRegistry.UpgradeType upgradeType = "parallel".equalsIgnoreCase(type)
                    ? AssemblyHubUpgradeRegistry.UpgradeType.PARALLEL : AssemblyHubUpgradeRegistry.UpgradeType.SPEED;
            AssemblyHubUpgradeRegistry.register(new AssemblyHubUpgradeRegistry.UpgradeDefinition(
                    copy, upgradeType, maxStack, values));
        }

        @Override
        public String describe() {
            return "Registering AssemblyHub " + type + " upgrade";
        }
    }

    public static class RemoveUpgradeAction implements IAction {
        private final IItemStack card;

        public RemoveUpgradeAction(IItemStack card) {
            this.card = card;
        }

        @Override
        public boolean validate() {
            return card != null && card.getInternal() instanceof ItemStack
                    && !((ItemStack) card.getInternal()).isEmpty();
        }

        @Override
        public String describeInvalid() {
            return "AssemblyHub.removeUpgrade requires a non-empty card";
        }

        @Override
        public void apply() {
            AssemblyHubUpgradeRegistry.removeById(
                    AssemblyHubUpgradeRegistry.keyOf((ItemStack) card.getInternal()));
        }

        @Override
        public String describe() {
            return "Removing AssemblyHub upgrade";
        }
    }

    public static class RemoveUpgradeByIdAction implements IAction {
        private final String id;

        public RemoveUpgradeByIdAction(String id) {
            this.id = id;
        }

        @Override
        public boolean validate() {
            return id != null && !id.trim().isEmpty();
        }

        @Override
        public String describeInvalid() {
            return "AssemblyHub.removeUpgrade requires a non-empty id";
        }

        @Override
        public void apply() {
            if (!AssemblyHubUpgradeRegistry.removeById(id)) {
                CraftTweakerAPI.logWarning("No AssemblyHub upgrade found with id " + id);
            }
        }

        @Override
        public String describe() {
            return "Removing AssemblyHub upgrade " + id;
        }
    }
}
