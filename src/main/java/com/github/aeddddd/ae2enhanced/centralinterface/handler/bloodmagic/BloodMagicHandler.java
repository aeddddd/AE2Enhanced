package com.github.aeddddd.ae2enhanced.centralinterface.handler.bloodmagic;

import com.github.aeddddd.ae2enhanced.centralinterface.TargetSession;

import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.fluids.util.AEFluidStack;
import appeng.util.item.AEItemStack;
import com.github.aeddddd.ae2enhanced.centralinterface.IRemoteHandler;
import com.github.aeddddd.ae2enhanced.centralinterface.IVirtualBatchCraftingHandler;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import WayofTime.bloodmagic.api.impl.BloodMagicAPI;
import WayofTime.bloodmagic.api.impl.recipe.RecipeAlchemyTable;
import WayofTime.bloodmagic.api.impl.recipe.RecipeBloodAltar;
import WayofTime.bloodmagic.tile.TileAlchemyTable;
import WayofTime.bloodmagic.tile.TileAltar;
import WayofTime.bloodmagic.tile.TileSoulForge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Blood Magic 远程处理器.
 *
 * 支持设备：
 * <ul>
 *   <li>炼金术桌 (bloodmagic:alchemy_table) — 6 输入槽均分,每槽 1 个,收集输出槽产物 + 输入槽残余</li>
 *   <li>狱火锻炉 (bloodmagic:soul_forge) — 4 输入槽均分,每槽 1 个,收集输出槽产物 + 输入槽残余</li>
 *   <li>祭坛 (bloodmagic:altar) — 单槽 push + startCycle 启动,回收槽位 0 产物</li>
 * </ul>
 */
public class BloodMagicHandler implements IRemoteHandler, IVirtualBatchCraftingHandler {

    /** 推料保护期：防止推料后 burnTime/progress 尚未增加就被判定为 idle */
    private static final int PUSH_IDLE_GRACE_TICKS = 4;

    @Override
    public boolean canHandle(String blockId) {
        return "bloodmagic:alchemy_table".equals(blockId)
                || "bloodmagic:soul_forge".equals(blockId)
                || "bloodmagic:altar".equals(blockId);
    }

