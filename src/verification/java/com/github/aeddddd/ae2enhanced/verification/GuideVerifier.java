package com.github.aeddddd.ae2enhanced.verification;

import com.github.aeddddd.ae2enhanced.chamber.ChamberRecipe;
import com.github.aeddddd.ae2enhanced.chamber.RecipeCatalog;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipe;
import com.github.aeddddd.ae2enhanced.crafting.BlackHoleRecipeRegistry;
import com.github.aeddddd.ae2enhanced.guide.script.ScriptGuidePage;
import com.github.aeddddd.ae2enhanced.guide.script.ScriptGuideService;
import com.github.aeddddd.ae2enhanced.guide.script.ScriptGuideSnapshot;
import com.github.aeddddd.ae2enhanced.network.PacketCatalog;
import org.apache.logging.log4j.LogManager;

import java.util.HashMap;
import java.util.Map;

/** 真实 CraftTweaker 脚本执行后的 WP6 服务端验收。 */
final class GuideVerifier {
    private GuideVerifier() {
    }

    static void verify() throws Exception {
        require(BlackHoleRecipeRegistry.getRecipes().stream()
                .map(BlackHoleRecipe::getId).anyMatch("ae2e:wp6_black_hole"::equals),
                "CraftTweaker explicit black hole id was not registered");
        ChamberRecipe chamber = RecipeCatalog.customChamberRecipes().stream()
                .filter(recipe -> recipe.getId().equals("ae2e:wp6_chamber"))
                .findFirst().orElseThrow(() -> new IllegalStateException("CraftTweaker chamber recipe missing"));
        require(chamber.getInputGroups().size() == 2, "CraftTweaker chamber input group count mismatch");
        require(chamber.getInputGroups().get(0).getTemplates().size() == 2,
                "CraftTweaker ingredient alternatives were not preserved");
        require(chamber.getInputGroups().get(1).getCount() == 2,
                "CraftTweaker ingredient amount was not preserved");

        ScriptGuideSnapshot snapshot = ScriptGuideService.serverRepository().snapshot();
        Map<String, String> pages = new HashMap<>();
        for (ScriptGuidePage page : snapshot.getPages()) {
            pages.put(page.getPageId() + "#" + page.getLanguage(), page.getContent());
        }
        require(pages.containsKey("mymod/external.md#"), "External Markdown was not loaded by Guide.load");
        require(pages.get("mymod/external.md#").contains("External Guide Body"),
                "External Markdown body mismatch");
        require(pages.containsKey("mymod/inline.md#"), "Guide.addPage did not create a page");
        require(pages.containsKey("mymod/inline.md#zh_cn"), "Guide.addTranslation did not create a translation");
        require(pages.get("mymod/inline.md#zh_cn").contains("中文正文"), "Guide translation body mismatch");
        require(snapshot.encode().length <= 2 * 1024 * 1024, "Guide snapshot exceeds sync budget");
        require(PacketCatalog.NEXT_DISCRIMINATOR >= 65, "Guide protocol discriminator was not appended");
        LogManager.getLogger("ae2enhancedsmoke").info("GUIDE_PASS revision={} entries={} bytes={}",
                snapshot.getRevision(), snapshot.getPages().size(), snapshot.encode().length);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
