/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.extension.proxytransport.transport.QuicProxyTransport;
import org.geysermc.extension.proxytransport.transport.TcpProxyTransport;
import org.geysermc.extension.proxytransport.util.QuicLibraryInstaller;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.network.netty.transport.GeyserDefineBedrockTransportsEvent;
import org.geysermc.geyser.network.netty.transport.RakNetGeyserTransport;

import java.io.IOException;

/**
 * Registers the ProxyTransport TCP/QUIC transports with Geyser via {@link GeyserDefineBedrockTransportsEvent}.
 */
public class ProxyTransportExtension implements Extension {

    @Subscribe
    public void onDefineTransports(GeyserDefineBedrockTransportsEvent event) {
        ProxyTransportConfig config;
        try {
            config = ProxyTransportConfig.load(this.dataFolder());
        } catch (IOException e) {
            this.logger().error("Failed to load config; no transports registered.", e);
            return;
        }

        boolean registered = false;

        if (config.tcpEnabled()) {
            event.register(new TcpProxyTransport(this, config));
            registered = true;
        }

        if (config.quicEnabled()) {
            // QUIC's native must be loadable before the transport is registered; skip QUIC if it can't be.
            try {
                QuicLibraryInstaller.install(this.dataFolder());
                event.register(new QuicProxyTransport(this, config));
                registered = true;
            } catch (QuicLibraryInstaller.InstallException e) {
                this.logger().error("QUIC could not be enabled; falling back to other transports. " + e.getMessage(), e);
            }
        }

        if (registered && config.disableRaknet()) {
            event.removeIf(transport -> transport.id().equals(RakNetGeyserTransport.ID));
        } else if (!registered) {
            this.logger().warning("No transport enabled in config.yml.");
        }
    }
}