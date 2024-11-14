package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.ReferenceCountUtil;

public class httpsRequestHandler extends ChannelInboundHandlerAdapter {

    private final String host;
    private final int port;

    public httpsRequestHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            System.out.println("processing https request... \n" + request.toString());

            if ("GET".equalsIgnoreCase(request.method().name())) {
                System.out.println("GET request from " + ctx.channel().remoteAddress());

                // Clone the request and modify headers for better compatibility
                FullHttpRequest modifiedRequest = request.copy();
                modifiedRequest.headers().set(HttpHeaderNames.ACCEPT_ENCODING, "gzip, deflate");
                modifiedRequest.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
                // Remove any existing content-length header as it might be modified
                modifiedRequest.headers().remove(HttpHeaderNames.CONTENT_LENGTH);

                Bootstrap bootstrap = new Bootstrap();
                bootstrap.group(ctx.channel().eventLoop())
                        .channel(ctx.channel().getClass())
                        .option(ChannelOption.AUTO_READ, true)
                        .handler(new ChannelInitializer<Channel>() {
                            @Override
                            protected void initChannel(Channel ch) throws Exception {
                                System.out.println("[httpsRequestHandler] initChannel");
                                ch.pipeline().addFirst(
                                        Certificate.getInstance().getSslContext().newHandler(ch.alloc(), host, port));

                                // Add handlers in the correct order
                                ch.pipeline().addLast("httpCodec", new HttpClientCodec());
                                ch.pipeline().addLast("chunkedWriter", new ChunkedWriteHandler());
                                ch.pipeline().addLast("decompressor", new HttpContentDecompressor());
                                // Use a larger max content length
                                ch.pipeline().addLast("aggregator", new HttpObjectAggregator(10 * 1024 * 1024)); // 10MB
                                ch.pipeline().addLast("proxyClientHandle", new HttpProxyClientHandle(ctx.channel()));
                            }
                        });

                ChannelFuture cf = bootstrap.connect(host, port);
                cf.addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        System.out.println("Connected to " + future.channel().remoteAddress());
                        future.channel().writeAndFlush(modifiedRequest);
                    } else {
                        System.err.println("Failed to connect to " + host + ":" + port);
                        future.cause().printStackTrace();
                        ctx.close();
                    }
                });
            }
        }
        // Important: release the message if we're not forwarding it
        else {
            ReferenceCountUtil.release(msg);
        }
    }
}