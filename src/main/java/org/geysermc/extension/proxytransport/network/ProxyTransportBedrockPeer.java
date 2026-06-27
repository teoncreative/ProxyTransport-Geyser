/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.batch.BedrockBatchDecoder;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;
import org.cloudburstmc.protocol.bedrock.packet.NetworkStackLatencyPacket;
import org.geysermc.extension.proxytransport.codec.ProxyTransportCompressionCodec;
import org.geysermc.extension.proxytransport.codec.ProxyTransportCompressionStrategy;
import org.geysermc.geyser.network.GeyserBedrockPeer;

/**
 * The {@link GeyserBedrockPeer} used for ProxyTransport connections. It differs from the RakNet peer in three
 * ways:
 * <ul>
 *     <li>Bedrock encryption is disabled (the proxy already authenticated the player and the link is trusted),</li>
 *     <li>compression is installed via {@link ProxyTransportCompressionCodec} so Zstd is understood, and</li>
 *     <li>latency is reported from a transport-specific measurement (see {@link #setPingMillis(int)}).</li>
 * </ul>
 * It also answers the proxy's {@link NetworkStackLatencyPacket} probes so the proxy can measure its round-trip
 * time to this downstream.
 */
public class ProxyTransportBedrockPeer extends GeyserBedrockPeer {

    private volatile int pingMillis = 0;

    public ProxyTransportBedrockPeer(Channel channel, BedrockSessionFactory sessionFactory) {
        super(channel, sessionFactory);
        // Security is handled by the transport/proxy; never negotiate Bedrock encryption on this link.
        this.setEncryptionSupported(false);
    }

    @Override
    public int getRakVersion() {
        // There is no RakNet channel here. Report the current RakNet protocol version so any code path that
        // still queries it (e.g. compression selection helpers) behaves as it would for a modern client.
        return 11;
    }

    @Override
    public void setCompression(CompressionStrategy strategy) {
        // Once compression is negotiated, prefix outgoing batches with the compression byte for clients
        // >= 1.20.60 (protocol >= 649) - exactly like WaterdogPE's setCompressionStrategy. Decode still always
        // reads the byte (handled inside ProxyTransportCompressionCodec).
        boolean encodePrefixed = getCodec().getProtocolVersion() >= 649;
        CompressionStrategy wrapped = new ProxyTransportCompressionStrategy(strategy.getDefaultCompression());
        ProxyTransportCompressionCodec codec = new ProxyTransportCompressionCodec(wrapped, encodePrefixed);

        ChannelPipeline pipeline = this.getChannel().pipeline();
        ChannelHandler existing = pipeline.get(CompressionCodec.NAME);
        if (existing == null) {
            pipeline.addBefore(BedrockBatchDecoder.NAME, CompressionCodec.NAME, codec);
        } else {
            pipeline.replace(CompressionCodec.NAME, CompressionCodec.NAME, codec);
        }
    }

    @Override
    protected void onBedrockPacket(BedrockPacketWrapper wrapper) {
        if (wrapper.getPacket() instanceof NetworkStackLatencyPacket latency && latency.getTimestamp() == 0L) {
            // A round-trip probe sent by the proxy. Echo it straight back so the proxy can measure latency to
            // this downstream, and do not forward it to the Geyser session - it is not a real client packet.
            NetworkStackLatencyPacket response = new NetworkStackLatencyPacket();
            response.setTimestamp(0L);
            response.setFromServer(true);
            this.sendPacketImmediately(0, wrapper.getSenderSubClientId(), response);
            return;
        }
        super.onBedrockPacket(wrapper);
    }

    @Override
    public int getPing() {
        return this.pingMillis;
    }

    /**
     * Updates the latency reported by {@link #getPing()}. Called by the QUIC transport from QUIC path stats.
     *
     * @param pingMillis the measured round-trip time in milliseconds
     */
    public void setPingMillis(int pingMillis) {
        this.pingMillis = pingMillis;
    }
}
