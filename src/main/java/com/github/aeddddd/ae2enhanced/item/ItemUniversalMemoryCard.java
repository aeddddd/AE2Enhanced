package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.UMCCopyService;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.UMCPasteService;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.UMCSelectionService;
import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用内存卡：复制/粘贴 AE2 设备配置(含升级卡),选取世界中的目标方块.
 *
 * 架构约定：
 * 业务逻辑已拆分到 UMCCopyService / UMCPasteService / UMCSelectionService.
 * 本类只保留：NBT 序列化、客户端事件分发、tooltip 渲染.
 *
 * 网络绑定：实现 {@link appeng.api.features.INetworkEncodable},与无线终端同款流程——
 * 将卡放入安全终端的编码槽即完成绑定(写入 encryptionKey),粘贴时经
 * LocatableRegistry 解析回安全终端所在网络.旧的右键绑定(坐标+维度)仍被读取以兼容存量卡.
 */
public class ItemUniversalMemoryCard extends Item implements appeng.api.features.INetworkEncodable {

    private static final String NBT_CONFIG = "ae2e:umc_config";
    private static final String NBT_SELECTIONS = "ae2e:umc_selections";
    private static final String NBT_BINDING = "ae2e:umc_binding";
    private static final String NBT_COPY_MODE = "ae2e:umc_copy_mode";
    private static final String NBT_SELECT_MODE = "ae2e:umc_select_mode";
    private static final String NBT_OPTIONS = "ae2e:umc_options";
    private static final String NBT_AREA_CORNER = "ae2e:umc_area_corner";
    /** AE2 约定的编码键(与无线终端一致,安全终端编码槽写入). */
    private static final String NBT_ENCRYPTION_KEY = "encryptionKey";
    public static final int GUI_ID = 10;

    /** 复制内容模式：决定粘贴时应用配置的哪些部分(数据始终完整保存,模式在粘贴时过滤). */
    public enum CopyMode {
        FULL,           // 完整(配置+升级)
        CONFIG_ONLY,    // 仅配置(不粘贴升级)
        UPGRADES_ONLY   // 仅升级(不粘贴配置)
    }

    /** 选取模式：决定 Ctrl+右键 的选取行为. */
    public enum SelectMode {
        SINGLE,  // 单方块
        CHAIN,   // 连锁同类
        AREA     // 区域框选(两次点击确定对角)
    }

    /** 粘贴选项位掩码. */
    public static final int OPT_UPGRADES = 1;
    public static final int OPT_FACING = 2;
    public static final int OPT_SIDES = 4;
    public static final int OPT_REDSTONE = 8;
    public static final int OPT_DEFAULT = OPT_UPGRADES | OPT_FACING | OPT_SIDES | OPT_REDSTONE;

    public ItemUniversalMemoryCard() {
        setMaxStackSize(1);
        setCreativeTab(AE2Enhanced.CREATIVE_TAB);
        setTranslationKey("ae2enhanced.universal_memory_card");
        setRegistryName("universal_memory_card");
    }

    // ============================================================
    // SelectionEntry
    // ============================================================

    public static class SelectionEntry {
        public final BlockPos pos;
        public final int dim;
        public final String tileId;
        public final int side;
        /** 选取时记录的图标物品堆(GUI 列表显示用,可为 EMPTY). */
        public final ItemStack icon;

        public SelectionEntry(BlockPos pos, int dim, String tileId, int side) {
            this(pos, dim, tileId, side, ItemStack.EMPTY);
        }

        public SelectionEntry(BlockPos pos, int dim, String tileId, int side, ItemStack icon) {
            this.pos = pos;
            this.dim = dim;
            this.tileId = tileId;
            this.side = side;
            this.icon = icon == null ? ItemStack.EMPTY : icon;
        }

        public NBTTagCompound toNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("pos", pos.toLong());
            tag.setInteger("dim", dim);
            tag.setString("id", tileId);
            tag.setInteger("side", side);
            if (!icon.isEmpty()) {
                tag.setTag("icon", icon.writeToNBT(new NBTTagCompound()));
            }
            return tag;
        }

