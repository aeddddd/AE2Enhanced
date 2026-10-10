package com.github.aeddddd.ae2enhanced.container;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleMetrics;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutCodec;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutFormatException;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.LegacyMigrationContext;

/**
 * 服务端对客户端上传的布局 JSON 做<strong>只读校验</strong>（阶段 4a）。
 *
 * <p>服务端不持有任何面板，也不把几何用于任何逻辑；这里做的是"这段 JSON 能不能被我们的 codec
 * 解码"——长度、版本、reconcile、节点数/深度（{@code DecodeBudget}）、坐标钳制全部由
 * {@link DockLayoutCodec} 的既有实现负责，解码结果<strong>直接丢弃</strong>，只把原始 JSON 回写 NBT。</p>
 *
 * <p>因此校验用的 {@link ModuleCatalog} 只需一个"模块 id 齐全"的占位目录：尺寸数值不参与校验结论
 * （新模块补默认窗的几何结果我们不要）。这也是服务端"零几何知识"这条红线的落点——它只认
 * "解码成功/失败"，不认坐标。</p>
 */
public final class SplicedTermLayoutValidator {

    private static final Logger LOGGER = LogManager.getLogger(SplicedTermLayoutValidator.class);

    /** 占位模块尺寸（校验用，不参与任何逻辑）。 */
    private static final DockSize PLACEHOLDER_MIN = new DockSize(1, 1);
    private static final DockSize PLACEHOLDER_DEFAULT = new DockSize(64, 64);
    /** 占位视口（迁移上下文需要正数）。 */
    private static final int PLACEHOLDER_VIEWPORT_W = 320;
    private static final int PLACEHOLDER_VIEWPORT_H = 240;

    private static final DockLayoutCodec VALIDATION_CODEC = createValidationCodec();

    private SplicedTermLayoutValidator() {
    }

    /**
     * 校验一段布局 JSON。
     *
     * @return 载荷可接受且能被 codec 解码时返回 {@code true}
     */
    public static boolean isValid(String json) {
        if (!OmniTerminalLayoutNbt.isAcceptable(json)) {
            return false;
        }
        try {
            VALIDATION_CODEC.decode(json);
            return true;
        } catch (DockLayoutFormatException | RuntimeException e) {
            // 只记一行：伪造包/版本不匹配都会走这里，不值得刷屏
            LOGGER.warn("拒绝 Omni 终端布局载荷({} 字符): {}", json.length(), e.getMessage());
            return false;
        }
    }

    private static DockLayoutCodec createValidationCodec() {
        Map<String, ModuleMetrics> entries = new LinkedHashMap<String, ModuleMetrics>();
        for (String moduleId : SplicedTermModules.moduleIds()) {
            entries.put(moduleId, new ModuleMetrics(PLACEHOLDER_MIN, PLACEHOLDER_DEFAULT));
        }
        ModuleCatalog catalog = new ModuleCatalog(entries);
        LegacyMigrationContext geometry =
                LegacyMigrationContext.currentDockDefaults(PLACEHOLDER_VIEWPORT_W, PLACEHOLDER_VIEWPORT_H);
        return new DockLayoutCodec(catalog, geometry, UnaryOperator.identity());
    }
}
