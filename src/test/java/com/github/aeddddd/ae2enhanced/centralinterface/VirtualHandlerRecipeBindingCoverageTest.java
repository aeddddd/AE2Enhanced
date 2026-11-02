package com.github.aeddddd.ae2enhanced.centralinterface;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualHandlerRecipeBindingCoverageTest {
    private static final Path HANDLER_ROOT = Paths.get("src/main/java/com/github/aeddddd/ae2enhanced/centralinterface/handler");
    private static final List<String> VIRTUAL_HANDLERS = Arrays.asList(
            "bloodmagic/BloodMagicHandler.java",
            "botania/BotaniaHandler.java",
            "extendedcrafting/CompressorHandler.java",
            "extendedcrafting/EnderCrafterHandler.java",
            "extendedcrafting/ExtendedCraftingTableHandler.java",
            "extendedcrafting/ExtendedCraftingHandler.java",
            "avaritia/ExtremeCraftingTableHandler.java",
            "astralsorcery/AstralSorceryHandler.java",
            "thaumcraft/ThaumcraftHandler.java",
            "thaumcraft/ThaumcraftCrucibleHandler.java");

    @Test
    void everyVirtualHandlerUsesBoundOutputsInsteadOfPatternOutputs() throws IOException {
        for (String handler : VIRTUAL_HANDLERS) {
            String source = new String(Files.readAllBytes(HANDLER_ROOT.resolve(handler)), StandardCharsets.UTF_8);
            assertThat(source).as(handler).contains("RecipeBinding");
            assertThat(source).as(handler).contains("binding.getOutputs()");
            assertThat(source).as(handler).doesNotContain("scaleOutputsByCount(outputs, count)");
        }
    }
}
