/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.transport;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.geysermc.extension.proxytransport.ProxyTransportConfig;
import org.geysermc.extension.proxytransport.network.TransportChannelInitializer;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.network.netty.GeyserServer;
import org.geysermc.geyser.network.netty.transport.GeyserBedrockTransport;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

/**
 * A ProxyTransport listener over plain TCP. The proxy connects, length-delimited Bedrock batches flow, and each
 * accepted socket becomes a Geyser session.
 */
public final class TcpProxyTransport implements GeyserBedrockTransport {
    public static final String ID = "proxy_transport_tcp";

    private final Extension extension;
    private final ProxyTransportConfig config;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel channel;

    public TcpProxyTransport(Extension extension, ProxyTransportConfig config) {
        this.extension = extension;
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public CompletableFuture<Void> bind(GeyserServer server, InetSocketAddress defaultBindAddress) {
        boolean epoll = Epoll.isAvailable();
        this.bossGroup = epoll
            ? new EpollEventLoopGroup(1, new DefaultThreadFactory("ProxyTransport-TCP-Boss"))
            : new NioEventLoopGroup(1, new DefaultThreadFactory("ProxyTransport-TCP-Boss"));
        this.workerGroup = epoll
            ? new EpollEventLoopGroup(new DefaultThreadFactory("ProxyTransport-TCP-Worker"))
            : new NioEventLoopGroup(new DefaultThreadFactory("ProxyTransport-TCP-Worker"));

        Class<? extends ServerChannel> channelClass = epoll ? EpollServerSocketChannel.class : NioServerSocketChannel.class;
        InetSocketAddress bindAddress = new InetSocketAddress(config.address(), config.tcpPort());

        CompletableFuture<Void> result = new CompletableFuture<>();
        new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(channelClass)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(new TransportChannelInitializer(server.getSessionInitializer()))
            .bind(bindAddress)
            .addListener((ChannelFuture future) -> {
                if (future.isSuccess()) {
                    this.channel = future.channel();
                    extension.logger().info("ProxyTransport TCP listening on " + bindAddress);
                } else {
                    extension.logger().error("TCP failed to bind on " + bindAddress + "; disabling it.", future.cause());
                    shutdownGroups();
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
        shutdownGroups();
    }

    private void shutdownGroups() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
            workerGroup = null;
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
            bossGroup = null;
        }
    }
}
