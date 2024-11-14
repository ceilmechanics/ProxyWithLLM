package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

public class HttpProxyServerHandler extends ChannelInboundHandlerAdapter {

    private ChannelFuture cf;
    private String host;
    private int port;

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String hostStr = request.headers().get("host");
            String[] hostInfo = hostStr.split(":");

            // processing host & port
            port = 80;
            if (hostInfo.length > 1) {
                port = Integer.parseInt(hostInfo[1]);
            }
            else if (request.uri().indexOf("https") == 0){
                port = 443;
            }
            host = hostInfo[0];

            if ("CONNECT".equalsIgnoreCase(request.method().name())) {
                System.out.println("ProxyServerHandler >>>> CONNECTION from " + ctx.channel().remoteAddress());

                // created a ssl secure connection on ProxyServer side
                SslContext sslCtx = SslContextBuilder.forServer(
                        Certificate.getInstance().getServerPrivateKey(),
                        Certificate.getInstance().getCertificate(host)
                ).build();

                // Send back a 200 Connection Established response
                FullHttpResponse response = new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1,
                        HttpResponseStatus.OK
                );
                ctx.writeAndFlush(response);

                // Remove existing HTTP handlers
                ctx.pipeline().remove("httpRequestDecoder");
                ctx.pipeline().remove("httpResponseEncoder");
                ctx.pipeline().remove("httpAggregator");
                ctx.pipeline().remove(this);  // Remove the current handler

                // Add SSL handler first
                ctx.pipeline().addFirst("ssl", sslCtx.newHandler(ctx.alloc()));

                // Wait for SSL handshake to complete
                ctx.pipeline().addLast("sslHandshakeHandler", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                        if (evt instanceof SslHandshakeCompletionEvent) {
                            SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
                            if (event.isSuccess()) {
                                // SSL handshake completed successfully, now add HTTP handlers
                                ctx.pipeline().remove(this);  // Remove this temporary handler

                                // Add HTTP handlers for decoding HTTPS traffic
                                ctx.pipeline().addLast("httpRequestDecoder", new HttpRequestDecoder());
                                ctx.pipeline().addLast("httpResponseEncoder", new HttpResponseEncoder());
                                ctx.pipeline().addLast("httpAggregator", new HttpObjectAggregator(65536));
                                ctx.pipeline().addLast("httpsRequestHandler", new httpsRequestHandler(host, port));

                                System.out.println("SSL Handshake completed. Pipeline ready for HTTPS traffic.");
                            } else {
                                System.err.println("SSL Handshake failed: " + event.cause());
                                ctx.close();
                            }
                        }
                        super.userEventTriggered(ctx, evt);
                    }
                });
            }
        }
    }

}