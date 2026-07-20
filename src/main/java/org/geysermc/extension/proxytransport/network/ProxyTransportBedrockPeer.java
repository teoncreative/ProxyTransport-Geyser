/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.network;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.ChannelInputShutdownEvent;
import org.cloudburstmc.protocol.bedrock.BedrockSessionFactory;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;
import org.geysermc.geyser.network.GeyserBedrockPeer;
import org.nethergames.proxytransport.common.network.ProxyTransportPeer;
import org.nethergames.proxytransport.common.network.ProxyTransportPeerSupport;

/**
 * The {@link GeyserBedrockPeer} used for ProxyTransport connections. Bedrock encryption is disabled because the
 * proxy already authenticated the player, and latency comes from a transport measurement rather than RakNet.
 * <p>
 * {@code setProxiedAddress} is inherited from {@link GeyserBedrockPeer}, which satisfies
 * {@link ProxyTransportPeer}.
 */
public class ProxyTransportBedrockPeer extends GeyserBedrockPeer implements ProxyTransportPeer {

    private volatile int pingMillis = 0;

    public ProxyTransportBedrockPeer(Channel channel, BedrockSessionFactory sessionFactory) {
        super(channel, sessionFactory);
        this.setEncryptionSupported(false);
    }

    @Override
    public int getRakVersion() {
        return ProxyTransportPeerSupport.RAK_VERSION;
    }

    @Override
    public void setCompression(CompressionStrategy strategy) {
        ProxyTransportPeerSupport.installCompression(getChannel(), strategy, getCodec().getProtocolVersion());
    }

    @Override
    protected void onBedrockPacket(BedrockPacketWrapper wrapper) {
        if (ProxyTransportPeerSupport.tryHandleLatencyProbe(this, wrapper)) {
            return;
        }
        super.onBedrockPacket(wrapper);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof ChannelInputShutdownEvent) {
            close("disconnect.closed");
            return;
        }
        super.userEventTriggered(ctx, event);
    }

    @Override
    public int getPing() {
        return this.pingMillis;
    }

    @Override
    public void setPingMillis(int pingMillis) {
        this.pingMillis = pingMillis;
    }
}