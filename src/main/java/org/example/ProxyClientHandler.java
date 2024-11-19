package org.example;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProxyClientHandler extends ChannelInboundHandlerAdapter {
    private final Channel clientChannel;
    private HttpResponse currentResponse;
    private StringBuilder contentBuilder; // Add this to accumulate content
    private static final Logger logger = LoggerFactory.getLogger(ProxyClientHandler.class);


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
                logger.info("\n" +
                                "+--------------------------------+\n" +
                                "|       Response Headers         |\n" +
                                "+--------------------------------+\n" +
                                "Status: {}\n" +
                                "Version: {}\n" +
                                "Headers:\n" +
                                "{}",
                        response.status(),
                        response.protocolVersion(),
                        formatHeaders(response.headers()));

//                logger.info("Headers:");
//                response.headers().forEach(h ->
//                        logger.info("\t{}: {}", h.getKey(), h.getValue()));
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
                        logger.info("\n" +
                                        "+--------------------------------+\n" +
                                        "|       {}         |\n" +
                                        "+--------------------------------+\n" +
                                        "{}\n" +
                                        "+--------------------------------+\n" +
                                        "|       End of Response Body     |\n" +
                                        "+--------------------------------+\n",
                                isTextContent(currentResponse.headers().get(HttpHeaderNames.CONTENT_TYPE, ""))
                                        ? "Response Content"
                                        : "Binary Content",
                                isTextContent(currentResponse.headers().get(HttpHeaderNames.CONTENT_TYPE, ""))
                                        ? contentBuilder.toString()
                                        : String.format("[Binary content length: %d bytes]", content.content().readableBytes())
                        );
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

    private String formatHeaders(HttpHeaders headers) {
        StringBuilder sb = new StringBuilder();
        headers.forEach(h ->
                sb.append(String.format("\t%s: %s\n", h.getKey(), h.getValue())));
        return sb.toString();
    }
}