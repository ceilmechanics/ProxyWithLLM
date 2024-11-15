package org.example;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.ReferenceCountUtil;

public class ProxyClientHandler extends ChannelInboundHandlerAdapter {
    private final Channel clientChannel;

    public ProxyClientHandler(Channel clientChannel) {
        this.clientChannel = clientChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            if (!clientChannel.isActive()) {
                ReferenceCountUtil.release(msg);
                ctx.close();
                return;
            }

            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                // Preserve chunked encoding if present
                if (HttpUtil.isTransferEncodingChunked(response)) {
                    response.headers().set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED);
                    response.headers().remove(HttpHeaderNames.CONTENT_LENGTH);
                }

                // Preserve content encoding
                if (response.headers().contains(HttpHeaderNames.CONTENT_ENCODING)) {
                    String encoding = response.headers().get(HttpHeaderNames.CONTENT_ENCODING);
                    response.headers().set(HttpHeaderNames.CONTENT_ENCODING, encoding);
                }

                // Handle range responses
                if (response.headers().contains(HttpHeaderNames.CONTENT_RANGE)) {
                    String range = response.headers().get(HttpHeaderNames.CONTENT_RANGE);
                    response.headers().set(HttpHeaderNames.CONTENT_RANGE, range);
                }

                // Modify the response header
                response.headers().set("test", "from my proxy:)");
            }

            clientChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    future.cause().printStackTrace();
                    closeOnFlush(ctx.channel());
                }
            });
        }
        catch (Exception e) {
            e.printStackTrace();
            ReferenceCountUtil.release(msg);
            closeOnFlush(ctx.channel());
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeOnFlush(clientChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    private static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}