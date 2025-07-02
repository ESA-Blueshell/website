#!/bin/sh

docker compose -f docker-compose.build.yml up --build -d

echo "Docker containers started"