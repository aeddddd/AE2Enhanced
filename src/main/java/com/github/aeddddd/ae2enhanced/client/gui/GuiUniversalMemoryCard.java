package com.github.aeddddd.ae2enhanced.client.gui;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemUniversalMemoryCard;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用内存卡管理 GUI, 横屏 340x200, 布局与 umc_gui.png 纹理面板一一对应:
 * 左侧选取目标列表带滚动条, 中部 3D 预览可拖动旋转, 右侧信息板显示来源/类型/升级/
 * 网络状态与绑定, 右下为三行芯片操作面板.
 *
 * <p>芯片用纹理横条九切片绘制, 点击切换即时发往服务端; 预览与信息板数据来自复制时
 * UMCCopyService 集中打标的 meta, 旧版内存卡无 meta 时自动降级为纯文字显示.</p>
 */
public class GuiUniversalMemoryCard extends GuiContainer {

    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation(AE2Enhanced.MOD_ID, "textures/gui/umc_gui.png");
    private static final float TEX_W = 512f;
    private static final float TEX_H = 256f;

    // GUI 尺寸(横屏)
    private static final int GUI_WIDTH = 340;
    private static final int GUI_HEIGHT = 200;

    // 列表面板(纹理内区 118×153,6 行行高 25)
    private static final int VISIBLE_COUNT = 6;
    private static final int ENTRY_HEIGHT = 25;
    private static final int LIST_X = 6;
    private static final int LIST_Y = 31;
    private static final int LIST_W = 118;
    private static final int LIST_HEIGHT = VISIBLE_COUNT * ENTRY_HEIGHT;
    private static final int SCROLL_X = 127; // 滑块 6px 宽,盖住纹理轨道 128..131
    private static final int SCROLL_W = 6;

    // 模型预览框(纹理内区 64×64)
    private static final int MODEL_X = 136;
    private static final int MODEL_Y = 31;
    private static final int MODEL_S = 64;

    // 信息板(纹理内区 128×64)
    private static final int INFO_X = 206;
    private static final int INFO_Y = 31;
    private static final int INFO_W = 128;
    private static final int INFO_LINES = 5;

    // 操作面板(纹理内区 198×72,三行 20px 芯片,行间留 4px)
    private static final int OPS_X = 136;
    private static final int OPS_W = 198;
    private static final int CHIP_H = 20;
    private static final int ROW1_Y = 115;
    private static final int ROW2_Y = 139;
    private static final int ROW3_Y = 163;

    // 动态控件素材(纹理 x>=340 素材区,均为九切片源; 最左 1px 为深色边框,不可裁掉)
    private static final int BTN_U = 340;         // 按压按钮 34×20:深紫=常态 灰=悬停
    private static final int BTN_V_NORMAL = 0;
    private static final int BTN_V_HOVER = 20;
    private static final int BTN_SPRITE_W = 34;
    private static final int BAR_U = 340;         // 芯片横条 118×20:灰=关 蓝=开/悬停
    private static final int BAR_V_GRAY = 78;
    private static final int BAR_V_BLUE = 98;
    private static final int BAR_SPRITE_W = 118;
    private static final int THUMB_U_GRAY = 340;  // 滚动滑块 6×18:灰=常态 蓝=悬停/拖动
    private static final int THUMB_U_BLUE = 352;
    private static final int THUMB_V = 60;
    private static final int THUMB_SPRITE_H = 18;
    private static final int X_U = 340;           // 行删除 X 8×9
    private static final int X_V = 118;
    private static final int X_W = 8;
    private static final int X_H = 9;
    private static final int SLICE_EDGE = 2;      // 九切片边缘保留宽度

    // 配色
    private static final int COLOR_TEXT = 0x3A3B4A;        // 面板上的主文字(深灰,适配浅底)
    private static final int COLOR_TEXT_DIM = 0x6E7190;
    private static final int COLOR_ONLINE = 0xFF1E7A34;
    private static final int COLOR_OFFLINE = 0xFFB03030;

    private final EntityPlayer player;
    private boolean hasConfig = false;
    private String configName = "";
    private String handlerId = "";
    private int upgradeCount = 0;
    private int netStatus = -1; // UMCCopyService.NetStatus.ordinal(),-1 = 无数据
    private boolean networkBound = false;
    private ItemStack renderStack = ItemStack.EMPTY;
    private IBlockState renderState = null;

    // 预览模型缓存:仅在 config NBT 引用变化时重建(服务端同步会整体替换 NBT 实例)
    private NBTTagCompound lastConfigRef = null;
    private TileEntity dummyTile = null;
    private IBakedModel previewModel = null;
    private boolean previewUseTeisr = false;
    /** 无物品形态的方块渲染载体(RenderItem.renderItem 对空堆直接跳过). */
    private static final ItemStack STONE_CARRIER = new ItemStack(Blocks.STONE);
    private List<ItemUniversalMemoryCard.SelectionEntry> selections = new ArrayList<>();
    private int lastCopyMode = -1;
    private int lastSelectMode = -1;
    private int lastOptions = -1;

    // 滚动条状态
    private int scrollIndex = 0;
    private boolean isDraggingThumb = false;
    private int dragStartY = 0;
    private int dragStartScroll = 0;

    // 模型拖动旋转状态
    private float modelRotX = 25f;
    private float modelRotY = 45f;
    private boolean isDraggingModel = false;
    private int lastDragX = 0;
    private int lastDragY = 0;

