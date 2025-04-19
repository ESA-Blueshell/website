#!/bin/sh

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

cd Docker || exit
if [ "$FORCE" = true ]; then
  ./spread.sh -f
else
  ./spread.sh
fi
cd ..

minikube start
eval $(minikube docker-env)

docker compose -f docker-compose.build.yml build

cd script || exit
./launch_kubernetes.sh
cd ..

echo "Kubernetes pods started"

minikube dashboard