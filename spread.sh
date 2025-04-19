#!/bin/bash

# Get the directory where the script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Read the content of the Dockerfile templates
DOCKERFILE_BUILD_TEMPLATE=$(<"$SCRIPT_DIR/docker/Dockerfile-build")
DOCKERFILE_DEV_TEMPLATE=$(<"$SCRIPT_DIR/docker/Dockerfile-dev")
KUBERNETES_DEPLOY_TEMPLATE=$(<"$SCRIPT_DIR/kubernetes/deployment.yaml")
KUBERNETES_SERVICE_TEMPLATE=$(<"$SCRIPT_DIR/kubernetes/service.yaml")
KUBERNETES_GATEWAY_SERVICE_TEMPLATE=$(<"$SCRIPT_DIR/kubernetes/gateway-service.yaml")
KUBERNETES_GATEWAY_DEPLOY_TEMPLATE=$(<"$SCRIPT_DIR/kubernetes/gateway-deployment.yaml")

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

for DIR in "$SCRIPT_DIR"/*/; do
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

    SERVICE_NAME=$(basename "$DIR")
    SERVICE_NAME_LOWER=$(basename "$SERVICE_NAME" | tr '[:upper:]' '[:lower:]')

    # Generate Dockerfile-build
    DOCKERFILE_BUILD_CONTENT="${DOCKERFILE_BUILD_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME}"
    DOCKERFILE_BUILD_PATH="${DIR}Dockerfile-build"
    if [ -f "$DOCKERFILE_BUILD_PATH" ] && [ "$FORCE" = false ]; then
        echo "Dockerfile-build already exists in ${DIR}. Use -f to overwrite."
    else
        echo "$DOCKERFILE_BUILD_CONTENT" > "$DOCKERFILE_BUILD_PATH"
        echo "Created $DOCKERFILE_BUILD_PATH"
    fi

    # Generate Dockerfile-dev
    DOCKERFILE_DEV_CONTENT="${DOCKERFILE_DEV_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME}"
    DOCKERFILE_DEV_PATH="${DIR}Dockerfile-dev"
    if [ -f "$DOCKERFILE_DEV_PATH" ] && [ "$FORCE" = false ]; then
        echo "Dockerfile-dev already exists in ${DIR}. Use -f to overwrite."
    else
        echo "$DOCKERFILE_DEV_CONTENT" > "$DOCKERFILE_DEV_PATH"
        echo "Created $DOCKERFILE_DEV_PATH"
    fi

    # Generate Kubernetes templates if applicable
    KUBERNETES_DEPLOY_CONTENT="${KUBERNETES_DEPLOY_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME_LOWER}"
    KUBERNETES_SERVICE_CONTENT="${KUBERNETES_SERVICE_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME_LOWER}"

    if [ "$SERVICE_NAME" == "api-gateway" ]; then
        KUBERNETES_DEPLOY_CONTENT="${KUBERNETES_GATEWAY_DEPLOY_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME_LOWER}"
        KUBERNETES_SERVICE_CONTENT="${KUBERNETES_GATEWAY_SERVICE_TEMPLATE//\$\{SERVICE_NAME\}/$SERVICE_NAME_LOWER}"
    fi

    KUBERNETES_DEPLOY_FILE="${DIR}${SERVICE_NAME_LOWER}-deployment.yaml"
    KUBERNETES_SERVICE_FILE="${DIR}${SERVICE_NAME_LOWER}-service.yaml"

    if [ "$FORCE" = true ] || ! { [ -f "$KUBERNETES_DEPLOY_FILE" ] && [ -f "$KUBERNETES_SERVICE_FILE" ]; }; then
        echo "$KUBERNETES_DEPLOY_CONTENT" > "$KUBERNETES_DEPLOY_FILE"
        echo "Created $KUBERNETES_DEPLOY_FILE"
        echo "$KUBERNETES_SERVICE_CONTENT" > "$KUBERNETES_SERVICE_FILE"
        echo "Created $KUBERNETES_SERVICE_FILE"
    else
        echo "Kubernetes templates already exist in ${DIR}. Use -f to overwrite."
    fi
done