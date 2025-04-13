This project is a Java-based proxy server that intercepts and inspects both HTTP and HTTPS traffic. With LLM integration, it enhances the user experience by generating summaries, tables of content, and study guides from the intercepted web content. Additionally, users can ask specific questions about the content in real time.

### Key Features

- **Man-in-the-Middle Proxy**: Implemented using Java and Netty's key components:
  - EventLoop
  - ChannelPipeline
  - HttpObjectAggregator
  - ThreadPool

- **Content Interception**: Intercepts responses with content-type: text/html and injects pre-written JavaScript code to embed an LLM-powered floating chatbox

- **Caching**: Uses Java Caffeine to cache LLM responses for 10 minutes
  - Cache key is hashed with the request's fields (model, query, system, lastk)

- **Example**: browsing wikipedia using LLM proxy
![image](https://github.com/user-attachments/assets/2cafa0ae-e4ac-45c5-bdaa-4d08783559bf)

## Performance

When accessing standard websites (without LLM features), the proxy demonstrates comparable performance to direct connections:
- Page load time differences consistently under 1 second
- Minimal latency overhead, indicating efficient proxy implementation

Adding LLM features does not significantly slow down the proxy's performance:
- Proxy functionality increases load and finish times slightly due to buffering complete HTTP responses
- Difference is less than 1 second and not particularly noticeable to users

## Compatibility

The LLM feature performs best with websites that:
- Have text as their main content
- Use server-side rendering
- Deliver the majority of content in the initial HTML document

Limitations exist with sites that:
- Don't allow JavaScript injection (e.g., Quora, GitHub)
- Have non-text primary content (e.g., YouTube videos)

## Installation

### Docker Setup

1. Pull the Docker image:
   ```bash
   docker pull ceilmechanics/proxy-with-llm:dev
   ```

2. Download the CA certificate:
   ```bash
   docker cp <docker_container_id>:/app/ca.crt /path/to/store/ca.crt
   ```

3. Import the CA certificate to your browser and run the proxy:
   ```bash
   docker run -d -p 6667:6667 ceilmechanics/proxy-with-llm:dev
   ```

### Docker Hub Repository
Available at: [https://hub.docker.com/r/ceilmechanics/proxy-with-llm/tags](https://hub.docker.com/r/ceilmechanics/proxy-with-llm/tags)
