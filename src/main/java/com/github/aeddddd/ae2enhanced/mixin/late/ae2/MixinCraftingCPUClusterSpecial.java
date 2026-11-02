package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import java.util.Map;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingJob;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.me.cache.CraftingGridCache;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuAdapter;
import com.github.aeddddd.ae2enhanced.crafting.CraftingCpuExecutionContext;
import com.github.aeddddd.ae2enhanced.crafting.SpecialCraftingCpuService;
import com.github.aeddddd.ae2enhanced.crafting.access.IComputationCoreAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialClusterMarkAccess;
import com.github.aeddddd.ae2enhanced.mixin.bridge.ISpecialCpuAccess;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.ITaskProgressAccessor;
import com.github.aeddddd.ae2enhanced.specialcrafting.SelfRefOutputGate;
import com.github.aeddddd.ae2enhanced.tile.TileComputationCore;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 特殊 CPU 注入仅负责桥接原生成员和调用普通 Java 管线。 */
@Mixin(value = CraftingCPUCluster.class, remap = false, priority = 1000)
public abstract class MixinCraftingCPUClusterSpecial implements ISpecialCpuAccess, ISpecialClusterMarkAccess {

    @Unique
    private final CraftingCpuExecutionContext ae2e$context = new CraftingCpuExecutionContext();

    @Shadow
    private Map<ICraftingPatternDetails, Object> tasks;
    @Shadow
    private IItemList<IAEItemStack> waitingFor;
    @Shadow
    private IAEItemStack finalOutput;
    @Shadow
    private boolean isComplete;
    @Shadow
    private ICraftingLink myLastLink;
    @Shadow
    private MachineSource machineSrc;
    @Shadow
    private int remainingOperations;

    @Shadow
    public abstract IMEInventory<IAEItemStack> getInventory();
    @Shadow
    private void postChange(IAEItemStack diff, IActionSource src) { }
    @Shadow
    private void postCraftingStatusChange(IAEItemStack diff) { }
    @Shadow
    private void updateRemainingItemCount(IAEItemStack is) { }
    @Shadow
    private void updateCPU() { }
    @Shadow
    private void completeJob() { }
    @Shadow
    private net.minecraft.world.World getWorld() {
        return null;
    }

    @Override
    public CraftingCpuExecutionContext ae2e$context() {
        return ae2e$context;
    }

    @Override
    public boolean ae2e$isSpecialMarked() {
        return ae2e$context.specialMarked();
    }

    @Override
    public void ae2e$setSpecialMarked(boolean marked) {
        ae2e$context.specialMarked(marked);
    }

    @Override
    public Map<ICraftingPatternDetails, Object> ae2e$tasks() {
        return tasks;
    }

    @Override
    public IMEInventory<IAEItemStack> ae2e$inventory() {
        return getInventory();
    }

    @Override
    public IActionSource ae2e$machineSource() {
        return machineSrc;
    }

    @Override
    public int ae2e$remainingOperations() {
        return remainingOperations;
    }

    @Override
    public void ae2e$setRemainingOperations(int value) {
        remainingOperations = value;
    }

    @Override
    public long ae2e$taskValue(Object progress) {
        return ((ITaskProgressAccessor) progress).ae2e$getValue();
    }

    @Override
    public void ae2e$setTaskValue(Object progress, long value) {
        ((ITaskProgressAccessor) progress).ae2e$setValue(value);
    }

    @Override
    public Map<ICraftingPatternDetails, Long> ae2e$remainingSnapshot() {
        return ae2e$context.remainingSnapshot();
    }

    @Override
    public IItemList<IAEItemStack> ae2e$waitingFor() {
        return waitingFor;
    }

    @Override
    public IAEItemStack ae2e$finalOutput() {
        return finalOutput;
    }

    @Override
    public void ae2e$clearFinalOutput() {
        finalOutput = null;
    }

