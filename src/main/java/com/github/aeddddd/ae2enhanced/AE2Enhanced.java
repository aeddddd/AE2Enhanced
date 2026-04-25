package com.github.aeddddd.ae2enhanced;

import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipe;
import com.github.aeddddd.ae2enhanced.crafting.SingularityRecipeRegistry;
import com.github.aeddddd.ae2enhanced.gui.GuiHandler;
import com.github.aeddddd.ae2enhanced.network.PacketPatternPage;
import com.github.aeddddd.ae2enhanced.network.PacketRequestAssembly;
import com.github.aeddddd.ae2enhanced.proxy.CommonProxy;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
    modid = AE2Enhanced.MOD_ID,
    name = AE2Enhanced.MOD_NAME,
    version = AE2Enhanced.VERSION,
    dependencies = "required-after:appliedenergistics2"
)
public class AE2Enhanced {

    public static final String MOD_ID = "ae2enhanced";
    public static final String MOD_NAME = "AE2Enhanced";
    public static final String VERSION = "1.0-SNAPSHOT";

    public static final String CLIENT_PROXY = "com.github.aeddddd.ae2enhanced.proxy.ClientProxy";
    public static final String SERVER_PROXY = "com.github.aeddddd.ae2enhanced.proxy.CommonProxy";

    @Mod.Instance(MOD_ID)
    public static AE2Enhanced instance;

    @SidedProxy(clientSide = CLIENT_PROXY, serverSide = SERVER_PROXY)
    public static CommonProxy proxy;

    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static SimpleNetworkWrapper network;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModItems.init();
        network = new SimpleNetworkWrapper(MOD_ID);
        network.registerMessage(PacketRequestAssembly.Handler.class, PacketRequestAssembly.class, 0, Side.SERVER);
        network.registerMessage(PacketPatternPage.Handler.class, PacketPatternPage.class, 1, Side.SERVER);
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new GuiHandler());
        proxy.init(event);
        LOGGER.info("[AE2E] init() called, registering singularity/black hole recipes...");
        registerSingularityRecipes();
        LOGGER.info("[AE2E] Registered {} black hole recipes", BlackHoleRecipeRegistry.getRecipes().size());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    private void registerSingularityRecipes() {
        // 系统 A：黑洞生成仪式 —— 64 AE2 奇点 + 4 下界之星 + 1 ME 控制器方块
        // 手持下界之星右键 ME 控制器触发，生成微型奇点
        Item ae2Material = Item.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "material"));
        if (ae2Material != null) {
            java.util.List<ItemStack> ritualInputs = new java.util.ArrayList<>();
            // AE2 奇点 metadata = 47
            ritualInputs.add(new ItemStack(ae2Material, 64, 47));
            ritualInputs.add(new ItemStack(Items.NETHER_STAR, 4));
            SingularityRecipeRegistry.register(new SingularityRecipe("micro_singularity_ritual", ritualInputs));
            AE2Enhanced.LOGGER.info("注册黑洞生成仪式配方: micro_singularity_ritual");
        } else {
            AE2Enhanced.LOGGER.warn("无法获取 AE2 材料物品，黑洞生成仪式配方未注册");
        }

        // 系统 B：黑洞合成 —— 把物品投入黑洞事件视界后转化为产物
        registerBlackHoleRecipes();
    }

    private void registerBlackHoleRecipes() {
        // 保留测试配方：8 石头 + 1 钻石 → 1 黑曜石（验证黑洞合成系统）
        java.util.Map<String, Integer> bhInputs = new java.util.HashMap<>();
        bhInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Blocks.STONE)), 8);
        bhInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Items.DIAMOND)), 1);
        BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                "test_obsidian",
                bhInputs,
                new ItemStack(Blocks.OBSIDIAN, 1)
        ));

        // 新材料黑洞合成配方（key 格式："registryName:meta"）
        Item ae2Material = Item.REGISTRY.getObject(new ResourceLocation("appliedenergistics2", "material"));
        if (ae2Material != null) {
            // 稳态时空流形：16 空间组件(material:34) + 64 奇点(material:47)
            java.util.Map<String, Integer> manifoldInputs = new java.util.HashMap<>();
            manifoldInputs.put("appliedenergistics2:material:34", 16);
            manifoldInputs.put("appliedenergistics2:material:47", 64);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "stable_spacetime_manifold",
                    manifoldInputs,
                    new ItemStack(ModItems.STABLE_SPACETIME_MANIFOLD, 1)
            ));

            // 微分形式稳定单元：128 奇点(material:47) + 16 下界之星
            java.util.Map<String, Integer> stabilizerInputs = new java.util.HashMap<>();
            stabilizerInputs.put("appliedenergistics2:material:47", 128);
            stabilizerInputs.put(BlackHoleRecipe.keyOf(new ItemStack(Items.NETHER_STAR)), 16);
            BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                    "differential_form_stabilizer",
                    stabilizerInputs,
                    new ItemStack(ModItems.DIFFERENTIAL_FORM_STABILIZER, 1)
            ));
        }

        // 共形不变荷：16 稳态时空流形 + 16 微分形式稳定单元
        java.util.Map<String, Integer> chargeInputs = new java.util.HashMap<>();
        chargeInputs.put(BlackHoleRecipe.keyOf(new ItemStack(ModItems.STABLE_SPACETIME_MANIFOLD)), 16);
        chargeInputs.put(BlackHoleRecipe.keyOf(new ItemStack(ModItems.DIFFERENTIAL_FORM_STABILIZER)), 16);
        BlackHoleRecipeRegistry.register(new BlackHoleRecipe(
                "conformal_invariant_charge",
                chargeInputs,
                new ItemStack(ModItems.CONFORMAL_CHARGE, 1)
        ));

        AE2Enhanced.LOGGER.info("注册黑洞合成配方数量: {}", BlackHoleRecipeRegistry.getRecipes().size());
    }


}
