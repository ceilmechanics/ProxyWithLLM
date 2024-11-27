package org.example;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class TunnelProxy extends ChannelInboundHandlerAdapter {

    private final int serverPort;

    public TunnelProxy(int port) {
        serverPort = port;
        tunnelDriver();
    }

    public void tunnelDriver() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup(20);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)  // at most 100 connections in the queue
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<Channel>() {

                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast("httpRequestDecoder", new HttpRequestDecoder());
                            ch.pipeline().addLast("httpResponseEncoder", new HttpResponseEncoder());
                            ch.pipeline().addLast("httpAggregator", new HttpObjectAggregator(65536));
                            ch.pipeline().addLast("httpProxyServer", new TunnelProxyServer());
                        }
                    });

            System.out.println("ProxyTunnel >>>> starting on port " + serverPort);

            ChannelFuture f = b.bind(serverPort).sync();
            f.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // clean up
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
