package com.github.aeddddd.ae2enhanced.mixin.late.terminal;

import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.client.gui.implementations.GuiCraftingStatus;
import com.github.aeddddd.ae2enhanced.client.gui.planview.CraftingDurationTracker;
import com.github.aeddddd.ae2enhanced.client.gui.planview.IPlanViewHost;
import com.github.aeddddd.ae2enhanced.client.gui.planview.PlanViewHelper;
import com.github.aeddddd.ae2enhanced.mixin.late.accessor.IGuiScreenAccessor;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

/**
 * Crafting CPU 状态界面显示增强.
 * <ul>
 * <li>混合排序: 进行中组置顶, 组内按指标降序; 排序模式可由玩家点击按钮切换并持久化
 * (进行中优先 / 按持续时间 / 按数量), 见 {@link PlanViewHelper}.</li>
 * <li>搜索框: 标题栏右侧, 按物品显示名过滤列表.</li>
 * <li>进行中子项持续时间: 格子左下角常显(0.5 缩放), 悬停 tooltip 追加已用时行.
 * 计时跨界面关闭/重开延续(静态共享跟踪器), 换世界/换服时重置.
 * 总已持续时间(替换原生 ETA)由 MixinContainerCraftingCPUElapsed 在服务端同步.</li>
 * </ul>
 */
@Mixin(value = GuiCraftingCPU.class, remap = false)
public abstract class MixinGuiCraftingCPU implements IPlanViewHost {

    @Shadow
    private List<IAEItemStack> visual;

    @Shadow
    private IItemList<IAEItemStack> storage;

    @Shadow
    private IItemList<IAEItemStack> active;

    @Shadow
    private IItemList<IAEItemStack> pending;

    @Shadow
    private int tooltip;

    @Unique
    private GuiButton ae2enhanced$sortButton;

    @Unique
    private GuiTextField ae2enhanced$searchField;

    @Unique
    private String ae2enhanced$searchText = "";

    /**
     * 子项持续时间跟踪器: 静态共享, 跨 GUI 会话持久(关闭/重开界面不清零),
     * 切换世界/服务器时由 {@link CraftingDurationTracker#update} 自动重置.
     */
    @Unique
    private static final CraftingDurationTracker ae2enhanced$durations = new CraftingDurationTracker();

    @Unique
    private final PlanViewHelper.ViewStats ae2enhanced$stats = new PlanViewHelper.ViewStats();

    // ==================== 列表重建(排序 + 搜索过滤) ====================

    @Inject(method = "postUpdate", at = @At("TAIL"))
    private void ae2enhanced$onPostUpdate(List<IAEItemStack> list, byte ref, CallbackInfo ci) {
        this.ae2enhanced$durations.update(this.active);
        ae2enhanced$refreshView();
    }

    @Unique
    private void ae2enhanced$refreshView() {
        PlanViewHelper.refreshCpu(this.visual, this.storage, this.active, this.pending,
                this.ae2enhanced$searchText, PlanViewHelper.cpuMode(), this.ae2enhanced$durations,
                this.ae2enhanced$stats);
        // 原生 setScrollBar 已在重建前执行, 用过滤后的尺寸重设范围(6 行视图)
        ((MixinAEBaseGuiAccessor) this).ae2enhanced$getMyScrollBar()
                .setRange(0, (this.visual.size() + 2) / 3 - 6, 1);
    }

    @Unique
    private boolean ae2enhanced$isStatusScreen() {
        return (Object) this instanceof GuiCraftingStatus;
    }

    // ==================== 排序按钮 + 搜索框 ====================

