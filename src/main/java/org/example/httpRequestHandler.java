package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.stream.ChunkedWriteHandler;

public class httpRequestHandler extends ChannelInboundHandlerAdapter {
    private final String host;
    private final int port;
    private final boolean isHttps;
    private Channel outboundChannel;

    public httpRequestHandler(String host, int port, boolean isHttps) {
        this.host = host;
        this.port = port;
        this.isHttps = isHttps;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            handleHttpRequest(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            if (outboundChannel != null && outboundChannel.isActive()) {
                outboundChannel.writeAndFlush(msg);
            }
        }
    }

    private void handleHttpRequest(final ChannelHandlerContext ctx, final FullHttpRequest request) {
        // Clone and modify the request
        final FullHttpRequest modifiedRequest = request.copy();

        // Modify URI for HTTP requests
        if (!isHttps && request.uri().startsWith("http://")) {
            String uri = request.uri();
            uri = uri.substring(uri.indexOf("/", 7));
            modifiedRequest.setUri(uri);
        }

        // Preserve important headers
        modifiedRequest.headers().set(HttpHeaderNames.HOST, host);
        modifiedRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);

        // Copy all original headers except those we explicitly modify
        request.headers().forEach(h -> {
            if (!h.getKey().equalsIgnoreCase(HttpHeaderNames.HOST.toString()) &&
                    !h.getKey().equalsIgnoreCase(HttpHeaderNames.CONNECTION.toString()) &&
                    !h.getKey().equalsIgnoreCase("Proxy-Connection")) {
                modifiedRequest.headers().set(h.getKey(), h.getValue());
            }
        });

        // Handle range requests properly
        if (request.headers().contains(HttpHeaderNames.RANGE)) {
            modifiedRequest.headers().set(HttpHeaderNames.RANGE, request.headers().get(HttpHeaderNames.RANGE));
        }

        if (outboundChannel != null && outboundChannel.isActive()) {
            outboundChannel.writeAndFlush(modifiedRequest);
            return;
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .option(ChannelOption.AUTO_READ, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        if (isHttps) {
                            ch.pipeline().addFirst(Certificate.theCertificate().getSslContext()
                                    .newHandler(ch.alloc(), host, port));
                        }
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(10 * 1024 * 1024));
                        ch.pipeline().addLast(new ChunkedWriteHandler());
                        ch.pipeline().addLast(new ProxyClientHandler(ctx.channel()));
                    }
                });

        ChannelFuture cf = bootstrap.connect(host, port);
        cf.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                outboundChannel = future.channel();
                System.out.println("Connected to " + host + ":" + port);
                future.channel().writeAndFlush(modifiedRequest);
            } else {
                System.err.println("Failed to connect to " + host + ":" + port);
                future.cause().printStackTrace();
                ctx.close();
            }
        });
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (outboundChannel != null) {
            closeOnFlush(outboundChannel);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        closeOnFlush(ctx.channel());
    }

    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}