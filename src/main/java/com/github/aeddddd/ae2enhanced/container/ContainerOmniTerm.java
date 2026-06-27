package com.github.aeddddd.ae2enhanced.container;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.container.guisync.GuiSync;
import appeng.container.implementations.ContainerMEMonitorable;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.container.slot.IOptionalSlotHost;
import appeng.container.slot.SlotCraftingMatrix;
import appeng.container.slot.SlotCraftingTerm;
import appeng.container.slot.SlotPatternTerm;
import appeng.container.slot.SlotPlayerHotBar;
import appeng.container.slot.SlotPlayerInv;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.ContainerNull;
import appeng.core.localization.PlayerMessages;
import appeng.helpers.IContainerCraftingPacket;
import appeng.helpers.ItemStackHelper;
import appeng.helpers.WirelessTerminalGuiObject;
import appeng.api.util.AEPartLocation;
import appeng.container.ContainerOpenContext;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import appeng.util.inv.IAEAppEngInventory;
import appeng.util.inv.InvOperation;
import appeng.util.prioritylist.IPartitionList;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotFakeCraftingMatrix;
import com.github.aeddddd.ae2enhanced.client.gui.slot.RCSlotPatternOutputs;
import com.github.aeddddd.ae2enhanced.client.gui.slot.SlotHighCapacity;
import com.github.aeddddd.ae2enhanced.client.gui.slot.SlotOmniUpgrade;
import com.github.aeddddd.ae2enhanced.item.ItemOmniWirelessTerminal;
import com.github.aeddddd.ae2enhanced.client.me.CraftingStatus;
import com.github.aeddddd.ae2enhanced.storage.OmniTerminalData;
import com.github.aeddddd.ae2enhanced.storage.OmniTerminalInventory;
import com.github.aeddddd.ae2enhanced.storage.OmniTerminalStorage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerInvWrapper;

import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniInventoryUpdate;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniSearchRequest;
import com.github.aeddddd.ae2enhanced.network.packet.PacketOmniSearchResult;
import com.github.aeddddd.ae2enhanced.storage.ItemDescriptor;
import com.github.aeddddd.ae2enhanced.storage.ItemStorageAdapter;
import com.github.aeddddd.ae2enhanced.storage.SimpleMEMonitor;
import com.github.aeddddd.ae2enhanced.tile.TileHyperdimensionalController;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 全能无线终端 Container —— 物品库 + 合成栏 + 81槽位编码样板 + 右侧存储
 */
