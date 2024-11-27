package org.example;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandshakeCompletionEvent;

import java.util.Arrays;

public class ProxyServerHandler extends ChannelInboundHandlerAdapter {

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

            if ("CONNECT".equals(request.method().name())) {
                handleConnectRequest(ctx, request);
            }
            else {
                // For HTTP requests, directly handle without pipeline modification
                // HTTP requests do not require encode, so just forward whatever the real host sent back to you
                RequestHandler handler = new RequestHandler(host, port, false);
                handler.channelRead(ctx, request);
                request.release(); // netty memory cleanup
            }
        }
    }

    private void handleConnectRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        System.out.println("ProxyServerHandler >>>> https CONNECT from " + ctx.channel().remoteAddress());

        // created a ssl secure connection on ProxyServer side
        SslContext sslCtx = SslContextBuilder.forServer(
                Certificate.theCertificate().getServerPrivateKey(),
                Certificate.theCertificate().getCertificate(host)
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
        ctx.pipeline().remove(this);

        // Add SSL handler first
        ctx.pipeline().addFirst("ssl", sslCtx.newHandler(ctx.alloc()));

        // Add handler for SSL handshake completion
        ctx.pipeline().addLast("sslHandshakeHandler", new ChannelInboundHandlerAdapter() {
            @Override
            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                if (evt instanceof SslHandshakeCompletionEvent) {
                    SslHandshakeCompletionEvent event = (SslHandshakeCompletionEvent) evt;
                    if (event.isSuccess()) {
                        ctx.pipeline().remove(this);
                        ctx.pipeline().addLast("httpRequestDecoder", new HttpRequestDecoder());
                        ctx.pipeline().addLast("httpResponseEncoder", new HttpResponseEncoder());
                        ctx.pipeline().addLast("httpAggregator", new HttpObjectAggregator(10 * 1024 * 1024));
                        ctx.pipeline().addLast("proxyHandler", new RequestHandler(host, port, true));

                        System.out.println("SSL Handshake completed. Pipeline ready for HTTPS traffic.");
                    }
                    else {
                        System.err.println("SSL Handshake failed: " + event.cause());
                        ctx.close();
                    }
                }
                super.userEventTriggered(ctx, evt);
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println(Arrays.toString(cause.getStackTrace()));
        ctx.close();
    }
}