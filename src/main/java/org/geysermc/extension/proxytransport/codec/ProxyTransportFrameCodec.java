/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;

import java.util.List;

/**
 * Bridges raw length-delimited frames and {@link BedrockBatchWrapper}s.
 * <p>
 * Unlike cloudburst's RakNet {@code FrameIdCodec}, ProxyTransport frames carry no {@code 0xFE} game-packet
 * byte: a frame is simply {@code [compression-byte][compressed batch]} (the length prefix is stripped/added by
 * the surrounding {@code LengthFieldBasedFrameDecoder}/{@code LengthFieldPrepender}). The compression byte is
 * left in place so the downstream {@link ProxyTransportCompressionCodec} can read it.
 */
public class ProxyTransportFrameCodec extends MessageToMessageCodec<ByteBuf, BedrockBatchWrapper> {
    // Reuse cloudburst's handler name so pipeline mutations keyed off it stay consistent.
    public static final String NAME = org.cloudburstmc.protocol.bedrock.netty.codec.FrameIdCodec.NAME;

    @Override
    protected void encode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) {
        if (msg.getCompressed() == null) {
            throw new IllegalStateException("Bedrock batch was not compressed");
        }
        out.add(msg.getCompressed().retain());
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) {
        out.add(BedrockBatchWrapper.newInstance(msg.readRetainedSlice(msg.readableBytes()), null));
    }
}
