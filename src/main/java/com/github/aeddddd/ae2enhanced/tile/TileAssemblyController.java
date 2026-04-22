package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TileAssemblyController extends TileEntity implements ICraftingProvider, ITickable {

    public static final int UPGRADE_SLOTS = 6;
    public static final int PATTERN_SLOTS = 36;
    public static final int TOTAL_SLOTS = UPGRADE_SLOTS + PATTERN_SLOTS;

    private static final IActionSource MACHINE_SOURCE = new IActionSource() {
        @Override public Optional<EntityPlayer> player() { return Optional.empty(); }
        @Override public Optional<appeng.api.networking.security.IActionHost> machine() { return Optional.empty(); }
        @Override public <T> Optional<T> context(Class<T> clazz) { return Optional.empty(); }
    };

    private int tickCounter = 0;
    private boolean formed = false;
    private BlockPos activeMeInterfacePos = null;
    private boolean networkActive = false;
    private boolean networkPowered = false;

    private final ItemStackHandler itemHandler = new ItemStackHandler(TOTAL_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
            if (slot >= UPGRADE_SLOTS && world != null && !world.isRemote) {
                patternsDirty = true;
            }
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot < UPGRADE_SLOTS) {
                return stack.getItem() instanceof ItemUpgradeCard;
            }
            return stack.getItem() instanceof ICraftingPatternItem;
        }
    };

    /** 缓存样板是否为纯虚拟合成（getRemainingItems 全空），String key 避免 hash 碰撞 */
    private final Map<String, Boolean> patternVirtualCache = new HashMap<>();
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    private final List<Integer> jobTimers = new ArrayList<>();
    private boolean patternsDirty = false;
    private int patternRefreshTicks = 0;

    /** 当前合成任务的 ActionSource（由 Mixin 在 pushPattern 前设置），用于让 AE2 正确追踪产物 */
    private IActionSource currentSource = null;

    public void setCurrentActionSource(IActionSource source) {
        this.currentSource = source;
    }

    private IActionSource getEffectiveSource() {
        return currentSource != null ? currentSource : MACHINE_SOURCE;
    }

    public boolean isFormed() {
        return formed;
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    public long getParallelCap() {
        int parallelCount = 0;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() instanceof ItemUpgradeCard && stack.getMetadata() == ItemUpgradeCard.META_PARALLEL) {
                parallelCount++;
            }
        }
        if (parallelCount >= 5) {
            return Long.MAX_VALUE;
        }
        long cap = 64;
        for (int i = 0; i < parallelCount; i++) {
            cap = cap * 32;
            if (cap > 67108864) {
                cap = 67108864;
                break;
            }
        }
        return cap;
    }

    public synchronized boolean isMeInterfaceActive(BlockPos mePos) {
        if (!formed || world == null) return false;
        if (activeMeInterfacePos != null && activeMeInterfacePos.equals(mePos)) {
            return true;
        }
        if (activeMeInterfacePos != null) {
            if (world.getTileEntity(activeMeInterfacePos) instanceof TileAssemblyMeInterface) {
                TileAssemblyMeInterface activeMe = (TileAssemblyMeInterface) world.getTileEntity(activeMeInterfacePos);
                if (activeMe.getControllerPos() != null && activeMe.getControllerPos().equals(pos)) {
                    return false;
                }
            }
            activeMeInterfacePos = null;
        }
        activeMeInterfacePos = mePos;
        markDirty();
        return true;
    }

    public BlockPos getActiveMeInterfacePos() {
        return activeMeInterfacePos;
    }

    public void assemble() {
        if (!formed) {
            formed = true;
            markDirty();
            if (world != null && !world.isRemote) {
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
            }
        }
    }

    public void disassemble() {
        if (formed) {
            formed = false;
            markDirty();
            pendingOutputs.clear();
            jobTimers.clear();
            patternVirtualCache.clear();
            if (world != null && !world.isRemote) {
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
            }
        }
    }

    public boolean isNetworkActive() {
        return networkActive;
    }

    public boolean isNetworkPowered() {
        return networkPowered;
    }

    @Override
    public void update() {
        if (world == null || world.isRemote) return;

        // 样板变化时触发 AE 网络重新扫描
        if (patternsDirty && activeMeInterfacePos != null) {
            patternsDirty = false;
            patternRefreshTicks = 5;
        }
        if (patternRefreshTicks > 0 && activeMeInterfacePos != null) {
            if (--patternRefreshTicks == 0) {
                TileEntity te = world.getTileEntity(activeMeInterfacePos);
                if (te instanceof TileAssemblyMeInterface) {
                    TileAssemblyMeInterface me = (TileAssemblyMeInterface) te;
                    appeng.me.helpers.AENetworkProxy proxy = me.getProxy();
                    IGridNode node = proxy.getNode();
                    if (node != null && node.getGrid() != null) {
                        // 发送 MENetworkCraftingPatternChange 事件，通知 AE2 重新扫描该节点的样板
                        appeng.api.networking.events.MENetworkCraftingPatternChange event =
                            new appeng.api.networking.events.MENetworkCraftingPatternChange(me, node);
                        node.getGrid().postEvent(event);
                    }
                }
            }
        }

        // 注入待输出物品（批量）
        if (!pendingOutputs.isEmpty()) {
            tryInjectPendingOutputs();
        }

        // 递减所有 job timer
        for (int i = jobTimers.size() - 1; i >= 0; i--) {
            int ticks = jobTimers.get(i) - 1;
            if (ticks <= 0) {
                jobTimers.remove(i);
            } else {
                jobTimers.set(i, ticks);
            }
        }

        // 每 20 ticks 刷新网络状态
        tickCounter++;
        if (tickCounter % 20 != 0) return;

        boolean newActive = false;
        boolean newPowered = false;
        if (formed && activeMeInterfacePos != null) {
            TileEntity te = world.getTileEntity(activeMeInterfacePos);
            if (te instanceof TileAssemblyMeInterface) {
                TileAssemblyMeInterface me = (TileAssemblyMeInterface) te;
                appeng.me.helpers.AENetworkProxy proxy = me.getProxy();
                if (proxy != null) {
                    newActive = proxy.isActive();
                    newPowered = proxy.isPowered();
                }
            }
        }

        if (newActive != networkActive || newPowered != networkPowered) {
            networkActive = newActive;
            networkPowered = newPowered;
            markDirty();
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        }
    }

    // ---------- 产物注入（BatchExporter 风格，合并后批量注入） ----------

    private void tryInjectPendingOutputs() {
        if (activeMeInterfacePos == null) return;
        TileEntity te = world.getTileEntity(activeMeInterfacePos);
        if (!(te instanceof TileAssemblyMeInterface)) return;

        TileAssemblyMeInterface me = (TileAssemblyMeInterface) te;
        appeng.me.helpers.AENetworkProxy proxy = me.getProxy();
        if (proxy == null) return;

        IGridNode node = proxy.getNode();
        if (node == null || node.getGrid() == null) return;

        IStorageGrid storage = node.getGrid().getCache(IStorageGrid.class);
        if (storage == null) return;

        IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IMEMonitor<IAEItemStack> monitor = storage.getInventory(channel);

        // 合并相同物品的 stack，避免逐个注入
        Map<String, Long> merged = new LinkedHashMap<>();
        Map<String, ItemStack> prototypes = new HashMap<>();

        for (ItemStack stack : pendingOutputs) {
            if (stack.isEmpty()) continue;
            String key = getStackKey(stack);
            merged.merge(key, (long) stack.getCount(), Long::sum);
            prototypes.putIfAbsent(key, stack.copy());
        }
        pendingOutputs.clear();

        for (Map.Entry<String, Long> entry : merged.entrySet()) {
            ItemStack proto = prototypes.get(entry.getKey());
            long count = entry.getValue();

            IAEItemStack aeStack = channel.createStack(proto);
            if (aeStack == null) continue;

            while (count > 0) {
                long batch = Math.min(count, Integer.MAX_VALUE);
                aeStack.setStackSize(batch);
                IAEItemStack remainder = monitor.injectItems(aeStack, Actionable.MODULATE, getEffectiveSource());

                if (remainder == null || remainder.getStackSize() == 0) {
                    count = 0;
                } else {
                    count = remainder.getStackSize();
                    // 网络满载，将剩余转回 pendingOutputs，下一 tick 再试
                    ItemStack leftover = proto.copy();
                    leftover.setCount((int) Math.min(count, Integer.MAX_VALUE));
                    pendingOutputs.add(leftover);
                    break;
                }
            }
        }
    }

    private String getStackKey(ItemStack stack) {
        String key = Objects.toString(stack.getItem().getRegistryName()) + "#" + stack.getMetadata();
        if (stack.hasTagCompound()) {
            key += "#" + stack.getTagCompound().toString();
        }
        return key;
    }

    // ---------- ICraftingMedium ----------

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        if (world == null || world.isRemote || isBusy()) return false;
        if (!patternDetails.isCraftable()) return false;

        String key = getPatternKey(patternDetails);
        Boolean cached = patternVirtualCache.get(key);
        boolean isVirtual;
        IRecipe recipe = null;
        NonNullList<ItemStack> remaining = null;

        if (cached != null) {
            isVirtual = cached;
        } else {
            recipe = CraftingManager.findMatchingRecipe(table, world);
            if (recipe == null) return false;
            remaining = recipe.getRemainingItems(table);
            isVirtual = remaining.stream().allMatch(ItemStack::isEmpty);
            patternVirtualCache.put(key, isVirtual);
        }

        if (isVirtual) {
            return executeVirtualCrafting(patternDetails, table);
        } else {
            if (recipe == null) {
                recipe = CraftingManager.findMatchingRecipe(table, world);
                if (recipe == null) return false;
                remaining = recipe.getRemainingItems(table);
            }
            return executeRealCrafting(patternDetails, table, recipe, remaining);
        }
    }

    /**
     * 虚拟轨道：普通合成，直接产出 1 份产物注入 AE 网络。
     * 并行度由 isBusy() 控制：AE2 会多次调用 pushPattern，每次 1 份。
     * 网络未就绪时返回 false，让 AE 重试。
     */
    private boolean executeVirtualCrafting(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        ItemStack output = patternDetails.getOutput(table, world);
        if (output.isEmpty()) return false;

        // 网络未就绪：拒绝，让 AE 稍后重试
        if (activeMeInterfacePos == null) return false;
        TileEntity te = world.getTileEntity(activeMeInterfacePos);
        if (!(te instanceof TileAssemblyMeInterface)) return false;
        appeng.me.helpers.AENetworkProxy proxy = ((TileAssemblyMeInterface) te).getProxy();
        IGridNode node = proxy.getNode();
        if (node == null || node.getGrid() == null) return false;

        IStorageGrid storage = node.getGrid().getCache(IStorageGrid.class);
        IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IMEMonitor<IAEItemStack> monitor = storage.getInventory(channel);

        IAEItemStack aeOutput = channel.createStack(output);
        if (aeOutput == null) return false;

        // 只注入 1 份（AE2 每次 pushPattern 只发配 1 份输入）
        aeOutput.setStackSize(output.getCount());
        IAEItemStack remainder = monitor.injectItems(aeOutput, Actionable.MODULATE, getEffectiveSource());

        if (remainder == null || remainder.getStackSize() == 0) {
            // 全部注入成功
            jobTimers.add(getCraftingTicks());
            return true;
        }

        // 网络满载：将剩余放入 pendingOutputs，下一 tick 再试
        long remCount = remainder.getStackSize();
        while (remCount > 0) {
            int batch = (int) Math.min(remCount, output.getMaxStackSize());
            ItemStack stack = output.copy();
            stack.setCount(batch);
            pendingOutputs.add(stack);
            remCount -= batch;
        }
        jobTimers.add(getCraftingTicks());
        return true;
    }

    /**
     * 真实轨道：特例合成（含耐久扣减、容器返还等）。
     * 输出和剩余物品均加入 pendingOutputs，由 tryInjectPendingOutputs 统一注入。
     * TODO: InventoryCrafting 中工具的实际耐久扣减逻辑待细化
     */
    private boolean executeRealCrafting(ICraftingPatternDetails patternDetails, InventoryCrafting table,
                                        IRecipe recipe, NonNullList<ItemStack> remaining) {
        ItemStack output = recipe.getCraftingResult(table);
        if (output.isEmpty()) return false;

        pendingOutputs.add(output.copy());

        for (ItemStack rem : remaining) {
            if (!rem.isEmpty()) {
                pendingOutputs.add(rem.copy());
            }
        }

        jobTimers.add(getCraftingTicks());
        return true;
    }

    private int getCraftingTicks() {
        int ticks = 20;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            ItemStack upgrade = itemHandler.getStackInSlot(i);
            if (upgrade.getItem() instanceof ItemUpgradeCard && upgrade.getMetadata() == ItemUpgradeCard.META_SPEED) {
                ticks = Math.max(ticks / 2, 1);
            }
        }
        return ticks;
    }

    @Override
    public boolean isBusy() {
        long cap = getParallelCap();
        int intCap = (cap >= Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) cap;
        return jobTimers.size() >= intCap;
    }

    public int getJobCount() {
        return jobTimers.size();
    }

    /**
     * 供 Mixin 调用：检查指定样板是否已被缓存为纯虚拟合成（无剩余物品）。
     */
    public boolean isVirtualPattern(ICraftingPatternDetails details) {
        String key = getPatternKey(details);
        Boolean cached = patternVirtualCache.get(key);
        return cached != null && cached;
    }

    /**
     * 供 Mixin 调用：批量执行虚拟合成，一次性扣除原材料并注入 batchSize 份产物。
     */
    public boolean executeBatch(ICraftingPatternDetails details, long batchSize) {
        // 实际原料扣除与产物注入已移至 MixinCraftingCPUCluster.batchProcessVirtualTasks
        // 中直接操作 CraftingCPUCluster.getInventory() 的内部列表，
        // 以保证嵌套配方时产物能被上层 canCraft() 正确识别。
        // 这里仅作为 batch 可行性确认（控制器在线、接口有效）。
        if (world == null || world.isRemote || activeMeInterfacePos == null) return false;
        if (batchSize <= 0) return false;

        TileEntity te = world.getTileEntity(activeMeInterfacePos);
        if (!(te instanceof TileAssemblyMeInterface)) return false;

        appeng.me.helpers.AENetworkProxy proxy = ((TileAssemblyMeInterface) te).getProxy();
        IGridNode node = proxy.getNode();
        return node != null && node.getGrid() != null;
    }

    // ---------- ICraftingProvider ----------

    @Override
    public void provideCrafting(ICraftingProviderHelper craftingTracker) {
        if (world == null || world.isRemote) return;

        // 使用 TileAssemblyMeInterface 作为 medium 注册样板，
        // 这样 CraftingGridCache.getMediums() 返回的是 TileAssemblyMeInterface 而不是 TileAssemblyController
        appeng.api.networking.crafting.ICraftingMedium medium = this;
        if (activeMeInterfacePos != null) {
            TileEntity te = world.getTileEntity(activeMeInterfacePos);
            if (te instanceof TileAssemblyMeInterface) {
                medium = (TileAssemblyMeInterface) te;
            }
        }

        for (int i = UPGRADE_SLOTS; i < TOTAL_SLOTS; i++) {
            ItemStack stack = itemHandler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() instanceof ICraftingPatternItem) {
                ICraftingPatternDetails pattern = ((ICraftingPatternItem) stack.getItem()).getPatternForItem(stack, world);
                if (pattern != null && pattern.isCraftable()) {
                    craftingTracker.addCraftingOption(medium, pattern);
                    prefillVirtualCache(pattern);
                }
            }
        }
    }

    /**
     * 预填充 patternVirtualCache，避免 CPU 首次派发任务时因缓存未命中而回退到 AE2 原生 pushPattern 路径。
     * 回退会导致：1) 性能骤降（逐次处理）；2) waitingFor 残留（原生逻辑添加记录但产物直接进网络，无法被 injectItems 清除）。
     */
    private void prefillVirtualCache(ICraftingPatternDetails pattern) {
        if (world == null || world.isRemote) return;
        String key = getPatternKey(pattern);
        if (patternVirtualCache.containsKey(key)) return;
        if (!pattern.isCraftable()) {
            patternVirtualCache.put(key, false);
            return;
        }

        IAEItemStack[] inputs = pattern.getInputs();
        InventoryCrafting ic = new InventoryCrafting(new net.minecraft.inventory.Container() {
            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer playerIn) {
                return false;
            }
        }, 3, 3);

        for (int i = 0; i < inputs.length && i < 9; i++) {
            ic.setInventorySlotContents(i, inputs[i] != null ? inputs[i].createItemStack() : ItemStack.EMPTY);
        }

        IRecipe recipe = CraftingManager.findMatchingRecipe(ic, world);
        if (recipe != null) {
            NonNullList<ItemStack> remaining = recipe.getRemainingItems(ic);
            boolean isVirtual = remaining.stream().allMatch(ItemStack::isEmpty);
            patternVirtualCache.put(key, isVirtual);
        } else {
            patternVirtualCache.put(key, false);
        }
    }

    private String getPatternKey(ICraftingPatternDetails pattern) {
        ItemStack stack = pattern.getPattern();
        String key = Objects.toString(stack.getItem().getRegistryName()) + "#" + stack.getMetadata();
        if (stack.hasTagCompound()) {
            key += "#" + stack.getTagCompound().toString();
        }
        return key;
    }

    // ---------- Capability ----------

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }

    @Nullable
    @Override
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(itemHandler);
        }
        return super.getCapability(capability, facing);
    }

    // ---------- NBT ----------

    @Override
    public void onLoad() {
        // 强制同步 formed 状态到客户端，避免加载后客户端 formed 仍为默认值 false，
        // 导致容器槽位数量不一致（服务端 78 槽 vs 客户端 36 槽），引发槽位索引映射错误
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.formed = compound.getBoolean("formed");
        this.networkActive = compound.getBoolean("networkActive");
        this.networkPowered = compound.getBoolean("networkPowered");
        if (compound.hasKey("activeMeX")) {
            activeMeInterfacePos = new BlockPos(
                compound.getInteger("activeMeX"),
                compound.getInteger("activeMeY"),
                compound.getInteger("activeMeZ")
            );
        }
        if (compound.hasKey("items")) {
            itemHandler.deserializeNBT(compound.getCompoundTag("items"));
        }
        if (compound.hasKey("pendingOutputs")) {
            pendingOutputs.clear();
            NBTTagList list = compound.getTagList("pendingOutputs", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                Item item = Item.getByNameOrId(tag.getString("id"));
                int count = tag.getInteger("Count"); // 自定义格式用 int 存 count
                int meta = tag.getInteger("Damage");
                ItemStack stack = new ItemStack(item, count, meta);
                if (tag.hasKey("tag", Constants.NBT.TAG_COMPOUND)) {
                    stack.setTagCompound(tag.getCompoundTag("tag"));
                }
                pendingOutputs.add(stack);
            }
        }
        // 存档加载后立即预填充虚拟缓存，避免 AE2 网络扫描前下单时缓存为空
        if (world != null && !world.isRemote) {
            for (int i = UPGRADE_SLOTS; i < TOTAL_SLOTS; i++) {
                ItemStack stack = itemHandler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (stack.getItem() instanceof ICraftingPatternItem) {
                    ICraftingPatternDetails pattern = ((ICraftingPatternItem) stack.getItem()).getPatternForItem(stack, world);
                    if (pattern != null && pattern.isCraftable()) {
                        prefillVirtualCache(pattern);
                    }
                }
            }
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("formed", formed);
        compound.setBoolean("networkActive", networkActive);
        compound.setBoolean("networkPowered", networkPowered);
        if (activeMeInterfacePos != null) {
            compound.setInteger("activeMeX", activeMeInterfacePos.getX());
            compound.setInteger("activeMeY", activeMeInterfacePos.getY());
            compound.setInteger("activeMeZ", activeMeInterfacePos.getZ());
        }
        compound.setTag("items", itemHandler.serializeNBT());

        NBTTagList list = new NBTTagList();
        for (ItemStack stack : pendingOutputs) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", Objects.toString(stack.getItem().getRegistryName()));
            tag.setInteger("Count", stack.getCount()); // int 存 count，突破 byte 限制
            tag.setInteger("Damage", stack.getMetadata());
            if (stack.hasTagCompound()) {
                tag.setTag("tag", stack.getTagCompound().copy());
            }
            list.appendTag(tag);
        }
        compound.setTag("pendingOutputs", list);
        return compound;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        // 使用 writeToNBT 保证字段完整，再移除 pendingOutputs 避免网络包膨胀。
        // b6f8b78 之前直接用 writeToNBT 没有问题；改为手动构造后导致某些字段
        // 在客户端初始化时不同步，进而引发容器槽位错位。
        NBTTagCompound tag = writeToNBT(new NBTTagCompound());
        tag.removeTag("pendingOutputs");
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromNBT(tag);
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
