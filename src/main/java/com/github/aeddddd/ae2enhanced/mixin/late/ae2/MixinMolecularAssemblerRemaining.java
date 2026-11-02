package com.github.aeddddd.ae2enhanced.mixin.late.ae2;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.tile.crafting.TileMolecularAssembler;
import appeng.util.Platform;
import com.github.aeddddd.ae2enhanced.crafting.AssemblerRemainingService;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 分子装配室剩余物注入壳；与 CPU waitingFor 使用同一配方口径。 */
@Mixin(value = TileMolecularAssembler.class, remap = false)
public abstract class MixinMolecularAssemblerRemaining {

    @Shadow
    private ICraftingPatternDetails myPlan;
    @Shadow
    private InventoryCrafting craftingInv;

    @Unique
    private final AssemblerRemainingService.State ae2e$remainingState = new AssemblerRemainingService.State();

    @Redirect(method = "tickingRequest", at = @At(value = "INVOKE",
            target = "Lappeng/util/Platform;getContainerItem(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;"))
    private ItemStack ae2enhanced$returnRecipeRemaining(ItemStack stack) {
        return AssemblerRemainingService.remaining(ae2e$remainingState, myPlan, craftingInv, stack);
    }
}
