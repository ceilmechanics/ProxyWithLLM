package org.example;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.netty.buffer.ByteBuf;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class ProxyClientHandler extends ChannelInboundHandlerAdapter {
    private final Channel clientChannel;
    private HttpResponse currentResponse;
    private StringBuilder contentBuilder; // Add this to accumulate content
    private static final Logger logger = LoggerFactory.getLogger(ProxyClientHandler.class);


    public ProxyClientHandler(Channel clientChannel) {
        this.clientChannel = clientChannel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {
            if (!clientChannel.isActive()) {
                ReferenceCountUtil.release(msg);
                ctx.close();
                return;
            }

            if (msg instanceof HttpResponse) {
                HttpResponse response = (HttpResponse) msg;
                currentResponse = response;

                // Initialize StringBuilder only for HTML content that needs modification
                String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE, "");
                if (contentType.contains("text/html") && response.status().code() == 200) {
                    contentBuilder = new StringBuilder();
                    response.headers().set("test2", "added js");
                }

                response.headers().set("test", "from my proxy:)");

                // Print headers for debugging
                logger.info("""
                                
                                +--------------------------------+
                                |       Response Headers         |
                                +--------------------------------+
                                Status: {}
                                Version: {}
                                Headers:
                                {}""",
                        response.status(),
                        response.protocolVersion(),
                        formatHeaders(response.headers()));

                // For non-HTML content, forward the response immediately
                if (!contentType.contains("text/html")) {
                    ReferenceCountUtil.retain(msg);
                    clientChannel.writeAndFlush(msg);
                }
            }

            if (msg instanceof HttpContent) {
                String contentType = currentResponse != null ?
                        currentResponse.headers().get(HttpHeaderNames.CONTENT_TYPE, "") : "";

                // Handle HTML content that needs modification
                if (contentType.contains("text/html") && currentResponse.status().code() == 200) {
                    HttpContent content = (HttpContent) msg;
                    if (content.content().isReadable()) {
                        contentBuilder.append(content.content().toString(CharsetUtil.UTF_8));
                    }

                    if (msg instanceof LastHttpContent) {
                        // Process and send modified HTML content
                        insertJavaScriptToHTML(ctx, currentResponse, contentBuilder);
                        currentResponse = null;
                        contentBuilder = null;
                        ReferenceCountUtil.release(msg);
                    }
                }
                // For non-HTML content, forward the content chunk immediately
                else {
                    ReferenceCountUtil.retain(msg);
                    clientChannel.writeAndFlush(msg);
                }
            }


//            if (msg instanceof HttpContent) {
//                HttpContent content = (HttpContent) msg;
//                if (currentResponse != null && content.content().isReadable()) {
//                    String contentType = currentResponse.headers().get(
//                            HttpHeaderNames.CONTENT_TYPE, "");
//
//                    if (isTextContent(contentType)) {
//                        // Accumulate content
//                        contentBuilder.append(content.content().toString(CharsetUtil.UTF_8));
//                    }
//
//                    // If this is the last chunk, print the complete content
//                    if (msg instanceof LastHttpContent) {
//                        if (currentResponse.headers().get(HttpHeaderNames.CONTENT_TYPE).contains("text/html") && currentResponse.status().code() == 200) {
//                            insertJavaScriptToHTML(ctx, currentResponse, contentBuilder);
//                            currentResponse = null;
//                            contentBuilder = null;
//                            return;
//                        }
//                        currentResponse = null;
//                        contentBuilder = null;
//                    }
//                }
//            }
//
//            // other responses will not be modified, will be sent directly
//            if (!currentResponse.headers().get(HttpHeaderNames.CONTENT_TYPE).contains("text/html")) {
//                logger.info("writing msg back to client");
//                clientChannel.writeAndFlush(msg).addListener((ChannelFutureListener) future -> {
//                    if (!future.isSuccess()) {
//                        System.err.println(Arrays.toString(future.cause().getStackTrace()));
//                        closeOnFlush(ctx.channel());
//                    }
//                });
//            }
        }
        catch (Exception e) {
            System.err.println(Arrays.toString(e.getStackTrace()));
            ReferenceCountUtil.release(msg);
            closeOnFlush(ctx.channel());
        }
    }

    private void insertJavaScriptToHTML(ChannelHandlerContext ctx, HttpResponse currentResponse, StringBuilder contentBuilder) {
        System.out.println("LINE 142");
        String jsCode = """
            <script>
                async function callRemoteApi() {
                    try {
                        console.log("Preparing for >>>> calling LLM API");
                        const response = await fetch('https://myproxydummyhost/llmapi', {
                            method: 'POST',
                            headers: {
                                'Content-Type': 'application/json'
                            },
                            body: JSON.stringify({
                                data: 'example'
                            })
                        });
                        console.log("Finished >>>> calling LLM API");
                        const data = await response.json();
                        console.log('API Response:', data);
                    } catch (error) {
                        console.error('Error calling API:', error);
                    }
                }

                // Call the function when page loads
                document.addEventListener('DOMContentLoaded', callRemoteApi);
            </script>
        """;

//        String jsCode = """
//            <script>
//                function callRemoteApi() {
//                    console.log("test >>>> api call maded!");
//                }
//
//                // Call the function when page loads
//                document.addEventListener('DOMContentLoaded', callRemoteApi);
//            </script>
//        """;

        // Insert the JavaScript code before the closing </body> tag
        String originalContent = contentBuilder.toString();
        int bodyCloseIndex = originalContent.lastIndexOf("</body>");
        if (bodyCloseIndex != -1) {
            contentBuilder.insert(bodyCloseIndex, jsCode);
        } else {
            contentBuilder.append(jsCode);
        }

        // Convert the modified content to ByteBuf
        ByteBuf content = Unpooled.copiedBuffer(contentBuilder.toString(), CharsetUtil.UTF_8);

        // Create a new response with the modified content
        FullHttpResponse newResponse = new DefaultFullHttpResponse(
                currentResponse.protocolVersion(),
                currentResponse.status(),
                content
        );

        logger.info("\n" +
                        "+--------------------------------+\n" +
                        "|       Modified Content         |\n" +
                        "+--------------------------------+\n" +
                        "{}\n" +
                        "+--------------------------------+\n" +
                        "|       End of Response Body     |\n" +
                        "+--------------------------------+\n",
                contentBuilder
        );

        // Copy all headers from the original response
        newResponse.headers().set(currentResponse.headers());
        // Update content length
        newResponse.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());

        // Write the new response to the client
        clientChannel.writeAndFlush(newResponse).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                System.err.println(Arrays.toString(future.cause().getStackTrace()));
                closeOnFlush(ctx.channel());
            }
        });
    }

    private boolean isTextContent(String contentType) {
        if (contentType == null) return false;
        contentType = contentType.toLowerCase();
        return contentType.contains("text") ||
                contentType.contains("json") ||
                contentType.contains("xml") ||
                contentType.contains("html") ||
                contentType.contains("javascript") ||
                contentType.contains("css");
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        closeOnFlush(clientChannel);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println(Arrays.toString(cause.getStackTrace()));
        closeOnFlush(ctx.channel());
    }

    private static void closeOnFlush(Channel ch) {
        if (ch.isActive()) {
            ch.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    private String formatHeaders(HttpHeaders headers) {
        StringBuilder sb = new StringBuilder();
        headers.forEach(h -> sb.append(String.format("\t%s: %s\n", h.getKey(), h.getValue())));
        return sb.toString();
    }
}