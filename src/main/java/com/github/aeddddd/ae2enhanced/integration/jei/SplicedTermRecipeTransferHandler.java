package com.github.aeddddd.ae2enhanced.integration.jei;

import com.github.aeddddd.ae2enhanced.container.ContainerSplicedTerm;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 可拼接终端（GUI 15）的 JEI 配方转移处理器。
 *
 * <p><b>为什么必须单独一份</b>：JEI/HEI 的转移处理器按<b>打开容器的精确类</b>查找
 * （{@code mezz.jei.recipes.RecipeRegistry.getRecipeTransferHandler} 用的是
 * {@code container.getClass()} 查 {@code ImmutableTable<Class, String, IRecipeTransferHandler>}），
 * 不会沿继承链回退。{@code ContainerSplicedTerm} 虽然继承自 {@code ContainerOmniTerm}，
 * 但只注册 {@code ContainerOmniTerm} 的处理器在拼接终端里查不到 → {@code [+]}
 * 按钮变成"不支持转移"。逻辑与旧终端完全共用
 * {@link OmniTermRecipeTransferHandler#transfer(IRecipeLayout, boolean, boolean)}，
 * 服务端也共用同一个 {@code PacketLoadOmniRecipe}（其 Handler 用
 * {@code instanceof ContainerOmniTerm} 判断，子类自然通过）。</p>
 */
public class SplicedTermRecipeTransferHandler implements IRecipeTransferHandler<ContainerSplicedTerm> {

    @Override
    public Class<ContainerSplicedTerm> getContainerClass() {
        return ContainerSplicedTerm.class;
    }

    @Override
    public IRecipeTransferError transferRecipe(ContainerSplicedTerm container, IRecipeLayout recipeLayout,
            EntityPlayer player, boolean maxTransfer, boolean doTransfer) {
        return OmniTermRecipeTransferHandler.transfer(recipeLayout, maxTransfer, doTransfer);
    }
}
