/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.transport;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.geysermc.extension.proxytransport.ProxyTransportConfig;
import org.geysermc.extension.proxytransport.network.ProxyTransportBedrockPeer;
import org.geysermc.extension.proxytransport.util.GeyserTransportLogger;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.network.netty.GeyserServer;
import org.geysermc.geyser.network.netty.transport.GeyserBedrockTransport;
import org.nethergames.proxytransport.common.network.TransportChannelInitializer;
import org.nethergames.proxytransport.common.transport.TcpProxyTransportServer;

/**
 * A ProxyTransport listener over plain TCP. Each accepted socket becomes a Geyser session.
 */
public final class TcpProxyTransport implements GeyserBedrockTransport {
    public static final String ID = "proxy_transport_tcp";

    private final ProxyTransportConfig config;
    private final TcpProxyTransportServer server;

    public TcpProxyTransport(Extension extension, ProxyTransportConfig config) {
        this.config = config;
        this.server = new TcpProxyTransportServer(new GeyserTransportLogger(extension));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public CompletableFuture<Void> bind(GeyserServer geyserServer, InetSocketAddress defaultBindAddress) {
        InetSocketAddress bindAddress = new InetSocketAddress(this.config.address(), this.config.tcpPort());
        return this.server.bind(bindAddress, new TransportChannelInitializer(
                channel -> new ProxyTransportBedrockPeer(channel, (peer, subClientId) -> {
                    BedrockServerSession session = new BedrockServerSession(peer, subClientId);
                    geyserServer.getSessionInitializer().initializeSession(session);
                    return session;
                })))
            // A bind failure must not abort Geyser's startup.
            .exceptionally(t -> null);
    }

    @Override
    public void shutdown() {
        this.server.shutdown();
    }
}