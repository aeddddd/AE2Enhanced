package com.github.aeddddd.ae2enhanced.event;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.NewRegistryEvent;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraftforge.registries.RegistryBuilder;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypesInternal;
import appeng.core.AppEng;
import appeng.core.AppEngBootstrap;
import appeng.hotkeys.HotkeyActions;
import appeng.init.internal.InitGridLinkables;
import appeng.init.internal.InitStorageCells;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;

/**
 * AE2 开发环境兼容性事件处理器。
 *
 * <p>在官方映射开发环境下，AE2 的 {@code AppEngBase} 构造阶段会过早触发
 * {@code AEItems} 的静态初始化，而彼时 {@code BuiltInRegistries.ITEM} 已经冻结。
 * 通过 Mixin 将 {@link InitGridLinkables#init()}、{@link InitStorageCells#init()}
 * 与 {@link HotkeyActions#init()} 延迟到 {@code RegisterEvent} 之后，再在这里补调用，
 * 确保 AE2 物品已构造并注册。
 *
 * <p>此外，由于 {@code NewRegistryEvent} 触发时 {@code BuiltInRegistries.CHUNK_GENERATOR}
 * 已冻结，AE2 的 {@code registerRegistries} 方法会被取消；本处理器取而代之创建
 * AE2 的 {@code keytypes} 注册表，并在 {@code RegisterEvent} 阶段补执行 AE2 的
 * {@link AppEngBootstrap#runEarlyStartup()}，确保 {@code AEConfig} 已加载、注册表已解冻。
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AE2CompatEventHandler {

    private static boolean registerEventInitialized = false;
    private static boolean newRegistryInitialized = false;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onNewRegistry(NewRegistryEvent event) {
        if (newRegistryInitialized) {
            return;
        }
        newRegistryInitialized = true;

        Supplier<IForgeRegistry<AEKeyType>> supplier = event.create(
                new RegistryBuilder<AEKeyType>().setMaxID(127).setName(AppEng.makeId("keytypes"))
        );
        AEKeyTypesInternal.setRegistry(supplier);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegister(RegisterEvent event) {
        if (registerEventInitialized || !event.getRegistryKey().equals(Registries.BLOCK)) {
            return;
        }
        registerEventInitialized = true;

        // 在反混淆开发环境下，AE2 的 EarlyStartupMixin 可能未能成功注入 Bootstrap，
        // 导致 runEarlyStartup()（加载配置、解冻注册表）未被调用。这里补调用一次，
        // 其内部的 bootstrapped 标记保证只会实际执行一次。
        AppEngBootstrap.runEarlyStartup();

        InitGridLinkables.init();
        InitStorageCells.init();
        HotkeyActions.init();
    }
}
