#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Get the parent directory
PARENT_DIR="$(dirname "$SCRIPT_DIR")"

# Iterate over each sibling directory
for DIR in "$PARENT_DIR"/*/; do
    # Skip if not a directory or if it is the script's directory
    [ -d "$DIR" ] || continue
    [ "$DIR" == "$SCRIPT_DIR/" ] && continue
    [ "$(basename "$DIR")" == "core-commons" ] && continue
    [ "$(basename "$DIR")" == "db-commons" ] && continue
    [ "$(basename "$DIR")" == "Docker" ] && continue
    [ "$(basename "$DIR")" == "Scripts" ] && continue
    [ "$(basename "$DIR")" == "telemetry-client" ] && continue
    [ "$(basename "$DIR")" == "api" ] && continue
    [ "$(basename "$DIR")" == "frontend" ] && continue

    # Get the name of the directory in lowercase
    SERVICE_NAME_LOWER=$(basename "$DIR" | tr '[:upper:]' '[:lower:]')

    # Remove the specified files
    rm -f "${DIR}${SERVICE_NAME_LOWER}-deployment.yaml"
    rm -f "${DIR}${SERVICE_NAME_LOWER}-service.yaml"
    rm -f "${DIR}Dockerfile"

    echo "Removed files from ${DIR}"
done