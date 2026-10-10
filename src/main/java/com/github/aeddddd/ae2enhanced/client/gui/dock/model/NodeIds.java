/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.model;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;

/** Utilities for stable layout identifiers. */
public final class NodeIds {
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}");

    private NodeIds() {
    }

    public static String random() {
        return UUID.randomUUID().toString();
    }

    public static String deterministic(String namespace, String source) {
        requireValid(namespace, "namespace");
        if (source == null || isBlank(source)) {
            throw new IllegalArgumentException("source must not be blank");
        }
        return namespace + ":" + UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8));
    }

    public static String requireValid(String id, String label) {
        if (id == null || !VALID_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(label + " must match " + VALID_ID.pattern());
        }
        return id;
    }

    /** Java 8 等价实现：{@code String.isBlank()} 按 Unicode 码点判定空白。 */
    private static boolean isBlank(String value) {
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            if (!Character.isWhitespace(codePoint)) {
                return false;
            }
            i += Character.charCount(codePoint);
        }
        return true;
    }
}
