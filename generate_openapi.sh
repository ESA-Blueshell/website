#!/bin/bash

# Generate OpenAPI Documentation Script
# This script generates the OpenAPI documentation from the Spring Boot API,
# downloads the latest Discord OpenAPI spec,
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

# Check if jq is available
if ! command -v jq >/dev/null 2>&1; then
    print_error "jq is not installed or not available in PATH"
    exit 1
fi

# Ensure openapi directory exists
if [ ! -d "openapi" ]; then
    print_error "openapi directory not found in current directory"
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

print_status "Downloading latest Discord OpenAPI spec..."

DISCORD_OPENAPI_URL="https://raw.githubusercontent.com/discord/discord-api-spec/refs/heads/main/specs/openapi.json"

# Download latest Discord OpenAPI JSON
curl -sS -L "$DISCORD_OPENAPI_URL" -o openapi/discord.json

if [ $? -ne 0 ]; then
    print_error "Failed to download Discord OpenAPI spec"
    exit 1
fi

print_success "Downloaded latest Discord OpenAPI spec"

print_status "Sorting and minifying openapi jsons..."

# Use a temporary file to avoid truncating on failure
TMP_FILE="$(mktemp)"

# Minify & sort blueshell.json
if [ -f "openapi/blueshell.json" ]; then
    jq -S -c . openapi/blueshell.json > "$TMP_FILE"
    mv "$TMP_FILE" openapi/blueshell.json
else
    print_error "openapi/blueshell.json not found"
    exit 1
fi

# Minify & sort discord.json
jq -S -c . openapi/discord.json > "$TMP_FILE"
mv "$TMP_FILE" openapi/discord.json

print_success "OpenAPI jsons sorted and minified"

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
