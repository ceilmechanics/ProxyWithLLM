package org.example;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

public class ProxyClientHandler extends ChannelInboundHandlerAdapter {
    private final Channel clientChannel;
    private HttpResponse currentResponse;
    private StringBuilder contentBuilder; // Add this to accumulate content

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
                currentResponse = response;
                contentBuilder = new StringBuilder(); // Initialize for new response
                response.headers().set("test", "from my proxy:)");

                // Print headers immediately
                System.out.println("\n=== Response Headers ===");
                System.out.println("Status: " + response.status());
                System.out.println("Version: " + response.protocolVersion());
                System.out.println("Headers:");
                response.headers().forEach(h ->
                        System.out.println("\t" + h.getKey() + ": " + h.getValue()));
            }

            if (msg instanceof HttpContent) {
                HttpContent content = (HttpContent) msg;
                if (currentResponse != null && content.content().isReadable()) {
                    String contentType = currentResponse.headers().get(
                            HttpHeaderNames.CONTENT_TYPE, "");

                    if (isTextContent(contentType)) {
                        // Accumulate content
                        contentBuilder.append(content.content().toString(CharsetUtil.UTF_8));
                    }

                    // If this is the last chunk, print the complete content
                    if (msg instanceof LastHttpContent) {
                        if (isTextContent(currentResponse.headers().get(
                                HttpHeaderNames.CONTENT_TYPE, ""))) {
                            System.out.println("\n=== Response Content ===");
                            System.out.println(contentBuilder.toString());
                        } else {
                            System.out.println("\n[Binary content length: " +
                                    content.content().readableBytes() + " bytes]");
                        }
                        System.out.println("\n=== End of Response ===");
                        currentResponse = null;
                        contentBuilder = null;
                    }
                }
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

    private boolean isTextContent(String contentType) {
        if (contentType == null) return false;
        contentType = contentType.toLowerCase();
        return contentType.contains("text") ||
                contentType.contains("json") ||
                contentType.contains("xml") ||
                contentType.contains("html") ||
                contentType.contains("javascript") ||
                contentType.contains("css");
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