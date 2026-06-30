package com.github.aeddddd.ae2enhanced.assembly.blockentity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.util.inv.AppEngInternalInventory;

import com.github.aeddddd.ae2enhanced.assembly.pattern.ScaledPatternDetails;
import com.github.aeddddd.ae2enhanced.multiblock.IPatternProviderHost;
import com.github.aeddddd.ae2enhanced.multiblock.MultiblockControllerBlockEntity;
import com.github.aeddddd.ae2enhanced.registry.ModBlockEntities;
import com.github.aeddddd.ae2enhanced.structure.AssemblyStructure;

/**
 * 装配枢纽控制器方块实体。
 * <p>核心功能：向 AE2 网络提供 Long 级别的并行虚拟样板合成。</p>
 */
public class AssemblyControllerBlockEntity extends MultiblockControllerBlockEntity
        implements IPatternProviderHost {

    private static final int BASE_PATTERN_SLOTS = 510; // 5 页 × 102 槽
    private static final long BASE_PARALLEL = 64;

    private final AppEngInternalInventory patternInventory = new AppEngInternalInventory(BASE_PATTERN_SLOTS);
    private int validationCooldown = 0;

    public AssemblyControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ASSEMBLY_CONTROLLER.get(), pos, state);
        this.patternInventory.setEnableClientEvents(false);
    }

    public InternalInventory getPatternInventory() {
        return patternInventory;
    }

    public void serverTick() {
        if (level == null || level.isClientSide()) {
            return;
        }
        if (validationCooldown-- <= 0) {
            validationCooldown = 20;
            if (isFormed() && !AssemblyStructure.validate(level, worldPosition)) {
                AssemblyStructure.disassemble(level, worldPosition);
            }
        }
    }

    /**
     * 当前并行倍数。后续接入升级卡后可动态调整。
     */
    public long getParallelMultiplier() {
        return BASE_PARALLEL;
    }

    // ---- IPatternProviderHost ----

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        List<IPatternDetails> result = new ArrayList<>();
        Level level = this.level;
        if (level == null || !isFormed()) {
            return result;
        }
        long multiplier = getParallelMultiplier();
        for (int i = 0; i < patternInventory.size(); i++) {
            ItemStack stack = patternInventory.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            IPatternDetails base = PatternDetailsHelper.decodePattern(stack, level);
            if (base != null) {
                result.add(new ScaledPatternDetails(base, multiplier));
            }
        }
        return result;
    }

    @Override
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
        return pushPattern(pattern, inputs, null);
    }

    /**
     * 执行虚拟合成。优先使用传入的 grid node；若未传入，则尝试使用任意已绑定接口的节点。
     */
    public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs, @Nullable IManagedGridNode node) {
        if (level == null || level.isClientSide() || !isFormed()) {
            return false;
        }

        IManagedGridNode targetNode = resolveNode(node);
        if (targetNode == null) {
            return false;
        }

        IStorageService storageService = targetNode.getGrid().getStorageService();
        if (storageService == null) {
            return false;
        }
        MEStorage storage = storageService.getInventory();
        IActionSource source = IActionSource.empty();

        // 预检查输入是否足够
        List<GenericStack> required = collectInputs(pattern);
        for (GenericStack req : required) {
            long available = storage.extract(req.what(), req.amount(), Actionable.SIMULATE, source);
            if (available < req.amount()) {
                return false;
            }
        }

        // 扣除输入
        for (GenericStack req : required) {
            storage.extract(req.what(), req.amount(), Actionable.MODULATE, source);
        }

        // 注入产物
        for (GenericStack output : pattern.getOutputs()) {
            storage.insert(output.what(), output.amount(), Actionable.MODULATE, source);
        }

        return true;
    }

    @Nullable
    private IManagedGridNode resolveNode(@Nullable IManagedGridNode preferred) {
        if (preferred != null && preferred.isReady()) {
            return preferred;
        }
        // 退而求其次：使用第一个绑定接口的节点
        for (BlockPos pos : getInterfaces()) {
            if (level != null && level.getBlockEntity(pos) instanceof com.github.aeddddd.ae2enhanced.multiblock.MultiblockMeInterfaceBlockEntity me) {
                IManagedGridNode node = me.getMainNode();
                if (node.isReady()) {
                    return node;
                }
            }
        }
        return null;
    }

    private static List<GenericStack> collectInputs(IPatternDetails pattern) {
        List<GenericStack> result = new ArrayList<>();
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            GenericStack[] possible = input.getPossibleInputs();
            if (possible.length > 0) {
                result.add(possible[0]);
            }
        }
        return result;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    // ---- NBT ----

    @Override
    public void loadTag(CompoundTag data) {
        super.loadTag(data);
        patternInventory.readFromNBT(data, "patterns");
    }

    @Override
    public void saveAdditional(CompoundTag data) {
        super.saveAdditional(data);
        patternInventory.writeToNBT(data, "patterns");
    }
}
