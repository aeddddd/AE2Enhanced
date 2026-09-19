package com.github.aeddddd.ae2enhanced.mixin.late.accessor;

import appeng.client.gui.implementations.GuiMEMonitorable;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.client.me.ItemRepo;
import appeng.container.implementations.ContainerMEMonitorable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * GuiMEMonitorable 私有成员访问接口, 仅客户端加载.
 * 替代 GuiOmniTerm / JEISearchKeyHandler / MixinGuiMEMonitorableKeyHandler 中的反射.
 */
@Mixin(value = GuiMEMonitorable.class, remap = false)
public interface IGuiMEMonitorableAccessor {

    @Accessor("repo")
    ItemRepo ae2e$getRepo();

    @Accessor("repo")
    void ae2e$setRepo(ItemRepo repo);

    @Accessor("viewCell")
    boolean ae2e$getViewCell();

    @Accessor("searchField")
    MEGuiTextField ae2e$getSearchField();

    @Accessor("searchField")
    void ae2e$setSearchField(MEGuiTextField field);

    @Accessor("craftingStatusBtn")
    GuiTabButton ae2e$getCraftingStatusBtn();

    @Accessor("craftingStatusBtn")
    void ae2e$setCraftingStatusBtn(GuiTabButton btn);

    @Accessor("rows")
    void ae2e$setRows(int rows);

    @Accessor("perRow")
    void ae2e$setPerRow(int perRow);

    @Accessor("isAutoFocus")
    boolean ae2e$isAutoFocus();

    @Accessor("monitorableContainer")
    ContainerMEMonitorable ae2e$getMonitorableContainer();

    @Accessor("currentMouseX")
    int ae2e$getCurrentMouseX();

    @Accessor("currentMouseY")
    int ae2e$getCurrentMouseY();

    @Accessor("memoryText")
    static void ae2e$setMemoryText(String text) {
        throw new UnsupportedOperationException();
    }

    @Invoker("setScrollBar")
    void ae2e$invokeSetScrollBar();
}
