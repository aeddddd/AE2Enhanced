package com.github.aeddddd.ae2enhanced.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.menu.AssemblyMenu;
import com.github.aeddddd.ae2enhanced.menu.AssemblyPatternMenu;
import com.github.aeddddd.ae2enhanced.menu.AssemblyUnformedMenu;
import com.github.aeddddd.ae2enhanced.menu.ComputationCoreMenu;
import com.github.aeddddd.ae2enhanced.menu.ComputationUnformedMenu;
import com.github.aeddddd.ae2enhanced.menu.HyperdimensionalNexusMenu;
import com.github.aeddddd.ae2enhanced.menu.HyperdimensionalUnformedMenu;
import com.github.aeddddd.ae2enhanced.menu.OmniToolConfigMenu;
import com.github.aeddddd.ae2enhanced.menu.PersonalDimensionCreateMenu;
import com.github.aeddddd.ae2enhanced.menu.PersonalDimensionManagerMenu;
import com.github.aeddddd.ae2enhanced.menu.UniversalMemoryCardMenu;

/**
 * 菜单类型注册中心.
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

    public static final RegistryObject<MenuType<PersonalDimensionManagerMenu>> PERSONAL_DIMENSION_MANAGER = DR
            .register("personal_dimension_manager", () -> IForgeMenuType.create(PersonalDimensionManagerMenu::create));

    public static final RegistryObject<MenuType<PersonalDimensionCreateMenu>> PERSONAL_DIMENSION_CREATE = DR
            .register("personal_dimension_create", () -> IForgeMenuType.create(PersonalDimensionCreateMenu::create));

    public static final RegistryObject<MenuType<OmniToolConfigMenu>> OMNI_TOOL_CONFIG = DR
            .register("omni_tool_config", () -> IForgeMenuType.create(OmniToolConfigMenu::create));

    public static final RegistryObject<MenuType<UniversalMemoryCardMenu>> UNIVERSAL_MEMORY_CARD = DR
            .register("universal_memory_card", () -> IForgeMenuType.create(UniversalMemoryCardMenu::create));

    private ModMenus() {
    }
}
