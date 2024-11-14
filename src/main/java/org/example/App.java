package org.example;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.proxy.HttpProxyHandler;

import java.security.*;

public class App {
    public static void main(String[] args) throws NoSuchAlgorithmException, NoSuchProviderException {
        int port = 6667;
        if (args.length > 0) {
            port = Integer.valueOf(args[0]);
        }
        new App().driver(port);
    }

    public void driver(int listenPort) throws NoSuchAlgorithmException, NoSuchProviderException {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup(2);
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 100)  // at most 100 connections in the queue
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<Channel>() {

                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ch.pipeline().addLast("httpRequestDecoder", new HttpRequestDecoder());
                            ch.pipeline().addLast("httpResponseEncoder", new HttpResponseEncoder());
                            ch.pipeline().addLast("httpAggregator", new HttpObjectAggregator(65536));
                            ch.pipeline().addLast("httpProxyServer", new HttpProxyServerHandler());
                        }
                    });
            System.out.println("driver >>>> proxy server start on port " + listenPort);
            ChannelFuture f = b.bind(listenPort).sync();
            f.channel().closeFuture().sync();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
