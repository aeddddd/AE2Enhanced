package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.chamber.LongItemStore;
import com.github.aeddddd.ae2enhanced.container.ContainerSingularityChamber;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChamberAction;
import com.github.aeddddd.ae2enhanced.network.packet.PacketChamberSync;
import com.github.aeddddd.ae2enhanced.tile.TileSingularityChamber;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 奇点处理仓 GUI（项目通用香草风格,单页）.
 *
 * <p>输入/输出缓存为虚拟槽位（{@link com.github.aeddddd.ae2enhanced.container.slot.SlotLongStore}）,
 * 点击走原版标准链路：光标有物点击输入格 = 倒入（左键全部/右键一个）,
 * 光标无物点击 = 取回到光标（左键一组/右键一个）,Shift 点击 = 全部取回入背包.</p>
 *
 * <p>物品图标由原版槽位渲染,本类负责 long 计数覆盖层、任务进度条与能量条.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiSingularityChamber extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/singularity_chamber.png");

    private static final int TEXT = 0x404040;

    // 布局常量（相对 guiLeft/guiTop）
    private static final int GRID_X = 7, GRID_Y = 18;
    private static final int OUT_X = 7, OUT_Y = 88;
    private static final int JOB_Y_0 = 110, JOB_STRIDE = 18, JOB_ROWS = 2;
    private static final int JOB_BAR_X = 28, JOB_BAR_W = 116, JOB_BAR_H = 8;
    private static final int REDSTONE_X = 106, REDSTONE_Y = 148;
    private static final int ENERGY_X = 172, ENERGY_Y = 18, ENERGY_H = 100;

    private final BlockPos pos;

    // ---- 同步数据 ----
    private int energy = 0;
    private long parallelChannels = 1;
    private long usedChannels = 0;
    private int activeJobs = 0;
    private int redstoneMode = 0;
    private List<ItemStack> inputItems = new ArrayList<>();
    private List<Long> inputCounts = new ArrayList<>();
    private List<ItemStack> outputItems = new ArrayList<>();
    private List<Long> outputCounts = new ArrayList<>();
    private List<PacketChamberSync.JobView> jobs = new ArrayList<>();

    public GuiSingularityChamber(InventoryPlayer playerInv, TileSingularityChamber tile) {
        super(new ContainerSingularityChamber(playerInv, tile));
        this.pos = tile.getPos();
        this.xSize = 194;
        this.ySize = 254;
    }

    public void acceptSync(PacketChamberSync packet) {
        if (!packet.getPos().equals(pos)) {
            return;
        }
        this.energy = packet.getEnergy();
        this.parallelChannels = packet.getParallelChannels();
        this.usedChannels = packet.getUsedChannels();
        this.activeJobs = packet.getActiveJobs();
        this.redstoneMode = packet.getRedstoneMode();
        this.inputItems = packet.getInputItems();
        this.inputCounts = packet.getInputCounts();
        this.outputItems = packet.getOutputItems();
        this.outputCounts = packet.getOutputCounts();
        this.jobs = packet.getJobs();
    }

    // ---- 背景层：纹理 + 能量条 + 任务区 + 红石指示 ----

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, xSize, ySize);

        // 能量条
        double ratio = Math.min(1.0, energy / (double) Integer.MAX_VALUE);
        int fill = (int) ((ENERGY_H - 2) * ratio);
        if (energy > 0 && fill == 0) {
            fill = 1;
        }
        drawRect(guiLeft + ENERGY_X + 1, guiTop + ENERGY_Y + ENERGY_H - 1 - fill,
                guiLeft + ENERGY_X + 13, guiTop + ENERGY_Y + ENERGY_H - 1, 0xFF8A2BE2);

        // 任务区（图标 + 进度条）
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (int i = 0; i < Math.min(JOB_ROWS, jobs.size()); i++) {
            PacketChamberSync.JobView job = jobs.get(i);
            int y = guiTop + JOB_Y_0 + i * JOB_STRIDE;
            if (!job.output.isEmpty()) {
                itemRender.renderItemAndEffectIntoGUI(job.output, guiLeft + 7, y);
            }
        }
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        for (int i = 0; i < Math.min(JOB_ROWS, jobs.size()); i++) {
            PacketChamberSync.JobView job = jobs.get(i);
            int y = guiTop + JOB_Y_0 + i * JOB_STRIDE;
            int barX = guiLeft + JOB_BAR_X;
            int barY = y + 4;
            drawRect(barX - 1, barY - 1, barX + JOB_BAR_W + 1, barY + JOB_BAR_H + 1, 0xFF373737);
            drawRect(barX, barY, barX + JOB_BAR_W, barY + JOB_BAR_H, 0xFF555555);
            int barFill = (int) (JOB_BAR_W * job.fraction());
            if (barFill > 0) {
                drawRect(barX, barY, barX + barFill, barY + JOB_BAR_H, 0xFF7B3FBF);
                drawRect(barX, barY, barX + barFill, barY + 2, 0xFFB048E0);
            }
            String pct = (int) (job.fraction() * 100) + "%";
            fontRenderer.drawString(pct, barX + JOB_BAR_W + 4, barY, TEXT, false);
        }
        if (jobs.size() > JOB_ROWS) {
            fontRenderer.drawString("+" + (jobs.size() - JOB_ROWS),
                    guiLeft + JOB_BAR_X, guiTop + JOB_Y_0 + JOB_ROWS * JOB_STRIDE - 4, TEXT, false);
        }

        // 红石模式指示
        int color;
        switch (redstoneMode) {
            case 1: color = 0xFFCC2222; break;
            case 2: color = 0xFF2255CC; break;
            default: color = 0xFF888888; break;
        }
        drawRect(guiLeft + REDSTONE_X + 5, guiTop + REDSTONE_Y + 5,
                guiLeft + REDSTONE_X + 13, guiTop + REDSTONE_Y + 13, color);
    }

    // ---- 前景文字 ----

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String title = I18n.format("tile.ae2enhanced.singularity_chamber.name");
        fontRenderer.drawString(title, (xSize - fontRenderer.getStringWidth(title)) / 2, 5, TEXT);

        String parallelText = parallelChannels == Long.MAX_VALUE ? "∞" : String.valueOf(parallelChannels);
        String usedText = usedChannels == Long.MAX_VALUE ? "∞" : String.valueOf(usedChannels);
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.chamber.status",
                usedText, parallelText, activeJobs), GRID_X, 76, TEXT);
        fontRenderer.drawString(I18n.format("gui.ae2enhanced.chamber.jobs"), 7, JOB_Y_0 - 10, TEXT);
        fontRenderer.drawString(I18n.format("container.inventory"), 7, 161, TEXT);
    }

    // ---- 交互：虚拟槽位走原版 slotClick 链路,这里只处理红石按钮 ----

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (inRect(mouseX, mouseY, guiLeft + REDSTONE_X, guiTop + REDSTONE_Y, 18, 18)) {
            AE2Enhanced.network.sendToServer(new PacketChamberAction(
                    pos, PacketChamberAction.ACTION_CYCLE_REDSTONE, ""));
            mc.getSoundHandler().playSound(PositionedSoundRecord.getMasterRecord(
                    SoundEvents.UI_BUTTON_CLICK, 1.0f));
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private static boolean inRect(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int gridIndexAt(int mx, int my, int baseX, int baseY, int cols, int rows, int size) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= size) {
                    return -1;
                }
                if (inRect(mx, my, guiLeft + baseX + col * 18, guiTop + baseY + row * 18, 18, 18)) {
                    return idx;
                }
            }
        }
        return -1;
    }

    // ---- 计数覆盖层 + Tooltip（在原版槽位渲染之后绘制） ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        // long 计数覆盖层（原版槽位只画图标,计数由我们画）
        drawCountOverlay(GRID_X, GRID_Y, 9, 3, inputCounts);
        drawCountOverlay(OUT_X, OUT_Y, 9, 1, outputCounts);
        for (int i = 0; i < Math.min(JOB_ROWS, jobs.size()); i++) {
            PacketChamberSync.JobView job = jobs.get(i);
            if (!job.output.isEmpty()) {
                drawCount(formatCount(job.batches), guiLeft + 7, guiTop + JOB_Y_0 + i * JOB_STRIDE);
            }
        }

        renderHoveredToolTip(mouseX, mouseY);
        drawCustomTooltips(mouseX, mouseY);
    }

    private void drawCountOverlay(int baseX, int baseY, int cols, int rows, List<Long> counts) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                if (idx >= counts.size()) {
                    return;
                }
                long count = counts.get(idx);
                if (count <= 1) {
                    continue;
                }
                drawCount(formatCount(count), guiLeft + baseX + col * 18, guiTop + baseY + row * 18);
            }
        }
    }

    private void drawCount(String text, int slotX, int slotY) {
        GlStateManager.pushMatrix();
        GlStateManager.translate(0, 0, 260);
        float scale = text.length() > 4 ? 0.5f : 0.75f;
        GlStateManager.scale(scale, scale, 1.0f);
        int w = fontRenderer.getStringWidth(text);
        fontRenderer.drawString(text,
                (int) ((slotX + 17 - w * scale) / scale),
                (int) ((slotY + 18 - 9 * scale) / scale),
                0xFFFFFFFF, true);
        GlStateManager.popMatrix();
    }

    private void drawCustomTooltips(int mouseX, int mouseY) {
        // 能量条
        if (inRect(mouseX, mouseY, guiLeft + ENERGY_X - 1, guiTop + ENERGY_Y - 1, 16, ENERGY_H + 2)) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add(I18n.format("gui.ae2enhanced.chamber.energy",
                    formatCount(energy), formatCount(Integer.MAX_VALUE)));
            drawHoveringText(tooltip, mouseX, mouseY);
            return;
        }
        // 红石按钮
        if (inRect(mouseX, mouseY, guiLeft + REDSTONE_X, guiTop + REDSTONE_Y, 18, 18)) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add(I18n.format("gui.ae2enhanced.chamber.redstone." + redstoneMode));
            drawHoveringText(tooltip, mouseX, mouseY);
            return;
        }
        // 输入缓存（虚拟槽位无原版 tooltip,由这里补）
        int idx = gridIndexAt(mouseX, mouseY, GRID_X, GRID_Y, 9, 3, inputItems.size());
        if (idx >= 0 && !inputItems.get(idx).isEmpty()) {
            List<String> tooltip = getItemToolTip(inputItems.get(idx));
            tooltip.add(I18n.format("gui.ae2enhanced.chamber.count", inputCounts.get(idx)));
            for (String line : I18n.format("gui.ae2enhanced.chamber.interact_hint")
                    .replace("\\n", "\n").split("\n")) {
                tooltip.add(line);
            }
            drawHoveringText(tooltip, mouseX, mouseY);
            return;
        }
        // 输出缓冲
        int outIdx = gridIndexAt(mouseX, mouseY, OUT_X, OUT_Y, 9, 1, outputItems.size());
        if (outIdx >= 0 && !outputItems.get(outIdx).isEmpty()) {
            List<String> tooltip = getItemToolTip(outputItems.get(outIdx));
            tooltip.add(I18n.format("gui.ae2enhanced.chamber.count", outputCounts.get(outIdx)));
            tooltip.add(I18n.format("gui.ae2enhanced.chamber.withdraw_hint"));
            drawHoveringText(tooltip, mouseX, mouseY);
            return;
        }
        // 任务行
        for (int i = 0; i < Math.min(JOB_ROWS, jobs.size()); i++) {
            int y = guiTop + JOB_Y_0 + i * JOB_STRIDE;
            if (inRect(mouseX, mouseY, guiLeft + 7, y, JOB_BAR_X + JOB_BAR_W, 16)) {
                PacketChamberSync.JobView job = jobs.get(i);
                List<String> tooltip = new ArrayList<>();
                tooltip.add(job.output.isEmpty() ? "?" : job.output.getDisplayName());
                tooltip.add(I18n.format("gui.ae2enhanced.chamber.job_info",
                        formatCount(job.batches), job.progress, job.required));
                drawHoveringText(tooltip, mouseX, mouseY);
                return;
            }
        }
    }

    static String formatCount(long count) {
        if (count == Long.MAX_VALUE) {
            return "∞";
        }
        if (count < 1000) {
            return String.valueOf(count);
        }
        String[] units = {"k", "M", "G", "T", "P", "E"};
        double value = count;
        int unit = -1;
        while (value >= 1000 && unit < units.length - 1) {
            value /= 1000.0;
            unit++;
        }
        return String.format("%.1f%s", value, units[unit]);
    }
}
