/*
 * Copyright (c) 2024-2026 GeyserMC & NetherGamesMC
 * Licensed under the MIT license. See the Geyser LICENSE for details.
 */

package org.geysermc.extension.proxytransport.codec;

import com.github.luben.zstd.Zstd;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.ReferenceCountUtil;
import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.BatchCompression;

import java.nio.ByteBuffer;

/**
 * Zstandard {@link BatchCompression}. Unlike the WaterdogPE-side implementation (which only ever <i>encodes</i>
 * Zstd), Geyser sits at the downstream end of the link and therefore must also <i>decode</i> Zstd: the proxy
 * recompresses rewritten server-bound batches as Zstd before forwarding them here.
 */
public class ZstdCompression implements BatchCompression {

    /**
     * Hard cap on a single decompressed batch (12 MiB). Guards against malicious or corrupt frames advertising
     * an enormous content size.
     */
    private static final int MAX_DECOMPRESSED_SIZE = 12 * 1024 * 1024;

    private int level = -1;

    @Override
    public ByteBuf encode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        ByteBuf direct;
        if (!msg.isDirect() || msg instanceof CompositeByteBuf) {
            direct = ctx.alloc().ioBuffer(msg.readableBytes());
            direct.writeBytes(msg);
        } else {
            direct = msg;
        }

        ByteBuf output = ctx.alloc().directBuffer();
        try {
            int uncompressedLength = direct.readableBytes();
            int maxLength = (int) Zstd.compressBound(uncompressedLength);
            output.ensureWritable(maxLength);

            int compressedLength;
            if (direct.hasMemoryAddress()) {
                compressedLength = (int) Zstd.compressUnsafe(output.memoryAddress(), maxLength,
                    direct.memoryAddress() + direct.readerIndex(), uncompressedLength, this.level);
            } else {
                ByteBuffer sourceNio = direct.nioBuffer(direct.readerIndex(), direct.readableBytes());
                ByteBuffer targetNio = output.nioBuffer(0, maxLength);
                compressedLength = Zstd.compress(targetNio, sourceNio, this.level);
            }

            output.writerIndex(compressedLength);
            return output.retain();
        } finally {
            ReferenceCountUtil.release(output);
            if (direct != msg) {
                ReferenceCountUtil.release(direct);
            }
        }
    }

    @Override
    public ByteBuf decode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        ByteBuf direct;
        if (!msg.isDirect() || msg instanceof CompositeByteBuf) {
            direct = ctx.alloc().ioBuffer(msg.readableBytes());
            direct.writeBytes(msg);
        } else {
            direct = msg;
        }

        try {
            ByteBuffer sourceNio = direct.nioBuffer(direct.readerIndex(), direct.readableBytes());

            long contentSize = Zstd.decompressedSize(sourceNio);
            if (contentSize <= 0 || contentSize > MAX_DECOMPRESSED_SIZE) {
                throw new IllegalArgumentException("Invalid Zstd frame content size: " + contentSize);
            }

            ByteBuf output = ctx.alloc().directBuffer((int) contentSize);
            try {
                ByteBuffer targetNio = output.nioBuffer(0, (int) contentSize);
                int decompressed = Zstd.decompress(targetNio, sourceNio);
                output.writerIndex(decompressed);
                return output.retain();
            } finally {
                ReferenceCountUtil.release(output);
            }
        } finally {
            if (direct != msg) {
                ReferenceCountUtil.release(direct);
            }
        }
    }

    @Override
    public CompressionAlgorithm getAlgorithm() {
        return ZstdCompressionAlgorithm.ZSTD;
    }

    @Override
    public void setLevel(int level) {
        this.level = level;
    }

    @Override
    public int getLevel() {
        return this.level;
    }
}