public class ContainerOmniTerm extends ContainerMEMonitorable
        implements IAEAppEngInventory, IOptionalSlotHost, IContainerCraftingPacket, IInventorySlotAware {

    // === 合成栏 ===
    private final SlotCraftingMatrix[] craftingSlots = new SlotCraftingMatrix[9];
    private SlotCraftingTerm craftOutputSlot;
    private final AppEngInternalInventory craftingOutput = new AppEngInternalInventory(this, 1);
    private IRecipe currentRecipe;
    private IRecipe currentPatternRecipe;
    private OmniTerminalInventory craftingInv;

    // === 编码区 ===
    public OmniTerminalInventory patternCraftingInv;
    public OmniTerminalInventory patternOutputInv;
    private OmniTerminalInventory patternInv;
    private final AppEngInternalInventory cOut = new AppEngInternalInventory(null, 1);

    private final RCSlotFakeCraftingMatrix[][] patternInputSlots = new RCSlotFakeCraftingMatrix[9][9];
    private final RCSlotPatternOutputs[][] outputSlotGroup = new RCSlotPatternOutputs[9][3];
    private SlotPatternTerm craftSlot;
    private SlotRestrictedInput patternSlotIN;
    private SlotRestrictedInput patternSlotOUT;

    @GuiSync(97)
    public boolean patternCraftMode = true;
    @GuiSync(96)
    public boolean substitute = false;

    private int scrollOffset = 0;

    // === 右侧存储 ===
    private final OmniTerminalInventory rightPatternStorage;
    private final OmniTerminalInventory rightUpgradeStorage;

    // === WorldSavedData 存储 ===
    private final OmniTerminalStorage omniStorage;
    private final OmniTerminalData omniData;

    // === 宿主 ===
    private final ITerminalHost terminalHost;

    public boolean isViewCellSlot(Slot slot) {
        for (int i = 0; i < this.cellView.length; i++) {
            if (this.cellView[i] == slot) return true;
        }
        return false;
    }
    private final WirelessTerminalGuiObject wirelessObject;
    private int wirelessTickCounter = 0;

    // === 合成置顶：active crafting 同步 ===
    private List<CraftingStatus> activeCraftingCache = Collections.emptyList();
    private List<CraftingStatus> previousActiveCrafting = Collections.emptyList();
    private List<IAEItemStack> completedCraftingCache = new ArrayList<>();
    private int craftingUpdateCooldown = 0;
    private List<CraftingStatus> clientActiveCrafting = Collections.emptyList();

    // === 本地 monitor 引用（父类 monitor 为 private，无法直接访问）===
    private final appeng.api.storage.IMEMonitor<IAEItemStack> omniMonitor;

    // === 缓存 adapter 引用，避免 postChange 高频触发时反复遍历网格节点 ===
    private ItemStorageAdapter cachedAdapter = null;

    // === Omni Terminal 自定义物品同步状态 ===
    private final appeng.api.storage.data.IItemList<IAEItemStack> omniItems =
            AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class).createList();
    private boolean omniNeedsFullSync = false;

    // === Item ID 映射表（per-session）===
    private final Object2IntOpenHashMap<IAEItemStack> omniItemToId = new Object2IntOpenHashMap<>();
    private final Int2ObjectOpenHashMap<IAEItemStack> omniIdToItem = new Int2ObjectOpenHashMap<>();
    private int omniNextItemId = 1;

    // === 编码区布局常量(可扩展) ===
    public static final int CRAFTING_GRID_BASE_X = 187;
    public static final int PROCESSING_GRID_BASE_X = 196;
    public static final int GRID_Y = 93;
    public static final int GRID_COL_SPACING = 18;
    public static final int GRID_ROW_SPACING = 18;

    // === NBT Keys(ItemStack 中仅存 craftingMode / substitute / scrollOffset)===
    private static final String NBT_CRAFTING_MODE = "omni_crafting_mode";
    private static final String NBT_SUBSTITUTE = "omni_substitute";
    private static final String NBT_SCROLL_OFFSET = "omni_scroll_offset";

    public ContainerOmniTerm(InventoryPlayer ip, ITerminalHost host) {
        // AE2-UEL 在运行时为 WirelessTerminalGuiObject 添加了 IGuiItemObject 实现.
        // 与标准无线终端容器 ContainerMEPortableTerminal 保持一致,直接传入 host.
        super(ip, host, (appeng.api.implementations.guiobjects.IGuiItemObject) (Object) host, false);
        this.terminalHost = host;
        this.wirelessObject = host instanceof WirelessTerminalGuiObject ? (WirelessTerminalGuiObject) host : null;
        this.omniMonitor = host.getInventory(AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));

        // === 从 WorldSavedData 获取持久化存储 ===
        if (host instanceof WirelessTerminalGuiObject) {
            WirelessTerminalGuiObject wt = (WirelessTerminalGuiObject) host;
            java.util.UUID storageId = ItemOmniWirelessTerminal.getStorageId(wt.getItemStack());
            this.omniData = OmniTerminalData.get(ip.player.world);
            this.omniStorage = this.omniData.getOrCreate(storageId);
        } else {
            // 非无线终端回退：使用临时存储(不持久化)
            this.omniData = null;
            this.omniStorage = new OmniTerminalStorage();
        }

        this.rightPatternStorage = this.omniStorage.getRightStorageInventory();

        // R3: 注册打开的玩家到 ItemStorageAdapter（用于 UPDATE_NOTIFY）
        if (ip.player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            ItemStorageAdapter adapter = findItemStorageAdapter();
            if (adapter != null) {
                adapter.addOpenPlayer((net.minecraft.entity.player.EntityPlayerMP) ip.player);
            }
        }
        this.rightUpgradeStorage = this.omniStorage.getUpgradeInventory();

        this.setupCraftingArea(ip, host);
        this.setupPatternArea(ip, host);
        this.setupRightStorage();

        // 设置 openContext,使 PacketSwitchGuis 能正确打开合成计划 GUI
        if (host instanceof WirelessTerminalGuiObject) {
            WirelessTerminalGuiObject wt = (WirelessTerminalGuiObject) host;
            ContainerOpenContext ctx = new ContainerOpenContext(wt);
            ctx.setWorld(ip.player.world);
            ctx.setX(0);
            ctx.setY(0);
            ctx.setZ(0);
            ctx.setSide(AEPartLocation.INTERNAL);
            this.setOpenContext(ctx);
        } else if (host instanceof appeng.api.networking.security.IActionHost) {
            appeng.api.networking.security.IActionHost ah = (appeng.api.networking.security.IActionHost) host;
            appeng.api.networking.IGridNode node = ah.getActionableNode();
            if (node != null) {
                Object machine = node.getMachine();
                if (machine instanceof net.minecraft.tileentity.TileEntity) {
                    net.minecraft.tileentity.TileEntity te = (net.minecraft.tileentity.TileEntity) machine;
                    ContainerOpenContext ctx = new ContainerOpenContext(te);
                    ctx.setWorld(te.getWorld());
                    ctx.setX(te.getPos().getX());
                    ctx.setY(te.getPos().getY());
                    ctx.setZ(te.getPos().getZ());
                    ctx.setSide(AEPartLocation.INTERNAL);
                    this.setOpenContext(ctx);
                }
            }
        }
        this.addCustomPlayerInventory(ip, 8, 167, 225);

        // 重新定位 view cell 槽位到 GUI 右侧外部(模仿标准终端侧栏)
        // yPos 保持 ContainerMEMonitorable 原始值(8 + jeiOffset + i * 18),
        // 因为 GuiOmniTerm.initGui 中会通过 AppEngSlot.getY() 恢复为 defY
        for (int i = 0; i < this.cellView.length; i++) {
            if (this.cellView[i] != null) {
                this.cellView[i].xPos = 366; // 357 + 9,位于侧栏面板内部
            }
        }

        for (int i = 0; i < this.inventorySlots.size(); i++) {
            this.inventorySlots.get(i).slotNumber = i;
        }

        // 从 WorldSavedData 加载模式状态
        this.loadStateFromItemNBT();
    }

    // ================== 合成栏 ==================

    private void setupCraftingArea(InventoryPlayer ip, ITerminalHost host) {
        this.craftingInv = this.omniStorage.getCraftingInventory();
        this.craftingInv.setOnContentsChangedCallback(this::updateRealCraftingRecipe);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int idx = x + y * 3;
                this.craftingSlots[idx] = new SlotCraftingMatrix(this, this.craftingInv, idx, 26 + x * 18, 93 + y * 18);
                this.func_75146_a(this.craftingSlots[idx]);
            }
        }
        this.craftOutputSlot = new SlotCraftingTerm(ip.player, this.getActionSource(), this.getPowerSource(), host,
                this.craftingInv, this.craftingInv, this.craftingOutput, 133, 111, this);
        this.func_75146_a(this.craftOutputSlot);
    }

    // ================== 编码区 ==================

    private void setupPatternArea(InventoryPlayer ip, ITerminalHost host) {
        this.patternCraftingInv = this.omniStorage.getPatternInputInventory();
        this.patternOutputInv = this.omniStorage.getPatternOutputInventory();

        // 设置变更回调,使 patternCraftMode 时自动更新配方预览
        this.patternCraftingInv.setOnContentsChangedCallback(() -> {
            if (this.patternCraftMode) {
                this.fixPatternCraftingRecipes();
                this.updatePatternCraftingRecipe();
            }
        });

        for (int g = 0; g < 9; g++) {
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    int idx = g * 9 + r * 3 + c;
                    int x = CRAFTING_GRID_BASE_X + c * GRID_COL_SPACING;
                    int y = GRID_Y + r * GRID_ROW_SPACING;
                    RCSlotFakeCraftingMatrix slot = new RCSlotFakeCraftingMatrix(this.patternCraftingInv, idx, x, y, CRAFTING_GRID_BASE_X);
                    this.patternInputSlots[g][r * 3 + c] = slot;
                    this.func_75146_a(slot);
                }
            }
            for (int r = 0; r < 3; r++) {
                int idx = g * 3 + r;
                int x = 281;
                int y = 93 + r * 18;
                RCSlotPatternOutputs slot = new RCSlotPatternOutputs(this.patternOutputInv, this, idx, x, y, 0, 0, 1);
                this.outputSlotGroup[g][r] = slot;
                this.func_75146_a(slot);
                slot.setRenderDisabled(false);
                slot.setIIcon(-1);
            }
        }

        this.patternInv = this.omniStorage.getPatternInventory();
        this.patternInv.setOnContentsChangedCallback(() -> {
            if (!Platform.isServer()) return;
            this.onPatternOutputChanged();
        });
        this.craftSlot = new SlotPatternTerm(ip.player, this.getActionSource(), this.getPowerSource(), host,
                this.patternCraftingInv, this.patternInv, this.cOut, 281, 111, this, 2, this);
        this.func_75146_a(this.craftSlot);
        this.craftSlot.setIIcon(-1);

        this.patternSlotIN = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.BLANK_PATTERN,
                this.patternInv, 0, 319, 86, this.getInventoryPlayer());
        this.func_75146_a(this.patternSlotIN);

        this.patternSlotOUT = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN,
                this.patternInv, 1, 319, 133, this.getInventoryPlayer());
        this.func_75146_a(this.patternSlotOUT);
        this.patternSlotOUT.setStackLimit(1);

        this.setRCSlot(0);
    }

    // ================== 右侧存储 ==================

    private void setupRightStorage() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                int idx = r * 9 + c;
                this.func_75146_a(new SlotHighCapacity(this.rightPatternStorage, idx, 180 + c * 18, 167 + r * 18));
            }
        }
        for (int c = 0; c < 9; c++) {
            this.func_75146_a(new SlotOmniUpgrade(this.rightUpgradeStorage, c, 180 + c * 18, 221));
        }
    }

    // ================== 背包绑定 ==================

    private void addCustomPlayerInventory(InventoryPlayer ip, int invX, int invY, int hotbarY) {
        PlayerInvWrapper wrapper = new PlayerInvWrapper(ip);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.func_75146_a(new SlotPlayerInv(wrapper, j + i * 9 + 9, invX + j * 18, invY + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            this.func_75146_a(new SlotPlayerHotBar(wrapper, i, invX + i * 18, hotbarY));
        }
    }

    // ================== 滚动控制 ==================

    public void setRCSlot(int offset) {
        if (this.patternCraftMode) {
            offset = 0;
        }
        this.scrollOffset = Math.max(0, Math.min(offset, getMaxScrollOffset()));
        for (int g = 0; g < 9; g++) {
            boolean visible = (g == offset);
            for (int i = 0; i < 9; i++) {
                RCSlotFakeCraftingMatrix slot = this.patternInputSlots[g][i];
                if (visible) {
                    slot.xPos = slot.getDefX();
                    slot.visible = true;
                } else {
                    slot.xPos = -9000;
                    slot.visible = false;
                }
            }
            for (int i = 0; i < 3; i++) {
                RCSlotPatternOutputs slot = this.outputSlotGroup[g][i];
                if (visible) {
                    slot.xPos = this.patternCraftMode ? -9000 : slot.getDefX();
                    slot.visible = true;
                } else {
                    slot.xPos = -9000;
                    slot.visible = false;
                }
            }
        }
    }

    public int getScrollOffset() {
        return this.scrollOffset;
    }

    public int getMaxScrollOffset() {
        return this.patternCraftMode ? 0 : 8;
    }

    public void updateOrderOfOutputSlots() {
        if (!this.patternCraftMode) {
            if (this.craftSlot != null) {
                this.craftSlot.xPos = -9000;
            }
        } else {
            if (this.craftSlot != null) {
                this.craftSlot.xPos = this.craftSlot.getX();
            }
        }
    }

    // ================== 模式切换 ==================

    private void applyPatternCraftMode() {
        int newBaseX = this.patternCraftMode ? CRAFTING_GRID_BASE_X : PROCESSING_GRID_BASE_X;
        for (int g = 0; g < 9; g++) {
            for (int i = 0; i < 9; i++) {
                this.patternInputSlots[g][i].setDefX(newBaseX);
            }
        }
        this.updateOrderOfOutputSlots();
        this.setRCSlot(this.scrollOffset);
    }

    public void setPatternCraftMode(boolean mode) {
        if (this.patternCraftMode == mode) {
            return;
        }
        this.patternCraftMode = mode;
        this.saveCraftingModeToNBT();
        this.applyPatternCraftMode();
        this.detectAndSendChanges();
    }

    public boolean isPatternCraftMode() {
        return this.patternCraftMode;
    }

    public void setSubstitute(boolean substitute) {
        this.substitute = substitute;
        this.saveSubstituteToNBT();
        this.detectAndSendChanges();
    }

    public boolean isSubstitute() {
        return this.substitute;
    }

    @Override
    public void onUpdate(String field, Object oldValue, Object newValue) {
        super.onUpdate(field, oldValue, newValue);
        if ("patternCraftMode".equals(field)) {
            this.applyPatternCraftMode();
        }
    }

    // ================== IOptionalSlotHost ==================

    @Override
    public boolean isSlotEnabled(int idx) {
        if (idx == 1) {
            return !this.patternCraftMode;
        }
        if (idx == 2) {
            return this.patternCraftMode;
        }
        return false;
    }

    // ================== 合成栏配方更新 ==================

    @Override
    public void onChangeInventory(IItemHandler inv, int slot, InvOperation op, ItemStack removed, ItemStack added) {
        if (inv == this.craftingInv) {
            this.updateRealCraftingRecipe();
        } else if (inv == this.patternCraftingInv) {
            if (this.patternCraftMode) {
                this.fixPatternCraftingRecipes();
                this.updatePatternCraftingRecipe();
            }
        }
    }

    /**
     * 当编码输出槽(patternSlotOUT,patternInv 第 1 槽)内容变化时调用.
     * 如果放入的是已编码样板,自动读取其内容并加载到输入/输出格.
     * 复刻 AE2 原版 AbstractPartEncoder.onChangeInventory 的逻辑.
     */
    private void onPatternOutputChanged() {
        ItemStack stack = this.patternInv.getStackInSlot(1);
        if (stack.isEmpty() || !(stack.getItem() instanceof ICraftingPatternItem)) {
            return;
        }
        ICraftingPatternDetails details = ((ICraftingPatternItem) stack.getItem()).getPatternForItem(
                stack, this.getPlayerInv().player.world);
        if (details == null) return;
        this.loadPatternFromDetails(details);
    }

    private void loadPatternFromDetails(ICraftingPatternDetails details) {
        // 1. 恢复 crafting / substitute 模式
        boolean crafting = details.isCraftable();
        if (this.patternCraftMode != crafting) {
            this.patternCraftMode = crafting;
            this.saveCraftingModeToNBT();
            this.applyPatternCraftMode();
        }
        boolean canSubstitute = details.canSubstitute();
        if (this.substitute != canSubstitute) {
            this.substitute = canSubstitute;
            this.saveSubstituteToNBT();
        }

        // 2. 清空所有输入/输出格
        for (int i = 0; i < this.patternCraftingInv.getSlots(); i++) {
            this.patternCraftingInv.setStackInSlot(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < this.patternOutputInv.getSlots(); i++) {
            this.patternOutputInv.setStackInSlot(i, ItemStack.EMPTY);
        }
        this.cOut.setStackInSlot(0, ItemStack.EMPTY);

        // 3. 加载输入
        IAEItemStack[] inputs = details.getInputs();
        for (int i = 0; i < inputs.length && i < this.patternCraftingInv.getSlots(); i++) {
            if (inputs[i] != null) {
                this.patternCraftingInv.setStackInSlot(i, inputs[i].createItemStack());
            }
        }

        // 4. 加载输出
        IAEItemStack[] outputs = details.getOutputs();
        for (int i = 0; i < outputs.length && i < this.patternOutputInv.getSlots(); i++) {
            if (outputs[i] != null) {
                this.patternOutputInv.setStackInSlot(i, outputs[i].createItemStack());
            }
        }

        // 5. crafting 模式下更新合成输出预览
        if (crafting) {
            this.updatePatternCraftingRecipe();
        }

        this.detectAndSendChanges();
    }

    private void fixPatternCraftingRecipes() {
        if (!this.patternCraftMode) return;
        for (int x = 0; x < 9; x++) {
            ItemStack is = this.patternCraftingInv.getStackInSlot(x);
            if (!is.isEmpty()) {
                is.setCount(1);
            }
        }
    }

    private void updateRealCraftingRecipe() {
        InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i = 0; i < 9; i++) {
            ic.setInventorySlotContents(i, this.craftingInv.getStackInSlot(i));
        }
        if (this.currentRecipe == null || !this.currentRecipe.matches(ic, this.getPlayerInv().player.world)) {
            this.currentRecipe = CraftingManager.findMatchingRecipe(ic, this.getPlayerInv().player.world);
        }
        if (this.currentRecipe == null) {
            this.craftOutputSlot.putStack(ItemStack.EMPTY);
            this.craftingOutput.setStackInSlot(0, ItemStack.EMPTY);
        } else {
            ItemStack result = this.currentRecipe.getCraftingResult(ic);
            this.craftOutputSlot.putStack(result);
            this.craftingOutput.setStackInSlot(0, result);
        }
    }

    private void updatePatternCraftingRecipe() {
        if (!this.patternCraftMode) return;
        InventoryCrafting ic = new InventoryCrafting(new ContainerNull(), 3, 3);
        for (int i = 0; i < 9; i++) {
            ic.setInventorySlotContents(i, this.patternCraftingInv.getStackInSlot(i));
        }
        if (this.currentPatternRecipe == null || !this.currentPatternRecipe.matches(ic, this.getPlayerInv().player.world)) {
            this.currentPatternRecipe = CraftingManager.findMatchingRecipe(ic, this.getPlayerInv().player.world);
        }
        if (this.currentPatternRecipe == null) {
            this.cOut.setStackInSlot(0, ItemStack.EMPTY);
        } else {
            ItemStack result = this.currentPatternRecipe.getCraftingResult(ic);
            this.cOut.setStackInSlot(0, result);
        }
    }

    // ================== 编码逻辑 ==================

    public void encode() {
        this.encode(false);
    }

    public void encodeAndMoveToInventory() {
        this.encode(true);
    }

    private void encode(boolean moveToInventory) {
        ItemStack output = this.patternSlotOUT.getStack();
        ItemStack[] in = this.getInputs();
        ItemStack[] out = this.getOutputs();
        if (in == null || out == null) {
            return;
        }

        if (!output.isEmpty() && !this.isPattern(output)) {
            return;
        }

        boolean hasFluid = false;
        for (ItemStack i : in) {
            if (isFluidDrop(i)) { hasFluid = true; break; }
        }
        if (!hasFluid) {
            for (ItemStack i : out) {
                if (isFluidDrop(i)) { hasFluid = true; break; }
            }
        }

        if (output.isEmpty()) {
            output = this.patternSlotIN.getStack();
            if (output.isEmpty() || !this.isPattern(output)) {
                return;
            }
            output.shrink(1);
            if (output.getCount() <= 0) {
                this.patternSlotIN.putStack(ItemStack.EMPTY);
            }

            if (hasFluid && com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidHelper.isLoaded()) {
                net.minecraft.item.Item fluidPattern = net.minecraftforge.fml.common.registry.ForgeRegistries.ITEMS.getValue(
                        new net.minecraft.util.ResourceLocation("ae2fc", this.patternCraftMode ? "dense_craft_encoded_pattern" : "dense_encoded_pattern"));
                if (fluidPattern != null) {
                    output = new ItemStack(fluidPattern);
                } else {
                    Optional<ItemStack> maybePattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1);
                    if (maybePattern.isPresent()) {
                        output = maybePattern.get();
                    } else {
                        return;
                    }
                }
            } else {
                Optional<ItemStack> maybePattern = AEApi.instance().definitions().items().encodedPattern().maybeStack(1);
                if (maybePattern.isPresent()) {
                    output = maybePattern.get();
                } else {
                    return;
                }
            }
        }

        NBTTagCompound encodedValue = new NBTTagCompound();
        NBTTagList tagIn = new NBTTagList();
        NBTTagList tagOut = new NBTTagList();

        if (this.patternCraftMode) {
            for (int i = 0; i < Math.min(in.length, 9); i++) {
                tagIn.appendTag(this.createItemTag(in[i]));
            }
            if (out.length > 0) {
                tagOut.appendTag(this.createItemTag(out[0]));
            }
        } else {
            for (ItemStack i : in) {
                if (!i.isEmpty()) {
                    tagIn.appendTag(this.createItemTag(i));
                }
            }
            for (ItemStack i : out) {
                if (!i.isEmpty()) {
                    tagOut.appendTag(this.createItemTag(i));
                }
            }
        }

        encodedValue.setTag("in", tagIn);
        encodedValue.setTag("out", tagOut);
        encodedValue.setBoolean("crafting", this.patternCraftMode);
        encodedValue.setBoolean("substitute", this.substitute);

        // ae2fc processing 流体样板使用 FluidPatternDetails，它读取 Inputs/Outputs tag
        // 必须使用 AEItemStack NBT 格式（包含 "Cnt" 字段），否则 stackSize 会被解析为 0
        if (hasFluid && !this.patternCraftMode && com.github.aeddddd.ae2enhanced.util.compat.Ae2fcFluidHelper.isLoaded()) {
            NBTTagList inputsTag = new NBTTagList();
            NBTTagList outputsTag = new NBTTagList();
            for (ItemStack i : in) {
                if (!i.isEmpty()) {
                    appeng.util.item.AEItemStack aeStack = appeng.util.item.AEItemStack.fromItemStack(i);
                    if (aeStack != null) {
                        NBTTagCompound stackTag = new NBTTagCompound();
                        aeStack.writeToNBT(stackTag);
                        inputsTag.appendTag(stackTag);
                    }
                }
            }
            for (ItemStack i : out) {
                if (!i.isEmpty()) {
                    appeng.util.item.AEItemStack aeStack = appeng.util.item.AEItemStack.fromItemStack(i);
                    if (aeStack != null) {
                        NBTTagCompound stackTag = new NBTTagCompound();
                        aeStack.writeToNBT(stackTag);
                        outputsTag.appendTag(stackTag);
                    }
                }
            }
            encodedValue.setTag("Inputs", inputsTag);
            encodedValue.setTag("Outputs", outputsTag);
        }

        output.setTagCompound(encodedValue);

        if (moveToInventory) {
            if (!this.getPlayerInv().addItemStackToInventory(output)) {
                this.patternSlotOUT.putStack(output);
            }
        } else {
            this.patternSlotOUT.putStack(output);
        }

        // 装配枢纽自动上传：如果 patternSlotOUT 中有合成样板,尝试上传到同一 ME 网络中的装配枢纽
        ItemStack patternInSlot = this.patternSlotOUT.getStack();
        if (!patternInSlot.isEmpty() && this.getPlayerInv().player != null
                && !this.getPlayerInv().player.world.isRemote
                && !this.getPlayerInv().player.isSneaking()) {
            appeng.api.networking.IGridNode node = this.getNetworkNode();
            appeng.api.networking.IGrid grid = (node != null) ? node.getGrid() : null;
            if (com.github.aeddddd.ae2enhanced.util.compat.AssemblyAutoUploadHelper.tryUploadPattern(
                    this.getPlayerInv().player.world, this.getPlayerInv().player, patternInSlot, grid)) {
                this.patternSlotOUT.putStack(ItemStack.EMPTY);
            }
        }

        this.detectAndSendChanges();
    }

    private ItemStack[] getInputs() {
        int size = this.patternCraftMode ? 9 : this.patternCraftingInv.getSlots();
        ItemStack[] input = new ItemStack[size];
        boolean hasValue = false;
        for (int i = 0; i < size; i++) {
            input[i] = this.patternCraftingInv.getStackInSlot(i);
            if (!input[i].isEmpty()) {
                hasValue = true;
            }
        }
        return hasValue ? input : null;
    }

    private ItemStack[] getOutputs() {
        if (this.patternCraftMode) {
            this.updatePatternCraftingRecipe();
            ItemStack out = this.cOut.getStackInSlot(0);
            if (!out.isEmpty() && out.getCount() > 0) {
                return new ItemStack[]{out};
            }
        } else {
            ArrayList<ItemStack> list = new ArrayList<>();
            boolean hasValue = false;
            for (int g = 0; g < 9; g++) {
                for (int r = 0; r < 3; r++) {
                    ItemStack out = this.outputSlotGroup[g][r].getStack();
                    if (!out.isEmpty() && out.getCount() > 0) {
                        list.add(out);
                        hasValue = true;
                    }
                }
            }
            if (hasValue) {
                return list.toArray(new ItemStack[0]);
            }
        }
        return null;
    }

    private boolean isPattern(ItemStack output) {
        if (output.isEmpty()) {
            return false;
        }
        // ae2fc 的流体/大物品样板继承自 ItemEncodedPattern，isSameAs 不匹配子类
        return output.getItem() instanceof appeng.items.misc.ItemEncodedPattern
                || AEApi.instance().definitions().materials().blankPattern().isSameAs(output);
    }

    private boolean isFluidDrop(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String registryName = stack.getItem().getRegistryName().toString();
        return "ae2fc:fluid_drop".equals(registryName) || "ae2fc:fluid_packet".equals(registryName);
    }

    private NBTTagCompound createItemTag(ItemStack stack) {
        NBTTagCompound tag = new NBTTagCompound();
        if (!stack.isEmpty()) {
            ItemStackHelper.stackWriteToNBT(stack, tag);
        }
        return tag;
    }

    // ================== 数量调整 ==================

    public void multiply(int multiple) {
        IItemHandler inv = this.patternCraftingInv;
        int size = this.patternCraftMode ? 9 : 81;
        IItemHandler outInv = this.patternCraftMode ? this.cOut : this.patternOutputInv;
        int outSize = this.patternCraftMode ? 1 : 27;

        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() * multiple);
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() * multiple);
            }
        }
        this.detectAndSendChanges();
    }

    public void divide(int divide) {
        IItemHandler inv = this.patternCraftingInv;
        int size = this.patternCraftMode ? 9 : 81;
        IItemHandler outInv = this.patternCraftMode ? this.cOut : this.patternOutputInv;
        int outSize = this.patternCraftMode ? 1 : 27;

        boolean canDiv = true;
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() % divide != 0) {
                canDiv = false;
                break;
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() % divide != 0) {
                canDiv = false;
                break;
            }
        }
        if (!canDiv) return;

        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() / divide);
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() / divide);
            }
        }
        this.detectAndSendChanges();
    }

    public void increase(int amount) {
        IItemHandler inv = this.patternCraftingInv;
        int size = this.patternCraftMode ? 9 : 81;
        IItemHandler outInv = this.patternCraftMode ? this.cOut : this.patternOutputInv;
        int outSize = this.patternCraftMode ? 1 : 27;

        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(Math.max(1, s.getCount() + amount));
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(Math.max(1, s.getCount() + amount));
            }
        }
        this.detectAndSendChanges();
    }

    public void decrease(int amount) {
        IItemHandler inv = this.patternCraftingInv;
        int size = this.patternCraftMode ? 9 : 81;
        IItemHandler outInv = this.patternCraftMode ? this.cOut : this.patternOutputInv;
        int outSize = this.patternCraftMode ? 1 : 27;

        boolean canDecrease = true;
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() - amount < 1) {
                canDecrease = false;
                break;
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty() && s.getCount() - amount < 1) {
                canDecrease = false;
                break;
            }
        }
        if (!canDecrease) return;

        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() - amount);
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() - amount);
            }
        }
        this.detectAndSendChanges();
    }

    public void maximizeCount() {
        IItemHandler inv = this.patternCraftingInv;
        int size = this.patternCraftMode ? 9 : 81;
        IItemHandler outInv = this.patternCraftMode ? this.cOut : this.patternOutputInv;
        int outSize = this.patternCraftMode ? 1 : 27;

        int maxGrowth = Integer.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                maxGrowth = Math.min(maxGrowth, s.getMaxStackSize() - s.getCount());
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                maxGrowth = Math.min(maxGrowth, s.getMaxStackSize() - s.getCount());
            }
        }
        if (maxGrowth <= 0 || maxGrowth == Integer.MAX_VALUE) return;

        for (int i = 0; i < size; i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() + maxGrowth);
            }
        }
        for (int i = 0; i < outSize; i++) {
            ItemStack s = outInv.getStackInSlot(i);
            if (!s.isEmpty()) {
                s.setCount(s.getCount() + maxGrowth);
            }
        }
        this.detectAndSendChanges();
    }

    // ================== JEI 配方转移 ==================

    public void loadPattern(byte mode, boolean isCrafting, int gridSize, java.util.Map<Integer, ItemStack> inputs, java.util.Map<Integer, ItemStack> outputs) {
        // mode: 0=default/both, 1=encoding only (shift), 2=alt (crafting→合成栏, processing→右侧存储区)

        if (isCrafting) {
            // Crafting recipe: 可以填充左边合成台和右边编码区

            // 1. 填充左边真实合成台(mode=0 或 mode=2),只支持 3x3
            if (mode == 0 || mode == 2) {
                this.fillCraftingGridFromJEI(inputs);
            }

            // 2. 填充右边编码区(mode=0 或 mode=1)
            if (mode == 0 || mode == 1) {
                if (!this.patternCraftMode) {
                    this.setPatternCraftMode(true);
                }
                // 清空所有 crafting slots(支持 Extended Crafting 大 grid)
                int maxSlots = gridSize * gridSize;
                for (int i = 0; i < 81; i++) {
                    this.patternCraftingInv.setStackInSlot(i, ItemStack.EMPTY);
                }
                this.cOut.setStackInSlot(0, ItemStack.EMPTY);
                for (java.util.Map.Entry<Integer, ItemStack> entry : inputs.entrySet()) {
                    int slot = entry.getKey();
                    if (slot >= 0 && slot < maxSlots && slot < 81) {
                        this.patternCraftingInv.setStackInSlot(slot, entry.getValue().copy());
                    }
                }
                if (!outputs.isEmpty()) {
                    this.cOut.setStackInSlot(0, outputs.values().iterator().next().copy());
                }
                this.updatePatternCraftingRecipe();
            }
        } else {
            // Processing recipe
            if (mode == 2) {
                // Alt: processing → 从网络提取 inputs 放入右侧 27 槽样板存储区(自动堆叠)
                for (int i = 0; i < this.rightPatternStorage.getSlots(); i++) {
                    this.rightPatternStorage.setStackInSlot(i, ItemStack.EMPTY);
                }
                IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
                for (ItemStack in : inputs.values()) {
                    if (in == null || in.isEmpty()) continue;
                    IAEItemStack toExtract = channel.createStack(in);
                    if (toExtract != null) {
                        toExtract.setStackSize(in.getCount());
                        IAEItemStack extracted = Platform.poweredExtraction(
                                this.getPowerSource(), this.getCellInventory(), toExtract, this.getActionSource());
                        if (extracted != null && extracted.getStackSize() > 0) {
                            ItemStack result = extracted.createItemStack();
                            ItemStack remainder = net.minecraftforge.items.ItemHandlerHelper.insertItemStacked(
                                    this.rightPatternStorage, result, false);
                            // 槽位满了仍有剩余,返还网络
                            if (!remainder.isEmpty()) {
                                IAEItemStack toReturn = channel.createStack(remainder);
                                if (toReturn != null) {
                                    toReturn.setStackSize(remainder.getCount());
                                    this.getCellInventory().injectItems(toReturn, Actionable.MODULATE, this.getActionSource());
                                }
                            }
                        }
                    }
                }
            } else if (mode == 0 || mode == 1) {
                if (this.patternCraftMode) {
                    this.setPatternCraftMode(false);
                }
                // 计算需要显示的 group 范围,清空相关 slots
                int maxSlot = 0;
                for (int slot : inputs.keySet()) {
                    if (slot > maxSlot) maxSlot = slot;
                }
                int neededGroups = (maxSlot / 9) + 1;
                for (int g = 0; g < neededGroups && g < 9; g++) {
                    int startIdx = g * 9;
                    for (int i = 0; i < 9 && startIdx + i < 81; i++) {
                        this.patternCraftingInv.setStackInSlot(startIdx + i, ItemStack.EMPTY);
                    }
                }
                int startOutIdx = this.scrollOffset * 3;
                for (int i = 0; i < 3 && startOutIdx + i < 27; i++) {
                    this.patternOutputInv.setStackInSlot(startOutIdx + i, ItemStack.EMPTY);
                }
                // 填充输入：按 JEI slot index 直接映射到 patternCraftingInv
                for (java.util.Map.Entry<Integer, ItemStack> entry : inputs.entrySet()) {
                    int slot = entry.getKey();
                    if (slot >= 0 && slot < 81) {
                        this.patternCraftingInv.setStackInSlot(slot, entry.getValue().copy());
                    }
                }
                // 填充输出
                int outIdx = 0;
                for (ItemStack out : outputs.values()) {
                    if (outIdx < 3 && startOutIdx + outIdx < 27) {
                        this.patternOutputInv.setStackInSlot(startOutIdx + outIdx, out.copy());
                        outIdx++;
                    }
                }
            }
        }

        this.detectAndSendChanges();
    }

    /**
     * 从 JEI 配方填充左侧真实合成台.先尝试从网络提取物品,网络中没有则从玩家背包移动.
     */
    private void fillCraftingGridFromJEI(java.util.Map<Integer, ItemStack> inputs) {
        // 1. 清空现有物品并返还网络/背包
        for (int i = 0; i < 9; i++) {
            ItemStack existing = this.craftingInv.extractItem(i, Integer.MAX_VALUE, false);
            if (!existing.isEmpty()) {
                this.returnToNetworkOrPlayer(existing);
            }
        }
        this.craftingOutput.setStackInSlot(0, ItemStack.EMPTY);

        // 2. 按 JEI slot index 提取并放入所需物品
        for (java.util.Map.Entry<Integer, ItemStack> entry : inputs.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= 9) continue;
            ItemStack needed = entry.getValue().copy();

            // 先尝试从 AE 网络提取
            boolean filled = false;
            if (this.getPowerSource() != null && this.getCellInventory() != null) {
                try {
                    IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
                    IAEItemStack toExtract = channel.createStack(needed);
                    if (toExtract != null) {
                        toExtract.setStackSize(needed.getCount());
                        IAEItemStack extracted = Platform.poweredExtraction(this.getPowerSource(), this.getCellInventory(), toExtract, this.getActionSource());
                        if (extracted != null && extracted.getStackSize() > 0) {
                            this.craftingInv.setStackInSlot(slot, extracted.createItemStack());
                            filled = true;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            if (filled) continue;

            // 网络没有,尝试从玩家背包移动
            if (this.moveFromPlayerToCrafting(slot, needed)) {
                continue;
            }

            // 都没有,slot 保持为空
        }

        this.updateRealCraftingRecipe();
    }

    /**
     * 从玩家背包寻找匹配物品并移动到 craftingInv 的指定 slot.
     */
    private boolean moveFromPlayerToCrafting(int craftingSlot, ItemStack needed) {
        for (int i = 0; i < this.getPlayerInv().getSizeInventory(); i++) {
            ItemStack playerStack = this.getPlayerInv().getStackInSlot(i);
            if (!playerStack.isEmpty() && playerStack.isItemEqual(needed) && ItemStack.areItemStackTagsEqual(playerStack, needed)) {
                int toMove = Math.min(playerStack.getCount(), needed.getCount());
                playerStack.shrink(toMove);
                if (playerStack.getCount() <= 0) {
                    this.getPlayerInv().setInventorySlotContents(i, ItemStack.EMPTY);
                }
                ItemStack placed = needed.copy();
                placed.setCount(toMove);
                this.craftingInv.setStackInSlot(craftingSlot, placed);
                return true;
            }
        }
        return false;
    }

    // ================== 清除 ==================

    public void clearCrafting() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.craftingInv.extractItem(i, Integer.MAX_VALUE, false);
            this.returnToNetworkOrPlayer(stack);
        }
        ItemStack output = this.craftingOutput.extractItem(0, Integer.MAX_VALUE, false);
        this.returnToNetworkOrPlayer(output);
        this.updateRealCraftingRecipe();
        this.detectAndSendChanges();
    }

    public void clearPattern() {
        if (this.patternCraftMode) {
            for (int i = 0; i < 9; i++) {
                this.patternCraftingInv.setStackInSlot(i, ItemStack.EMPTY);
            }
            this.cOut.setStackInSlot(0, ItemStack.EMPTY);
        } else {
            for (int i = 0; i < 81; i++) {
                this.patternCraftingInv.setStackInSlot(i, ItemStack.EMPTY);
            }
            for (int i = 0; i < 27; i++) {
                this.patternOutputInv.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
        this.detectAndSendChanges();
    }

    private void returnToNetworkOrPlayer(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (this.getPowerSource() == null || this.getCellInventory() == null) {
            if (!this.getPlayerInv().addItemStackToInventory(stack)) {
                this.getPlayerInv().player.dropItem(stack, false);
            }
            return;
        }
        IItemStorageChannel channel = AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class);
        IAEItemStack ais = channel.createStack(stack);
        IAEItemStack remainder = Platform.poweredInsert(this.getPowerSource(), this.getCellInventory(), ais, this.getActionSource());
        if (remainder != null && remainder.getStackSize() > 0) {
            ItemStack left = remainder.createItemStack();
            if (!this.getPlayerInv().addItemStackToInventory(left)) {
                this.getPlayerInv().player.dropItem(left, false);
            }
        }
    }

    // ================== ItemStack NBT 状态持久化(仅存 craftingMode / substitute / scrollOffset) ==================

    private void loadStateFromItemNBT() {
        if (!(this.terminalHost instanceof WirelessTerminalGuiObject)) return;
        WirelessTerminalGuiObject wt = (WirelessTerminalGuiObject) this.terminalHost;
        ItemStack stack = wt.getItemStack();
        if (stack.isEmpty()) return;
        NBTTagCompound tag = Platform.openNbtData(stack);

        if (tag.hasKey(NBT_CRAFTING_MODE)) {
            this.patternCraftMode = tag.getBoolean(NBT_CRAFTING_MODE);
        }
        if (tag.hasKey(NBT_SUBSTITUTE)) {
            this.substitute = tag.getBoolean(NBT_SUBSTITUTE);
        }
        if (tag.hasKey(NBT_SCROLL_OFFSET)) {
            this.scrollOffset = Math.max(0, Math.min(tag.getInteger(NBT_SCROLL_OFFSET), getMaxScrollOffset()));
        }

        this.applyPatternCraftMode();
    }

    private void saveStateToItemNBT() {
        if (!(this.terminalHost instanceof WirelessTerminalGuiObject)) return;
        if (!Platform.isServer()) return;
        WirelessTerminalGuiObject wt = (WirelessTerminalGuiObject) this.terminalHost;
        ItemStack stack = wt.getItemStack();
        if (stack.isEmpty()) return;
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setBoolean(NBT_CRAFTING_MODE, this.patternCraftMode);
        tag.setBoolean(NBT_SUBSTITUTE, this.substitute);
        tag.setInteger(NBT_SCROLL_OFFSET, this.scrollOffset);
    }

    private void saveCraftingModeToNBT() {
        if (!(this.terminalHost instanceof WirelessTerminalGuiObject)) return;
        WirelessTerminalGuiObject wt = (WirelessTerminalGuiObject) this.terminalHost;
        ItemStack stack = wt.getItemStack();
        if (stack.isEmpty()) return;
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setBoolean(NBT_CRAFTING_MODE, this.patternCraftMode);
    }

    private void saveSubstituteToNBT() {
        if (!(this.terminalHost instanceof WirelessTerminalGuiObject)) return;
        WirelessTerminalGuiObject wt = (WirelessTerminalGuiObject) this.terminalHost;
        ItemStack stack = wt.getItemStack();
        if (stack.isEmpty()) return;
        NBTTagCompound tag = Platform.openNbtData(stack);
        tag.setBoolean(NBT_SUBSTITUTE, this.substitute);
    }

    @Override
    public void onContainerClosed(EntityPlayer playerIn) {
        // R3: 从 ItemStorageAdapter 的打开玩家列表中移除
        if (playerIn instanceof net.minecraft.entity.player.EntityPlayerMP) {
            ItemStorageAdapter adapter = this.cachedAdapter != null ? this.cachedAdapter : findItemStorageAdapter();
            if (adapter != null) {
                adapter.removeOpenPlayer((net.minecraft.entity.player.EntityPlayerMP) playerIn);
            }
        }
        this.cachedAdapter = null; // 清空缓存，避免持有过期引用
        super.onContainerClosed(playerIn);
        this.saveStateToItemNBT();
        if (this.omniData != null) {
            this.omniData.markDirty();
        }
    }

    // ================== IContainerCraftingPacket ==================

    @Override
    public appeng.api.networking.IGridNode getNetworkNode() {
        if (this.terminalHost instanceof appeng.api.networking.IGridHost) {
            return ((appeng.api.networking.IGridHost) this.terminalHost).getGridNode(appeng.api.util.AEPartLocation.INTERNAL);
        }
        if (this.wirelessObject != null) {
            return this.wirelessObject.getActionableNode();
        }
        return null;
    }

    @Override
    public IItemHandler getInventoryByName(String name) {
        if ("crafting".equals(name)) {
            return this.craftingInv;
        }
        if ("output".equals(name)) {
            return this.craftingOutput;
        }
        if ("pattern".equals(name)) {
            return this.patternInv;
        }
        return null;
    }

    @Override
    public appeng.api.networking.security.IActionSource getActionSource() {
        return super.getActionSource();
    }

    @Override
    public boolean useRealItems() {
        return true;
    }

    @Override
    public ItemStack[] getViewCells() {
        return super.getViewCells();
    }

    // ================== 物品库更新回调 ==================

    public interface IInventoryUpdateListener {
        void onInventoryUpdate(List<IAEItemStack> list);
    }

    private IInventoryUpdateListener inventoryListener;

    public void setInventoryListener(IInventoryUpdateListener listener) {
        this.inventoryListener = listener;
    }

    // ================== IInventorySlotAware ==================

    @Override
    public int getInventorySlot() {
        if (this.terminalHost instanceof IInventorySlotAware) {
            return ((IInventorySlotAware) this.terminalHost).getInventorySlot();
        }
        return -1;
    }

    @Override
    public boolean isBaubleSlot() {
        if (this.terminalHost instanceof IInventorySlotAware) {
            return ((IInventorySlotAware) this.terminalHost).isBaubleSlot();
        }
        return false;
    }

    @Override
    public Object getTarget() {
        Object target = super.getTarget();
        if (target == null && this.terminalHost != null) {
            return this.terminalHost;
        }
        return target;
    }

    @Override
    public void saveChanges() {
    }

    @Override
    public void postUpdate(List<IAEItemStack> list) {
        for (IAEItemStack is : list) {
            this.items.add(is);
        }
        if (this.inventoryListener != null) {
            this.inventoryListener.onInventoryUpdate(list);
        }
    }

    /**
     * 【关键覆盖】拦截 NetworkMonitor 的增量事件，阻止原版将变化写入 this.items。
     * 改为将变化累积到 omniItems，由自定义同步通道发送。
     */
    @Override
    public void postChange(appeng.api.networking.storage.IBaseMonitor<IAEItemStack> monitor,
                           Iterable<IAEItemStack> change,
                           appeng.api.networking.security.IActionSource source) {
        // 不调用 super.postChange() —— 阻止原版增量进入 this.items
        for (IAEItemStack is : change) {
            this.omniItems.add(is);
        }
        // 外部 ME 网络存储变化时，通知 adapter 刷新 sortedList
        if (this.cachedAdapter == null) {
            this.cachedAdapter = findItemStorageAdapter();
        }
        if (this.cachedAdapter != null) {
            this.cachedAdapter.markSortedListDirty();
        }
    }

    /**
     * 【关键覆盖】拦截 NetworkMonitor 的 forceUpdate 全量刷新事件，
     * 阻止原版立即调用 queueInventory() 发送致命大包。
     * 改为标记 omniNeedsFullSync，由自定义通道在后续 tick 中分批处理。
     */
    @Override
    public void onListUpdate() {
        // 不调用 super.onListUpdate() —— 阻止 forceUpdate 触发 queueInventory 全量
        this.omniNeedsFullSync = true;
    }

    /**
     * 【关键覆盖】玩家打开 GUI 时触发。跳过 ContainerMEMonitorable 的 queueInventory() 全量同步，
     * 改为触发 Omni Terminal 自己的分批全量同步。
     */
    @Override
    public void func_75132_a(net.minecraft.inventory.IContainerListener c) {
        try {
            Method m = appeng.container.AEBaseContainer.class
                    .getDeclaredMethod("func_75132_a", net.minecraft.inventory.IContainerListener.class);
            m.setAccessible(true);
            m.invoke(this, c);
        } catch (NoSuchMethodException e) {
            if (this.listeners.contains(c)) {
                throw new IllegalArgumentException("Listener already listening");
            }
            this.listeners.add(c);
            c.sendAllContents(this, this.getInventory());
            this.detectAndSendChanges();
        } catch (Exception e) {
            appeng.core.AELog.error(e);
        }

        // 重置 ID 映射表（新 session）
        this.omniItemToId.clear();
        this.omniIdToItem.clear();
        this.omniNextItemId = 1;

        this.omniNeedsFullSync = true;
    }

    /**
     * 覆盖 mergeItemStack 以突破 ItemStack.getMaxStackSize() 的 64 上限.
     * 原版 Container.mergeItemStack 使用 Math.min(slot.getSlotStackLimit(), stack.getMaxStackSize())
     * 计算最大合并数量,导致即使 slot 支持 4096,shift+点击仍被截断为 64.
     */
    @Override
    protected boolean mergeItemStack(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection) {
        boolean flag = false;
        int i = startIndex;

        if (reverseDirection) {
            i = endIndex - 1;
        }

        if (stack.isStackable()) {
            while (!stack.isEmpty()) {
                if (reverseDirection) {
                    if (i < startIndex) break;
                } else {
                    if (i >= endIndex) break;
                }

                Slot slot = this.inventorySlots.get(i);
                ItemStack itemstack = slot.getStack();

                if (!itemstack.isEmpty() && itemstack.getItem() == stack.getItem()
                        && (!stack.getHasSubtypes() || stack.getMetadata() == itemstack.getMetadata())
                        && ItemStack.areItemStackTagsEqual(stack, itemstack)) {
                    int j = itemstack.getCount() + stack.getCount();
                    // 原版这里用 Math.min(slot.getSlotStackLimit(), stack.getMaxStackSize())
                    // 我们直接取 slot 的上限,不再被 ItemStack.getMaxStackSize() 截断
                    int maxSize = slot.getSlotStackLimit();

                    if (j <= maxSize) {
                        stack.setCount(0);
                        itemstack.setCount(j);
                        slot.onSlotChanged();
                        flag = true;
                    } else if (itemstack.getCount() < maxSize) {
                        stack.shrink(maxSize - itemstack.getCount());
                        itemstack.setCount(maxSize);
                        slot.onSlotChanged();
                        flag = true;
                    }
                }

                if (reverseDirection) {
                    --i;
                } else {
                    ++i;
                }
            }
        }

        if (!stack.isEmpty()) {
            if (reverseDirection) {
                i = endIndex - 1;
            } else {
                i = startIndex;
            }

            while (true) {
                if (reverseDirection) {
                    if (i < startIndex) break;
                } else {
                    if (i >= endIndex) break;
                }

                Slot slot = this.inventorySlots.get(i);
                ItemStack itemstack = slot.getStack();

                if (itemstack.isEmpty() && slot.isItemValid(stack)) {
                    if (stack.getCount() > slot.getSlotStackLimit()) {
                        slot.putStack(stack.splitStack(slot.getSlotStackLimit()));
                    } else {
                        slot.putStack(stack.splitStack(stack.getCount()));
                    }
                    slot.onSlotChanged();
                    flag = true;
                    break;
                }

                if (reverseDirection) {
                    --i;
                } else {
                    ++i;
                }
            }
        }

        return flag;
    }

    // ================== 合成置顶：active crafting 采集与同步 ==================

    /**
     * 每 20 ticks 检查一次 Crafting CPU 中的正在合成物品,变化时发送网络包到客户端.
     */
    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer() && this.wirelessObject != null) {
            if (!this.wirelessObject.rangeCheck()) {
                if (this.isValidContainer()) {
                    this.getPlayerInv().player.sendMessage(PlayerMessages.OutOfRange.get());
                }
                this.setValidContainer(false);
                this.getPlayerInv().player.closeScreen();
                return;
            }
            this.wirelessTickCounter++;
            if (this.wirelessTickCounter > 10) {
                double ext = this.wirelessObject.extractAEPower(0.5 * this.wirelessTickCounter, Actionable.MODULATE, PowerMultiplier.CONFIG);
                if (ext < 0.5 * this.wirelessTickCounter) {
                    if (this.isValidContainer()) {
                        this.getPlayerInv().player.sendMessage(PlayerMessages.DeviceNotPowered.get());
                    }
                    this.setValidContainer(false);
                    this.getPlayerInv().player.closeScreen();
                    return;
                }
                this.wirelessTickCounter = 0;
            }
        }
        super.detectAndSendChanges();
        if (!Platform.isServer()) return;

        // === R3: Omni Terminal 增量更新通知 ===
        // 客户端主动发送 PAGE_REQUEST，服务端只需在变化时通知客户端刷新
        boolean hasChanges = this.omniNeedsFullSync || !this.omniItems.isEmpty();
        if (hasChanges) {
            ItemStorageAdapter adapter = findItemStorageAdapter();
            if (adapter != null) {
                adapter.onStorageChanged(this.getPlayerInv().player.world.getTotalWorldTime());
            }
            this.omniNeedsFullSync = false;
            this.omniItems.resetStatus();
        }

        if (--this.craftingUpdateCooldown > 0) return;
        this.craftingUpdateCooldown = 20;

        List<CraftingStatus> currentActive = this.collectActiveCraftingOnly();

        // 检测已完成的合成：之前存在但现在不在 active 列表中的物品
        for (CraftingStatus prev : this.previousActiveCrafting) {
            boolean stillActive = false;
            for (CraftingStatus curr : currentActive) {
                if (curr.output.equals(prev.output)) {
                    stillActive = true;
                    break;
                }
            }
            if (!stillActive) {
                // 避免重复添加
                boolean alreadyExists = false;
                for (IAEItemStack existing : this.completedCraftingCache) {
                    if (existing.equals(prev.output)) {
                        alreadyExists = true;
                        break;
                    }
                }
                if (!alreadyExists) {
                    this.completedCraftingCache.add(prev.output.copy());
                }
            }
        }
        this.previousActiveCrafting = currentActive;

        // 合并 active + completed 发送给客户端
        List<CraftingStatus> current = new ArrayList<>(currentActive);
        for (IAEItemStack completed : this.completedCraftingCache) {
            current.add(new CraftingStatus(completed, 0, 0));
        }

        if (!this.isCraftingListEqual(this.activeCraftingCache, current)) {
            this.activeCraftingCache = current;
            if (this.getPlayerInv().player instanceof net.minecraft.entity.player.EntityPlayerMP) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.network.sendTo(
                        new com.github.aeddddd.ae2enhanced.network.packet.PacketOmniCraftingUpdate(current),
                        (net.minecraft.entity.player.EntityPlayerMP) this.getPlayerInv().player);
            }
        }
    }

    /**
     * 仅收集当前正在 Crafting CPU 中执行的合成任务.
     * 如果某个物品正在重新合成,将其从 completedCraftingCache 中移除.
     */
    private List<CraftingStatus> collectActiveCraftingOnly() {
        List<CraftingStatus> result = new ArrayList<>();
        if (!(this.terminalHost instanceof appeng.api.networking.security.IActionHost)) return result;
        appeng.api.networking.security.IActionHost host = (appeng.api.networking.security.IActionHost) this.terminalHost;
        appeng.api.networking.IGridNode node = host.getActionableNode();
        if (node == null || node.getGrid() == null) return result;
        appeng.api.networking.crafting.ICraftingGrid craftingGrid = node.getGrid().getCache(appeng.api.networking.crafting.ICraftingGrid.class);
        if (craftingGrid == null) return result;
        for (appeng.api.networking.crafting.ICraftingCPU cpu : craftingGrid.getCpus()) {
            IAEItemStack output = cpu.getFinalOutput();
            if (output != null && output.getStackSize() > 0) {
                long remaining = cpu.getRemainingItemCount();
                long start = cpu.getStartItemCount();
                result.add(new CraftingStatus(output, remaining, start));
                // 该物品正在重新合成,从已完成缓存中移除
                this.completedCraftingCache.removeIf(c -> c.equals(output));
            }
        }
        return result;
    }

    private boolean isCraftingListEqual(List<CraftingStatus> a, List<CraftingStatus> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            CraftingStatus sa = a.get(i);
            CraftingStatus sb = b.get(i);
            if (sa == null || sb == null) return false;
            if (!sa.output.equals(sb.output)) return false;
            if (sa.remaining != sb.remaining) return false;
        }
        return true;
    }

    public void setClientActiveCrafting(List<CraftingStatus> list) {
        this.clientActiveCrafting = list != null ? list : Collections.emptyList();
    }

    public List<CraftingStatus> getClientActiveCrafting() {
        return this.clientActiveCrafting;
    }

    /**
     * 检查 Omni Terminal 升级槽中是否装有选取交互卡.
     */
    public boolean hasPickerUpgrade() {
        for (int i = 0; i < this.rightUpgradeStorage.getSlots(); i++) {
            net.minecraft.item.ItemStack stack = this.rightUpgradeStorage.getStackInSlot(i);
            if (stack.getItem() instanceof com.github.aeddddd.ae2enhanced.item.ItemOmniUpgradeCard
                    && stack.getMetadata() == com.github.aeddddd.ae2enhanced.item.ItemOmniUpgradeCard.META_PICKER) {
                return true;
            }
        }
        return false;
    }

    // ==================== Omni Terminal 自定义同步方法 ====================

    private int getOrAllocateId(IAEItemStack stack) {
        int id = this.omniItemToId.getInt(stack);
        if (id == 0) {
            id = this.omniNextItemId++;
            IAEItemStack key = stack.copy();
            this.omniItemToId.put(key, id);
            this.omniIdToItem.put(id, key);
        }
        return id;
    }

    // R3: sendOmniFullSyncBatch() / sendOmniDeltaSync() 已删除
    // 客户端主动发送 PAGE_REQUEST，服务端通过 UPDATE_NOTIFY 通知变化

    // ================== 服务端搜索（基于超维度仓储索引）====================

    /**
     * 处理客户端发来的搜索请求，利用超维度仓储的预构建索引快速筛选。
     */
    /**
     * R3: 处理客户端发来的分页请求。
     */
    public void handlePageRequest(com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequest request) {
        ItemStorageAdapter adapter = findItemStorageAdapter();
        ItemStorageAdapter.PageResult result;

        IPartitionList<IAEItemStack> viewCellFilter = appeng.items.storage.ItemViewCell.createFilter(this.getViewCells());
        Set<ItemDescriptor> clientFilter = null;
        List<ItemDescriptor> requestFilter = request.getClientFilter();
        if (requestFilter != null) {
            clientFilter = new HashSet<>(requestFilter);
        }

        if (adapter != null) {
            result = adapter.query(
                request.getSearchString(),
                request.getSearchMode(),
                request.getSortBy(),
                request.getSortDir(),
                request.getViewMode(),
                request.getOffset(),
                request.getLimit(),
                viewCellFilter,
                clientFilter
            );
        } else {
            // Fallback: 遍历 omniMonitor.getStorageList()
            result = fallbackPageQuery(request, viewCellFilter, clientFilter);
        }

        // 构建响应
        List<com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult.Entry> entries =
                new ArrayList<>(result.items.size());
        for (IAEItemStack stack : result.items) {
            int id = this.getOrAllocateId(stack);
            entries.add(new com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult.Entry(
                    id, stack, stack.getStackSize()));
        }

        com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult response =
                new com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageResult(
                        result.totalCount, result.offset, entries);

        com.github.aeddddd.ae2enhanced.AE2Enhanced.network.sendTo(
                response,
                (net.minecraft.entity.player.EntityPlayerMP) this.getPlayerInv().player);
    }

    private ItemStorageAdapter.PageResult fallbackPageQuery(
            com.github.aeddddd.ae2enhanced.network.packet.PacketOmniPageRequest request,
            IPartitionList<IAEItemStack> viewCellFilter,
            Set<ItemDescriptor> clientFilter) {
        appeng.api.storage.data.IItemList<IAEItemStack> list = this.omniMonitor.getStorageList();
        List<IAEItemStack> all = new ArrayList<>();
        for (IAEItemStack stack : list) {
            if (!stack.isMeaningful()) continue;
            all.add(stack.copy());
        }

        // ViewMode / ViewCell / ClientFilter 过滤
        List<IAEItemStack> filtered = new ArrayList<>();
        for (IAEItemStack stack : all) {
            if (request.getViewMode() == 2 && !stack.isCraftable()) continue;
            if (request.getViewMode() == 0 && stack.getStackSize() == 0L) continue;
            if (viewCellFilter != null && !viewCellFilter.isListed(stack)) continue;
            if (clientFilter != null && !clientFilter.contains(new ItemDescriptor(stack.getDefinition().copy()))) continue;
            filtered.add(stack);
        }

        // 排序
        appeng.api.config.SortOrder sortBy = appeng.api.config.SortOrder.values()[request.getSortBy()];
        appeng.api.config.SortDir sortDir = appeng.api.config.SortDir.values()[request.getSortDir()];
        Comparator<IAEItemStack> c = getSearchComparator(sortBy);
        appeng.util.ItemSorters.setDirection(sortDir);
        appeng.util.ItemSorters.init();
        filtered.sort(c);

        // 搜索过滤
        String search = request.getSearchString();
        if (search != null && !search.isEmpty()) {
            String query = search.toLowerCase();
            List<IAEItemStack> searched = new ArrayList<>();
            boolean isModSearch = request.getSearchMode() == 1;
            boolean fuzzyModSearch = isModSearch
                    && (com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.terminal.modSearchFuzzyThreshold <= 0
                    || all.size() <= com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.terminal.modSearchFuzzyThreshold);
            for (IAEItemStack stack : filtered) {
                if (isModSearch) {
                    String modId = stack.asItemStackRepresentation().getItem().getRegistryName().getNamespace().toLowerCase();
                    if (fuzzyModSearch ? modId.contains(query) : modId.equals(query)) {
                        searched.add(stack);
                    }
                } else {
                    if (stack.asItemStackRepresentation().getDisplayName().toLowerCase().contains(query)) {
                        searched.add(stack);
                    }
                }
            }
            filtered = searched;
        }

        int total = filtered.size();
        int start = Math.min(request.getOffset(), total);
        int end = Math.min(request.getOffset() + request.getLimit(), total);
        List<IAEItemStack> page = new ArrayList<>(Math.max(0, end - start));
        for (int i = start; i < end; i++) {
            page.add(filtered.get(i));
        }

        return new ItemStorageAdapter.PageResult(total, request.getOffset(), page);
    }

    public void handleSearchRequest(PacketOmniSearchRequest request) {
        ItemStorageAdapter adapter = findItemStorageAdapter();
        List<IAEItemStack> results;

        if (adapter != null) {
            results = adapter.search(request.getQuery(), request.isModSearch(), request.getLimit());
        } else {
            results = fallbackSearch(request);
        }

        // ViewMode 过滤
        appeng.api.config.ViewItems viewMode = appeng.api.config.ViewItems.values()[request.getViewModeOrdinal()];
        List<IAEItemStack> filtered = new ArrayList<>();
        for (IAEItemStack stack : results) {
            if (viewMode == appeng.api.config.ViewItems.CRAFTABLE && !stack.isCraftable()) continue;
            if (viewMode == appeng.api.config.ViewItems.STORED && stack.getStackSize() == 0L) continue;
            filtered.add(stack);
        }

        // 排序
        appeng.api.config.SortOrder sortBy = appeng.api.config.SortOrder.values()[request.getSortByOrdinal()];
        appeng.api.config.SortDir sortDir = appeng.api.config.SortDir.values()[request.getSortDirOrdinal()];
        Comparator<IAEItemStack> c = getSearchComparator(sortBy);
        appeng.util.ItemSorters.setDirection(sortDir);
        appeng.util.ItemSorters.init();
        filtered.sort(c);

        // 截断到 limit
        if (filtered.size() > request.getLimit()) {
            filtered = filtered.subList(0, request.getLimit());
        }

        // 转换为 Entry 列表
        List<PacketOmniSearchResult.Entry> entries = new ArrayList<>(filtered.size());
        for (IAEItemStack stack : filtered) {
            int id = this.getOrAllocateId(stack);
            entries.add(new PacketOmniSearchResult.Entry(id, stack, stack.getStackSize()));
        }

        // 发送结果到客户端
        PacketOmniSearchResult result = new PacketOmniSearchResult(entries);
        result.setFullResult(true);
        com.github.aeddddd.ae2enhanced.AE2Enhanced.network.sendTo(
                result,
                (net.minecraft.entity.player.EntityPlayerMP) this.getPlayerInv().player);
    }

    private ItemStorageAdapter findItemStorageAdapter() {
        ItemStorageAdapter adapter = null;

        // 方案 1：如果 omniMonitor 直接是 SimpleMEMonitor
        if (this.omniMonitor instanceof SimpleMEMonitor) {
            adapter = ((SimpleMEMonitor) this.omniMonitor).getAdapter();
        }

        // 方案 2：遍历网络节点找到 TileHyperdimensionalController
        if (adapter == null) {
            try {
                appeng.api.networking.IGridNode node = getNetworkNode();
                if (node == null || node.getGrid() == null) return null;
                appeng.api.networking.IGrid grid = node.getGrid();

                for (appeng.api.networking.IGridNode gridNode : grid.getNodes()) {
                    Object machine = gridNode.getMachine();
                    if (machine instanceof TileHyperdimensionalController) {
                        TileHyperdimensionalController controller = (TileHyperdimensionalController) machine;
                        if (controller.isFormed()) {
                            adapter = controller.getItemAdapter();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                com.github.aeddddd.ae2enhanced.AE2Enhanced.LOGGER.error("[AE2E] Failed to find ItemStorageAdapter", e);
            }
        }

        // 将网络 monitor 设为 adapter 的外部存储源，确保终端能看到全部物品。
        // 如果 omniMonitor 只是 SimpleMEMonitor（即 adapter 自身包装），则尝试从网格获取真正的网络物品 monitor，
        // 以便拿到 crafting grid 注入的 craftable 标记。
        if (adapter != null) {
            appeng.api.storage.IMEMonitor<IAEItemStack> desiredExternal = this.omniMonitor;
            if (desiredExternal instanceof SimpleMEMonitor) {
                appeng.api.networking.IGridNode node = getNetworkNode();
                if (node != null && node.getGrid() != null) {
                    appeng.api.networking.IGrid grid = node.getGrid();
                    IStorageGrid storageGrid = grid.getCache(IStorageGrid.class);
                    if (storageGrid != null) {
                        appeng.api.storage.IMEMonitor<IAEItemStack> netMonitor = storageGrid.getInventory(
                                AEApi.instance().storage().getStorageChannel(IItemStorageChannel.class));
                        if (netMonitor != null) {
                            desiredExternal = netMonitor;
                        }
                    }
                }
            }
            if (adapter.getExternalMonitor() != desiredExternal) {
                adapter.setExternalMonitor(desiredExternal);
            }
        }

        return adapter;
    }

    private List<IAEItemStack> fallbackSearch(PacketOmniSearchRequest request) {
        // 未找到超维度仓储时的 fallback：遍历 omniMonitor.getStorageList()
        appeng.api.storage.data.IItemList<IAEItemStack> list = this.omniMonitor.getStorageList();
        List<IAEItemStack> results = new ArrayList<>();
        String query = request.getQuery().toLowerCase();

        boolean fuzzyModSearch = request.isModSearch()
                && (com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.terminal.modSearchFuzzyThreshold <= 0
                || getMonitorItemCount() <= com.github.aeddddd.ae2enhanced.config.AE2EnhancedConfig.terminal.modSearchFuzzyThreshold);

        for (IAEItemStack stack : list) {
            if (!stack.isMeaningful()) continue;
            if (request.isModSearch()) {
                String modId = stack.asItemStackRepresentation().getItem().getRegistryName().getNamespace().toLowerCase();
                if (fuzzyModSearch ? modId.contains(query) : modId.equals(query)) {
                    results.add(stack.copy());
                }
            } else {
                String name = stack.asItemStackRepresentation().getDisplayName().toLowerCase();
                if (name.contains(query)) {
                    results.add(stack.copy());
                }
            }
        }
        return results;
    }

    private int getMonitorItemCount() {
        int count = 0;
        for (IAEItemStack stack : this.omniMonitor.getStorageList()) {
            if (stack.isMeaningful()) count++;
        }
        return count;
    }

    private static Comparator<IAEItemStack> getSearchComparator(appeng.api.config.SortOrder sortBy) {
        if (sortBy == appeng.api.config.SortOrder.MOD) {
            return appeng.util.ItemSorters.CONFIG_BASED_SORT_BY_MOD;
        } else if (sortBy == appeng.api.config.SortOrder.AMOUNT) {
            return appeng.util.ItemSorters.CONFIG_BASED_SORT_BY_SIZE;
        } else if (sortBy == appeng.api.config.SortOrder.INVTWEAKS) {
            return appeng.util.ItemSorters.CONFIG_BASED_SORT_BY_INV_TWEAKS;
        }
        return appeng.util.ItemSorters.CONFIG_BASED_SORT_BY_NAME;
    }
}