    // 芯片点击区域(initGui 中计算)
    private final int[] chipCopyMode = new int[4];
    private final int[] chipSelectMode = new int[4];
    private final int[][] chipOptions = new int[4][4];
    private boolean chipsLayoutDone = false;

    public GuiUniversalMemoryCard(EntityPlayer player) {
        super(new com.github.aeddddd.ae2enhanced.container.ContainerUniversalMemoryCard(player));
        this.player = player;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    public void initGui() {
        super.initGui();
        refreshData();
        clampScroll();

        this.buttonList.clear();
        // 行 3 清除按钮(纹理按压按钮,位置与宽度在 layoutChips 中按文字计算)
        this.buttonList.add(new GuiModernButton(0, 0, 0, 20, CHIP_H,
                I18n.format("gui.ae2enhanced.umc.btn.clear_config")));
        this.buttonList.add(new GuiModernButton(1, 0, 0, 20, CHIP_H,
                I18n.format("gui.ae2enhanced.umc.btn.clear_selections")));

        // 逐行删除按钮(隐形命中区,纹理 X 图标在背景层绘制)
        for (int i = 0; i < VISIBLE_COUNT; i++) {
            GuiModernButton btn = new GuiModernButton(2 + i, 0, 0, X_W + 2, X_H + 2, "");
            btn.visible = false;
            this.buttonList.add(btn);
        }
        layoutChips();
    }

    /** 计算操作面板各芯片点击区域与清除按钮位置(依赖字体宽度,每帧重算以保证模式切换后命中区同步). */
    private void layoutChips() {
        String copyText = modeChipText("gui.ae2enhanced.umc.section.copy",
                "gui.ae2enhanced.umc.copy_mode.", ItemUniversalMemoryCard.CopyMode.values(), lastCopyMode);
        String selectText = modeChipText("gui.ae2enhanced.umc.section.select",
                "gui.ae2enhanced.umc.select_mode.", ItemUniversalMemoryCard.SelectMode.values(), lastSelectMode);

        int w1 = this.fontRenderer.getStringWidth(copyText) + 12;
        int w2 = this.fontRenderer.getStringWidth(selectText) + 12;
        int row1X = OPS_X + (OPS_W - (w1 + 8 + w2)) / 2;
        setRect(chipCopyMode, row1X, ROW1_Y, w1, CHIP_H);
        setRect(chipSelectMode, row1X + w1 + 8, ROW1_Y, w2, CHIP_H);

        int optTotal = 15; // 4 个芯片间 3 个 5px 间隙
        for (int i = 0; i < 4; i++) {
            optTotal += this.fontRenderer.getStringWidth(I18n.format("gui.ae2enhanced.umc.option." + i)) + 12;
        }
        int ox = OPS_X + (OPS_W - optTotal) / 2;
        for (int i = 0; i < 4; i++) {
            int w = this.fontRenderer.getStringWidth(I18n.format("gui.ae2enhanced.umc.option." + i)) + 12;
            setRect(chipOptions[i], ox, ROW2_Y, w, CHIP_H);
            ox += w + 5;
        }

        if (this.buttonList.size() >= 2) {
            int bw1 = this.fontRenderer.getStringWidth(I18n.format("gui.ae2enhanced.umc.btn.clear_config")) + 16;
            int bw2 = this.fontRenderer.getStringWidth(I18n.format("gui.ae2enhanced.umc.btn.clear_selections")) + 16;
            int bx = OPS_X + (OPS_W - (bw1 + 8 + bw2)) / 2;
            GuiButton b0 = this.buttonList.get(0);
            GuiButton b1 = this.buttonList.get(1);
            b0.x = this.guiLeft + bx;
            b0.y = this.guiTop + ROW3_Y;
            b0.width = bw1;
            b0.height = CHIP_H;
            b1.x = this.guiLeft + bx + bw1 + 8;
            b1.y = this.guiTop + ROW3_Y;
            b1.width = bw2;
            b1.height = CHIP_H;
        }
        chipsLayoutDone = true;
    }

    /** 模式芯片文字:"标签: 当前值"(标签与值均走本地化). */
    private static String modeChipText(String labelKey, String valuePrefix, Enum<?>[] values, int lastOrdinal) {
        int idx = lastOrdinal < 0 ? 0 : Math.min(lastOrdinal, values.length - 1);
        return I18n.format(labelKey) + ": " + I18n.format(valuePrefix + values[idx].name().toLowerCase());
    }

    private static void setRect(int[] r, int x, int y, int w, int h) {
        r[0] = x; r[1] = y; r[2] = w; r[3] = h;
    }

    private void refreshData() {
        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemUniversalMemoryCard)) return;

        hasConfig = ItemUniversalMemoryCard.hasConfig(stack);
        configName = "";
        handlerId = "";
        upgradeCount = 0;
        netStatus = -1;
        renderStack = ItemStack.EMPTY;
        renderState = null;

