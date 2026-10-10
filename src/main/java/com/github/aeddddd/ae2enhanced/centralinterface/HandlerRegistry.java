package com.github.aeddddd.ae2enhanced.centralinterface;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.AbstractMachineHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.machine.MachineSpec;
import net.minecraftforge.fml.common.Loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程处理器反射隔离注册表.
 *
 * 无条件加载,内部通过 {@link Class#forName(String)} 懒加载具体 handler 类.
 * 未安装的 mod 对应的 handler 类永远不会被触碰,避免 {@link NoClassDefFoundError}.
 *
 * <p>注册顺序：通用 fallback 最先注册,mod-specific handlers 随后通过反射加载.
 * 注册顺序即优先级,索引只用于<b>缩小候选集</b>：</p>
 * <ul>
 *   <li>精确 blockId 索引({@link #EXACT_INDEX})：一个 blockId 只落在极少数候选 handler 上</li>
 *   <li>命名空间前缀列表({@link #PREFIX_RULES})：EnderIO / Thermal Expansion / NuclearCraft
 *       按命名空间前缀匹配整个 mod 的方块</li>
 * </ul>
 *
 * <p><b>表驱动</b>：六个"通用机器"家族（Default 兜底 + TE / EIO / NuclearCraft / TechReborn /
 * Actually Additions，以及 Bewitchment / Draconic Evolution）各自声明一条
 * {@link MachineSpec} 表项（blockId 匹配规则 + 行为描述），注册表读这份表项建索引
 * （{@link #tryLoadSpec}）。带虚拟路径的 handler（合成拓展、植物魔法等）
 * 仍用 {@link #tryLoadExact} / {@link #tryLoadPrefix} 显式声明，因为它们的匹配规则本身就带有
 * 各自的放置语义。</p>
 *
 * <p>{@link #findHandler(String)} 先按上述两级索引取候选,候选<b>仍按注册顺序</b>逐个询问
 * {@link IRemoteHandler#canHandle(String)},第一个返回 true 者即为结果；索引整体未命中时退回
 * 线性扫描,仍未匹配则返回默认 fallback({@link DefaultSingleBatchHandler},
 * 其 {@code canHandle} 恒为 false,仅作兜底)。索引本身不可能"凭空命中"一个 canHandle
 * 不接受的 handler。</p>
 *
 * <p><b>维护约束</b>：精确 id 与命名空间前缀不得覆盖同一个 blockId。当前所有 handler 都只匹配
 * 自己 mod 的命名空间,故两级索引互不重叠、彼此顺序无歧义；若将来某 handler 开始匹配外来
 * 命名空间的 id,必须同时调整索引声明,否则该 id 的优先级会被索引顺序而非注册顺序决定。</p>
 */
public class HandlerRegistry {

    private static final String[] NO_IDS = new String[0];
    private static final String[] NO_PREFIXES = new String[0];

    private static final List<IRemoteHandler> HANDLERS = new ArrayList<>();
    /** 精确 blockId → 候选 handler(按注册顺序). */
    private static final Map<String, List<IRemoteHandler>> EXACT_INDEX = new HashMap<>();
    /** 命名空间前缀规则(按注册顺序). */
    private static final List<PrefixRule> PREFIX_RULES = new ArrayList<>();
    private static boolean initialized = false;

    /**
     * 初始化注册表.线程安全,重复调用无效果.
     */
    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // 1. 通用 fallback 必须最先注册(作为默认匹配)：其表项是"兜底行",不声明任何 blockId
        registerFromSpec(new DefaultSingleBatchHandler());

        // 2. Mod-specific handlers(反射隔离加载)
        //    带虚拟路径的 handler 用 tryLoadExact/tryLoadPrefix 显式声明 blockId：
        //    声明必须与其 canHandle 一致——声明只决定候选集,命中与否最终仍由 canHandle 裁定,
        //    声明漏写的 id 会退回线性扫描而不会漏匹配。
        //    通用机器族（tryLoadSpec）的声明与 canHandle 同源于表项，不存在两处不同步的问题。
        // Botania
        tryLoadExact("botania", "com.github.aeddddd.ae2enhanced.centralinterface.handler.botania.BotaniaHandler",
                "botania:pool",
                "botania:alfheimportal",
                "botania:terraplate",
                "botania:runealtar",
                "botania:altar");
        // Blood Magic
        tryLoadExact("bloodmagic", "com.github.aeddddd.ae2enhanced.centralinterface.handler.bloodmagic.BloodMagicHandler",
                "bloodmagic:alchemy_table",
                "bloodmagic:soul_forge",
                "bloodmagic:altar");
        // Astral Sorcery / Actually Additions / Extended Crafting / Draconic Evolution / Bewitchment
        // 前两个与 Bewitchment / Draconic Evolution 是"表项驱动"的机器族：匹配规则在各自表项里声明
        tryLoadSpec("bewitchment", "com.github.aeddddd.ae2enhanced.centralinterface.handler.bewitchment.BewitchmentHandler");
        tryLoadExact("astralsorcery", "com.github.aeddddd.ae2enhanced.centralinterface.handler.astralsorcery.AstralSorceryHandler",
                "astralsorcery:blockaltar");
        tryLoadSpec("actuallyadditions", "com.github.aeddddd.ae2enhanced.centralinterface.handler.actuallyadditions.ActuallyAdditionsHandler");
        tryLoadExact("extendedcrafting", "com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting.ExtendedCraftingHandler",
                "extendedcrafting:crafting_core");
        tryLoadExact("extendedcrafting", "com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting.ExtendedCraftingTableHandler",
                "extendedcrafting:table_basic",
                "extendedcrafting:table_advanced",
                "extendedcrafting:table_elite",
                "extendedcrafting:table_ultimate");
        tryLoadExact("extendedcrafting", "com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting.CompressorHandler",
                "extendedcrafting:compressor");
        tryLoadExact("extendedcrafting", "com.github.aeddddd.ae2enhanced.centralinterface.handler.extendedcrafting.EnderCrafterHandler",
                "extendedcrafting:ender_crafter");
        tryLoadSpec("draconicevolution", "com.github.aeddddd.ae2enhanced.centralinterface.handler.draconicevolution.DraconicEvolutionHandler");
        // Avaritia - 终焉工作台(纯虚拟,机制与合成拓展工作台一致)
        tryLoadExact("avaritia", "com.github.aeddddd.ae2enhanced.centralinterface.handler.avaritia.ExtremeCraftingTableHandler",
                "avaritia:extreme_crafting_table");
        // Thaumcraft (Infusion & Crucible)
        tryLoadExact("thaumcraft", "com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft.ThaumcraftHandler",
                "thaumcraft:infusion_matrix");
        tryLoadExact("thaumcraft", "com.github.aeddddd.ae2enhanced.centralinterface.handler.thaumcraft.ThaumcraftCrucibleHandler",
                "thaumcraft:crucible");

        // Ender IO / Thermal Expansion / NuclearCraft 2.19a
        // 这些 handler 通过反射直接访问机器内部库存,绕过侧面配置限制。
        // 三者（以及 TR）都是表项驱动的机器族：前缀/精确 id 在各自表项里声明。
        tryLoadSpec("enderio", "com.github.aeddddd.ae2enhanced.centralinterface.handler.enderio.EnderIOMachineHandler");
        tryLoadSpec("thermalexpansion", "com.github.aeddddd.ae2enhanced.centralinterface.handler.thermalexpansion.ThermalExpansionMachineHandler");
        tryLoadSpec("nuclearcraft", "com.github.aeddddd.ae2enhanced.centralinterface.handler.nuclearcraft.NuclearCraftLegacyMachineHandler");
        // Tech Reborn:机器 Inventory 对所有面禁插禁取,只能通过反射直读内部库存与机器自身的槽位定义。
        // 注意：TR 是显式枚举而非 "techreborn:" 前缀通配(线缆/储电单元等同样以该前缀开头的方块
        // 必须继续落到 DefaultSingleBatchHandler)——那 19 个 id 声明在该 handler 的表项里。
        tryLoadSpec("techreborn", "com.github.aeddddd.ae2enhanced.centralinterface.handler.techreborn.TechRebornMachineHandler");
    }

    /** 加载并注册一个按<b>精确 blockId</b>匹配的 handler. */
    private static void tryLoadExact(String modId, String className, String... exactBlockIds) {
        tryLoad(modId, className, exactBlockIds, NO_PREFIXES);
    }

    /** 加载并注册一个按<b>命名空间前缀</b>匹配的 handler. */
    private static void tryLoadPrefix(String modId, String className, String... prefixes) {
        tryLoad(modId, className, NO_IDS, prefixes);
    }

    private static void tryLoad(String modId, String className, String[] exactBlockIds, String[] prefixes) {
        if (!Loader.isModLoaded(modId)) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className);
            IRemoteHandler handler = (IRemoteHandler) clazz.newInstance();
            register(handler, exactBlockIds, prefixes);
            AE2Enhanced.LOGGER.info("[AE2E] HandlerRegistry loaded handler for mod: {}", modId);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] HandlerRegistry failed to load handler for mod: {}", modId, e);
        }
    }

    /**
     * 加载并注册一个<b>表项驱动</b>的机器族处理器.
     *
     * <p>这类 handler（通用机器骨架 {@code AbstractMachineHandler} 的子类）把 blockId 匹配规则与
     * 行为描述一起声明在 {@link MachineSpec} 表项里，表项是 blockId 规则的唯一声明处。</p>
     *
     * <p>门控与反射隔离不变：{@code Loader.isModLoaded} 为假时连类名都不会被触碰，
     * 因此未安装的 mod 对应的类（及其反射入口）永远不会被加载。</p>
     */
    private static void tryLoadSpec(String modId, String className) {
        if (!Loader.isModLoaded(modId)) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(className);
            registerFromSpec((AbstractMachineHandler) clazz.newInstance());
            AE2Enhanced.LOGGER.info("[AE2E] HandlerRegistry loaded handler for mod: {}", modId);
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] HandlerRegistry failed to load handler for mod: {}", modId, e);
        }
    }

    /** 按 handler 自己的表项登记匹配规则（表项是 blockId 规则的唯一声明处）。 */
    private static void registerFromSpec(AbstractMachineHandler handler) {
        MachineSpec spec = handler.spec();
        register(handler,
                spec.exactBlockIds().toArray(NO_IDS),
                spec.blockIdPrefixes().toArray(NO_PREFIXES));
    }

    /** 追加注册 handler 并把它挂到两级索引上(未声明规则的 handler 只能靠线性扫描命中). */
    private static void register(IRemoteHandler handler, String[] exactBlockIds, String[] prefixes) {
        HANDLERS.add(handler);
        for (String exactBlockId : exactBlockIds) {
            List<IRemoteHandler> candidates = EXACT_INDEX.get(exactBlockId);
            if (candidates == null) {
                candidates = new ArrayList<>(2);
                EXACT_INDEX.put(exactBlockId, candidates);
            }
            candidates.add(handler);
        }
        for (String prefix : prefixes) {
            PREFIX_RULES.add(new PrefixRule(prefix, handler));
        }
    }

    /**
     * 根据方块 ID 查找匹配的处理器.
     *
     * @param blockId 方块注册 ID(如 "minecraft:furnace")
     * @return 匹配的 handler；若无可匹配者,返回默认 fallback
     */
    public static IRemoteHandler findHandler(String blockId) {
        init();
        IRemoteHandler indexed = findByIndex(blockId);
        if (indexed != null) {
            return indexed;
        }
        // 索引未命中：退回完整线性扫描,覆盖未声明规则的 handler、
        // 声明漏写的 id 以及未知 blockId
        for (IRemoteHandler handler : HANDLERS) {
            if (handler.canHandle(blockId)) {
                return handler;
            }
        }
        return HANDLERS.get(0);
    }

    /**
     * 两级索引查找：先精确 blockId,再命名空间前缀.
     *
     * <p>候选一律要过 {@link IRemoteHandler#canHandle(String)},索引只做剪枝不做裁决.</p>
     */
    private static IRemoteHandler findByIndex(String blockId) {
        if (blockId == null) {
            // null 直接交回线性扫描：逐个交给 canHandle 结果均为 false,两条路径结果一致
            return null;
        }
        List<IRemoteHandler> candidates = EXACT_INDEX.get(blockId);
        if (candidates != null) {
            for (IRemoteHandler candidate : candidates) {
                if (candidate.canHandle(blockId)) {
                    return candidate;
                }
            }
        }
        for (PrefixRule rule : PREFIX_RULES) {
            if (blockId.startsWith(rule.prefix) && rule.handler.canHandle(blockId)) {
                return rule.handler;
            }
        }
        return null;
    }

    /**
     * 获取所有已注册的处理器副本(用于调试或列表展示).
     */
    public static List<IRemoteHandler> getHandlers() {
        init();
        return new ArrayList<>(HANDLERS);
    }

    /**
     * 清空注册表并允许重新初始化。<b>仅供单元测试使用</b>：静态注册表状态需要在
     * 测试间隔离，生产代码不得调用此方法。
     */
    public static synchronized void resetForTesting() {
        HANDLERS.clear();
        EXACT_INDEX.clear();
        PREFIX_RULES.clear();
        initialized = false;
    }

    /**
     * 向注册表追加一个处理器。<b>仅供单元测试使用</b>：用于注入 fake handler
     * 验证 {@link #findHandler(String)} 的匹配顺序与兜底行为。
     *
     * <p>注入的 handler 不声明 blockId 规则,因此只会被线性扫描命中,注册顺序即优先级。</p>
     */
    public static synchronized void registerForTesting(IRemoteHandler handler) {
        init();
        register(handler, NO_IDS, NO_PREFIXES);
    }

    /** 一条命名空间前缀规则：命中条件 = blockId 以 {@code prefix} 开头,且 handler 的 canHandle 通过. */
    private static final class PrefixRule {
        private final String prefix;
        private final IRemoteHandler handler;

        private PrefixRule(String prefix, IRemoteHandler handler) {
            this.prefix = prefix;
            this.handler = handler;
        }
    }
}
