package com.github.aeddddd.ae2enhanced;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import appeng.api.features.GridLinkables;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.data.DataGenerators;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimChunkGenerator;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PresetLoader;
import com.github.aeddddd.ae2enhanced.event.OmniToolEventHandler;
import com.github.aeddddd.ae2enhanced.event.StructureEventHandler;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.memorycard.network.UMCNetworkLink;
import com.github.aeddddd.ae2enhanced.omnitool.network.OmniToolNetworkLink;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.registry.ModBlocks;
import com.github.aeddddd.ae2enhanced.registry.ModCreativeTab;
import com.github.aeddddd.ae2enhanced.registry.ModItems;
import com.github.aeddddd.ae2enhanced.registry.ModMenus;
import com.github.aeddddd.ae2enhanced.registry.ModRecipes;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import com.github.aeddddd.ae2enhanced.structure.HyperdimensionalStructure;
import com.github.aeddddd.ae2enhanced.structure.SupercausalStructure;

@Mod(AE2Enhanced.MOD_ID)
public class AE2Enhanced {
    public static final String MOD_ID = "ae2enhanced";
    public static final Logger LOGGER = LogManager.getLogger(AE2Enhanced.class);

    public AE2Enhanced() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        // 注册配置
        AE2EnhancedConfig.register();

        // 注册 DeferredRegister
        ModBlocks.DR.register(modEventBus);
        ModItems.DR.register(modEventBus);
        ModBlockEntities.DR.register(modEventBus);
        ModMenus.DR.register(modEventBus);
        ModCreativeTab.DR.register(modEventBus);
        ModRecipes.DR.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);

        // 生命周期事件
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(DataGenerators::gatherData);

        // 注册网络包
        ModNetwork.init();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("AE2Enhanced common setup");

        // 在方块注册完成后初始化多方块结构定义
        AssemblyStructure.init();
        HyperdimensionalStructure.init();
        SupercausalStructure.init();

        MinecraftForge.EVENT_BUS.register(StructureEventHandler.class);
        MinecraftForge.EVENT_BUS.register(PersonalDimensionManager.class);
        MinecraftForge.EVENT_BUS.register(OmniToolEventHandler.class);

        // 个人维度：注册区块生成器编解码器并释放默认地板预设到 config 目录
        event.enqueueWork(() -> {
            Registry.register(BuiltInRegistries.CHUNK_GENERATOR,
                    new ResourceLocation(MOD_ID, "personal_dim"), PersonalDimChunkGenerator.CODEC.codec());
            // 旧配置一次性迁移(旧 presetPath 值与旧自动复制文件),需在加载预设前执行
            PresetLoader.migrateLegacyPresetIfNeeded();
            PresetLoader.copyDefaultPresetToConfigIfMissing();
            // 将内置默认地板样式注册到 API 注册表,供 config 以 "ae2enhanced:default" 引用
            PresetLoader.registerBuiltinDefault();
            // 全能工具:注册到 AE2 可链接物品表,使其可在无线访问点 GUI 中绑定网络
            GridLinkables.register(ModItems.ME_OMNI_TOOL.get(), OmniToolNetworkLink.LINKABLE_HANDLER);
            // 通用内存卡:同样注册到可链接物品表,支持在无线访问点 GUI 中绑定网络
            GridLinkables.register(ModItems.UNIVERSAL_MEMORY_CARD.get(), UMCNetworkLink.LINKABLE_HANDLER);
        });
    }
}
