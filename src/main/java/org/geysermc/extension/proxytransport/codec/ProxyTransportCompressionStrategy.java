/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.codec;

import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.BatchCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;

/**
 * A {@link SimpleCompressionStrategy} that additionally knows how to (de)compress Zstd. The default (encode)
 * algorithm remains whatever vanilla algorithm Geyser negotiated with the client - Geyser only ever sends
 * client-native compression downstream - but Zstd can still be resolved by header byte when <i>decoding</i>
 * server-bound batches the proxy recompressed.
 */
public class ProxyTransportCompressionStrategy extends SimpleCompressionStrategy {
    private final ZstdCompression zstd = new ZstdCompression();

    public ProxyTransportCompressionStrategy(BatchCompression compression) {
        super(compression);
    }

    @Override
    public BatchCompression getCompression(CompressionAlgorithm algorithm) {
        if (algorithm == ZstdCompressionAlgorithm.ZSTD) {
            return this.zstd;
        }
        return super.getCompression(algorithm);
    }
}
