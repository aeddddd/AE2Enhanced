import mods.ae2enhanced.BlackHole;
import mods.ae2enhanced.SingularityChamber;
import mods.ae2enhanced.Guide;

BlackHole.addRecipe("ae2e:wp6_black_hole", <minecraft:diamond>, [<minecraft:stone> * 2]);
SingularityChamber.addRecipeAlternatives("ae2e:wp6_chamber", <minecraft:emerald>,
    [<minecraft:stone> | <minecraft:cobblestone>, <minecraft:redstone> * 2], 40);

Guide.load("mymod/external.md");
Guide.addPage("mymod/inline.md", "Inline Guide", null, 20,
    "minecraft:diamond", ["minecraft:diamond"], "# Inline Guide\n\nInline Guide Body");
Guide.addTranslation("mymod/inline.md", "zh_cn", "# 内联指南\n\n中文正文");
