package com.github.aeddddd.ae2enhanced.tile;

import appeng.api.storage.data.IAEItemStack;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternRepository;
import com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartRecipe;
import com.github.aeddddd.ae2enhanced.item.ItemSmartBlankPattern;
import com.github.aeddddd.ae2enhanced.item.ItemSmartPattern;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 智能样板接口的 TileEntity.
 *
 * <p>功能：</p>
 * <ul>
 *   <li>存储绑定目标方块信息(boundPos / boundDim / boundBlockId)</li>
 *   <li>管理 SmartPatternData(配方列表、冲突/禁用掩码)</li>
 *   <li>提供样板输入槽和输出槽(通过 ItemStackHandler)</li>
 *   <li>处理编码逻辑(空白样板 → 编码后样板)</li>
 *   <li>MiniGUI 编辑：锁定配方后可修改输入输出</li>
 *   <li>批量替换：底部槽位在所有配方中替换物品</li>
 * </ul>
 *
 * <p>注意：不接入 ME 网络,纯手动配置终端.</p>
 */
public class TileSmartPatternInterface extends TileEntity {

    private static final String NBT_BOUND_POS = "boundPos";
    private static final String NBT_BOUND_DIM = "boundDim";
    private static final String NBT_BOUND_BLOCK_ID = "boundBlockId";
    private static final String NBT_PATTERN_DATA_ID = "patternDataId";
    private static final String NBT_PATTERN_REVISION = "revision";

    // 绑定目标信息
    @Nullable
    private BlockPos boundPos;
    private int boundDim = Integer.MIN_VALUE;
    @Nonnull
    private String boundBlockId = "";

    // 服务端仅持久化 UUID/revision；这里是 repository 快照或客户端 DTO，不是第二权威。
    @Nullable
    private UUID patternDataId;
    private long patternRevision;
    @Nullable
    private SmartPatternData patternData;

    // 配方显示页码(0 = 第1页,每页45个配方)
    private int scrollOffset = 0;

    // 锁定配方的原始索引(-1 = 未锁定)
    // 注意：必须存原始索引而非排序后索引,否则 detectConflicts 重排 displayOrder 后锁定目标会漂移
    private int lockedRecipeIndex = -1;

    // MiniGUI 滚动偏移 (0-8, 对应 9 组输入/输出)
    private int miniGuiScrollOffset = 0;

    // 防止 onContentsChanged 递归
    private boolean isUpdatingMiniGui = false;
    private boolean isUpdatingEncodedPattern = false;

    // MiniGUI 物品栏：前81=输入(9组 x 3x3),后9=输出(1组 x 3x3)
    private final ItemStackHandler miniGuiInventory = new ItemStackHandler(90) {
        @Override
        protected void onContentsChanged(int slot) {
            if (!isUpdatingMiniGui && world != null && !world.isRemote
                    && lockedRecipeIndex >= 0 && patternData != null) {
                syncMiniGuiSlotToRecipe(slot);
            }
            markDirty();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return lockedRecipeIndex >= 0;
        }
    };

