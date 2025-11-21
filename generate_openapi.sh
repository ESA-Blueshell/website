#!/bin/bash

# Generate OpenAPI Documentation Script
# This script generates the OpenAPI documentation from the Spring Boot API
# and then generates the frontend TypeScript client

set -e  # Exit on any error

echo "🚀 Starting OpenAPI documentation generation..."

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if docker compose is available
if ! docker compose version >/dev/null 2>&1; then
    print_error "Docker Compose is not available"
    exit 1
fi

# Check if docker-compose.dev.yml exists
if [ ! -f "docker-compose.dev.yml" ]; then
    print_error "docker-compose.dev.yml not found in current directory"
    exit 1
fi

print_status "Generating OpenAPI documentation from Spring Boot API..."

# Generate OpenAPI documentation using Maven in the API container
docker compose -f docker-compose.dev.yml exec api sh -c "
    cd /app && \
    mvn springdoc-openapi:generate
"

if [ $? -ne 0 ]; then
    print_error "Failed to generate OpenAPI documentation"
    exit 1
fi

print_success "OpenAPI documentation generated successfully"

print_status "Generating TypeScript client for frontend..."

# Generate TypeScript client in the frontend container
docker compose -f docker-compose.dev.yml exec frontend sh -c "
    cd /usr/app && \
    yarn gen:all && \
    (yarn lint:gen || true)
"

if [ $? -ne 0 ]; then
    print_error "Failed to generate TypeScript client"
    exit 1
fi

print_success "TypeScript client generated successfully"

print_success "🎉 OpenAPI documentation and TypeScript client generation completed!"
print_status "Generated files:"
print_status "  - API OpenAPI spec: openapi/blueshell.json"
print_status "  - Discord OpenAPI spec: openapi/discord.json"
print_status "  - Frontend clients: frontend/src/services/"

echo ""
print_status "You can now use the generated TypeScript client in your frontend application."