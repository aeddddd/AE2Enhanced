package com.github.aeddddd.ae2enhanced.integration.jei;

import appeng.api.storage.data.IAEItemStack;
import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.gui.jei.GhostIngredientTarget;
import com.github.aeddddd.ae2enhanced.container.ContainerSmartPatternInterface;
import com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternFill;
import com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
import mezz.jei.api.gui.IGuiIngredient;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 智能样板接口的 JEI 一键转移 Handler.
 *
 * <p>点击 JEI 配方界面的 [+] 时,将配方的物品/流体输入输出按槽位顺序
 * 转换为 AE 物品(含流体/气体/源质假物品,与 ghost 拖拽一致),
 * 通过 {@link PacketSmartPatternFill} 整体覆盖当前锁定的配方.</p>
 *
 * <p>仅在有配方被锁定(Shift+左键)时可用.</p>
 */
public class SmartPatternRecipeTransferHandler implements IRecipeTransferHandler<ContainerSmartPatternInterface> {

    private final IRecipeTransferHandlerHelper helper;

    public SmartPatternRecipeTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<ContainerSmartPatternInterface> getContainerClass() {
        return ContainerSmartPatternInterface.class;
    }

    @Override
    public IRecipeTransferError transferRecipe(ContainerSmartPatternInterface container, IRecipeLayout recipeLayout,
                                               EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        TileSmartPatternInterface tile = container.getTile();
        if (tile == null || tile.getLockedRecipeIndex() < 0) {
            return helper.createUserErrorWithTooltip(
                    I18n.format("gui.ae2enhanced.smart_pattern_interface.transfer_no_lock"));
        }

        List<IAEItemStack> inputs = new ArrayList<>();
        List<IAEItemStack> outputs = new ArrayList<>();
        collect(recipeLayout.getItemStacks().getGuiIngredients(), inputs, outputs);
        collect(recipeLayout.getFluidStacks().getGuiIngredients(), inputs, outputs);

        if (inputs.isEmpty() && outputs.isEmpty()) {
            return null;
        }
        if (!doTransfer) {
            return null;
        }

        AE2Enhanced.network.sendToServer(new PacketSmartPatternFill(tile.getPos(), inputs, outputs));
        return null;
    }

    /**
     * 按槽位顺序收集一组 JEI ingredients,转换为 AE 物品后追加到输入/输出列表.
     */
    private static <T> void collect(Map<Integer, ? extends IGuiIngredient<T>> guiIngredients,
                                    List<IAEItemStack> inputs, List<IAEItemStack> outputs) {
        List<? extends Map.Entry<Integer, ? extends IGuiIngredient<T>>> sorted = new ArrayList<>(guiIngredients.entrySet());
        sorted.sort(Comparator.comparingInt(Map.Entry::getKey));
        for (Map.Entry<Integer, ? extends IGuiIngredient<T>> entry : sorted) {
            IGuiIngredient<T> ing = entry.getValue();
            T ingredient = ing.getDisplayedIngredient();
            if (ingredient == null) {
                List<T> all = ing.getAllIngredients();
                ingredient = all.isEmpty() ? null : all.get(0);
            }
            if (ingredient == null) continue;
            IAEItemStack aeStack = GhostIngredientTarget.resolveIngredient(ingredient);
            if (aeStack == null) continue;
            if (ing.isInput()) {
                inputs.add(aeStack);
            } else {
                outputs.add(aeStack);
            }
        }
    }
}
