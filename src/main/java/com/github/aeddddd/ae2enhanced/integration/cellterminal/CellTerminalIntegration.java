package com.github.aeddddd.ae2enhanced.integration.cellterminal;

import com.cellterminal.integration.storagebus.StorageBusScannerRegistry;
import net.minecraftforge.fml.common.Loader;

/**
 * 元件终端(Cell Terminal, modid: cellterminal)集成入口.
 *
 * <p>向 Cell Terminal 的存储总线扫描注册表登记 EMC 接口扫描器,
 * 使 EMC 接口的白名单/内容/绑定信息可在元件终端中集中查看与编辑.</p>
 *
 * <p>本类引用 cellterminal 类,调用方必须先通过 {@link #isLoaded()} 判定,
 * 再以 {@code Class.forName} 方式触达本类(反射隔离约定).</p>
 */
public final class CellTerminalIntegration {

    public static final String MOD_ID = "cellterminal";

    private CellTerminalIntegration() {
    }

    public static boolean isLoaded() {
        return Loader.isModLoaded(MOD_ID);
    }

    /**
     * 注册 EMC 接口的存储总线扫描器.在 postInit 调用(注册表为静态列表,
     * 晚于 Cell Terminal 自身 init 阶段的内建注册亦可生效).
     */
    public static void init() {
        StorageBusScannerRegistry.register(new EMCInterfaceBusScanner());
    }
}
