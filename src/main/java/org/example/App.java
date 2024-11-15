package org.example;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

public class App {
    private static String mode = "ssl";
    private static final int port = 6667;

    public static void main(String[] args) {
        if (args.length > 0) {
            mode = args[0];
        }

        if (mode.equals("tunnel")) {
            new TunnelProxy(port);
        }
        new App().sslDriver();
    }

    public void sslDriver() {
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
                            ch.pipeline().addLast("httpAggregator", new HttpObjectAggregator(10 * 1024 * 1024)); // 10MB
                            ch.pipeline().addLast("httpProxyServer", new ProxyServerHandler());
                        }
                    });

            System.out.println("ProxyUsingSslConnection >>>> starting on port " + port);

            ChannelFuture f = b.bind(port).sync();
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
