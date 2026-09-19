package com.github.aeddddd.ae2enhanced.integration.crafttweaker;

import com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRecipe;
import com.github.aeddddd.ae2enhanced.crafting.SingularityFuelRegistry;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import net.minecraft.item.ItemStack;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

/**
 * CraftTweaker 集成: 允许通过 ZenScript 添加/移除微型奇点燃料配方.
 *
 * 用法示例:
 * <pre>
 *   mods.ae2enhanced.SingularityFuel.addFuel("my_fuel", &lt;minecraft:diamond&gt;, 6000);
 *   mods.ae2enhanced.SingularityFuel.addPermanentFuel("my_permanent", &lt;minecraft:nether_star&gt;);
 *   mods.ae2enhanced.SingularityFuel.removeFuel("my_fuel");
 * </pre>
 */
@ZenRegister
@ZenClass("mods.ae2enhanced.SingularityFuel")
public class SingularityFuelCraftTweaker {

    @ZenMethod
    public static void addFuel(String id, IItemStack item, int ticks) {
        CraftTweakerAPI.apply(new AddFuelAction(id, item, ticks, false));
    }

    @ZenMethod
    public static void addPermanentFuel(String id, IItemStack item) {
        CraftTweakerAPI.apply(new AddFuelAction(id, item, 0, true));
    }

    @ZenMethod
    public static void removeFuel(String id) {
        CraftTweakerAPI.apply(new RemoveFuelAction(id));
    }

    public static class AddFuelAction implements IAction {
        private final String id;
        private final IItemStack item;
        private final int ticks;
        private final boolean permanent;

        public AddFuelAction(String id, IItemStack item, int ticks, boolean permanent) {
            this.id = id;
            this.item = item;
            this.ticks = ticks;
            this.permanent = permanent;
        }

        @Override
        public void apply() {
            if (item == null) {
                return;
            }
            ItemStack internal = (ItemStack) item.getInternal();
            if (internal.isEmpty()) {
                return;
            }
            SingularityFuelRegistry.register(
                    new SingularityFuelRecipe(id, internal.copy(), ticks, permanent));
        }

        @Override
        public String describe() {
            return "Adding Singularity Fuel recipe: " + id;
        }
    }

    public static class RemoveFuelAction implements IAction {
        private final String id;

        public RemoveFuelAction(String id) {
            this.id = id;
        }

        @Override
        public void apply() {
            SingularityFuelRegistry.queueRemoval(id);
        }

        @Override
        public String describe() {
            return "Queueing Singularity Fuel recipe removal: " + id;
        }
    }
}
