/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.quic.InsecureQuicTokenHandler;
import io.netty.handler.codec.quic.QuicServerCodecBuilder;
import io.netty.handler.codec.quic.QuicSslContext;
import io.netty.handler.codec.quic.QuicSslContextBuilder;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.geysermc.extension.proxytransport.ProxyTransportConfig;
import org.geysermc.extension.proxytransport.network.QuicStreamChannelInitializer;
import org.geysermc.extension.proxytransport.util.SelfSignedCertificateGenerator;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.network.netty.GeyserServer;
import org.geysermc.geyser.network.netty.transport.GeyserBedrockTransport;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * A ProxyTransport listener over QUIC. Each bidirectional stream becomes a Geyser session. ALPN is {@code "ng"},
 * as the WaterdogPE client expects. Requires the {@code netty-codec-native-quic} native (bundled for linux-x86_64).
 */
public final class QuicProxyTransport implements GeyserBedrockTransport {
    public static final String ID = "proxy_transport_quic";

    private final Extension extension;
    private final ProxyTransportConfig config;

    private EventLoopGroup group;
    private Channel channel;

    public QuicProxyTransport(Extension extension, ProxyTransportConfig config) {
        this.extension = extension;
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public CompletableFuture<Void> bind(GeyserServer server, InetSocketAddress defaultBindAddress) {
        CompletableFuture<Void> result = new CompletableFuture<>();

        final QuicSslContext sslContext;
        try {
            // Throwable, not Exception: a missing native surfaces as UnsatisfiedLinkError. A QUIC failure must
            // never abort startup, so degrade and complete normally.
            SelfSignedCertificateGenerator.Result certificate = SelfSignedCertificateGenerator.generate();
            sslContext = QuicSslContextBuilder.forServer(certificate.privateKey(), null, certificate.certificate())
                .applicationProtocols("ng")
                .build();
        } catch (Throwable t) {
            extension.logger().error("QUIC could not initialise; disabling it.", t);
            result.complete(null);
            return result;
        }

        boolean epoll = Epoll.isAvailable();
        this.group = epoll
            ? new EpollEventLoopGroup(new DefaultThreadFactory("ProxyTransport-QUIC"))
            : new NioEventLoopGroup(new DefaultThreadFactory("ProxyTransport-QUIC"));

        ChannelHandler codec = new QuicServerCodecBuilder()
            .sslContext(sslContext)
            .maxIdleTimeout(30, TimeUnit.SECONDS)
            .initialMaxData(10_000_000)
            .initialMaxStreamDataBidirectionalLocal(1_000_000)
            .initialMaxStreamDataBidirectionalRemote(1_000_000)
            .initialMaxStreamsBidirectional(256)
            .initialMaxStreamsUnidirectional(256)
            .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
            // Per-connection handler must be @Sharable; sessions are created per stream below. Nothing to do
            // at the connection level.
            .handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                }
            })
            .streamHandler(new QuicStreamChannelInitializer(server.getSessionInitializer()))
            .build();

        InetSocketAddress bindAddress = new InetSocketAddress(config.address(), config.quicPort());
        new Bootstrap()
            .group(group)
            .channel(epoll ? EpollDatagramChannel.class : NioDatagramChannel.class)
            .handler(codec)
            .bind(bindAddress)
            .addListener((ChannelFuture future) -> {
                if (future.isSuccess()) {
                    this.channel = future.channel();
                    extension.logger().info("ProxyTransport QUIC listening on " + bindAddress);
                } else {
                    extension.logger().error("QUIC failed to bind on " + bindAddress + "; disabling it.", future.cause());
                    if (group != null) {
                        group.shutdownGracefully();
                        group = null;
                    }
                }
                // Complete normally regardless: a bind failure must not abort startup.
                result.complete(null);
            });
        return result;
    }

    @Override
    public void shutdown() {
        if (channel != null) {
            channel.close().syncUninterruptibly();
            channel = null;
        }
        if (group != null) {
            group.shutdownGracefully();
            group = null;
        }
    }
}
