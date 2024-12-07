package org.example;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.util.CharsetUtil;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class RequestHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LoggerFactory.getLogger(RequestHandler.class);
    private final String host;
    private final int port;
    private final boolean isHttps;
    private Channel outboundChannel;

    private final String llmHost = "localhost";
    private final int llmPort = 5000;


    public RequestHandler(String host, int port, boolean isHttps) {
        this.host = host;
        this.port = port;
        this.isHttps = isHttps;
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws IOException {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;

            if (host.equals("myproxydummyhost")) {
                if (request.uri().contains("/llmapi")) {
                    System.out.println("[requestHandler] processing LLM calls");
                    handleLargeLanguageModelRequest(ctx, request);
                }
            }
            else {
                handleHttpRequest(ctx, (FullHttpRequest) msg);
            }
        }
    }

    private void handleLargeLanguageModelRequest(final ChannelHandlerContext ctx, final FullHttpRequest request) throws IOException {
        if (request.method().equals(HttpMethod.OPTIONS)) {
            FullHttpResponse clientResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.NO_CONTENT
            );

            clientResponse.headers()
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "POST, GET, OPTIONS")
                    .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");

            ctx.channel().writeAndFlush(clientResponse);
            return;
        }
//        // Clone and modify the request
//        logger.info("Original request headers: {}", request.headers());
//        logger.info("Original request content length: {}", request.content().readableBytes());
//
//        final FullHttpRequest modifiedRequest = request.copy();
//        logger.info("\n" +
//                "+-----------------------------------------+\n" +
//                "|   TEXT FED TO LLM                       |\n" +
//                "+-----------------------------------------+\n");
//        logger.info(modifiedRequest.toString());

        String content = request.content().toString(CharsetUtil.UTF_8);
//
//        if (contentBuf.isReadable()) {
//            logger.info("Content size: {}", contentBuf.readableBytes());
//            String content = contentBuf.toString(CharsetUtil.UTF_8);
//            logger.info("Content: {}", content);
//        } else {
//            logger.warn("Content buffer is not readable!");
//        }


        JSONObject jsonBody = new JSONObject(content);
        String data = jsonBody.getString("data");
        logger.info("\n" +
                        "+-----------------------------------------+\n" +
                        "|   TEXT FED TO LLM                       |\n" +
                        "+-----------------------------------------+\n" +
                        "{}",
                content);

        JSONObject requestBody = new JSONObject()
                .put("model", "4o-mini")
                .put("system", "Answer my question in a funny manner")
                .put("query", "Who are the Jumbos")
                .put("temperature", 0.0)
                .put("lastk", 1)
                .put("session_id", "GenericSession");

        // Create the HTTP request
        FullHttpRequest llmRequest = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                HttpMethod.POST,
                "/post",
                Unpooled.copiedBuffer(requestBody.toString(), CharsetUtil.UTF_8)
        );

        Properties prop = new Properties();
        String apiKey = "";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            prop.load(input);
            apiKey = prop.getProperty("api-key");
        }

        llmRequest.headers()
                .set(HttpHeaderNames.HOST, "localhost:5000")
                .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
                .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                .set(HttpHeaderNames.CONTENT_LENGTH, llmRequest.content().readableBytes())
                .set("x-api-key", apiKey);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .option(ChannelOption.AUTO_READ, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<Channel>() {
                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(10 * 1024 * 1024));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {

                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx0, FullHttpResponse llmResponse) throws Exception {
                                String content = llmResponse.content().toString(CharsetUtil.UTF_8);
                                FullHttpResponse clientResponse = new DefaultFullHttpResponse(
                                        HttpVersion.HTTP_1_1,
                                        HttpResponseStatus.OK,
                                        Unpooled.copiedBuffer(content, CharsetUtil.UTF_8)
                                );

                                clientResponse.headers()
                                        .set(HttpHeaderNames.CONTENT_TYPE, "application/json")
                                        .set(HttpHeaderNames.CONTENT_LENGTH, clientResponse.content().readableBytes())
                                        .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*")
                                        .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "POST, GET, OPTIONS")
                                        .set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type");

                                ctx.channel().writeAndFlush(clientResponse);
                            }
                        });
                    }
                });

        ChannelFuture cf = bootstrap.connect(llmHost, llmPort);
        cf.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                outboundChannel = future.channel();
                System.out.println("Proxy as a client >>>> Connected to " + llmHost + ":" + llmPort);

                logger.info("Proxy, as a client, is connected to {}:{}", llmHost, llmPort);
                logger.info("\n" +
                                "+-----------------------------------------+\n" +
                                "|   sending LLM Request                   |\n" +
                                "+-----------------------------------------+\n" +
                                "{}",
                        llmRequest);
                future.channel().writeAndFlush(llmRequest);
            } else {
                System.err.println(" Proxy as a client >>>> Failed to connect to " + host + ":" + port);
                ctx.close();
            }
        });
    }

    private void handleHttpRequest(final ChannelHandlerContext ctx, final FullHttpRequest request) {
        // Clone and modify the request
        final FullHttpRequest modifiedRequest = request.copy();

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
            logger.info("\n" +
                            "+-----------------------------------------+\n" +
                            "|   forward client request to real host   |\n" +
                            "+-----------------------------------------+\n" +
                            "real host address: {} \n" +
                            "{}",
                    outboundChannel.remoteAddress(),
                    modifiedRequest.headers());
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
                        ch.pipeline().addLast(new HttpContentDecompressor());
                        ch.pipeline().addLast(new HttpObjectAggregator(10 * 1024 * 1024));
                        ch.pipeline().addLast(new ChunkedWriteHandler());
                        ch.pipeline().addLast(new ProxyClientHandler(ctx.channel(), host));
                    }
                });

        ChannelFuture cf = bootstrap.connect(host, port);
        cf.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                outboundChannel = future.channel();
                System.out.println("Proxy as a client >>>> Connected to " + host + ":" + port);

                logger.info("Proxy, as a client, is connected to {}:{}", host, port);
                logger.info("\n" +
                                "+-----------------------------------------+\n" +
                                "|   forward client request to real host   |\n" +
                                "+-----------------------------------------+\n" +
                                "{}",
                        modifiedRequest);
                future.channel().writeAndFlush(modifiedRequest);
            } else {
                System.err.println(" Proxy as a client >>>> Failed to connect to " + host + ":" + port);
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
        logger.error(Arrays.toString(cause.getStackTrace()));
        closeOnFlush(ctx.channel());
    }

    static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }
}