#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMON_DIR="$(cd "$(dirname "core-commons")" && pwd)"

# Get the parent directory
PARENT_DIR="$(dirname "$SCRIPT_DIR")"

# Read the content of the Dockerfile in the script's directory
DOCKERFILE_TEMPLATE=$(<"$SCRIPT_DIR/dockerfile")
KUBERNETES_DEPLOY_TEMPLATE=$(<"$SCRIPT_DIR/deployment")
KUBERNETES_SERVICE_TEMPLATE=$(<"$SCRIPT_DIR/service")
KUBERNETES_GATEWAY_SERVICE_TEMPLATE=$(<"$SCRIPT_DIR/gateway-service")
KUBERNETES_GATEWAY_DEPLOY_TEMPLATE=$(<"$SCRIPT_DIR/gateway-deployment")

# Check for the -f flag
FORCE=false
while getopts "f" opt; do
  case $opt in
    f)
      FORCE=true
      ;;
    *)
      ;;
  esac
done

# Iterate over each sibling directory
for DIR in "$PARENT_DIR"/*/; do
    # Skip if not a directory or if it is the script's directory
    [ -d "$DIR" ] || continue
    [ "$DIR" == "$SCRIPT_DIR/" ] && continue
    [ "$(basename "$DIR")" == "core-commons" ] && continue
    [ "$(basename "$DIR")" == "db-commons" ] && continue
    [ "$(basename "$DIR")" == "Docker" ] && continue
    [ "$(basename "$DIR")" == "Scripts" ] && continue
    [ "$(basename "$DIR")" == "files" ] && continue
    [ "$(basename "$DIR")" == "telemetry-client" ] && continue
    [ "$(basename "$DIR")" == "blueshell-api" ] && continue
    [ "$(basename "$DIR")" == "blueshell-frontend" ] && continue

    # Get the name of the directory
    SERVICE_NAME=$(basename "$DIR")
    SERVICE_NAME_LOWER=$(basename "$SERVICE_NAME" | tr '[:upper:]' '[:lower:]')

    # Check if Dockerfile already exists
    if [ -f "${DIR}Dockerfile" ] && [ "$FORCE" = false ]; then
        echo "Dockerfile already exists in ${DIR}. Use -f to overwrite."
        continue
    fi

    # Replace ${SERVICE_NAME} with the actual directory name
    DOCKERFILE_CONTENT="${DOCKERFILE_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME}"
    KUBERNETES_DEPLOY_CONTENT="${KUBERNETES_DEPLOY_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME_LOWER}"
    KUBERNETES_SERVICE_CONTENT="${KUBERNETES_SERVICE_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME_LOWER}"

    # Create the Dockerfile in the directory
    echo "$DOCKERFILE_CONTENT" > "${DIR}Dockerfile"
    echo "Created ${DIR}Dockerfile"

    # Perform an action if the directory name is APIGateway
    if [ "$SERVICE_NAME" == "APIGateway" ]; then
        KUBERNETES_SERVICE_CONTENT="${KUBERNETES_GATEWAY_SERVICE_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME_LOWER}"
        KUBERNETES_DEPLOY_CONTENT="${KUBERNETES_GATEWAY_DEPLOY_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME_LOWER}"
    fi

    echo "$KUBERNETES_DEPLOY_CONTENT" > "${DIR}/${SERVICE_NAME_LOWER}-deployment.yaml"
    echo "Created ${DIR_LOWER}${SERVICE_NAME_LOWER}-deployment.yaml"
    echo "$KUBERNETES_SERVICE_CONTENT" > "${DIR}/${SERVICE_NAME_LOWER}-service.yaml"
    echo "Created ${DIR_LOWER}${SERVICE_NAME_LOWER}-service.yaml"
done