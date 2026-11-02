package com.github.aeddddd.ae2enhanced.craftingapi;

/** 计划层可消费的最小 CPU 端口；公共 CraftingCpuAdapter 也实现此接口。 */
public interface CraftingCpuPort {

    static CraftingCpuPort of(Object cpu) {
        CraftingCpuAccess access = (CraftingCpuAccess) cpu;
        return new CraftingCpuPort() {
            @Override
            public CraftingCpuAccess access() {
                return access;
            }

            @Override
            public CraftingCpuState context() {
                return access.ae2e$context();
            }
        };
    }

    CraftingCpuAccess access();

    CraftingCpuState context();
}
