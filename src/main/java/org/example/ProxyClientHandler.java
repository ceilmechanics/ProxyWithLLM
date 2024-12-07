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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

public class ProxyClientHandler extends ChannelInboundHandlerAdapter {
    private final Channel clientChannel;
    private final String host;
    private HttpResponse currentResponse;
    private StringBuilder contentBuilder; // Add this to accumulate content
    private static final Logger logger = LoggerFactory.getLogger(ProxyClientHandler.class);

    public ProxyClientHandler(Channel clientChannel, String host) {
        this.clientChannel = clientChannel;
        this.host = host;
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

                String contentType = response.headers().get(HttpHeaderNames.CONTENT_TYPE, "");
                response.headers().set("test", "from my proxy:)");

                if (whitelistHost(host) && contentType != null && contentType.contains("text/html") && response.status().code() == 200) {
                    contentBuilder = new StringBuilder();
                    // Keep the message for later modification
                    ReferenceCountUtil.retain(msg);
                } else {
                    // For non-HTML content, forward with proper reference counting
                    clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))
                            .addListener((ChannelFutureListener) future -> {
                                if (!future.isSuccess()) {
                                    closeOnFlush(ctx.channel());
                                }
                                ReferenceCountUtil.release(msg);
                            });
                }

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
            }

            if (msg instanceof HttpContent) {
                if (currentResponse == null) {
                    // If no response context, forward with proper reference counting
                    clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))
                            .addListener((ChannelFutureListener) future -> {
                                if (!future.isSuccess()) {
                                    closeOnFlush(ctx.channel());
                                }
                                ReferenceCountUtil.release(msg);
                            });
                    return;
                }

                String contentType = currentResponse.headers().get(HttpHeaderNames.CONTENT_TYPE, "");
                boolean isHtml = whitelistHost(host) &&
                        contentType != null &&
                        contentType.contains("text/html") &&
                        currentResponse.status().code() == 200;

                if (isHtml) {
                    HttpContent content = (HttpContent) msg;
                    if (content.content().isReadable()) {
                        contentBuilder.append(content.content().toString(CharsetUtil.UTF_8));
                    }

                    if (msg instanceof LastHttpContent) {
                        insertJavaScriptToHTML(ctx, currentResponse, contentBuilder);
                        currentResponse = null;
                        contentBuilder = null;
                    }
                    ReferenceCountUtil.release(msg);
                } else {
                    // Forward non-HTML content with proper reference counting
                    clientChannel.writeAndFlush(ReferenceCountUtil.retain(msg))
                            .addListener((ChannelFutureListener) future -> {
                                if (!future.isSuccess()) {
                                    closeOnFlush(ctx.channel());
                                }
                                ReferenceCountUtil.release(msg);
                            });
                }
            }

        } catch (Exception e) {
            logger.error("Error in channelRead: ", e);
            ReferenceCountUtil.release(msg);
            closeOnFlush(ctx.channel());
        }
    }

    private void insertJavaScriptToHTML(ChannelHandlerContext ctx, HttpResponse currentResponse, StringBuilder contentBuilder) {
        String jsCode = """
     <script>
         function getCleanContent() {
             let mainContent;
            
             // Try Wikipedia specific content first
             mainContent = document.getElementById('mw-content-text');
             if (!mainContent) {
                 // Fallback to main/article tags
                 mainContent = document.querySelector('main') || document.querySelector('article');
             }
            
             if (!mainContent) {
                 // Last resort: get body and clean it
                 mainContent = document.body.cloneNode(true);
             }
            
             // Create a temporary div to clean content
             const tempDiv = document.createElement('div');
             tempDiv.appendChild(mainContent.cloneNode(true));
            
             // Remove unwanted elements
             const selectorsToRemove = [
                 'script',
                 'style',
                 'iframe',
                 'nav',
                 'header',
                 'footer',
                 '.navigation',
                 '.sidebar',
                 '.menu',
                 '.ad',
                 '.advertisement',
                 '.reference',
                 '.mw-editsection',  // Wikipedia edit links
                 '#mw-navigation',   // Wikipedia navigation
                 '.mw-jump-link',    // Wikipedia accessibility links
                 '.thumb',           // Wikipedia image thumbnails
                 '.metadata',        // Wikipedia metadata
                 '.navbox'           // Wikipedia navigation boxes
             ];
            
             selectorsToRemove.forEach(selector => {
                 const elements = tempDiv.querySelectorAll(selector);
                 elements.forEach(el => el.remove());
             });
            
             // Get only text content and clean it
             let textContent = tempDiv.textContent || tempDiv.innerText;
            
             // Clean up the text
             textContent = textContent
                 .replace(/\\s+/g, ' ')           // Replace multiple spaces with single space
                 .replace(/\\n\\s*/g, '\\n')        // Clean up newlines
                 .trim();                        // Remove leading/trailing whitespace
            
             return textContent;
         }
        
         async function callRemoteApi() {
             try {
                 console.log("Preparing for >>>> calling LLM API");
                 const response = await fetch('https://myproxydummyhost/llmapi/', {
                     method: 'POST',
                     headers: {
                         'Content-Type': 'application/json'
                     },
                     body: JSON.stringify({ data: "example" })
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

    private boolean whitelistHost(String givenHost) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("whitelist")) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    String host = line.trim().toLowerCase();
                    if (!host.isEmpty() && host.contains(givenHost)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error loading whitelist: {}", e.getMessage());
        }
        return false;
    }
}