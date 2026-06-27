package com.github.aeddddd.ae2enhanced.recycler;

import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * 机器产物源头直注入口。
 *
 * <p>Mod Mixin 在机器产物即将写入输出槽时调用本类，
 * 若该机器已被回收节点绑定且网络中存在超维度仓储中枢，
 * 则把产物直接注入超维度仓储；否则返回原 stack 让机器按原逻辑处理。</p>
 */
public final class MachineOutputRedirector {

    private MachineOutputRedirector() {
    }

    /**
     * 尝试把产物重定向到 AE2 网络。
     *
     * @param output 产物堆叠
     * @param world  机器所在世界
     * @param pos    机器位置
     * @return 未能注入的部分；若全部注入成功返回 {@link ItemStack#EMPTY}
     */
    @Nonnull
    public static ItemStack tryRedirect(@Nonnull ItemStack output, World world, BlockPos pos) {
        if (output.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!AE2EnhancedConfig.recycler.machineOutputRedirect) {
            return output;
        }
        if (world == null || pos == null) {
            return output;
        }

        RecyclerBindingRegistry.Entry entry = RecyclerBindingRegistry.getInstance()
                .find(world.provider.getDimension(), pos);
        if (entry == null) {
            return output;
        }

        RecyclerNetworkHandler handler = entry.getHandler();
        if (handler == null) {
            // 回收节点已失效，清理绑定并回退
            RecyclerBindingRegistry.getInstance().unregister(entry.ref);
            return output;
        }

        return handler.tryInjectMachineOutput(output);
    }
}