    @Inject(method = "func_73866_w_", at = @At("TAIL"))
    private void ae2enhanced$onInitGui(CallbackInfo ci) {
        GuiContainer self = (GuiContainer) (Object) this;
        int guiLeft = self.getGuiLeft();
        int guiTop = self.getGuiTop();
        FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
        if (ae2enhanced$isStatusScreen()) {
            // GuiCraftingStatus(CPU 选择界面)底行无空位: 排序按钮置于左侧 CPU 面板顶部,
            // 搜索框收窄避开右上角 tab 按钮(标题截断相应收窄)
            this.ae2enhanced$sortButton = new GuiButton(3092, guiLeft - 85, guiTop + 4, 76, 13, "");
            ((IGuiScreenAccessor) self).ae2enhanced$getButtonList().add(this.ae2enhanced$sortButton);
            this.ae2enhanced$searchField = new GuiTextField(3093, fr, guiLeft + 126, guiTop + 4, 82, 12);
        } else {
            this.ae2enhanced$sortButton = new GuiButton(3092, guiLeft + 6, guiTop + 159, 100, 20, "");
            ((IGuiScreenAccessor) self).ae2enhanced$getButtonList().add(this.ae2enhanced$sortButton);
            this.ae2enhanced$searchField = new GuiTextField(3093, fr, guiLeft + 154, guiTop + 4, 78, 12);
        }
        this.ae2enhanced$searchField.setMaxStringLength(64);
        this.ae2enhanced$searchField.setText(this.ae2enhanced$searchText);
        ae2enhanced$updateSortButtonText();
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"))
    private void ae2enhanced$onActionPerformed(GuiButton btn, CallbackInfo ci) {
        if (btn == this.ae2enhanced$sortButton) {
            PlanViewHelper.cycleCpuMode(GuiScreen.isShiftKeyDown());
            ae2enhanced$updateSortButtonText();
            ae2enhanced$refreshView();
        }
    }

    @Unique
    private void ae2enhanced$updateSortButtonText() {
        if (this.ae2enhanced$sortButton != null) {
            this.ae2enhanced$sortButton.displayString = I18n.format(
                    "gui.ae2enhanced.plan_sort.button", PlanViewHelper.cpuMode().label());
        }
    }

    @Inject(method = "func_73863_a", at = @At("TAIL"))
    private void ae2enhanced$onDrawScreen(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        ae2enhanced$updateSortButtonText();
        if (this.ae2enhanced$searchField != null) {
            this.ae2enhanced$searchField.drawTextBox();
        }
        GuiScreen self = (GuiScreen) (Object) this;
        if (this.ae2enhanced$sortButton != null && ae2enhanced$isHover(this.ae2enhanced$sortButton.x,
                this.ae2enhanced$sortButton.y, this.ae2enhanced$sortButton.width,
                this.ae2enhanced$sortButton.height, mouseX, mouseY)) {
            self.drawHoveringText(Collections.singletonList(
                    I18n.format("gui.ae2enhanced.plan_sort.hint")), mouseX, mouseY);
        } else if (this.ae2enhanced$searchField != null && this.ae2enhanced$searchText.isEmpty()
                && !this.ae2enhanced$searchField.isFocused()
                && ae2enhanced$isHover(this.ae2enhanced$searchField.x, this.ae2enhanced$searchField.y,
                        this.ae2enhanced$searchField.width, this.ae2enhanced$searchField.height, mouseX, mouseY)) {
            self.drawHoveringText(Collections.singletonList(
                    I18n.format("gui.ae2enhanced.plan_search.hint")), mouseX, mouseY);
        }
    }

    @Unique
    private boolean ae2enhanced$isHover(int x, int y, int w, int h, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ==================== IPlanViewHost(搜索框事件, 由钩子 mixin 转发) ====================

    @Override
    public void ae2enhanced$planMouseClicked(int mouseX, int mouseY, int button) {
        if (this.ae2enhanced$searchField != null) {
            this.ae2enhanced$searchField.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public boolean ae2enhanced$planKeyTyped(char typedChar, int keyCode) {
        if (this.ae2enhanced$searchField == null || !this.ae2enhanced$searchField.isFocused()) {
            return false;
        }
        if (keyCode == 1) {
            return false; // ESC 放行: 关闭界面
        }
        if (keyCode == 28 || keyCode == 156) {
            this.ae2enhanced$searchField.setFocused(false);
            return true;
        }
        if (this.ae2enhanced$searchField.textboxKeyTyped(typedChar, keyCode)) {
            this.ae2enhanced$searchText = this.ae2enhanced$searchField.getText();
            ae2enhanced$refreshView();
        }
        return true; // 聚焦时吞掉其余按键, 避免触发界面快捷键
    }

    @Override
    public void ae2enhanced$planUpdateScreen() {
        if (this.ae2enhanced$searchField != null) {
            this.ae2enhanced$searchField.updateCursorCounter();
        }
    }

    // ==================== 标题截断(为搜索框留位) ====================

    @WrapOperation(
        method = "drawFG",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/gui/FontRenderer;func_78276_b(Ljava/lang/String;III)I",
            ordinal = 0
        ),
        require = 0
    )
    private int ae2enhanced$truncateTitle(FontRenderer fr, String title, int x, int y, int color,
            Operation<Integer> original) {
        // CPU 选择界面右上角有 tab 按钮, 标题截断相应收窄
        return original.call(fr, PlanViewHelper.truncateTitle(title, ae2enhanced$isStatusScreen() ? 110 : 140),
                x, y, color);
    }

    // ==================== 汇总统计行 ====================

    @Inject(method = "drawFG", at = @At("TAIL"), require = 0)
    private void ae2enhanced$drawSummary(int offsetX, int offsetY, int mouseX, int mouseY,
            CallbackInfo ci) {
        try {
            if (this.ae2enhanced$stats.total <= 0) {
                return;
            }
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            String activeLine = I18n.format("gui.ae2enhanced.cpu.summary.active", this.ae2enhanced$stats.second);
            String queuedLine = I18n.format("gui.ae2enhanced.cpu.summary.queued", this.ae2enhanced$stats.third);
            if (ae2enhanced$isStatusScreen()) {
                // CPU 选择界面: 左侧 CPU 面板下方(纹理外区域, 用亮色文字)
                fr.drawString(activeLine, -83, 160, 0xAAAAAA);
                fr.drawString(queuedLine, -83, 170, 0xAAAAAA);
            } else {
                // 方块 GUI: 底部按钮行中央空位, 0.75 缩放
                GlStateManager.pushMatrix();
                GlStateManager.scale(0.75, 0.75, 0.75);
                fr.drawString(activeLine, 146, 214, 0x404040);
                fr.drawString(queuedLine, 146, 226, 0x404040);
                GlStateManager.popMatrix();
            }
        } catch (Throwable ignored) {
            // 渲染增强失败静默
        }
    }

    // ==================== 子项持续时间: tooltip 追加 ====================

    @WrapOperation(
        method = "drawFG",
        at = @At(
            value = "INVOKE",
            target = "Lappeng/client/gui/implementations/GuiCraftingCPU;drawTooltip(IILjava/lang/String;)V"
        ),
        require = 0
    )
    private void ae2enhanced$wrapStatusTooltip(GuiCraftingCPU self, int x, int y, String message,
            Operation<Void> original) {
        original.call(self, x, y, ae2enhanced$appendElapsedLine(message));
    }

    @Unique
    private String ae2enhanced$appendElapsedLine(String message) {
        try {
            IAEItemStack hovered = ae2enhanced$hoveredStack();
            if (hovered == null) {
                return message;
            }
            IAEItemStack activeStack = this.active.findPrecise(hovered);
            if (activeStack == null || activeStack.getStackSize() <= 0) {
                return message;
            }
            long elapsed = this.ae2enhanced$durations.elapsedMs(hovered);
            if (elapsed <= 0) {
                return message;
            }
            return message + '\n' + "\u00a77" + I18n.format(
                    "gui.ae2enhanced.cpu.item_elapsed", CraftingDurationTracker.format(elapsed));
        } catch (Throwable t) {
            return message;
        }
    }

    @Unique
    private IAEItemStack ae2enhanced$hoveredStack() {
        if (this.tooltip < 0 || this.visual == null) {
            return null;
        }
        int viewStart = ((MixinAEBaseGuiAccessor) this).ae2enhanced$getMyScrollBar().getCurrentScroll() * 3;
        int idx = viewStart + this.tooltip;
        if (idx < 0 || idx >= this.visual.size()) {
            return null;
        }
        return this.visual.get(idx);
    }

    // ==================== 子项持续时间: 格子左下角常显 ====================

    @Inject(method = "drawFG", at = @At("TAIL"), require = 0)
    private void ae2enhanced$drawElapsedOverlay(int offsetX, int offsetY, int mouseX, int mouseY,
            CallbackInfo ci) {
        try {
            if (this.visual == null || this.visual.isEmpty()) {
                return;
            }
            int viewStart = ((MixinAEBaseGuiAccessor) this).ae2enhanced$getMyScrollBar().getCurrentScroll() * 3;
            int viewEnd = viewStart + 18; // 3 列 × 6 行
            FontRenderer fr = Minecraft.getMinecraft().fontRenderer;
            int x = 0;
            int y = 0;
            for (int z = viewStart; z < Math.min(viewEnd, this.visual.size()); z++) {
                IAEItemStack refStack = this.visual.get(z);
                if (refStack != null) {
                    IAEItemStack activeStack = this.active.findPrecise(refStack);
                    if (activeStack != null && activeStack.getStackSize() > 0) {
                        long elapsed = this.ae2enhanced$durations.elapsedMs(refStack);
                        if (elapsed > 0) {
                            String str = CraftingDurationTracker.format(elapsed);
                            GlStateManager.pushMatrix();
                            GlStateManager.scale(0.5, 0.5, 0.5);
                            // 格子左下角(原生计数行下方), 中青色小字
                            fr.drawString(str, (x * 68 + 11) * 2, (y * 23 + 38) * 2, 0x2A9D8F);
                            GlStateManager.popMatrix();
                        }
                    }
                }
                if (++x > 2) {
                    ++y;
                    x = 0;
                }
            }
        } catch (Throwable ignored) {
            // 渲染增强失败静默
        }
    }
}
