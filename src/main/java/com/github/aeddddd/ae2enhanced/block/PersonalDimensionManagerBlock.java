package com.github.aeddddd.ae2enhanced.block;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

import com.github.aeddddd.ae2enhanced.blockentity.PersonalDimensionManagerBlockEntity;
import com.github.aeddddd.ae2enhanced.common.menu.PersonalDimensionManagerMenu;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
import com.github.aeddddd.ae2enhanced.network.ModNetwork;
import com.github.aeddddd.ae2enhanced.network.packet.PersonalDimManagerStatePacket;

/**
 * 个人维度管理器：集中管理放置者个人维度的规则、权限与地板预设预览.
 *
 * <p>首次由玩家放置时绑定所有者;仅所有者、拥有 MANAGE_RULES 权限的玩家
 * 或服务器管理员（权限等级 ≥ 2）可打开管理界面.</p>
 */
public class PersonalDimensionManagerBlock extends Block implements EntityBlock {

    public PersonalDimensionManagerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PersonalDimensionManagerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        if (!level.isClientSide() && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof PersonalDimensionManagerBlockEntity be) {
            be.setOwner(player.getUUID());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof PersonalDimensionManagerBlockEntity be)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        UUID owner = be.getOwner();
        if (owner == null) {
            // 旧方块或数据缺失：首个使用者认领
            be.setOwner(player.getUUID());
            owner = player.getUUID();
        }
        if (!PersonalDimensionManager.canManage(serverPlayer, owner)) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("chat.ae2enhanced.personal_dimension.no_manage"));
            return InteractionResult.FAIL;
        }
        final UUID ownerId = owner;
        NetworkHooks.openScreen(serverPlayer, new SimpleMenuProvider(
                (id, inv, p) -> new PersonalDimensionManagerMenu(id, inv, pos, ownerId),
                Component.translatable("gui.ae2enhanced.personal_dimension_manager")),
                buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeUUID(ownerId);
                });
        // 打开后立即同步完整状态（规则/权限/预设预览）
        ModNetwork.CHANNEL.sendTo(
                PersonalDimManagerStatePacket.create(serverPlayer.server, pos, ownerId),
                serverPlayer.connection.connection,
                net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT);
        return InteractionResult.SUCCESS;
    }
}
