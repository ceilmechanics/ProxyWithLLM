# Proxy with Large Language Model
HTTPS proxy powered by GPT-4o to enhance web content delivery through real-time AI processing.

## Setup
- Docker installed on your machine
- Port 6667 available on your system

## Quick Start
1. Pull the Docker image:
```bash
docker pull ceilmechanics/proxy-with-llm:dev
```
2. Download ca.crt
```bash
docker cp <dokcer_container_id>:/app/ca.crt /path/to/store/ca.crt
```
Import the ca.crt to your browser.
3. Run proxy
```bash
docker run -d -p 6667:6667 ceilmechanics/proxy-with-llm:dev
```