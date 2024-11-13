//package org.example;
//
//import io.netty.bootstrap.Bootstrap;
//import io.netty.channel.*;
//import io.netty.handler.codec.http.*;
//import io.netty.handler.ssl.SslContext;
//import io.netty.handler.ssl.SslContextBuilder;
//
//public class TunnelProxy extends ChannelInboundHandlerAdapter {
//
//    private ChannelFuture cf;
//    private String host;
//    private int port;
//
//    @Override
//    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
//        if (msg instanceof FullHttpRequest) {
//            FullHttpRequest request = (FullHttpRequest) msg;
//            String host = request.headers().get("host");
//            String[] hostInfo = host.split(":");
//
//            // processing host & port
//            int port = 80;
//            if (hostInfo.length > 1) {
//                port = Integer.parseInt(hostInfo[1]);
//            }
//            else if (request.uri().indexOf("https") == 0){
//                port = 443;
//            }
//            this.host = hostInfo[0];
//            this.port = port;
//
//            if ("CONNECT".equalsIgnoreCase(request.method().name())) {
//                System.out.println("ProxyServerHandler >>>> CONNECT request received.");
//
//                // SSL handshake, establishing a secure connection
//                SslContext sslCtx = SslContextBuilder.forServer(
//                        Certificate.getInstance().getServerPrivateKey(),
//                        Certificate.getInstance().getCertificate(this.host)
//                ).build();
//
//                // proxy will server as a tunnel, response with 200
//                HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
//                ctx.writeAndFlush(response);
//                ctx.pipeline().remove("httpCodec");
//                ctx.pipeline().remove("httpObject");
//            }
//            else if ("GET".equalsIgnoreCase(request.method().name())){
//                System.out.println("ProxyServerHandler >>>> GET request received.");
//
//                // Proxy, as a client, talks to the real server
//                Bootstrap bootstrap = new Bootstrap();
//                bootstrap.group(ctx.channel().eventLoop()) // register thread pool
//                        .channel(ctx.channel().getClass())
//                        .handler(new HttpProxyInitializer(ctx.channel()));
//
//                ChannelFuture httpcf = bootstrap.connect(host, port);
//                httpcf.addListener(new ChannelFutureListener() {
//                    public void operationComplete(ChannelFuture future) throws Exception {
//                        if (future.isSuccess()) {
//                            System.out.println("http cfListener >>>> connection with server established, writing to the target server...");
//                            future.channel().writeAndFlush(msg);
//                        } else {
//                            ctx.channel().close();
//                        }
//                    }
//                });
//            }
//            else {
//                // it is not a GET request, close connection
//                System.out.println("ProxyServerHandler >>>> invalid request method: " + request.method().name());
//                ctx.channel().close();
//            }
//        }
//        // private key only exists on client side & real server
//        // therefore, what proxy can do at this moment is actually forward client data to server
//        // and send server's data back to client
//        else {
//            if (cf == null) {
//                // build a connection
//                Bootstrap bootstrap = new Bootstrap();
//                bootstrap.group(ctx.channel().eventLoop())
//                        .channel(ctx.channel().getClass())
//                        .handler(new ChannelInitializer() {
//
//                            @Override
//                            protected void initChannel(Channel ch) throws Exception {
//                                ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
//                                    @Override
//                                    public void channelRead(ChannelHandlerContext ctx0, Object msg) throws Exception {
//                                        System.out.println("HTTPS >>>> forwarding server's response back to client " +
//                                                ctx.channel().remoteAddress());
//                                        ctx.channel().writeAndFlush(msg);
//                                    }
//                                });
//                            }
//                        });
//                cf = bootstrap.connect(host, port);
//                cf.addListener(new ChannelFutureListener() {
//                    public void operationComplete(ChannelFuture future) throws Exception {
//                        if (future.isSuccess()) {
//                            System.out.println("HTTPS created cf successfully >>>> writing to " + future.channel().remoteAddress());
//                            future.channel().writeAndFlush(msg);
//                        } else {
//                            ctx.channel().close();
//                        }
//                    }
//                });
//            }
//
//            else {
//                System.out.println("HTTPS cf already exists >>>> writing to " + cf.channel().remoteAddress());
//                cf.channel().writeAndFlush(msg);
//            }
//        }
//    }
//
//}
