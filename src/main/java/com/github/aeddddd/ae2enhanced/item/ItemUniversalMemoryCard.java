package com.github.aeddddd.ae2enhanced.item;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.network.packet.PacketUMCAction;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.UMCCopyService;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.UMCPasteService;
import com.github.aeddddd.ae2enhanced.util.memorycard.core.UMCSelectionService;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 通用内存卡：复制/粘贴 AE2 设备配置(含升级卡),选取世界中的目标方块.
 *
 * 架构约定：
 * 业务逻辑已拆分到 UMCCopyService / UMCPasteService / UMCSelectionService.
 * 本类只保留：NBT 序列化、客户端事件分发、tooltip 渲染.
 */
public class ItemUniversalMemoryCard extends Item {

    private static final String NBT_CONFIG = "ae2e:umc_config";
    private static final String NBT_SELECTIONS = "ae2e:umc_selections";
    private static final String NBT_BINDING = "ae2e:umc_binding";
    private static final String NBT_MODE = "ae2e:umc_mode";
    public static final int GUI_ID = 10;

    public enum Mode {
        CONFIG_COPY
    }

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

        public SelectionEntry(BlockPos pos, int dim, String tileId, int side) {
            this.pos = pos;
            this.dim = dim;
            this.tileId = tileId;
            this.side = side;
        }

        public NBTTagCompound toNBT() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setLong("pos", pos.toLong());
            tag.setInteger("dim", dim);
            tag.setString("id", tileId);
            tag.setInteger("side", side);
            return tag;
        }

        public static SelectionEntry fromNBT(NBTTagCompound tag) {
            return new SelectionEntry(
                    BlockPos.fromLong(tag.getLong("pos")),
                    tag.getInteger("dim"),
                    tag.getString("id"),
                    tag.hasKey("side") ? tag.getInteger("side") : -1
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
    // Mode Helpers
    // ============================================================

    public static Mode getMode(ItemStack stack) {
        if (!stack.hasTagCompound()) return Mode.CONFIG_COPY;
        int ordinal = stack.getTagCompound().getInteger(NBT_MODE);
        if (ordinal < 0 || ordinal >= Mode.values().length) return Mode.CONFIG_COPY;
        return Mode.values()[ordinal];
    }

    public static void setMode(ItemStack stack, Mode mode) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        stack.getTagCompound().setInteger(NBT_MODE, mode.ordinal());
    }

    public static boolean isUniversalMemoryCard(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemUniversalMemoryCard;
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
                    boolean isCtrl = net.minecraft.client.Minecraft.getMinecraft().gameSettings.keyBindSprint.isKeyDown();

                    net.minecraft.tileentity.TileEntity te = event.getWorld().getTileEntity(event.getPos());
                    boolean isCentralInterface = te instanceof com.github.aeddddd.ae2enhanced.tile.TileCentralMEInterface;
                    boolean isSmartPatternInterface = te instanceof com.github.aeddddd.ae2enhanced.tile.TileSmartPatternInterface;
                    boolean isRecycler = te instanceof com.github.aeddddd.ae2enhanced.tile.TileMENetworkRecycler;

                    boolean isAlt = org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LMENU)
                            || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RMENU);

                    // 智能样板接口绑定：客户端查询 JEI 后直接发送 PacketSmartPatternBind
                    if (isSmartPatternInterface && !isSneaking && !isCtrl && !isAlt) {
                        handleSmartPatternBindClient(player, stack, event.getPos(), event.getWorld());
                        event.setCanceled(true);
                        event.setCancellationResult(EnumActionResult.FAIL);
                        return;
                    }

                    PacketUMCAction.ActionType type;
                    if (isRecycler && isAlt && !isSneaking && !isCtrl) {
                        type = PacketUMCAction.ActionType.CLEAR_RECYCLER_BINDINGS;
                    } else if (isRecycler && !isSneaking && !isCtrl) {
                        type = PacketUMCAction.ActionType.BIND_RECYCLER;
                    } else if (isCentralInterface && isAlt && !isSneaking && !isCtrl) {
                        type = PacketUMCAction.ActionType.CLEAR_BINDINGS;
                    } else if (isCentralInterface && !isSneaking && !isCtrl) {
                        type = PacketUMCAction.ActionType.BIND_SOURCE;
                    } else if (isCtrl) {
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

        switch (message.getType()) {
            case COPY:
                UMCCopyService.handleCopy(player, stack, message.getPos(), message.getFace());
                break;
            case PASTE:
                UMCPasteService.handlePaste(player, stack, message.getPos(), message.getFace());
                break;
            case SELECT:
                UMCSelectionService.handleSelect(player, stack, message.getPos(), message.getFace());
                break;
            case CLEAR_CONFIG:
                clearConfig(stack);
                break;
            case CLEAR_SELECTIONS:
                clearSelections(stack);
                break;
            case REMOVE_SELECTION:
                removeSelection(stack, message.getIndex());
                break;
            case OPEN_GUI:
                player.openGui(AE2Enhanced.instance, GUI_ID, player.world,
                        (int) player.posX, (int) player.posY, (int) player.posZ);
                break;
            case BIND_SOURCE:
                UMCSelectionService.handleBindSource(player, stack, message.getPos(), message.getFace());
                break;
            case CLEAR_BINDINGS:
                UMCSelectionService.handleClearBindings(player, message.getPos());
                break;
            case BIND_RECYCLER:
                UMCSelectionService.handleBindRecycler(player, stack, message.getPos(), message.getFace());
                break;
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
                new com.github.aeddddd.ae2enhanced.network.packet.PacketSmartPatternBind(interfacePos, data.toNBT()));
        player.sendMessage(new TextComponentTranslation("gui.ae2enhanced.smart_pattern_interface.bind_success",
                recipes.size(), blockId));
    }

    // ============================================================
    // Server-side fallback
    // ============================================================

    // ============================================================
    // Tooltip
    // ============================================================

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.header"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.separator"));

        if (hasConfig(stack)) {
            NBTTagCompound config = getConfig(stack);
            tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.source", config.getString("name")));

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
        } else {
            tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.no_config"));
        }

        int count = getSelectionCount(stack);
        if (count > 0) {
            tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.selections", count));
        }

        Mode mode = getMode(stack);
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.mode", I18n.format("item.ae2enhanced.universal_memory_card.tooltip.mode." + mode.name().toLowerCase())));

        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.separator"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.sneak"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.use"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.ctrl"));
        tooltip.add(I18n.format("item.ae2enhanced.universal_memory_card.tooltip.alt"));
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
