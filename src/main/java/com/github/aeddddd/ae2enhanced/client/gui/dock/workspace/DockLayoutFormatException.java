/*
 * Ported from MEST (ME Spliced Terminal)
 * https://github.com/lhy512103/MEST
 * Copyright (c) 2026 lhy512103
 * Licensed under the MIT License. See licenses/MEST-LICENSE.txt.
 */
package com.github.aeddddd.ae2enhanced.client.gui.dock.workspace;

import java.io.IOException;

public class DockLayoutFormatException extends IOException {
    public DockLayoutFormatException(String message) {
        super(message);
    }

    public DockLayoutFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