    @Override
    public boolean ae2e$isComplete() {
        return isComplete;
    }

    @Override
    public net.minecraft.world.World ae2e$world() {
        return getWorld();
    }

    @Override
    public ICraftingLink ae2e$myLastLink() {
        return myLastLink;
    }

    @Override
    public void ae2e$postChange(IAEItemStack diff, IActionSource src) {
        postChange(diff, src);
    }

    @Override
    public void ae2e$postCraftingStatusChange(IAEItemStack diff) {
        postCraftingStatusChange(diff);
    }

    @Override
    public void ae2e$updateRemainingItemCount(IAEItemStack is) {
        updateRemainingItemCount(is);
    }

    @Override
    public void ae2e$markDirtyCluster() {
        TileComputationCore core = (TileComputationCore)
                ((IComputationCoreAccess) this).ae2enhanced$getComputationCore();
        if (core != null) {
            core.markDirty();
        }
    }

    @Override
    public void ae2e$updateCPU() {
        updateCPU();
    }

    @Override
    public void ae2e$completeJob() {
        completeJob();
    }

    private CraftingCpuAdapter ae2enhanced$specialCpu() {
        return new CraftingCpuAdapter(this, ae2e$context);
    }

    @Inject(method = "submitJob", at = @At("RETURN"))
    private void ae2enhanced$onSpecialJobSubmitted(IGrid grid, ICraftingJob job, IActionSource src,
            ICraftingRequester requestingMachine, CallbackInfoReturnable<ICraftingLink> cir) {
        SpecialCraftingCpuService.submitted(ae2enhanced$specialCpu(), job, cir.getReturnValue());
    }

    @Inject(method = "completeJob", at = @At("HEAD"))
    private void ae2enhanced$onCompleteJob(CallbackInfo ci) {
        SpecialCraftingCpuService.finished(ae2enhanced$specialCpu());
    }

    @Inject(method = "cancel", at = @At("HEAD"))
    private void ae2enhanced$onCancel(CallbackInfo ci) {
        SpecialCraftingCpuService.finished(ae2enhanced$specialCpu());
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"))
    private void ae2enhanced$writeSpecialState(NBTTagCompound data, CallbackInfo ci) {
        SpecialCraftingCpuService.writeNbt(ae2enhanced$specialCpu(), data);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"))
    private void ae2enhanced$readSpecialState(NBTTagCompound data, CallbackInfo ci) {
        SpecialCraftingCpuService.readNbt(ae2enhanced$specialCpu(), data);
    }

    @Inject(method = "injectItems", at = @At("HEAD"), cancellable = true)
    private void ae2enhanced$gateSelfConsumingOutput(IAEItemStack input, appeng.api.config.Actionable type,
            IActionSource src, CallbackInfoReturnable<IAEItemStack> cir) {
        com.github.aeddddd.ae2enhanced.craftingapi.CraftingGateResult result =
                SpecialCraftingCpuService.inject(ae2enhanced$specialCpu(), input, type, src);
        if (result.handled) {
            cir.setReturnValue(result.leftover);
        }
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), order = 900)
    private void ae2enhanced$snapshotRemaining(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        ae2e$context.beginExecute();
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"), order = 900)
    private void ae2enhanced$dropRemainingSnapshot(IEnergyGrid energy, CraftingGridCache cache, CallbackInfo ci) {
        ae2e$context.endExecute();
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;canCraft(Lappeng/api/networking/crafting/ICraftingPatternDetails;[Lappeng/api/storage/data/IAEItemStack;)Z"))
    private boolean ae2enhanced$vetoPushOverQuota(CraftingCPUCluster self, ICraftingPatternDetails details,
            IAEItemStack[] condensedInputs, Operation<Boolean> original) {
        return SpecialCraftingCpuService.canCraft(ae2enhanced$specialCpu(), details,
                () -> original.call(self, details, condensedInputs));
    }
}
