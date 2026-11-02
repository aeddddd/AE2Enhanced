package com.github.aeddddd.ae2enhanced.integration.crafttweaker;

import com.github.aeddddd.ae2enhanced.guide.script.ScriptGuideService;
import crafttweaker.CraftTweakerAPI;
import crafttweaker.IAction;
import crafttweaker.annotations.ZenRegister;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

/** CraftTweaker 指南入口；只调用服务端中性仓库，不依赖客户端指南类。 */
@ZenRegister
@ZenClass("mods.ae2enhanced.Guide")
public class GuideCraftTweaker {

    @ZenMethod
    public static void load(String path) {
        CraftTweakerAPI.apply(new GuideAction("Load guide Markdown " + path,
                () -> ScriptGuideService.server().validateLoad(path),
                () -> ScriptGuideService.server().load(path)));
    }

    @ZenMethod
    public static void loadDirectory(String path) {
        CraftTweakerAPI.apply(new GuideAction("Load guide directory " + path,
                () -> ScriptGuideService.server().validateDirectory(path),
                () -> ScriptGuideService.server().loadDirectory(path)));
    }

    @ZenMethod
    public static void addPage(String pageId, String title, String body) {
        addPage(pageId, title, null, 0, null, new String[0], body);
    }

    @ZenMethod
    public static void addPage(String pageId, String title, String parent, int position,
            String icon, String[] itemIds, String body) {
        CraftTweakerAPI.apply(new GuideAction("Add guide page " + pageId,
                () -> ScriptGuideService.server().validateAddPage(pageId, title, parent, position, icon, itemIds, body),
                () -> ScriptGuideService.server().addPage(pageId, title, parent, position, icon, itemIds, body)));
    }

    @ZenMethod
    public static void addTranslation(String pageId, String language, String body) {
        CraftTweakerAPI.apply(new GuideAction("Add guide translation " + pageId + " [" + language + "]",
                () -> ScriptGuideService.server().validateTranslation(pageId, language, body),
                () -> ScriptGuideService.server().addTranslation(pageId, language, body)));
    }

    @ZenMethod
    public static void removePage(String pageId) {
        CraftTweakerAPI.apply(new GuideAction("Remove guide page " + pageId,
                () -> ScriptGuideService.validatePageId(pageId),
                () -> ScriptGuideService.server().removePage(pageId)));
    }

    private interface CheckedOperation {
        void run() throws Exception;
    }

    private static final class GuideAction implements IAction {
        private final String description;
        private final CheckedOperation validation;
        private final CheckedOperation operation;

        private GuideAction(String description, CheckedOperation validation, CheckedOperation operation) {
            this.description = description;
            this.validation = validation;
            this.operation = operation;
        }

        @Override
        public boolean validate() {
            try {
                validation.run();
                return true;
            } catch (Exception exception) {
                CraftTweakerAPI.logError("[AE2Enhanced] " + description + ": " + exception.getMessage());
                return false;
            }
        }

        @Override
        public void apply() {
            try {
                operation.run();
            } catch (Exception exception) {
                CraftTweakerAPI.logError("[AE2Enhanced] " + description + ": " + exception.getMessage());
            }
        }

        @Override
        public String describe() {
            return description;
        }

        @Override
        public String describeInvalid() {
            return description + " is invalid; see crafttweaker.log";
        }
    }
}