    // 底部替换槽位：0=当前物品(左侧), 1=替换目标(右侧)
    private final ItemStackHandler replaceInventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            markDirty();
        }
    };

    // 配方显示槽位：45槽 (9列 x 5行),用于GUI展示配方输出
    private final ItemStackHandler recipeDisplayInventory = new ItemStackHandler(45) {
        @Override
        protected void onContentsChanged(int slot) {
            // 配方显示槽位变化不需要 markDirty(内容由patternData动态计算)
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return false; // 不允许手动放入
        }
    };

    // 物品槽位：0=空白样板输入, 1=编码样板输出
    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            if (slot == 1 && world != null && !world.isRemote && !isUpdatingEncodedPattern) {
                ItemStack stack = getStackInSlot(1);
                if (!stack.isEmpty() && stack.getItem() instanceof ItemSmartPattern) {
                    reloadPatternFromStack(stack);
                }
            }
            markDirty();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (slot == 0) {
                return stack.getItem() instanceof ItemSmartBlankPattern;
            }
            if (slot == 1) {
                return stack.getItem() instanceof ItemSmartPattern;
            }
            return false;
        }
    };

    public TileSmartPatternInterface() {
    }

    // ---- 绑定管理 ----

    public boolean isBound() {
        return boundPos != null && boundDim != Integer.MIN_VALUE && !boundBlockId.isEmpty();
    }

    @Nullable
    public BlockPos getBoundPos() {
        return boundPos;
    }

    public int getBoundDim() {
        return boundDim;
    }

    @Nonnull
    public String getBoundBlockId() {
        return boundBlockId;
    }

    @Nonnull
    public String getBoundBlockDisplayName() {
        if (boundBlockId.isEmpty()) return "";
        String id = boundBlockId.contains("@") ? boundBlockId.substring(0, boundBlockId.indexOf('@')) : boundBlockId;
        try {
            net.minecraft.block.Block block = net.minecraftforge.fml.common.registry.ForgeRegistries.BLOCKS.getValue(new net.minecraft.util.ResourceLocation(id));
            if (block != null) {
                return new net.minecraft.item.ItemStack(block).getDisplayName();
            }
        } catch (Exception e) {
            // fallback to raw id
        }
        return id;
    }

    private static void applyRecipeAction(@Nonnull SmartRecipe recipe, @Nonnull String action) {
        switch (action) {
            case "keepPrimary": recipe.keepPrimary(); break;
            case "multiply2": recipe.multiplyAmounts(2); break;
            case "multiply3": recipe.multiplyAmounts(3); break;
            case "multiply4": recipe.multiplyAmounts(4); break;
            case "multiply5": recipe.multiplyAmounts(5); break;
            case "divide2": recipe.divideAmounts(2); break;
            case "divide3": recipe.divideAmounts(3); break;
            case "divide4": recipe.divideAmounts(4); break;
            case "divide5": recipe.divideAmounts(5); break;
            case "add1": recipe.addToAmounts(1); break;
            case "add-1": recipe.addToAmounts(-1); break;
            case "rotateInputs": recipe.rotateInputs(); break;
            case "rotateOutputs": recipe.rotateOutputs(); break;
            case "clearInputs": recipe.clearInputs(); break;
            case "clearOutputs": recipe.clearOutputs(); break;
            case "stack": recipe.stackInputs(); break;
            case "unstack": recipe.unstackInputs(); break;
            default: break;
        }
    }

    public void setBinding(@Nullable BlockPos pos, int dim, @Nonnull String blockId) {
        this.boundPos = pos;
        this.boundDim = dim;
        this.boundBlockId = blockId;
        clearPatternSnapshot();
        this.lockedRecipeIndex = -1;
        clearMiniGuiInventory();
        markDirty();
        syncToClient();
    }

    public boolean bindWithData(@Nullable BlockPos pos, int dim, @Nonnull String blockId,
                                @Nonnull SmartPatternData data) {
        return bindWithData(pos, dim, blockId, data,
                data.getPatternDataId().equals(patternDataId) ? patternRevision : 0L);
    }

    public boolean bindWithData(@Nullable BlockPos pos, int dim, @Nonnull String blockId,
                                @Nonnull SmartPatternData data, long expectedRevision) {
        if (world == null || world.isRemote) return false;
        long expectedForId = data.getPatternDataId().equals(patternDataId) ? expectedRevision : 0L;
        SmartPatternRepository.UpdateResult result = SmartPatternRepository.get(world)
                .migrate(data, expectedForId);
        if (!result.committed() && result.status() != SmartPatternRepository.Status.STALE) return false;
        applySnapshot(result.snapshot());
        if (patternData != null) patternData.setDisabledMask(data.getDisabledMask());
        if (result.committed()) {
            this.boundPos = pos;
            this.boundDim = dim;
            this.boundBlockId = blockId;
        } else {
            reloadBindingFromSnapshot();
        }
        this.lockedRecipeIndex = -1;
        clearMiniGuiInventory();
        updateRecipeDisplay();
        markDirty();
        syncToClient();
        return result.committed();
    }

    // ---- SmartPatternData 管理 ----

    @Nullable
    public SmartPatternData getPatternData() {
        return patternData;
    }

    public void clearBinding() {
        this.boundPos = null;
        this.boundDim = Integer.MIN_VALUE;
        this.boundBlockId = "";
        clearPatternSnapshot();
        this.lockedRecipeIndex = -1;
        clearMiniGuiInventory();
        markDirty();
        syncToClient();
    }

    @Nullable
    public UUID getPatternDataId() {
        return patternDataId;
    }

    public long getPatternRevision() {
        return patternRevision;
    }

    public void setPatternData(@Nullable SmartPatternData data) {
        if (data == null) {
            clearPatternSnapshot();
        } else if (world != null && !world.isRemote) {
            bindWithData(boundPos, boundDim, data.getTargetBlockId(), data);
            return;
        } else {
            this.patternDataId = data.getPatternDataId();
            this.patternData = data;
        }
        this.lockedRecipeIndex = -1;
        clearMiniGuiInventory();
        markDirty();
        syncToClient();
    }

    private void clearPatternSnapshot() {
        patternDataId = null;
        patternRevision = 0L;
        patternData = null;
    }

    private void reloadBindingFromSnapshot() {
        boundBlockId = patternData == null ? "" : patternData.getTargetBlockId();
    }

    /**
     * 编码：将输入槽的空白样板转换为编码后的智能样板,放入输出槽.
     *
     * <p>如果输出槽已存在同一 patternDataId 的已编码智能样板,则直接更新该样板的 NBT,
     * 不会消耗新的空白样板.</p>
     *
     * @return 是否编码成功
     */
    public boolean encodePattern(@Nonnull EntityPlayer player) {
        return encodePattern(patternRevision);
    }

    public boolean encodePattern(long expectedRevision) {
        if (patternData == null || patternDataId == null || patternData.hasConflicts()
                || !isCurrent(expectedRevision)) {
            reloadFromRepository(expectedRevision);
            return false;
        }

        ItemStack output = inventory.getStackInSlot(1);
        if (!output.isEmpty() && (!(output.getItem() instanceof ItemSmartPattern)
                || !patternDataId.equals(ItemSmartPattern.getPatternDataId(output)))) {
            return false;
        }
        ItemStack input = inventory.getStackInSlot(0);
        if (output.isEmpty() && (input.isEmpty()
                || !(input.getItem() instanceof ItemSmartBlankPattern))) {
            return false;
        }

        ItemStack encoded = ItemSmartPattern.createPattern(patternDataId, patternData.getDisabledMask(),
                patternData.getRecipeCount(), patternData.getTargetBlockId(), patternRevision);
        if (output.isEmpty()) {
            input.shrink(1);
            if (input.isEmpty()) inventory.setStackInSlot(0, ItemStack.EMPTY);
        }
        isUpdatingEncodedPattern = true;
        try {
            inventory.setStackInSlot(1, encoded);
        } finally {
            isUpdatingEncodedPattern = false;
        }
        markDirty();
        syncToClient();
        return true;
    }

    /** 从物品重载共享内容，但 disabledMask 始终以该物品自己的 NBT 为准。 */
    private void reloadPatternFromStack(@Nonnull ItemStack stack) {
        UUID dataId = ItemSmartPattern.getPatternDataId(stack);
        if (dataId == null || world == null || world.isRemote) return;
        SmartPatternRepository.Snapshot snapshot = SmartPatternRepository.get(world).get(dataId);
        if (snapshot == null) return;
        applySnapshot(snapshot);
        if (patternData != null) patternData.setDisabledMask(ItemSmartPattern.getDisabledMask(stack));
        this.lockedRecipeIndex = -1;
        clearMiniGuiInventory();
        updateRecipeDisplay();
        markDirty();
        syncToClient();
    }

    private boolean isCurrent(long expectedRevision) {
        return patternDataId != null && expectedRevision == patternRevision && world != null && !world.isRemote
                && isCurrentInRepository();
    }

    private boolean isCurrentInRepository() {
        SmartPatternRepository.Snapshot current = SmartPatternRepository.get(world).get(patternDataId);
        return current != null && current.revision() == patternRevision;
    }

    private void reloadFromRepository(long attemptedRevision) {
        if (attemptedRevision != patternRevision || patternDataId == null || world == null || world.isRemote) return;
        SmartPatternRepository.Snapshot current = SmartPatternRepository.get(world).get(patternDataId);
        if (current != null && current.revision() != patternRevision) {
            java.util.BitSet disabled = patternData == null ? new java.util.BitSet() : patternData.getDisabledMask();
            applySnapshot(current);
            if (patternData != null) patternData.setDisabledMask(disabled);
            lockedRecipeIndex = -1;
            clearMiniGuiInventory();
            updateRecipeDisplay();
            markDirty();
            syncToClient();
        }
    }

    // ---- 物品槽位 ----

    @Nonnull
    public ItemStackHandler getRecipeDisplayInventory() {
        return recipeDisplayInventory;
    }

    @Nonnull
    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Nonnull
    public ItemStackHandler getMiniGuiInventory() {
        return miniGuiInventory;
    }

    @Nonnull
    public ItemStackHandler getReplaceInventory() {
        return replaceInventory;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public void setScrollOffset(int scrollOffset) {
        int maxPage = getMaxPage();
        int newOffset = Math.max(0, Math.min(maxPage, scrollOffset));
        if (this.scrollOffset != newOffset) {
            this.scrollOffset = newOffset;
            updateRecipeDisplay();
            markDirty();
        }
    }

    private int getMaxPage() {
        if (patternData == null) return 0;
        return Math.max(0, (patternData.getRecipeCount() - 1) / 45);
    }

    // ---- 锁定与 MiniGUI ----

    /**
     * 获取锁定配方的排序后显示索引(供 GUI 显示用,-1 = 未锁定).
     * 内部存储为原始索引,此处映射为显示索引.
     */
    public int getLockedRecipeIndex() {
        if (lockedRecipeIndex < 0 || patternData == null) return -1;
        return patternData.getSortedIndex(lockedRecipeIndex);
    }

    /**
     * 判断指定排序后显示索引的配方是否被锁定.
     */
    public boolean isRecipeLocked(int sortedIndex) {
        if (lockedRecipeIndex < 0 || patternData == null) return false;
        return patternData.getDisplayIndex(sortedIndex) == lockedRecipeIndex;
    }

    /**
     * 锁定指定排序后显示索引的配方(内部转换为原始索引存储).
     */
    public void lockRecipe(int sortedIndex) {
        if (patternData == null) return;
        int original = patternData.getDisplayIndex(sortedIndex);
        if (original < 0) return;
        this.lockedRecipeIndex = original;
        updateMiniGuiFromRecipe();
        markDirty();
        syncToClient();
    }

    public void unlockRecipe() {
        this.lockedRecipeIndex = -1;
        clearMiniGuiInventory();
        markDirty();
        syncToClient();
    }

    private void applySnapshot(@Nullable SmartPatternRepository.Snapshot snapshot) {
        if (snapshot == null) {
            clearPatternSnapshot();
            return;
        }
        this.patternData = snapshot.data();
        this.patternDataId = patternData.getPatternDataId();
        this.patternRevision = snapshot.revision();
    }

    public void modifyLockedRecipe(@Nonnull String action) {
        modifyLockedRecipe(action, patternRevision);
    }

    public boolean modifyLockedRecipe(@Nonnull String action, long expectedRevision) {
        int recipeIndex = lockedRecipeIndex;
        if (recipeIndex < 0 || patternData == null || recipeIndex >= patternData.getRecipeCount()) return false;
        boolean committed = commitEdit(expectedRevision, data -> {
            SmartRecipe recipe = data.getRecipes().get(recipeIndex);
            applyRecipeAction(recipe, action);
            data.detectConflicts();
            return data;
        });
        if (committed) updateMiniGuiFromRecipe();
        return committed;
    }

    public void deleteDisabledRecipes() {
        deleteDisabledRecipes(patternRevision);
    }

    public boolean deleteDisabledRecipes(long expectedRevision) {
        boolean committed = commitEdit(expectedRevision, data -> {
            data.deleteDisabledRecipes();
            return data;
        });
        if (committed && lockedRecipeIndex >= 0) {
            lockedRecipeIndex = -1;
            clearMiniGuiInventory();
        }
        setScrollOffset(scrollOffset);
        return committed;
    }

    public void replaceInAllRecipes(@Nonnull ItemStack from, @Nonnull ItemStack to) {
        replaceInAllRecipes(from, to, patternRevision);
    }

    public boolean replaceInAllRecipes(@Nonnull ItemStack from, @Nonnull ItemStack to, long expectedRevision) {
        if (from.isEmpty()) return false;
        IAEItemStack fromAE = AEItemStack.fromItemStack(from);
        if (fromAE == null) return false;
        IAEItemStack toAE = to.isEmpty() ? null : AEItemStack.fromItemStack(to);
        boolean committed = commitEdit(expectedRevision, data -> {
            data.replaceInAllRecipes(fromAE, toAE);
            return data;
        });
        if (committed && lockedRecipeIndex >= 0) updateMiniGuiFromRecipe();
        return committed;
    }

    public void addNewRecipe() {
        addNewRecipe(patternRevision);
    }

    public boolean addNewRecipe(long expectedRevision) {
        if (patternData == null) return false;
        boolean committed = commitEdit(expectedRevision, data -> {
            if (data.getRecipeCount() < com.github.aeddddd.ae2enhanced.config.ServerConfig.current().smartPattern.maxRecipes) {
                data.addEmptyRecipe();
            }
            return data;
        });
        if (committed) {
            int original = patternData.getRecipeCount() - 1;
            this.lockedRecipeIndex = original;
            this.miniGuiScrollOffset = 0;
            int sorted = patternData.getSortedIndex(original);
            if (sorted >= 0) this.scrollOffset = sorted / 45;
            updateMiniGuiFromRecipe();
        }
        return committed;
    }

    public void fillLockedRecipe(@Nonnull IAEItemStack[] inputs, @Nonnull IAEItemStack[] outputs) {
        fillLockedRecipe(inputs, outputs, patternRevision);
    }

    public boolean fillLockedRecipe(@Nonnull IAEItemStack[] inputs, @Nonnull IAEItemStack[] outputs,
                                    long expectedRevision) {
        int recipeIndex = lockedRecipeIndex;
        if (recipeIndex < 0 || patternData == null || recipeIndex >= patternData.getRecipeCount()) return false;
        boolean committed = commitEdit(expectedRevision, data -> {
            SmartRecipe recipe = data.getRecipes().get(recipeIndex);
            recipe.setInputs(inputs);
            recipe.setOutputs(outputs);
            data.detectConflicts();
            return data;
        });
        if (committed) updateMiniGuiFromRecipe();
        return committed;
    }

    private boolean commitEdit(long expectedRevision,
                               @Nonnull java.util.function.UnaryOperator<SmartPatternData> editor) {
        if (patternDataId == null || world == null || world.isRemote) return false;
        java.util.BitSet disabled = patternData == null ? new java.util.BitSet() : patternData.getDisabledMask();
        SmartPatternRepository.UpdateResult result = SmartPatternRepository.get(world)
                .edit(patternDataId, expectedRevision, editor);
        if (result.committed() || result.status() == SmartPatternRepository.Status.STALE) {
            applySnapshot(result.snapshot());
            if (patternData != null) patternData.setDisabledMask(disabled);
        }
        if (!result.committed()) {
            lockedRecipeIndex = -1;
            clearMiniGuiInventory();
            updateRecipeDisplay();
            syncToClient();
            return false;
        }
        refreshEncodedPattern();
        updateRecipeDisplay();
        markDirty();
        syncToClient();
        return true;
    }

    private void refreshEncodedPattern() {
        ItemStack output = inventory.getStackInSlot(1);
        if (output.isEmpty() || patternData == null
                || !patternDataId.equals(ItemSmartPattern.getPatternDataId(output))) return;
        ItemStack updated = ItemSmartPattern.createPattern(patternDataId, patternData.getDisabledMask(),
                patternData.getRecipeCount(), patternData.getTargetBlockId(), patternRevision);
        isUpdatingEncodedPattern = true;
        try {
            inventory.setStackInSlot(1, updated);
        } finally {
            isUpdatingEncodedPattern = false;
        }
    }

    private void clearMiniGuiInventory() {
        isUpdatingMiniGui = true;
        try {
            for (int i = 0; i < miniGuiInventory.getSlots(); i++) {
                miniGuiInventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        } finally {
            isUpdatingMiniGui = false;
        }
    }

    private void updateMiniGuiFromRecipe() {
        if (lockedRecipeIndex < 0 || patternData == null
                || lockedRecipeIndex >= patternData.getRecipeCount()) {
            clearMiniGuiInventory();
            return;
        }
        SmartRecipe recipe = patternData.getRecipes().get(lockedRecipeIndex);
        IAEItemStack[] inputs = recipe.getInputs();
        IAEItemStack[] outputs = recipe.getOutputs();

        isUpdatingMiniGui = true;
        try {
            for (int i = 0; i < 81; i++) {
                if (i < inputs.length && inputs[i] != null) {
                    miniGuiInventory.setStackInSlot(i, inputs[i].createItemStack());
                } else {
                    miniGuiInventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            }
            for (int i = 0; i < 9; i++) {
                if (i < outputs.length && outputs[i] != null) {
                    miniGuiInventory.setStackInSlot(i + 81, outputs[i].createItemStack());
                } else {
                    miniGuiInventory.setStackInSlot(i + 81, ItemStack.EMPTY);
                }
            }
        } finally {
            isUpdatingMiniGui = false;
        }
    }

    private void syncMiniGuiSlotToRecipe(int slot) {
        int recipeIndex = lockedRecipeIndex;
        if (recipeIndex < 0 || patternData == null || recipeIndex >= patternData.getRecipeCount()) return;
        ItemStack stack = miniGuiInventory.getStackInSlot(slot);
        IAEItemStack aeStack = stack.isEmpty() ? null : AEItemStack.fromItemStack(stack);
        commitEdit(patternRevision, data -> {
            SmartRecipe recipe = data.getRecipes().get(recipeIndex);
            if (slot < 81) recipe.setInput(slot, aeStack);
            else recipe.setOutput(slot - 81, aeStack);
            data.detectConflicts();
            return data;
        });
    }

    /** Container 的幽灵槽没有独立网络包，进入原槽位写路径前先用每容器 expectedRevision 闸门。 */
    public boolean prepareEdit(long expectedRevision) {
        if (isCurrent(expectedRevision)) return true;
        reloadFromRepository(expectedRevision);
        return false;
    }

    public int getMiniGuiScrollOffset() {
        return miniGuiScrollOffset;
    }

    public void setMiniGuiScrollOffset(int scroll) {
        this.miniGuiScrollOffset = Math.max(0, Math.min(8, scroll));
        markDirty();
    }

    public int getMaxScrollOffset() {
        return 8;
    }

    // ---- 配方显示 ----

    /**
     * 根据当前 scrollOffset(页码)和 patternData 更新配方显示槽位.
     */
    public void updateRecipeDisplay() {
        if (patternData == null) {
            for (int i = 0; i < recipeDisplayInventory.getSlots(); i++) {
                recipeDisplayInventory.setStackInSlot(i, ItemStack.EMPTY);
            }
            return;
        }
        int recipeCount = patternData.getRecipeCount();
        int pageStart = this.scrollOffset * 45;
        for (int i = 0; i < recipeDisplayInventory.getSlots(); i++) {
            int sortedIndex = pageStart + i;
            if (sortedIndex < recipeCount) {
                SmartRecipe recipe = patternData.getRecipe(sortedIndex);
                if (recipe != null) {
                    IAEItemStack primary = recipe.getPrimaryOutput();
                    recipeDisplayInventory.setStackInSlot(i, primary != null ? primary.createItemStack() : ItemStack.EMPTY);
                } else {
                    recipeDisplayInventory.setStackInSlot(i, ItemStack.EMPTY);
                }
            } else {
                recipeDisplayInventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /**
     * 切换指定配方索引的禁用/启用状态.
     */
    public void toggleRecipe(int sortedRecipeIndex) {
        toggleRecipe(sortedRecipeIndex, patternRevision);
    }

    public boolean toggleRecipe(int sortedRecipeIndex, long expectedRevision) {
        if (patternData == null || !prepareEdit(expectedRevision)) return false;
        int originalIndex = patternData.getDisplayIndex(sortedRecipeIndex);
        if (originalIndex < 0 || originalIndex >= patternData.getRecipeCount()) return false;
        patternData.getDisabledMask().flip(originalIndex);
        refreshEncodedPattern();
        updateRecipeDisplay();
        markDirty();
        syncToClient();
        return true;
    }

    /**
     * 破坏时掉落所有内容物.
     */
    public void dropAllContents() {
        if (world == null || world.isRemote) return;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Platform.spawnDrops(world, pos, java.util.Collections.singletonList(stack));
            }
        }
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey(NBT_BOUND_POS)) {
            int[] arr = compound.getIntArray(NBT_BOUND_POS);
            if (arr.length == 3) {
                boundPos = new BlockPos(arr[0], arr[1], arr[2]);
            }
        }
        boundDim = compound.getInteger(NBT_BOUND_DIM);
        boundBlockId = compound.getString(NBT_BOUND_BLOCK_ID);
        if (compound.hasKey("inventory")) {
            inventory.deserializeNBT(compound.getCompoundTag("inventory"));
        }
        scrollOffset = compound.getInteger("scrollOffset");
        lockedRecipeIndex = compound.getInteger("lockedRecipeIndex");
        miniGuiScrollOffset = compound.getInteger("miniGuiScrollOffset");
        if (world != null && world.isRemote && compound.hasKey("recipeDisplay")) {
            NBTTagCompound tag = compound.getCompoundTag("recipeDisplay").copy();
            tag.setInteger("Size", 45);
            recipeDisplayInventory.deserializeNBT(tag);
        }
        if (world != null && world.isRemote && compound.hasKey("miniGuiInventory")) {
            NBTTagCompound tag = compound.getCompoundTag("miniGuiInventory").copy();
            tag.setInteger("Size", 90);
            miniGuiInventory.deserializeNBT(tag);
        }
        if (compound.hasKey("replaceInventory")) {
            replaceInventory.deserializeNBT(compound.getCompoundTag("replaceInventory"));
        }
        clearPatternSnapshot();
        patternRevision = compound.getLong(NBT_PATTERN_REVISION);
        SmartPatternData embedded = compound.hasKey("patternData")
                ? SmartPatternData.fromNBT(compound.getCompoundTag("patternData")) : null;
        if (world != null && world.isRemote) {
            if (embedded != null) {
                patternData = embedded;
                patternDataId = embedded.getPatternDataId();
            }
        } else if (embedded != null) {
            SmartPatternRepository.UpdateResult migrated = SmartPatternRepository.get(world)
                    .migrate(embedded, patternRevision);
            applySnapshot(migrated.snapshot());
            if (patternData != null) patternData.setDisabledMask(embedded.getDisabledMask());
        } else if (compound.hasKey(NBT_PATTERN_DATA_ID + "Most")) {
            UUID dataId = compound.getUniqueId(NBT_PATTERN_DATA_ID);
            applySnapshot(SmartPatternRepository.get(world).get(dataId));
        }
        ItemStack encoded = inventory.getStackInSlot(1);
        if (patternData != null && patternDataId != null
                && patternDataId.equals(ItemSmartPattern.getPatternDataId(encoded))) {
            patternData.setDisabledMask(ItemSmartPattern.getDisabledMask(encoded));
        }
    }

    @Override
    @Nonnull
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (boundPos != null) {
            compound.setIntArray(NBT_BOUND_POS, new int[]{boundPos.getX(), boundPos.getY(), boundPos.getZ()});
        }
        compound.setInteger(NBT_BOUND_DIM, boundDim);
        compound.setString(NBT_BOUND_BLOCK_ID, boundBlockId);
        compound.setTag("inventory", inventory.serializeNBT());
        compound.setInteger("scrollOffset", scrollOffset);
        compound.setInteger("lockedRecipeIndex", lockedRecipeIndex);
        compound.setInteger("miniGuiScrollOffset", miniGuiScrollOffset);
        compound.setTag("replaceInventory", replaceInventory.serializeNBT());
        if (patternDataId != null) {
            compound.setUniqueId(NBT_PATTERN_DATA_ID, patternDataId);
            compound.setLong(NBT_PATTERN_REVISION, patternRevision);
        }
        return compound;
    }

    // ---- 客户端同步 ----

    @Override
    @Nonnull
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = writeToNBT(super.getUpdateTag());
        tag.setTag("recipeDisplay", recipeDisplayInventory.serializeNBT());
        tag.setTag("miniGuiInventory", miniGuiInventory.serializeNBT());
        if (patternData != null) {
            tag.setTag("patternData", patternData.toNBT());
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        readFromNBT(tag);
    }

    @Override
    @Nullable
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, -1, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        handleUpdateTag(pkt.getNbtCompound());
    }

    private void syncToClient() {
        if (world != null && !world.isRemote) {
            net.minecraft.block.state.IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 2);
        }
    }

    // ---- Capability ----

    @Override
    public boolean hasCapability(net.minecraftforge.common.capabilities.Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY
                || super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(net.minecraftforge.common.capabilities.Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (facing == null) {
                return net.minecraftforge.items.CapabilityItemHandler.ITEM_HANDLER_CAPABILITY.cast(inventory);
            }
        }
        return super.getCapability(capability, facing);
    }
}
