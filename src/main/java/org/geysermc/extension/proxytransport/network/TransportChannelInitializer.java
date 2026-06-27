/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.PacketDirection;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchEncoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.NoopCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec_v3;
import org.geysermc.extension.proxytransport.codec.ProxyTransportCompressionCodec;
import org.geysermc.extension.proxytransport.codec.ProxyTransportCompressionStrategy;
import org.geysermc.extension.proxytransport.codec.ProxyTransportFrameCodec;
import org.geysermc.geyser.network.GeyserSessionInitializer;

/**
 * Builds the per-connection (TCP socket or QUIC stream) pipeline for a ProxyTransport downstream connection and
 * hands the resulting {@link BedrockServerSession} to Geyser via {@link GeyserSessionInitializer}.
 * <p>
 * The pipeline mirrors the WaterdogPE ProxyTransport client pipeline, but for the server role: Geyser is the
 * Bedrock <i>server</i>, so packets it produces are {@link PacketDirection#CLIENT_BOUND}.
 * <p>
 * This class deliberately references <b>no</b> QUIC types, so it loads cleanly on a TCP-only deployment where
 * the QUIC libraries were never injected. QUIC-specific wiring lives in {@code QuicStreamChannelInitializer},
 * which extends this and overrides {@link #onPeerCreated(Channel, ProxyTransportBedrockPeer)}.
 */
public class TransportChannelInitializer extends ChannelInitializer<Channel> {
    private static final String FRAME_DECODER = "frame-decoder";
    private static final String FRAME_ENCODER = "frame-encoder";

    private final GeyserSessionInitializer sessionInitializer;

    public TransportChannelInitializer(GeyserSessionInitializer sessionInitializer) {
        this.sessionInitializer = sessionInitializer;
    }

    @Override
    protected void initChannel(Channel channel) {
        channel.attr(PacketDirection.ATTRIBUTE).set(PacketDirection.CLIENT_BOUND);

        channel.pipeline()
            .addLast(FRAME_DECODER, new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4))
            .addLast(FRAME_ENCODER, new LengthFieldPrepender(4))
            .addLast(ProxyTransportFrameCodec.NAME, new ProxyTransportFrameCodec())
            // Initial codec: encode unprefixed (the downstream must NOT write the compression byte before
            // NetworkSettings), but decode always reads the byte (the proxy always writes it). Geyser swaps in
            // the negotiated codec via ProxyTransportBedrockPeer#setCompression once NetworkSettings is exchanged.
            .addLast(CompressionCodec.NAME, new ProxyTransportCompressionCodec(new ProxyTransportCompressionStrategy(new NoopCompression()), false))
            .addLast(BedrockBatchDecoder.NAME, new BedrockBatchDecoder())
            .addLast(BedrockBatchEncoder.NAME, new BedrockBatchEncoder())
            .addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3());

        ProxyTransportBedrockPeer peer = new ProxyTransportBedrockPeer(channel, (p, subClientId) -> {
            BedrockServerSession session = new BedrockServerSession(p, subClientId);
            this.sessionInitializer.initializeSession(session);
            return session;
        });

        channel.pipeline().addLast(BedrockPeer.NAME, peer);

        onPeerCreated(channel, peer);
    }

    /**
     * Hook invoked after the session peer is installed. Transport-specific subclasses (e.g. QUIC) override this
     * to add extra behaviour such as latency measurement. The base (TCP) implementation does nothing.
     *
     * @param channel the connection channel
     * @param peer    the peer that was created for it
     */
    protected void onPeerCreated(Channel channel, ProxyTransportBedrockPeer peer) {
    }
}