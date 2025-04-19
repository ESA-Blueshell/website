#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Get the parent directory
PARENT_DIR="$(dirname "$SCRIPT_DIR")"

# Iterate over each sibling directory
for DIR in "$PARENT_DIR"/*/; do
    # Skip if not a directory or if it is excluded
    [ -d "$DIR" ] || continue
    [[ "$DIR" == "$SCRIPT_DIR/" || \
       "$(basename "$DIR")" == "core-commons" || \
       "$(basename "$DIR")" == "db-commons" || \
       "$(basename "$DIR")" == "docker" || \
       "$(basename "$DIR")" == "kubernetes" || \
       "$(basename "$DIR")" == "files" || \
       "$(basename "$DIR")" == "data" || \
       "$(basename "$DIR")" == "telemetry-client" || \
       "$(basename "$DIR")" == "frontend" || \
       "$(basename "$DIR")" == "test" || \
        "$(basename "$DIR")" == "dist" ]] && continue

    # Get the name of the directory in lowercase
    SERVICE_NAME_LOWER=$(basename "$DIR" | tr '[:upper:]' '[:lower:]')

    # Remove the specified files
    rm -f "${DIR}${SERVICE_NAME_LOWER}-deployment.yaml"
    rm -f "${DIR}${SERVICE_NAME_LOWER}-service.yaml"
    rm -f "${DIR}Dockerfile-build"

    echo "Removed files from ${DIR}"
done