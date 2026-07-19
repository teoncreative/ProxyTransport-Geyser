/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.util;

import org.geysermc.geyser.api.extension.Extension;
import org.nethergames.proxytransport.common.transport.TransportLogger;

/** Bridges the shared transports' logging onto the extension logger. */
public final class GeyserTransportLogger implements TransportLogger {

    private final Extension extension;

    public GeyserTransportLogger(Extension extension) {
        this.extension = extension;
    }

    @Override
    public void info(String message) {
        this.extension.logger().info(message);
    }

    @Override
    public void warn(String message) {
        this.extension.logger().warning(message);
    }

    @Override
    public void error(String message, Throwable cause) {
        this.extension.logger().error(message, cause);
    }
}
