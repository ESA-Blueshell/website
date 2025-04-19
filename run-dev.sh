#!/bin/sh

docker compose -f docker/docker-compose.dev.yml up --build -d

echo "Docker containers started"