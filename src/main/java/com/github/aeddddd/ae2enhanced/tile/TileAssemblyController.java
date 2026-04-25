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
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleCraftingHelper;
import com.github.aeddddd.ae2enhanced.item.ItemUpgradeCard;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;
import net.minecraft.entity.EntityLivingBase;
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
import net.minecraft.util.DamageSource;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ITickable;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;


import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TileAssemblyController extends TileEntity implements ICraftingProvider, ITickable {

    public static final int UPGRADE_SLOTS = 6;
    public static final int PATTERN_SLOTS_PER_PAGE = 96; // 16×6
    public static final int PATTERN_PAGES_BASE = 5;               // 基础页数
    public static final int PATTERN_PAGES_PER_CAPACITY = 5;       // 每张扩容升级卡增加的页数
    public static final int PATTERN_PAGES_MAX = 30;               // 上限页数
    public static final int PATTERN_SLOTS_MAX = PATTERN_SLOTS_PER_PAGE * PATTERN_PAGES_MAX; // 2880
    public static final int TOTAL_SLOTS_MAX = UPGRADE_SLOTS + PATTERN_SLOTS_MAX;            // 2886
    public static final int TOTAL_SLOTS_BASE = UPGRADE_SLOTS + PATTERN_SLOTS_PER_PAGE * PATTERN_PAGES_BASE; // 486

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
    private int batchCooldown = 0;
    private boolean batchBusy = false;

    private final PatternItemHandler itemHandler = new PatternItemHandler(TOTAL_SLOTS_BASE);

    /** 自定义 ItemStackHandler，支持动态容量扩展 + 扩容升级取出限制 */
    public class PatternItemHandler extends ItemStackHandler {
        PatternItemHandler(int size) { super(size); }

        @Override
        protected void onContentsChanged(int slot) {
            TileAssemblyController.this.markDirty();
            // 强制同步到客户端，修复'取出升级后重新打开 GUI 发现升级还在原位'的同步延迟问题
            if (world != null && !world.isRemote) {
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 2);
            }
            if (slot >= UPGRADE_SLOTS && world != null && !world.isRemote) {
                patternsDirty = true;
            }
            // 扩容升级增加时自动扩展容量
            if (slot == ItemUpgradeCard.META_CAPACITY && world != null && !world.isRemote) {
                ensurePatternCapacity();
            }
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot < UPGRADE_SLOTS) {
                return stack.getItem() instanceof ItemUpgradeCard;
            }
            return stack.getItem() instanceof ICraftingPatternItem;
        }

        /** 扩容升级取出限制：如果扩展页面留有样板，禁止提取 */
        @Override
        @Nonnull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= stacks.size()) return ItemStack.EMPTY;
            if (slot == ItemUpgradeCard.META_CAPACITY && !simulate) {
                ItemStack current = getStackInSlot(slot);
                int newCount = Math.max(0, current.getCount() - amount);
                if (!canReduceCapacity(newCount)) {
                    return ItemStack.EMPTY;
                }
            }
            return super.extractItem(slot, amount, simulate);
        }

        public void setCapacity(int newSize) {
            if (newSize == stacks.size()) return;
            NonNullList<ItemStack> newStacks = NonNullList.withSize(newSize, ItemStack.EMPTY);
            for (int i = 0; i < Math.min(stacks.size(), newSize); i++) {
                newStacks.set(i, stacks.get(i));
            }
            stacks = newStacks;
        }

        /** 越界保护：客户端 itemHandler 容量可能尚未同步，避免 ArrayIndexOutOfBoundsException */
        @Override
        @Nonnull
        public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= stacks.size()) return ItemStack.EMPTY;
            return super.getStackInSlot(slot);
        }

        @Override
        @Nonnull
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (slot < 0 || slot >= stacks.size()) return stack;
            return super.insertItem(slot, stack, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0 || slot >= stacks.size()) return 0;
            return super.getSlotLimit(slot);
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            if (slot < 0 || slot >= stacks.size()) return;
            super.setStackInSlot(slot, stack);
        }
    }

    /** 缓存样板是否为纯虚拟合成（getRemainingItems 全空），String key 避免 hash 碰撞 */
    private final Map<ICraftingPatternDetails, Boolean> patternVirtualCache = new HashMap<>();
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    private final List<Integer> jobTimers = new ArrayList<>();
    private final Map<UUID, Integer> eventHorizonStrikes = new HashMap<>();
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

    @Override
    public double getMaxRenderDistanceSquared() {
        return 65536.0; // 256 格渲染距离，确保远处也能看到黑洞效果
    }

    public ItemStackHandler getItemHandler() {
        return itemHandler;
    }

    /**
     * 获取当前并行上限。并行升级卡固定在槽位 0，堆叠数量即为安装数量。
     * 0 张 = 64，每多 1 张 ×32，5 张 = Long.MAX_VALUE。
     */
    public long getParallelCap() {
        ItemStack stack = itemHandler.getStackInSlot(0);
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemUpgradeCard) || stack.getMetadata() != ItemUpgradeCard.META_PARALLEL) {
            return 64;
        }
        int count = stack.getCount();
        if (count >= 5) return Long.MAX_VALUE;
        long cap = 64;
        for (int i = 0; i < count; i++) {
            cap = cap * 32;
            if (cap > 67108864) return 67108864;
        }
        return cap;
    }

    /**
     * 获取当前可用的样板页数。基础 5 页，每张扩容升级卡 +5 页，上限 30 页。
     */
    public int getPatternPages() {
        ItemStack stack = itemHandler.getStackInSlot(ItemUpgradeCard.META_CAPACITY);
        int count = 0;
        if (!stack.isEmpty() && stack.getItem() instanceof ItemUpgradeCard
                && stack.getMetadata() == ItemUpgradeCard.META_CAPACITY) {
            count = stack.getCount();
        }
        int pages = PATTERN_PAGES_BASE + count * PATTERN_PAGES_PER_CAPACITY;
        return Math.min(pages, PATTERN_PAGES_MAX);
    }

    /**
     * 客户端：在几何中心周围生成紫色 REDSTONE 粒子，模拟黑洞粒子光环。
     * 粒子沿切向运动并螺旋向内，颜色为偏紫的随机色调。
     */
    private void spawnBlackHoleParticles() {
        BlockPos origin = AssemblyStructure.getOriginFromController(pos);
        double cx = origin.getX() + 0.5;
        double cy = origin.getY() + 0.5;
        double cz = origin.getZ() + 0.5;

        for (int i = 0; i < 8; i++) {
            double angle = world.rand.nextDouble() * Math.PI * 2;
            double radius = 2.8 + world.rand.nextDouble() * 1.8;
            double px = cx + Math.cos(angle) * radius;
            double pz = cz + Math.sin(angle) * radius;
            double py = cy + (world.rand.nextDouble() - 0.5) * 0.4;

            double tangentSpeed = 0.05 + world.rand.nextDouble() * 0.03;
            double inwardSpeed = 0.01 + world.rand.nextDouble() * 0.01;

            // 切向速度（逆时针）+ 径向速度（向内）
            double vx = -Math.sin(angle) * tangentSpeed - Math.cos(angle) * inwardSpeed;
            double vz = Math.cos(angle) * tangentSpeed - Math.sin(angle) * inwardSpeed;
            double vy = (world.rand.nextDouble() - 0.5) * 0.005;

            // REDSTONE 粒子的 velocity 参数解释为颜色 (R, G, B)
            float red = 0.4f + world.rand.nextFloat() * 0.2f;
            float green = 0.05f + world.rand.nextFloat() * 0.1f;
            float blue = 0.7f + world.rand.nextFloat() * 0.2f;

            world.spawnParticle(EnumParticleTypes.REDSTONE, px, py, pz, red, green, blue);
        }
    }

    /**
     * 获取当前总样板槽数（可用页数 × 每页槽数）
     */
    public int getPatternSlotCount() {
        return getPatternPages() * PATTERN_SLOTS_PER_PAGE;
    }

    /**
     * 检查扩容升级能否减少到 newCapacityCount 张。
     * 如果被移除的扩展页面中任意槽位留有样板，返回 false。
     */
    public boolean canReduceCapacity(int newCapacityCount) {
        int oldPages = getPatternPages();
        int newPages = PATTERN_PAGES_BASE + newCapacityCount * PATTERN_PAGES_PER_CAPACITY;
        newPages = Math.min(newPages, PATTERN_PAGES_MAX);
        if (newPages >= oldPages) return true;

        int startSlot = UPGRADE_SLOTS + newPages * PATTERN_SLOTS_PER_PAGE;
        int endSlot = UPGRADE_SLOTS + oldPages * PATTERN_SLOTS_PER_PAGE;
        for (int i = startSlot; i < endSlot && i < itemHandler.getSlots(); i++) {
            if (!itemHandler.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 扩容升级增加时扩展 ItemStackHandler 容量。减少时由 extractItem 阻止，此处不收缩。
     */
    private void ensurePatternCapacity() {
        int pages = getPatternPages();
        int targetSize = UPGRADE_SLOTS + pages * PATTERN_SLOTS_PER_PAGE;
        if (itemHandler.getSlots() < targetSize) {
            itemHandler.setCapacity(targetSize);
        }
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
    @Nonnull
    public AxisAlignedBB getRenderBoundingBox() {
        BlockPos origin = AssemblyStructure.getOriginFromController(pos);
        return new AxisAlignedBB(
            origin.getX() - 4, origin.getY() - 4, origin.getZ() - 4,
            origin.getX() + 5, origin.getY() + 5, origin.getZ() + 5
        );
    }

    @Override
    public void update() {
        if (world == null) return;

        // 客户端：生成黑洞粒子光环
        if (world.isRemote) {
            if (formed) {
                spawnBlackHoleParticles();
            }
            return;
        }

        // 黑洞事件视界：秒杀进入中心区域的生物；16次未死则放逐至随机维度
        if (formed) {
            BlockPos origin = AssemblyStructure.getOriginFromController(pos);
            AxisAlignedBB eventHorizon = new AxisAlignedBB(
                origin.getX() - 2, origin.getY() - 2, origin.getZ() - 2,
                origin.getX() + 3, origin.getY() + 3, origin.getZ() + 3
            );
            DamageSource spacetime = new DamageSource("spacetime") {
                @Override
                public ITextComponent getDeathMessage(EntityLivingBase entityLivingBaseIn) {
                    return new TextComponentTranslation("death.spacetime.blackHole", entityLivingBaseIn.getDisplayName());
                }
            }.setDamageBypassesArmor().setDamageAllowedInCreativeMode();
            List<EntityLivingBase> inHorizon = world.getEntitiesWithinAABB(EntityLivingBase.class, eventHorizon);
            Set<UUID> currentUuids = new HashSet<>();
            for (EntityLivingBase entity : inHorizon) {
                if (!entity.isEntityAlive()) continue;
                UUID uuid = entity.getUniqueID();
                currentUuids.add(uuid);
                int strikes = eventHorizonStrikes.getOrDefault(uuid, 0) + 1;
                entity.hurtResistantTime = 0;
                entity.hurtTime = 0;
                boolean killed = false;
                if (entity.attackEntityFrom(spacetime, Float.MAX_VALUE)) {
                    if (!entity.isEntityAlive()) killed = true;
                }
                if (!killed) {
                    entity.setHealth(0);
                    entity.onDeath(spacetime);
                    if (!entity.isEntityAlive()) killed = true;
                }
                if (killed) {
                    eventHorizonStrikes.remove(uuid);
                } else if (strikes >= 16) {
                    eventHorizonStrikes.remove(uuid);
                    banishToOverworld(entity);
                } else {
                    eventHorizonStrikes.put(uuid, strikes);
                }
            }
            eventHorizonStrikes.keySet().removeIf(uuid -> !currentUuids.contains(uuid));

            // 黑洞合成：对事件视界内的物品执行合成或销毁
            BlackHoleCraftingHelper.tryCraft(world, origin);
        }

        // 样板变化时触发 AE 网络重新扫描，1 tick 延迟合并同一 tick 内的连续变化
        // 如果 activeMeInterfacePos 为 null，先尝试从结构坐标恢复，避免死锁：
        // patternsDirty=true → 无法发送事件 → AE2 不扫描 → provideCrafting 不调用 → 永远无法恢复
        if (patternsDirty && activeMeInterfacePos == null && formed) {
            BlockPos origin = AssemblyStructure.getOriginFromController(pos);
            for (BlockPos rel : AssemblyStructure.PART1_SET) {
                BlockPos mePos = origin.add(rel);
                TileEntity te = world.getTileEntity(mePos);
                if (te instanceof TileAssemblyMeInterface) {
                    TileAssemblyMeInterface me = (TileAssemblyMeInterface) te;
                    if (me.getControllerPos() != null && me.getControllerPos().equals(pos)) {
                        activeMeInterfacePos = mePos;
                        markDirty();
                        break;
                    }
                }
            }
        }
        if (patternsDirty && activeMeInterfacePos != null) {
            patternsDirty = false;
            patternRefreshTicks = 1;
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

        // 递减 batch 冷却
        if (batchCooldown > 0) {
            batchCooldown--;
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

        // 重置 batchBusy，允许下一 tick 继续 batch
        this.batchBusy = false;

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

    /**
     * 将实体放逐至主世界（维度 0）原点 10000 格以内的随机坐标，Y=256 高空。
     * 用于事件视界 16 次击杀失败后的最终放逐手段。
     */
    private void banishToOverworld(EntityLivingBase entity) {
        int targetX = world.rand.nextInt(20001) - 10000; // -10000 ~ +10000
        int targetZ = world.rand.nextInt(20001) - 10000;
        int targetY = 256;

        if (entity.dimension != 0) {
            net.minecraft.entity.Entity newEntity = entity.changeDimension(0);
            if (newEntity != null) {
                newEntity.setPositionAndUpdate(targetX, targetY, targetZ);
            }
        } else {
            entity.setPositionAndUpdate(targetX, targetY, targetZ);
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

        Boolean cached = patternVirtualCache.get(patternDetails);
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
            patternVirtualCache.put(patternDetails, isVirtual);
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

    /**
     * 获取当前合成延迟 tick 数。速度升级卡固定在槽位 1，堆叠数量即为安装数量。
     * 每张减半，最低 1 tick。
     */
    public int getCraftingTicks() {
        int ticks = 20;
        ItemStack stack = itemHandler.getStackInSlot(1);
        if (!stack.isEmpty() && stack.getItem() instanceof ItemUpgradeCard && stack.getMetadata() == ItemUpgradeCard.META_SPEED) {
            for (int i = 0; i < stack.getCount() && ticks > 1; i++) {
                ticks = Math.max(ticks / 2, 1);
            }
        }
        return ticks;
    }

    /**
     * 供 Mixin 调用：检查当前 batch 冷却是否已结束。
     */
    public boolean canBatch() {
        return batchCooldown <= 0;
    }

    /**
     * 供 Mixin 调用：batch 执行成功后重置冷却。
     */
    public void resetBatchCooldown() {
        this.batchCooldown = getCraftingTicks();
    }

    /**
     * 供 Mixin 调用：标记当前 tick 已有 batch 任务被执行，
     * 让原生 executeCrafting 在同一 tick 内跳过该控制器，避免双重扣减。
     */
    public void setBatchBusy(boolean busy) {
        this.batchBusy = busy;
    }

    @Override
    public boolean isBusy() {
        long cap = getParallelCap();
        int intCap = (cap >= Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) cap;
        return jobTimers.size() >= intCap || batchBusy;
    }

    public int getJobCount() {
        return jobTimers.size();
    }

    /**
     * 供 Mixin 调用：检查指定样板是否已被缓存为纯虚拟合成（无剩余物品）。
     */
    public boolean isVirtualPattern(ICraftingPatternDetails details) {
        Boolean cached = patternVirtualCache.get(details);
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

        // 旧存档可能未保存 activeMeInterfacePos，尝试从结构坐标恢复
        if (activeMeInterfacePos == null && formed) {
            BlockPos origin = AssemblyStructure.getOriginFromController(pos);
            for (BlockPos rel : AssemblyStructure.PART1_SET) {
                BlockPos mePos = origin.add(rel);
                TileEntity te = world.getTileEntity(mePos);
                if (te instanceof TileAssemblyMeInterface) {
                    TileAssemblyMeInterface me = (TileAssemblyMeInterface) te;
                    if (me.getControllerPos() != null && me.getControllerPos().equals(pos)) {
                        activeMeInterfacePos = mePos;
                        markDirty();
                        break;
                    }
                }
            }
        }

        // 使用 TileAssemblyMeInterface 作为 medium 注册样板，
        // 这样 CraftingGridCache.getMediums() 返回的是 TileAssemblyMeInterface 而不是 TileAssemblyController
        appeng.api.networking.crafting.ICraftingMedium medium = this;
        if (activeMeInterfacePos != null) {
            TileEntity te = world.getTileEntity(activeMeInterfacePos);
            if (te instanceof TileAssemblyMeInterface) {
                medium = (TileAssemblyMeInterface) te;
            }
        }

        int patternSlots = getPatternSlotCount();
        for (int i = UPGRADE_SLOTS; i < UPGRADE_SLOTS + patternSlots; i++) {
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
        if (patternVirtualCache.containsKey(pattern)) return;
        if (!pattern.isCraftable()) {
            patternVirtualCache.put(pattern, false);
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
            patternVirtualCache.put(pattern, isVirtual);
        } else {
            patternVirtualCache.put(pattern, false);
        }
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
        if (world != null && !world.isRemote) {
            // readFromNBT 时 world 通常为 null，patternVirtualCache 未被预填充。
            // 此处补全缓存，确保 AE2 网络扫描前缓存已就绪。
            int patternSlots = getPatternSlotCount();
            for (int i = UPGRADE_SLOTS; i < UPGRADE_SLOTS + patternSlots; i++) {
                ItemStack stack = itemHandler.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                if (stack.getItem() instanceof ICraftingPatternItem) {
                    ICraftingPatternDetails pattern = ((ICraftingPatternItem) stack.getItem()).getPatternForItem(stack, world);
                    if (pattern != null && pattern.isCraftable()) {
                        prefillVirtualCache(pattern);
                    }
                }
            }
            // 强制同步 formed 状态到客户端，避免加载后客户端 formed 仍为默认值 false，
            // 导致容器槽位数量不一致（服务端 78 槽 vs 客户端 36 槽），引发槽位索引映射错误
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
            NBTTagCompound itemsTag = compound.getCompoundTag("items");
            // 旧存档 Size 可能小于当前基础容量（从 42/96/102 升级），先扩展为基础容量避免越界
            if (itemsTag.hasKey("Size", Constants.NBT.TAG_INT)) {
                int oldSize = itemsTag.getInteger("Size");
                if (oldSize < TOTAL_SLOTS_BASE) {
                    itemsTag.setInteger("Size", TOTAL_SLOTS_BASE);
                }
            }
            itemHandler.deserializeNBT(itemsTag);
            // 加载扩容升级后，根据实际数量扩展容量
            ensurePatternCapacity();
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
            int patternSlots = getPatternSlotCount();
        for (int i = UPGRADE_SLOTS; i < UPGRADE_SLOTS + patternSlots; i++) {
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
