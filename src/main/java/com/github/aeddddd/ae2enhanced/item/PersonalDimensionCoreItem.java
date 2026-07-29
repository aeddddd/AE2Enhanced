package com.github.aeddddd.ae2enhanced.item;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import net.minecraft.world.SimpleMenuProvider;

import com.github.aeddddd.ae2enhanced.menu.PersonalDimensionCreateMenu;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.dimension.PlayerDimEntry;

/**
 * 个人维度核心：便携进出个人维度的工具.
 *
 * <p>首次使用:打开创建向导,确定地板方案后才创建维度.
 * 之后右键（空气或方块）:记录返回点并进入个人维度；在维度内再次使用则返回记录点.
 * Shift+右键方块：在个人维度内绑定入口点.
 * 规则配置、权限管理与地板预设选择由个人维度管理器方块承担.</p>
 */
public class PersonalDimensionCoreItem extends Item {

    public PersonalDimensionCoreItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            return bindEntryPoint(context);
        }
        toggleTeleport((ServerPlayer) player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            toggleTeleport(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    private InteractionResult bindEntryPoint(UseOnContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        if (!PersonalDimensionManager.isPersonalDimension(context.getLevel().dimension())) {
            player.sendSystemMessage(
                    Component.translatable("chat.ae2enhanced.personal_dimension.bind_only_in_dim"));
            return InteractionResult.FAIL;
        }
        PersonalDimensionManager.setEntryPoint(player.server, player.getUUID(),
                context.getClickedPos().above());
        player.sendSystemMessage(Component.translatable("chat.ae2enhanced.personal_dimension.bound",
                context.getClickedPos().getX(), context.getClickedPos().getY() + 1,
                context.getClickedPos().getZ()));
        return InteractionResult.SUCCESS;
    }

    private static void toggleTeleport(ServerPlayer player) {
        ResourceKey<Level> ownKey = PersonalDimensionManager.dimensionKeyFor(player.getUUID());
        if (player.level().dimension().equals(ownKey)) {
            // 已在个人维度,返回上一次位置
            PersonalDimensionManager.teleportToReturnPoint(player);
            return;
        }
        PlayerDimEntry entry = PersonalDimensionManager.getEntry(player.server, player.getUUID());
        if (entry == null || !entry.created) {
            // 首次使用:打开创建向导,在预览界面确定地板方案后才创建维度
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (id, inv, p) -> new PersonalDimensionCreateMenu(id, inv, player.getUUID()),
                    Component.translatable("gui.ae2enhanced.pdim.create_title")),
                    buf -> PersonalDimensionCreateMenu.writePreviewData(buf, player.getUUID()));
            return;
        }
        // 记录当前位置并进入个人维度
        PersonalDimensionManager.setReturnPoint(player);
        ServerLevel level = PersonalDimensionManager.getOrCreateDimension(player);
        if (level == null) {
            player.sendSystemMessage(
                    Component.translatable("chat.ae2enhanced.personal_dimension.create_failed"));
            return;
        }
        PersonalDimensionManager.teleportToDimension(player, ownKey);
    }
}
