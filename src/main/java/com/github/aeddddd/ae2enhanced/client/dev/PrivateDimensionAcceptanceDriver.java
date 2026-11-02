package com.github.aeddddd.ae2enhanced.client.dev;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.client.ClientPersonalDimensionRules;
import com.github.aeddddd.ae2enhanced.dimension.DimensionOwner;
import com.github.aeddddd.ae2enhanced.dimension.PrivateDimensionInfo;
import com.github.aeddddd.ae2enhanced.dimension.dev.PrivateDimensionHarness;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** 隔离双客户端私域验收驱动；系统属性未开启时不注册。 */
public final class PrivateDimensionAcceptanceDriver {
    private final boolean owner = !Boolean.getBoolean("ae2e.harness.e5Peer");
    private int ticks;
    private int state;
    private int actionTick;
    private int connectAttempts;
    private boolean done;

    public static void register() {
        if (PrivateDimensionHarness.enabled()) {
            PrivateDimensionHarness.registerFloorStyle();
            MinecraftForge.EVENT_BUS.register(new PrivateDimensionAcceptanceDriver());
        }
    }

    private static boolean floorReady(Minecraft mc) {
        BlockPos floor = mc.player.getPosition().down();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos pos = floor.add(x, 0, z);
                IBlockState state = mc.world.getBlockState(pos);
                if (state.getBlock() != Blocks.CONCRETE || state.getBlock().getMetaFromState(state) != 11) {
                    return false;
                }
                if (mc.world.getBlockState(pos.down()).getBlock() == Blocks.BEDROCK
                        || mc.world.getBlockState(pos.down(2)).getBlock() == Blocks.BEDROCK
                        || mc.world.getLight(pos.up()) < 14) {
                    return false;
                }
            }
        }
        return true;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            tryConnect(mc);
            return;
        }
        if (mc.player == null) {
            return;
        }
        ticks++;
        if (ticks > 3600) {
            fail("timeout in state " + state);
            return;
        }
        if (state == 0) {
            if (ticks >= 40 && (actionTick == 0 || ticks - actionTick >= 100)) {
                actionTick = ticks;
                mc.player.sendChatMessage("/ae2e pd mode team");
                mc.player.sendChatMessage("/ae2e pd enter team");
            }
            if (com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager.isPersonalDimension(
                    mc.player.dimension)) {
                state = 1;
            }
            return;
        }
        if (state == 1) {
            if (owner) {
                mc.player.sendChatMessage("/ae2e pd floor style " + PrivateDimensionHarness.STYLE_ID);
                mc.player.sendChatMessage("/ae2e pd floor color 1 blue");
                mc.player.sendChatMessage("/ae2e pd floor apply");
            } else {
                mc.player.sendChatMessage("/ae2e pd floor color 1 red");
            }
            state = 2;
            return;
        }
        if (state == 2) {
            PrivateDimensionInfo info = ClientPersonalDimensionRules.info();
            if (ticks % 200 == 0) {
                BlockPos sample = mc.player.getPosition().down();
                IBlockState state = mc.world.getBlockState(sample);
                AE2Enhanced.LOGGER.info("PRIVATE_DIM_DEBUG user={} scope={} revision={} canManage={} floor={}/{}",
                        mc.player.getName(), info.scope, info.floorRevision, info.canManage,
                        state.getBlock(), state.getBlock().getMetaFromState(state));
            }
            if (info.scope != DimensionOwner.Scope.TEAM || info.floorRevision < 2L || info.canManage == owner
                    && !floorReady(mc)) {
                return;
            }
            if (info.canManage != owner) {
                fail("management permission mismatch: " + info.canManage);
                return;
            }
            if (!floorReady(mc)) {
                return;
            }
            AE2Enhanced.LOGGER.info("PRIVATE_DIM_CLIENT_PASS user={} role={} revision={} concrete=blue bedrock=removed",
                    mc.player.getName(), owner ? "OWNER" : "MEMBER", info.floorRevision);
            if (owner) {
                finish();
            } else {
                state = 3;
            }
            return;
        }
        if (state == 3 && mc.player.dimension == 0) {
            AE2Enhanced.LOGGER.info("PRIVATE_DIM_LEFT_CLIENT_PASS user={} safeReturn=true", mc.player.getName());
            finish();
        }
    }

    private void tryConnect(Minecraft mc) {
        if (connectAttempts >= 3 || !(mc.currentScreen instanceof net.minecraft.client.gui.GuiMainMenu
                || mc.currentScreen instanceof net.minecraft.client.gui.GuiDisconnected)) {
            return;
        }
        connectAttempts++;
        String address = System.getProperty("ae2e.harness.serverAddress", "localhost:25566");
        mc.displayGuiScreen(new net.minecraft.client.multiplayer.GuiConnecting(mc.currentScreen, mc,
                new net.minecraft.client.multiplayer.ServerData("private-dimension-harness", address, false)));
    }

    private void fail(String reason) {
        AE2Enhanced.LOGGER.error("PRIVATE_DIM_HARNESS_FAILED user={} reason={}",
                Minecraft.getMinecraft().player.getName(), reason);
        done = true;
    }

    private void finish() {
        AE2Enhanced.LOGGER.info("PRIVATE_DIM_CLIENT_DONE user={}", Minecraft.getMinecraft().player.getName());
        done = true;
    }
}
