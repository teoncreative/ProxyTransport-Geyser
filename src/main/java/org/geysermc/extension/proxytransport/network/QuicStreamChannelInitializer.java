/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.network;

import io.netty.channel.Channel;
import io.netty.handler.codec.quic.QuicConnectionPathStats;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ScheduledFuture;
import org.geysermc.geyser.network.GeyserSessionInitializer;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * The {@link TransportChannelInitializer} used for QUIC streams. In addition to the shared Bedrock pipeline, it
 * periodically reads the QUIC connection's path statistics to report an accurate round-trip latency to Geyser
 * (mirroring the WaterdogPE side). All QUIC-specific types are confined to this class so the base initializer
 * stays loadable on TCP-only deployments where the QUIC libraries are absent.
 */
public class QuicStreamChannelInitializer extends TransportChannelInitializer {
    private static final int PING_CYCLE_SECONDS = 2;

    public QuicStreamChannelInitializer(GeyserSessionInitializer sessionInitializer) {
        super(sessionInitializer);
    }

    @Override
    protected void onPeerCreated(Channel channel, ProxyTransportBedrockPeer peer) {
        if (!(channel instanceof QuicStreamChannel streamChannel)) {
            return;
        }

        // A QUIC stream's own address is a QuicStreamAddress, and QuicChannel#remoteAddress returns a
        // QuicConnectionAddress (the connection id) - neither is an InetSocketAddress, which Geyser's
        // UpstreamSession#getAddress casts to. Use remoteSocketAddress() (the real UDP address) and seed the
        // peer with it, until waterdog forwarding replaces it with the player's IP during login.
        SocketAddress remote = streamChannel.parent().remoteSocketAddress();
        if (remote instanceof InetSocketAddress inet) {
            peer.setProxiedAddress(inet);
        }

        ScheduledFuture<?> task = streamChannel.eventLoop().scheduleAtFixedRate(() ->
            streamChannel.parent().collectPathStats(0).addListener((Future<QuicConnectionPathStats> future) -> {
                if (future.isSuccess()) {
                    QuicConnectionPathStats stats = future.getNow();
                    peer.setPingMillis((int) (stats.rtt() / 1_000_000L)); // nanoseconds -> milliseconds
                }
            }), PING_CYCLE_SECONDS, PING_CYCLE_SECONDS, TimeUnit.SECONDS);

        streamChannel.closeFuture().addListener(f -> task.cancel(false));
    }
}