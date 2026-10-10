package com.github.aeddddd.ae2enhanced.util.dev;

import java.util.ArrayList;
import java.util.List;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.definitions.IBlocks;
import appeng.api.features.IWirelessTermHandler;
import appeng.api.implementations.items.IAEItemPowerStorage;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.ICellInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.me.helpers.PlayerSource;
import appeng.tile.misc.TileSecurityStation;
import appeng.tile.storage.TileDrive;
import appeng.util.item.AEItemStack;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig;
import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import com.github.aeddddd.ae2enhanced.container.OmniTerminalLayoutNbt;
import com.github.aeddddd.ae2enhanced.container.SplicedTermModules;
import com.github.aeddddd.ae2enhanced.container.SplicedTermSlotPolicy;
import com.github.aeddddd.ae2enhanced.registry.content.ItemRegistry;

/**
 * 可拼接终端验收 harness 的 <b>共享 + 服务端</b>部分。
 *
 * <p>由配置 {@code terminal.splicedTerminalTestHarness} 门控（默认 false，关闭时每个入口第一行返回）。
 * 打开后：</p>
 * <ol>
 *   <li><b>本类（服务端）</b>：玩家登录后的第一个服务端 tick 布景 —— 玩家脚边铺 4 格 AE2 网络
 *       （创造能源仓 / ME 驱动器 / 安全终端 / 无线接入点）、往驱动器插若干块注入测试物品的 64k 元件、
 *       给玩家两把充能并绑定的终端（快捷栏 0 = 新 V2、1 = 旧终端），随后做服务端侧验收
 *       （A24 转换配方走真实配方注册表、A21b 物品 NBT 里的 {@code omni_layout} 落盘确认）。</li>
 *   <li><b>{@link SplicedTermAcceptanceDriver}（客户端）</b>：用"与真实输入同一条事件入口"的合成事件
 *       驱动 GUI，逐项跑 A 表并打印 {@code ACCEPT <id>: PASS/FAIL <细节>}，最后打印
 *       {@link #ACCEPT_ALL_DONE_MARKER}。</li>
 * </ol>
 *
 * <p>验收结果集中记录在这里（{@link #recordAccept}），因为单人环境下客户端与服务端同 JVM。</p>
 *
 * <p>本类<strong>不引用任何客户端类</strong>（客户端部分全在 {@code SplicedTermAcceptanceDriver}，
 * 那个类用 {@code @Mod.EventBusSubscriber(value = Side.CLIENT)} 只在客户端加载），
 * 因此专职服务器加载本类不会有"客户端类符号"问题。</p>
 */
@Mod.EventBusSubscriber(modid = AE2Enhanced.MOD_ID)
public final class SplicedTermTestHarness {

    /** 自检脚本（旧 v1 语义）全部执行完毕时写进日志的标记。 */
    public static final String DONE_MARKER = "SPLICED_TERM_HARNESS_DONE";
    /** 脚本中途失败时写进日志的标记。 */
    public static final String FAIL_MARKER = "SPLICED_TERM_HARNESS_FAILED";
    /** 验收表跑完（无论有无 FAIL）时写进日志的标记。 */
    public static final String ACCEPT_ALL_DONE_MARKER = "ACCEPT_ALL_DONE";

    /** 日志前缀。 */
    public static final String TAG = "[AE2E][HARNESS] ";

    /** 单块 64k 物品元件的类型上限（AE2 {@code CellConfig} 也是 63），超过就得再插一块。 */
    static final int CELL_TYPE_LIMIT = 63;

    /** 本次布景注入的物品种类数（客户端 A3 期望值用它，避免两侧硬编码不一致）。 */
    static int stagedItemTypeCount;

    /** 已登录、等待下一个服务端 tick 布景的玩家。 */
    private static EntityPlayerMP pendingPlayer;
    /** 布景完成的玩家（服务端侧验收用它读物品栈）。 */
    private static EntityPlayerMP stagedPlayer;
    /** 布景后累计的服务端 tick（服务端侧验收的节拍）。 */
    private static int serverTicks;
    /** A21b 是否已经确认过布局落盘。 */
    private static boolean layoutNbtChecked;
    /** 本次布景是否发生在专用（独立）服务端上：E 系验收靠它区分布景细节。 */
    private static boolean dedicatedServer;
    /** E2 服务端观测：上一次读到的 panelMask（-1 = 还没看到可拼接终端容器）。 */
    private static int lastSeenPanelMask = -1;
    /** E4 服务端自检是否已经跑过（第一个服务端 tick 跑一次，与玩家无关）。 */
    private static boolean serverSelfCheckDone;
    /** 布景时的 ME 驱动器坐标（G3 追加元件用）。 */
    private static BlockPos stagedDrivePos;
    /** G3 是否已经追加过物品。 */
    private static boolean growthInjected;
    /** G3 追加的物品种类数（日志/断言用）。 */
    private static int growthItemCount;
    /** E1 服务端观测：上一次看到的容器 windowId（-1 = 还没看到）。 */
    private static int lastServerWindowId = -1;
    /** E1 服务端观测：最近一次读到的 shift-click 累计次数。 */
    private static int lastClickCountSeen;
    /** 心跳用的上次采样时刻/tick。 */
    private static long heartbeatLastNanos;
    private static int heartbeatLastTick = -1;