        if (hasConfig) {
            NBTTagCompound config = ItemUniversalMemoryCard.getConfig(stack);
            configName = ItemUniversalMemoryCard.getLocalizedConfigName(stack);
            NBTTagCompound data = config.getCompoundTag("data");
            upgradeCount = data.hasKey("ae2e:upgrades") ? data.getTagList("ae2e:upgrades", 10).tagCount() : 0;

            // 集中打标的 meta(旧卡可能缺失,降级处理)
            NBTTagCompound meta = config.getCompoundTag("meta");
            handlerId = meta.getString("handlerId");
            if (handlerId.isEmpty()) handlerId = config.getString("handler");
            netStatus = meta.hasKey("net") ? meta.getInteger("net") : -1;
            renderStack = resolveRenderStack(meta);

            // config 引用变化(复制/粘贴/服务端同步)时重建预览模型
            if (config != lastConfigRef) {
                lastConfigRef = config;
                rebuildPreview(config);
            }
        } else if (lastConfigRef != null) {
            lastConfigRef = null;
            dummyTile = null;
            previewModel = null;
            previewUseTeisr = false;
        }
        networkBound = ItemUniversalMemoryCard.hasNetworkBinding(stack);
        selections = ItemUniversalMemoryCard.getSelections(stack);
        lastCopyMode = ItemUniversalMemoryCard.getCopyMode(stack).ordinal();
        lastSelectMode = ItemUniversalMemoryCard.getSelectMode(stack).ordinal();
        lastOptions = ItemUniversalMemoryCard.getOptions(stack);
    }

    /**
     * 从 meta 解析预览数据(Part 物品优先,其次 pickBlock 物品形态,最后裸方块).
     * 方块目标额外记录方块状态(渲染用状态模型,带正确朝向);
     * 优先返回复制时记录的 blockItem(pickBlock 形态,含机型 NBT,
     * 如热力机器的物品模型与显示名依赖 Type 字段,光有 blockMeta 只有等级),
     * 缺失时(旧卡)回退 meta 0 的裸物品堆.
     */
    private ItemStack resolveRenderStack(NBTTagCompound meta) {
        renderState = null;
        try {
            if (meta.hasKey("partItem")) {
                ItemStack s = new ItemStack(meta.getCompoundTag("partItem"));
                if (!s.isEmpty()) return s;
            }
            Block block = null;
            if (meta.hasKey("block")) {
                block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(meta.getString("block")));
                if (block != null) {
                    try {
                        renderState = block.getStateFromMeta(meta.getInteger("blockMeta"));
                    } catch (Exception e) {
                        renderState = block.getDefaultState();
                    }
                }
            }
            if (meta.hasKey("blockItem")) {
                ItemStack s = new ItemStack(meta.getCompoundTag("blockItem"));
                if (!s.isEmpty()) return s;
            }
            if (block != null) {
                ItemStack s = new ItemStack(block, 1, 0);
                if (!s.isEmpty()) return s;
            }
        } catch (Exception e) {
            // 预览解析失败静默降级
        }
        return ItemStack.EMPTY;
    }

    private void clampScroll() {
        if (scrollIndex < 0) scrollIndex = 0;
        int max = Math.max(0, selections.size() - VISIBLE_COUNT);
        if (scrollIndex > max) scrollIndex = max;
    }

    private int getThumbHeight() {
        if (selections.size() <= VISIBLE_COUNT) return LIST_HEIGHT;
        return Math.max(16, LIST_HEIGHT * VISIBLE_COUNT / selections.size());
    }

    private int getThumbY() {
        int sbY = this.guiTop + LIST_Y;
        if (selections.size() <= VISIBLE_COUNT) return sbY;
        int maxScroll = selections.size() - VISIBLE_COUNT;
        return sbY + scrollIndex * (LIST_HEIGHT - getThumbHeight()) / maxScroll;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.CLEAR_CONFIG, -1));
            return;
        }
        if (button.id == 1) {
            AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.CLEAR_SELECTIONS, -1));
            return;
        }
        int visibleIdx = button.id - 2;
        int actualIndex = scrollIndex + visibleIdx;
        if (actualIndex >= 0 && actualIndex < selections.size()) {
            AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.REMOVE_SELECTION, actualIndex));
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        ItemStack stack = player.getHeldItemMainhand();
        if (stack.getItem() instanceof ItemUniversalMemoryCard) {
            int currentCount = ItemUniversalMemoryCard.getSelectionCount(stack);
            boolean currentHasConfig = ItemUniversalMemoryCard.hasConfig(stack);
            if (currentCount != selections.size() || currentHasConfig != hasConfig) {
                this.initGui();
            }
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int delta = Mouse.getEventDWheel();
        if (delta != 0) {
            scrollIndex -= Integer.signum(delta);
            clampScroll();
        }
    }

    private boolean inRect(int[] r, int mouseX, int mouseY) {
        int gx = this.guiLeft + r[0];
        int gy = this.guiTop + r[1];
        return mouseX >= gx && mouseX < gx + r[2] && mouseY >= gy && mouseY < gy + r[3];
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // 1. 设置条芯片
        if (chipsLayoutDone && mouseButton == 0) {
            if (inRect(chipCopyMode, mouseX, mouseY)) {
                AE2Enhanced.network.sendToServer(new PacketUMCAction(
                        PacketUMCAction.ActionType.SET_COPY_MODE, (lastCopyMode + 1) % ItemUniversalMemoryCard.CopyMode.values().length));
                return;
            }
            if (inRect(chipSelectMode, mouseX, mouseY)) {
                AE2Enhanced.network.sendToServer(new PacketUMCAction(
                        PacketUMCAction.ActionType.SET_SELECT_MODE, (lastSelectMode + 1) % ItemUniversalMemoryCard.SelectMode.values().length));
                return;
            }
            for (int i = 0; i < 4; i++) {
                if (inRect(chipOptions[i], mouseX, mouseY)) {
                    AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.TOGGLE_OPTION, i));
                    return;
                }
            }
        }

        // 2. 模型框拖动旋转
        if (mouseButton == 0 && mouseX >= this.guiLeft + MODEL_X && mouseX < this.guiLeft + MODEL_X + MODEL_S
                && mouseY >= this.guiTop + MODEL_Y && mouseY < this.guiTop + MODEL_Y + MODEL_S) {
            isDraggingModel = true;
            lastDragX = mouseX;
            lastDragY = mouseY;
            return;
        }

        // 3. 滚动条
        int sbX = this.guiLeft + SCROLL_X;
        int sbY = this.guiTop + LIST_Y;
        if (mouseX >= sbX && mouseX < sbX + SCROLL_W &&
            mouseY >= sbY && mouseY < sbY + LIST_HEIGHT &&
            selections.size() > VISIBLE_COUNT) {
            int thumbY = getThumbY();
            int thumbH = getThumbHeight();
            if (mouseY >= thumbY && mouseY < thumbY + thumbH) {
                isDraggingThumb = true;
                dragStartY = mouseY;
                dragStartScroll = scrollIndex;
            } else if (mouseY < thumbY) {
                scrollIndex -= VISIBLE_COUNT;
                clampScroll();
            } else {
                scrollIndex += VISIBLE_COUNT;
                clampScroll();
            }
            return;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (isDraggingModel) {
            modelRotY += (mouseX - lastDragX) * 1.5f;
            modelRotX += (mouseY - lastDragY) * 1.5f;
            if (modelRotX < -80f) modelRotX = -80f;
            if (modelRotX > 80f) modelRotX = 80f;
            lastDragX = mouseX;
            lastDragY = mouseY;
            return;
        }
        if (isDraggingThumb && selections.size() > VISIBLE_COUNT) {
            int thumbH = getThumbHeight();
            int maxScroll = selections.size() - VISIBLE_COUNT;
            int deltaPixels = mouseY - dragStartY;
            int deltaSlots = deltaPixels * maxScroll / (LIST_HEIGHT - thumbH);
            scrollIndex = dragStartScroll + deltaSlots;
            clampScroll();
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        isDraggingThumb = false;
        isDraggingModel = false;
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        refreshData();
        clampScroll();

        int x = this.guiLeft;
        int y = this.guiTop;

        Minecraft.getMinecraft().getTextureManager().bindTexture(GUI_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawModalRectWithCustomSizedTexture(x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT, TEX_W, TEX_H);

        // ===== 标题栏:左列表标题(暗色辅助),中央主标题(放大凸显) =====
        String title = I18n.format("gui.ae2enhanced.umc.title");
        GlStateManager.pushMatrix();
        GlStateManager.translate(x + GUI_WIDTH / 2.0f, y + 15.0f, 0.0f);
        GlStateManager.scale(1.5f, 1.5f, 1.0f);
        this.fontRenderer.drawString(title, -this.fontRenderer.getStringWidth(title) / 2, -4, COLOR_TEXT);
        GlStateManager.popMatrix();
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.list_title", selections.size()),
                x + 10, y + 13, COLOR_TEXT_DIM);

        // ===== 左列:列表条目(图标 + 机器名 + 坐标/维度) =====
        Minecraft mc = Minecraft.getMinecraft();
        int maxDisplay = Math.min(selections.size() - scrollIndex, VISIBLE_COUNT);
        int currentDim = player.world.provider.getDimension();
        int rowIconX = x + LIST_X + 3;
        int rowTextX = x + LIST_X + 23;
        int rowTextW = LIST_W - 37;
        int rowXIconX = x + LIST_X + LIST_W - 11;
        for (int i = 0; i < maxDisplay; i++) {
            ItemUniversalMemoryCard.SelectionEntry entry = selections.get(scrollIndex + i);
            int rowY = y + LIST_Y + i * ENTRY_HEIGHT;

            // 静态图标(选取时记录的方块/Part 物品堆)
            if (entry.icon != null && !entry.icon.isEmpty()) {
                mc.getRenderItem().renderItemAndEffectIntoGUI(entry.icon, rowIconX, rowY + 4);
            }

            // 行 1:机器名(客户端本地化)
            String name = getEntryName(entry);
            this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(name, rowTextW - 4),
                    rowTextX, rowY + 3, COLOR_TEXT);

            // 行 2:坐标 + [P];跨维度条目追加红色维度标签(同维度省略,让位坐标)
            String coords = entry.pos.getX() + ", " + entry.pos.getY() + ", " + entry.pos.getZ()
                    + (entry.side >= 0 ? " [P]" : "");
            String dimTag = entry.dim == currentDim ? "" : I18n.format("gui.ae2enhanced.umc.dim_tag", entry.dim);
            coords = this.fontRenderer.trimStringToWidth(coords, Math.max(0, rowTextW - 4
                    - (dimTag.isEmpty() ? 0 : this.fontRenderer.getStringWidth(dimTag))));
            this.fontRenderer.drawString(coords, rowTextX, rowY + 14, COLOR_TEXT_DIM);
            if (!dimTag.isEmpty()) {
                this.fontRenderer.drawString(dimTag, rowTextX + this.fontRenderer.getStringWidth(coords),
                        rowY + 14, COLOR_OFFLINE);
            }

            // 删除 X(纹理图标,hover 泛红)
            int xy = rowY + 8;
            boolean hoverX = mouseX >= rowXIconX && mouseX < rowXIconX + X_W && mouseY >= xy && mouseY < xy + X_H;
            mc.getTextureManager().bindTexture(GUI_TEXTURE);
            GlStateManager.color(1.0f, hoverX ? 0.35f : 1.0f, hoverX ? 0.35f : 1.0f, 1.0f);
            drawScaledCustomSizeModalRect(rowXIconX, xy, X_U, X_V, X_W, X_H, X_W, X_H, TEX_W, TEX_H);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // ===== 滚动条滑块(纹理竖条,hover/拖动变蓝) =====
        if (selections.size() > VISIBLE_COUNT) {
            int thumbY = getThumbY();
            int thumbH = getThumbHeight();
            boolean hoverThumb = isDraggingThumb || (mouseX >= x + SCROLL_X && mouseX < x + SCROLL_X + SCROLL_W
                    && mouseY >= thumbY && mouseY < thumbY + thumbH);
            mc.getTextureManager().bindTexture(GUI_TEXTURE);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            drawSpriteV(x + SCROLL_X, thumbY, SCROLL_W, thumbH,
                    hoverThumb ? THUMB_U_BLUE : THUMB_U_GRAY, THUMB_V, THUMB_SPRITE_H, 3, 3);
        }

        // 同步删除按钮位置与可见性
        for (int i = 0; i < VISIBLE_COUNT; i++) {
            int btnIdx = 2 + i;
            if (btnIdx < this.buttonList.size()) {
                GuiButton btn = this.buttonList.get(btnIdx);
                btn.x = x + LIST_X + LIST_W - 12;
                btn.y = y + LIST_Y + i * ENTRY_HEIGHT + 7;
                btn.visible = i < maxDisplay;
            }
        }

        // ===== 右列:3D 模型预览 =====
        drawModelPreview(x, y);

        // ===== 右列:信息板 =====
        drawInfoPanel(x, y);

        // ===== 右下:操作面板(三行芯片) =====
        drawOpsPanel(x, y, mouseX, mouseY);
    }

    // ============================================================
    // 模型预览(可拖动旋转)
    // ============================================================

    private void drawModelPreview(int x, int y) {
        if (renderStack.isEmpty() && renderState == null) {
            String hint = I18n.format(hasConfig ? "gui.ae2enhanced.umc.old_data" : "gui.ae2enhanced.umc.no_preview");
            int hw = this.fontRenderer.getStringWidth(hint);
            this.fontRenderer.drawString(hint, x + MODEL_X + (MODEL_S - hw) / 2, y + MODEL_Y + MODEL_S / 2 - 4, COLOR_TEXT_DIM);
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        // 模型已在 rebuildPreview 中解析并缓存(含假 TileEntity 的扩展状态,
        // 热力机器的侧面 IO 覆盖层即来自此路径)
        IBakedModel model = previewModel;
        boolean useTeisr = previewUseTeisr;

        if (model == null && !useTeisr) {
            String hint = I18n.format("gui.ae2enhanced.umc.no_preview");
            int hw = this.fontRenderer.getStringWidth(hint);
            this.fontRenderer.drawString(hint, x + MODEL_X + (MODEL_S - hw) / 2, y + MODEL_Y + MODEL_S / 2 - 4, COLOR_TEXT_DIM);
            return;
        }

        GlStateManager.pushMatrix();
        try {
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).setBlurMipmap(false, false);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableAlpha();
            GlStateManager.alphaFunc(516, 0.1F);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
                    GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            RenderHelper.enableGUIStandardItemLighting();

            float scale = useTeisr ? 36f : (model.isGui3d() ? 30f : 40f);
            GlStateManager.translate(x + MODEL_X + MODEL_S / 2.0F, y + MODEL_Y + MODEL_S / 2.0F, 100.0F + this.zLevel);
            GlStateManager.scale(1.0F, -1.0F, 1.0F);
            GlStateManager.scale(scale, scale, scale);
            GlStateManager.rotate(modelRotX, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(modelRotY, 0.0F, 1.0F, 0.0F);
            if (useTeisr) {
                // TESR 方块(箱子等):模型围绕 0..1 渲染,先平移到中心
                GlStateManager.translate(-0.5F, -0.5F, -0.5F);
                net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer.instance.renderByItem(renderStack);
            } else {
                ItemStack carrier = renderStack.isEmpty() ? STONE_CARRIER : renderStack;
                mc.getRenderItem().renderItem(carrier, model);
            }

            GlStateManager.disableAlpha();
            GlStateManager.disableRescaleNormal();
            RenderHelper.disableStandardItemLighting();
        } catch (Exception e) {
            // 奇异模型渲染失败不应拖垮整个 GUI
        } finally {
            GlStateManager.popMatrix();
            mc.getTextureManager().bindTexture(GUI_TEXTURE);
            mc.getTextureManager().getTexture(TextureMap.LOCATION_BLOCKS_TEXTURE).restoreLastBlurMipmap();
        }
    }

    /**
     * 重建预览模型(仅在 config NBT 引用变化时调用).
     *
     * <p>解析链:</p>
     * <ol>
     *   <li>若方块带 TileEntity 且复制了 NBT 数据,构造假 TileEntity 并读入复制的
     *       NBT,再通过 {@link Block#getExtendedState} 提供扩展状态——热力(Thermal
     *       Expansion)等 mod 的机器侧面 IO 覆盖层(ISmartBlockModel 烘焙路径)
     *       依赖 TileEntity 中的 SideCache,此路径可原样显示复制时刻的侧面配置;</li>
     *   <li>普通方块状态模型(带朝向);</li>
     *   <li>物品模型;</li>
     *   <li>TEISR 物品渲染(箱子等 TESR 方块).</li>
     * </ol>
     * 任一步失败均回退到下一级,不抛出.
     */
    private void rebuildPreview(NBTTagCompound config) {
        previewModel = null;
        previewUseTeisr = false;
        dummyTile = null;
        Minecraft mc = Minecraft.getMinecraft();
        IBakedModel missingModel = mc.getRenderItem().getItemModelMesher().getModelManager().getMissingModel();
        try {
            IBlockState stateForModel = renderState;
            if (renderState != null && config.hasKey("data", 10)) {
                TileEntity te = createDummyTile(renderState, config.getCompoundTag("data"));
                if (te != null) {
                    dummyTile = te;
                    try {
                        IBlockState ext = renderState.getBlock().getExtendedState(renderState,
                                new PreviewBlockAccess(renderState, te), BlockPos.ORIGIN);
                        if (ext != null) stateForModel = ext;
                    } catch (Throwable t) {
                        // getExtendedState 不容忍假世界时退回普通状态
                        dummyTile = null;
                    }
                }
            }
            if (stateForModel != null) {
                // Forge 的 BlockModelShapes 对 IExtendedBlockState 自动走
                // ISmartBlockModel.handleBlockState,得到含侧面覆盖层的烘焙模型
                IBakedModel m = mc.getBlockRendererDispatcher().getModelForState(stateForModel);
                if (m != null && m != missingModel && !isEmptyModel(m, stateForModel)) {
                    previewModel = m;
                    return;
                }
                // 状态模型不可用(如 EIO 等 ISmartBlockModel 无 tile 数据时 quads 为空)
                // 不得直接跳到 TEISR——原版 TEISR 只识别箱子/旗帜等少数物品,
                // 普通机器会渲染为空白;继续尝试下面的物品模型路径
            }
            if (!renderStack.isEmpty()) {
                IBakedModel m = mc.getRenderItem().getItemModelWithOverrides(renderStack, null, null);
                if (m != null && m != missingModel && !isEmptyModel(m, null)) {
                    previewModel = m;
                    // 箱子等内建渲染器模型仍走 TEISR 路径
                    previewUseTeisr = m.isBuiltInRenderer();
                    return;
                }
                if (m != null && m.isBuiltInRenderer()) {
                    previewUseTeisr = true;
                }
            }
        } catch (Throwable t) {
            previewModel = null;
            previewUseTeisr = false;
            dummyTile = null;
        }
    }

    /**
     * 用复制的 NBT 构造假 TileEntity(world 为 null).
     * 依赖各 mod readFromNBT 对空 world 的容忍度,任何异常都返回 null 走回退路径.
     */
    private static TileEntity createDummyTile(IBlockState state, NBTTagCompound data) {
        try {
            Block block = state.getBlock();
            if (!block.hasTileEntity(state)) return null;
            TileEntity te = block.createTileEntity(null, state);
            if (te == null) return null;
            te.setPos(BlockPos.ORIGIN);
            NBTTagCompound copy = data.copy();
            copy.removeTag("ae2e:upgrades"); // 我方附加键不属于 tile NBT
            te.readFromNBT(copy);
            return te;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 预览用假世界:ORIGIN 处返回假 TileEntity 与目标方块状态,
     * 其余位置一律为空气(所有面可见);光照恒为最大亮度.
     */
    private static class PreviewBlockAccess implements IBlockAccess {
        private final IBlockState state;
        private final TileEntity tile;

        PreviewBlockAccess(IBlockState state, TileEntity tile) {
            this.state = state;
            this.tile = tile;
        }

        @Nullable
        @Override
        public TileEntity getTileEntity(BlockPos pos) {
            return BlockPos.ORIGIN.equals(pos) ? this.tile : null;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public int getCombinedLight(BlockPos pos, int lightValue) {
            return 0xF000F0;
        }

        @Override
        public IBlockState getBlockState(BlockPos pos) {
            return BlockPos.ORIGIN.equals(pos) ? this.state : Blocks.AIR.getDefaultState();
        }

        @Override
        public boolean isAirBlock(BlockPos pos) {
            return !BlockPos.ORIGIN.equals(pos);
        }

        @SideOnly(Side.CLIENT)
        @Override
        public Biome getBiome(BlockPos pos) {
            return Biomes.PLAINS;
        }

        @Override
        public int getStrongPower(BlockPos pos, EnumFacing direction) {
            return 0;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public WorldType getWorldType() {
            return WorldType.DEFAULT;
        }

        @Override
        public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean _default) {
            return false;
        }
    }

    /**
     * 列表条目的机器名:优先取图标物品堆的显示名(客户端本地化);
     * 旧版条目(无图标)退化为 tileId 的简单名.
     */
    private static String getEntryName(ItemUniversalMemoryCard.SelectionEntry entry) {
        if (entry.icon != null && !entry.icon.isEmpty()) return entry.icon.getDisplayName();
        String id = entry.tileId;
        int dot = Math.max(id.lastIndexOf('.'), id.lastIndexOf(':'));
        return dot >= 0 && dot < id.length() - 1 ? id.substring(dot + 1) : id;
    }

    /** 模型是否没有任何几何面(TESR 方块的占位模型). */
    private static boolean isEmptyModel(IBakedModel model, net.minecraft.block.state.IBlockState state) {
        try {
            if (!model.getQuads(state, null, 0L).isEmpty()) return false;
            for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.values()) {
                if (!model.getQuads(state, f, 0L).isEmpty()) return false;
            }
        } catch (Exception e) {
            return false; // 查询失败时按非空处理,走正常渲染路径
        }
        return true;
    }

    // ============================================================
    // 信息板
    // ============================================================

    private void drawInfoPanel(int x, int y) {
        int tx = x + INFO_X + 5;
        int ty = y + INFO_Y + 4;
        int maxW = INFO_W - 10;

        if (!hasConfig) {
            String empty = I18n.format("gui.ae2enhanced.umc.no_config_line");
            this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(empty, maxW), tx, ty, COLOR_TEXT_DIM);
            this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.info.binding",
                    boundText()), tx, ty + 20, COLOR_TEXT_DIM);
            return;
        }

        // 1. 来源
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(
                I18n.format("gui.ae2enhanced.umc.source", configName), maxW), tx, ty, COLOR_TEXT);
        // 2. 类型(handler)
        String handlerName = getHandlerDisplayName();
        this.fontRenderer.drawString(this.fontRenderer.trimStringToWidth(
                I18n.format("gui.ae2enhanced.umc.info.type", handlerName), maxW), tx, ty + 10, COLOR_TEXT_DIM);
        // 3. 升级数
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.upgrades", upgradeCount), tx, ty + 20, COLOR_TEXT);
        // 4. AE 网络状态(复制时刻快照)
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.info.net", netStatusText()), tx, ty + 30, netStatusColor());
        // 5. 网络绑定状态
        this.fontRenderer.drawString(I18n.format("gui.ae2enhanced.umc.info.binding", boundText()), tx, ty + 40,
                networkBound ? COLOR_ONLINE : COLOR_TEXT_DIM);
    }

    private String getHandlerDisplayName() {
        if (handlerId.isEmpty()) return "-";
        String key = "gui.ae2enhanced.umc.handler." + handlerId;
        return I18n.hasKey(key) ? I18n.format(key) : handlerId;
    }

    private String netStatusText() {
        switch (netStatus) {
            case 2: return I18n.format("gui.ae2enhanced.umc.net.online");
            case 1: return I18n.format("gui.ae2enhanced.umc.net.offline");
            case 0: return I18n.format("gui.ae2enhanced.umc.net.not_ae");
            default: return "§7-";
        }
    }

    private int netStatusColor() {
        switch (netStatus) {
            case 2: return COLOR_ONLINE;
            case 1: return COLOR_OFFLINE;
            default: return COLOR_TEXT_DIM;
        }
    }

    private String boundText() {
        return I18n.format(networkBound ? "gui.ae2enhanced.umc.bound.yes" : "gui.ae2enhanced.umc.bound.no");
    }

    // ============================================================
    // 操作面板(三行可点击芯片,纹理横条九切片)
    // ============================================================

    private void drawOpsPanel(int x, int y, int mouseX, int mouseY) {
        if (!chipsLayoutDone) return;
        layoutChips(); // 模式值切换后文字变长,每帧同步命中区与宽度

        // 行 1:粘贴内容 / 选取模式(循环切换,hover 高亮)
        String copyText = modeChipText("gui.ae2enhanced.umc.section.copy",
                "gui.ae2enhanced.umc.copy_mode.", ItemUniversalMemoryCard.CopyMode.values(), lastCopyMode);
        String selectText = modeChipText("gui.ae2enhanced.umc.section.select",
                "gui.ae2enhanced.umc.select_mode.", ItemUniversalMemoryCard.SelectMode.values(), lastSelectMode);
        drawBarChip(chipCopyMode, copyText, inRect(chipCopyMode, mouseX, mouseY));
        drawBarChip(chipSelectMode, selectText, inRect(chipSelectMode, mouseX, mouseY));

        // 行 2:4 个粘贴选项开关芯片(蓝=开,灰=关)
        int[] optBits = {
                ItemUniversalMemoryCard.OPT_UPGRADES, ItemUniversalMemoryCard.OPT_FACING,
                ItemUniversalMemoryCard.OPT_SIDES, ItemUniversalMemoryCard.OPT_REDSTONE
        };
        for (int i = 0; i < 4; i++) {
            drawBarChip(chipOptions[i], I18n.format("gui.ae2enhanced.umc.option." + i),
                    (lastOptions & optBits[i]) != 0);
        }
    }

    /** 绘制一枚芯片:纹理横条横向九切片 + 居中深色文字. blue=选中/悬停态. */
    private void drawBarChip(int[] r, String text, boolean blue) {
        int gx = this.guiLeft + r[0];
        int gy = this.guiTop + r[1];
        Minecraft.getMinecraft().getTextureManager().bindTexture(GUI_TEXTURE);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        drawSpriteH(gx, gy, r[2], r[3], BAR_U, blue ? BAR_V_BLUE : BAR_V_GRAY, BAR_SPRITE_W);
        int tw = this.fontRenderer.getStringWidth(text);
        this.fontRenderer.drawString(text, gx + (r[2] - tw) / 2, gy + (r[3] - 8) / 2, COLOR_TEXT);
    }

    /** 横向九切片:左右各保留 SLICE_EDGE 像素,中段取 1px 宽拉伸(源图填充色均匀). */
    private static void drawSpriteH(int x, int y, int w, int h, int u, int v, int spriteW) {
        drawScaledCustomSizeModalRect(x, y, u, v, SLICE_EDGE, h, SLICE_EDGE, h, TEX_W, TEX_H);
        drawScaledCustomSizeModalRect(x + SLICE_EDGE, y, u + SLICE_EDGE, v, 1, h,
                w - SLICE_EDGE * 2, h, TEX_W, TEX_H);
        drawScaledCustomSizeModalRect(x + w - SLICE_EDGE, y, u + spriteW - SLICE_EDGE, v,
                SLICE_EDGE, h, SLICE_EDGE, h, TEX_W, TEX_H);
    }

    /** 纵向九切片:上下各保留 edgeT/edgeB 像素,中段取 1px 高拉伸. */
    private static void drawSpriteV(int x, int y, int w, int h, int u, int v, int spriteH, int edgeT, int edgeB) {
        drawScaledCustomSizeModalRect(x, y, u, v, w, edgeT, w, edgeT, TEX_W, TEX_H);
        drawScaledCustomSizeModalRect(x, y + edgeT, u, v + edgeT, w, 1, w,
                h - edgeT - edgeB, TEX_W, TEX_H);
        drawScaledCustomSizeModalRect(x, y + h - edgeB, u, v + spriteH - edgeB, w, edgeB, w, edgeB, TEX_W, TEX_H);
    }

    // ============================================================
    // 悬停 tooltip
    // ============================================================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);

        if (chipsLayoutDone) {
            if (inRect(chipCopyMode, mouseX, mouseY)) {
                drawHoveringText(java.util.Collections.singletonList(
                        I18n.format("gui.ae2enhanced.umc.tip.copy_mode")), mouseX, mouseY);
                return;
            }
            if (inRect(chipSelectMode, mouseX, mouseY)) {
                drawHoveringText(java.util.Collections.singletonList(I18n.format("gui.ae2enhanced.umc.tip.select_mode",
                        com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.displayName(
                                com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.SELECT_MODIFIER))), mouseX, mouseY);
                return;
            }
            for (int i = 0; i < 4; i++) {
                if (inRect(chipOptions[i], mouseX, mouseY)) {
                    drawHoveringText(java.util.Collections.singletonList(
                            I18n.format("gui.ae2enhanced.umc.tip.option." + i)), mouseX, mouseY);
                    return;
                }
            }
        }

        // 模型框悬停提示
        if (mouseX >= this.guiLeft + MODEL_X && mouseX < this.guiLeft + MODEL_X + MODEL_S
                && mouseY >= this.guiTop + MODEL_Y && mouseY < this.guiTop + MODEL_Y + MODEL_S) {
            if (!renderStack.isEmpty()) {
                List<String> tip = new ArrayList<>();
                tip.add(renderStack.getDisplayName());
                tip.add("§7" + I18n.format("gui.ae2enhanced.umc.tip.model"));
                drawHoveringText(tip, mouseX, mouseY);
            }
            return;
        }

        // 升级行悬停显示升级明细
        if (hasConfig && upgradeCount > 0) {
            int tx = this.guiLeft + INFO_X;
            int ty = this.guiTop + INFO_Y + 24;
            if (mouseX >= tx && mouseX < tx + INFO_W && mouseY >= ty && mouseY < ty + 10) {
                List<String> tip = buildUpgradeTooltip();
                if (!tip.isEmpty()) {
                    drawHoveringText(tip, mouseX, mouseY);
                    return;
                }
            }
        }

        this.renderHoveredToolTip(mouseX, mouseY);
    }

    private List<String> buildUpgradeTooltip() {
        List<String> tip = new ArrayList<>();
        ItemStack stack = player.getHeldItemMainhand();
        if (!ItemUniversalMemoryCard.hasConfig(stack)) return tip;
        NBTTagCompound data = ItemUniversalMemoryCard.getConfig(stack).getCompoundTag("data");
        if (!data.hasKey("ae2e:upgrades")) return tip;
        NBTTagList upgrades = data.getTagList("ae2e:upgrades", 10);
        for (int i = 0; i < upgrades.tagCount(); i++) {
            ItemStack up = new ItemStack(upgrades.getCompoundTagAt(i));
            if (!up.isEmpty()) {
                tip.add(up.getDisplayName() + (up.getCount() > 1 ? " ×" + up.getCount() : ""));
            }
        }
        return tip;
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // 全部绘制在背景层完成(模型渲染需要全局坐标)
    }

    // ============================================================
    // 纹理按压按钮(33×20 按钮素材九切片,替代原纯色按钮)
    // ============================================================

    public static class GuiModernButton extends GuiButton {

        public GuiModernButton(int buttonId, int x, int y, int width, int height, String text) {
            super(buttonId, x, y, width, height, text);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;
            this.hovered = mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
            if (this.displayString.isEmpty()) return; // 隐形命中区(如列表 X 按钮)

            mc.getTextureManager().bindTexture(GUI_TEXTURE);
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            drawSpriteH(this.x, this.y, this.width, this.height, BTN_U,
                    this.hovered ? BTN_V_HOVER : BTN_V_NORMAL, BTN_SPRITE_W);
            int textColor = this.enabled ? COLOR_TEXT : COLOR_TEXT_DIM;
            this.drawCenteredString(mc.fontRenderer, this.displayString,
                    this.x + this.width / 2, this.y + (this.height - 8) / 2, textColor);
        }
    }
}
