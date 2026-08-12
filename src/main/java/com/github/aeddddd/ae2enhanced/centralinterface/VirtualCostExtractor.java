package com.github.aeddddd.ae2enhanced.centralinterface;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.storage.energy.EnergyChannelResolver;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 虚拟合成资源提取器：统一从 AE2 网络提取各类资源，并支持原子回滚。
 *
 * <p>当前支持：物品、流体、RF、Mana、Starlight、气体、源质。
 * 物品/流体通道通过 instanceof 直接路由；其余通道在 {@link #CHANNEL_ROUTES}
 * 路由表中显式声明（栈类名 token + 通道类名 + mod 门控），
 * 通道类经 {@code Class.forName} 懒加载，避免硬引用可选 mod 类。</p>
 */
public class VirtualCostExtractor {

    private static final double ENERGY_EPSILON = 0.0001;

    /** 能量路由的栈类名 token,用于 Flux_Applied 外部通道生效时的特判 */
    private static final String ENERGY_ROUTE_TOKEN = "AEEnergyStack";

    /**
     * 非物品/流体通道的显式路由表。
     *
     * <p>匹配规则：栈类全限定名包含 {@code classNameToken} 即命中（与原
     * {@code className.contains(...)} 语义一致），按声明顺序逐个匹配。</p>
     */
    private static final class ChannelRoute {
        final String classNameToken;
        final String channelClassName;
        final boolean ownChannel;       // 本 mod 自有通道，无需 mod 门控
        final String[] requiredModIds;  // ownChannel=false 时生效，全部安装才可用

        ChannelRoute(String classNameToken, String channelClassName, String... requiredModIds) {
            this.classNameToken = classNameToken;
            this.channelClassName = channelClassName;
            this.ownChannel = requiredModIds.length == 0;
            this.requiredModIds = requiredModIds;
        }

        boolean isAvailable() {
            if (this.ownChannel) {
                return true;
            }
            for (String modId : this.requiredModIds) {
                if (!Loader.isModLoaded(modId)) {
                    return false;
                }
            }
            return true;
        }

        boolean matches(String className) {
            return className.contains(this.classNameToken);
        }
    }

    private static final List<ChannelRoute> CHANNEL_ROUTES = Arrays.asList(
            new ChannelRoute(ENERGY_ROUTE_TOKEN,
                    "com.github.aeddddd.ae2enhanced.storage.energy.IEnergyStorageChannel"),
            new ChannelRoute("AEManaStack",
                    "com.github.aeddddd.ae2enhanced.storage.mana.IManaStorageChannel"),
            new ChannelRoute("AEStarlightStack",
                    "com.github.aeddddd.ae2enhanced.storage.starlight.IStarlightStorageChannel"),
            new ChannelRoute("GasStack",
                    "com.mekeng.github.common.me.storage.IGasStorageChannel",
                    "mekanism", "mekeng"),
            new ChannelRoute("EssentiaStack",
                    "thaumicenergistics.api.storage.IEssentiaStorageChannel",
                    "thaumcraft", "thaumicenergistics"));

    /**
     * 模拟提取全部资源，返回是否可行。
     *
     * <p>AE2 {@code extractItems} 返回的是实际提取到的堆叠（大小 {@code <=} 请求），
     * 提取成功时大小等于请求，未提取或不足时返回 {@code null} 或更小的堆叠。</p>
     *
     * @param itemSource 可选的 CPU 内部物品缓存；若提供，物品成本从此处模拟提取而非网络。
     */
    public static boolean simulateExtract(IStorageGrid storage, List<IAEStack> costs, IActionSource source,
                                          IMEInventory<IAEItemStack> itemSource) {
        for (IAEStack cost : costs) {
            if (isEmpty(cost)) continue;
            IAEStack extracted = extractOne(storage, cost, Actionable.SIMULATE, source, itemSource);
            if (!isSufficient(extracted, cost)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 实际提取全部资源，失败时自动回滚已提取部分。
     *
     * @param itemSource 可选的 CPU 内部物品缓存；若提供，物品成本从此处实际提取而非网络。
     * @return 成功时返回已提取的资源清单（用于外部进一步回滚）；失败返回 null
     */
    public static List<IAEStack> extractAll(IStorageGrid storage, List<IAEStack> costs, IActionSource source,
                                            IMEInventory<IAEItemStack> itemSource) {
        List<IAEStack> extracted = new ArrayList<>();
        for (IAEStack cost : costs) {
            if (isEmpty(cost)) continue;
            IAEStack got = extractOne(storage, cost, Actionable.MODULATE, source, itemSource);
            if (!isSufficient(got, cost)) {
                rollback(storage, extracted, source, itemSource);
                return null;
            }
            extracted.add(cost.copy());
        }
        return extracted;
    }

    /**
     * 回滚已提取的资源。用于能量扣除失败后恢复已提取的材料。
     */
    public static void rollbackExtracted(IStorageGrid storage, List<IAEStack> extracted, IActionSource source,
                                         IMEInventory<IAEItemStack> itemSource) {
        if (extracted == null || extracted.isEmpty()) {
            return;
        }
        rollback(storage, extracted, source, itemSource);
    }

    /**
     * 模拟扣除 AE 能量，返回网络是否有足够能量。
     */
    public static boolean simulateExtractEnergy(IEnergySource energy, double amount) {
        if (amount <= 0) return true;
        return energy.extractAEPower(amount, Actionable.SIMULATE, PowerMultiplier.CONFIG) >= amount - ENERGY_EPSILON;
    }

    /**
     * 扣除 AE 能量。
     *
     * @return 是否扣除成功
     */
    public static boolean extractEnergy(IEnergySource energy, double amount, IActionSource source) {
        if (amount <= 0) return true;
        return energy.extractAEPower(amount, Actionable.MODULATE, PowerMultiplier.CONFIG) >= amount - ENERGY_EPSILON;
    }

    /**
     * 查询指定 AE 堆叠的可用数量。
     *
     * <p>通过模拟提取 {@code Long.MAX_VALUE} 并返回实际可取到的数量实现，
     * 避免 binary search 中多次部分提取。</p>
     *
     * @param itemSource 可选的 CPU 内部物品缓存；若提供，物品可用量从此处查询。
     */
    public static long queryAvailable(IStorageGrid storage, IAEStack cost, IActionSource source,
                                      IMEInventory<IAEItemStack> itemSource) {
        if (isEmpty(cost)) return 0;
        IAEStack request = cost.copy();
        request.setStackSize(Long.MAX_VALUE);
        IAEStack extracted = extractOne(storage, request, Actionable.SIMULATE, source, itemSource);
        return extracted != null ? extracted.getStackSize() : 0;
    }

    /**
     * 查询网络可用 AE 能量。
     */
    public static double queryAvailableEnergy(IEnergySource energy) {
        return energy.extractAEPower(Double.MAX_VALUE, Actionable.SIMULATE, PowerMultiplier.CONFIG);
    }

    private static boolean isEmpty(IAEStack stack) {
        return stack == null || stack.getStackSize() <= 0;
    }

    /**
     * 判断提取结果是否满足请求。
     *
     * <p>AE2 的 {@code extractItems} 成功时返回大小等于请求的堆叠，
     * 未提取或不足时返回 {@code null} 或更小的堆叠。</p>
     */
    private static boolean isSufficient(IAEStack extracted, IAEStack request) {
        return extracted != null && extracted.getStackSize() >= request.getStackSize();
    }

    private static IAEStack extractOne(IStorageGrid storage, IAEStack cost,
                                       Actionable mode, IActionSource source,
                                       IMEInventory<IAEItemStack> itemSource) {
        if (cost instanceof IAEItemStack) {
            if (itemSource != null) {
                return itemSource.extractItems((IAEItemStack) cost, mode, source);
            }
            IStorageChannel<IAEItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            return storage.getInventory(channel).extractItems((IAEItemStack) cost, mode, source);
        }
        if (cost instanceof IAEFluidStack) {
            IStorageChannel<IAEFluidStack> channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            return storage.getInventory(channel).extractItems((IAEFluidStack) cost, mode, source);
        }

        String className = cost.getClass().getName();
        for (ChannelRoute route : CHANNEL_ROUTES) {
            if (route.matches(className)) {
                return extractViaChannel(storage, cost, mode, source, route);
            }
        }

        AE2Enhanced.LOGGER.warn("[AE2E] Unknown virtual cost stack type: {}", className);
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IAEStack extractViaChannel(IStorageGrid storage, IAEStack cost, Actionable mode, IActionSource source,
                                              ChannelRoute route) {
        if (!route.isAvailable()) return null;
        try {
            IStorageChannel channel;
            IAEStack request = cost;
            if (ENERGY_ROUTE_TOKEN.equals(route.classNameToken) && EnergyChannelResolver.isFluxChannelActive()) {
                // Flux_Applied 通道生效: AE2E 自有能量通道未注册,
                // 且请求堆叠必须为 FluxStack(AEEnergyStack 进 FluxList 会 CCE)
                channel = (IStorageChannel) EnergyChannelResolver.getChannel();
                request = EnergyChannelResolver.createStack(cost.getStackSize());
                if (request == null) return null;
            } else {
                Class<?> channelClass = Class.forName(route.channelClassName);
                channel = AEApi.instance().storage().getStorageChannel((Class) channelClass);
            }
            if (channel == null) {
                AE2Enhanced.LOGGER.warn("[AE2E-CostExtract] channel {} not registered", route.channelClassName);
                return null;
            }

            IMEInventory monitor = storage.getInventory(channel);
            if (monitor == null) {
                AE2Enhanced.LOGGER.warn("[AE2E-CostExtract] monitor for {} is null", route.channelClassName);
                return null;
            }

            return (IAEStack) monitor.extractItems(request, mode, source);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.error("[AE2E] Failed to extract via channel {}: {}", route.channelClassName, e.toString(), e);
            return null;
        }
    }

    private static void rollback(IStorageGrid storage, List<IAEStack> extracted, IActionSource source,
                                 IMEInventory<IAEItemStack> itemSource) {
        for (IAEStack stack : extracted) {
            injectOne(storage, stack, source, itemSource);
        }
    }

    private static void injectOne(IStorageGrid storage, IAEStack stack, IActionSource source,
                                  IMEInventory<IAEItemStack> itemSource) {
        if (stack instanceof IAEItemStack) {
            if (itemSource != null) {
                itemSource.injectItems((IAEItemStack) stack, Actionable.MODULATE, source);
                return;
            }
            IStorageChannel<IAEItemStack> channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
            storage.getInventory(channel).injectItems((IAEItemStack) stack, Actionable.MODULATE, source);
            return;
        }
        if (stack instanceof IAEFluidStack) {
            IStorageChannel<IAEFluidStack> channel = AEApi.instance().storage().getStorageChannel(IFluidStorageChannel.class);
            storage.getInventory(channel).injectItems((IAEFluidStack) stack, Actionable.MODULATE, source);
            return;
        }

        String className = stack.getClass().getName();
        for (ChannelRoute route : CHANNEL_ROUTES) {
            if (route.matches(className)) {
                injectViaChannel(storage, stack, source, route);
                return;
            }
        }
        AE2Enhanced.LOGGER.warn("[AE2E] Cannot rollback unknown stack type: {}", className);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void injectViaChannel(IStorageGrid storage, IAEStack stack, IActionSource source, ChannelRoute route) {
        try {
            IStorageChannel channel;
            IAEStack input = stack;
            if (ENERGY_ROUTE_TOKEN.equals(route.classNameToken) && EnergyChannelResolver.isFluxChannelActive()) {
                // 与 extractViaChannel 同理: Flux 通道下必须注入 FluxStack
                channel = (IStorageChannel) EnergyChannelResolver.getChannel();
                input = EnergyChannelResolver.createStack(stack.getStackSize());
                if (input == null) return;
            } else {
                Class<?> channelClass = Class.forName(route.channelClassName);
                channel = AEApi.instance().storage().getStorageChannel((Class) channelClass);
            }
            if (channel == null) return;

            IMEInventory monitor = storage.getInventory(channel);
            if (monitor == null) return;

            monitor.injectItems(input, Actionable.MODULATE, source);
        } catch (Exception ignored) {
        }
    }
}