    // --- 验收结果汇总（客户端与服务端共用；单人环境同 JVM） ---
    private static final List<String> ACCEPT_RESULTS = new ArrayList<String>();
    private static int acceptFailures;
    private static int acceptTotal;

    private SplicedTermTestHarness() {
    }

    /** 配置门控。 */
    public static boolean enabled() {
        try {
            return AE2EnhancedConfig.terminal.splicedTerminalTestHarness;
        } catch (Throwable t) {
            return false;
        }
    }

    // ==================== 验收结果记录（两端共用） ====================

    /** 记录一条验收结果并打印 {@code ACCEPT <id>: PASS/FAIL <detail>}。 */
    public static synchronized void recordAccept(String id, boolean pass, String detail) {
        String line = "ACCEPT " + id + ": " + (pass ? "PASS" : "FAIL") + " " + detail;
        ACCEPT_RESULTS.add(line);
        acceptTotal++;
        if (!pass) {
            acceptFailures++;
        }
        AE2Enhanced.LOGGER.info(TAG + line);
    }

    /** 已记录的验收条目（供结尾汇总/报告使用）。 */
    public static synchronized List<String> acceptResults() {
        return new ArrayList<String>(ACCEPT_RESULTS);
    }

    /** 已记录的失败条数。 */
    public static synchronized int acceptFailures() {
        return acceptFailures;
    }

    /** 已记录的总条数。 */
    public static synchronized int acceptTotal() {
        return acceptTotal;
    }

