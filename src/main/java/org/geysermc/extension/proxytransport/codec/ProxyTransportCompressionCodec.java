/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.BedrockBatchWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.BatchCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionCodec;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;

import java.util.List;

/**
 * A {@link CompressionCodec} that adds ProxyTransport's Zstd compression byte ({@code 254} / {@code -2}).
 * <p>
 * Prefixing is asymmetric (mirroring the proxy): we always read the leading compression byte on decode (the
 * proxy always writes it), but only write it on encode once compression is negotiated and the client is
 * &ge; 1.20.60 (protocol &ge; 649) - never before {@code NetworkSettings}.
 */
public class ProxyTransportCompressionCodec extends CompressionCodec {

    public ProxyTransportCompressionCodec(CompressionStrategy strategy, boolean encodePrefixed) {
        super(strategy, encodePrefixed);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, BedrockBatchWrapper msg, List<Object> out) throws Exception {
        // Always read the leading compression byte: the proxy always prefixes its batches.
        ByteBuf compressed = msg.getCompressed().slice();
        CompressionAlgorithm algorithm = getCompressionAlgorithm(compressed.readByte());
        BatchCompression compression = getStrategy().getCompression(algorithm);

        msg.setAlgorithm(compression.getAlgorithm());
        msg.setUncompressed(compression.decode(ctx, compressed.slice()));
        onDecompressed(ctx, msg);
        out.add(msg.retain());
    }

    @Override
    protected byte getCompressionHeader0(CompressionAlgorithm algorithm) {
        if (algorithm == ZstdCompressionAlgorithm.ZSTD) {
            return -2;
        }
        return super.getCompressionHeader0(algorithm);
    }

    @Override
    protected CompressionAlgorithm getCompressionAlgorithm0(byte header) {
        if (header == -2) {
            return ZstdCompressionAlgorithm.ZSTD;
        }
        return super.getCompressionAlgorithm0(header);
    }
}