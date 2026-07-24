package com.github.aeddddd.ae2enhanced.gametest;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.dimension.PersonalDimensionManager;
/**
 * 个人维度动态创建的冒烟测试：验证运行时 ServerLevel 注入、区块生成与地板/基岩铺设.
 */
@GameTestHolder(AE2Enhanced.MOD_ID)
public class PersonalDimensionGameTest {

    @GameTest(template = "empty")
    public static void personalDimensionCreation(GameTestHelper helper) {
        try {
            AE2Enhanced.LOGGER.info("[AE2E] personalDimensionCreation test started");
            MinecraftServer server = helper.getLevel().getServer();
            UUID owner = UUID.randomUUID();

            ServerLevel level = PersonalDimensionManager.getOrCreateDimension(server, owner);
            helper.assertTrue(level != null, "个人维度创建失败");

            ResourceKey<Level> key = PersonalDimensionManager.dimensionKeyFor(owner);
            helper.assertTrue(server.getLevel(key) == level, "个人维度未注入服务器维度表");
            helper.assertTrue(PersonalDimensionManager.isPersonalDimension(key), "维度键未被识别为个人维度");

            // 强制生成一个区块并校验地板与基岩垫层
            BlockState floor = level.getBlockState(new BlockPos(1, 64, 1));
            helper.assertTrue(!floor.isAir(), "个人维度地板未生成");
            BlockState below = level.getBlockState(new BlockPos(1, 63, 1));
            helper.assertTrue(below.is(Blocks.BEDROCK), "地板下方基岩垫层未生成");
            // 地板上方应为空气（空世界）
            BlockState above = level.getBlockState(new BlockPos(1, 65, 1));
            helper.assertTrue(above.isAir(), "个人维度地板上方应为空气");

            helper.succeed();
        } catch (Throwable t) {
            AE2Enhanced.LOGGER.error("[AE2E] personalDimensionCreation test error", t);
            helper.fail(t.getMessage() != null ? t.getMessage() : t.toString());
        }
    }
}
