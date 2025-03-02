#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMON_DIR="$(cd "$(dirname "Common")" && pwd)"

# Get the parent directory
PARENT_DIR="$(dirname "$SCRIPT_DIR")"

# Read the content of the Dockerfile in the script's directory
DOCKERFILE_TEMPLATE=$(<"$SCRIPT_DIR/Dockerfile")

# Iterate over each sibling directory
for DIR in "$PARENT_DIR"/*/; do
    # Skip if not a directory or if it is the script's directory
    [ -d "$DIR" ] || continue
    [ "$DIR" == "$SCRIPT_DIR/" ] && continue
    [ "$(basename "$DIR")" == "Common" ] && continue
    [ "$(basename "$DIR")" == "telemetry-client" ] && continue
    [ "$(basename "$DIR")" == "blueshell-api" ] && continue
    [ "$(basename "$DIR")" == "blueshell-frontend" ] && continue

    # Get the name of the directory
    SERVICE_NAME=$(basename "$DIR")

    # Replace ${SERVICE_NAME} with the actual directory name
    DOCKERFILE_CONTENT="${DOCKERFILE_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME}"

    # Create the Dockerfile in the directory
    echo "$DOCKERFILE_CONTENT" > "${DIR}Dockerfile"
    echo "Created ${DIR}Dockerfile"
done