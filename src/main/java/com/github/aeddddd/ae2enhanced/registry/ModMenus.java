package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.AssemblyMenu;
import com.github.aeddddd.ae2enhanced.client.gui.AssemblyPatternMenu;
import com.github.aeddddd.ae2enhanced.client.gui.AssemblyUnformedMenu;
import com.github.aeddddd.ae2enhanced.client.gui.ComputationCoreMenu;
import com.github.aeddddd.ae2enhanced.client.gui.ComputationUnformedMenu;
import com.github.aeddddd.ae2enhanced.client.gui.HyperdimensionalNexusMenu;
import com.github.aeddddd.ae2enhanced.client.gui.HyperdimensionalUnformedMenu;

/**
 * 菜单类型注册中心。
 */
public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> DR = DeferredRegister.create(Registries.MENU, AE2Enhanced.MOD_ID);

    public static final RegistryObject<MenuType<AssemblyMenu>> ASSEMBLY = DR.register("assembly",
            () -> IForgeMenuType.create(AssemblyMenu::create));

    public static final RegistryObject<MenuType<AssemblyPatternMenu>> ASSEMBLY_PATTERN = DR.register("assembly_pattern",
            () -> IForgeMenuType.create(AssemblyPatternMenu::create));

    public static final RegistryObject<MenuType<AssemblyUnformedMenu>> ASSEMBLY_UNFORMED = DR.register("assembly_unformed",
            () -> IForgeMenuType.create(AssemblyUnformedMenu::create));

    public static final RegistryObject<MenuType<HyperdimensionalNexusMenu>> HYPERDIMENSIONAL_NEXUS = DR.register(
            "hyperdimensional_nexus", () -> IForgeMenuType.create(HyperdimensionalNexusMenu::create));

    public static final RegistryObject<MenuType<HyperdimensionalUnformedMenu>> HYPERDIMENSIONAL_UNFORMED = DR.register(
            "hyperdimensional_unformed", () -> IForgeMenuType.create(HyperdimensionalUnformedMenu::create));

    public static final RegistryObject<MenuType<ComputationCoreMenu>> COMPUTATION_CORE = DR.register(
            "computation_core", () -> IForgeMenuType.create(ComputationCoreMenu::create));

    public static final RegistryObject<MenuType<ComputationUnformedMenu>> COMPUTATION_UNFORMED = DR.register(
            "computation_unformed", () -> IForgeMenuType.create(ComputationUnformedMenu::create));

    private ModMenus() {
    }
}
