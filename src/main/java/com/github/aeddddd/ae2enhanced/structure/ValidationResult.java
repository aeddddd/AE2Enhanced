package com.github.aeddddd.ae2enhanced.structure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.level.block.Block;

/**
 * 多方块结构验证结果。
 */
public class ValidationResult {

    public final boolean passed;
    public final Map<Block, Integer> missing;
    public final boolean allChunksLoaded;
    public final int causalAnchorCount;
    public final int parallelLimit;

    public ValidationResult(boolean passed, Map<Block, Integer> missing, boolean allChunksLoaded) {
        this(passed, missing, allChunksLoaded, 0, 0);
    }

    public ValidationResult(boolean passed, Map<Block, Integer> missing, boolean allChunksLoaded,
            int causalAnchorCount, int parallelLimit) {
        this.passed = passed;
        this.missing = Collections.unmodifiableMap(new LinkedHashMap<>(missing));
        this.allChunksLoaded = allChunksLoaded;
        this.causalAnchorCount = causalAnchorCount;
        this.parallelLimit = parallelLimit;
    }

    public boolean passed() {
        return passed;
    }

    public boolean allChunksLoaded() {
        return allChunksLoaded;
    }

    public int causalAnchorCount() {
        return causalAnchorCount;
    }

    public int parallelLimit() {
        return parallelLimit;
    }

    public static ValidationResult incomplete(Map<Block, Integer> missing, boolean allChunksLoaded) {
        return new ValidationResult(false, missing, allChunksLoaded);
    }

    public static ValidationResult ok() {
        return new ValidationResult(true, Collections.emptyMap(), true);
    }
}
