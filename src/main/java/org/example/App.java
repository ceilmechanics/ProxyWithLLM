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
    private static String mode = "ssl"; // set SSL connection as default mode
    private static final int port = 6667;

    public static void main(String[] args) {
        // our proxy will take only one argument, which is "tunnel"
        if (args.length > 0) {
            mode = args[0];
        }

        // if "tunnel" is specified in the command line, it will run in "tunnel" mode
        if (mode.equals("tunnel")) {
            new TunnelProxy(port);
        }
        new App().sslDriver();
    }

    public void sslDriver() {
        //  concurrency is supported by netty's event-driven architecture
        //  The boss group accepts connections and distributes them to the worker group
        //  The worker group (20 threads) handles multiple connections simultaneously
        //  All operations are non-blocking
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

                        // pipeline processing is also non-blocking and event-driven
                        // Inbound (Request) Flow:
                        // [Client] -> HttpRequestDecoder -> HttpObjectAggregator -> ProxyServerHandler

                        // Outbound (Response) Flow:
                        // ProxyServerHandler -> HttpResponseEncoder -> [Client]
                        @Override
                        protected void initChannel(Channel ch) {
                            ch.pipeline().addLast("httpRequestDecoder", new HttpRequestDecoder()); // bytes -> request
                            ch.pipeline().addLast("httpResponseEncoder", new HttpResponseEncoder()); // responses -> bytes
                            ch.pipeline().addLast("httpAggregator", new HttpObjectAggregator(10 * 1024 * 1024)); // 10MB
                            ch.pipeline().addLast("httpProxyServer", new ProxyServerHandler());
                        }
                    });

            System.out.println("ProxyUsingSslConnection >>>> starting on port " + port);

            ChannelFuture f = b.bind(port).sync();
            f.channel().closeFuture().sync();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            // clean up
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
