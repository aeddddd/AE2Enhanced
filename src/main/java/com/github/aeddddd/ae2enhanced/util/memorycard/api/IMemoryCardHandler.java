package com.github.aeddddd.ae2enhanced.util.memorycard.api;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Collections;
import java.util.Set;

/**
 * 通用内存卡配置复制粘贴的 Handler 接口.
 * 第一期实现 AE2PartHandler / AE2TileHandler,后续新增设备类型只需实现此接口并注册.
 *
 * <h2>规范架构(防止遗漏约定)</h2>
 * <ol>
 *   <li>每个 handler 有稳定 ID({@link #getId()}),复制时由 UMCCopyService 集中写入配置,
 *       粘贴时过滤器据此反查 handler 的键分类.</li>
 *   <li>handler 必须在自己的代码旁声明其产出的配置键分类
 *       ({@link #getUpgradeKeys()}/{@link #getFacingKeys()}/{@link #getSideKeys()}/{@link #getRedstoneKeys()}),
 *       使"新增配置键"与"粘贴选项过滤"在同一文件内可见,杜绝全局清单遗漏.</li>
 *   <li>渲染/展示元数据(方块 ID、网络状态)由 UMCCopyService 集中打标,handler 无需关心.</li>
 * </ol>
 */
public interface IMemoryCardHandler {

    /**
     * 此 Handler 是否能处理该目标.
     * @param target TileEntity 或 IPart
     */
    boolean canHandle(Object target);

    /**
     * 复制目标设备的完整配置(含升级槽).
     * @return 配置 NBT,返回 null 表示无法复制
     */
    NBTTagCompound copy(Object target);

    /**
     * 将配置粘贴到目标设备.
     * @return 粘贴结果
     */
    PasteResult paste(Object target, NBTTagCompound data, EntityPlayer player);

    /**
     * 获取目标设备的显示名称(用于内存卡 tooltip / GUI).
     */
    String getDisplayName(Object target);

    /**
     * 稳定 ID,随配置存入内存卡,粘贴时用于反查键分类.
     * 默认为类简单名;如需改类名请保持返回值不变.
     */
    default String getId() {
        return getClass().getSimpleName();
    }

    /** 本 handler 产出的"升级类"配置键(消耗物品的升级卡/套件/插件). */
    default Set<String> getUpgradeKeys() {
        return Collections.emptySet();
    }

    /** 本 handler 产出的"朝向"配置键. */
    default Set<String> getFacingKeys() {
        return Collections.emptySet();
    }

    /** 本 handler 产出的"侧面配置"键. */
    default Set<String> getSideKeys() {
        return Collections.emptySet();
    }

    /** 本 handler 产出的"红石控制"键. */
    default Set<String> getRedstoneKeys() {
        return Collections.emptySet();
    }
}
