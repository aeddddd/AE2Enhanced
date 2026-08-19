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
 * 奇点处理仓 GUI（手绘纹理 singularity_gui.png）.
 *
 * <p>布局：9×3 输入缓存（虚拟槽,点击倒入/取回）、9 列任务区
 * （进度条 + 任务输出图标）、箭头流向、9 格输出缓冲（虚拟槽）、
 * 右侧竖排 5 卡片槽（并行 + 4 升级）、输出行右侧红石模式按钮
 * （灰=未选中 / 浅蓝=悬停 / 蓝=已激活,中央图标指示模式）.</p>
 */
@SideOnly(Side.CLIENT)
public class GuiSingularityChamber extends GuiContainer {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/singularity_gui.png");

    private static final int TEXT = 0x404040;

    // ---- 布局常量（与手绘纹理像素对齐） ----
    private static final int PANEL_W = 176;
    private static final int GRID_X = 8, GRID_Y = 26;          // 输入缓存 9×3
    private static final int TRACK_X = 9, TRACK_Y = 87;        // 进度条轨道
    private static final int TRACK_W = 13, TRACK_H = 4;
    private static final int JOB_X = 8, JOB_Y = 94;            // 任务图标
    private static final int OUT_X = 8, OUT_Y = 120;           // 输出缓冲
    private static final int INV_Y = 152, HOTBAR_Y = 210;      // 玩家背包
    private static final int REDSTONE_X = 180, REDSTONE_Y = 119; // 红石按钮（输出行右侧预留位）

    // ---- 贴图区精灵坐标 ----
    private static final int[] SPRITE_FILL = {212, 1, 13, 4};   // 紫色进度填充
    private static final int[] SPRITE_BTN_NORMAL = {228, 6};    // 灰：未选中
    private static final int[] SPRITE_BTN_HOVER = {210, 6};     // 浅蓝：悬停
    private static final int[] SPRITE_BTN_ACTIVE = {210, 26};   // 蓝：已按下
    private static final int[] ICON_HIGH = {208, 61, 9, 9};     // 红：高电平
    private static final int[] ICON_LOW = {208, 52, 9, 9};      // 暗红：低电平
    private static final int[] ICON_IGNORE = {208, 70, 9, 9};   // 深色：忽略

    private final BlockPos pos;

    // ---- 同步数据 ----
    private long energy = 0;
    private long maxEnergy = Integer.MAX_VALUE;
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
        this.xSize = 208;
        this.ySize = 252;
    }

    public void acceptSync(PacketChamberSync packet) {
        if (!packet.getPos().equals(pos)) {
            return;
        }
        this.energy = packet.getEnergy();
        this.maxEnergy = packet.getMaxEnergy();
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

    // ---- 背景层 ----

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(TEXTURE);
        // 主面板 + 右侧卡片条带（条带右边界在 texture x=206,需画满 32px 宽）
        drawTexturedModalRect(guiLeft, guiTop, 0, 0, PANEL_W, ySize);
        drawTexturedModalRect(guiLeft + PANEL_W, guiTop, PANEL_W, 0, 32, 100);

        // 任务进度填充（紫色精灵,按进度裁剪宽度）
        for (int i = 0; i < Math.min(9, jobs.size()); i++) {
            int w = (int) (TRACK_W * jobs.get(i).fraction());
            if (w > 0) {
                drawTexturedModalRect(guiLeft + TRACK_X + i * 18, guiTop + TRACK_Y,
                        SPRITE_FILL[0], SPRITE_FILL[1], w, TRACK_H);
            }
        }

        // 任务输出图标
        RenderHelper.enableGUIStandardItemLighting();
        GlStateManager.enableDepth();
        for (int i = 0; i < Math.min(9, jobs.size()); i++) {
            ItemStack output = jobs.get(i).output;
            if (!output.isEmpty()) {
                itemRender.renderItemAndEffectIntoGUI(output, guiLeft + JOB_X + i * 18, guiTop + JOB_Y);
            }
        }
        GlStateManager.disableDepth();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);

        // 红石按钮：悬停 > 已激活(非忽略) > 未选中
        mc.getTextureManager().bindTexture(TEXTURE);
        boolean hovered = inRect(mouseX, mouseY, guiLeft + REDSTONE_X, guiTop + REDSTONE_Y, 18, 18);
        int[] sprite;
        if (hovered) {
            sprite = SPRITE_BTN_HOVER;
        } else if (redstoneMode != 0) {
            sprite = SPRITE_BTN_ACTIVE;
        } else {
            sprite = SPRITE_BTN_NORMAL;
        }
        drawTexturedModalRect(guiLeft + REDSTONE_X, guiTop + REDSTONE_Y, sprite[0], sprite[1], 18, 18);
        // 模式图标（9×9 紧密瓦片,1:1 居中绘制在按钮的图标层区域）
        int[] icon = redstoneMode == 1 ? ICON_HIGH : redstoneMode == 2 ? ICON_LOW : ICON_IGNORE;
        drawTexturedModalRect(guiLeft + REDSTONE_X + 4, guiTop + REDSTONE_Y + 4,
                icon[0], icon[1], icon[2], icon[3]);
    }

    // ---- 前景文字 ----

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(I18n.format("tile.ae2enhanced.singularity_chamber.name"), 8, 8, TEXT);
        String parallelText = parallelChannels == Long.MAX_VALUE ? "∞" : String.valueOf(parallelChannels);
        String usedText = usedChannels == Long.MAX_VALUE ? "∞" : String.valueOf(usedChannels);
        String status = I18n.format("gui.ae2enhanced.chamber.status", usedText, parallelText, activeJobs);
        fontRenderer.drawString(status, PANEL_W - 8 - fontRenderer.getStringWidth(status), 8, TEXT);
        fontRenderer.drawString(I18n.format("container.inventory"), 8, INV_Y - 11, TEXT);
    }

    // ---- 交互：红石按钮（虚拟槽位走原版 slotClick 链路） ----

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

    // ---- 计数覆盖层 + Tooltip ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        drawCountOverlay(GRID_X, GRID_Y, 9, 3, inputCounts);
        drawCountOverlay(OUT_X, OUT_Y, 9, 1, outputCounts);
        for (int i = 0; i < Math.min(9, jobs.size()); i++) {
            if (!jobs.get(i).output.isEmpty()) {
                drawCount(formatCount(jobs.get(i).batches), guiLeft + JOB_X + i * 18, guiTop + JOB_Y);
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
        // 红石按钮
        if (inRect(mouseX, mouseY, guiLeft + REDSTONE_X, guiTop + REDSTONE_Y, 18, 18)) {
            List<String> tooltip = new ArrayList<>();
            tooltip.add(I18n.format("gui.ae2enhanced.chamber.redstone." + redstoneMode));
            tooltip.add(I18n.format("gui.ae2enhanced.chamber.energy",
                    formatCount(energy), formatCount(maxEnergy)));
            drawHoveringText(tooltip, mouseX, mouseY);
            return;
        }
        // 输入缓存
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
        // 任务列（图标 + 进度条区域）
        for (int i = 0; i < Math.min(9, jobs.size()); i++) {
            if (inRect(mouseX, mouseY, guiLeft + JOB_X + i * 18 - 1, guiTop + TRACK_Y - 1, 18, 26)) {
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