        public static SelectionEntry fromNBT(NBTTagCompound tag) {
            return new SelectionEntry(
                    BlockPos.fromLong(tag.getLong("pos")),
                    tag.getInteger("dim"),
                    tag.getString("id"),
                    tag.hasKey("side") ? tag.getInteger("side") : -1,
                    tag.hasKey("icon") ? new ItemStack(tag.getCompoundTag("icon")) : ItemStack.EMPTY
            );
        }
    }

    // ============================================================
    // NBT Helpers
    // ============================================================

    public static boolean hasConfig(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(NBT_CONFIG);
    }

    @Nullable
    public static NBTTagCompound getConfig(ItemStack stack) {
        if (!hasConfig(stack)) return null;
        return stack.getTagCompound().getCompoundTag(NBT_CONFIG);
    }

    public static void setConfig(ItemStack stack, String handlerId, String name, NBTTagCompound data) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound config = new NBTTagCompound();
        config.setString("handler", handlerId);
        config.setString("name", name);
        config.setTag("data", data);
        stack.getTagCompound().setTag(NBT_CONFIG, config);
    }

    public static void clearConfig(ItemStack stack) {
        if (stack.hasTagCompound()) {
            stack.getTagCompound().removeTag(NBT_CONFIG);
        }
    }

    /**
     * 客户端显示用配置名称.
     *
     * <p>复制发生在服务端线程,handler 给的名称可能只是英文名甚至未翻译的
     * lang key(如热力机器:单方块多机型,方块级翻译键不存在).
     * 此处按 meta 中记录的物品/方块重建翻译键,用客户端语言重新本地化;
     * 键缺失时回退到复制时存储的名称.</p>
     */
    @SideOnly(Side.CLIENT)
    public static String getLocalizedConfigName(ItemStack stack) {
        if (!hasConfig(stack)) return "";
        NBTTagCompound config = getConfig(stack);
        String name = config.getString("name");
        try {
            NBTTagCompound meta = config.getCompoundTag("meta");
            ItemStack s = ItemStack.EMPTY;
            if (meta.hasKey("blockItem")) {
                // pickBlock 形态含机型 NBT(如热力机器的 Type),优先级最高
                s = new ItemStack(meta.getCompoundTag("blockItem"));
            }
            if (s.isEmpty() && meta.hasKey("partItem")) {
                s = new ItemStack(meta.getCompoundTag("partItem"));
            }
            if (s.isEmpty() && meta.hasKey("block")) {
                Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(meta.getString("block")));
                if (b != null) {
                    Item item = Item.getItemFromBlock(b);
                    // 注意:使用真实 blockMeta(机型变体),而非模型用的 meta 0
                    if (item != null) s = new ItemStack(item, 1, meta.getInteger("blockMeta"));
                }
            }
            if (!s.isEmpty()) {
                String key = s.getItem().getTranslationKey(s) + ".name";
                if (I18n.hasKey(key)) return I18n.format(key);
            }
        } catch (Exception e) {
            // 回退到复制时存储的名称
        }
        return name;
    }

    // ============================================================
    // INetworkEncodable(安全终端编码槽绑定)
    // ============================================================

    @Override
    public String getEncryptionKey(ItemStack stack) {
        if (!stack.hasTagCompound()) return "";
        return stack.getTagCompound().getString(NBT_ENCRYPTION_KEY);
    }

    @Override
    public void setEncryptionKey(ItemStack stack, String encKey, String name) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setString(NBT_ENCRYPTION_KEY, encKey);
        if (name != null && !name.isEmpty()) {
            stack.getTagCompound().setString("encName", name);
        }
        // 新机制生效后清除旧的坐标绑定,避免两套绑定并存产生歧义
        clearBinding(stack);
    }

    /**
     * 是否已绑定网络(新编码键 或 旧坐标绑定任一存在).
     */
    public static boolean hasNetworkBinding(ItemStack stack) {
        if (hasBinding(stack)) return true;
        if (!stack.hasTagCompound()) return false;
        return !stack.getTagCompound().getString(NBT_ENCRYPTION_KEY).isEmpty();
    }

    /**
     * 读取编码键,未绑定时返回 null.
     */
    @Nullable
    public static Long getBoundSecurityKey(ItemStack stack) {
        if (!stack.hasTagCompound()) return null;
        String key = stack.getTagCompound().getString(NBT_ENCRYPTION_KEY);
        if (key.isEmpty()) return null;
        try {
            return Long.parseLong(key);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean hasBinding(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(NBT_BINDING);
    }

    @Nullable
    public static NBTTagCompound getBinding(ItemStack stack) {
        if (!hasBinding(stack)) return null;
        return stack.getTagCompound().getCompoundTag(NBT_BINDING);
    }

    public static void setBinding(ItemStack stack, BlockPos pos, int dim) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound binding = new NBTTagCompound();
        binding.setLong("pos", pos.toLong());
        binding.setInteger("dim", dim);
        stack.getTagCompound().setTag(NBT_BINDING, binding);
    }

    public static void clearBinding(ItemStack stack) {
        if (stack.hasTagCompound()) {
            stack.getTagCompound().removeTag(NBT_BINDING);
        }
    }

    public static List<SelectionEntry> getSelections(ItemStack stack) {
        List<SelectionEntry> list = new ArrayList<>();
        if (!stack.hasTagCompound()) return list;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_SELECTIONS)) return list;
        NBTTagList selections = tag.getTagList(NBT_SELECTIONS, 10);
        for (int i = 0; i < selections.tagCount(); i++) {
            list.add(SelectionEntry.fromNBT(selections.getCompoundTagAt(i)));
        }
        return list;
    }

    public static int getSelectionCount(ItemStack stack) {
        return getSelections(stack).size();
    }

    public static void addSelection(ItemStack stack, SelectionEntry entry) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagList selections;
        if (stack.getTagCompound().hasKey(NBT_SELECTIONS)) {
            selections = stack.getTagCompound().getTagList(NBT_SELECTIONS, 10);
        } else {
            selections = new NBTTagList();
        }
        for (int i = 0; i < selections.tagCount(); i++) {
            NBTTagCompound tag = selections.getCompoundTagAt(i);
            if (BlockPos.fromLong(tag.getLong("pos")).equals(entry.pos) && tag.getInteger("dim") == entry.dim) {
                return;
            }
        }
        selections.appendTag(entry.toNBT());
        stack.getTagCompound().setTag(NBT_SELECTIONS, selections);
    }

    public static void removeSelection(ItemStack stack, int index) {
        if (!stack.hasTagCompound()) return;
        NBTTagList selections = stack.getTagCompound().getTagList(NBT_SELECTIONS, 10);
        if (index >= 0 && index < selections.tagCount()) {
            selections.removeTag(index);
        }
        if (selections.tagCount() == 0) {
            stack.getTagCompound().removeTag(NBT_SELECTIONS);
        }
    }

    public static void clearSelections(ItemStack stack) {
        if (stack.hasTagCompound()) {
            stack.getTagCompound().removeTag(NBT_SELECTIONS);
        }
    }

    // ============================================================
    // Mode / Option Helpers
    // ============================================================

    public static CopyMode getCopyMode(ItemStack stack) {
        if (!stack.hasTagCompound()) return CopyMode.FULL;
        int ordinal = stack.getTagCompound().getInteger(NBT_COPY_MODE);
        if (ordinal < 0 || ordinal >= CopyMode.values().length) return CopyMode.FULL;
        return CopyMode.values()[ordinal];
    }

    public static CopyMode cycleCopyMode(ItemStack stack) {
        CopyMode next = CopyMode.values()[(getCopyMode(stack).ordinal() + 1) % CopyMode.values().length];
        setCopyMode(stack, next);
        return next;
    }

    public static void setCopyMode(ItemStack stack, CopyMode mode) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(NBT_COPY_MODE, mode.ordinal());
    }

    public static SelectMode getSelectMode(ItemStack stack) {
        if (!stack.hasTagCompound()) return SelectMode.CHAIN;
        int ordinal = stack.getTagCompound().getInteger(NBT_SELECT_MODE);
        if (ordinal < 0 || ordinal >= SelectMode.values().length) return SelectMode.CHAIN;
        return SelectMode.values()[ordinal];
    }

    public static SelectMode cycleSelectMode(ItemStack stack) {
        SelectMode next = SelectMode.values()[(getSelectMode(stack).ordinal() + 1) % SelectMode.values().length];
        setSelectMode(stack, next);
        return next;
    }

    public static void setSelectMode(ItemStack stack, SelectMode mode) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(NBT_SELECT_MODE, mode.ordinal());
        // 切换选取模式时清除未完成的区域角点
        clearAreaCorner(stack);
    }

    public static int getOptions(ItemStack stack) {
        if (!stack.hasTagCompound()) return OPT_DEFAULT;
        NBTTagCompound tag = stack.getTagCompound();
        if (!tag.hasKey(NBT_OPTIONS)) return OPT_DEFAULT;
        return tag.getByte(NBT_OPTIONS) & 0xFF;
    }

    public static boolean hasOption(ItemStack stack, int option) {
        return (getOptions(stack) & option) != 0;
    }

    public static boolean toggleOption(ItemStack stack, int option) {
        int next = getOptions(stack) ^ option;
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setByte(NBT_OPTIONS, (byte) next);
        return (next & option) != 0;
    }

    // ============================================================
    // Area Selection Corner
    // ============================================================

    public static boolean hasAreaCorner(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().hasKey(NBT_AREA_CORNER);
    }

    @Nullable
    public static NBTTagCompound getAreaCorner(ItemStack stack) {
        if (!hasAreaCorner(stack)) return null;
        return stack.getTagCompound().getCompoundTag(NBT_AREA_CORNER);
    }

    public static void setAreaCorner(ItemStack stack, BlockPos pos, int dim) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        NBTTagCompound corner = new NBTTagCompound();
        corner.setLong("pos", pos.toLong());
        corner.setInteger("dim", dim);
        stack.getTagCompound().setTag(NBT_AREA_CORNER, corner);
    }

    public static void clearAreaCorner(ItemStack stack) {
        if (stack.hasTagCompound()) {
            stack.getTagCompound().removeTag(NBT_AREA_CORNER);
        }
    }

    // ============================================================
    // Client Events
    // ============================================================

    @SideOnly(Side.CLIENT)
    public static void registerClientEvents() {
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }

    @SideOnly(Side.CLIENT)
    public static class ClientEvents {
        @SideOnly(Side.CLIENT)
        @SubscribeEvent(priority = net.minecraftforge.fml.common.eventhandler.EventPriority.HIGH)
        public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            if (event.getWorld().isRemote && event.getHand() == EnumHand.MAIN_HAND) {
                ItemStack stack = event.getEntityPlayer().getHeldItemMainhand();
                if (stack.getItem() instanceof ItemUniversalMemoryCard) {
                    EntityPlayer player = event.getEntityPlayer();
                    boolean isSneaking = player.isSneaking();
                    // 选取/清除修饰键均为可配置键位(默认左 Ctrl / 左 Alt),不再锁死
                    boolean isSelect = com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.isSelectDown();
                    boolean isClear = com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.isClearDown();

                    net.minecraft.tileentity.TileEntity te = event.getWorld().getTileEntity(event.getPos());
                    boolean isCentralInterface = te instanceof com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
                    boolean isSmartPatternInterface = te instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
                    boolean isRecycler = te instanceof com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;
                    boolean isSecurityStation = te instanceof appeng.tile.misc.TileSecurityStation;

                    // 安全终端:普通右键不再拦截——直接打开终端 GUI,
                    // 绑定改为将卡放入终端的编码槽(INetworkEncodable,与无线终端一致)
                    if (isSecurityStation && !isSneaking && !isSelect && !isClear) {
                        return;
                    }

                    // 智能样板接口绑定：客户端查询 JEI 后直接发送 PacketSmartPatternBind
                    if (isSmartPatternInterface && !isSneaking && !isSelect && !isClear) {
                        handleSmartPatternBindClient(player, stack, event.getPos(), event.getWorld());
                        event.setCanceled(true);
                        event.setCancellationResult(EnumActionResult.FAIL);
                        return;
                    }

                    PacketUMCAction.ActionType type;
                    if (isRecycler && isClear && !isSneaking && !isSelect) {
                        type = PacketUMCAction.ActionType.CLEAR_RECYCLER_BINDINGS;
                    } else if (isRecycler && !isSneaking && !isSelect) {
                        type = PacketUMCAction.ActionType.BIND_RECYCLER;
                    } else if (isCentralInterface && isClear && !isSneaking && !isSelect) {
                        type = PacketUMCAction.ActionType.CLEAR_BINDINGS;
                    } else if (isCentralInterface && !isSneaking && !isSelect) {
                        type = PacketUMCAction.ActionType.BIND_SOURCE;
                    } else if (isSelect) {
                        type = PacketUMCAction.ActionType.SELECT;
                    } else if (isSneaking) {
                        type = PacketUMCAction.ActionType.COPY;
                    } else {
                        type = PacketUMCAction.ActionType.PASTE;
                    }

                    AE2Enhanced.network.sendToServer(new PacketUMCAction(type, event.getPos(), event.getFace()));
                    event.setCanceled(true);
                    event.setCancellationResult(EnumActionResult.FAIL);
                }
            }
        }

        @SideOnly(Side.CLIENT)
        @SubscribeEvent
        public void onMouseInput(MouseEvent event) {
            if (event.getButton() == 1 && event.isButtonstate()) {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
                if (mc.player == null || mc.currentScreen != null) return;
                ItemStack stack = mc.player.getHeldItemMainhand();
                if (stack.getItem() instanceof ItemUniversalMemoryCard) {
                    net.minecraft.util.math.RayTraceResult ray = mc.objectMouseOver;
                    if (ray == null || ray.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.MISS) {
                        AE2Enhanced.network.sendToServer(new PacketUMCAction(PacketUMCAction.ActionType.OPEN_GUI));
                        event.setCanceled(true);
                    }
                }
            }
        }
    }

    // ============================================================
    // Server Action Handler (called by PacketUMCAction.Handler)
    // ============================================================

    public static void handleServerAction(EntityPlayer player, PacketUMCAction message) {
        ItemStack stack = player.getHeldItemMainhand();
        if (!(stack.getItem() instanceof ItemUniversalMemoryCard)) return;
        // 非法动作类型(反序列化校验失败)直接丢弃
        if (message.getType() == null) return;

        switch (message.getType()) {
            case COPY: {
                EnumFacing face = message.getFace();
                if (face != null) UMCCopyService.handleCopy(player, stack, message.getPos(), face);
                break;
            }
            case PASTE: {
                EnumFacing face = message.getFace();
                if (face != null) UMCPasteService.handlePaste(player, stack, message.getPos(), face);
                break;
            }
            case SELECT: {
                EnumFacing face = message.getFace();
                if (face != null) UMCSelectionService.handleSelect(player, stack, message.getPos(), face);
                break;
            }
            case CLEAR_CONFIG:
                clearConfig(stack);
                break;
            case CLEAR_SELECTIONS:
                clearSelections(stack);
                clearAreaCorner(stack);
                break;
            case CYCLE_COPY_MODE: {
                CopyMode next = cycleCopyMode(stack);
                player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.copy_mode_changed",
                        new TextComponentTranslation("gui.ae2enhanced.umc.copy_mode." + next.name().toLowerCase())));
                break;
            }
            case CYCLE_SELECT_MODE: {
                SelectMode next = cycleSelectMode(stack);
                player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.select_mode_changed",
                        new TextComponentTranslation("gui.ae2enhanced.umc.select_mode." + next.name().toLowerCase())));
                break;
            }
            case SET_COPY_MODE: {
                int ordinal = message.getIndex();
                if (ordinal >= 0 && ordinal < CopyMode.values().length) {
                    setCopyMode(stack, CopyMode.values()[ordinal]);
                }
                break;
            }
            case SET_SELECT_MODE: {
                int ordinal = message.getIndex();
                if (ordinal >= 0 && ordinal < SelectMode.values().length) {
                    setSelectMode(stack, SelectMode.values()[ordinal]);
                }
                break;
            }
            case BIND_NETWORK:
                // 已废弃：网络绑定改为将卡放入安全终端编码槽(INetworkEncodable).
                // 保留枚举值占位以维持网络包序数稳定,收到时静默忽略.
                break;
            case TOGGLE_OPTION: {
                int bit = message.getIndex();
                int option;
                switch (bit) {
                    case 0: option = OPT_UPGRADES; break;
                    case 1: option = OPT_FACING; break;
                    case 2: option = OPT_SIDES; break;
                    case 3: option = OPT_REDSTONE; break;
                    default: return;
                }
                boolean enabled = toggleOption(stack, option);
                player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.option_toggled",
                        new TextComponentTranslation("gui.ae2enhanced.umc.option." + bit),
                        new TextComponentTranslation(enabled ? "gui.ae2enhanced.umc.option.on" : "gui.ae2enhanced.umc.option.off")));
                break;
            }
            case REMOVE_SELECTION:
                removeSelection(stack, message.getIndex());
                break;
            case OPEN_GUI:
                player.openGui(AE2Enhanced.instance, GUI_ID, player.world,
                        (int) player.posX, (int) player.posY, (int) player.posZ);
                break;
            case BIND_SOURCE: {
                EnumFacing face = message.getFace();
                if (face != null) UMCSelectionService.handleBindSource(player, stack, message.getPos(), face);
                break;
            }
            case CLEAR_BINDINGS:
                UMCSelectionService.handleClearBindings(player, message.getPos());
                break;
            case BIND_RECYCLER: {
                EnumFacing face = message.getFace();
                if (face != null) UMCSelectionService.handleBindRecycler(player, stack, message.getPos(), face);
                break;
            }
            case CLEAR_RECYCLER_BINDINGS:
                UMCSelectionService.handleClearRecyclerBindings(player, message.getPos());
                break;
        }

        player.inventoryContainer.detectAndSendChanges();
    }

    // ============================================================
    // Smart Pattern Interface Binding (Client-side)
    // ============================================================

    @SideOnly(Side.CLIENT)
    private static void handleSmartPatternBindClient(EntityPlayer player, ItemStack stack, BlockPos interfacePos, World world) {
        List<SelectionEntry> selections = getSelections(stack);
        if (selections.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.umc.msg.no_selections"));
            return;
        }

        SelectionEntry entry = null;
        for (SelectionEntry e : selections) {
            if (e.dim == world.provider.getDimension()) {
                entry = e;
                break;
            }
        }
        if (entry == null) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.smart_pattern_interface.bind_wrong_dim"));
            return;
        }

        net.minecraft.block.state.IBlockState targetState = world.getBlockState(entry.pos);
        net.minecraft.block.Block targetBlock = targetState.getBlock();
        int targetMeta = targetBlock.getMetaFromState(targetState);
        String blockId = targetBlock.getRegistryName().toString() + "@" + targetMeta;

        // 黑名单检查
        if (com.github.aeddddd.ae2enhanced.integration.jei.JEIRecipeHelper.isBlacklisted(blockId)) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.smart_pattern_interface.bind_blacklisted", blockId));
            return;
        }

        // JEI 查询
        if (!com.github.aeddddd.ae2enhanced.integration.jei.JEIRecipeHelper.isJeiAvailable()) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.smart_pattern_interface.bind_no_jei"));
            return;
        }

        java.util.List<com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartRecipe> recipes =
                com.github.aeddddd.ae2enhanced.integration.jei.JEIRecipeHelper.getRecipesForBlock(blockId);
        if (recipes.isEmpty()) {
            player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.smart_pattern_interface.bind_no_recipes", blockId));
            return;
        }

        com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData data =
                new com.github.aeddddd.ae2enhanced.crafting.smartpattern.SmartPatternData(
                        java.util.UUID.randomUUID(), blockId, recipes);
        data.detectConflicts();

        AE2Enhanced.network.sendToServer(
                new com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternBind(
                        interfacePos, data.toNBT(), entry.pos, entry.dim));
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.smart_pattern_interface.bind_success",
                recipes.size(), blockId));
    }

    // ============================================================
    // Server-side fallback
    // ============================================================

    // ============================================================
    // Tooltip
    // ============================================================

    @SideOnly(Side.CLIENT)
    private static String buildOptionSegment(ItemStack stack, int option, String key) {
        String name = I18n.format(key);
        // 开=绿色,关=灰色删除线
        return hasOption(stack, option) ? "§a" + name : "§8§m" + name;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        boolean detail = com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.isTooltipDetailDown();

        // ===== 常驻概要(未按详情键时仅显示这些) =====
        if (hasConfig(stack)) {
            tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.source",
                    getLocalizedConfigName(stack)));
        } else {
            tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.no_config"));
        }
        int summaryCount = getSelectionCount(stack);
        if (summaryCount > 0) {
            tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.selections", summaryCount));
        }
        if (hasNetworkBinding(stack)) {
            tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.bound_network_simple"));
        }
        if (!detail) {
            tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.hold_detail",
                    com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.displayName(
                            com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.TOOLTIP_DETAIL)));
            return;
        }

        // ===== 以下仅在按住详情键时显示 =====
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.separator"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.section_storage"));
        if (hasConfig(stack)) {
            NBTTagCompound config = getConfig(stack);
            NBTTagCompound data = config.getCompoundTag("data");
            if (data.hasKey("ae2e:upgrades")) {
                NBTTagList upgrades = data.getTagList("ae2e:upgrades", 10);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < upgrades.tagCount(); i++) {
                    NBTTagCompound tag = upgrades.getCompoundTagAt(i);
                    ItemStack upgradeStack = new ItemStack(tag);
                    if (!upgradeStack.isEmpty()) {
                        if (sb.length() > 0) sb.append(", ");
                        sb.append(upgradeStack.getDisplayName());
                        if (upgradeStack.getCount() > 1) {
                            sb.append("×").append(upgradeStack.getCount());
                        }
                    }
                }
                if (sb.length() > 0) {
                    tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.upgrades_detail", sb.toString()));
                }
            }
        }

        if (hasAreaCorner(stack)) {
            tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.area_pending"));
        }
        if (hasBinding(stack)) {
            NBTTagCompound binding = getBinding(stack);
            BlockPos bp = BlockPos.fromLong(binding.getLong("pos"));
            tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.bound_network",
                    bp.getX(), bp.getY(), bp.getZ()));
        }

        // ===== 第二组：模式与粘贴选项 =====
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.separator"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.section_settings"));
        CopyMode copyMode = getCopyMode(stack);
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.copy_mode",
                I18n.format("item.ae2enhanced.universal_memory_card.tooltip.copy_mode." + copyMode.name().toLowerCase())));
        SelectMode selectMode = getSelectMode(stack);
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.select_mode",
                I18n.format("item.ae2enhanced.universal_memory_card.tooltip.select_mode." + selectMode.name().toLowerCase())));

        // 粘贴选项逐项着色:开=绿,关=灰删除线
        String opts = buildOptionSegment(stack, OPT_UPGRADES, "item.ae2enhanced.universal_memory_card.tooltip.option.upgrades")
                + "§7 | " + buildOptionSegment(stack, OPT_FACING, "item.ae2enhanced.universal_memory_card.tooltip.option.facing")
                + "§7 | " + buildOptionSegment(stack, OPT_SIDES, "item.ae2enhanced.universal_memory_card.tooltip.option.sides")
                + "§7 | " + buildOptionSegment(stack, OPT_REDSTONE, "item.ae2enhanced.universal_memory_card.tooltip.option.redstone");
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.options", opts));

        // ===== 第三组：操作提示(键名为实际绑定,可在控制菜单改绑) =====
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.separator"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.section_actions"));
        String selectKey = com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.displayName(
                com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.SELECT_MODIFIER);
        String clearKey = com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.displayName(
                com.github.aeddddd.ae2enhanced.client.handler.UMCKeyBindings.CLEAR_MODIFIER);
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.sneak"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.use"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.ctrl", selectKey,
                I18n.format("item.ae2enhanced.universal_memory_card.tooltip.select_mode." + selectMode.name().toLowerCase())));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.bind"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.alt", clearKey));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.air"));

        if (world != null && world.isRemote) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
            net.minecraft.util.math.RayTraceResult ray = mc.objectMouseOver;
            if (ray != null && ray.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK) {
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(ray.getBlockPos());
                if (te instanceof com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface) {
                    com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface source = (com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface) te;
                    int boundCount = source.getInterfaceDuality().getBindings().size();
                    tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.separator"));
                    tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.central_bindings", boundCount));
                }
            }
        }
    }
}