    @Override
    public boolean isValidTarget(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return !((TileAlchemyTable) te).isSlave();
        }
        return te instanceof TileSoulForge || te instanceof TileAltar;
    }

    @Override
    public boolean canStart(World world, BlockPos pos, InventoryCrafting ingredients, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return canStartAlchemyTable((TileAlchemyTable) te, ingredients);
        } else if (te instanceof TileSoulForge) {
            return canStartSoulForge((TileSoulForge) te, ingredients);
        } else if (te instanceof TileAltar) {
            return canStartAltar((TileAltar) te, ingredients);
        }
        return false;
    }

    @Override
    public boolean pushMaterials(World world, BlockPos pos, InventoryCrafting ingredients, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        boolean success = false;
        if (te instanceof TileAlchemyTable) {
            success = pushMaterialsAlchemyTable((TileAlchemyTable) te, ingredients);
        } else if (te instanceof TileSoulForge) {
            success = pushMaterialsSoulForge((TileSoulForge) te, ingredients);
        } else if (te instanceof TileAltar) {
            success = pushMaterialsAltar((TileAltar) te, ingredients);
        }
        if (success && session != null) {
            session.setPushTick(world.getTotalWorldTime());
        }
        return success;
    }

    @Override
    public boolean startProcess(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAltar) {
            ((TileAltar) te).startCycle();
            return true;
        }
        // 炼金术桌和狱火锻炉 tick 自动处理,无需显式启动
        return true;
    }

    @Override
    public List<ItemStack> collectProducts(World world, BlockPos pos, IAEItemStack[] expectedOutputs, List<ItemStack> inputs, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return collectProductsAlchemyTable((TileAlchemyTable) te);
        } else if (te instanceof TileSoulForge) {
            return collectProductsSoulForge((TileSoulForge) te);
        } else if (te instanceof TileAltar) {
            return collectProductsAltar((TileAltar) te);
        }
        return new ArrayList<>();
    }

    @Override
    public boolean isIdle(World world, BlockPos pos, List<ItemStack> inputs, TargetSession session) {
        if (session != null && !session.isPushGraceElapsed(world.getTotalWorldTime(), PUSH_IDLE_GRACE_TICKS)) {
            return false;
        }
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return ((TileAlchemyTable) te).getBurnTime() == 0;
        } else if (te instanceof TileSoulForge) {
            return ((TileSoulForge) te).burnTime == 0;
        } else if (te instanceof TileAltar) {
            TileAltar altar = (TileAltar) te;
            return !altar.isActive() && altar.getProgress() == 0;
        }
        return true;
    }

    // ---- IVirtualCraftingHandler / IVirtualBatchCraftingHandler ----

    @Override
    public boolean canCraftVirtually(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return canCraftVirtuallyAlchemyTable(ingredients, outputs);
        } else if (te instanceof TileAltar) {
            return canCraftVirtuallyAltar(ingredients, outputs);
        }
        // Soul Forge 暂不接入（缺少 Demon Will AE 通道）
        return false;
    }

    public List<ItemStack> virtualCraft(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, IActionSource source) {
        return virtualCraftBatch(world, pos, ingredients, outputs, 1, source);
    }

    @Override
    public List<EnumParticleTypes> getVirtualCraftingParticles(World world, BlockPos pos) {
        return Arrays.asList(
                EnumParticleTypes.SPELL_WITCH,
                EnumParticleTypes.PORTAL,
                EnumParticleTypes.SMOKE_LARGE,
                EnumParticleTypes.END_ROD
        );
    }

    @Override
    public List<IAEStack> getVirtualCost(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return getVirtualCostAlchemyTable(ingredients, outputs, count);
        } else if (te instanceof TileAltar) {
            return getVirtualCostAltar(ingredients, outputs, count);
        }
        return new ArrayList<>();
    }

    @Override
    public List<ItemStack> virtualCraftBatch(World world, BlockPos pos, InventoryCrafting ingredients, IAEItemStack[] outputs, long count, IActionSource source) {
        List<ItemStack> products = new ArrayList<>();
        if (!canCraftVirtually(world, pos, ingredients, outputs)) return products;
        return scaleOutputsByCount(outputs, count);
    }

    // ---- 批量虚拟合成辅助 ----

    private boolean canCraftVirtuallyAlchemyTable(InventoryCrafting ingredients, IAEItemStack[] outputs) {
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        RecipeAlchemyTable recipe = findAlchemyRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return false;
        return matchIngredients(recipe.getInput(), collectNonEmpty(ingredients));
    }

    private List<IAEStack> getVirtualCostAlchemyTable(InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;
        RecipeAlchemyTable recipe = findAlchemyRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return costs;

        List<ItemStack> available = collectNonEmpty(ingredients);
        for (Ingredient ing : recipe.getInput()) {
            if (ing == null || ing == Ingredient.EMPTY) continue;
            for (int i = 0; i < available.size(); i++) {
                if (ing.apply(available.get(i))) {
                    IAEItemStack cost = AEItemStack.fromItemStack(available.remove(i).copy());
                    cost.setStackSize(count);
                    costs.add(cost);
                    break;
                }
            }
        }

        IAEStack lp = createLPCost(recipe.getSyphon(), count);
        if (lp == null) {
            return null;
        }
        costs.add(lp);
        return costs;
    }

    private boolean canCraftVirtuallyAltar(InventoryCrafting ingredients, IAEItemStack[] outputs) {
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return false;
        RecipeBloodAltar recipe = findAltarRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return false;
        Ingredient input = recipe.getInput();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            if (input.apply(ingredients.getStackInSlot(i))) return true;
        }
        return false;
    }

    private List<IAEStack> getVirtualCostAltar(InventoryCrafting ingredients, IAEItemStack[] outputs, long count) {
        List<IAEStack> costs = new ArrayList<>();
        if (outputs == null || outputs.length == 0 || outputs[0] == null) return costs;
        RecipeBloodAltar recipe = findAltarRecipeByOutput(outputs[0].createItemStack());
        if (recipe == null) return costs;

        Ingredient input = recipe.getInput();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty() && input.apply(stack)) {
                IAEItemStack cost = AEItemStack.fromItemStack(stack.copy());
                cost.setStackSize(count);
                costs.add(cost);
                break;
            }
        }

        IAEStack lp = createLPCost(recipe.getSyphon(), count);
        if (lp == null) {
            return null;
        }
        costs.add(lp);
        return costs;
    }

    private RecipeAlchemyTable findAlchemyRecipeByOutput(ItemStack output) {
        if (output.isEmpty()) return null;
        for (RecipeAlchemyTable recipe : BloodMagicAPI.INSTANCE.getRecipeRegistrar().getAlchemyRecipes()) {
            ItemStack recipeOutput = recipe.getOutput();
            if (!recipeOutput.isEmpty()
                    && recipeOutput.getItem() == output.getItem()
                    && recipeOutput.getMetadata() == output.getMetadata()) {
                return recipe;
            }
        }
        return null;
    }

    private RecipeBloodAltar findAltarRecipeByOutput(ItemStack output) {
        if (output.isEmpty()) return null;
        for (RecipeBloodAltar recipe : BloodMagicAPI.INSTANCE.getRecipeRegistrar().getAltarRecipes()) {
            ItemStack recipeOutput = recipe.getOutput();
            if (!recipeOutput.isEmpty()
                    && recipeOutput.getItem() == output.getItem()
                    && recipeOutput.getMetadata() == output.getMetadata()) {
                return recipe;
            }
        }
        return null;
    }

    private IAEStack createLPCost(int syphon, long count) {
        Fluid fluid = FluidRegistry.getFluid("lifeessence");
        if (fluid == null) {
            // 部分魔改版使用不同注册名，尝试反射获取
            try {
                Class<?> blockLifeEssence = Class.forName("WayofTime.bloodmagic.block.BlockLifeEssence");
                java.lang.reflect.Method getLifeEssence = blockLifeEssence.getMethod("getLifeEssence");
                fluid = (Fluid) getLifeEssence.invoke(null);
            } catch (Exception ignored) {
            }
        }
        if (fluid == null) return null;
        return AEFluidStack.fromFluidStack(new FluidStack(fluid, (int) ((long) syphon * count)));
    }

    private List<ItemStack> collectNonEmpty(InventoryCrafting ingredients) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) list.add(stack.copy());
        }
        return list;
    }

    private boolean matchIngredients(List<Ingredient> required, List<ItemStack> available) {
        for (Ingredient ing : required) {
            if (ing == null || ing == Ingredient.EMPTY) continue;
            boolean found = false;
            for (int i = 0; i < available.size(); i++) {
                if (ing.apply(available.get(i))) {
                    available.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return available.isEmpty();
    }

    // ==================== Helpers ====================

    /**
     * 从 InventoryCrafting 中收集所有非空物品,每个只保留 1 个(均分策略).
     */
    private List<ItemStack> collectSingles(InventoryCrafting ingredients) {
        List<ItemStack> materials = new ArrayList<>();
        for (int i = 0; i < ingredients.getSizeInventory(); i++) {
            ItemStack stack = ingredients.getStackInSlot(i);
            if (!stack.isEmpty()) {
                ItemStack single = stack.copy();
                single.setCount(1);
                materials.add(single);
            }
        }
        return materials;
    }

    // ==================== Alchemy Table ====================

    private boolean canStartAlchemyTable(TileAlchemyTable table, InventoryCrafting ingredients) {
        if (table.isSlave()) return false;

        List<ItemStack> materials = collectSingles(ingredients);
        if (materials.isEmpty()) return false;
        if (materials.size() > 6) return false;

        IItemHandler inputHandler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
        if (inputHandler == null) return false;

        // 检查是否有足够空输入槽
        int emptySlots = 0;
        for (int i = 0; i < inputHandler.getSlots(); i++) {
            if (table.isInputSlotAccessible(i) && inputHandler.getStackInSlot(i).isEmpty()) {
                emptySlots++;
            }
        }
        if (emptySlots < materials.size()) return false;

        // 检查输出槽
        IItemHandler outputHandler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
        if (outputHandler == null) return false;
        return outputHandler.getStackInSlot(0).isEmpty();
    }

    private boolean pushMaterialsAlchemyTable(TileAlchemyTable table, InventoryCrafting ingredients) {
        List<ItemStack> materials = collectSingles(ingredients);
        IItemHandler handler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
        if (handler == null) return false;

        int slot = 0;
        for (ItemStack material : materials) {
            boolean placed = false;
            while (slot < handler.getSlots()) {
                if (table.isInputSlotAccessible(slot) && handler.getStackInSlot(slot).isEmpty()) {
                    ItemStack remainder = handler.insertItem(slot, material, false);
                    if (remainder.isEmpty()) {
                        placed = true;
                        slot++;
                        break;
                    }
                }
                slot++;
            }
            if (!placed) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> collectProductsAlchemyTable(TileAlchemyTable table) {
        List<ItemStack> collected = new ArrayList<>();

        // 1. 收集输出槽产物(DOWN 面,槽位 0 对应绝对槽位 8)
        IItemHandler outputHandler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.DOWN);
        if (outputHandler != null) {
            ItemStack output = outputHandler.extractItem(0, 64, false);
            if (!output.isEmpty()) {
                collected.add(output);
            }
        }

        // 2. 收集输入槽残余(NORTH 面,槽位 0-5 对应绝对槽位 0-5)
        IItemHandler inputHandler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, EnumFacing.NORTH);
        if (inputHandler != null) {
            for (int i = 0; i < inputHandler.getSlots(); i++) {
                ItemStack stack = inputHandler.extractItem(i, 64, false);
                if (!stack.isEmpty()) {
                    collected.add(stack);
                }
            }
        }

        return collected;
    }

    // ==================== Soul Forge ====================

    private boolean canStartSoulForge(TileSoulForge forge, InventoryCrafting ingredients) {
        List<ItemStack> materials = collectSingles(ingredients);
        if (materials.isEmpty()) return false;
        if (materials.size() > 4) return false;

        IItemHandler handler = forge.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return false;

        int emptySlots = 0;
        for (int i = 0; i < 4; i++) {
            if (handler.getStackInSlot(i).isEmpty()) {
                emptySlots++;
            }
        }
        if (emptySlots < materials.size()) return false;

        return handler.getStackInSlot(5).isEmpty();
    }

    private boolean pushMaterialsSoulForge(TileSoulForge forge, InventoryCrafting ingredients) {
        List<ItemStack> materials = collectSingles(ingredients);
        IItemHandler handler = forge.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return false;

        int slot = 0;
        for (ItemStack material : materials) {
            boolean placed = false;
            while (slot < 4) {
                if (handler.getStackInSlot(slot).isEmpty()) {
                    ItemStack remainder = handler.insertItem(slot, material, false);
                    if (remainder.isEmpty()) {
                        placed = true;
                        slot++;
                        break;
                    }
                }
                slot++;
            }
            if (!placed) {
                return false;
            }
        }
        return true;
    }

    private List<ItemStack> collectProductsSoulForge(TileSoulForge forge) {
        List<ItemStack> collected = new ArrayList<>();
        IItemHandler handler = forge.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return collected;

        ItemStack output = handler.extractItem(5, 64, false);
        if (!output.isEmpty()) {
            collected.add(output);
        }

        for (int i = 0; i < 4; i++) {
            ItemStack stack = handler.extractItem(i, 64, false);
            if (!stack.isEmpty()) {
                collected.add(stack);
            }
        }

        return collected;
    }

    // ==================== Altar ====================

    private boolean canStartAltar(TileAltar altar, InventoryCrafting ingredients) {
        List<ItemStack> materials = collectSingles(ingredients);
        if (materials.size() != 1) return false;

        IItemHandler handler = altar.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return false;
        return handler.getStackInSlot(0).isEmpty();
    }

    private boolean pushMaterialsAltar(TileAltar altar, InventoryCrafting ingredients) {
        List<ItemStack> materials = collectSingles(ingredients);
        if (materials.isEmpty()) return false;

        IItemHandler handler = altar.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return false;

        ItemStack remainder = handler.insertItem(0, materials.get(0), false);
        return remainder.isEmpty();
    }

    private List<ItemStack> collectProductsAltar(TileAltar altar) {
        List<ItemStack> collected = new ArrayList<>();
        IItemHandler handler = altar.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler == null) return collected;

        ItemStack stack = handler.extractItem(0, 64, false);
        if (!stack.isEmpty()) {
            collected.add(stack);
        }
        return collected;
    }

    @Override
    public List<ItemStack> revertMaterials(World world, BlockPos pos, IActionSource source, TargetSession session) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileAlchemyTable) {
            return revertMaterialsAlchemyTable((TileAlchemyTable) te);
        } else if (te instanceof TileSoulForge) {
            return revertMaterialsSoulForge((TileSoulForge) te);
        } else if (te instanceof TileAltar) {
            return revertMaterialsAltar((TileAltar) te);
        }
        return java.util.Collections.emptyList();
    }

    private List<ItemStack> revertMaterialsAlchemyTable(TileAlchemyTable table) {
        List<ItemStack> reverted = new ArrayList<>();
        IItemHandler handler = table.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.extractItem(i, 64, false);
                if (!stack.isEmpty()) reverted.add(stack);
            }
        }
        return reverted;
    }

    private List<ItemStack> revertMaterialsSoulForge(TileSoulForge forge) {
        List<ItemStack> reverted = new ArrayList<>();
        IItemHandler handler = forge.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler != null) {
            for (int i = 0; i < handler.getSlots(); i++) {
                ItemStack stack = handler.extractItem(i, 64, false);
                if (!stack.isEmpty()) reverted.add(stack);
            }
        }
        return reverted;
    }

    private List<ItemStack> revertMaterialsAltar(TileAltar altar) {
        List<ItemStack> reverted = new ArrayList<>();
        IItemHandler handler = altar.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (handler != null) {
            ItemStack stack = handler.extractItem(0, 64, false);
            if (!stack.isEmpty()) reverted.add(stack);
        }
        return reverted;
    }
}
