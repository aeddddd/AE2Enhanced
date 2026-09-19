package com.github.aeddddd.ae2enhanced.util.memorycard.handler.rftools;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.IMemoryCardHandler;
import com.github.aeddddd.ae2enhanced.util.memorycard.api.PasteResult;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fml.common.Loader;

/**
 * RFTools 自动合成器(Crafter T1/T2/T3)的配置复制粘贴 Handler.
 *
 * <p>支持的设备(类名前缀 {@code mcjty.rftools.blocks.crafter.CrafterBlockTileEntity}):</p>
 * <ul>
 *   <li>{@code Recipes} - 全部合成配方.配方网格为幽灵物品引用,粘贴不消耗实物;
 *       每配方含 {@code Keep}(keepOne) 与 {@code Int}(craftMode 序号) 选项</li>
 *   <li>{@code GItems} - Remember/Forget 记忆布局(幽灵过滤物品)</li>
 *   <li>{@code speedMode} - 速度模式(0=Slow/1=Fast)</li>
 *   <li>{@code rsMode} - 红石模式(0=Ignored/1=Off/2=On)</li>
 * </ul>
 *
 * <p>排除 {@code Items}(输入/输出缓冲区真实物品 + 过滤模块)、{@code Energy}、
 * {@code infused}、{@code owner}/{@code secChannel} 等运行时/个体状态.</p>
 *
 * <p>合并陷阱: {@code readRecipesFromNBT} 只按下标覆盖配方数组,不清空多余旧配方
 * (8 配方机器粘贴 2 配方快照会残留旧配方 3-8).粘贴前用空配方将快照列表
 * 补齐到目标机器的配方数.等级间(T1/T2/T3)通过 dataType 类名精确校验禁止互贴.</p>
 *
 * <p>实现仅操作 NBT,不直接引用 RFTools 类,满足反射隔离约定.</p>
 */
public class RFToolsCrafterHandler implements IMemoryCardHandler {

    private static final boolean AVAILABLE;

    private static final String CLASS_PREFIX = "mcjty.rftools.blocks.crafter.CrafterBlockTileEntity";

    /** 配置键(存在才复制). */
    private static final String[] CONFIG_KEYS = {
            "GItems", "speedMode", "rsMode"
    };

    static {
        AVAILABLE = Loader.isModLoaded("rftools");
    }

    @Override
    public boolean canHandle(Object target) {
        return AVAILABLE && target instanceof TileEntity
                && target.getClass().getName().startsWith(CLASS_PREFIX);
    }

    @Override
    public NBTTagCompound copy(Object target) {
        TileEntity tile = (TileEntity) target;
        NBTTagCompound output = new NBTTagCompound();

        try {
            NBTTagCompound fullNbt = tile.writeToNBT(new NBTTagCompound());

            if (fullNbt.hasKey("Recipes")) {
                output.setTag("Recipes", fullNbt.getTagList("Recipes", 10));
            }
            for (String key : CONFIG_KEYS) {
                if (fullNbt.hasKey(key)) {
                    output.setTag(key, fullNbt.getTag(key));
                }
            }
            output.setString("dataType", target.getClass().getName());
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to copy RFTools crafter config", e);
        }

        return output;
    }

    @Override
    public PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player) {
        TileEntity tile = (TileEntity) target;

        try {
            NBTTagCompound currentNbt = tile.writeToNBT(new NBTTagCompound());

            // 类型校验:要求完全同等级(TE 类名区分 T1/T2/T3)
            if (data.hasKey("dataType") && !data.getString("dataType").equals(target.getClass().getName())) {
                return PasteResult.INVALID_MACHINE;
            }

            // ===== 配方(整体替换 + 空配方补齐,避免残留旧配方) =====
            if (data.hasKey("Recipes")) {
                NBTTagList source = data.getTagList("Recipes", 10);
                int targetCount = currentNbt.hasKey("Recipes")
                        ? currentNbt.getTagList("Recipes", 10).tagCount() : 0;
                NBTTagList merged = source.copy();
                while (merged.tagCount() < targetCount) {
                    merged.appendTag(createEmptyRecipe());
                }
                currentNbt.setTag("Recipes", merged);
            }

            // ===== 通用配置 =====
            for (String key : CONFIG_KEYS) {
                if (data.hasKey(key)) {
                    currentNbt.setTag(key, data.getTag(key));
                }
            }

            tile.readFromNBT(currentNbt);
            tile.markDirty();
            if (tile.getWorld() != null) {
                tile.getWorld().notifyBlockUpdate(tile.getPos(),
                        tile.getWorld().getBlockState(tile.getPos()),
                        tile.getWorld().getBlockState(tile.getPos()), 3);
            }

            return PasteResult.SUCCESS;
        } catch (Exception e) {
            AE2Enhanced.LOGGER.warn("[AE2E] Failed to paste RFTools crafter config", e);
            return PasteResult.FAILED;
        }
    }

    /**
     * 构造空配方(与 CraftingRecipe 序列化格式一致):
     * 3×3 空网格 + 空产出 + Keep:0 + Int:0(EXT 模式).
     */
    private static NBTTagCompound createEmptyRecipe() {
        NBTTagCompound recipe = new NBTTagCompound();
        NBTTagList grid = new NBTTagList();
        for (int i = 0; i < 9; i++) {
            grid.appendTag(new NBTTagCompound());
        }
        recipe.setTag("Items", grid);
        recipe.setTag("Result", new NBTTagCompound());
        recipe.setByte("Keep", (byte) 0);
        recipe.setByte("Int", (byte) 0);
        return recipe;
    }

    // ===== 键分类声明(粘贴选项过滤用) =====

    @Override
    public java.util.Set<String> getRedstoneKeys() {
        return new java.util.HashSet<>(java.util.Arrays.asList("rsMode"));
    }

    @Override
    public String getDisplayName(Object target) {
        if (target instanceof TileEntity) {
            return ((TileEntity) target).getBlockType().getLocalizedName();
        }
        return target.getClass().getSimpleName();
    }
}