    // ==================== 服务端：布景 ====================

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!enabled() || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        net.minecraft.server.MinecraftServer server = player.getServerWorld().getMinecraftServer();
        if (server == null) {
            return;
        }
        // 单人（内置服务端）与专用服务端都布景：
        //   单人 → A 系（客户端与服务端同 JVM，客户端驱动 GUI 跑完整 A 表）；
        //   专用 → E 系（本类在服务端 JVM 里布景 + 做服务端侧断言，客户端 JVM 里由
        //           SplicedTermAcceptanceDriver 检测到"非单机"后跑 E 表）。
        dedicatedServer = !server.isSinglePlayer();
        pendingPlayer = player;
        AE2Enhanced.LOGGER.info(TAG + "玩家 {} 登录，布景模式={}",
                player.getName(), dedicatedServer ? "E 系（专用服务端）" : "A 系（内置服务端）");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !enabled()) {
            return;
        }
        // 心跳：每 20 tick 一条（tick 号 + 本段每 tick 平均耗时 ms）。卡死时心跳会停住，
        // 用"停在第几个 tick / 恢复后间隔多大"判断是否单 tick 干不完。
        if (serverTicks % 20 == 0) {
            long now = System.nanoTime();
            long deltaMs = heartbeatLastNanos == 0 ? 0 : (now - heartbeatLastNanos) / 1000000L;
            long deltaTicks = heartbeatLastTick < 0 ? 20 : serverTicks - heartbeatLastTick;
            heartbeatLastNanos = now;
            heartbeatLastTick = serverTicks;
            AE2Enhanced.LOGGER.info(TAG + "NOTE beat tick={} 本段 {} tick 用时 {}ms（{} ms/tick）",
                    serverTicks, deltaTicks, deltaMs, deltaTicks <= 0 ? -1 : deltaMs / deltaTicks);
        }
        // 点击包取证：每 tick 滚动统计（服务端收包），超阈值时告警（每 20 tick 最多一条）
        int serverClicksThisTick = ClickPacketTrace.rollServerTick();
        if (serverClicksThisTick > ClickPacketTrace.FLOOD_THRESHOLD && serverTicks % 20 == 0) {
            AE2Enhanced.LOGGER.warn(TAG + "服务端单 tick 收到 {} 个点击包（>{}）——点击包流异常。{}",
                    serverClicksThisTick, ClickPacketTrace.FLOOD_THRESHOLD, ClickPacketTrace.summary());
        }
        // E 系自检（不依赖客户端/玩家）：AE2 排序比较器在专用服务端 JVM 里是否可用。
        net.minecraft.server.MinecraftServer runningServer =
                net.minecraftforge.fml.common.FMLCommonHandler.instance().getMinecraftServerInstance();
        if (!serverSelfCheckDone && runningServer != null && !runningServer.isSinglePlayer()) {
            serverSelfCheckDone = true;
            dedicatedServer = true;
            serverTicks = 0;
            checkServerItemSorters();
        }
        if (pendingPlayer != null) {
            EntityPlayerMP player = pendingPlayer;
            pendingPlayer = null;
            try {
                stage(player);
            } catch (Throwable t) {
                AE2Enhanced.LOGGER.error(TAG + "布景异常", t);
            }
        }
        if (stagedPlayer == null) {
            return;
        }
        serverTicks++;
        // A21b：布局 JSON 是否真的写进了终端物品 NBT（服务端权威那一份）
        if (!layoutNbtChecked && serverTicks % 20 == 0) {
            checkLayoutNbt();
        }
        // E2 服务端侧：panelMask 每次变化，白名单必须跟着重算（服务端拒绝隐藏面板的依据）
        if (dedicatedServer && serverTicks % 5 == 0) {
            checkServerPanelMask();
        }
        // G3：布景后 ~70s 往网络里追加几百种物品，测"分页耗时是否随总数线性增长"
        if (dedicatedServer && !growthInjected && serverTicks == 3200) {
            growthInjected = true;
            try {
                injectGrowthItems();
            } catch (Throwable t) {
                AE2Enhanced.LOGGER.error(TAG + "G3 追加物品异常", t);
            }
        }
        // 专用服务端侧：本 JVM 记到的断言（A1 布景 / A21b 布局落盘 / E2-server）独立成表，
        // 客户端 JVM 看不到它们，所以在这里按时间点打印几遍，取最后一份即可。
        if (dedicatedServer && (serverTicks == 1200 || serverTicks == 2400 || serverTicks == 3600)) {
            printServerSummary();
        }
    }

    /** 专用服务端侧的断言汇总（格式与客户端一致，便于同一套脚本解析）。 */
    private static void printServerSummary() {
        List<String> results = acceptResults();
        AE2Enhanced.LOGGER.info(TAG + "==== 服务端侧验收汇总（serverTicks={}）====", serverTicks);
        for (String line : results) {
            AE2Enhanced.LOGGER.info(TAG + line);
        }
        AE2Enhanced.LOGGER.info(TAG + "服务端侧失败 {} / 共 {} 项", acceptFailures(), results.size());
    }

    /**
     * E4 服务端侧自检：这个 JVM 上"分页排序"能不能用。
     *
     * <p>R3 分页的排序在两条路径上都走 AE2 的 {@code ItemSorters}
     * （适配器路径 {@code ItemStorageAdapter.sortSlice}、无中枢存储时的兜底 {@code ContainerOmniTerm.fallbackPageQuery}），
     * 而 {@code ItemSorters.CONFIG_BASED_SORT_BY_NAME} 会经 {@code Platform.getItemDisplayName} 调
     * {@code AEItemStack.getDisplayName()}——这个方法在专用服务端的运行期 JVM 上实测会
     * {@code NoSuchMethodError}（类加载期 remap 与开发工作区不同）。产品侧的契约不是"AE2 比较器一定可用"，
     * 而是"排序绝不把分页请求炸掉"：{@code TerminalItemSort} 收口了这两条路径，比较器抛错时退回本地比较器。</p>
     *
     * <p>所以这里断言的是<b>产品契约</b>：AE2 比较器的可用性只作为细节记录，
     * 真正判定的是 {@code TerminalItemSort.sort(...)} 不抛错且结果有序。不需要客户端，
     * 在 E 模式下第一个服务端 tick 就跑一次。</p>
     */
    private static void checkServerItemSorters() {
        String aeStatus;
        try {
            appeng.util.ItemSorters.init();
            appeng.util.ItemSorters.setDirection(appeng.api.config.SortDir.ASCENDING);
            List<appeng.api.storage.data.IAEItemStack> probe = new ArrayList<appeng.api.storage.data.IAEItemStack>();
            probe.add(AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)));
            probe.add(AEItemStack.fromItemStack(new ItemStack(Items.DIAMOND)));
            probe.sort(appeng.util.ItemSorters.CONFIG_BASED_SORT_BY_NAME);
            aeStatus = "AE2 比较器可用（[0]=" + probe.get(0).getDefinition() + "）";
        } catch (Throwable t) {
            aeStatus = "AE2 比较器不可用（" + t + "）→ 走 TerminalItemSort 兜底";
        }
        try {
            List<appeng.api.storage.data.IAEItemStack> probe = new ArrayList<appeng.api.storage.data.IAEItemStack>();
            probe.add(AEItemStack.fromItemStack(new ItemStack(Items.DIAMOND)));
            probe.add(AEItemStack.fromItemStack(new ItemStack(Items.IRON_INGOT)));
            com.github.aeddddd.ae2enhanced.util.TerminalItemSort.sort(probe,
                    appeng.api.config.SortOrder.NAME, appeng.api.config.SortDir.ASCENDING);
            recordAccept("E4-sort-server", probe.size() == 2 && probe.get(0) != null,
                    "服务端 JVM 分页排序入口（TerminalItemSort）：" + aeStatus + "；排序后[0]="
                            + probe.get(0).getDefinition());
        } catch (Throwable t) {
            recordAccept("E4-sort-server", false, "服务端 JVM 分页排序入口（TerminalItemSort）抛错 → " + t);
        }
    }

    /**
     * E2 服务端侧断言：容器上的 {@code panelMask} 一变，shift-click 白名单必须按新掩码重算。
     *
     * <p>这是"服务端拒绝向隐藏面板 shift-click"的服务端一半：客户端只能看到槽位没动，
     * 这里证明服务端确实把隐藏模块的槽位从白名单里摘掉了。</p>
     */
    private static void checkServerPanelMask() {
        if (!(stagedPlayer.openContainer instanceof ContainerSplicedTerm)) {
            return;
        }
        ContainerSplicedTerm container = (ContainerSplicedTerm) stagedPlayer.openContainer;
        // E1 服务端观测：容器一打开就记下 windowId / 槽位总数（客户端侧点不动时用来对账）
        if (container.windowId != lastServerWindowId) {
            lastServerWindowId = container.windowId;
            AE2Enhanced.LOGGER.info(TAG + "NOTE E1-server 容器已打开：windowId={} 槽位={} 快捷栏2={} 快捷栏7={} 快捷栏8={} viewcell槽={}",
                    container.windowId, container.inventorySlots.size(),
                    stagedPlayer.inventory.getStackInSlot(2), stagedPlayer.inventory.getStackInSlot(7),
                    stagedPlayer.inventory.getStackInSlot(8),
                    container.viewCellSlots().isEmpty() ? "空"
                            : container.viewCellSlots().get(0).getStack().toString());
        }
        // E1 服务端观测：每 40 tick 记一次玩家侧物品与服务端视图（判断"客户端点了、服务端有没有变化"）
        if (serverTicks % 40 == 0) {
            lastClickCountSeen = ContainerSplicedTerm.shiftClickCount();
            AE2Enhanced.LOGGER.info(TAG + "NOTE click-trace tick={} {}",
                    serverTicks, ClickPacketTrace.summary());
            AE2Enhanced.LOGGER.info(TAG + "NOTE alloc tick={} {}", serverTicks,
                    com.github.aeddddd.ae2enhanced.util.dev.AllocSampler.summary());
            AE2Enhanced.LOGGER.info(TAG + "NOTE policy tick={} {}（槽位={}）", serverTicks,
                    container.slotPolicy().describeGroupSizes(), container.inventorySlots.size());
            // 槽位总数每次也打出来：若它随 tick 增长，就说明有东西在服务端往容器里加槽位
            // （崩溃栈叶子是 AbstractList$Itr.hasNext ← InventoryPlayer.getStackInSlot，无界迭代的直接嫌疑）。
            AE2Enhanced.LOGGER.info(TAG + "NOTE E1-server tick={} windowId={} canCraft={} shiftClicks={} defCache={} 槽位={} 快捷栏2={} 快捷栏7={} 快捷栏8={} viewcell={}",
                    serverTicks, container.windowId, container.getCanCraft(stagedPlayer), lastClickCountSeen,
                    com.github.aeddddd.ae2enhanced.util.DefinitionStackCache.size(), container.inventorySlots.size(),
                    stagedPlayer.inventory.getStackInSlot(2), stagedPlayer.inventory.getStackInSlot(7),
                    stagedPlayer.inventory.getStackInSlot(8),
                    container.viewCellSlots().isEmpty() ? "空" : container.viewCellSlots().get(0).getStack().toString());
        }
        int mask = container.getPanelMask();
        if (mask == lastSeenPanelMask) {
            return;
        }
        int previous = lastSeenPanelMask;
        lastSeenPanelMask = mask;
        SplicedTermSlotPolicy policy = container.slotPolicy();
        int storageSlots = policy.slotsOf(SplicedTermSlotPolicy.MODULE_STORAGE).size();
        int viewCellSlots = policy.slotsOf(SplicedTermSlotPolicy.MODULE_VIEWCELL).size();
        boolean consistent = (SplicedTermModules.isVisible(mask, SplicedTermModules.STORAGE) == (storageSlots > 0))
                && (SplicedTermModules.isVisible(mask, SplicedTermModules.VIEWCELL) == (viewCellSlots > 0));
        recordAccept("E2-server", consistent,
                "panelMask " + maskText(previous) + " → " + maskText(mask)
                        + "；服务端白名单：storage 槽=" + storageSlots + "、viewcell 槽=" + viewCellSlots
                        + "、玩家槽=" + policy.playerSlots().size()
                        + "（可见位与登记数一致=" + consistent + "）");
    }

    private static String maskText(int mask) {
        return mask < 0 ? "未观测" : String.format("0x%X", mask);
    }

    /**
     * 在玩家脚边铺一条 4 格网络并准备终端。
     *
     * <p>方块一律走 {@code ItemBlock.placeBlockAt}（真实放置链路），因为
     * {@code AEBaseItemBlock.placeBlockAt} 会同时设置节点 owner 与朝向；
     * 直接 {@code setBlockState} 会漏掉这两步，导致安全终端没有属主、无线接入点可连接面错误。</p>
     */
    private static void stage(EntityPlayerMP player) {
        World world = player.getServerWorld();
        BlockPos feet = new BlockPos(
                MathHelper.floor(player.posX),
                MathHelper.floor(player.posY),
                MathHelper.floor(player.posZ));

        IBlocks blocks = AEApi.instance().definitions().blocks();
        Block energyCell = resolveBlock(blocks.energyCellCreative(), "creative_energy_cell");
        Block driveBlock = resolveBlock(blocks.drive(), "drive");
        Block securityBlock = resolveBlock(blocks.securityStation(), "security_station");
        Block accessPoint = resolveBlock(blocks.wirelessAccessPoint(), "wireless_access_point");
        if (energyCell == null || driveBlock == null || securityBlock == null || accessPoint == null) {
            fail("AE2 方块定义缺失，布景中止");
            return;
        }

        BlockPos energyPos = feet.add(2, 0, 0);
        BlockPos drivePos = feet.add(3, 0, 0);
        BlockPos securityPos = feet.add(4, 0, 0);
        BlockPos accessPointPos = feet.add(5, 0, 0);

        placeBlock(world, player, energyCell, energyPos, EnumFacing.UP);
        placeBlock(world, player, driveBlock, drivePos, EnumFacing.UP);
        placeBlock(world, player, securityBlock, securityPos, EnumFacing.UP);
        // 无线接入点：forward = 放置时传入的面，可连接面 = forward 的反面。
        // 传 EAST 让它的可连接面朝向 -X（即旁边那台安全终端），默认朝向会连不上网。
        placeBlock(world, player, accessPoint, accessPointPos, EnumFacing.EAST);

        TileEntity securityTile = world.getTileEntity(securityPos);
        if (!(securityTile instanceof TileSecurityStation)) {
            fail("安全终端方块实体缺失，布景中止");
            return;
        }
        long securityKey = ((TileSecurityStation) securityTile).getSecurityKey();

        // 64k 元件：一块最多 63 种物品，测试物品超过就再拿一块；
        // 先把测试物品注入元件本身，再插进驱动器，驱动器联网后内容即为网络库存。
        List<ItemStack> testItems = buildTestItems();
        List<ItemStack> cells = new ArrayList<ItemStack>();
        for (int start = 0; start < testItems.size(); start += CELL_TYPE_LIMIT) {
            ItemStack cell = AEApi.instance().definitions().items().cell64k().maybeStack(1).orElse(ItemStack.EMPTY);
            if (cell.isEmpty()) {
                fail("取不到 64k 物品元件，布景中止");
                return;
            }
            injectTestItems(cell, testItems.subList(start, Math.min(testItems.size(), start + CELL_TYPE_LIMIT)), player);
            cells.add(cell);
        }

        stagedDrivePos = drivePos;
        TileEntity driveTile = world.getTileEntity(drivePos);
        if (!(driveTile instanceof TileDrive)) {
            fail("ME 驱动器方块实体缺失，布景中止");
            return;
        }
        // 1.12.2 的 IItemHandler 没有 setStackInSlot（那是 IItemHandlerModifiable 的方法）
        net.minecraftforge.items.IItemHandlerModifiable driveInv =
                (net.minecraftforge.items.IItemHandlerModifiable) ((TileDrive) driveTile).getInternalInventory();
        // 先清空驱动器：G3 追加的元件（以及上一轮布景留下的）会随存档持久化，
        // 不清掉的话"基线物品数"每轮都不一样，G3 的前后对比就失效。
        for (int i = 0; i < driveInv.getSlots(); i++) {
            driveInv.setStackInSlot(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < cells.size() && i < driveInv.getSlots(); i++) {
            driveInv.setStackInSlot(i, cells.get(i));
        }
        AE2Enhanced.LOGGER.info(TAG + "测试物品 {} 种，装入 {} 块 64k 元件（驱动器槽 0~{}）",
                testItems.size(), cells.size(), cells.size() - 1);

        ItemStack newTerminal = new ItemStack(ItemRegistry.OMNI_WIRELESS_TERMINAL_II);
        ItemStack oldTerminal = new ItemStack(ItemRegistry.OMNI_WIRELESS_TERMINAL);
        linkTerminal(newTerminal, securityKey);
        linkTerminal(oldTerminal, securityKey);
        player.inventory.setInventorySlotContents(0, newTerminal);
        player.inventory.setInventorySlotContents(1, oldTerminal);
        // 快捷栏 2~4 放一点可搬运的物品：
        //   2 = 钻石（A16：shift-click 进存储槽）
        //   3 = 木棍（A18：shift-click 进合成矩阵必须被拒）
        //   4 = 原木（A14：放进合成矩阵应出现"原木→木板"预览）
        player.inventory.setInventorySlotContents(2, new ItemStack(Items.DIAMOND, 3));
        player.inventory.setInventorySlotContents(3, new ItemStack(Items.STICK, 8));
        player.inventory.setInventorySlotContents(4, new ItemStack(net.minecraft.init.Blocks.LOG, 1));
        //   5 = 9 铁锭（A15：放满 3x3 矩阵后编码样板）
        //   6 = 空白样板（A15：放进样板 IN 槽）
        //   7 = view cell（A17：插进 view cell 槽）
        player.inventory.setInventorySlotContents(5, new ItemStack(Items.IRON_INGOT, 9));
        player.inventory.setInventorySlotContents(6, AEApi.instance().definitions().materials()
                .blankPattern().maybeStack(1).orElse(ItemStack.EMPTY));
        player.inventory.setInventorySlotContents(7, AEApi.instance().definitions().items()
                .viewCell().maybeStack(1).orElse(ItemStack.EMPTY));
        // E 系（专用服务端）额外：8 = 第二个 view cell。
        // E2 要用"view cell 只进 viewcell 槽"这条独占分支做 A/B：
        // 面板可见 → shift-click 进 viewcell 槽；面板隐藏 → 服务端白名单为空 → 必须原地不动。
        // 需要两个 view cell 才能在同一次会话里做"拒绝后再验证放行"，而 A 系用不到快捷栏 8。
        if (dedicatedServer) {
            player.inventory.setInventorySlotContents(8, AEApi.instance().definitions().items()
                    .viewCell().maybeStack(1).orElse(ItemStack.EMPTY));
            // 独立服务端默认是生存模式（server.properties 的 gamemode 会被服务端自己改写）：
            // 布景与验收（放置/取放物品、右键开终端）统一按创造模式进行，避免掉血/饥饿干扰。
            if (player instanceof EntityPlayerMP) {
                ((EntityPlayerMP) player).setGameType(net.minecraft.world.GameType.CREATIVE);
            }
        }
        player.inventory.currentItem = 0;
        player.inventory.markDirty();

        AE2Enhanced.LOGGER.info(TAG + "布景完成: 网络 {} ~ {}，安全码 {}，新终端=快捷栏0 旧终端=快捷栏1（均已充能并绑定）",
                energyPos, accessPointPos, securityKey);
        stagedPlayer = player;
        stagedItemTypeCount = testItems.size();
        serverTicks = 0;
        layoutNbtChecked = false;
        lastSeenPanelMask = -1;

        recordAccept("A1", true, "布景完成: 4 方块网络 " + energyPos + " ~ " + accessPointPos
                + "；测试物品 " + testItems.size() + " 种 / " + cells.size() + " 块 64k 元件；"
                + "快捷栏 0=新终端 1=旧终端（已充能+绑定）"
                + (dedicatedServer ? "；专用服务端：8=第二个 view cell（E2 用）" : ""));
        checkConversionRecipe(world, oldTerminal);
    }

    /**
     * G3：往网络里追加几百种物品（独立服务端验收专用）。
     *
     * <p>做法与布景一致：把新物品注入若干块 64k 元件，再插进 ME 驱动器空槽（驱动器 10 槽，布景已用 3）。
     * 追加后网络物品总数从 135 涨到 4 倍左右，客户端那边 G3 就用"同一个查询的前后往返耗时"判断
     * 分页机制是否随总数线性退化。</p>
     */
    private static void injectGrowthItems() {
        if (stagedPlayer == null || stagedDrivePos == null) {
            return;
        }
        World world = stagedPlayer.getServerWorld();
        TileEntity tile = world.getTileEntity(stagedDrivePos);
        if (!(tile instanceof TileDrive)) {
            AE2Enhanced.LOGGER.warn(TAG + "G3 追加失败：驱动器方块实体不在 {}", stagedDrivePos);
            return;
        }
        net.minecraftforge.items.IItemHandlerModifiable driveInv =
                (net.minecraftforge.items.IItemHandlerModifiable) ((TileDrive) tile).getInternalInventory();
        List<ItemStack> extras = buildGrowthItems(400, buildTestItems());
        List<ItemStack> cells = new ArrayList<ItemStack>();
        for (int start = 0; start < extras.size(); start += CELL_TYPE_LIMIT) {
            ItemStack cell = AEApi.instance().definitions().items().cell64k().maybeStack(1).orElse(ItemStack.EMPTY);
            if (cell.isEmpty()) {
                break;
            }
            injectTestItems(cell, extras.subList(start, Math.min(extras.size(), start + CELL_TYPE_LIMIT)), stagedPlayer);
            cells.add(cell);
        }
        int inserted = 0;
        for (int i = 0; i < driveInv.getSlots() && inserted < cells.size(); i++) {
            if (driveInv.getStackInSlot(i).isEmpty()) {
                driveInv.setStackInSlot(i, cells.get(inserted));
                inserted++;
            }
        }
        growthItemCount = extras.size();
        AE2Enhanced.LOGGER.info(TAG + "NOTE G3-server 追加 {} 种物品（{} 块 64k 元件，驱动器槽 {}+），"
                + "网络物品总数应显著上涨", extras.size(), inserted, 3);
    }

    /**
     * 造一批"种类多、模型都在"的额外物品（G3 用）。
     *
     * <p>优先取注册表里没有子类型的物品（每个注册名一种），跳过布景已注入的那些，
     * 凑够 {@code count} 种。</p>
     */
    static List<ItemStack> buildGrowthItems(int count, List<ItemStack> already) {
        java.util.Set<String> used = new java.util.HashSet<String>();
        for (ItemStack stack : already) {
            if (stack != null && !stack.isEmpty() && stack.getItem().getRegistryName() != null) {
                used.add(stack.getItem().getRegistryName() + "@" + stack.getMetadata());
            }
        }
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (Item item : ForgeRegistries.ITEMS.getValues()) {
            if (out.size() >= count) {
                break;
            }
            if (item == null || item.getRegistryName() == null) {
                continue;
            }
            // 只取"无子类型"的物品：注册名唯一，注入时不会与已有种类重复
            if (item.getHasSubtypes()) {
                continue;
            }
            String key = item.getRegistryName() + "@0";
            if (!used.add(key)) {
                continue;
            }
            out.add(new ItemStack(item, 1));
        }
        return out;
    }

    /** 取方块定义对应的 Block。 */
    private static Block resolveBlock(appeng.api.definitions.IBlockDefinition definition, String name) {
        Block block = definition.maybeBlock().orElse(null);
        if (block == null) {
            AE2Enhanced.LOGGER.error(TAG + "取不到方块 {}", name);
        }
        return block;
    }

    /**
     * 用真实的物品放置链路放一个方块。
     *
     * <p>{@code Item.getItemFromBlock} 拿到的才是注册时构造的 {@code AEBaseItemBlock}；
     * {@code IBlockDefinition.maybeItemBlock()} 会新建一个原版 {@code ItemBlock}，走它放置不会
     * 设置节点 owner 与朝向。</p>
     */
    private static void placeBlock(World world, EntityPlayer player, Block block, BlockPos pos, EnumFacing side) {
        Item item = Item.getItemFromBlock(block);
        if (!(item instanceof ItemBlock)) {
            fail("方块 " + block + " 没有对应的 ItemBlock，无法按真实链路放置");
            return;
        }
        ItemBlock itemBlock = (ItemBlock) item;
        boolean placed = itemBlock.placeBlockAt(new ItemStack(itemBlock), player, world, pos, side,
                0.5F, 0.5F, 0.5F, block.getDefaultState());
        if (placed) {
            return;
        }
        // placeBlockAt 返回 false 最常见的原因是 World.setBlockState 认为"状态没变化"：
        // 重复布景时同一位置已经是同一个方块（连同方块实体/owner/朝向都还在），按成功处理。
        if (world.getBlockState(pos).getBlock() == block) {
            AE2Enhanced.LOGGER.info(TAG + "{} 已存在 @ {}（重复布景，跳过放置）", block.getRegistryName(), pos);
            return;
        }
        AE2Enhanced.LOGGER.error(TAG + "放置 {} 失败 @ {}", block.getRegistryName(), pos);
    }

    /**
     * 注入用的测试物品：130+ 种（含 16 色羊毛/染色玻璃/染色粘土/地毯/染料/混凝土粉末等变体），
     * 数量覆盖 1 ~ 32000。
     *
     * <p>种类数刻意超过一页可显示的格数（仓库面板约 20+ 列 × 5 行），这样"滚动"这一步才有意义；
     * 名字带 Iron 的 4 件用于搜索验证。</p>
     */
    static List<ItemStack> buildTestItems() {
        List<ItemStack> out = new ArrayList<ItemStack>();
        for (int i = 0; i < 16; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.WOOL, 1, i));
        }
        for (int i = 0; i < 16; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.STAINED_GLASS, 1, i));
        }
        for (int i = 0; i < 16; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.STAINED_HARDENED_CLAY, 1, i));
        }
        for (int i = 0; i < 16; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.CARPET, 1, i));
        }
        for (int i = 0; i < 16; i++) {
            out.add(new ItemStack(Items.DYE, 1, i));
        }
        for (int i = 0; i < 16; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.CONCRETE_POWDER, 1, i));
        }
        for (int i = 0; i < 6; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.PLANKS, 1, i));
        }
        // 注意：minecraft:log 只有 meta 0~3 有模型（4/5 会渲染成紫黑"缺模型"格），这里刻意只放 0~3
        for (int i = 0; i < 4; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.LOG, 1, i));
        }
        for (int i = 0; i < 2; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.LOG2, 1, i));
        }
        for (int i = 0; i < 6; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.SAPLING, 1, i));
        }
        for (int i = 0; i < 4; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.LEAVES, 1, i));
        }
        for (int i = 0; i < 3; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.SANDSTONE, 1, i));
        }
        for (int i = 0; i < 7; i++) {
            out.add(new ItemStack(net.minecraft.init.Blocks.STONE, 1, i));
        }
        // 搜索 "iron" 的验证目标：这 4 件的名字里都带 Iron
        out.add(new ItemStack(Items.IRON_INGOT, 64));
        out.add(new ItemStack(net.minecraft.init.Blocks.IRON_BLOCK, 8));
        out.add(new ItemStack(net.minecraft.init.Blocks.IRON_ORE, 1));
        out.add(new ItemStack(Items.IRON_DOOR, 3));
        // 大数量显示（k 后缀 / 万级）
        out.add(new ItemStack(Items.DIAMOND, 32000));
        out.add(new ItemStack(Items.REDSTONE, 20000));
        out.add(new ItemStack(Items.COAL, 1000));
        return out;
    }

    /** 往一块 64k 元件里注入指定的测试物品（数量覆盖 1 ~ 32000）。 */
    private static void injectTestItems(ItemStack cell, List<ItemStack> items, EntityPlayer player) {
        IStorageChannel<IAEItemStack> channel =
                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        ICellInventoryHandler<IAEItemStack> handler =
                AEApi.instance().registries().cell().getCellInventory(cell, null, channel);
        if (handler == null) {
            fail("64k 元件没有可用的 cell inventory handler，无法注入测试物品");
            return;
        }
        IActionSource source = new PlayerSource(player, null);
        int injected = 0;
        for (ItemStack stack : items) {
            IAEItemStack leftover = handler.injectItems(AEItemStack.fromItemStack(stack), Actionable.MODULATE, source);
            if (leftover != null && leftover.getStackSize() > 0) {
                AE2Enhanced.LOGGER.warn(TAG + "注入 {} 有剩余 {}（元件容量不足）",
                        stack.getItem().getRegistryName(), leftover.getStackSize());
                continue;
            }
            injected++;
        }
        AE2Enhanced.LOGGER.info(TAG + "一块 64k 元件注入 {}/{} 种测试物品", injected, items.size());
    }

    /**
     * 充能 + 绑定终端。
     *
     * <p>绑定与 {@code ContainerSecurityStation} 的生效路径完全一致：先取无线处理器
     * （{@code INetworkEncodable}），再把安全终端的 {@code securityKey} 写进物品 NBT。</p>
     */
    private static void linkTerminal(ItemStack terminal, long securityKey) {
        if (terminal.isEmpty()) {
            return;
        }
        if (terminal.getItem() instanceof IAEItemPowerStorage) {
            ((IAEItemPowerStorage) terminal.getItem())
                    .injectAEPower(terminal, Double.MAX_VALUE, Actionable.MODULATE);
        }
        IWirelessTermHandler handler = AEApi.instance().registries().wireless().getWirelessTerminalHandler(terminal);
        if (handler == null) {
            AE2Enhanced.LOGGER.error(TAG + "终端 {} 没有无线处理器，无法绑定",
                    terminal.getItem().getRegistryName());
            return;
        }
        handler.setEncryptionKey(terminal, String.valueOf(securityKey), "");
    }

    // ==================== 服务端侧验收 ====================

    /**
     * A24：把旧终端放进真实的 {@code InventoryCrafting}，在<strong>实时配方注册表</strong>里找匹配的配方，
     * 断言产物是新终端且 NBT（storageId / encryptionKey / omni_layout）逐项保留。
     *
     * <p>与单测的区别：这里的配方实例来自游戏内注册表（
     * {@code ForgeRegistries.RECIPES}），因此同时验证了"配方真的注册进游戏了"。</p>
     */
    private static void checkConversionRecipe(World world, ItemStack oldTerminal) {
        ItemStack source = new ItemStack(ItemRegistry.OMNI_WIRELESS_TERMINAL);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("encryptionKey", "123456789");
        tag.setString(OmniTerminalLayoutNbt.KEY_LAYOUT, "{\"probe\":\"a24\"}");
        // storageId 是 UUID 类型：用 ItemStack 自己的 API 写，保证类型正确
        source.setTagCompound(tag);
        source.getTagCompound().setUniqueId("storageId", java.util.UUID.randomUUID());

        InventoryCrafting inv = new InventoryCrafting(new ProbeContainer(), 3, 3);
        inv.setInventorySlotContents(0, source);

        IRecipe match = null;
        for (IRecipe recipe : ForgeRegistries.RECIPES.getValuesCollection()) {
            try {
                if (recipe.matches(inv, world)) {
                    match = recipe;
                    break;
                }
            } catch (Throwable ignored) {
                // 某个配方对空网格不健壮：跳过，不影响本次验收
            }
        }
        if (match == null) {
            recordAccept("A24", false, "实时配方注册表里没有能匹配\"单个旧终端\"的配方");
            return;
        }
        ItemStack result = match.getCraftingResult(inv);
        boolean rightItem = !result.isEmpty() && result.getItem() == ItemRegistry.OMNI_WIRELESS_TERMINAL_II;
        boolean keptKey = "123456789".equals(tagOf(result).getString("encryptionKey"));
        boolean keptStorage = tagOf(result).hasUniqueId("storageId")
                && tagOf(result).getUniqueId("storageId").equals(source.getTagCompound().getUniqueId("storageId"));
        boolean keptLayout = "{\"probe\":\"a24\"}".equals(tagOf(result).getString(OmniTerminalLayoutNbt.KEY_LAYOUT));
        boolean singleItem = result.getCount() == 1;
        recordAccept("A24", rightItem && keptKey && keptStorage && keptLayout && singleItem,
                "recipe=" + match.getRegistryName() + " result=" + result.getItem().getRegistryName()
                        + " count=" + result.getCount()
                        + " nbt[encKey=" + keptKey + " storageId=" + keptStorage + " layout=" + keptLayout + "]");
    }

    private static NBTTagCompound tagOf(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        return tag == null ? new NBTTagCompound() : tag;
    }

    /** A21b：终端物品 NBT 上是否已经写上 {@code omni_layout}（布局持久化的服务端权威副本）。 */
    private static void checkLayoutNbt() {
        if (stagedPlayer == null) {
            return;
        }
        ItemStack held = stagedPlayer.inventory.getStackInSlot(0);
        if (held.isEmpty()) {
            return;
        }
        String json = OmniTerminalLayoutNbt.read(held);
        if (json == null || json.isEmpty()) {
            return;
        }
        layoutNbtChecked = true;
        recordAccept("A21b", true, "新终端物品 NBT 已写入 omni_layout（" + json.length() + " 字符）");
    }

    private static void fail(String reason) {
        AE2Enhanced.LOGGER.error(TAG + reason);
        AE2Enhanced.LOGGER.info(FAIL_MARKER);
    }

    /** 只为构造 {@code InventoryCrafting} 用的空容器（1.12.2 的 {@code Container} 是抽象类）。 */
    private static final class ProbeContainer extends Container {
        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return false;
        }
    }
}
