package com.github.aeddddd.ae2enhanced.container;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.junit.jupiter.api.Test;

import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockRect;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockSize;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.DockWorkspace;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.FloatingRoot;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.LeafNode;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleCatalog;
import com.github.aeddddd.ae2enhanced.client.gui.dock.model.ModuleMetrics;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.DockLayoutCodec;
import com.github.aeddddd.ae2enhanced.client.gui.dock.workspace.LegacyMigrationContext;

/**
 * 阶段 4a：服务端对上传布局的只读校验。
 *
 * <p>最有价值的一条断言是"客户端 codec 产出的东西服务端一定收"——服务端用的是占位尺寸目录，
 * 只要模块 id 集合一致就不会误拒。其余是长度与垃圾载荷的拒绝。</p>
 */
class SplicedTermLayoutValidatorTest {

    /** 与客户端同构的目录（模块 id 相同、尺寸用真实值即可，校验不看尺寸）。 */
    private static DockLayoutCodec clientCodec() {
        Map<String, ModuleMetrics> entries = new LinkedHashMap<String, ModuleMetrics>();
        for (String moduleId : SplicedTermModules.moduleIds()) {
            entries.put(moduleId, new ModuleMetrics(new DockSize(64, 40), new DockSize(176, 96)));
        }
        ModuleCatalog catalog = new ModuleCatalog(entries);
        return new DockLayoutCodec(catalog,
                LegacyMigrationContext.currentDockDefaults(640, 360), UnaryOperator.identity());
    }

    /** 客户端 codec 产出的真实布局：每个模块一个窗口（校验器只要求"能解码"）。 */
    private static String clientEncodedLayout() {
        java.util.List<FloatingRoot> roots = new java.util.ArrayList<FloatingRoot>();
        int x = 4;
        for (String moduleId : SplicedTermModules.moduleIds()) {
            roots.add(new FloatingRoot("root:" + moduleId, new DockRect(x, 4, 176, 96),
                    new LeafNode("leaf:" + moduleId, moduleId, true)));
            x += 12;
        }
        DockWorkspace workspace = new DockWorkspace(roots);
        return clientCodec().encode(workspace);
    }

    @Test
    void acceptsWhatTheClientCodecProduces() {
        String json = clientEncodedLayout();
        assertTrue(SplicedTermLayoutValidator.isValid(json),
                "服务端占位目录必须能把客户端自产的布局解码出来");
    }

    @Test
    void rejectsEmptyAndNull() {
        assertFalse(SplicedTermLayoutValidator.isValid(null));
        assertFalse(SplicedTermLayoutValidator.isValid(""));
    }

    @Test
    void rejectsOversizedPayload() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i <= OmniTerminalLayoutNbt.MAX_LAYOUT_BYTES; i++) {
            builder.append(' ');
        }
        assertFalse(SplicedTermLayoutValidator.isValid(builder.toString()));
    }

    @Test
    void rejectsGarbage() {
        assertFalse(SplicedTermLayoutValidator.isValid("not json at all"));
        assertFalse(SplicedTermLayoutValidator.isValid("{\"version\":6}"));
        assertFalse(SplicedTermLayoutValidator.isValid("[]"));
    }

    @Test
    void acceptsAnUnknownModuleBecauseReconcileDropsIt() {
        // 未知模块叶子由 reconcile 丢弃（评估文档 §2.3 的既有行为），不算校验失败
        String json = "{\"version\":6,\"roots\":[{\"rootId\":\"root:x\",\"bounds\":{\"x\":0,\"y\":0,\"width\":100,\"height\":100},"
                + "\"content\":{\"type\":\"leaf\",\"nodeId\":\"leaf:x\",\"moduleId\":\"some_removed_module\",\"visible\":true}}],"
                + "\"policies\":{}}";
        assertTrue(SplicedTermLayoutValidator.isValid(json));
    }
}
